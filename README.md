# Discord Visual Room

A highly sophisticated, fault-tolerant 3D Discord Visualizer with elastic environment and LLM-enhanced layout.

## Architecture Philosophy

### Backend as Source of Truth
The frontend is a "dumb renderer." The Scala backend maintains the entire world state (Furniture, Positions, Users) and pushes a complete SceneGraph JSON to the frontend via WebSocket.

### Elastic Environment
The 3D scene is not static. Furniture spawns and despawns based on the number of active users (e.g., if a 4th user joins, the system instantiates a 4th chair).

### Graceful Degradation
The LLM is an enhancement, not a dependency. If the AI fails, times out, or hallucinates, the system seamlessly falls back to a deterministic mathematical layout.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Scala, Akka Actors, Akka Streams, discorde4j |
| Frontend | Three.js, TypeScript |
| LLM | Local llama.cpp server (HTTP) |
| Protocol | WebSocket (SceneGraph JSON) |

## Asset Dictionary (Frontend Meshes)

The LLM must strictly choose from these pre-loaded 3D meshes:

| Asset | Use Case |
|-------|----------|
| `COMPUTER_DESK` | Competitive gaming |
| `COUCH_2_SEATER` | Casual co-op |
| `COUCH_SINGLE` | Solo/AFK |
| `BAR_STOOL` | Mobile/handheld games |

## Features

### Feature 1: Elastic Layout Algorithm
- Trigger: User joins Voice Channel or starts Game Activity
- LLM generates furniture assignment based on users and game types
- Validation: Backend parses LLM response, rejects invalid furniture types
- Fallback: `generateLinearLayout(count)` places COMPUTER_DESK nodes in a grid

### Feature 2: Dynamic Scene Graph Generation
- Backend constructs JSON object representing the room
- Calculates transforms, rotations, and positions
- Pushes complete state via WebSocket

## Monorepo Structure

```
discord-visual-room/
├── backend/           # Scala/Akka backend
├── frontend/          # Three.js renderer
├── shared/            # Shared types and protocols
├── docker/            # Docker configurations
└── .github/           # CI/CD workflows
```

## Development

```bash
# Install dependencies
npm install

# Start all services
npm run dev

# Build
npm run build

# Test
npm test
```

## Deployment

All deployments go through GitHub. See [Deployment Guide](./docs/DEPLOYMENT.md).
