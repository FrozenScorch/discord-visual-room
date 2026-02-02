# Discord Visual Room - Frontend

Three.js frontend renderer for the Discord Visual Room project.

## Architecture: DUMB RENDERER

This frontend maintains NO state. All state comes from the backend via WebSocket SCENE_UPDATE messages.

## Features

- **Three.js 3D Rendering**: WebGL-powered 3D scene with shadows and lighting
- **WebSocket Client**: Auto-reconnecting WebSocket for real-time updates
- **Pre-loaded Furniture Meshes**: Four furniture types created with basic geometries
- **User Avatars**: 3D avatar representations with username labels
- **Speaking Indicators**: Visual feedback when users are talking
- **Responsive Design**: Adapts to different screen sizes
- **Orbit Controls**: User can rotate, pan, and zoom the camera

## Furniture Types

The frontend has these pre-loaded meshes (must match backend exactly):

1. **COMPUTER_DESK** - Large desk for competitive gaming
2. **COUCH_2_SEATER** - Two-seater couch for co-op gaming
3. **COUCH_SINGLE** - Single armchair for solo/AFK
4. **BAR_STOOL** - Tall stool for mobile/handheld gaming

## Development

### Installation

```bash
npm install
```

### Build Shared Types First

```bash
cd ../shared/types
npm run build
cd ../../frontend
```

### Run Development Server

```bash
npm run dev
```

The dev server runs on **port 8000** (as per project requirements).

Open http://localhost:8000 in your browser.

### Build for Production

```bash
npm run build
```

### Type Check

```bash
npm run typecheck
```

## Configuration

Create a `.env` file in the frontend directory:

```bash
cp .env.example .env
```

Edit `.env` to set the WebSocket URL:

```
VITE_WS_URL=ws://your-backend-server:8080
```

## File Structure

```
frontend/
├── src/
│   ├── main.ts                    # Entry point
│   ├── SceneRenderer.ts           # Three.js scene manager
│   ├── WSClient.ts                # WebSocket client
│   ├── types.ts                   # Frontend-specific types
│   ├── styles.css                 # Main stylesheet
│   ├── meshes/
│   │   ├── FurnitureFactory.ts    # Create furniture meshes
│   │   └── UserAvatar.ts          # User avatar class
│   └── utils/
│       └── sceneUtils.ts          # Helper functions
├── index.html                     # HTML container
├── vite.config.ts                 # Vite configuration
├── tsconfig.json                  # TypeScript configuration
└── package.json                   # Dependencies
```

## WebSocket Protocol

The frontend listens for these message types from the backend:

### SCENE_UPDATE

Main message type containing the complete scene state:

```typescript
{
  type: "SCENE_UPDATE",
  timestamp: number,
  payload: {
    version: string,
    timestamp: number,
    users: UserNode[],
    furniture: FurnitureNode[],
    room: RoomConfig
  }
}
```

On each SCENE_UPDATE, the frontend:
1. Updates room visualization
2. Creates/updates/removes furniture
3. Creates/updates/removes user avatars
4. Updates positions and rotations smoothly (lerp)

## Controls

- **Left click + drag**: Rotate camera
- **Right click + drag**: Pan camera
- **Scroll wheel**: Zoom in/out

## Dependencies

- **three**: ^0.160.0 - 3D rendering engine
- **@discord-visual-room/types**: workspace:* - Shared TypeScript types

## Dev Dependencies

- **@types/three**: ^0.160.0 - Three.js type definitions
- **typescript**: ^5.4.0 - TypeScript compiler
- **vite**: ^5.1.0 - Build tool and dev server
