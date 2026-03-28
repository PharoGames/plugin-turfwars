#!/bin/bash
# Plugin publish script - uploads plugin JAR to S3 (pharogames-plugins bucket)
# In CI: uploads to s3://pharogames-plugins/<artifact-id>.jar
# Locally: installs to local Maven repository (~/.m2)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/maven.sh"

# Default values
plugin_dir="."
artifact_path=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --plugin-dir)
            plugin_dir="$2"
            shift 2
            ;;
        --artifact-path)
            artifact_path="$2"
            shift 2
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

log_step "Publishing Plugin"

cd "$plugin_dir"

# Resolve artifact path
if [[ -z "$artifact_path" ]]; then
    if is_ci && [[ -n "${GITHUB_OUTPUT:-}" ]] && [[ -f "$GITHUB_OUTPUT" ]]; then
        artifact_path=$(grep "^artifact_path=" "$GITHUB_OUTPUT" 2>/dev/null | cut -d'=' -f2- || echo "")
    fi
    if [[ -z "$artifact_path" ]] && [[ -f "build-metadata.env" ]]; then
        source "build-metadata.env"
        artifact_path="${ARTIFACT_PATH:-}"
    fi
fi

if [[ -z "$artifact_path" ]] || [[ ! -f "$artifact_path" ]]; then
    [[ -n "$artifact_path" ]] && log_warning "Artifact from metadata not found at: $artifact_path"
    log_info "Searching target/ for built artifact..."
    artifact_path=$(find_jar_artifact)
fi

[[ ! -f "$artifact_path" ]] && die "Artifact not found: $artifact_path"

# Get Maven coordinates
read -r group_id artifact_id version <<< "$(get_maven_coordinates)"
log_info "Publishing: ${group_id}:${artifact_id}:${version}"
log_info "Artifact: $artifact_path"

if is_ci; then
    # CI: Upload to S3
    S3_BUCKET="${AWS_S3_PLUGINS_BUCKET:-pharogames-plugins}"
    S3_KEY="${artifact_id}.jar"
    AWS_REGION="${AWS_REGION:-us-east-1}"

    log_info "Uploading to s3://${S3_BUCKET}/${S3_KEY} ..."

    if ! command -v aws &>/dev/null; then
        log_info "Installing awscli..."
        python3 -m venv /tmp/awsvenv && /tmp/awsvenv/bin/pip install awscli -q
        AWS_CMD="/tmp/awsvenv/bin/aws"
    else
        AWS_CMD="aws"
    fi

    if ! "$AWS_CMD" s3 cp "$artifact_path" "s3://${S3_BUCKET}/${S3_KEY}" --region "$AWS_REGION"; then
        die "Failed to upload to S3"
    fi

    log_success "Plugin uploaded: s3://${S3_BUCKET}/${S3_KEY}"
else
    # Local: Install to local Maven repository
    log_info "Installing to local Maven repository..."

    if ! maven_install "$artifact_path" "$group_id" "$artifact_id" "$version"; then
        die "Failed to install to local Maven repository"
    fi

    log_success "Plugin installed to local Maven repository"
    log_info "Location: ~/.m2/repository/${group_id//.//}/${artifact_id}/${version}"
fi
