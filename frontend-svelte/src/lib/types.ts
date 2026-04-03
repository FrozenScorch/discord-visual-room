/**
 * Frontend types for Discord Visual Room (SvelteKit)
 *
 * Shared types are duplicated inline from @discord-visual-room/types
 * since this is a separate frontend-svelte directory.
 */

// ─── Shared types (from shared/types/src/index.ts) ──────────────────────────

/**
 * Strict furniture types - MUST match frontend's pre-loaded 3D meshes exactly
 */
export type FurnitureType = "COMPUTER_DESK" | "COUCH_2_SEATER" | "COUCH_SINGLE" | "BAR_STOOL";

/**
 * User activity types from Discord
 */
export type ActivityType = "PLAYING" | "STREAMING" | "LISTENING" | "WATCHING" | "COMPETING";

/**
 * Complete scene graph that backend sends to frontend
 */
export interface SceneGraph {
  version: string;
  timestamp: number;
  users: UserNode[];
  furniture: FurnitureNode[];
  room: RoomConfig;
}

/**
 * A user in the 3D space
 */
export interface UserNode {
  id: string;
  username: string;
  displayName: string;
  avatar: string;
  position: Vector3D;
  rotation: Vector3D;
  activity?: UserActivity;
  isSpeaking: boolean;
  currentFurnitureId?: string;
}

/**
 * Furniture placement in the 3D space
 */
export interface FurnitureNode {
  id: string;
  type: FurnitureType;
  position: Vector3D;
  rotation: Vector3D;
  assignedUserId?: string;
  capacity: number;
}

/**
 * User's Discord activity
 */
export interface UserActivity {
  name: string;
  type: ActivityType;
  details?: string;
  state?: string;
}

/**
 * 3D vector for positions and rotations
 */
export interface Vector3D {
  x: number;
  y: number;
  z: number;
}

/**
 * Room configuration
 */
export interface RoomConfig {
  id: string;
  name: string;
  dimensions: {
    width: number;
    height: number;
    depth: number;
  };
  maxUsers: number;
}

/**
 * WebSocket message types
 */
export type WSMessageType =
  | "SCENE_UPDATE"
  | "USER_JOINED"
  | "USER_LEFT"
  | "USER_MOVED"
  | "ACTIVITY_CHANGED"
  | "ERROR";

export interface WSMessage {
  type: WSMessageType;
  timestamp: number;
  payload: unknown;
}

export interface SceneUpdateMessage extends WSMessage {
  type: "SCENE_UPDATE";
  payload: SceneGraph;
}

/**
 * LLM request/response types
 */
export interface LLMLayoutRequest {
  users: Array<{
    id: string;
    username: string;
    activity?: UserActivity;
  }>;
  roomCapacity: number;
  availableFurniture: FurnitureType[];
}

export interface LLMLayoutResponse {
  assignments: Array<{
    userId: string;
    furniture: FurnitureType;
  }>;
}

/**
 * Validation result for LLM responses
 */
export interface ValidationResult {
  valid: boolean;
  errors: string[];
  sanitizedAssignments?: Array<{
    userId: string;
    furniture: FurnitureType;
  }>;
}

// ─── Frontend-only types ────────────────────────────────────────────────────

/**
 * WebSocket connection state
 */
export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

/**
 * Configuration for the renderer / WebSocket connection
 */
export interface RendererConfig {
  wsUrl: string;
  roomName: string;
}
