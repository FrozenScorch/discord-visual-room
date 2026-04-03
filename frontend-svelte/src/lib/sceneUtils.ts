import * as THREE from 'three';

// ---------------------------------------------------------------------------
// Types (ported from @discord-visual-room/types; self-contained for lib use)
// ---------------------------------------------------------------------------

/** 3D vector used in backend communication */
export interface Vector3D {
  x: number;
  y: number;
  z: number;
}

/** Target position with x/y/z for smooth lerping */
export interface PositionTarget {
  x: number;
  y: number;
  z: number;
}

// ---------------------------------------------------------------------------
// Vector conversion helpers
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Interpolation
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Sprite helpers
// ---------------------------------------------------------------------------

/**
 * Create a clean text sprite for user labels - rounded pill with soft shadow
 */
export function createTextSprite(
  text: string,
  fontSize: number = 28,
  color: string = '#ffffff',
  backgroundColor: string = 'rgba(30, 30, 50, 0.75)'
): THREE.Sprite {
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  if (!context) {
    const material = new THREE.SpriteMaterial({ color: 0xffffff });
    return new THREE.Sprite(material);
  }

  canvas.width = 512;
  canvas.height = 96;

  // Rounded pill background with soft glow
  const pillHeight = 56;
  const pillY = (canvas.height - pillHeight) / 2;
  const radius = pillHeight / 2;

  // Measure text to size pill
  context.font = `600 ${fontSize}px "Segoe UI", "SF Pro", Arial, sans-serif`;
  const textWidth = context.measureText(text).width;
  const pillWidth = Math.min(textWidth + 40, canvas.width - 20);
  const pillX = (canvas.width - pillWidth) / 2;

  // Soft shadow
  context.shadowColor = 'rgba(0, 0, 0, 0.4)';
  context.shadowBlur = 8;
  context.shadowOffsetY = 2;

  // Draw pill
  context.fillStyle = backgroundColor;
  context.beginPath();
  context.roundRect(pillX, pillY, pillWidth, pillHeight, radius);
  context.fill();

  // Reset shadow for text
  context.shadowColor = 'transparent';
  context.shadowBlur = 0;
  context.shadowOffsetY = 0;

  // Draw text
  context.fillStyle = color;
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillText(text, canvas.width / 2, canvas.height / 2);

  const texture = new THREE.CanvasTexture(canvas);
  texture.minFilter = THREE.LinearFilter;
  texture.magFilter = THREE.LinearFilter;

  const material = new THREE.SpriteMaterial({
    map: texture,
    transparent: true,
    depthTest: false,
  });

  const sprite = new THREE.Sprite(material);
  sprite.scale.set(3, 0.56, 1);

  return sprite;
}

/**
 * Create an activity label sprite - smaller, muted style
 */
export function createActivitySprite(activityText: string): THREE.Sprite {
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  if (!context) {
    const material = new THREE.SpriteMaterial({ color: 0xffffff });
    return new THREE.Sprite(material);
  }

  canvas.width = 512;
  canvas.height = 64;

  context.font = '500 20px "Segoe UI", "SF Pro", Arial, sans-serif';
  const textWidth = context.measureText(activityText).width;
  const pillWidth = Math.min(textWidth + 30, canvas.width - 10);
  const pillHeight = 36;
  const pillX = (canvas.width - pillWidth) / 2;
  const pillY = (canvas.height - pillHeight) / 2;

  // Subtle colored pill
  context.fillStyle = 'rgba(88, 101, 242, 0.6)';
  context.beginPath();
  context.roundRect(pillX, pillY, pillWidth, pillHeight, pillHeight / 2);
  context.fill();

  context.fillStyle = 'rgba(255, 255, 255, 0.9)';
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillText(activityText, canvas.width / 2, canvas.height / 2);

  const texture = new THREE.CanvasTexture(canvas);
  texture.minFilter = THREE.LinearFilter;
  texture.magFilter = THREE.LinearFilter;

  const material = new THREE.SpriteMaterial({
    map: texture,
    transparent: true,
    depthTest: false,
  });

  const sprite = new THREE.Sprite(material);
  sprite.scale.set(2.5, 0.32, 1);

  return sprite;
}

/**
 * Create a speaking ring indicator - glowing torus
 */
export function createSpeakingIndicator(): THREE.Mesh {
  const geometry = new THREE.TorusGeometry(0.55, 0.04, 12, 32);
  const material = new THREE.MeshBasicMaterial({
    color: 0x43b581, // Discord green
    transparent: true,
    opacity: 0.8,
  });
  const ring = new THREE.Mesh(geometry, material);
  ring.rotation.x = -Math.PI / 2;
  return ring;
}

// ---------------------------------------------------------------------------
// Texture helpers
// ---------------------------------------------------------------------------

/**
 * Load an image texture from a URL with CORS proxy fallback.
 * For Discord CDN URLs, falls back to images.weserv.nl proxy and then a
 * size-parameterised variant before giving up.
 */
export async function loadTexture(url: string): Promise<THREE.Texture | null> {
  const tryLoad = (loadUrl: string): Promise<THREE.Texture | null> => {
    return new Promise((resolve) => {
      const loader = new THREE.TextureLoader();
      loader.setCrossOrigin('anonymous');
      loader.load(
        loadUrl,
        (texture) => {
          texture.generateMipmaps = true;
          texture.minFilter = THREE.LinearMipmapLinearFilter;
          texture.magFilter = THREE.LinearFilter;
          resolve(texture);
        },
        undefined,
        () => resolve(null)
      );
    });
  };

  // Try direct URL first
  let texture = await tryLoad(url);
  if (texture) return texture;

  // For Discord CDN URLs, try images.weserv.nl CORS proxy
  if (url.includes('cdn.discordapp.com')) {
    const proxyUrl = `https://images.weserv.nl/?url=${encodeURIComponent(url)}`;
    texture = await tryLoad(proxyUrl);
    if (texture) return texture;

    // Also try with a size parameter
    const sizedUrl = url.includes('?') ? `${url}&size=128` : `${url}?size=128`;
    texture = await tryLoad(sizedUrl);
    if (texture) return texture;

    // Proxy + size
    const proxySizedUrl = `https://images.weserv.nl/?url=${encodeURIComponent(sizedUrl)}`;
    texture = await tryLoad(proxySizedUrl);
    if (texture) return texture;
  }

  return null;
}

/**
 * Create a default avatar texture - Discord-style colored circle with initials
 */
export function createDefaultAvatarTexture(username: string): THREE.CanvasTexture {
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');

  if (!context) {
    return new THREE.CanvasTexture(document.createElement('canvas'));
  }

  canvas.width = 256;
  canvas.height = 256;

  // Generate a consistent pastel color based on username
  const hash = username.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
  const hue = hash % 360;

  // Soft gradient circle
  const gradient = context.createRadialGradient(128, 128, 0, 128, 128, 128);
  gradient.addColorStop(0, `hsl(${hue}, 55%, 65%)`);
  gradient.addColorStop(1, `hsl(${hue}, 50%, 50%)`);

  context.fillStyle = gradient;
  context.beginPath();
  context.arc(128, 128, 120, 0, Math.PI * 2);
  context.fill();

  // Subtle inner shadow
  const innerShadow = context.createRadialGradient(128, 100, 60, 128, 128, 120);
  innerShadow.addColorStop(0, 'rgba(255, 255, 255, 0.15)');
  innerShadow.addColorStop(1, 'rgba(0, 0, 0, 0.1)');
  context.fillStyle = innerShadow;
  context.beginPath();
  context.arc(128, 128, 120, 0, Math.PI * 2);
  context.fill();

  // Draw initials
  const initials = username
    .split(' ')
    .map((word) => word[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

  context.fillStyle = '#ffffff';
  context.font = 'bold 88px "Segoe UI", "SF Pro", Arial, sans-serif';
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.shadowColor = 'rgba(0, 0, 0, 0.2)';
  context.shadowBlur = 4;
  context.shadowOffsetY = 2;
  context.fillText(initials || '?', 128, 132);

  const texture = new THREE.CanvasTexture(canvas);
  texture.minFilter = THREE.LinearFilter;
  return texture;
}

// ---------------------------------------------------------------------------
// Math helpers
// ---------------------------------------------------------------------------

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
