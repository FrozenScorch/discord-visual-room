package com.discordvisualroom.serialization

import com.discordvisualroom.model._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write

import scala.collection.immutable.Seq

/**
 * JSON serialization for SceneGraph and related types
 * Converts Scala case classes to JSON compatible with frontend TypeScript types
 */
object JsonSerializers {

  // Custom serializers for our types
  implicit val formats: Formats = DefaultFormats + new FurnitureTypeSerializer + new ActivityTypeSerializer + new WSMessageTypeSerializer

  /**
   * Serializer for FurnitureType
   */
  class FurnitureTypeSerializer extends CustomSerializer[FurnitureType](formats => (
    {
      case JString(typeName) =>
        FurnitureType.fromString(typeName).getOrElse(
          throw new IllegalArgumentException(s"Unknown furniture type: $typeName")
        )
      case JNull => FurnitureType.ComputerDesk // default
    },
    {
      case ft: FurnitureType => JString(ft.typeName)
    }
  ))

  /**
   * Serializer for ActivityType
   */
  class ActivityTypeSerializer extends CustomSerializer[ActivityType](formats => (
    {
      case JString(typeName) =>
        ActivityType.fromString(typeName).getOrElse(
          throw new IllegalArgumentException(s"Unknown activity type: $typeName")
        )
      case JNull => ActivityType.Playing // default
    },
    {
      case at: ActivityType => JString(at.typeName)
    }
  ))

  /**
   * Serializer for WSMessageType
   */
  class WSMessageTypeSerializer extends CustomSerializer[WSMessageType](formats => (
    {
      case JString(typeName) =>
        WSMessageType.fromString(typeName).getOrElse(
          throw new IllegalArgumentException(s"Unknown message type: $typeName")
        )
    },
    {
      case mt: WSMessageType => JString(mt.typeName)
    }
  ))

  /**
   * Write SceneGraph to JSON string
   */
  def writeSceneGraph(sceneGraph: SceneGraph): String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._

    val json: JObject =
      ("version" -> sceneGraph.version) ~
        ("timestamp" -> sceneGraph.timestamp) ~
        ("users" -> sceneGraph.users.map(writeUserNode)) ~
        ("furniture" -> sceneGraph.furniture.map(writeFurnitureNode)) ~
        ("room" -> writeRoomConfig(sceneGraph.room))

    compact(render(json))
  }

  private def writeUserNode(user: UserNode): JObject = {
    ("id" -> user.id) ~
      ("username" -> user.username) ~
      ("displayName" -> user.displayName) ~
      ("avatar" -> user.avatar) ~
      ("position" -> writeVector3D(user.position)) ~
      ("rotation" -> writeVector3D(user.rotation)) ~
      ("activity" -> user.activity.map(writeUserActivity)) ~
      ("isSpeaking" -> user.isSpeaking) ~
      ("currentFurnitureId" -> user.currentFurnitureId)
  }

  private def writeFurnitureNode(furniture: FurnitureNode): JObject = {
    ("id" -> furniture.id) ~
      ("type" -> furniture.furnitureType.typeName) ~
      ("position" -> writeVector3D(furniture.position)) ~
      ("rotation" -> writeVector3D(furniture.rotation)) ~
      ("assignedUserId" -> furniture.assignedUserId) ~
      ("capacity" -> furniture.capacity)
  }

  private def writeUserActivity(activity: UserActivity): JObject = {
    ("name" -> activity.name) ~
      ("type" -> activity.activityType.typeName) ~
      ("details" -> activity.details) ~
      ("state" -> activity.state)
  }

  private def writeVector3D(v: Vector3D): JObject = {
    ("x" -> v.x) ~
      ("y" -> v.y) ~
      ("z" -> v.z)
  }

  private def writeRoomConfig(config: RoomConfig): JObject = {
    ("id" -> config.id) ~
      ("name" -> config.name) ~
      ("dimensions" ->
        ("width" -> config.dimensions.width) ~
          ("height" -> config.dimensions.height) ~
          ("depth" -> config.dimensions.depth)) ~
      ("maxUsers" -> config.maxUsers)
  }

  /**
   * Parse SceneGraph from JSON string
   */
  def readSceneGraph(json: String): SceneGraph = {
    import org.json4s.jackson.JsonMethods._

    val parsed = parse(json)
    extractSceneGraph(parsed)
  }

  private def extractSceneGraph(json: JValue): SceneGraph = {
    implicit val formats: Formats = DefaultFormats

    SceneGraph(
      version = (json \ "version").extract[String],
      timestamp = (json \ "timestamp").extract[Long],
      users = (json \ "users").extract[Seq[JValue]].map(extractUserNode),
      furniture = (json \ "furniture").extract[Seq[JValue]].map(extractFurnitureNode),
      room = extractRoomConfig(json \ "room")
    )
  }

  private def extractUserNode(json: JValue): UserNode = {
    UserNode(
      id = (json \ "id").extract[String],
      username = (json \ "username").extract[String],
      displayName = (json \ "displayName").extract[String],
      avatar = (json \ "avatar").extract[String],
      position = extractVector3D(json \ "position"),
      rotation = extractVector3D(json \ "rotation"),
      activity = (json \ "activity").extractOpt[JValue].map(extractUserActivity),
      isSpeaking = (json \ "isSpeaking").extract[Boolean],
      currentFurnitureId = (json \ "currentFurnitureId").extractOpt[String]
    )
  }

  private def extractFurnitureNode(json: JValue): FurnitureNode = {
    FurnitureNode(
      id = (json \ "id").extract[String],
      furnitureType = FurnitureType.unsafeFromString((json \ "type").extract[String]),
      position = extractVector3D(json \ "position"),
      rotation = extractVector3D(json \ "rotation"),
      assignedUserId = (json \ "assignedUserId").extractOpt[String],
      capacity = (json \ "capacity").extract[Int]
    )
  }

  private def extractUserActivity(json: JValue): UserActivity = {
    UserActivity(
      name = (json \ "name").extract[String],
      activityType = ActivityType.fromString((json \ "type").extract[String])
        .getOrElse(ActivityType.Playing),
      details = (json \ "details").extractOpt[String],
      state = (json \ "state").extractOpt[String]
    )
  }

  private def extractVector3D(json: JValue): Vector3D = {
    Vector3D(
      x = (json \ "x").extract[Double],
      y = (json \ "y").extract[Double],
      z = (json \ "z").extract[Double]
    )
  }

  private def extractRoomConfig(json: JValue): RoomConfig = {
    val dimensions = json \ "dimensions"
    RoomConfig(
      id = (json \ "id").extract[String],
      name = (json \ "name").extract[String],
      dimensions = RoomDimensions(
        width = (dimensions \ "width").extract[Double],
        height = (dimensions \ "height").extract[Double],
        depth = (dimensions \ "depth").extract[Double]
      ),
      maxUsers = (json \ "maxUsers").extract[Int]
    )
  }

  /**
   * Write WebSocket message to JSON
   */
  def writeWSMessage(messageType: WSMessageType, payload: Option[String]): String = {
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._

    val json: JObject =
      ("type" -> messageType.typeName) ~
        ("timestamp" -> System.currentTimeMillis()) ~
        ("payload" -> payload.map(JString).getOrElse(JNull))

    compact(render(json))
  }

  /**
   * Parse WebSocket message from JSON
   */
  def readWSMessage(json: String): Option[(WSMessageType, Option[String])] = {
    import org.json4s.jackson.JsonMethods._

    try {
      val parsed = parse(json)
      val messageType = WSMessageType.fromString((parsed \ "type").extract[String])
      val payload = (parsed \ "payload").extractOpt[String]

      messageType.map((_, payload))
    } catch {
      case ex: Exception =>
        None
    }
  }
}
