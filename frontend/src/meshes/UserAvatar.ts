import * as THREE from 'three';
import type { UserNode } from '../types';
import {
  toThreeVector3,
  createTextSprite,
  createActivitySprite,
  createSpeakingIndicator,
  createDefaultAvatarTexture,
} from '../utils/sceneUtils';

/**
 * UserAvatar - Cute 3D representation of a Discord user
 *
 * Features:
 * - Round avatar billboard with Discord PFP
 * - Clean username pill label
 * - Activity label (game/status)
 * - Speaking pulse ring + gentle bounce
 * - Spawn animation (scale up from 0)
 * - Smooth position lerping
 */
export class UserAvatar {
  public readonly mesh: THREE.Group;
  private userData: UserNode;
  private targetPosition: THREE.Vector3;
  private targetRotation: THREE.Euler;
  private speakingRing: THREE.Mesh;
  private usernameLabel: THREE.Sprite;
  private activityLabel: THREE.Sprite | null = null;
  private avatarSprite!: THREE.Sprite;
  private avatarBacking!: THREE.Mesh;
  private isSpeaking: boolean = false;
  private speakingPulseTime: number = 0;
  private spawnProgress: number = 0;
  private isSpawning: boolean = true;
  private isDespawning: boolean = false;
  private despawnCallback: (() => void) | null = null;

  constructor(userData: UserNode, avatarTexture?: THREE.Texture) {
    this.userData = userData;
    this.targetPosition = toThreeVector3(userData.position);
    this.targetRotation = new THREE.Euler(userData.rotation.x, userData.rotation.y, userData.rotation.z);

    this.mesh = new THREE.Group();
    this.mesh.position.copy(this.targetPosition);
    this.mesh.rotation.copy(this.targetRotation);

    // Start at scale 0 for spawn animation
    this.mesh.scale.set(0, 0, 0);

    // Create avatar disc (main visual)
    this.createAvatarDisc(avatarTexture);

    // Username label above avatar
    this.usernameLabel = createTextSprite(userData.displayName);
    this.usernameLabel.position.set(0, 1.4, 0);
    this.mesh.add(this.usernameLabel);

    // Activity label if present
    this.updateActivityLabel();

    // Speaking ring (hidden by default)
    this.speakingRing = createSpeakingIndicator();
    this.speakingRing.position.set(0, 0.65, 0);
    this.speakingRing.visible = false;
    this.mesh.add(this.speakingRing);

    // Identification data
    this.mesh.userData = { type: 'user', userId: userData.id };
  }

  /**
   * Per-frame update - animations, lerping, speaking effects
   */
  public update(lerpFactor: number, deltaTime: number): void {
    // Spawn animation - smooth elastic scale-in
    if (this.isSpawning) {
      this.spawnProgress = Math.min(this.spawnProgress + deltaTime * 2.5, 1);
      const t = this.spawnProgress;
      // Elastic ease-out
      const elastic = t === 1 ? 1 : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
      this.mesh.scale.setScalar(elastic);
      if (this.spawnProgress >= 1) {
        this.isSpawning = false;
        this.mesh.scale.setScalar(1);
      }
    }

    // Despawn animation - shrink + fade
    if (this.isDespawning) {
      this.spawnProgress = Math.max(this.spawnProgress - deltaTime * 3, 0);
      this.mesh.scale.setScalar(this.spawnProgress);
      if (this.spawnProgress <= 0) {
        this.isDespawning = false;
        if (this.despawnCallback) this.despawnCallback();
      }
      return; // Skip other updates during despawn
    }

    // Smooth position lerp
    this.mesh.position.lerp(this.targetPosition, lerpFactor);

    // Smooth rotation lerp
    this.mesh.rotation.x = THREE.MathUtils.lerp(this.mesh.rotation.x, this.targetRotation.x, lerpFactor);
    this.mesh.rotation.y = THREE.MathUtils.lerp(this.mesh.rotation.y, this.targetRotation.y, lerpFactor);
    this.mesh.rotation.z = THREE.MathUtils.lerp(this.mesh.rotation.z, this.targetRotation.z, lerpFactor);

    // Speaking animation
    if (this.isSpeaking) {
      this.speakingPulseTime += deltaTime * 4;

      // Ring pulse
      const ringScale = 1 + Math.sin(this.speakingPulseTime) * 0.15;
      this.speakingRing.scale.set(ringScale, ringScale, ringScale);
      const ringMat = this.speakingRing.material as THREE.MeshBasicMaterial;
      ringMat.opacity = 0.5 + Math.sin(this.speakingPulseTime * 1.5) * 0.3;

      // Gentle avatar bounce
      const bounce = Math.abs(Math.sin(this.speakingPulseTime * 1.2)) * 0.06;
      this.avatarSprite.position.y = 0.65 + bounce;
      this.avatarBacking.position.y = 0.65 + bounce;
    }
  }

  /**
   * Update user data from backend
   */
  public updateData(newData: UserNode): void {
    const oldDisplayName = this.userData.displayName;
    const oldActivity = this.userData.activity;
    this.userData = newData;
    this.targetPosition = toThreeVector3(newData.position);
    this.targetRotation = new THREE.Euler(newData.rotation.x, newData.rotation.y, newData.rotation.z);

    // Update speaking state
    this.setSpeaking(newData.isSpeaking);

    // Update username if changed
    if (oldDisplayName !== newData.displayName) {
      this.mesh.remove(this.usernameLabel);
      this.usernameLabel = createTextSprite(newData.displayName);
      this.usernameLabel.position.set(0, 1.4, 0);
      this.mesh.add(this.usernameLabel);
    }

    // Update activity label if changed
    const oldActivityName = oldActivity?.name;
    const newActivityName = newData.activity?.name;
    if (oldActivityName !== newActivityName) {
      this.updateActivityLabel();
    }
  }

  /**
   * Set speaking state with visual feedback
   */
  public setSpeaking(speaking: boolean): void {
    if (this.isSpeaking !== speaking) {
      this.isSpeaking = speaking;
      this.speakingRing.visible = speaking;
      this.speakingPulseTime = 0;

      if (!speaking) {
        // Reset avatar position when done speaking
        this.avatarSprite.position.y = 0.65;
        this.avatarBacking.position.y = 0.65;
      }
    }
  }

  /**
   * Start despawn animation, call callback when done
   */
  public despawn(callback: () => void): void {
    this.isDespawning = true;
    this.spawnProgress = 1;
    this.despawnCallback = callback;
  }

  /**
   * Update avatar texture (e.g. when loaded async)
   */
  public setAvatarTexture(texture: THREE.Texture): void {
    if (this.avatarSprite.material instanceof THREE.SpriteMaterial) {
      this.avatarSprite.material.map = texture;
      this.avatarSprite.material.needsUpdate = true;
    }
  }

  public getData(): UserNode {
    return this.userData;
  }

  public dispose(): void {
    this.mesh.traverse((child) => {
      if (child instanceof THREE.Mesh || child instanceof THREE.Sprite) {
        if ('geometry' in child) child.geometry.dispose();
        if (child.material instanceof THREE.Material) {
          child.material.dispose();
        } else if (Array.isArray(child.material)) {
          child.material.forEach(mat => mat.dispose());
        }
      }
    });
  }

  /**
   * Create the main avatar disc - circular PFP billboard
   */
  private createAvatarDisc(avatarTexture?: THREE.Texture): void {
    // Circular backing disc (soft shadow ring)
    const backingGeom = new THREE.CircleGeometry(0.52, 32);
    const backingMat = new THREE.MeshBasicMaterial({
      color: 0x1a1a2e,
      transparent: true,
      opacity: 0.5,
      side: THREE.DoubleSide,
    });
    this.avatarBacking = new THREE.Mesh(backingGeom, backingMat);
    this.avatarBacking.position.set(0, 0.65, -0.01);
    this.mesh.add(this.avatarBacking);

    // Avatar sprite (billboard that always faces camera)
    const texture = avatarTexture || createDefaultAvatarTexture(this.userData.username);
    const spriteMat = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
    });
    this.avatarSprite = new THREE.Sprite(spriteMat);
    this.avatarSprite.position.set(0, 0.65, 0);
    this.avatarSprite.scale.set(1, 1, 1);
    this.mesh.add(this.avatarSprite);
  }

  /**
   * Update or create activity label
   */
  private updateActivityLabel(): void {
    // Remove old label
    if (this.activityLabel) {
      this.mesh.remove(this.activityLabel);
      this.activityLabel.material.dispose();
      this.activityLabel = null;
    }

    if (this.userData.activity) {
      const prefix = this.getActivityPrefix(this.userData.activity.type);
      const text = `${prefix} ${this.userData.activity.name}`;
      this.activityLabel = createActivitySprite(text);
      this.activityLabel.position.set(0, 1.75, 0);
      this.mesh.add(this.activityLabel);
    }
  }

  private getActivityPrefix(type: string): string {
    switch (type) {
      case 'PLAYING': return '\u25B6';
      case 'STREAMING': return '\u25CF';
      case 'LISTENING': return '\u266B';
      case 'WATCHING': return '\u25A0';
      case 'COMPETING': return '\u2694';
      default: return '\u25B6';
    }
  }
}
