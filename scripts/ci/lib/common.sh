#!/bin/bash
# Common utilities for CI/CD scripts
# Provides environment detection, logging, and error handling

set -euo pipefail

# Colors for terminal output (disabled in CI)
if [[ -t 1 ]] && [[ "${CI:-false}" != "true" ]]; then
    export COLOR_RED='\033[0;31m'
    export COLOR_GREEN='\033[0;32m'
    export COLOR_YELLOW='\033[1;33m'
    export COLOR_BLUE='\033[0;34m'
    export COLOR_CYAN='\033[0;36m'
    export COLOR_NC='\033[0m' # No Color
else
    export COLOR_RED=''
    export COLOR_GREEN=''
    export COLOR_YELLOW=''
    export COLOR_BLUE=''
    export COLOR_CYAN=''
    export COLOR_NC=''
fi

# Logging functions
log_info() {
    echo -e "${COLOR_BLUE}[INFO]${COLOR_NC} $*" >&2
}

log_success() {
    echo -e "${COLOR_GREEN}[SUCCESS]${COLOR_NC} $*" >&2
}

log_warning() {
    echo -e "${COLOR_YELLOW}[WARN]${COLOR_NC} $*" >&2
}

log_error() {
    echo -e "${COLOR_RED}[ERROR]${COLOR_NC} $*" >&2
}

log_step() {
    echo -e "${COLOR_CYAN}==>${COLOR_NC} $*" >&2
}

# Error handling
die() {
    log_error "$@"
    exit 1
}

# Environment detection
is_ci() {
    [[ "${CI:-false}" == "true" ]] || [[ "${GITHUB_ACTIONS:-false}" == "true" ]]
}



# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Require command to exist
require_command() {
    if ! command_exists "$1"; then
        die "Required command not found: $1"
    fi
}

# Get script directory (useful for finding relative paths)
get_script_dir() {
    local source="${BASH_SOURCE[0]}"
    while [[ -L "$source" ]]; do
        local dir
        dir="$(cd -P "$(dirname "$source")" && pwd)"
        source="$(readlink "$source")"
        [[ $source != /* ]] && source="$dir/$source"
    done
    cd -P "$(dirname "$source")" && pwd
}

# Get project root (assumes scripts are in scripts/ci/lib)
get_project_root() {
    local script_dir
    script_dir="$(get_script_dir)"
    cd "$script_dir/../../.." && pwd
}

# Calculate SHA256 digest of file
calculate_sha256() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        die "File not found: $file"
    fi
    
    if command_exists sha256sum; then
        sha256sum "$file" | cut -d' ' -f1
    elif command_exists shasum; then
        shasum -a 256 "$file" | cut -d' ' -f1
    else
        die "No SHA256 command found (tried sha256sum, shasum)"
    fi
}

# Get current timestamp in ISO 8601 format
get_timestamp() {
    date -u +"%Y-%m-%dT%H:%M:%SZ"
}

# Get git commit SHA
get_commit_sha() {
    git rev-parse HEAD 2>/dev/null || echo "unknown"
}

# Get git branch name
get_branch_name() {
    if is_ci && [[ -n "${GITHUB_REF:-}" ]]; then
        echo "${GITHUB_REF#refs/heads/}"
    else
        git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown"
    fi
}

# Get repository owner (GitHub org/user)
get_repo_owner() {
    if is_ci && [[ -n "${GITHUB_REPOSITORY_OWNER:-}" ]]; then
        echo "${GITHUB_REPOSITORY_OWNER}"
    else
        # Try to extract from git remote
        local remote_url
        remote_url=$(git config --get remote.origin.url 2>/dev/null || echo "")
        if [[ -n "$remote_url" ]]; then
            # Extract owner from https://github.com/owner/repo or git@github.com:owner/repo
            echo "$remote_url" | sed -E 's|.*[:/]([^/]+)/[^/]+(.git)?$|\1|'
        else
            echo "${DEFAULT_REPO_OWNER:-pharogames}"
        fi
    fi
}

# Get repository name
get_repo_name() {
    if is_ci && [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
        echo "${GITHUB_REPOSITORY#*/}"
    else
        # Try to extract from git remote
        local remote_url
        remote_url=$(git config --get remote.origin.url 2>/dev/null || echo "")
        if [[ -n "$remote_url" ]]; then
            # Extract repo from https://github.com/owner/repo or git@github.com:owner/repo
            echo "$remote_url" | sed -E 's|.*[:/][^/]+/([^/]+)(.git)?$|\1|'
        else
            basename "$(pwd)"
        fi
    fi
}

# Parse command line arguments into variables
# Usage: parse_args VAR1:default1 VAR2:default2 -- "$@"
parse_args() {
    local args=()
    local parsing_defaults=true
    
    for arg in "$@"; do
        if [[ "$parsing_defaults" == "true" ]]; then
            if [[ "$arg" == "--" ]]; then
                parsing_defaults=false
                continue
            fi
            local var_name="${arg%%:*}"
            local default_value="${arg#*:}"
            eval "${var_name}=\"${default_value}\""
        else
            args+=("$arg")
        fi
    done
    
    # Parse actual arguments
    local i=0
    while [[ $i -lt ${#args[@]} ]]; do
        local arg="${args[$i]}"
        if [[ "$arg" =~ ^--([^=]+)=(.+)$ ]]; then
            # Format: --key=value
            local key="${BASH_REMATCH[1]}"
            local value="${BASH_REMATCH[2]}"
            key="${key//-/_}" # Replace hyphens with underscores
            eval "${key}=\"${value}\""
        elif [[ "$arg" =~ ^--(.+)$ ]]; then
            # Format: --key value
            local key="${BASH_REMATCH[1]}"
            key="${key//-/_}"
            i=$((i + 1))
            if [[ $i -lt ${#args[@]} ]]; then
                eval "${key}=\"${args[$i]}\""
            else
                eval "${key}=true"
            fi
        fi
        i=$((i + 1))
    done
}

# Create temporary directory that's cleaned up on exit
create_temp_dir() {
    local temp_dir
    temp_dir=$(mktemp -d)
    trap "rm -rf '$temp_dir'" EXIT
    echo "$temp_dir"
}

log_info "CI/CD Common Library loaded"

