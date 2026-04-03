<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { useThrelte, useTask } from '@threlte/core';
  import * as THREE from 'three';

  const { scene } = useThrelte();

  let particles: THREE.Points;
  let elapsed = 0;

  onMount(() => {
    const count = 200;
    const positions = new Float32Array(count * 3);

    for (let i = 0; i < count; i++) {
      positions[i * 3] = (Math.random() - 0.5) * 30;
      positions[i * 3 + 1] = Math.random() * 8 + 0.5;
      positions[i * 3 + 2] = (Math.random() - 0.5) * 30;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));

    const material = new THREE.PointsMaterial({
      color: 0xffeedd,
      size: 0.06,
      transparent: true,
      opacity: 0.4,
      sizeAttenuation: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });

    particles = new THREE.Points(geometry, material);
    // @ts-expect-error @types/three version mismatch with threlte
    scene.add(particles);

    return () => {
      if (particles) {
        // @ts-expect-error @types/three version mismatch with threlte
        scene.remove(particles);
        particles.geometry.dispose();
        (particles.material as THREE.Material).dispose();
      }
    };
  });

  // Animate particles using Threlte's unified render loop
  useTask('particles', (delta) => {
    if (!particles) return;
    elapsed += delta;

    const positions = particles.geometry.attributes.position;
    for (let i = 0; i < positions.count; i++) {
      const y = positions.getY(i);
      positions.setY(i, y + Math.sin(elapsed * 0.3 + i) * 0.002);
      if (positions.getY(i) > 10) positions.setY(i, 0.5);
    }
    positions.needsUpdate = true;
    particles.rotation.y = elapsed * 0.01;
  });
</script>
