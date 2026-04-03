<script lang="ts">
  import { Canvas, T, useThrelte, useTask } from '@threlte/core';
  import { OrbitControls } from '@threlte/extras';
  import * as THREE from 'three';
  import SceneSetup from './SceneSetup.svelte';
  import ParticleSystem from './ParticleSystem.svelte';
  import type { SceneGraph } from '$lib/types';

  let { sceneData, children }: { sceneData: SceneGraph; children?: import('svelte').Snippet } = $props();
</script>

<div class="scene-container">
  <Canvas
    shadows
    toneMapping={THREE.ACESFilmicToneMapping}
  >
    <!-- Imperative scene setup (background, fog, renderer) -->
    <SceneSetup />

    <!-- Camera + Orbit Controls -->
    <T.PerspectiveCamera
      position={[0, 12, 16]}
      fov={55}
      near={0.1}
      far={200}
      makeDefault
      // @ts-expect-error threlte extends PerspectiveCamera with extra props
      oncreate={(camera: THREE.PerspectiveCamera) => {
        camera.lookAt(0, 0, 0);
      }}
    >
      <OrbitControls
        enableDamping
        dampingFactor={0.05}
        maxPolarAngle={Math.PI / 2.2}
        minDistance={4}
        maxDistance={35}
        target={[0, 0.5, 0]}
      />
    </T.PerspectiveCamera>

    <!-- Lights -->
    <T.AmbientLight intensity={0.4} color="#b8a9d4" />

    <T.DirectionalLight
      position={[8, 12, 6]}
      intensity={0.8}
      castShadow
      // @ts-expect-error threlte extends DirectionalLight with extra props
      oncreate={(light: THREE.DirectionalLight) => {
        light.shadow.mapSize.width = 2048;
        light.shadow.mapSize.height = 2048;
        light.shadow.camera.near = 0.5;
        light.shadow.camera.far = 40;
        light.shadow.camera.left = -15;
        light.shadow.camera.right = 15;
        light.shadow.camera.top = 15;
        light.shadow.camera.bottom = -15;
        light.shadow.bias = -0.001;
      }}
    />

    <T.HemisphereLight args={['#b8a9d4', '#2a1f3d', 0.3]} />

    <T.PointLight position={[0, 5, 0]} intensity={0.3} color="#d4a9b8" />

    <!-- Floor -->
    <T.Mesh rotation.x={-Math.PI / 2} receiveShadow>
      <T.CircleGeometry args={[12, 64]} />
      <T.MeshStandardMaterial color="#2a1f3d" roughness={0.85} metalness={0.05} />
    </T.Mesh>

    <!-- Rug -->
    <T.Mesh rotation.x={-Math.PI / 2} position.y={0.005} receiveShadow>
      <T.CircleGeometry args={[5, 48]} />
      <T.MeshStandardMaterial color="#3d2a4f" roughness={0.95} metalness={0.0} />
    </T.Mesh>

    <!-- Rug edge glow ring -->
    <T.Mesh rotation.x={-Math.PI / 2} position.y={0.01}>
      <T.TorusGeometry args={[5, 0.08, 8, 64]} />
      <T.MeshStandardMaterial
        color="#5865F2"
        roughness={0.7}
        emissive="#5865F2"
        emissiveIntensity={0.1}
      />
    </T.Mesh>

    <!-- Floating particles -->
    <ParticleSystem />

    <!-- Room boundary wireframe -->
    {#if sceneData.room}
      {@const dims = sceneData.room.dimensions}
      <T.LineSegments position.y={dims.height / 2}>
        <T.EdgesGeometry args={[new THREE.BoxGeometry(dims.width, dims.height, dims.depth)]} />
        <T.LineBasicMaterial color="#5865F2" transparent opacity={0.15} />
      </T.LineSegments>
    {/if}

    <!-- Render children (furniture + users injected from parent) -->
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
