<script lang="ts">
  import { Canvas, T, useThrelte, useTask } from '@threlte/core';
  import { OrbitControls } from '@threlte/extras';
  import * as THREE from 'three';
  import SceneSetup from './SceneSetup.svelte';
  import ParticleSystem from './ParticleSystem.svelte';
  import RoomZone from './RoomZone.svelte';
  import CorridorGroup from './CorridorGroup.svelte';
  import type { GuildSceneGraph, RoomData, ViewMode, CameraTarget } from '$lib/types';

  let {
    sceneData,
    viewMode = $bindable('overview'),
    focusedRoom = $bindable(null),
    children,
  }: {
    sceneData: GuildSceneGraph;
    viewMode?: ViewMode;
    focusedRoom?: CameraTarget | null;
    children?: import('svelte').Snippet;
  } = $props();

  // Compute overview camera height based on room spread
  let overviewHeight = $derived.by(() => {
    if (sceneData.rooms.length === 0) return 40;
    const maxDist = sceneData.rooms.reduce((max, r) => {
      return Math.max(max, Math.abs(r.position.x), Math.abs(r.position.z));
    }, 0);
    return Math.max(35, maxDist * 1.5 + 15);
  });

  // Derive camera position and target from viewMode
  let cameraPos = $derived.by(() => {
    if (viewMode === 'room' && focusedRoom) {
      return [focusedRoom.position.x, 12, focusedRoom.position.z + 16] as [number, number, number];
    }
    return [0, overviewHeight, 0.1] as [number, number, number];
  });

  let cameraTarget = $derived.by(() => {
    if (viewMode === 'room' && focusedRoom) {
      return [focusedRoom.position.x, 0.5, focusedRoom.position.z] as [number, number, number];
    }
    return [0, 0, 0] as [number, number, number];
  });

  // Total online users across all rooms
  let totalUsers = $derived(
    sceneData.rooms.reduce((sum, r) => sum + r.users.length, 0)
  );
</script>

<div class="scene-container">
  <Canvas
    shadows
    toneMapping={THREE.ACESFilmicToneMapping}
  >
    <SceneSetup />

    <!-- Camera + Orbit Controls -->
    <T.PerspectiveCamera
      position={cameraPos}
      fov={55}
      near={0.1}
      far={500}
      makeDefault
      oncreate={(camera: THREE.PerspectiveCamera) => {
        camera.lookAt(...cameraTarget);
      }}
    >
      <OrbitControls
        enableDamping
        dampingFactor={0.05}
        maxPolarAngle={Math.PI / 2.2}
        minDistance={4}
        maxDistance={viewMode === 'overview' ? overviewHeight * 2 : 35}
        target={cameraTarget}
      />
    </T.PerspectiveCamera>

    <!-- Lights -->
    <T.AmbientLight intensity={0.4} color="#b8a9d4" />

    <T.DirectionalLight
      position={[8, 12, 6]}
      intensity={0.8}
      castShadow
      oncreate={(light: THREE.DirectionalLight) => {
        light.shadow.mapSize.width = 2048;
        light.shadow.mapSize.height = 2048;
        light.shadow.camera.near = 0.5;
        light.shadow.camera.far = 80;
        light.shadow.camera.left = -30;
        light.shadow.camera.right = 30;
        light.shadow.camera.top = 30;
        light.shadow.camera.bottom = -30;
        light.shadow.bias = -0.001;
      }}
    />

    <T.HemisphereLight args={['#b8a9d4', '#2a1f3d', 0.3]} />

    <T.PointLight position={[0, 5, 0]} intensity={0.3} color="#d4a9b8" />

    <!-- Ground plane (large, under everything) -->
    <T.Mesh rotation.x={-Math.PI / 2} position.y={-0.05} receiveShadow>
      <T.PlaneGeometry args={[200, 200]} />
      <T.MeshStandardMaterial color="#110e1a" roughness={0.95} metalness={0.0} />
    </T.Mesh>

    <!-- Corridors between rooms -->
    <CorridorGroup rooms={sceneData.rooms} />

    <!-- Room zones -->
    {#each sceneData.rooms as room (room.id)}
      <RoomZone
        {room}
        active={focusedRoom?.roomId === room.id}
        onclick={() => {
          focusedRoom = { roomId: room.id, position: room.position, roomName: room.name };
          viewMode = 'room';
        }}
      />
    {/each}

    <!-- Floating particles -->
    <ParticleSystem />

    <!-- Render children (injected from parent if needed) -->
    {#if children}
      {@render children()}
    {/if}
  </Canvas>
</div>

<style>
  .scene-container {
    position: fixed;
    inset: 0;
    width: 100vw;
    height: 100vh;
  }

  .scene-container :global(canvas) {
    display: block;
    width: 100% !important;
    height: 100% !important;
  }
</style>
