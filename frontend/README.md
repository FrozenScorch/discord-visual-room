# Discord Visual Room - Frontend

Three.js frontend renderer for the Discord Visual Room project. Renders a cozy ambient 3D room that visualizes Discord voice channel activity.

## Architecture: DUMB RENDERER

This frontend maintains NO state. All state comes from the backend via WebSocket `SCENE_UPDATE` messages. The renderer diffs the incoming SceneGraph against current scene objects and applies changes.

## Visual Design

Designed as an ambient side-monitor display - "a cute little window into the life of the server."

### Aesthetic
- **Lighting**: Warm ACES filmic tone mapping, ambient + directional key + cool fill + center point glow + hemisphere sky/ground
- **Room**: Circular floor with dark purple rug, Discord-accent (#5865F2) glow ring edge, exponential fog
- **Particles**: 200 floating warm-white dust motes with additive blending, gentle drift
- **Camera**: Auto-orbit with sway after 8s idle; pauses on interaction

### Furniture Types (4 meshes)
1. **COMPUTER_DESK** - Warm maple desk (0xD4A574), glowing blue monitor (emissive 0x4488CC), keyboard tray
2. **COUCH_2_SEATER** - Soft purple frame (0x7B68AE), sphere cushions, matching base
3. **COUCH_SINGLE** - Teal armchair (0x5BA4B5), puffy cushion, armrests
4. **BAR_STOOL** - Warm peach seat (0xE8A87C), chrome pole (0xCCCCCC), footrest ring

### User Avatars
- Discord PFP as billboard sprite (always faces camera)
- Fallback: pastel gradient circle with username initials
- Pill-shaped username label (semi-transparent dark background)
- Activity label in Discord blurple pill (shows game/status)
- **Spawn**: Elastic scale from 0 with overshoot (0.4s)
- **Despawn**: Scale to 0 (0.3s), then dispose
- **Speaking**: Discord green (#43b581) torus ring pulse + gentle avatar bounce

### UI Overlay
- Minimal top bar with room title + connection status dot (fades after 3s connected)
- User count badge (bottom-left, glassmorphism pill)
- No legends, no controls panel - clean ambient look

## File Structure

```
frontend/
├── src/
│   ├── main.ts                    # Entry point, UI overlay wiring
│   ├── SceneRenderer.ts           # Three.js scene, lighting, camera, particles, render loop
│   ├── WSClient.ts                # WebSocket client with auto-reconnect
│   ├── types.ts                   # Re-exports shared types + frontend-specific types
│   ├── styles.css                 # Minimal ambient UI styles
│   ├── meshes/
│   │   ├── FurnitureFactory.ts    # Procedural furniture geometry + materials
│   │   └── UserAvatar.ts          # User avatar with PFP, animations, labels
│   ├── utils/
│   │   └── sceneUtils.ts          # Text sprites, texture loading, speaking ring
│   ├── logging/                   # Logger utility
│   ├── metrics/                   # Metrics collector
│   └── monitoring/                # Error handler
├── index.html                     # Minimal HTML shell
├── vite.config.ts                 # Vite config (port 8000)
├── tsconfig.json                  # TypeScript config
└── package.json                   # Dependencies
```

## Development

```bash
npm install
npm run dev          # Dev server on port 8000
npm run build        # Production build
npm run typecheck    # TypeScript check (strict)
```

### Environment

Create `.env`:
```
VITE_WS_URL=ws://localhost:8080/ws
```

Both `VITE_WS_URL` and `VITE_FRONTEND_WS_URL` are supported. The URL must end with `/ws` to match the backend route (auto-appended if missing).

## WebSocket Protocol

### SCENE_UPDATE (main message type)

```json
{
  "type": "SCENE_UPDATE",
  "timestamp": 1234567890,
  "payload": {
    "version": "1.0",
    "timestamp": 1234567890,
    "users": [...],
    "furniture": [...],
    "room": { "name": "...", "dimensions": {...} }
  }
}
```

The frontend also accepts raw SceneGraph JSON (without the wrapper) for backwards compatibility.

### On each SCENE_UPDATE
1. Diff furniture: create new, remove missing (with despawn animation), lerp existing
2. Diff users: create new avatars (with spawn animation), despawn missing, update data
3. Update room boundary wireframe
4. Update user count badge

## Dependencies

- **three** ^0.160.0 - 3D rendering
- **@discord-visual-room/types** workspace:* - Shared TypeScript types
- **vite** ^5.1.0 - Build tool
- **typescript** ^5.4.0 - TypeScript compiler
