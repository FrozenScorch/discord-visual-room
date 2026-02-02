import * as THREE from 'three';
import type { Vector3D, PositionTarget } from '../types';

/**
 * Convert a Vector3D from the backend to a THREE.Vector3
 */
export function toThreeVector3(vec: Vector3D): THREE.Vector3 {
  return new THREE.Vector3(vec.x, vec.y, vec.z);
}

/**
 * Convert a THREE.Vector3 to a Vector3D for backend communication
 */
export function fromThreeVector3(vec: THREE.Vector3): Vector3D {
  return { x: vec.x, y: vec.y, z: vec.z };
}

/**
 * Linear interpolation for smooth position updates
 */
export function lerpPosition(
  current: THREE.Vector3,
  target: PositionTarget,
  factor: number
): THREE.Vector3 {
  return new THREE.Vector3(
    current.x + (target.x - current.x) * factor,
    current.y + (target.y - current.y) * factor,
    current.z + (target.z - current.z) * factor
  );
}

/**
 * Linear interpolation for rotation (Euler angles)
 */
export function lerpRotation(
  current: THREE.Euler,
  target: Vector3D,
  factor: number
): THREE.Euler {
  return new THREE.Euler(
    current.x + (target.x - current.x) * factor,
    current.y + (target.y - current.y) * factor,
    current.z + (target.z - current.z) * factor
  );
}

/**
 * Create a sprite with text for user labels
 */
export function createTextSprite(
  text: string,
  fontSize: number = 32,
  color: string = '#ffffff',
  backgroundColor: string = 'rgba(0, 0, 0, 0.6)'
): THREE.Sprite {
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  if (!context) {
    // Fallback to basic sprite if canvas not available
    const material = new THREE.SpriteMaterial({ color: 0xffffff });
    return new THREE.Sprite(material);
  }

  // Set canvas size (power of 2 for better texture performance)
  canvas.width = 512;
  canvas.height = 128;

  // Draw background
  context.fillStyle = backgroundColor;
  context.roundRect(0, 0, canvas.width, canvas.height, 10);
  context.fill();

  // Draw text
  context.font = `bold ${fontSize}px Arial, sans-serif`;
  context.fillStyle = color;
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillText(text, canvas.width / 2, canvas.height / 2);

  // Create texture from canvas
  const texture = new THREE.CanvasTexture(canvas);
  texture.minFilter = THREE.LinearFilter;
  texture.magFilter = THREE.LinearFilter;

  // Create sprite
  const material = new THREE.SpriteMaterial({
    map: texture,
    transparent: true,
    depthTest: false, // Always show on top
  });

  const sprite = new THREE.Sprite(material);
  sprite.scale.set(4, 1, 1);

  return sprite;
}

/**
 * Create a glowing sphere for speaking indicator
 */
export function createSpeakingIndicator(): THREE.Mesh {
  const geometry = new THREE.SphereGeometry(0.1, 16, 16);
  const material = new THREE.MeshBasicMaterial({
    color: 0x00ff00,
    transparent: true,
    opacity: 0.8,
  });
  return new THREE.Mesh(geometry, material);
}

/**
 * Load an image texture from a URL
 */
export async function loadTexture(url: string): Promise<THREE.Texture | null> {
  return new Promise((resolve) => {
    const loader = new THREE.TextureLoader();
    loader.load(
      url,
      (texture) => {
        // Enable mipmaps for better quality at distance
        texture.generateMipmaps = true;
        texture.minFilter = THREE.LinearMipmapLinearFilter;
        texture.magFilter = THREE.LinearFilter;
        resolve(texture);
      },
      undefined,
      (error) => {
        console.warn(`Failed to load texture from ${url}:`, error);
        resolve(null);
      }
    );
  });
}

/**
 * Create a default avatar texture (colored circle with initials)
 */
export function createDefaultAvatarTexture(username: string): THREE.CanvasTexture {
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');

  if (!context) {
    return new THREE.CanvasTexture(document.createElement('canvas'));
  }

  canvas.width = 256;
  canvas.height = 256;

  // Generate a consistent color based on username
  const hash = username.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
  const hue = hash % 360;
  const color = `hsl(${hue}, 60%, 50%)`;

  // Draw circle background
  context.fillStyle = color;
  context.beginPath();
  context.arc(128, 128, 120, 0, Math.PI * 2);
  context.fill();

  // Draw initials
  const initials = username
    .split(' ')
    .map(word => word[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

  context.fillStyle = '#ffffff';
  context.font = 'bold 96px Arial, sans-serif';
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillText(initials, 128, 128);

  return new THREE.CanvasTexture(canvas);
}

/**
 * Calculate distance between two 3D positions
 */
export function distance3D(a: Vector3D, b: Vector3D): number {
  const dx = a.x - b.x;
  const dy = a.y - b.y;
  const dz = a.z - b.z;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

/**
 * Generate a random color for debugging/fallback
 */
export function randomColor(): number {
  return Math.floor(Math.random() * 0xffffff);
}
