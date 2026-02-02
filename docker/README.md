# Docker Infrastructure - Discord Visual Room

This directory contains the complete Docker infrastructure for the Discord Visual Room project, including production and development configurations.

## Directory Structure

```
docker/
├── docker-compose.yml       # Production deployment
├── docker-compose.dev.yml   # Development with hot-reload
├── .env.example             # Environment configuration template
├── backend/
│   ├── Dockerfile           # Production backend image
│   ├── Dockerfile.dev       # Development backend image
│   └── entrypoint.sh        # Backend entrypoint script
└── frontend/
    ├── Dockerfile           # Production frontend image
    ├── Dockerfile.dev       # Development frontend image
    ├── nginx.conf           # Main nginx configuration
    └── default.conf         # Nginx server block configuration
```

## Quick Start

### Production Deployment

1. **Configure environment variables:**
   ```bash
   cp docker/.env.example .env
   # Edit .env with your configuration
   ```

2. **Build and start services:**
   ```bash
   docker compose -f docker/docker-compose.yml build
   docker compose -f docker/docker-compose.yml up -d
   ```

3. **View logs:**
   ```bash
   docker compose -f docker/docker-compose.yml logs -f
   ```

4. **Stop services:**
   ```bash
   docker compose -f docker/docker-compose.yml down
   ```

### Development Mode

1. **Start development environment:**
   ```bash
   docker compose -f docker/docker-compose.dev.yml up
   ```

2. **Access services:**
   - Frontend: http://localhost:8000
   - Backend WebSocket: ws://localhost:8080
   - Backend Debug: localhost:5005 (Java debug)

3. **Rebuild services:**
   ```bash
   docker compose -f docker/docker-compose.dev.yml build --no-cache
   ```

## Architecture

### Network Configuration

The project uses `--network host` mode as required by deployment constraints:

- **Backend** runs on `localhost:8080` (WebSocket)
- **Frontend** runs on `localhost:8000` (HTTP via nginx)
- **LLM Server** is at `http://192.168.68.62:1234` (Windows PC)

All service URLs use `localhost:PORT` instead of Docker service names.

### Services

#### Backend Service (Scala/Akka)

- **Base Image**: `eclipse-temurin:17-jre-alpine` (production)
- **Build Image**: `hseeberger/scala-sbt:jdk17-graalvm-ce-21.0_1.9.7`
- **Port**: 8080 (WebSocket)
- **Health Check**: `/health` endpoint

#### Frontend Service (Three.js)

- **Base Image**: `nginx:alpine` (production)
- **Build Image**: `node:20-alpine`
- **Port**: 8000 (HTTP)
- **Health Check**: HTTP GET on `/`

### Multi-stage Builds

Both backend and frontend use multi-stage builds for optimized production images:

1. **Builder Stage**: Compiles code and builds artifacts
2. **Runtime Stage**: Minimal image with only the application

This results in smaller final images:
- Backend: ~200MB (JRE + JAR)
- Frontend: ~40MB (nginx + static files)

## Environment Variables

### Required Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DISCORD_BOT_TOKEN` | Discord bot token | Required |
| `LLM_SERVER_URL` | LLM server URL | `http://192.168.68.62:1234` |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `WS_PORT` | Backend WebSocket port | `8080` |
| `VITE_API_URL` | Frontend API URL | `ws://localhost:8080` |
| `LOG_LEVEL` | Backend log level | `INFO` |
| `JVM_OPTS` | JVM options | Pre-configured |
| `ROOM_MAX_USERS` | Maximum room users | `10` |

## Health Checks

Both services include Docker health checks:

- **Backend**: `curl http://localhost:8080/health`
- **Frontend**: `curl http://localhost:8000/`

Check health status:
```bash
docker ps
docker inspect <container_id> | jq '.[0].State.Health'
```

## Troubleshooting

### View logs for a service:
```bash
docker compose -f docker/docker-compose.yml logs backend
docker compose -f docker/docker-compose.yml logs frontend
```

### Restart a service:
```bash
docker compose -f docker/docker-compose.yml restart backend
```

### Rebuild without cache:
```bash
docker compose -f docker/docker-compose.yml build --no-cache backend
```

### Enter a container for debugging:
```bash
docker compose -f docker/docker-compose.yml exec backend /bin/bash
docker compose -f docker/docker-compose.yml exec frontend /bin/sh
```

### Check resource usage:
```bash
docker stats
```

## Deployment to Server

1. **Push changes to GitHub:**
   ```bash
   git add .
   git commit -m "Update Docker infrastructure"
   git push origin main
   ```

2. **SSH to server and deploy:**
   ```bash
   ssh frozenscorch@192.168.68.56
   cd ~/discord-visual-room
   git pull
   docker compose -f docker/docker-compose.yml build
   docker compose -f docker/docker-compose.yml up -d
   ```

3. **Verify deployment:**
   ```bash
   docker compose -f docker/docker-compose.yml ps
   docker compose -f docker/docker-compose.yml logs -f
   ```

## Production Considerations

### Resource Limits

Production containers have resource limits:

- **Backend**: 1 CPU, 1GB RAM (max)
- **Frontend**: 0.5 CPU, 128MB RAM (max)

### Security

- Non-root user for application processes
- Minimal base images (Alpine Linux)
- Security headers in nginx
- No secrets in images (use environment variables)

### Restart Policies

- `unless-stopped`: Restart on failure, manual stops are respected
- Health checks before marking service as healthy

### Logging

- JSON file driver with size limits
- Max file size: 10MB
- Max files: 3 per service
- Total log size: ~30MB per service

## Development Tips

### Hot-Reload

In development mode (`docker-compose.dev.yml`):

- **Backend**: Uses `sbt run` which recompiles on file changes
- **Frontend**: Uses Vite dev server with HMR (Hot Module Replacement)

### Debugging

- **Backend**: Connect debugger to `localhost:5005`
- **Frontend**: Browser DevTools (source maps enabled)

### Workspace Dependencies

The project uses npm workspaces for shared types. The development Dockerfile
automatically detects and configures pnpm when a workspace is detected.

## File Details

### backend/Dockerfile

Multi-stage production build for Scala backend:
1. **builder**: Compiles Scala with sbt, creates assembly JAR
2. **runtime**: Minimal JRE image with only the JAR

### backend/Dockerfile.dev

Development image with:
- sbt included for compilation
- Source code mounted as volume
- JVM debug agent on port 5005

### backend/entrypoint.sh

Entrypoint script that:
- Parses environment variables
- Configures JVM options
- Sets up graceful shutdown
- Starts the application

### frontend/Dockerfile

Multi-stage production build for Three.js frontend:
1. **builder**: Node.js with Vite build
2. **runtime**: nginx:alpine serving static files

### frontend/Dockerfile.dev

Development image with:
- Vite dev server with HMR
- Source code mounted as volume
- No build step required

### frontend/nginx.conf

Main nginx configuration with:
- Gzip compression
- Security headers
- SPA routing support
- Static asset caching

### frontend/default.conf

Server block configuration with:
- Cache strategy for Vite output
- Health check endpoint
- SPA fallback routing

