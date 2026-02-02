# Discord Visual Room - Claude Instructions

## Project Overview

Building a fault-tolerant 3D Discord Visualizer with:
- **Backend**: Scala, Akka Actors, Akka Streams, discorde4j
- **Frontend**: Three.js (dumb renderer)
- **LLM**: Local llama.cpp for layout generation with fallback

## Critical Constraints

### Asset Dictionary (MUST USE EXACTLY)
The frontend only has these meshes. LLM must choose ONLY from:
- `COMPUTER_DESK` - Competitive gaming
- `COUCH_2_SEATER` - Casual co-op
- `COUCH_SINGLE` - Solo/AFK
- `BAR_STOOL` - Mobile/handheld

If LLM returns anything else (e.g., "Beanbag Chair"), it's a **validation failure**.

### Graceful Degradation
- LLM failure → `generateLinearLayout(count)` fallback
- No crashes, ever
- System must always render something

## Architecture Decisions

### Backend as Source of Truth
- Frontend = dumb renderer
- Backend maintains entire world state
- WebSocket pushes complete SceneGraph JSON

### Elastic Environment
- Furniture spawns/despawns based on active user count
- 4th user joins → 4th chair appears
- No static furniture counts

## SceneGraph JSON Schema (Tentative)

```typescript
interface SceneGraph {
  version: string;
  timestamp: number;
  users: UserNode[];
  furniture: FurnitureNode[];
  room: RoomConfig;
}

interface FurnitureNode {
  id: string;
  type: "COMPUTER_DESK" | "COUCH_2_SEATER" | "COUCH_SINGLE" | "BAR_STOOL";
  position: { x: number; y: number; z: number };
  rotation: { x: number; y: number; z: number };
  assignedUser?: string; // userId if occupied
}

interface UserNode {
  id: string; // Discord user ID
  username: string;
  avatar: string;
  currentPosition: { x: number; y: number; z: number };
  activity?: {
    name: string; // Game name
    type: "PLAYING" | "STREAMING" | "LISTENING" | "WATCHING"
  };
}
```

## Parallel Work with Claude Orchestrator

Use `/spawn-worktree` to spawn parallel sessions for independent components.

## LLM Prompt Template

```
You are a furniture layout assistant. Given these users and their activities, assign ONE furniture type from the EXACT list below to each user.

VALID FURNITURE TYPES (use ONLY these):
- COMPUTER_DESK: For competitive gaming
- COUCH_2_SEATER: For casual co-op games
- COUCH_SINGLE: For solo/AFK users
- BAR_STOOL: For mobile/handheld games

Users and activities:
${usersJson}

Return ONLY valid JSON array of assignments:
[{"userId": "...", "furniture": "COMPUTER_DESK"}, ...]
```

## Deployment

- All deployments via GitHub
- Server: 192.168.68.56
- Container network: `--network host`
- LLM Server: http://192.168.68.62:1234
