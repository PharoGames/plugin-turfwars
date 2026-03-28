#!/bin/bash
# Docker build and push helpers for CI/CD scripts
# Handles Docker operations with multi-arch support and registry detection

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Get Docker registry
get_docker_registry() {
    local owner
    owner=$(get_repo_owner)
    echo "ghcr.io/${owner}"
}

# Build Docker image
# Usage: docker_build image-name:tag /path/to/dockerfile /path/to/context [--platform linux/amd64,linux/arm64]
docker_build() {
    local image_tag="$1"
    local dockerfile="${2:-Dockerfile}"
    local context="${3:-.}"
    shift 3
    local extra_args=("$@")
    
    require_command docker
    
    log_step "Building Docker image: $image_tag"
    log_info "Dockerfile: $dockerfile"
    log_info "Context: $context"
    
    # Check if buildx is available for multi-arch builds
    local use_buildx=false
    if docker buildx version >/dev/null 2>&1; then
        use_buildx=true
        log_info "Using Docker buildx for multi-platform builds"
    fi
    
    # Build command
    local build_cmd=(docker)
    
    if [[ "$use_buildx" == "true" ]]; then
        build_cmd+=(buildx build --load)
    else
        build_cmd+=(build)
    fi
    
    build_cmd+=(
        -f "$dockerfile"
        -t "$image_tag"
    )
    
    # Add extra args if provided
    if [[ ${#extra_args[@]} -gt 0 ]]; then
        build_cmd+=("${extra_args[@]}")
    fi
    
    build_cmd+=("$context")
    
    log_info "Running: ${build_cmd[*]}"
    
    if "${build_cmd[@]}"; then
        log_success "Image built successfully: $image_tag"
        return 0
    else
        log_error "Failed to build image"
        return 1
    fi
}

# Build multi-arch Docker image
# Usage: docker_build_multiarch image-name:tag /path/to/dockerfile /path/to/context [extra args]
docker_build_multiarch() {
    local image_tag="$1"
    local dockerfile="${2:-Dockerfile}"
    local context="${3:-.}"
    shift 3
    local extra_args=("$@")
    
    require_command docker
    
    if ! docker buildx version >/dev/null 2>&1; then
        log_warning "Docker buildx not available, falling back to single-arch build"
        docker_build "$image_tag" "$dockerfile" "$context" "${extra_args[@]}"
        return $?
    fi
    
    log_step "Building multi-arch Docker image: $image_tag"
    log_info "Platforms: linux/amd64, linux/arm64"
    log_info "Dockerfile: $dockerfile"
    log_info "Context: $context"
    
    # Ensure buildx builder exists
    if ! docker buildx inspect multiarch-builder >/dev/null 2>&1; then
        log_info "Creating buildx builder: multiarch-builder"
        docker buildx create --name multiarch-builder --driver docker-container --use
    else
        docker buildx use multiarch-builder
    fi
    
    # Build for multiple platforms
    local build_cmd=(
        docker buildx build
        --platform linux/amd64,linux/arm64
        -f "$dockerfile"
        -t "$image_tag"
        "${extra_args[@]}"
    )
    
    build_cmd+=(--push)
    
    build_cmd+=("$context")
    
    log_info "Running: ${build_cmd[*]}"
    
    if "${build_cmd[@]}"; then
        log_success "Multi-arch image built successfully: $image_tag"
        return 0
    else
        log_error "Failed to build multi-arch image"
        return 1
    fi
}

# Push Docker image to registry
# Usage: docker_push image-name:tag
docker_push() {
    local image_tag="$1"
    
    require_command docker
    
    log_step "Pushing Docker image: $image_tag"
    
    # Login to registry if credentials available
    if is_ci && [[ -n "${GITHUB_TOKEN:-}" ]]; then
        log_info "Logging into GHCR..."
        echo "$GITHUB_TOKEN" | docker login ghcr.io -u "${GITHUB_ACTOR:-ci}" --password-stdin
    fi
    
    if docker push "$image_tag"; then
        log_success "Image pushed successfully: $image_tag"
        return 0
    else
        log_error "Failed to push image"
        return 1
    fi
}

# Tag Docker image
# Usage: docker_tag source-tag dest-tag
docker_tag() {
    local source_tag="$1"
    local dest_tag="$2"
    
    log_info "Tagging image: $source_tag -> $dest_tag"
    
    if docker tag "$source_tag" "$dest_tag"; then
        log_success "Image tagged successfully"
        return 0
    else
        log_error "Failed to tag image"
        return 1
    fi
}

# Build and push Docker image (convenience function)
# Usage: docker_build_and_push image-name:tag dockerfile context [extra args]
docker_build_and_push() {
    local image_tag="$1"
    local dockerfile="${2:-Dockerfile}"
    local context="${3:-.}"
    shift 3
    local extra_args=("$@")
    
    if ! docker_build "$image_tag" "$dockerfile" "$context" "${extra_args[@]}"; then
        return 1
    fi
    
    if ! docker_push "$image_tag"; then
        return 1
    fi
    
    return 0
}

# Build and push multi-arch Docker image (convenience function)
# Usage: docker_build_and_push_multiarch image-name:tag dockerfile context [extra args]
docker_build_and_push_multiarch() {
    local image_tag="$1"
    local dockerfile="${2:-Dockerfile}"
    local context="${3:-.}"
    shift 3
    local extra_args=("$@")
    
    require_command docker
    
    if ! docker buildx version >/dev/null 2>&1; then
        log_warning "Docker buildx not available, falling back to single-arch"
        docker_build_and_push "$image_tag" "$dockerfile" "$context" "${extra_args[@]}"
        return $?
    fi
    
    log_step "Building and pushing multi-arch Docker image: $image_tag"
    log_info "Platforms: linux/amd64, linux/arm64"
    
    # Ensure buildx builder exists
    if ! docker buildx inspect multiarch-builder >/dev/null 2>&1; then
        log_info "Creating buildx builder: multiarch-builder"
        docker buildx create --name multiarch-builder --driver docker-container --use
    else
        docker buildx use multiarch-builder
    fi
    
    # Login to registry if needed
    if is_ci && [[ -n "${GITHUB_TOKEN:-}" ]]; then
        log_info "Logging into GHCR..."
        echo "$GITHUB_TOKEN" | docker login ghcr.io -u "${GITHUB_ACTOR:-ci}" --password-stdin
    fi
    
    # Build and push in one command
    local build_cmd=(
        docker buildx build
        --platform linux/amd64,linux/arm64
        -f "$dockerfile"
        -t "$image_tag"
        "${extra_args[@]}"
    )
    
    build_cmd+=(--push)
    
    build_cmd+=("$context")
    
    log_info "Running: ${build_cmd[*]}"
    
    if "${build_cmd[@]}"; then
        log_success "Multi-arch image built and pushed successfully: $image_tag"
        return 0
    else
        log_error "Failed to build and push multi-arch image"
        return 1
    fi
}

# Get image digest from registry
# Usage: get_image_digest image-name:tag
get_image_digest() {
    local image_tag="$1"
    
    require_command docker
    
    log_info "Getting digest for: $image_tag"
    
    local digest
    digest=$(docker inspect --format='{{index .RepoDigests 0}}' "$image_tag" 2>/dev/null | cut -d'@' -f2)
    
    if [[ -z "$digest" ]]; then
        log_warning "Could not get digest from local image, pulling from registry..."
        docker pull "$image_tag" >/dev/null 2>&1
        digest=$(docker inspect --format='{{index .RepoDigests 0}}' "$image_tag" 2>/dev/null | cut -d'@' -f2)
    fi
    
    if [[ -z "$digest" ]]; then
        log_error "Failed to get image digest"
        return 1
    fi
    
    echo "$digest"
}

# Generate image tag with timestamp and commit SHA
# Usage: generate_image_tag base-name [branch] [sha]
generate_image_tag() {
    local base_name="$1"
    local branch="${2:-$(get_branch_name)}"
    local sha="${3:-$(get_commit_sha)}"
    
    local registry
    registry=$(get_docker_registry)
    
    local short_sha="${sha:0:7}"
    local tag="${branch}-${short_sha}"
    
    echo "${registry}/${base_name}:${tag}"
}

# Generate latest tag for image
# Usage: generate_latest_tag base-name
generate_latest_tag() {
    local base_name="$1"
    
    local registry
    registry=$(get_docker_registry)
    
    echo "${registry}/${base_name}:latest"
}

log_info "Docker Library loaded"

