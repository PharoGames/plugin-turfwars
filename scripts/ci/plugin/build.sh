#!/bin/bash
# Plugin build script - builds Java plugin with Maven
# Works in CI environments

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/maven.sh"

# Default values
plugin_dir="."
skip_tests="true"
output_dir=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --plugin-dir)
            plugin_dir="$2"
            shift 2
            ;;
        --skip-tests)
            skip_tests="$2"
            shift 2
            ;;
        --output-dir)
            output_dir="$2"
            shift 2
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

log_step "Building Plugin"
log_info "Plugin directory: $plugin_dir"

# Change to plugin directory
cd "$plugin_dir"

# Verify pom.xml exists
if [[ ! -f "pom.xml" ]]; then
    die "pom.xml not found in $plugin_dir"
fi

# Get Maven coordinates
read -r group_id artifact_id version <<< "$(get_maven_coordinates)"
log_info "Plugin: ${group_id}:${artifact_id}:${version}"

# Build the plugin
if ! maven_build "$skip_tests"; then
    die "Maven build failed"
fi

# Find the built JAR
jar_file=$(find_jar_artifact)
log_info "Built artifact: $jar_file"

# Calculate SHA256 digest
digest=$(calculate_sha256 "$jar_file")
log_info "Artifact digest: sha256:${digest}"

# Export build metadata for subsequent steps
if is_ci; then
    # GitHub Actions output
    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        echo "artifact_path=$jar_file" >> "$GITHUB_OUTPUT"
        echo "artifact_digest=$digest" >> "$GITHUB_OUTPUT"
        echo "plugin_name=$artifact_id" >> "$GITHUB_OUTPUT"
        echo "plugin_version=$version" >> "$GITHUB_OUTPUT"
        echo "group_id=$group_id" >> "$GITHUB_OUTPUT"
    else
        # CI mode but GITHUB_OUTPUT not set (local testing)
        log_info "CI mode detected but GITHUB_OUTPUT not set (local CI simulation)"
    fi
else
    # Local: write to file
    metadata_file="${output_dir:-.}/build-metadata.env"
    cat > "$metadata_file" <<EOF
ARTIFACT_PATH=$jar_file
ARTIFACT_DIGEST=$digest
PLUGIN_NAME=$artifact_id
PLUGIN_VERSION=$version
GROUP_ID=$group_id
EOF
    log_info "Build metadata written to: $metadata_file"
fi

log_success "Plugin build completed successfully"
log_success "Artifact: $jar_file"
log_success "Digest: sha256:${digest}"

