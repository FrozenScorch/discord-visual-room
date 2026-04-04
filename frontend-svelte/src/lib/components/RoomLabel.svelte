<script lang="ts">
  import { onMount } from 'svelte';
  import { T, useThrelte } from '@threlte/core';
  import * as THREE from 'three';
  import type { RoomData } from '$lib/types';

  let { room }: { room: RoomData } = $props();

  const { scene } = useThrelte();

  // Derive a color tint from the room name hash
  let accentColor = $derived.by(() => {
    let hash = 0;
    for (let i = 0; i < room.name.length; i++) {
      hash = room.name.charCodeAt(i) + ((hash << 5) - hash);
    }
    const hue = Math.abs(hash) % 360;
    return `hsl(${hue}, 60%, 70%)`;
  });

  // Create sprite label using Canvas texture
  let labelTexture: THREE.CanvasTexture | null = $state(null);
  let countTexture: THREE.CanvasTexture | null = $state(null);

  function createTextTexture(text: string, color: string, fontSize: number): THREE.CanvasTexture {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d')!;
    canvas.width = 512;
    canvas.height = 128;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.font = `bold ${fontSize}px "Segoe UI", Arial, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = color;
    ctx.fillText(text, canvas.width / 2, canvas.height / 2);

    const texture = new THREE.CanvasTexture(canvas);
    texture.needsUpdate = true;
    return texture;
  }

  onMount(() => {
    const isText = room.channelType === 'TEXT';
    const displayName = isText ? `# ${room.name}` : room.name;
    labelTexture = createTextTexture(displayName, accentColor, 48);
    if (room.users.length > 0) {
      countTexture = createTextTexture(`${room.users.length} ${isText ? 'active' : 'online'}`, '#9999bb', 32);
    }
  });

  // Re-create textures when room changes
  $effect(() => {
    const name = room.name;
    const count = room.users.length;
    const color = accentColor;
    const isText = room.channelType === 'TEXT';
    const displayName = isText ? `# ${name}` : name;
    labelTexture = createTextTexture(displayName, color, 48);
    countTexture = count > 0
      ? createTextTexture(`${count} ${isText ? 'active' : 'online'}`, '#9999bb', 32)
      : null;
  });
</script>

<!-- Room name label sprite -->
{#if labelTexture}
  <T.Sprite position={[0, 3.5, 0]} scale={[8, 2, 1]}>
    <T.SpriteMaterial map={labelTexture} transparent depthTest={false} />
  </T.Sprite>
{/if}

<!-- User count label sprite -->
{#if countTexture}
  <T.Sprite position={[0, 2.8, 0]} scale={[5, 1.2, 1]}>
    <T.SpriteMaterial map={countTexture} transparent depthTest={false} />
  </T.Sprite>
{/if}
