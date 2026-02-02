# Docker Infrastructure Summary - Discord Visual Room

This document provides a complete overview of the Docker infrastructure for the Discord Visual Room project.

## Directory Layout

```
discord-visual-room/
├── docker/
│   ├── docker-compose.yml         # Production Docker Compose
│   ├── docker-compose.dev.yml     # Development Docker Compose
│   ├── .env.example               # Environment template
│   ├── .gitignore                 # Docker-specific gitignore
│   ├── README.md                  # Docker documentation
│   ├── deploy.sh                  # Production deployment script
│   ├── dev.sh                     # Development startup script
│   ├── health.sh                  # Health check script
│   ├── Makefile                   # Convenient commands
│   ├── backend/
│   │   ├── Dockerfile             # Production backend image
│   │   ├── Dockerfile.dev         # Development backend image
│   │   └── entrypoint.sh          # Backend entrypoint
│   └── frontend/
│       ├── Dockerfile             # Production frontend image
│       ├── Dockerfile.dev         # Development frontend image
│       ├── nginx.conf             # Main nginx config
│       └── default.conf           # Nginx server block
├── backend/scala/
│   ├── .dockerignore              # Backend build exclusions
│   ├── build.sbt                  # Scala build config
│   └── src/                       # Scala source code
└── frontend/
    ├── .dockerignore              # Frontend build exclusions
    ├── package.json               # Node.js dependencies
    ├── vite.config.ts             # Vite build config
    └── src/                       # TypeScript source code
```

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Host Network (192.168.68.56)                 │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Backend Container                          │  │
│  │  Image: discord-visual-room-backend:latest                   │  │
│  │  Runtime: eclipse-temurin:17-jre-alpine                      │  │
│  │  Port: 8080 (WebSocket)                                      │  │
│  │                                                               │  │
│  │  - Scala/Akka Application                                     │  │
│  │  - Discord Bot Integration                                    │  │
│  │  - WebSocket Server                                           │  │
│  │  - LLM Client (connects to 192.168.68.62:1234)              │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              ▲                                       │
│                              │ WebSocket                             │
│                              ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                   Frontend Container                          │  │
│  │  Image: discord-visual-room-frontend:latest                  │  │
│  │  Runtime: nginx:alpine                                       │  │
│  │  Port: 8000 (HTTP)                                           │  │
│  │                                                               │  │
│  │  - Static Files (Three.js SPA)                                │  │
│  │  - nginx Server                                               │  │
│  │  - WebSocket Client (connects to localhost:8080)             │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  External Services:                                                 │
│  - LLM Server: http://192.168.68.62:1234 (Windows PC)              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Network Configuration

The project uses `--network host` mode as required by deployment constraints.

**Implications:**
- All services communicate via `localhost:PORT`
- No Docker bridge networks are created
- Port conflicts must be managed manually
- All service URLs in environment variables use `localhost`

**Port Assignments:**
- Backend WebSocket: `localhost:8080`
- Frontend HTTP: `localhost:8000`
- Java Debug (dev only): `localhost:5005`

## Service Details

### Backend (Scala/Akka)

**Technology Stack:**
- Language: Scala 2.13.12
- Build Tool: sbt 1.9.7
- Frameworks: Akka Actors, Akka HTTP, Discord4J
- JRE: Eclipse Temurin 17 (Alpine Linux)

**Production Image:**
- Base: `eclipse-temurin:17-jre-alpine`
- Size: ~200MB
- Contains: JRE + application JAR

**Development Image:**
- Base: `hseeberger/scala-sbt:jdk17-graalvm-ce-21.0_1.9.7`
- Contains: JDK + sbt for live compilation
- Debug port: 5005

**Environment Variables:**
| Variable | Description | Default |
|----------|-------------|---------|
| `WS_PORT` | WebSocket port | `8080` |
| `WS_HOST` | Bind address | `0.0.0.0` |
| `LLM_SERVER_URL` | LLM endpoint | `http://192.168.68.62:1234` |
| `LLM_TIMEOUT` | Request timeout | `30000` |
| `DISCORD_BOT_TOKEN` | Bot token | Required |
| `LOG_LEVEL` | Log level | `INFO` |
| `JVM_OPTS` | JVM options | Pre-configured |

### Frontend (Three.js)

**Technology Stack:**
- Language: TypeScript 5.4
- Build Tool: Vite 5.1
- Framework: Three.js 0.160
- Runtime: nginx (Alpine Linux)

**Production Image:**
- Base: `nginx:alpine`
- Size: ~40MB
- Contains: nginx + optimized static files

**Development Image:**
- Base: `node:20-alpine`
- Contains: Node.js + Vite dev server (HMR enabled)

**Environment Variables:**
| Variable | Description | Default |
|----------|-------------|---------|
| `VITE_API_URL` | Backend WebSocket URL | `ws://localhost:8080` |
| `VITE_PORT` | Dev server port | `8000` |
| `VITE_HOST` | Dev server bind address | `0.0.0.0` |

## Build Process

### Backend Production Build

1. **Builder Stage:**
   ```
   FROM hseeberger/scala-sbt:jdk17-graalvm-ce-21.0_1.9.7
   ↓
   Copy project files → sbt update (download dependencies)
   ↓
   Copy source code → sbt clean assembly
   ↓
   Output: /app/app.jar (fat JAR with all dependencies)
   ```

2. **Runtime Stage:**
   ```
   FROM eclipse-temurin:17-jre-alpine
   ↓
   Install curl/bash → Copy JAR from builder
   ↓
   Create non-root user → Set entrypoint
   ↓
   Output: Production-ready image
   ```

### Frontend Production Build

1. **Builder Stage:**
   ```
   FROM node:20-alpine
   ↓
   Install build tools → npm ci
   ↓
   Copy source → npm run build
   ↓
   Output: /build/dist (optimized static files)
   ```

2. **Runtime Stage:**
   ```
   FROM nginx:alpine
   ↓
   Install curl → Copy dist from builder
   ↓
   Copy nginx config → Create non-root user
   ↓
   Output: Production-ready image
   ```

## Configuration Files

### nginx.conf

Main nginx configuration with:
- Worker process auto-scaling
- Gzip compression for JS/CSS/JSON
- Security headers (X-Frame-Options, CSP)
- Error logging
- MIME type handling

### default.conf

Server block configuration with:
- Cache strategy (hashed assets cached 1 year)
- Health check endpoint at `/health`
- SPA fallback routing (all routes → index.html)
- Hidden file protection

### entrypoint.sh

Backend entrypoint script:
- Parses environment variables
- Builds JVM options
- Handles graceful shutdown
- Sets system properties
- Executes JAR

## Deployment Workflow

### Local Development

```bash
# Option 1: Use the dev script
./docker/dev.sh

# Option 2: Use docker-compose directly
docker compose -f docker/docker-compose.dev.yml up

# Option 3: Use Make
make dev
```

### Production Deployment

```bash
# 1. Commit and push to GitHub
git add .
git commit -m "Update"
git push origin main

# 2. Deploy to server (from local machine)
make deploy

# 3. Or deploy from server directly
ssh frozenscorch@192.168.68.56
cd ~/discord-visual-room
./docker/deploy.sh
```

## Quick Commands Reference

```bash
# Development
make dev              # Start dev environment
make dev-build        # Build dev images
make dev-down         # Stop dev environment

# Production
make build            # Build prod images
make up               # Start prod environment
make down             # Stop prod environment
make restart          # Restart services

# Logs & Debug
make logs             # View all logs
make logs-be          # View backend logs
make logs-fe          # View frontend logs
make shell-be         # Shell into backend
make shell-fe         # Shell into frontend

# Utilities
make health           # Check service health
make clean            # Remove containers/volumes/images
make env              # Create .env from template
```

## Health Checks

Both services include Docker health checks:

**Backend:**
```bash
curl http://localhost:8080/health
```

**Frontend:**
```bash
curl http://localhost:8000/
```

View health status:
```bash
docker ps
docker inspect <container_id> | jq '.[0].State.Health'
```

## Resource Limits

Production containers have these resource limits:

| Service | CPU Limit | Memory Limit | CPU Reservation | Memory Reservation |
|---------|-----------|--------------|-----------------|-------------------|
| Backend | 1.0       | 1GB          | 0.25            | 256MB             |
| Frontend| 0.5       | 128MB        | 0.1             | 32MB              |

## Security Considerations

1. **Non-root User**: Both services run as non-privileged users
2. **Minimal Images**: Alpine Linux reduces attack surface
3. **No Secrets in Images**: All secrets via environment variables
4. **Security Headers**: nginx includes security headers
5. **Health Checks**: Ensures services are running properly

## Troubleshooting

### Common Issues

**Port already in use:**
```bash
# Check what's using the port
netstat -an | grep :8080
lsof -i :8080

# Stop conflicting service
```

**Container won't start:**
```bash
# Check logs
docker compose -f docker/docker-compose.yml logs backend

# Check health
docker compose -f docker/docker-compose.yml ps
```

**Build fails:**
```bash
# Clean build
docker compose -f docker/docker-compose.yml build --no-cache
```

**Cannot connect to backend:**
```bash
# Verify backend is running
curl http://localhost:8080/health

# Check frontend is using correct URL
echo $VITE_API_URL
```

## File Sizes (Approximate)

| Image | Size | Description |
|-------|------|-------------|
| Backend (prod) | ~200MB | JRE + JAR |
| Backend (dev) | ~1.5GB | JDK + sbt + source |
| Frontend (prod) | ~40MB | nginx + static files |
| Frontend (dev) | ~500MB | Node.js + dev server |

## CI/CD Integration

The infrastructure is designed for GitHub Actions deployment:

```yaml
# .github/workflows/deploy.yml
- name: Deploy to server
  run: |
    ssh frozenscorch@192.168.68.56 \
      "cd ~/discord-visual-room && ./docker/deploy.sh"
```

## Maintenance

**Regular maintenance tasks:**

```bash
# Prune unused Docker resources
make prune

# Update base images
docker pull eclipse-temurin:17-jre-alpine
docker pull nginx:alpine
docker pull hseeberger/scala-sbt:jdk17-graalvm-ce-21.0_1.9.7

# Rebuild with updated base images
docker compose -f docker/docker-compose.yml build --no-cache
```

## Future Enhancements

Potential improvements for the Docker infrastructure:

1. **Docker Swarm mode** for multi-host deployment
2. **Kubernetes manifests** for orchestration
3. **Prometheus metrics** endpoints
4. **Grafana dashboards** for monitoring
5. **Automated backup** of volumes
6. **Blue-green deployment** support
7. **Automatic SSL** with Let's Encrypt
8. **Rate limiting** in nginx

