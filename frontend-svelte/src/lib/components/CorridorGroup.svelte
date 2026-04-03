<script lang="ts">
  import { T } from '@threlte/core';
  import type { RoomData } from '$lib/types';

  const CORRIDOR_WIDTH = 6;

  let { rooms }: { rooms: RoomData[] } = $props();

  // Compute corridors between adjacent rooms (nearest-neighbor pairs within grid distance)
  let corridors = $derived.by(() => {
    if (rooms.length < 2) return [];

    const pairs: Array<{ cx: number; cz: number; angle: number; dist: number }> = [];

    for (let i = 0; i < rooms.length; i++) {
      for (let j = i + 1; j < rooms.length; j++) {
        const a = rooms[i];
        const b = rooms[j];
        const dx = b.position.x - a.position.x;
        const dz = b.position.z - a.position.z;
        const dist = Math.sqrt(dx * dx + dz * dz);

        // Only connect rooms that are grid neighbors (within ~40 units)
        if (dist > 40) continue;

        const cx = (a.position.x + b.position.x) / 2;
        const cz = (a.position.z + b.position.z) / 2;
        const angle = Math.atan2(dx, dz);

        pairs.push({ cx, cz, angle, dist });
      }
    }

    return pairs;
  });
</script>

{#each corridors as corr, i (i)}
  <T.Group position={[corr.cx, 0, corr.cz]} rotation.y={corr.angle}>
    <!-- Corridor floor -->
    <T.Mesh rotation.x={-Math.PI / 2} position.y={-0.01} receiveShadow>
      <T.PlaneGeometry args={[CORRIDOR_WIDTH, corr.dist]} />
      <T.MeshStandardMaterial
        color="#1e1530"
        roughness={0.95}
        metalness={0.0}
      />
    </T.Mesh>
  </T.Group>
{/each}
