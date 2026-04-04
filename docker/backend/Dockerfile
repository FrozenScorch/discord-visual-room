# ========================================================================
# Multi-stage Dockerfile for Discord Visual Room - Backend (Production)
# ========================================================================
#
# This Dockerfile builds a production-ready Docker image for the Scala/Akka
# backend service. It uses a multi-stage build to minimize the final image
# size while ensuring all build dependencies are available during compilation.
#
# Stages:
#   1. builder    - Compiles Scala code and creates fat JAR with sbt
#   2. runtime    - Creates minimal runtime image with JRE only
#
# Build:
#   docker build -f docker/backend/Dockerfile -t discord-visual-room-backend:latest backend/scala
#
# Run:
#   docker run -p 8080:8080 -e LLM_SERVER_URL=http://192.168.68.62:1234 discord-visual-room-backend:latest
#
# ========================================================================

# ========================================================================
# Stage 1: Builder
# ------------------------------------------------------------------------
# - Uses sbt image to compile Scala code
# - Creates assembly JAR with all dependencies
# - Caches dependencies for faster rebuilds
# ========================================================================
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.12.8_2.13.18 AS builder

# Set working directory
WORKDIR /build

# Metadata labels
LABEL maintainer="Discord Visual Room Project"
LABEL description="Production build stage for Discord Visual Room backend"
LABEL stage="builder"

# Copy sbt project files first for better caching
# These change less frequently than source code
COPY backend/scala/project/build.properties project/build.properties
COPY backend/scala/project/plugins.sbt project/plugins.sbt
COPY backend/scala/build.sbt build.sbt

# Pre-download dependencies (cached layer)
# This speeds up subsequent builds when dependencies haven't changed
RUN sbt update

# Copy source code
COPY backend/scala/src ./src

# ========================================================================
# Build the Application
# ------------------------------------------------------------------------
# - Compile the Scala code
# - Run tests (if they exist)
# - Create a fat JAR using sbt-assembly
#
# The assembly JAR includes all dependencies and can be run standalone.
# ========================================================================
RUN sbt clean assembly && \
    # Verify the JAR was created
    ls -lh target/scala-*//*.jar && \
    # Move to a known location
    mkdir -p /app && \
    cp target/scala-*/discord-visual-room-backend-*.jar /app/app.jar && \
    # Calculate JAR checksum for verification
    sha256sum /app/app.jar > /app/app.jar.sha256

# ========================================================================
# Stage 2: Runtime
# ------------------------------------------------------------------------
# - Minimal JRE image for running the application
# - Only contains the compiled JAR
# - Smaller attack surface and faster startup
# ========================================================================
FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Metadata labels
LABEL maintainer="Discord Visual Room Project"
LABEL description="Runtime image for Discord Visual Room backend"
LABEL stage="runtime"

# Install runtime dependencies
# - curl: for health checks
# - bash: for entrypoint script
RUN apk add --no-cache \
    curl \
    bash \
    tzdata && \
    # Clean up APK cache
    rm -rf /var/cache/apk/*

# Create non-root user for running the application
# Security best practice: don't run as root
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

# Copy the JAR from builder stage
COPY --from=builder --chown=appuser:appgroup /app/app.jar /app/app.jar
COPY --from=builder --chown=appuser:appgroup /app/app.jar.sha256 /app/app.jar.sha256

# Copy entrypoint script
COPY docker/backend/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh && \
    chown appuser:appgroup /app/entrypoint.sh

# Expose WebSocket port
# Port 8080 is used for WebSocket connections from the frontend
EXPOSE 8080

# ========================================================================
# Health Check
# ------------------------------------------------------------------------
# The application should expose a /health endpoint that returns 200 OK
# The health check runs every 30 seconds, with a 10 second timeout
# ========================================================================
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:9050/health || exit 1

# ========================================================================
# JVM Tuning for Containerized Environments
# ------------------------------------------------------------------------
# - Xmx: Maximum heap size (512MB by default, can be overridden)
# - Xms: Initial heap size (128MB by default)
# - XX:+UseContainerSupport: Aware of container memory limits
# - XX:MaxRAMPercentage: Use percentage of container memory
# - XX:+UseG1GC: G1 garbage collector for predictable pause times
# - Djava.security.egd: Use /dev/urandom for faster random number generation
# ========================================================================
ENV JVM_OPTS="-Xmx512m -Xms128m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Application configuration defaults
ENV WS_PORT=9050
ENV WS_HOST=0.0.0.0
ENV LOG_LEVEL=INFO

# Switch to non-root user
USER appuser

# Set the entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]

# Default command (can be overridden)
CMD ["java"]

# ========================================================================
# Build Arguments (optional, for CI/CD customization)
# ========================================================================
# ARG BUILD_DATE
# ARG VCS_REF
# LABEL org.opencontainers.image.created=${BUILD_DATE}
# LABEL org.opencontainers.image.revision=${VCS_REF}
# ========================================================================

