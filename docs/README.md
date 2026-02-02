# Discord Visual Room Documentation

Welcome to the Discord Visual Room documentation portal. This comprehensive guide covers the architecture, implementation, deployment, and maintenance of the fault-tolerant 3D Discord Visualizer with elastic environment and LLM-enhanced layout.

## Quick Navigation

### Getting Started
- [Quick Start Guide](#quick-start)
- [Architecture Overview](architecture.md)
- [Development Setup](development.md)

### Core Documentation
- [Architecture](architecture.md) - System design, component interaction, and data flow
- [Backend](backend.md) - Scala/Akka backend implementation
- [Frontend](frontend.md) - Three.js renderer implementation
- [LLM Service](llm-service.md) - Layout generation with graceful fallback
- [API Reference](api.md) - WebSocket protocol and message formats

### Operations
- [Deployment Guide](deployment.md) - Docker setup and CI/CD pipeline
- [Troubleshooting](troubleshooting.md) - Common issues and solutions

## Quick Start

### Prerequisites

- **Node.js** >= 20.0.0
- **npm** >= 10.0.0
- **Scala** 2.13.12
- **Docker** & Docker Compose
- **llama.cpp server** (for LLM features)

### Installation

```bash
# Clone repository
git clone https://github.com/yourusername/discord-visual-room.git
cd discord-visual-room

# Install dependencies
npm install

# Build shared types
npm run build:shared

# Build frontend
npm run build:frontend
```

### Local Development

```bash
# Start all services (backend + frontend)
npm run dev
```

### Production Deployment

```bash
# Deploy via GitHub (recommended)
git add .
git commit -m "Deployment commit"
git push origin main

# Or deploy manually
docker compose -f docker/docker-compose.yml up -d
```

## Project Overview

Discord Visual Room is a sophisticated 3D visualization system for Discord voice channels that:

1. **Visualizes Voice Channel Users** - Displays Discord users as 3D avatars in real-time
2. **Elastic Furniture Layout** - Dynamically spawns furniture based on user count and activities
3. **AI-Enhanced Placement** - Uses LLM to intelligently assign furniture based on user activities
4. **Graceful Degradation** - Falls back to deterministic layout if AI fails
5. **Real-time Updates** - WebSocket-based streaming of scene state

### Key Features

- **Fault-Tolerant Architecture** - System never crashes, always renders something
- **Elastic Environment** - Furniture scales with active user count
- **Smart Furniture Assignment** - AI considers game types and user activities
- **Asset Dictionary Validation** - Strict validation prevents invalid furniture types
- **Dumb Renderer Pattern** - Frontend is stateless, backend is source of truth

## Architecture Philosophy

### Backend as Source of Truth
The frontend is a "dumb renderer" that displays whatever the backend sends. The Scala backend maintains the entire world state (Furniture, Positions, Users) and pushes complete SceneGraph JSON updates via WebSocket.

### Elastic Environment
The 3D scene is not static. Furniture spawns and despawns based on the number of active users:
- 4th user joins → 4th chair appears
- User leaves → furniture is removed
- Layout adapts to activity changes

### Graceful Degradation
The LLM is an enhancement, not a dependency:
- LLM fails → Fallback to deterministic grid layout
- LLM times out → System continues working
- LLM hallucinates → Validation rejects invalid types → Fallback
- System NEVER crashes

## Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Backend** | Scala 2.13.12, Akka Actors, Akka Streams, discorde4j | Real-time state management, Discord integration |
| **Frontend** | Three.js, TypeScript, Vite | 3D rendering, WebSocket client |
| **LLM Service** | TypeScript, llama.cpp HTTP client | Furniture layout generation |
| **Protocol** | WebSocket, JSON | Real-time scene updates |
| **Deployment** | Docker, Docker Compose, GitHub Actions | Container orchestration, CI/CD |

## Asset Dictionary

The LLM must strictly choose from these pre-loaded 3D meshes:

| Asset | Use Case | Description |
|-------|----------|-------------|
| `COMPUTER_DESK` | Competitive gaming | Large desk with monitor stand |
| `COUCH_2_SEATER` | Casual co-op | 2-person couch for shared gaming |
| `COUCH_SINGLE` | Solo/AFK | Single armchair for relaxed users |
| `BAR_STOOL` | Mobile/handheld | Tall stool for mobile gaming |

**Critical Rule**: If the LLM returns any other furniture type (e.g., "Beanbag Chair", "DESK", "couch"), it's a **validation failure** and triggers the fallback algorithm.

## System Components

```
┌─────────────────────────────────────────────────────────────┐
│                         Discord API                          │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                      Scala Backend                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  RoomActor   │──│ UserManager  │──│FurnitureMgr  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│         │                                     │              │
│         └─────────────────┬───────────────────┘              │
│                           ▼                                  │
│                    ┌─────────────┐                            │
│                    │SceneGraph   │                            │
│                    │Generator    │                            │
│                    └─────────────┘                            │
└───────────────────────────┬───────────────────────────────────┘
                            │
                    ┌───────▼────────┐
                    │   WebSocket    │
                    │   (port 8080)  │
                    └───────┬────────┘
                            │
┌───────────────────────────▼───────────────────────────────────┐
│                      Frontend (Three.js)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ WSClient     │──│SceneRenderer │──│FurnitureFctry│      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       LLM Service                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ LLMClient    │──│  Validator   │──│  Fallback    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

## Documentation Structure

### Core Documents

1. **[Architecture](architecture.md)** - Deep dive into system design
   - Component interaction diagrams
   - Data flow sequences
   - Technology choices rationale
   - Scaling considerations

2. **[Backend](backend.md)** - Scala/Akka implementation
   - Actor hierarchy and message types
   - State management patterns
   - Discord integration details
   - LLM client usage

3. **[Frontend](frontend.md)** - Three.js renderer
   - Scene structure and organization
   - WebSocket message handling
   - Furniture factory patterns
   - Animation system

4. **[LLM Service](llm-service.md)** - AI layout generation
   - Validation rules and constraints
   - Fallback algorithm details
   - Error handling strategies
   - Extension guidelines

5. **[API Reference](api.md)** - WebSocket protocol
   - Message formats and schemas
   - SceneGraph JSON structure
   - Event types and codes
   - Error handling

### Operational Documents

6. **[Deployment](deployment.md)** - Production setup
   - Docker configuration
   - Environment variables
   - CI/CD pipeline
   - Server deployment

7. **[Development](development.md)** - Local setup
   - Environment configuration
   - Running tests
   - Building from source
   - Debugging tips

8. **[Troubleshooting](troubleshooting.md)** - Problem solving
   - Common issues and fixes
   - Debug mode usage
   - Log analysis
   - Performance tuning

## Contributing

When contributing to the codebase:

1. **Read existing code** - Understand patterns before modifying
2. **Check tests** - Run tests before committing
3. **Document changes** - Update relevant docs
4. **Follow conventions** - Match existing code style
5. **Test thoroughly** - Ensure graceful degradation

## Support

For issues, questions, or contributions:

- **GitHub Issues**: [github.com/yourusername/discord-visual-room/issues](https://github.com/yourusername/discord-visual-room/issues)
- **Documentation**: See [Troubleshooting](troubleshooting.md) for common issues

## License

[Your License Here]

---

**Last Updated**: 2025-02-01
**Version**: 1.0.0
