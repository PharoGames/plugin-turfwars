#!/bin/bash
# Plugin registry update script - commits artifact metadata to infrastructure repo
# Only runs in CI (skipped locally)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/github.sh"

# Default values
plugin_dir="."
infrastructure_repo="${INFRASTRUCTURE_REPO:-https://github.com/PharoGames/infrastructure.git}"
infrastructure_branch="${INFRASTRUCTURE_BRANCH:-main}"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --plugin-dir)
            plugin_dir="$2"
            shift 2
            ;;
        --infrastructure-repo)
            infrastructure_repo="$2"
            shift 2
            ;;
        --infrastructure-branch)
            infrastructure_branch="$2"
            shift 2
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

log_step "Updating Plugin Registry"

# Get build metadata — prefer env vars passed from workflow step outputs,
# fall back to reading GITHUB_OUTPUT file (which the runner may clear between steps)
plugin_name="${PLUGIN_NAME:-}"
artifact_digest="${ARTIFACT_DIGEST:-}"

if [[ -z "$plugin_name" ]] || [[ -z "$artifact_digest" ]]; then
    if [[ -f "${GITHUB_OUTPUT:-}" ]]; then
        plugin_name="${plugin_name:-$(grep "^plugin_name=" "$GITHUB_OUTPUT" | cut -d'=' -f2- || true)}"
        artifact_digest="${artifact_digest:-$(grep "^artifact_digest=" "$GITHUB_OUTPUT" | cut -d'=' -f2- || true)}"
    fi
fi

if [[ -z "$plugin_name" ]] || [[ -z "$artifact_digest" ]]; then
    die "Build metadata incomplete — pass PLUGIN_NAME and ARTIFACT_DIGEST env vars, or ensure build step writes to GITHUB_OUTPUT"
fi

log_info "Plugin: $plugin_name"
log_info "Digest: $artifact_digest"

# Create temporary directory for infrastructure repo
temp_dir=$(create_temp_dir)
log_info "Cloning infrastructure repository..."

if ! clone_repo "$infrastructure_repo" "$temp_dir" "$infrastructure_branch"; then
    die "Failed to clone infrastructure repository"
fi

# Create plugins directory if it doesn't exist
mkdir -p "$temp_dir/plugins"

# Create/update plugin registry file
registry_file="$temp_dir/plugins/${plugin_name}.yaml"
built_at=$(get_timestamp)

log_info "Writing registry file: $registry_file"

normalized_digest="${artifact_digest}"
if [[ "$normalized_digest" != sha256:* ]]; then
    normalized_digest="sha256:${normalized_digest}"
fi

cat > "$registry_file" <<EOF
plugin: ${plugin_name}
artifact:
  digest: ${normalized_digest}
  builtAt: ${built_at}
EOF

# Commit and push changes
log_info "Committing plugin artifact metadata..."

if ! commit_and_push "$temp_dir" "chore: update plugin artifact for ${plugin_name}" "$infrastructure_branch"; then
    die "Failed to commit and push registry update"
fi

log_success "Plugin registry updated successfully"
log_info "Registry file: plugins/${plugin_name}.yaml"
log_info "Infrastructure repo: $infrastructure_repo"

