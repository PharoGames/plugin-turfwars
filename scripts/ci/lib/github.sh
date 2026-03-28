#!/bin/bash
# GitHub API helpers for CI/CD scripts
# Handles GitHub App token generation, API calls, and package operations

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Generate GitHub App token using jwt-cli and GitHub API
# Requires: APP_ID, APP_PRIVATE_KEY (or APP_PRIVATE_KEY_FILE)
generate_github_app_token() {
    local app_id="${APP_ID:-}"
    local private_key="${APP_PRIVATE_KEY:-}"
    local private_key_file="${APP_PRIVATE_KEY_FILE:-}"
    
    if [[ -z "$app_id" ]]; then
        die "APP_ID not set"
    fi
    
    # Get private key from file if not provided directly
    if [[ -z "$private_key" ]] && [[ -n "$private_key_file" ]]; then
        if [[ ! -f "$private_key_file" ]]; then
            die "Private key file not found: $private_key_file"
        fi
        private_key=$(cat "$private_key_file")
    fi
    
    if [[ -z "$private_key" ]]; then
        die "APP_PRIVATE_KEY or APP_PRIVATE_KEY_FILE not set"
    fi
    
    # Create JWT token
    local now
    now=$(date +%s)
    local iat=$((now - 60))
    local exp=$((now + 600))
    
    local header='{"alg":"RS256","typ":"JWT"}'
    local payload="{\"iat\":${iat},\"exp\":${exp},\"iss\":\"${app_id}\"}"
    
    # Save private key to temp file for signing
    local key_file
    key_file=$(mktemp)
    echo "$private_key" > "$key_file"
    
    # Generate JWT using openssl
    local jwt_header
    jwt_header=$(echo -n "$header" | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
    local jwt_payload
    jwt_payload=$(echo -n "$payload" | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
    local jwt_signature
    jwt_signature=$(echo -n "${jwt_header}.${jwt_payload}" | openssl dgst -sha256 -sign "$key_file" | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
    local jwt="${jwt_header}.${jwt_payload}.${jwt_signature}"
    
    rm -f "$key_file"
    
    # Get installation ID
    local installation_id="${GITHUB_INSTALLATION_ID:-}"
    if [[ -z "$installation_id" ]]; then
        log_info "Fetching installation ID..."
        local installations
        installations=$(curl -s -H "Authorization: Bearer ${jwt}" \
            -H "Accept: application/vnd.github.v3+json" \
            "https://api.github.com/app/installations")
        installation_id=$(echo "$installations" | grep -o '"id": [0-9]*' | head -1 | grep -o '[0-9]*')
        
        if [[ -z "$installation_id" ]]; then
            die "Failed to get installation ID"
        fi
        log_info "Using installation ID: $installation_id"
    fi
    
    # Get installation access token
    log_info "Generating installation access token..."
    local response
    response=$(curl -s -X POST \
        -H "Authorization: Bearer ${jwt}" \
        -H "Accept: application/vnd.github.v3+json" \
        "https://api.github.com/app/installations/${installation_id}/access_tokens")
    
    local token
    token=$(echo "$response" | grep -o '"token": "[^"]*"' | cut -d'"' -f4)
    
    if [[ -z "$token" ]]; then
        die "Failed to generate installation access token"
    fi
    
    echo "$token"
}

# Get GitHub token (from environment or generate from App)
get_github_token() {
    if [[ -n "${GITHUB_TOKEN:-}" ]]; then
        echo "$GITHUB_TOKEN"
    elif [[ -n "${APP_ID:-}" ]]; then
        generate_github_app_token
    else
        die "No GitHub authentication available (GITHUB_TOKEN or APP_ID required)"
    fi
}

# Make authenticated GitHub API call
# Usage: github_api_call GET /repos/owner/repo
github_api_call() {
    local method="$1"
    local endpoint="$2"
    shift 2
    local extra_args=("$@")
    
    local token
    token=$(get_github_token)
    
    local url="https://api.github.com${endpoint}"
    
    curl -s -X "$method" \
        -H "Authorization: token ${token}" \
        -H "Accept: application/vnd.github.v3+json" \
        "${extra_args[@]}" \
        "$url"
}

# Clone repository with authentication
# Usage: clone_repo https://github.com/owner/repo.git /path/to/dest
clone_repo() {
    local repo_url="$1"
    local dest_dir="$2"
    local branch="${3:-main}"
    
    local token
    token=$(get_github_token)
    
    # Convert HTTPS URL to authenticated URL
    local auth_url
    auth_url=$(echo "$repo_url" | sed "s|https://|https://x-access-token:${token}@|")
    
    log_info "Cloning repository: $repo_url (branch: $branch)"
    git clone --depth 1 --branch "$branch" "$auth_url" "$dest_dir"
}

# Commit and push changes to repository
# Usage: commit_and_push /path/to/repo "commit message"
commit_and_push() {
    local repo_dir="$1"
    local commit_message="$2"
    local branch="${3:-main}"
    
    cd "$repo_dir"
    
    # Configure git user (required for commits)
    if ! git config user.email >/dev/null 2>&1; then
        git config user.email "ci@pharogames.com"
        git config user.name "PharoGames CI"
    fi
    
    # Check if there are changes
    if [[ -z "$(git status --porcelain)" ]]; then
        log_info "No changes to commit"
        return 0
    fi
    
    log_info "Committing changes..."
    git add -A
    git commit -m "$commit_message"
    
    log_info "Pushing to $branch..."
    git push origin "$branch"
    
    log_success "Changes pushed successfully"
}

# Trigger repository dispatch event
# Usage: trigger_repository_dispatch owner/repo event-type '{"key":"value"}'
trigger_repository_dispatch() {
    local repo="$1"
    local event_type="$2"
    local payload="${3:-{}}"
    
    log_info "Triggering repository_dispatch: $event_type on $repo"
    
    local response
    response=$(github_api_call POST "/repos/${repo}/dispatches" \
        -d "{\"event_type\":\"${event_type}\",\"client_payload\":${payload}}")
    
    if [[ -n "$response" ]]; then
        log_error "Failed to trigger repository_dispatch: $response"
        return 1
    fi
    
    log_success "Repository dispatch triggered"
}

# Trigger workflow dispatch
# Usage: trigger_workflow_dispatch owner/repo workflow-id ref '{"key":"value"}'
trigger_workflow_dispatch() {
    local repo="$1"
    local workflow="$2"
    local ref="${3:-main}"
    local inputs="${4:-{}}"
    
    log_info "Triggering workflow_dispatch: $workflow on $repo (ref: $ref)"
    
    local response
    response=$(github_api_call POST "/repos/${repo}/actions/workflows/${workflow}/dispatches" \
        -d "{\"ref\":\"${ref}\",\"inputs\":${inputs}}")
    
    if [[ -n "$response" ]]; then
        log_error "Failed to trigger workflow_dispatch: $response"
        return 1
    fi
    
    log_success "Workflow dispatch triggered"
}

log_info "GitHub API Library loaded"

