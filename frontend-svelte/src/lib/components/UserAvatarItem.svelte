<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { useThrelte, useTask } from '@threlte/core';
  import * as THREE from 'three';
  import {
    toThreeVector3,
    createTextSprite,
    createActivitySprite,
    createSpeakingIndicator,
    createDefaultAvatarTexture,
    loadTexture,
  } from '$lib/sceneUtils';
  import type { UserNode } from '$lib/types';

  let { node }: { node: UserNode } = $props();

  const { scene } = useThrelte();

  // Avatar group
  let group: THREE.Group;

  // Sub-components (kept for animation access)
  let avatarSprite: THREE.Sprite | null = null;
  let avatarBacking: THREE.Mesh | null = null;
  let usernameLabel: THREE.Sprite | null = null;
  let activityLabel: THREE.Sprite | null = null;
  let speakingRing: THREE.Mesh | null = null;

  // Animation state
  let spawnProgress = 0;
  let speakingPulseTime = 0;
  let disposed = false;

  onMount(() => {
    const targetPosition = toThreeVector3(node.position);
    const targetRotation = new THREE.Euler(node.rotation.x, node.rotation.y, node.rotation.z);

    group = new THREE.Group();
    group.position.copy(targetPosition);
    group.rotation.copy(targetRotation);
    group.scale.set(0, 0, 0); // Start at 0 for spawn animation

    // ── Avatar disc ──────────────────────────────────────────────────────
    createAvatarDisc(node);

    // ── Username label ───────────────────────────────────────────────────
    usernameLabel = createTextSprite(node.displayName);
    usernameLabel.position.set(0, 1.4, 0);
    group.add(usernameLabel);

    // ── Activity label ───────────────────────────────────────────────────
    updateActivityLabel();

    // ── Speaking ring ────────────────────────────────────────────────────
    speakingRing = createSpeakingIndicator();
    speakingRing.position.set(0, 0.65, 0);
    speakingRing.visible = node.isSpeaking;
    group.add(speakingRing);

    // ── Identification data ──────────────────────────────────────────────
    group.userData = { type: 'user', userId: node.id };

    scene.add(group);
  });

  // ── Reactive updates via $effect ──────────────────────────────────────────

  $effect(() => {
    const isSpeaking = node.isSpeaking;
    if (speakingRing) {
      speakingRing.visible = isSpeaking;
      speakingPulseTime = 0;
    }
    if (!isSpeaking) {
      if (avatarSprite) avatarSprite.position.y = 0.65;
      if (avatarBacking) avatarBacking.position.y = 0.65;
    }
  });

  $effect(() => {
    const displayName = node.displayName;
    if (group && usernameLabel) {
      group.remove(usernameLabel);
      usernameLabel.material.dispose();
      usernameLabel = createTextSprite(displayName);
      usernameLabel.position.set(0, 1.4, 0);
      group.add(usernameLabel);
    }
  });

  $effect(() => {
    const activityName = node.activity?.name ?? null;
    updateActivityLabel();
    void activityName; // read to track reactively
  });

  $effect(() => {
    const avatarUrl = node.avatar;
    if (avatarUrl) {
      loadTexture(avatarUrl).then((texture) => {
        if (texture && avatarSprite && avatarSprite.material instanceof THREE.SpriteMaterial) {
          avatarSprite.material.map = texture;
          avatarSprite.material.needsUpdate = true;
        }
      });
    }
  });

  // ── Animation loop via Threlte useTask ─────────────────────────────────────

  const { id: nodeId } = node;
  useTask(`avatar-${nodeId}`, (delta) => {
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
      if (spawnProgress >= 1) group.scale.setScalar(1);
    }

    // Smooth position lerp
    const targetPos = toThreeVector3(node.position);
    group.position.lerp(targetPos, 0.08);

    // Smooth rotation lerp
    const targetRot = new THREE.Euler(node.rotation.x, node.rotation.y, node.rotation.z);
    group.rotation.x = THREE.MathUtils.lerp(group.rotation.x, targetRot.x, 0.08);
    group.rotation.y = THREE.MathUtils.lerp(group.rotation.y, targetRot.y, 0.08);
    group.rotation.z = THREE.MathUtils.lerp(group.rotation.z, targetRot.z, 0.08);

    // Speaking animation
    if (node.isSpeaking) {
      speakingPulseTime += delta * 4;

      if (speakingRing) {
        const ringScale = 1 + Math.sin(speakingPulseTime) * 0.15;
        speakingRing.scale.set(ringScale, ringScale, ringScale);
        const ringMat = speakingRing.material as THREE.MeshBasicMaterial;
        ringMat.opacity = 0.5 + Math.sin(speakingPulseTime * 1.5) * 0.3;
      }

      const bounce = Math.abs(Math.sin(speakingPulseTime * 1.2)) * 0.06;
      if (avatarSprite) avatarSprite.position.y = 0.65 + bounce;
      if (avatarBacking) avatarBacking.position.y = 0.65 + bounce;
    }
  });

  // ── Helper functions ──────────────────────────────────────────────────────

  function createAvatarDisc(userData: UserNode) {
    // Circular backing disc
    const backingGeom = new THREE.CircleGeometry(0.52, 32);
    const backingMat = new THREE.MeshBasicMaterial({
      color: 0x1a1a2e,
      transparent: true,
      opacity: 0.5,
      side: THREE.DoubleSide,
    });
    avatarBacking = new THREE.Mesh(backingGeom, backingMat);
    avatarBacking.position.set(0, 0.65, -0.01);
    group.add(avatarBacking);

    // Avatar sprite (billboard)
    const texture = createDefaultAvatarTexture(userData.username);
    const spriteMat = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
    });
    avatarSprite = new THREE.Sprite(spriteMat);
    avatarSprite.position.set(0, 0.65, 0);
    avatarSprite.scale.set(1, 1, 1);
    group.add(avatarSprite);

    // Async texture load
    if (userData.avatar) {
      loadTexture(userData.avatar).then((tex) => {
        if (tex && avatarSprite && avatarSprite.material instanceof THREE.SpriteMaterial) {
          avatarSprite.material.map = tex;
          avatarSprite.material.needsUpdate = true;
        }
      });
    }
  }

  function updateActivityLabel() {
    if (activityLabel) {
      group.remove(activityLabel);
      activityLabel.material.dispose();
      activityLabel = null;
    }

    if (node.activity) {
      const prefix = getActivityPrefix(node.activity.type);
      const text = `${prefix} ${node.activity.name}`;
      activityLabel = createActivitySprite(text);
      activityLabel.position.set(0, 1.75, 0);
      group.add(activityLabel);
    }
  }

  function getActivityPrefix(type: string): string {
    switch (type) {
      case 'PLAYING':
        return '\u25B6';
      case 'STREAMING':
        return '\u25CF';
      case 'LISTENING':
        return '\u266B';
      case 'WATCHING':
        return '\u25A0';
      case 'COMPETING':
        return '\u2694';
      default:
        return '\u25B6';
    }
  }

  // ── Cleanup ───────────────────────────────────────────────────────────────

  onDestroy(() => {
    disposed = true;
    if (group) {
      scene.remove(group);
      group.traverse((child) => {
        if (child instanceof THREE.Mesh || child instanceof THREE.Sprite) {
          if ('geometry' in child) child.geometry.dispose();
          if (child.material instanceof THREE.Material) {
            child.material.dispose();
          } else if (Array.isArray(child.material)) {
            child.material.forEach((mat) => mat.dispose());
          }
        }
      });
    }
  });
</script>
