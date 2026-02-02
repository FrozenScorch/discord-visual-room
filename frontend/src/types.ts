/**
 * Frontend types - re-exports shared types from @discord-visual-room/types
 */

export type {
  FurnitureType,
  ActivityType,
  SceneGraph,
  UserNode,
  FurnitureNode,
  UserActivity,
  Vector3D,
  RoomConfig,
  WSMessage,
  WSMessageType,
  SceneUpdateMessage,
  LLMLayoutRequest,
  LLMLayoutResponse,
  ValidationResult,
} from '@discord-visual-room/types';

/**
 * Frontend-specific types
 */

/**
 * Three.js object wrapper for tracking scene objects
 */
export interface SceneObject {
  id: string;
  type: 'furniture' | 'user';
  mesh: THREE.Object3D;
  data: FurnitureNode | UserNode;
}

/**
 * Target position for smooth lerping
 */
export interface PositionTarget {
  x: number;
  y: number;
  z: number;
}

/**
 * Configuration for the frontend renderer
 */
export interface RendererConfig {
  wsUrl: string;
  cameraFov: number;
  cameraNear: number;
  cameraFar: number;
  lerpFactor: number;
}

/**
 * WebSocket connection state
 */
export type ConnectionState = 'connecting' | 'connected' | 'disconnected' | 'error';

/**
 * Texture cache for avatar images
 */
export interface AvatarCache {
  [userId: string]: THREE.Texture;
}
