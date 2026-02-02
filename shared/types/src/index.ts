/**
 * Shared TypeScript types for Discord Visual Room
 * These types are used by both frontend (Three.js) and backend (via ts2scala translation)
 */

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
  id: string; // Discord user ID
  username: string;
  displayName: string;
  avatar: string; // URL to Discord avatar
  position: Vector3D;
  rotation: Vector3D;
  activity?: UserActivity;
  isSpeaking: boolean; // Voice state
  currentFurnitureId?: string; // ID of furniture user is assigned to
}

/**
 * Furniture placement in the 3D space
 */
export interface FurnitureNode {
  id: string;
  type: FurnitureType;
  position: Vector3D;
  rotation: Vector3D;
  assignedUserId?: string; // User currently using this furniture
  capacity: number; // How many users can use this furniture
}

/**
 * User's Discord activity
 */
export interface UserActivity {
  name: string; // Game/activity name
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
  id: string; // Discord voice channel ID
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
