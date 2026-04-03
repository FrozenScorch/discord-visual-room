<script lang="ts">
  import { useThrelte } from '@threlte/core';
  import * as THREE from 'three';

  const { scene, renderer } = useThrelte();

  // Scene background and fog
  // @ts-expect-error @types/three Color version mismatch with threlte
  scene.background = new THREE.Color(0x161625);
  // @ts-expect-error @types/three FogExp2 version mismatch with threlte
  scene.fog = new THREE.FogExp2(0x161625, 0.018);

  // Renderer settings
  if (renderer) {
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  }
</script>
