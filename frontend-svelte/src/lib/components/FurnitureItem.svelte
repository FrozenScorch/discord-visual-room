<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { useThrelte, useTask } from '@threlte/core';
  import * as THREE from 'three';
  import { getFurnitureMesh, initializeFurnitureFactory } from '$lib/utils/furnitureFactory';
  import type { FurnitureNode } from '$lib/types';

  let { node }: { node: FurnitureNode } = $props();

  const { scene } = useThrelte();

  let group: THREE.Group;
  let spawnProgress = 0;
  let disposed = false;

  onMount(() => {
    initializeFurnitureFactory();
    const mesh = getFurnitureMesh(node.type);
    if (!mesh) {
      console.error(`Unknown furniture type: ${node.type}`);
      return;
    }

    group = new THREE.Group();
    group.position.set(node.position.x, node.position.y, node.position.z);
    group.rotation.set(node.rotation.x, node.rotation.y, node.rotation.z);

    // Enable shadows on all child meshes
    mesh.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        child.castShadow = true;
        child.receiveShadow = true;
      }
    });

    group.add(mesh);
    group.scale.setScalar(0.001); // Start at 0 for spawn animation
    scene.add(group);
  });

  const { id: nodeId } = node;
  useTask(`furniture-spawn-${nodeId}`, (delta) => {
    if (!group || disposed) return;

    // Spawn animation - elastic scale-in
    if (spawnProgress < 1) {
      spawnProgress = Math.min(spawnProgress + delta * 2.5, 1);
      const t = spawnProgress;
      const elastic =
        t === 1
          ? 1
          : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * ((2 * Math.PI) / 3)) + 1;
      group.scale.setScalar(elastic);
      if (t >= 1) group.scale.setScalar(1);
      return;
    }

    // Smooth position/rotation lerp
    const lerpFactor = 0.08;
    const targetPos = new THREE.Vector3(node.position.x, node.position.y, node.position.z);
    group.position.lerp(targetPos, lerpFactor);

    const targetRot = new THREE.Euler(node.rotation.x, node.rotation.y, node.rotation.z);
    group.rotation.x = THREE.MathUtils.lerp(group.rotation.x, targetRot.x, lerpFactor);
    group.rotation.y = THREE.MathUtils.lerp(group.rotation.y, targetRot.y, lerpFactor);
    group.rotation.z = THREE.MathUtils.lerp(group.rotation.z, targetRot.z, lerpFactor);
  });

  onDestroy(() => {
    disposed = true;
    if (group) {
      scene.remove(group);
      group.traverse((child) => {
        if (child instanceof THREE.Mesh) {
          child.geometry.dispose();
          if (child.material instanceof THREE.Material) {
            child.material.dispose();
          }
        }
      });
    }
  });
</script>
