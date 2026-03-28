#!/bin/bash
# Maven build helpers for CI/CD scripts

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Build Maven project
# Usage: maven_build [skip-tests] [profiles]
maven_build() {
    local skip_tests="${1:-false}"
    local profiles="${2:-}"
    
    require_command mvn
    
    log_step "Building Maven project"
    
    local build_cmd=(mvn clean package -U)
    
    if [[ "$skip_tests" == "true" ]]; then
        build_cmd+=(-DskipTests)
        log_info "Skipping tests"
    fi
    
    if [[ -n "$profiles" ]]; then
        build_cmd+=(-P"$profiles")
        log_info "Using profiles: $profiles"
    fi
    
    # Add batch mode and show errors in CI
    if is_ci; then
        build_cmd+=(--batch-mode --errors)
    fi
    
    log_info "Running: ${build_cmd[*]}"
    
    if "${build_cmd[@]}"; then
        log_success "Maven build completed successfully"
        return 0
    else
        log_error "Maven build failed"
        return 1
    fi
}

# Install Maven artifact to local repository
# Usage: maven_install jar-file group-id artifact-id version
maven_install() {
    local jar_file="$1"
    local group_id="$2"
    local artifact_id="$3"
    local version="$4"
    
    require_command mvn
    
    if [[ ! -f "$jar_file" ]]; then
        die "JAR file not found: $jar_file"
    fi
    
    log_step "Installing Maven artifact to local repository"
    log_info "File: $jar_file"
    log_info "Coordinates: ${group_id}:${artifact_id}:${version}"
    
    local install_cmd=(
        mvn install:install-file
        -Dfile="$jar_file"
        -DgroupId="$group_id"
        -DartifactId="$artifact_id"
        -Dversion="$version"
        -Dpackaging=jar
    )
    
    if is_ci; then
        install_cmd+=(--batch-mode --errors)
    fi
    
    if "${install_cmd[@]}"; then
        log_success "Artifact installed to local repository"
        return 0
    else
        log_error "Failed to install artifact"
        return 1
    fi
}

# Get Maven project property
# Usage: get_maven_property property-name
get_maven_property() {
    local property="$1"
    
    require_command mvn
    
    mvn help:evaluate -Dexpression="$property" -q -DforceStdout 2>/dev/null
}

# Get Maven project coordinates
# Returns: group-id artifact-id version (space-separated)
get_maven_coordinates() {
    local group_id
    group_id=$(get_maven_property project.groupId)
    
    local artifact_id
    artifact_id=$(get_maven_property project.artifactId)
    
    local version
    version=$(get_maven_property project.version)
    
    if [[ -z "$group_id" ]] || [[ -z "$artifact_id" ]] || [[ -z "$version" ]]; then
        die "Failed to extract Maven coordinates from pom.xml"
    fi
    
    echo "$group_id $artifact_id $version"
}

# Find built JAR file in target directory
# Prefers the final (shaded) JAR over original-* prefixed JARs
# Usage: find_jar_artifact [target-dir]
find_jar_artifact() {
    local target_dir="${1:-target}"
    
    if [[ ! -d "$target_dir" ]]; then
        die "Target directory not found: $target_dir"
    fi
    
    local jar_file=""

    # Prefer the final artifact (non-original, non-shaded suffix)
    jar_file=$(find "$target_dir" -maxdepth 1 -name "*.jar" \
        ! -name "original-*" \
        ! -name "*-sources.jar" \
        ! -name "*-javadoc.jar" \
        ! -name "*-shaded.jar" \
        -type f \
        | head -n 1)

    # Fall back to any JAR
    if [[ -z "$jar_file" ]]; then
        jar_file=$(find "$target_dir" -maxdepth 1 -name "*.jar" \
            ! -name "*-sources.jar" \
            ! -name "*-javadoc.jar" \
            -type f \
            | head -n 1)
    fi
    
    if [[ -z "$jar_file" ]]; then
        die "No JAR artifact found in $target_dir"
    fi
    
    echo "$jar_file"
}

# Run Maven tests
# Usage: maven_test [test-class-pattern]
maven_test() {
    local test_pattern="${1:-}"
    
    require_command mvn
    
    log_step "Running Maven tests"
    
    local test_cmd=(mvn test)
    
    if [[ -n "$test_pattern" ]]; then
        test_cmd+=(-Dtest="$test_pattern")
        log_info "Test pattern: $test_pattern"
    fi
    
    if is_ci; then
        test_cmd+=(--batch-mode --errors)
    fi
    
    if "${test_cmd[@]}"; then
        log_success "Tests passed"
        return 0
    else
        log_error "Tests failed"
        return 1
    fi
}

# Clean Maven build artifacts
# Usage: maven_clean
maven_clean() {
    require_command mvn
    
    log_step "Cleaning Maven build artifacts"
    
    local clean_cmd=(mvn clean)
    
    if is_ci; then
        clean_cmd+=(--batch-mode --errors)
    fi
    
    if "${clean_cmd[@]}"; then
        log_success "Build artifacts cleaned"
        return 0
    else
        log_error "Failed to clean artifacts"
        return 1
    fi
}

# Verify Maven project
# Usage: maven_verify
maven_verify() {
    require_command mvn
    
    log_step "Verifying Maven project"
    
    local verify_cmd=(mvn verify)
    
    if is_ci; then
        verify_cmd+=(--batch-mode --errors)
    fi
    
    if "${verify_cmd[@]}"; then
        log_success "Project verified successfully"
        return 0
    else
        log_error "Project verification failed"
        return 1
    fi
}

log_info "Maven Library loaded"

