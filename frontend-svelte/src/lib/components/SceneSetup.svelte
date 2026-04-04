<script lang="ts">
  import { useThrelte } from '@threlte/core';
  import * as THREE from 'three';

  const { scene, renderer } = useThrelte();

  // Scene background and fog
  scene.background = new THREE.Color(0x161625);
  scene.fog = new THREE.FogExp2(0x161625, 0.018);

  // Renderer settings
  if (renderer) {
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  }
</script>
