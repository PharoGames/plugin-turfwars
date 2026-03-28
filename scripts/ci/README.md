# CI/CD Scripts

This directory contains modular CI/CD scripts designed for GitHub Actions.

## Quick Start

### Build a Plugin

```bash
./scripts/ci/plugin/build.sh --plugin-dir ./plugin-chat
./scripts/ci/plugin/publish.sh --plugin-dir ./plugin-chat
```

### Build a Server Image

```bash
./scripts/ci/server-image/build.sh --server-type lobby
./scripts/ci/server-image/push.sh
```

### Build a Microservice

```bash
./scripts/ci/microservice/build.sh --service-dir ./orchestrator
./scripts/ci/microservice/push.sh
```

### Test Workflows with act

```bash
# Install act
brew install act  # macOS

# Test plugin workflow
act push -W .github/workflows/plugin-build.yml

# Test with secrets
act push -W .github/workflows/plugin-build.yml --secret-file .secrets
```

## Directory Structure

```
scripts/ci/
├── lib/                 # Shared libraries
│   ├── common.sh       # Environment detection, logging, utilities
│   ├── github.sh       # GitHub API, authentication, repo operations
│   ├── docker.sh       # Docker build/push, multi-arch support
│   └── maven.sh        # Maven build, publish, coordinate extraction
│
├── plugin/             # Plugin CI scripts
│   ├── build.sh        # Build plugin JAR
│   ├── publish.sh      # Upload JAR to S3 (pharogames-plugins bucket)
│   └── update-registry.sh  # Update infrastructure registry (CI only)
│
├── server-image/       # Server image CI scripts
│   ├── build.sh        # Build Docker image with plugins
│   ├── push.sh         # Push to GHCR
│
└── microservice/       # Microservice CI scripts
    ├── build.sh        # Build microservice image
    └── push.sh         # Push to GHCR
```

## Environment Detection

Scripts automatically detect whether they're running in CI:

- **CI Mode** (`CI=true` or `GITHUB_ACTIONS=true`)
  - Uploads plugin JARs to S3 (`pharogames-plugins` bucket)
  - Pushes Docker images to GHCR
  - Updates infrastructure repository

## Core Libraries

### common.sh

Foundational utilities for all scripts:

- **Environment Detection**: `is_ci()`
- **Logging**: `log_info()`, `log_success()`, `log_error()`, `log_warning()`
- **Git Helpers**: `get_commit_sha()`, `get_branch_name()`, `get_repo_owner()`
- **Utilities**: `calculate_sha256()`, `get_timestamp()`, `create_temp_dir()`

### github.sh

GitHub API and authentication:

- **Authentication**: GitHub App token generation
- **API Calls**: Authenticated GitHub API requests
- **Repository Operations**: Clone, commit, push with authentication
- **Workflow Triggers**: `repository_dispatch`, `workflow_dispatch`
- **Workflow Triggers**: `repository_dispatch`, `workflow_dispatch`

### docker.sh

Docker operations with multi-arch support:

- **Build**: Single-arch and multi-arch Docker builds
- **Push**: Pushes to GHCR
- **Tagging**: Generate tags with branch/SHA
- **Multi-arch**: Build for amd64 and arm64 simultaneously

### maven.sh

Maven build operations:

- **Build**: Maven clean package with test skipping
- **Install**: Install to local Maven repository
- **Utilities**: Extract coordinates, find artifacts

## Usage in GitHub Actions

Workflows are thin wrappers that invoke scripts:

```yaml
- name: Build plugin
  run: ./scripts/ci/plugin/build.sh --plugin-dir .
  env:
    CI: true
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

See `.github/workflows/` for complete examples.

## Environment Variables

### Common

- `CI` - Set to "true" in CI environments

### GitHub Authentication (cross-repo operations)

- `APP_ID` - GitHub App ID
- `APP_PRIVATE_KEY` - GitHub App private key
- `GITHUB_INSTALLATION_ID` - GitHub App installation ID

### AWS (plugin publishing)

- `AWS_ACCESS_KEY_ID` - AWS access key for S3 upload
- `AWS_SECRET_ACCESS_KEY` - AWS secret for S3 upload
- `AWS_REGION` - AWS region (default: `us-east-1`)
- `AWS_S3_PLUGINS_BUCKET` - S3 bucket name (default: `pharogames-plugins`)

### Build-Specific

- `INFRASTRUCTURE_REPO` - Infrastructure repository URL
- `INFRASTRUCTURE_BRANCH` - Infrastructure branch (default: main)
- `ORCHESTRATOR_URL` - Orchestrator API URL

## Examples

### Plugin Build

```bash
# Build only
./scripts/ci/plugin/build.sh --plugin-dir ./plugin-chat

# Build and upload to S3 (requires AWS credentials)
./scripts/ci/plugin/build.sh --plugin-dir ./plugin-chat
AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... \
  ./scripts/ci/plugin/publish.sh --plugin-dir ./plugin-chat

# Build and install to local Maven repo (for local dev)
./scripts/ci/plugin/build.sh --plugin-dir ./plugin-chat
./scripts/ci/plugin/publish.sh --plugin-dir ./plugin-chat  # no CI=true → local install
```

### Server Image Build

```bash
# Build with default plugins
./scripts/ci/server-image/build.sh --server-type lobby

# Build with custom plugins
PLUGINS_VERSIONS_JSON='[{"name":"plugin-chat","version":"1.0.0"}]' \
  ./scripts/ci/server-image/build.sh --server-type lobby

# Build and push
./scripts/ci/server-image/build.sh --server-type lobby
./scripts/ci/server-image/push.sh
```

### Microservice Build

```bash
# Build image
./scripts/ci/microservice/build.sh --service-dir ./orchestrator

# Multi-arch build (CI)
CI=true ./scripts/ci/microservice/build.sh --service-dir ./orchestrator
```

## Testing with act

[act](https://github.com/nektos/act) allows you to test GitHub Actions workflows locally:

```bash
# Install act
brew install act  # macOS
curl https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash  # Linux

# Test plugin workflow
act push -W .github/workflows/plugin-build.yml

# Test with secrets file
echo "GITHUB_TOKEN=ghp_xxxx" > .secrets
act push -W .github/workflows/plugin-build.yml --secret-file .secrets

# Test specific job
act push -W .github/workflows/plugin-build.yml -j build

# Dry run (show what would happen)
act push -W .github/workflows/plugin-build.yml -n
```

## Troubleshooting

### Script not executable

```bash
chmod +x scripts/ci/**/*.sh
```

### GitHub authentication failed

Ensure you have either:
- `GITHUB_TOKEN` environment variable set, or
- `APP_ID`, `APP_PRIVATE_KEY`, `GITHUB_INSTALLATION_ID` for GitHub App

### Multi-arch build failed

Ensure Docker buildx is installed:

```bash
docker buildx create --name multiarch-builder --driver docker-container --use
```

## Contributing

When adding new scripts:

1. Source the appropriate libraries (`common.sh`, etc.)
2. Use consistent logging (`log_info`, `log_error`, etc.)
3. Make scripts executable (`chmod +x`)
4. Add documentation and examples
