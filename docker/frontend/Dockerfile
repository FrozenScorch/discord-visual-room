# ========================================================================
# Multi-stage Dockerfile for Discord Visual Room - Frontend (Production)
# ========================================================================
#
# This Dockerfile builds a production-ready Docker image for the Three.js
# frontend. It uses a multi-stage build for:
# 1. Minimal final image size (nginx:alpine ~40MB)
# 2. Optimized static assets (minified, tree-shaken)
# 3. Security best practices (alpine-based, non-root user)
#
# Stages:
#   1. builder    - Node.js build environment with Vite
#   2. runtime    - nginx:alpine serving static files
#
# Build:
#   docker build -f docker/frontend/Dockerfile -t discord-visual-room-frontend:latest frontend
#
# Run:
#   docker run --network host discord-visual-room-frontend:latest
#   (serves on port 8000 directly via host network)
#
# ========================================================================

# ========================================================================
# Stage 1: Builder
# ------------------------------------------------------------------------
# - Uses Node.js 20 LTS
# - Installs dependencies and builds with Vite
# - Output: /app/dist directory with optimized static files
# ========================================================================
FROM node:20-alpine AS builder

# Set working directory
WORKDIR /build

# Metadata labels
LABEL maintainer="Discord Visual Room Project"
LABEL description="Production build stage for Discord Visual Room frontend"
LABEL stage="builder"

# Install build dependencies
# - Python and g++: Required for compiling native modules (if any)
# - git: Required for workspace protocol dependencies
RUN apk add --no-cache \
    python3 \
    g++ \
    make \
    git && \
    rm -rf /var/cache/apk/*

# Copy package files first for better Docker layer caching
# These change less frequently than source code
COPY frontend-svelte/package*.json ./

# Check if this is part of a workspace (monorepo)
# If so, we need to handle shared dependencies
RUN if grep -q '"workspace:' package.json; then \
        echo "Workspace detected - installing pnpm for workspace support"; \
        npm install -g pnpm; \
    else \
        echo "Standalone project - using npm"; \
    fi

# Install dependencies
# --prefer-offline: Use cached packages when available
RUN npm install

# Copy source code
COPY frontend-svelte/tsconfig.json ./
COPY frontend-svelte/vite.config.ts ./
COPY frontend-svelte/svelte.config.js ./
COPY frontend-svelte/package.json ./
COPY frontend-svelte/src ./src
COPY frontend-svelte/static ./static

# Build argument for backend WebSocket URL
# This gets baked into the static files during build
ARG VITE_API_URL=ws://localhost:9050
ENV VITE_API_URL=${VITE_API_URL}

# ========================================================================
# Build the Application
# ------------------------------------------------------------------------
# - TypeScript compilation for type checking
# - Vite build for bundling and optimization
# - Output goes to /build/dist
# ========================================================================
RUN npm run build && \
    # Verify build output
    ls -lah build/ && \
    # Calculate file checksums for verification
    find build -type f -exec sha256sum {} \; > /build/dist.sha256

# ========================================================================
# Stage 2: Runtime
# ------------------------------------------------------------------------
# - Uses nginx:alpine for serving static files
# - Configured for Single Page Application (SPA) routing
# - Minimal attack surface and fast startup
# ========================================================================
FROM nginx:alpine

# Set working directory
WORKDIR /app

# Metadata labels
LABEL maintainer="Discord Visual Room Project"
LABEL description="Runtime image for Discord Visual Room frontend"
LABEL stage="runtime"

# Install runtime dependencies
# - curl: For health checks
# - tzdata: For timezone support
RUN apk add --no-cache \
    curl \
    tzdata && \
    # Clean up
    rm -rf /var/cache/apk/*

# Create non-root user for nginx worker processes
# Security best practice: nginx runs as unprivileged user
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    # Set ownership of nginx directories
    chown -R appuser:appgroup /var/cache/nginx /var/log/nginx /run

# Copy built files from builder stage
COPY --from=builder --chown=appuser:appgroup /build/build /usr/share/nginx/html

# Copy nginx configuration
COPY docker/frontend/nginx.conf /etc/nginx/nginx.conf
COPY docker/frontend/default.conf /etc/nginx/conf.d/default.conf

# Copy build checksums for verification
COPY --from=builder /build/dist.sha256 /app/dist.sha256

# Expose HTTP port
# Note: In host network mode, this maps directly to host port
EXPOSE 8000

# ========================================================================
# Health Check
# ------------------------------------------------------------------------
# Simple HTTP GET request to verify nginx is serving content
# ========================================================================
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8050/ || exit 1

# ========================================================================
# nginx Runtime Configuration
# ------------------------------------------------------------------------
# - nginx runs in foreground
# - Signals are handled for graceful shutdown
# ========================================================================

# Switch to non-root user
USER appuser

# Default command (nginx in foreground)
CMD ["nginx", "-g", "daemon off;"]

# ========================================================================
# Build Arguments (optional, for CI/CD customization)
# ========================================================================
# ARG BUILD_DATE
# ARG VCS_REF
# LABEL org.opencontainers.image.created=${BUILD_DATE}
# LABEL org.opencontainers.image.revision=${VCS_REF}
# ========================================================================

