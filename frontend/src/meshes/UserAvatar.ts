import * as THREE from 'three';
import type { UserNode } from '../types';
import {
  toThreeVector3,
  createTextSprite,
  createSpeakingIndicator,
  loadTexture,
  createDefaultAvatarTexture,
} from '../utils/sceneUtils';

/**
 * UserAvatar - Represents a user in the 3D space
 *
 * Features:
 * - 3D avatar (sphere with user avatar texture)
 * - Username label above avatar
 * - Speaking indicator (green glow when talking)
 * - Smooth position updates
 */
export class UserAvatar {
  public readonly mesh: THREE.Group;
  private userData: UserNode;
  private targetPosition: THREE.Vector3;
  private targetRotation: THREE.Euler;
  private speakingIndicator: THREE.Mesh;
  private usernameLabel: THREE.Sprite;
  private avatarSprite: THREE.Sprite;
  private isSpeaking: boolean = false;
  private speakingPulseTime: number = 0;

  constructor(userData: UserNode, avatarTexture?: THREE.Texture) {
    this.userData = userData;
    this.targetPosition = toThreeVector3(userData.position);
    this.targetRotation = new THREE.Euler(userData.rotation.x, userData.rotation.y, userData.rotation.z);

    this.mesh = new THREE.Group();
    this.mesh.position.copy(this.targetPosition);
    this.mesh.rotation.copy(this.targetRotation);

    // Create avatar components
    this.createAvatarBody(avatarTexture);
    this.usernameLabel = createTextSprite(userData.displayName);
    this.usernameLabel.position.set(0, 1.5, 0);
    this.mesh.add(this.usernameLabel);

    // Speaking indicator (hidden by default)
    this.speakingIndicator = createSpeakingIndicator();
    this.speakingIndicator.position.set(0, 1.2, 0);
    this.speakingIndicator.visible = false;
    this.mesh.add(this.speakingIndicator);

    // Set user data on mesh for identification
    this.mesh.userData = {
      type: 'user',
      userId: userData.id,
    };
  }

  /**
   * Update avatar position and rotation (called every frame)
   * @param lerpFactor - Interpolation factor (0-1) for smooth movement
   */
  public update(lerpFactor: number, deltaTime: number): void {
    // Smooth position update
    this.mesh.position.lerp(this.targetPosition, lerpFactor);

    // Smooth rotation update
    this.mesh.rotation.x = THREE.MathUtils.lerp(this.mesh.rotation.x, this.targetRotation.x, lerpFactor);
    this.mesh.rotation.y = THREE.MathUtils.lerp(this.mesh.rotation.y, this.targetRotation.y, lerpFactor);
    this.mesh.rotation.z = THREE.MathUtils.lerp(this.mesh.rotation.z, this.targetRotation.z, lerpFactor);

    // Update speaking indicator pulse
    if (this.isSpeaking) {
      this.speakingPulseTime += deltaTime * 5;
      const scale = 1 + Math.sin(this.speakingPulseTime) * 0.3;
      this.speakingIndicator.scale.set(scale, scale, scale);
    }
  }

  /**
   * Update user data from backend
   */
  public updateData(userData: UserNode): void {
    this.userData = userData;
    this.targetPosition = toThreeVector3(userData.position);
    this.targetRotation = new THREE.Euler(userData.rotation.x, userData.rotation.y, userData.rotation.z);

    // Update speaking state
    this.setSpeaking(userData.isSpeaking);

    // Update username if changed
    if (this.userData.displayName !== userData.displayName) {
      this.mesh.remove(this.usernameLabel);
      this.usernameLabel = createTextSprite(userData.displayName);
      this.usernameLabel.position.set(0, 1.5, 0);
      this.mesh.add(this.usernameLabel);
    }
  }

  /**
   * Set speaking state
   */
  public setSpeaking(speaking: boolean): void {
    if (this.isSpeaking !== speaking) {
      this.isSpeaking = speaking;
      this.speakingIndicator.visible = speaking;
      this.speakingPulseTime = 0;

      // Add/subtract emissive glow when speaking
      const body = this.mesh.children.find(child => child instanceof THREE.Mesh && child !== this.speakingIndicator);
      if (body && body instanceof THREE.Mesh && body.material instanceof THREE.MeshStandardMaterial) {
        if (speaking) {
          body.material.emissive.setHex(0x00ff00);
          body.material.emissiveIntensity = 0.3;
        } else {
          body.material.emissive.setHex(0x000000);
          body.material.emissiveIntensity = 0;
        }
      }
    }
  }

  /**
   * Update avatar texture
   */
  public setAvatarTexture(texture: THREE.Texture): void {
    if (this.avatarSprite.material instanceof THREE.SpriteMaterial) {
      this.avatarSprite.material.map = texture;
      this.avatarSprite.material.needsUpdate = true;
    }
  }

  /**
   * Get current user data
   */
  public getData(): UserNode {
    return this.userData;
  }

  /**
   * Dispose of resources
   */
  public dispose(): void {
    // Dispose geometries and materials
    this.mesh.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        child.geometry.dispose();
        if (child.material instanceof THREE.Material) {
          child.material.dispose();
        } else if (Array.isArray(child.material)) {
          child.material.forEach(mat => mat.dispose());
        }
      }
    });
  }

  /**
   * Create the main avatar body (3D representation)
   */
  private createAvatarBody(avatarTexture?: THREE.Texture): void {
    // Body - sphere with user avatar
    const bodyGeometry = new THREE.SphereGeometry(0.5, 32, 32);

    let bodyMaterial: THREE.Material;

    if (avatarTexture) {
      bodyMaterial = new THREE.MeshStandardMaterial({
        map: avatarTexture,
        roughness: 0.5,
        metalness: 0.1,
      });
    } else {
      // Create default material with user color
      const hue = (this.userData.id.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0) % 360) / 360;
      bodyMaterial = new THREE.MeshStandardMaterial({
        color: new THREE.Color().setHSL(hue, 0.6, 0.5),
        roughness: 0.5,
        metalness: 0.1,
      });
    }

    const body = new THREE.Mesh(bodyGeometry, bodyMaterial);
    body.position.y = 0.5;
    body.castShadow = true;
    this.mesh.add(body);

    // Add 2D avatar sprite facing camera
    this.createAvatarSprite(avatarTexture);

    // Add a simple "head" above body
    const headGeometry = new THREE.SphereGeometry(0.25, 16, 16);
    const headMaterial = new THREE.MeshStandardMaterial({
      color: 0xffccaa, // Skin tone
      roughness: 0.6,
    });
    const head = new THREE.Mesh(headGeometry, headMaterial);
    head.position.y = 1.15;
    head.castShadow = true;
    this.mesh.add(head);
  }

  /**
   * Create a billboard sprite with avatar image
   */
  private createAvatarSprite(texture?: THREE.Texture): void {
    let spriteMaterial: THREE.SpriteMaterial;

    if (texture) {
      spriteMaterial = new THREE.SpriteMaterial({
        map: texture,
        transparent: true,
      });
    } else {
      // Use default texture
      const defaultTexture = createDefaultAvatarTexture(this.userData.username);
      spriteMaterial = new THREE.SpriteMaterial({
        map: defaultTexture,
        transparent: true,
      });
    }

    this.avatarSprite = new THREE.Sprite(spriteMaterial);
    this.avatarSprite.position.set(0, 1, 0.3);
    this.avatarSprite.scale.set(0.8, 0.8, 1);
    this.mesh.add(this.avatarSprite);
  }
}
