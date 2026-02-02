#!/bin/bash
# ========================================================================
# Entrypoint Script for Discord Visual Room - Backend
# ========================================================================
#
# This script serves as the entrypoint for the backend container.
# It handles:
# - Environment variable parsing
# - JVM option configuration
# - Health check setup
# - Graceful shutdown handling
#
# ========================================================================

set -e

# ========================================================================
# Configuration Functions
# ========================================================================

# Print startup banner
print_banner() {
    cat << "EOF"
================================================================================
  ____  ____  ____  __  __       ___  ____  __  __
 / ___||  _ \|  _ \|  \/  |___  / _ \|  _ \|  \/  |
 \___ \| | | | | | | |\/| |_ _| | | | | | | |\/| |
  ___) | |_| | |_| | |  | || | | |_| | |_| | |  | |
 |____/|____/|____/|_|  |_|___| \___/|____/|_|  |_|

              Discord Visual Room - Backend Service
================================================================================
EOF
}

# Print configuration
print_config() {
    echo "Configuration:"
    echo "----------------"
    echo "WS_HOST:       ${WS_HOST:-0.0.0.0}"
    echo "WS_PORT:       ${WS_PORT:-8080}"
    echo "LLM_SERVER_URL: ${LLM_SERVER_URL:-http://192.168.68.62:1234}"
    echo "LOG_LEVEL:     ${LOG_LEVEL:-INFO}"
    echo "JVM_OPTS:      ${JVM_OPTS}"
    echo "----------------"
}

# Parse JVM options and append defaults
parse_jvm_opts() {
    local opts="${JVM_OPTS:-}"

    # Add container support if not already present
    if [[ ! "$opts" =~ UseContainerSupport ]]; then
        opts="$opts -XX:+UseContainerSupport"
    fi

    # Add G1GC if not already present
    if [[ ! "$opts" =~ UseG1GC ]]; then
        opts="$opts -XX:+UseG1GC"
    fi

    # Add urandom for faster random number generation
    if [[ ! "$opts" =~ java.security.egd ]]; then
        opts="$opts -Djava.security.egd=file:/dev/./urandom"
    fi

    # Set heap size based on container limits if not specified
    if [[ ! "$opts" =~ -Xmx ]]; then
        # Use 75% of container memory, max 512MB
        opts="$opts -Xmx512m -XX:MaxRAMPercentage=75.0"
    fi

    echo "$opts"
}

# ========================================================================
# Main Application Start
# ========================================================================

main() {
    print_banner
    print_config

    # Check if JAR file exists
    if [[ ! -f /app/app.jar ]]; then
        echo "ERROR: Application JAR not found at /app/app.jar"
        echo "This container may be misconfigured."
        exit 1
    fi

    # Verify JAR checksum
    if [[ -f /app/app.jar.sha256 ]]; then
        echo "Verifying JAR checksum..."
        if ! sha256sum -c /app/app.jar.sha256 --status; then
            echo "WARNING: JAR checksum verification failed!"
            echo "The application file may be corrupted."
        else
            echo "JAR checksum verified."
        fi
    fi

    # Build JVM options
    JVM_OPTIONS=$(parse_jvm_opts)

    echo ""
    echo "Starting Discord Visual Room Backend..."
    echo "JVM Options: ${JVM_OPTIONS}"
    echo ""

    # ========================================================================
    # Trap signals for graceful shutdown
    # ------------------------------------------------------------------------
    # - SIGTERM: Standard container stop signal
    # - SIGINT: Interrupt signal (Ctrl+C)
    # ========================================================================

    # Function to handle shutdown
    shutdown_handler() {
        echo ""
        echo "Received shutdown signal, gracefully stopping..."
        # The JVM will handle SIGTERM for graceful shutdown
        exit 0
    }

    trap shutdown_handler SIGTERM SIGINT

    # ========================================================================
    # Start the Application
    # ------------------------------------------------------------------------
    # - java: Java executable
    # - $JVM_OPTIONS: Parsed JVM options from environment
    # - -jar /app/app.jar: The application JAR file
    # ========================================================================

    exec java ${JVM_OPTIONS} \
        -Dws.host="${WS_HOST:-0.0.0.0}" \
        -Dws.port="${WS_PORT:-8080}" \
        -Dllm.server.url="${LLM_SERVER_URL:-http://192.168.68.62:1234}" \
        -Dlog.level="${LOG_LEVEL:-INFO}" \
        -Ddiscord.bot.token="${DISCORD_BOT_TOKEN}" \
        -Droom.id="${ROOM_ID:-default-room}" \
        -Droom.name="${ROOM_NAME:-Discord Visual Room}" \
        -Droom.maxUsers="${ROOM_MAX_USERS:-10}" \
        -Droom.width="${ROOM_WIDTH:-10}" \
        -Droom.height="${ROOM_HEIGHT:-3}" \
        -Droom.depth="${ROOM_DEPTH:-10}" \
        -jar /app/app.jar
}

# Run main function
main "$@"

