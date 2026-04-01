# Discord Visual Room - Claude Instructions

## Frontend Framework

**Svelte is the preferred frontend framework.** Do not use React, Vue, or Angular unless explicitly requested.
**Three.js is the preferred library for 3D graphics.** Use `threlte` or raw Three.js within Svelte components.

> **Note:** The current frontend is vanilla TypeScript + Three.js (no framework). Any new frontend work or rewrites MUST use Svelte.

## Project Overview

Building a fault-tolerant 3D Discord Visualizer with:
- **Backend**: Scala, Akka Actors, Akka Streams, Discord4j
- **Frontend**: Svelte + Three.js (dumb renderer) with cozy ambient aesthetic
- **LLM**: Local llama.cpp for layout generation with fallback

## Current State (as of PR #2 merge)

### What Works End-to-End
- Discord4j listens for voice channel events (join/leave/activity/speaking)
- RoomActor coordinates UserManager + FurnitureManager via message adapters
- LLMClient calls llama.cpp with fallback to deterministic `LayoutGenerator`
- WebSocket pushes SceneGraph JSON via `Source.actorRef.preMaterialize()` pattern
- Frontend renders furniture, user avatars, speaking state, activity labels
- Elastic spawn/despawn with scale animations
- Auto-orbit camera with idle detection
- Warm ambient lighting, floating particles, circular rug with accent glow
- Minimal UI (fading top bar + user count badge)

### Tests
- **All tests are functional** - zero mocks, zero scalacheck
- FurnitureManagerSpec uses real LLMClient pointed at unreachable `127.0.0.1:1`
- UserManagerSpec uses real UserManager actor with probe assertions
- DomainModelsSpec uses pure functional assertions on real domain objects

## Critical Constraints

### Asset Dictionary (MUST USE EXACTLY)
The frontend only has these meshes. LLM must choose ONLY from:
- `COMPUTER_DESK` - Competitive gaming (warm maple desk + glowing monitor)
- `COUCH_2_SEATER` - Casual co-op (plush purple couch + sphere cushions)
- `COUCH_SINGLE` - Solo/AFK (teal armchair + puffy cushion)
- `BAR_STOOL` - Mobile/handheld (warm peach stool + chrome pole)

If LLM returns anything else (e.g., "Beanbag Chair"), it's a **validation failure**.

### Graceful Degradation
- LLM failure -> `generateLinearLayout(count)` fallback
- No crashes, ever
- System must always render something

## Architecture Decisions

### Backend as Source of Truth
- Frontend = dumb renderer
- Backend maintains entire world state
- WebSocket pushes complete SceneGraph JSON

### Elastic Environment
- Furniture spawns/despawns based on active user count
- 4th user joins -> 4th chair appears
- No static furniture counts

### Message Flow
- Backend wraps SceneGraph in `{"type":"SCENE_UPDATE","timestamp":...,"payload":{SceneGraph}}`
- Frontend `WSClient` handles both wrapped and raw SceneGraph messages
- `SceneRenderer` receives parsed SceneGraph and diffs against current scene

### Key Backend Patterns
- `RoomActor` uses `messageAdapter` for inter-actor communication (NOT `context.ask`)
- `UserJoined`/`UserLeft` are fire-and-forget (no `replyTo`) to avoid null NPE
- `GetCurrentSceneGraph(replyTo)` replies with `SceneGraphUpdate`
- `SubscribeToSceneUpdates`/`UnsubscribeFromSceneUpdates` for WebSocket clients
- LLMClient uses `java.net.http.HttpClient` (NOT sttp)

## SceneGraph JSON Schema

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
  id: string;
  username: string;
  displayName: string;
  avatar: string;
  position: { x: number; y: number; z: number };
  rotation: { x: number; y: number; z: number };
  activity?: { name: string; type: "PLAYING" | "STREAMING" | "LISTENING" | "WATCHING" | "COMPETING" };
  isSpeaking: boolean;
  currentFurnitureId?: string;
}
```

## Remaining TODO for Future Agents

### High Priority
1. **Occupied furniture glow** - When `furniture.assignedUser` is set, the seat cushion should emit a soft lavender glow (`0x9966ff`, emissive intensity 0.25). Requires deep-cloning materials in `FurnitureFactory.getMesh()` so each instance has independent materials (currently Three.js `.clone()` shares material refs).
2. **CORS proxy for Discord PFPs** - Discord CDN blocks direct browser texture loads. Add `https://images.weserv.nl/?url=<encoded>` as primary attempt in `sceneUtils.ts:loadTexture()`, fall back to `createDefaultAvatarTexture()`. Currently PFPs only show for same-origin URLs.
3. **Room/channel name in UI** - The `SceneGraph.room.name` field exists but the top bar hardcodes "Discord Visual Room". Wire it to `#room-badge` in `main.ts:updateUIOverlay()`.

### Medium Priority
4. **Delta updates** - Currently backend sends full SceneGraph on every change. Add delta/diff mode to reduce WebSocket payload.
5. **Occupied animation** - When a user sits at furniture, their avatar should lerp to a "seated" position offset relative to the furniture.
6. **Body mesh** - Users are currently sprite-only (billboard PFP). Consider adding a `CapsuleGeometry` body below the PFP for more 3D presence.
7. **Hint text fade** - Add a "drag to orbit / scroll to zoom" hint that fades after 10s on first load.

### Low Priority
8. **Object pooling** - Reuse disposed furniture meshes instead of recreating
9. **LOD system** - Reduce geometry detail for distant objects
10. **Sound indicators** - Subtle audio pings on user join/leave (opt-in)

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
