<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { useThrelte, useTask } from '@threlte/core';
  import * as THREE from 'three';
  import { getFurnitureMesh, initializeFurnitureFactory } from '$lib/utils/furnitureFactory';
  import type { FurnitureNode } from '$lib/types';

  let { node }: { node: FurnitureNode } = $props();

  const { scene } = useThrelte();

  let group: THREE.Group;
  let seatMaterials: THREE.MeshStandardMaterial[] = [];
  let spawnProgress = 0;
  let disposed = false;
  let glowPulseTime = 0;

  /**
   * Identify seat/cushion meshes per furniture type and return their materials.
   * This lets us apply the occupied glow independently per instance.
   */
  function collectSeatMaterials(mesh: THREE.Object3D, type: string): THREE.MeshStandardMaterial[] {
    const mats: THREE.MeshStandardMaterial[] = [];
    mesh.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return;
      const mat = child.material;
      if (!(mat instanceof THREE.MeshStandardMaterial)) return;

      // Match seat/cushion materials by their position (they sit high, are soft-colored)
      const y = child.position.y;
      switch (type) {
        case 'COMPUTER_DESK':
          // Monitor screen glows when occupied
          if (y > 1.0) mats.push(mat);
          break;
        case 'COUCH_2_SEATER':
          // Seat base + cushions
          if (y >= 0.25 && y <= 0.6) mats.push(mat);
          break;
        case 'COUCH_SINGLE':
          // Seat + cushion
          if (y >= 0.25 && y <= 0.6) mats.push(mat);
          break;
        case 'BAR_STOOL':
          // Seat cushion (the round top)
          if (y > 1.0) mats.push(mat);
          break;
      }
    });
    return mats;
  }

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

    // Deep-clone materials so each instance has independent material state
    mesh.traverse((child) => {
      if (child instanceof THREE.Mesh && child.material instanceof THREE.Material) {
        child.material = child.material.clone();
      }
    });

    // Enable shadows on all child meshes
    mesh.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        child.castShadow = true;
        child.receiveShadow = true;
      }
    });

    // Collect seat materials for glow effect
    seatMaterials = collectSeatMaterials(mesh, node.type);

    // Apply occupied glow if already assigned
    if (node.assignedUserId) {
      applyOccupiedGlow(true);
    }

    group.add(mesh);
    group.scale.setScalar(0.001); // Start at 0 for spawn animation
    scene.add(group);
  });

  // ── Reactive: update glow when assignedUserId changes ──────────────────
  $effect(() => {
    const isOccupied = !!node.assignedUserId;
    applyOccupiedGlow(isOccupied);
  });

  function applyOccupiedGlow(occupied: boolean): void {
    for (const mat of seatMaterials) {
      if (occupied) {
        mat.emissive = mat.emissive || new THREE.Color();
        mat.emissive.set(0x9966ff); // lavender
        mat.emissiveIntensity = 0.25;
      } else {
        // Reset to no glow (preserve any base emissive from factory)
        mat.emissiveIntensity = 0;
      }
    }
  }

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

    // Subtle glow pulse for occupied furniture
    if (node.assignedUserId && seatMaterials.length > 0) {
      glowPulseTime += delta * 1.5;
      const pulse = 0.2 + Math.sin(glowPulseTime) * 0.08;
      for (const mat of seatMaterials) {
        mat.emissiveIntensity = pulse;
      }
    }
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
