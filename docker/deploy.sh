#!/bin/bash
# ========================================================================
# Deployment Script - Discord Visual Room
# ========================================================================
#
# This script is used on the production server (192.168.68.56) to deploy
# the Discord Visual Room application from GitHub.
#
# Usage:
#   ./deploy.sh [--no-build] [--no-restart]
#
# Options:
#   --no-build    Skip building images (use existing)
#   --no-restart  Skip restarting containers
#
# ========================================================================

set -e

# ========================================================================
# Configuration
# ========================================================================
PROJECT_NAME="discord-visual-room"
PROJECT_DIR="$HOME/$PROJECT_NAME"
DOCKER_COMPOSE_FILE="$PROJECT_DIR/docker/docker-compose.yml"
BRANCH="${BRANCH:-main}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ========================================================================
# Functions
# ========================================================================

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_banner() {
    cat << "EOF"
================================================================================
   ____                _    ____  ____  __  __
  |  _ \ _   _ _ __   / \  |  _ \|  _ \|  \/  |
  | |_) | | | | '_ \ / _ \ | | | | | | | |\/| |
  |  _ <| |_| | | | / ___ \| |_| | |_| | |  | |
  |_| \_\\__,_|_| |_/_/   \_\____/|____/|_|  |_|

              Discord Visual Room - Deployment Script
================================================================================
EOF
}

# ========================================================================
# Pre-flight Checks
# ========================================================================

preflight_checks() {
    log_info "Running pre-flight checks..."

    # Check if we're on the production server
    if [[ ! "$HOSTNAME" =~ .*frozenscorch.* ]] && [[ ! "$(hostname -I)" =~ .*192.168.68.56.* ]]; then
        log_warn "This script should be run on the production server (192.168.68.56)"
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi

    # Check if project directory exists
    if [[ ! -d "$PROJECT_DIR" ]]; then
        log_error "Project directory not found: $PROJECT_DIR"
        log_info "Cloning repository..."
        git clone "https://github.com/frozenscorch/$PROJECT_NAME.git" "$PROJECT_DIR"
    fi

    # Check if Docker is installed
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi

    # Check if Docker Compose is installed
    if ! docker compose version &> /dev/null; then
        log_error "Docker Compose is not installed"
        exit 1
    fi

    log_info "Pre-flight checks passed"
}

# ========================================================================
# Update from GitHub
# ========================================================================

update_from_github() {
    log_info "Updating from GitHub (branch: $BRANCH)..."

    cd "$PROJECT_DIR"

    # Fetch latest changes
    git fetch origin

    # Check if we need to pull
    LOCAL=$(git rev-parse HEAD)
    REMOTE=$(git rev-parse origin/"$BRANCH")

    if [[ "$LOCAL" != "$REMOTE" ]]; then
        log_info "New changes detected, pulling..."
        git pull origin "$BRANCH"
    else
        log_info "Already up to date"
    fi
}

# ========================================================================
# Build Docker Images
# ========================================================================

build_images() {
    if [[ "$SKIP_BUILD" == "true" ]]; then
        log_warn "Skipping build (--no-build flag)"
        return
    fi

    log_info "Building Docker images..."

    cd "$PROJECT_DIR"

    # Build images using Docker Compose
    docker compose -f "$DOCKER_COMPOSE_FILE" build --no-cache

    log_info "Docker images built successfully"
}

# ========================================================================
# Stop Existing Containers
# ========================================================================

stop_containers() {
    log_info "Stopping existing containers..."

    cd "$PROJECT_DIR"

    # Stop and remove containers
    docker compose -f "$DOCKER_COMPOSE_FILE" down

    log_info "Containers stopped"
}

# ========================================================================
# Start New Containers
# ========================================================================

start_containers() {
    if [[ "$SKIP_RESTART" == "true" ]]; then
        log_warn "Skipping restart (--no-restart flag)"
        return
    fi

    log_info "Starting new containers..."

    cd "$PROJECT_DIR"

    # Start containers in detached mode
    docker compose -f "$DOCKER_COMPOSE_FILE" up -d

    log_info "Containers started"
}

# ========================================================================
# Health Check
# ========================================================================

health_check() {
    log_info "Running health checks..."

    cd "$PROJECT_DIR"

    # Wait for containers to be healthy
    local max_attempts=30
    local attempt=0

    while [[ $attempt -lt $max_attempts ]]; do
        local healthy=true

        # Check backend health
        if ! docker compose -f "$DOCKER_COMPOSE_FILE" ps --format json | jq -r '.[].State' | grep -q "running"; then
            healthy=false
        fi

        if [[ "$healthy" == "true" ]]; then
            log_info "All services are healthy!"
            break
        fi

        attempt=$((attempt + 1))
        log_info "Waiting for services to be healthy... ($attempt/$max_attempts)"
        sleep 2
    done

    if [[ $attempt -eq $max_attempts ]]; then
        log_error "Health check failed - services may not be running properly"
        log_info "Check logs with: docker compose -f $DOCKER_COMPOSE_FILE logs"
        return 1
    fi
}

# ========================================================================
# Show Status
# ========================================================================

show_status() {
    log_info "Container status:"
    cd "$PROJECT_DIR"
    docker compose -f "$DOCKER_COMPOSE_FILE" ps
}

# ========================================================================
# Main Deployment Flow
# ========================================================================

main() {
    print_banner

    # Parse command-line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --no-build)
                SKIP_BUILD=true
                shift
                ;;
            --no-restart)
                SKIP_RESTART=true
                shift
                ;;
            -h|--help)
                cat << EOF
Usage: $0 [OPTIONS]

Options:
  --no-build     Skip building Docker images
  --no-restart   Skip restarting containers
  -h, --help     Show this help message

Environment Variables:
  BRANCH         Git branch to deploy (default: main)

EOF
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    # Run deployment steps
    preflight_checks
    update_from_github
    stop_containers
    build_images
    start_containers
    health_check
    show_status

    log_info "Deployment completed successfully!"
    log_info ""
    log_info "Access the application at:"
    log_info "  Frontend: http://192.168.68.56:8000"
    log_info "  Backend WebSocket: ws://192.168.68.56:8080"
    log_info ""
    log_info "View logs with: docker compose -f $DOCKER_COMPOSE_FILE logs -f"
}

# Run main function
main "$@"

