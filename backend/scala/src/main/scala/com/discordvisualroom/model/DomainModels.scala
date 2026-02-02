package com.discordvisualroom.model

import scala.collection.immutable.Seq

/**
 * Domain models matching the TypeScript schema in shared/types/src/index.ts
 * These case classes represent the complete state of the visual room.
 */

/**
 * Strict furniture types - MUST match frontend's pre-loaded 3D meshes exactly
 */
sealed trait FurnitureType {
  def typeName: String
}

object FurnitureType {
  case object ComputerDesk extends FurnitureType { val typeName: String = "COMPUTER_DESK" }
  case object Couch2Seater extends FurnitureType { val typeName: String = "COUCH_2_SEATER" }
  case object CouchSingle extends FurnitureType { val typeName: String = "COUCH_SINGLE" }
  case object BarStool extends FurnitureType { val typeName: String = "BAR_STOOL" }

  val allValues: Seq[FurnitureType] = Seq(ComputerDesk, Couch2Seater, CouchSingle, BarStool)

  def fromString(value: String): Option[FurnitureType] = allValues.find(_.typeName == value)

  /**
   * Parse furniture type from string, with validation
   * @throws IllegalArgumentException if type is invalid
   */
  def unsafeFromString(value: String): FurnitureType =
    fromString(value).getOrElse(throw new IllegalArgumentException(s"Invalid furniture type: $value"))
}

/**
 * User activity types from Discord
 */
sealed trait ActivityType {
  def typeName: String
}

object ActivityType {
  case object Playing extends ActivityType { val typeName: String = "PLAYING" }
  case object Streaming extends ActivityType { val typeName: String = "STREAMING" }
  case object Listening extends ActivityType { val typeName: String = "LISTENING" }
  case object Watching extends ActivityType { val typeName: String = "WATCHING" }
  case object Competing extends ActivityType { val typeName: String = "COMPETING" }

  def fromString(value: String): Option[ActivityType] =
    value.toUpperCase match {
      case "PLAYING" => Some(Playing)
      case "STREAMING" => Some(Streaming)
      case "LISTENING" => Some(Listening)
      case "WATCHING" => Some(Watching)
      case "COMPETING" => Some(Competing)
      case _ => None
    }
}

/**
 * 3D vector for positions and rotations
 */
case class Vector3D(x: Double, y: Double, z: Double) {
  def +(other: Vector3D): Vector3D = Vector3D(x + other.x, y + other.y, z + other.z)
  def -(other: Vector3D): Vector3D = Vector3D(x - other.x, y - other.y, z - other.z)
  def *(scalar: Double): Vector3D = Vector3D(x * scalar, y * scalar, z * scalar)
  def distanceTo(other: Vector3D): Double = math.sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y) + (z - other.z) * (z - other.z))
}

object Vector3D {
  val zero: Vector3D = Vector3D(0, 0, 0)
  val up: Vector3D = Vector3D(0, 1, 0)
  val forward: Vector3D = Vector3D(0, 0, 1)
  val right: Vector3D = Vector3D(1, 0, 0)
}

/**
 * User's Discord activity
 */
case class UserActivity(
  name: String,
  activityType: ActivityType,
  details: Option[String] = None,
  state: Option[String] = None
)

/**
 * A user in the 3D space
 */
case class UserNode(
  id: String,
  username: String,
  displayName: String,
  avatar: String,
  position: Vector3D,
  rotation: Vector3D,
  activity: Option[UserActivity] = None,
  isSpeaking: Boolean = false,
  currentFurnitureId: Option[String] = None
)

/**
 * Furniture placement in the 3D space
 */
case class FurnitureNode(
  id: String,
  furnitureType: FurnitureType,
  position: Vector3D,
  rotation: Vector3D,
  assignedUserId: Option[String] = None,
  capacity: Int = 1
) {
  def isAvailable: Boolean = assignedUserId.isEmpty || capacity > 1
  def assignUser(userId: String): FurnitureNode = copy(assignedUserId = Some(userId))
  def unassignUser: FurnitureNode = copy(assignedUserId = None)
}

/**
 * Room dimensions
 */
case class RoomDimensions(width: Double, height: Double, depth: Double)

/**
 * Room configuration
 */
case class RoomConfig(
  id: String,
  name: String,
  dimensions: RoomDimensions,
  maxUsers: Int
)

/**
 * Complete scene graph that backend sends to frontend
 */
case class SceneGraph(
  version: String,
  timestamp: Long,
  users: Seq[UserNode],
  furniture: Seq[FurnitureNode],
  room: RoomConfig
)

object SceneGraph {
  val currentVersion: String = "1.0.0"

  def create(
    users: Seq[UserNode],
    furniture: Seq[FurnitureNode],
    room: RoomConfig
  ): SceneGraph = SceneGraph(
    version = currentVersion,
    timestamp = System.currentTimeMillis(),
    users = users,
    furniture = furniture,
    room = room
  )
}

/**
 * WebSocket message types
 */
sealed trait WSMessageType {
  def typeName: String
}

object WSMessageType {
  case object SceneUpdate extends WSMessageType { val typeName: String = "SCENE_UPDATE" }
  case object UserJoined extends WSMessageType { val typeName: String = "USER_JOINED" }
  case object UserLeft extends WSMessageType { val typeName: String = "USER_LEFT" }
  case object UserMoved extends WSMessageType { val typeName: String = "USER_MOVED" }
  case object ActivityChanged extends WSMessageType { val typeName: String = "ACTIVITY_CHANGED" }
  case object Error extends WSMessageType { val typeName: String = "ERROR" }

  def fromString(value: String): Option[WSMessageType] =
    value match {
      case "SCENE_UPDATE" => Some(SceneUpdate)
      case "USER_JOINED" => Some(UserJoined)
      case "USER_LEFT" => Some(UserLeft)
      case "USER_MOVED" => Some(UserMoved)
      case "ACTIVITY_CHANGED" => Some(ActivityChanged)
      case "ERROR" => Some(Error)
      case _ => None
    }
}

/**
 * Base WebSocket message
 */
case class WSMessage(
  messageType: WSMessageType,
  timestamp: Long,
  payload: Option[String] // JSON payload
)

/**
 * Scene update message with full scene graph
 */
case class SceneUpdateMessage(
  timestamp: Long,
  sceneGraph: SceneGraph
)

/**
 * LLM request for furniture layout
 */
case class LLMLayoutRequest(
  users: Seq[LLMLayoutUser],
  roomCapacity: Int,
  availableFurniture: Seq[String]
)

case class LLMLayoutUser(
  id: String,
  username: String,
  activity: Option[UserActivity]
)

/**
 * LLM response for furniture assignment
 */
case class LLMLayoutResponse(
  assignments: Seq[FurnitureAssignment]
)

case class FurnitureAssignment(
  userId: String,
  furniture: String // FurnitureType.typeName
)

/**
 * Validation result for LLM responses
 */
case class ValidationResult(
  valid: Boolean,
  errors: Seq[String],
  sanitizedAssignments: Option[Seq[FurnitureAssignment]] = None
)

/**
 * Actor messages for RoomActor
 */
sealed trait RoomCommand

object RoomCommand {
  case class Initialize(config: RoomConfig) extends RoomCommand
  case class AddUser(user: UserNode) extends RoomCommand
  case class RemoveUser(userId: String) extends RoomCommand
  case class UpdateUserActivity(userId: String, activity: Option[UserActivity]) extends RoomCommand
  case class UpdateUserSpeaking(userId: String, isSpeaking: Boolean) extends RoomCommand
  case class UpdateFurnitureAssignments(assignments: Seq[FurnitureAssignment]) extends RoomCommand
  case object RequestSceneGraph extends RoomCommand
  case object RequestLayoutUpdate extends RoomCommand
}

/**
 * Actor messages for UserManager
 */
sealed trait UserManagerMessage

object UserManagerMessage {
  case class TrackUser(user: UserNode) extends UserManagerMessage
  case class UntrackUser(userId: String) extends UserManagerMessage
  case class UpdateActivity(userId: String, activity: Option[UserActivity]) extends UserManagerMessage
  case class UpdateSpeakingState(userId: String, isSpeaking: Boolean) extends UserManagerMessage
  case object GetActiveUsers extends UserManagerMessage
}

/**
 * Actor messages for FurnitureManager
 */
sealed trait FurnitureManagerMessage

object FurnitureManagerMessage {
  case class GenerateLayout(users: Seq[UserNode], roomConfig: RoomConfig) extends FurnitureManagerMessage
  case class AssignFurniture(assignments: Seq[FurnitureAssignment]) extends FurnitureManagerMessage
  case object GetFurniture extends FurnitureManagerMessage
  case class UserLeft(userId: String) extends FurnitureManagerMessage
}

/**
 * Actor responses
 */
sealed trait RoomResponse

object RoomResponse {
  case class SceneGraphState(sceneGraph: SceneGraph) extends RoomResponse
  case class UserAdded(userId: String) extends RoomResponse
  case class UserRemoved(userId: String) extends RoomResponse
  case class LayoutUpdated(furnitureCount: Int) extends RoomResponse
  case class OperationFailed(reason: String) extends RoomResponse
}

/**
 * Configuration for the application
 */
case class AppConfig(
  discord: DiscordConfig,
  llm: LLMConfig,
  websocket: WebSocketConfig,
  room: RoomConfig
)

case class DiscordConfig(
  token: String,
  voiceChannelId: String,
  guildId: String
)

case class LLMConfig(
  baseUrl: String,
  timeoutMs: Int,
  maxRetries: Int
)

case class WebSocketConfig(
  host: String,
  port: Int,
  path: String
)
