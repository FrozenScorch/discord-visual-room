# Discord Visual Room

A fault-tolerant 3D Discord Visualizer that renders voice channel activity as a cozy ambient 3D room. Designed as a "cute little window into the life of the server" for a side monitor.

## Architecture Philosophy

### Backend as Source of Truth
The frontend is a "dumb renderer." The Scala backend maintains the entire world state (Furniture, Positions, Users) and pushes a complete SceneGraph JSON to the frontend via WebSocket.

### Elastic Environment
The 3D scene dynamically adapts. Furniture spawns and despawns based on active user count (e.g., 4th user joins -> 4th chair appears with elastic scale-in animation).

### Graceful Degradation
The LLM is an enhancement, not a dependency. If the AI fails, times out, or hallucinates, the system seamlessly falls back to a deterministic mathematical layout.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Scala, Akka Typed Actors, Akka Streams, Discord4j |
| Frontend | Three.js 0.160, TypeScript, Vite |
| LLM | Local llama.cpp server (HTTP) |
| Protocol | WebSocket (SceneGraph JSON envelope) |

## Visual Features

### 3D Room
- Warm ambient lighting with ACES filmic tone mapping
- Floating dust particles with additive blending
- Circular floor rug with Discord-accent (#5865F2) glow edge
- Exponential fog for depth
- Hemisphere sky/ground color contrast

### Furniture (4 types)
| Asset | Visual | Use Case |
|-------|--------|----------|
| `COMPUTER_DESK` | Warm maple desk + glowing blue monitor + keyboard | Competitive gaming |
| `COUCH_2_SEATER` | Plush purple couch + sphere cushions | Casual co-op |
| `COUCH_SINGLE` | Teal armchair + puffy cushion | Solo/AFK |
| `BAR_STOOL` | Warm peach seat + chrome pole + footrest ring | Mobile/handheld |

### User Avatars
- Discord PFP billboard sprites (with fallback to colored initials)
- Pill-shaped username labels
- Activity labels showing current game/status (e.g., "Playing Minecraft")
- Elastic spawn-in animation (scale from 0 with overshoot)
- Shrink despawn animation
- Speaking: green (#43b581) pulse ring + gentle avatar bounce

### Camera
- Auto-orbit with gentle sway after 8s of idle
- Pauses on user interaction, resumes smoothly
- OrbitControls: drag to rotate, scroll to zoom, right-drag to pan

### UI
- Minimal ambient overlay: fading top bar + user count badge
- Connection status dot (green/yellow/red)
- Designed for side-monitor / always-on display

## Monorepo Structure

```
discord-visual-room/
├── backend/scala/      # Akka actor system, Discord4j, WebSocket server
├── frontend/           # Three.js renderer, Vite dev server
├── shared/
│   ├── types/          # Shared TypeScript interfaces (SceneGraph, etc.)
│   └── llm-service/    # LLM client + fallback layout generator
├── docker/             # Docker configurations
├── docs/               # Architecture and deployment docs
└── .github/            # CI/CD workflows
```

## Development

```bash
# Install dependencies
npm install

# Build shared types first
cd shared/types && npm run build && cd ../..

# Start all services
npm run dev

# Build frontend for production
cd frontend && npx vite build

# Run backend tests (from backend/scala/)
sbt test
```

## Configuration

### Frontend
Create `frontend/.env`:
```
VITE_WS_URL=ws://localhost:8080/ws
```

### Backend
Environment variables:
- `DISCORD_BOT_TOKEN` - Discord bot token
- `LLM_BASE_URL` - llama.cpp server URL (default: http://192.168.68.62:1234)

## Deployment

All deployments go through GitHub. See [Infrastructure Guide](./docker/INFRASTRUCTURE.md).

- Production server: 192.168.68.56
- Container network: `--network host`
- LLM Server: 192.168.68.62:1234
