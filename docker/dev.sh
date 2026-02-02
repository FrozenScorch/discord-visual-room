#!/bin/bash
# ========================================================================
# Development Start Script - Discord Visual Room
# ========================================================================
#
# This script starts the development environment with all necessary setup.
# It's a convenient wrapper around docker compose for development.
#
# Usage:
#   ./dev.sh [--build] [--clean]
#
# Options:
#   --build    Rebuild images before starting
#   --clean    Clean up volumes before starting
#
# ========================================================================

set -e

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Configuration
COMPOSE_FILE="docker/docker-compose.dev.yml"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

# ========================================================================
# Functions
# ========================================================================

print_banner() {
    cat << "EOF"
================================================================================
   ____                _    ____  ____  __  __
  |  _ \ _   _ _ __   / \  |  _ \|  _ \|  \/  |
  | |_) | | | | '_ \ / _ \ | | | | | | | |\/| |
  |  _ <| |_| | | | / ___ \| |_| | |_| | |  | |
  |_| \_\\__,_|_| |_/_/   \_\____/|____/|_|  |_|

              Discord Visual Room - Development Environment
================================================================================
EOF
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# ========================================================================
# Pre-flight Checks
# ========================================================================

check_environment() {
    log_info "Checking environment..."

    # Check if Docker is running
    if ! docker info &> /dev/null; then
        log_error "Docker is not running. Please start Docker Desktop."
        exit 1
    fi

    # Check if .env exists
    if [[ ! -f .env ]]; then
        log_warn ".env file not found. Creating from template..."
        cp docker/.env.example .env
        log_warn "Please edit .env with your Discord bot token and other configuration."
        log_warn "Press Ctrl+C to edit, or Enter to continue with defaults..."
        read -r
    fi

    # Check if required ports are available
    if netstat -an 2>/dev/null | grep -q ":8000.*LISTEN" || \
       netstat -an 2>/dev/null | grep -q ":8080.*LISTEN"; then
        log_warn "Ports 8000 or 8080 may already be in use."
        log_warn "If services fail to start, check for conflicting applications."
    fi

    log_info "Environment check complete"
}

# ========================================================================
# Build (if needed)
# ========================================================================

build_images() {
    if [[ "$REBUILD" == "true" ]]; then
        log_info "Rebuilding Docker images (no cache)..."
        docker compose -f "$COMPOSE_FILE" build --no-cache
    else
        log_info "Building Docker images..."
        docker compose -f "$COMPOSE_FILE" build
    fi
}

# ========================================================================
# Clean (if requested)
# ========================================================================

clean_volumes() {
    if [[ "$CLEAN" == "true" ]]; then
        log_info "Cleaning up volumes..."
        docker compose -f "$COMPOSE_FILE" down -v
        log_info "Cleanup complete"
    fi
}

# ========================================================================
# Start Services
# ========================================================================

start_services() {
    log_info "Starting development environment..."

    docker compose -f "$COMPOSE_FILE" up

    # Note: This command will block and show logs
    # Press Ctrl+C to stop all services
}

# ========================================================================
# Trap Handler
# ========================================================================

cleanup() {
    log_info ""
    log_info "Stopping development environment..."
    docker compose -f "$COMPOSE_FILE" down
    log_info "Development environment stopped"
    exit 0
}

trap cleanup SIGINT SIGTERM

# ========================================================================
# Main
# ========================================================================

main() {
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --build)
                REBUILD=true
                shift
                ;;
            --clean)
                CLEAN=true
                shift
                ;;
            -h|--help)
                cat << EOF
Usage: $0 [OPTIONS]

Start the Discord Visual Room development environment.

Options:
  --build    Rebuild images before starting
  --clean    Clean up volumes before starting
  -h, --help Show this help message

Services:
  Frontend:  http://localhost:8000
  Backend:   ws://localhost:8080
  Debugger:  localhost:5005 (Java debug port)

EOF
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    print_banner

    # Run setup
    check_environment
    clean_volumes
    build_images

    # Show access information
    echo ""
    log_info "Development environment is starting..."
    echo ""
    echo -e "${BLUE}Access URLs:${NC}"
    echo -e "  Frontend:        ${GREEN}http://localhost:8000${NC}"
    echo -e "  Backend WS:      ${GREEN}ws://localhost:8080${NC}"
    echo -e "  Java Debugger:   ${GREEN}localhost:5005${NC}"
    echo ""
    log_info "Press Ctrl+C to stop all services"
    echo ""

    # Start services (blocking)
    start_services
}

main "$@"

