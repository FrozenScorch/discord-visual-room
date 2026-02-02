#!/bin/bash
# ========================================================================
# Health Check Script - Discord Visual Room
# ========================================================================
#
# This script checks the health of all Discord Visual Room services.
# It can be used for monitoring or CI/CD pipelines.
#
# Usage:
#   ./health.sh [--verbose]
#
# Exit codes:
#   0 - All services healthy
#   1 - One or more services unhealthy
#
# ========================================================================

set -e

# Colors (disable for CI)
if [[ -t 1 ]] && [[ "${NO_COLOR:-}" != "1" ]]; then
    BLUE='\033[0;34m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    RED='\033[0;31m'
    NC='\033[0m'
else
    BLUE=''
    GREEN=''
    YELLOW=''
    RED=''
    NC=''
fi

# Configuration
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:8000}"
TIMEOUT="${CURL_TIMEOUT:-5}"

# Verbose flag
VERBOSE=false

# Health status tracking
BACKEND_HEALTHY=false
FRONTEND_HEALTHY=false

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

log_verbose() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $1"
    fi
}

# ========================================================================
# Health Checks
# ========================================================================

check_backend() {
    echo -n "Checking Backend (${BACKEND_URL})... "

    local response
    local http_code
    local response_time

    # Get response with time measurement
    start_time=$(date +%s%N)
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT" "${BACKEND_URL}/health" 2>&1) || true
    end_time=$(date +%s%N)
    response_time=$(( (end_time - start_time) / 1000000 ))  # Convert to milliseconds

    http_code="$response"

    log_verbose "Backend HTTP code: ${http_code}, Response time: ${response_time}ms"

    if [[ "$http_code" == "200" ]]; then
        echo -e "${GREEN}Healthy${NC} (${response_time}ms)"
        BACKEND_HEALTHY=true
    else
        echo -e "${RED}Unhealthy${NC} (HTTP ${http_code})"
    fi
}

check_frontend() {
    echo -n "Checking Frontend (${FRONTEND_URL})... "

    local response
    local http_code
    local response_time

    # Get response with time measurement
    start_time=$(date +%s%N)
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT" "${FRONTEND_URL}/" 2>&1) || true
    end_time=$(date +%s%N)
    response_time=$(( (end_time - start_time) / 1000000 ))  # Convert to milliseconds

    http_code="$response"

    log_verbose "Frontend HTTP code: ${http_code}, Response time: ${response_time}ms"

    if [[ "$http_code" == "200" ]]; then
        echo -e "${GREEN}Healthy${NC} (${response_time}ms)"
        FRONTEND_HEALTHY=true
    else
        echo -e "${RED}Unhealthy${NC} (HTTP ${http_code})"
    fi
}

check_docker_containers() {
    echo ""
    echo "Docker Containers:"

    local containers
    containers=$(docker ps --filter "name=discord-visual-room" --format "{{.Names}}|{{.Status}}|{{.Ports}}" 2>/dev/null || echo "")

    if [[ -z "$containers" ]]; then
        echo -e "  ${YELLOW}No Discord Visual Room containers running${NC}"
        return
    fi

    echo "$containers" | while IFS='|' read -r name status ports; do
        if [[ "$status" =~ healthy|Up ]]; then
            echo -e "  ${GREEN}✓${NC} ${name}"
            log_verbose "    Status: ${status}"
        else
            echo -e "  ${RED}✗${NC} ${name}"
            log_verbose "    Status: ${status}"
        fi
    done
}

check_llm_server() {
    echo ""
    echo -n "Checking LLM Server (192.168.68.62:1234)... "

    local response
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$TIMEOUT" "http://192.168.68.62:1234/health" 2>&1) || true

    if [[ "$response" == "200" ]] || [[ "$response" == "404" ]]; then
        # 404 is ok - server is up but may not have /health endpoint
        echo -e "${GREEN}Healthy${NC}"
    else
        echo -e "${YELLOW}Unknown${NC} (HTTP ${response:-none})"
        echo -e "  ${YELLOW}Note: LLM server may be unavailable for layout generation${NC}"
    fi
}

# ========================================================================
# Main
# ========================================================================

main() {
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -h|--help)
                cat << EOF
Usage: $0 [OPTIONS]

Check the health of Discord Visual Room services.

Options:
  -v, --verbose    Show verbose output
  -h, --help       Show this help message

Environment Variables:
  BACKEND_URL      Backend URL (default: http://localhost:8080)
  FRONTEND_URL     Frontend URL (default: http://localhost:8000)
  CURL_TIMEOUT     Request timeout in seconds (default: 5)
  NO_COLOR         Disable colors (set to 1)

Exit codes:
  0 - All services healthy
  1 - One or more services unhealthy

EOF
                exit 0
                ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    echo "======================================"
    echo "Discord Visual Room - Health Check"
    echo "======================================"
    echo ""

    # Run health checks
    check_backend
    check_frontend
    check_docker_containers
    check_llm_server

    echo ""
    echo "======================================"

    # Determine overall health
    if [[ "$BACKEND_HEALTHY" == "true" ]] && [[ "$FRONTEND_HEALTHY" == "true" ]]; then
        echo -e "${GREEN}Overall Status: Healthy${NC}"
        echo ""
        exit 0
    else
        echo -e "${RED}Overall Status: Unhealthy${NC}"
        echo ""

        if [[ "$BACKEND_HEALTHY" == "false" ]]; then
            echo -e "  ${RED}✗${NC} Backend is unhealthy"
            echo "    Check logs: docker compose -f docker/docker-compose.yml logs backend"
        fi

        if [[ "$FRONTEND_HEALTHY" == "false" ]]; then
            echo -e "  ${RED}✗${NC} Frontend is unhealthy"
            echo "    Check logs: docker compose -f docker/docker-compose.yml logs frontend"
        fi

        echo ""
        exit 1
    fi
}

main "$@"

