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
 * 3D vector for positions and rotations
 */
export interface Vector3D {
  x: number;
  y: number;
  z: number;
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

// ─── Guild-level types (v2.0.0) ────────────────────────────────────────────

export type ChannelType = "VOICE" | "TEXT";

export interface GuildRole {
  id: string;
  name: string;
  color: number;
  position: number;
}

export interface GuildInfo {
  id: string;
  name: string;
  icon?: string;
  roles: GuildRole[];
  onlineMemberCount: number;
}

export interface RoomPosition {
  x: number;
  z: number;
}

export interface RoomMeta {
  id: string;
  name: string;
  channelType: ChannelType;
  position?: RoomPosition;
  userCount: number;
}

export interface RoomData {
  id: string;
  name: string;
  channelType: ChannelType;
  position: RoomPosition;
  users: UserNode[];
  furniture: FurnitureNode[];
}

/**
 * Complete guild scene graph (v2.0.0) that backend sends to frontend
 */
export interface GuildSceneGraph {
  version: string;
  timestamp: number;
  guild: GuildInfo;
  rooms: RoomData[];
  roomsMeta: RoomMeta[];
}

// ─── Legacy types (kept for backward compatibility) ─────────────────────────

/**
 * Room configuration (legacy v1, used only as fallback)
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
 * Legacy single-room scene graph (v1.0.0)
 */
export interface SceneGraph {
  version: string;
  timestamp: number;
  users: UserNode[];
  furniture: FurnitureNode[];
  room: RoomConfig;
}

// ─── WebSocket message types ────────────────────────────────────────────────

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
  payload: GuildSceneGraph | SceneGraph;
}

// ─── Frontend-only types ────────────────────────────────────────────────────

/**
 * WebSocket connection state
 */
export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

/**
 * Camera view mode for guild scene
 */
export type ViewMode = 'overview' | 'room';

/**
 * Camera focus target for room navigation
 */
export interface CameraTarget {
  roomId: string;
  position: RoomPosition;
  roomName: string;
}
