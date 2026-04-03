# Svelte Rewrite Plan — Discord Visual Room Frontend

## Approach: Plain Svelte + Threlte (v8.x)

**No SvelteKit** — this is a single-page 3D viewer with no routing or SSR needs. Plain Svelte + Vite.

## Why Threlte over Raw Three.js

- Declarative Svelte components for scene objects (lights, meshes, cameras)
- Built-in `useTask` for animation loops (replaces manual `requestAnimationFrame`)
- Built-in `OrbitControls` via `@threlte/extras`
- Automatic cleanup on component unmount
- Svelte reactivity handles SceneGraph diffing (no manual object pooling needed)

## Component Architecture

```
frontend-svelte/
├── src/
│   ├── lib/
│   │   ├── stores/
│   │   │   ├── sceneGraph.ts          # Writable store for SceneGraph updates
│   │   │   └── connection.ts          # Writable store for WS connection state
│   │   ├── wsClient.ts                # WebSocket client class (triggers store updates)
│   │   ├── sceneUtils.ts              # Port: vector math, texture loading, sprite creation
│   │   ├── furnitureFactory.ts        # Port: creates Three.js geometry/material for furniture
│   │   └── types.ts                   # Re-exports from @discord-visual-room/types
│   ├── components/
│   │   ├── Scene.svelte               # Threlte Canvas, camera, lights, particles, floor, rug
│   │   ├── FurnitureItem.svelte       # Single furniture piece (receives FurnitureNode prop)
│   │   ├── UserAvatarItem.svelte      # Single user avatar (receives UserNode prop)
│   │   ├── FurnitureGroup.svelte      # {#each furniture} → renders FurnitureItem components
│   │   ├── UserGroup.svelte           # {#each users} → renders UserAvatarItem components
│   │   ├── ConnectionStatus.svelte    # Dot indicator (connecting/connected/error/disconnected)
│   │   └── TopBar.svelte              # Room name + user count badge
│   ├── App.svelte                     # Root: WS lifecycle, stores, layout
│   ├── main.ts                        # Mount App.svelte
│   ├── app.css                        # Port: dark ambient theme from styles.css
│   └── app.html                       # Vite entry HTML
├── static/                            # Fallback avatar textures
├── package.json
├── svelte.config.js
├── vite.config.ts                     # Port 8000
└── tsconfig.json
```

## What Gets Ported vs What Changes

### Direct Ports (logic stays the same, wrapped in Svelte)
| Current File | New Location | Notes |
|---|---|---|
| `WSClient.ts` (272 lines) | `lib/wsClient.ts` | Same class, but calls `sceneGraph.set()` on updates |
| `sceneUtils.ts` (290 lines) | `lib/sceneUtils.ts` | Mostly unchanged — pure Three.js utility functions |
| `FurnitureFactory.ts` (225 lines) | `lib/furnitureFactory.ts` | Returns Three.js objects, used by `FurnitureItem.svelte` |
| `UserAvatar.ts` (266 lines) | `UserAvatarItem.svelte` | Split into: factory logic in lib, rendering in component |
| `types.ts` (69 lines) | `lib/types.ts` | Same re-exports |
| `styles.css` (204 lines) | `app.css` | Minimal changes for Svelte conventions |

### Replaced by Svelte/Threlte
| Current File | Replaced By |
|---|---|
| `SceneRenderer.ts` (518 lines) | `Scene.svelte` + Threlte declarative scene |
| `main.ts` (171 lines — app orchestration) | `App.svelte` (reactive stores, not imperative class) |
| `index.html` (DOM elements) | `App.svelte` template |

### Dropped (not needed in Svelte)
| Current File | Reason |
|---|---|
| `logging/Logger.ts` (378 lines) | Svelte has `$app/environment` + simple console; over-engineered |
| `monitoring/ErrorHandler.ts` (365 lines) | Same — `window.onerror` + `onerror` event in `main.ts` suffices |
| `metrics/MetricsCollector.ts` (381 lines) | Not needed for local dev; add back if production metrics needed |

**Net result:** ~1,775 lines of vanilla TS → ~600-800 lines of Svelte + lib code

## Implementation Steps

### Step 1: Scaffold new Svelte project
- Create `frontend-svelte/` with `npm create svelte@latest` (skeleton template)
- Install deps: `@threlte/core`, `@threlte/extras`, `three`, `@discord-visual-room/types`
- Configure `vite.config.ts` with port 8000, path aliases
- Configure `svelte.config.js`

### Step 2: Port WebSocket client + stores
- Port `WSClient.ts` → `lib/wsClient.ts`
- Create `lib/stores/sceneGraph.ts` (writable store)
- Create `lib/stores/connection.ts` (writable store for ConnectionState)
- Wire WSClient to update stores on messages

### Step 3: Port Three.js utilities
- Port `sceneUtils.ts` → `lib/sceneUtils.ts` (vector math, texture loading, sprites)
- Port `FurnitureFactory.ts` → `lib/furnitureFactory.ts` (geometry + materials)

### Step 4: Build Threlte scene component
- `Scene.svelte` — Canvas, camera (position from current code), lights (ambient, directional, hemisphere, point), particle system, floor plane, rug circle
- Auto-orbit camera with `useTask` + idle timer
- Shadow mapping config

### Step 5: Build furniture + user components
- `FurnitureItem.svelte` — Receives `FurnitureNode`, calls `furnitureFactory.getMesh()`, handles position/rotation via threlte, spawn/despawn animation via `useTask`
- `UserAvatarItem.svelte` — Receives `UserNode`, creates PFP sprite + username label + speaking indicator + activity badge, spawn animation + speaking pulse via `useTask`
- `FurnitureGroup.svelte` — `{#each}` over `sceneGraph.furniture`
- `UserGroup.svelte` — `{#each}` over `sceneGraph.users`

### Step 6: Build UI overlay components
- `TopBar.svelte` — Room name from `sceneGraph.room.name`, user count from `sceneGraph.users.length`
- `ConnectionStatus.svelte` — Reactive dot color based on `connection` store

### Step 7: Wire up App.svelte
- `onMount`: create WSClient, connect, subscribe to stores
- `onDestroy`: disconnect, cleanup
- Layout: Scene (full viewport) + TopBar (overlay) + ConnectionStatus (overlay)

### Step 8: Port styles + static assets
- Port dark ambient CSS from `styles.css` to `app.css`
- Copy fallback avatar texture approach from `sceneUtils.ts`

### Step 9: Build verification
- `npm run build` passes (svelte-check + vite build)
- Dev server starts on port 8000

### Step 10: Replace old frontend
- Update root `package.json` workspace to point to `frontend-svelte/`
- Remove old `frontend/` directory
- Verify full build still works

## Branch Strategy

- Branch: `feat/svelte-frontend-rewrite`
- Single PR with all steps (they're interdependent — can't partially merge)
- After merge, CI verifies build passes

## Key Threlte Patterns Used

```svelte
<!-- Scene.svelte -->
<script lang="ts">
  import { Canvas } from '@threlte/core';
  import { OrbitControls } from '@threlte/extras';
  import { useTask } from '@threlte/core';
</script>

<Canvas shadows antialias toneMapped>
  <T.PerspectiveCamera makeDefault position={[0, 12, 16]} fov={55}>
    <OrbitControls enableDamping dampingFactor={0.05}
      maxPolarAngle={Math.PI / 2.2} target={[0, 0, 0]} />
  </T.PerspectiveCamera>

  <T.AmbientLight intensity={0.4} color="#b8a9d4" />
  <T.DirectionalLight position={[8, 12, 6]} intensity={0.8} castShadow />
  <T.HemisphereLight args={['#b8a9d4', '#2a1f3d', 0.3]} />

  <slot />
</Canvas>
```

```svelte
<!-- FurnitureItem.svelte -->
<script lang="ts">
  import { useTask } from '@threlte/core';
  import { getMesh } from '$lib/furnitureFactory';

  export let node: FurnitureNode;
  let mesh = getMesh(node.type);
  let scale = $state(0); // spawn animation

  useTask((delta) => {
    if (scale < 1) {
      scale = Math.min(1, scale + delta * 3);
      // elastic easing
    }
  });
</script>

<T.Mesh position={[node.position.x, node.position.y, node.position.z]}
  rotation={[node.rotation.x, node.rotation.y, node.rotation.z]}
  scale={scale} castShadow>
  {#if mesh.geometry}
    <T.BufferGeometry attach="geometry" {...mesh.geometry} />
  {/if}
  {#if mesh.material}
    <T.MeshStandardMaterial attach="material" {...mesh.material} />
  {/if}
</T.Mesh>
```

## Risk / Mitigation

| Risk | Mitigation |
|---|---|
| Threlte declarative API can't handle complex furniture meshes | Fall back to raw Three.js inside `onMount` for FurnitureFactory |
| Sprite/billboard rendering differs in threlte | Use raw `THREE.Sprite` via `useThrelte` ref access |
| Speaking indicator per-frame animation perf | Use `useTask` with throttled updates |
| WebSocket reconnection logic | Keep imperative WSClient class, only Svelte-ify the store updates |
