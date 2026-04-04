<script lang="ts">
  import { T } from '@threlte/core';
  import * as THREE from 'three';
  import type { RoomData } from '$lib/types';
  import RoomLabel from './RoomLabel.svelte';
  import FurnitureGroup from './FurnitureGroup.svelte';
  import UserGroup from './UserGroup.svelte';

  const ROOM_SIZE = 12; // half-width of the room floor
  const ROOM_HEIGHT = 4;

  let { room, active = false, onclick }: { room: RoomData; active?: boolean; onclick?: (e: MouseEvent) => void } = $props();

  let isTextChannel = $derived(room.channelType === 'TEXT');

  // Derive a unique room color from the room name hash
  let roomColor = $derived.by(() => {
    if (isTextChannel) {
      return new THREE.Color('#1a1a30'); // Darker blue floor for text channels
    }
    let hash = 0;
    for (let i = 0; i < room.name.length; i++) {
      hash = room.name.charCodeAt(i) + ((hash << 5) - hash);
    }
    const hue = Math.abs(hash) % 360;
    return new THREE.Color(`hsl(${hue}, 40%, 18%)`);
  });

  let accentHex = $derived.by(() => {
    if (isTextChannel) {
      return '#5577cc'; // Blue accent for text channels
    }
    let hash = 0;
    for (let i = 0; i < room.name.length; i++) {
      hash = room.name.charCodeAt(i) + ((hash << 5) - hash);
    }
    const hue = Math.abs(hash) % 360;
    return `hsl(${hue}, 60%, 50%)`;
  });

  let wireframeOpacity = $derived(active ? 0.35 : 0.15);
</script>

<T.Group position={[room.position.x, 0, room.position.z]}>
  <!-- Floor tile -->
  <T.Mesh rotation.x={-Math.PI / 2} position.y={0} receiveShadow>
    <T.CircleGeometry args={[ROOM_SIZE, 64]} />
    <T.MeshStandardMaterial
      color={roomColor}
      roughness={0.85}
      metalness={0.05}
    />
  </T.Mesh>

  <!-- Inner rug -->
  <T.Mesh rotation.x={-Math.PI / 2} position.y={0.005} receiveShadow>
    <T.CircleGeometry args={[ROOM_SIZE * 0.5, 48]} />
    <T.MeshStandardMaterial
      color={new THREE.Color(roomColor).multiplyScalar(1.3)}
      roughness={0.95}
      metalness={0.0}
    />
  </T.Mesh>

  <!-- Room boundary wireframe -->
  <T.LineSegments position.y={ROOM_HEIGHT / 2}>
    <T.EdgesGeometry args={[new THREE.BoxGeometry(ROOM_SIZE * 2, ROOM_HEIGHT, ROOM_SIZE * 2)]} />
    <T.LineBasicMaterial color={accentHex} transparent opacity={wireframeOpacity} />
  </T.LineSegments>

  <!-- Glow ring on floor edge -->
  <T.Mesh rotation.x={-Math.PI / 2} position.y={0.01}>
    <T.TorusGeometry args={[ROOM_SIZE, 0.08, 8, 64]} />
    <T.MeshStandardMaterial
      color={accentHex}
      roughness={0.7}
      emissive={accentHex}
      emissiveIntensity={active ? 0.25 : 0.1}
    />
  </T.Mesh>

  <!-- Room label (floating above) -->
  <RoomLabel {room} />

  <!-- Furniture + Users (only when room has occupants or always for active room) -->
  {#if room.furniture.length > 0 || active}
    <FurnitureGroup furniture={room.furniture} />
  {/if}
  {#if room.users.length > 0 || active}
    <UserGroup users={room.users} />
  {/if}
</T.Group>
