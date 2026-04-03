package com.discordvisualroom.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable

/**
 * GuildManager - Top-level actor that manages a full Discord guild visualization.
 *
 * Responsibilities:
 * - Creates one RoomActor per voice channel
 * - Routes VoiceStateUpdate events to correct RoomActor
 * - On RoomActor state change, builds GuildSceneGraph and broadcasts to subscribers
 * - Manages WebSocket subscribers for guild-level scene updates
 */
object GuildManager extends LazyLogging {
  sealed trait Command

  // Lifecycle
  final case class Initialize(guildId: String, replyTo: ActorRef[InitResponse]) extends Command
  final case class InitResponse(success: Boolean, message: String)

  // Discord events (from DiscordBot)
  final case class UserJoinedVoiceChannel(
    userId: String,
    channelId: String,
    username: String,
    displayName: String,
    avatarUrl: String,
    activity: Option[UserActivity]
  ) extends Command

  final case class UserLeftVoiceChannel(userId: String, channelId: String) extends Command

  final case class UserActivityChanged(userId: String, channelId: String, activity: Option[UserActivity]) extends Command

  final case class UserSpeakingChanged(userId: String, channelId: String, isSpeaking: Boolean) extends Command

  // Guild metadata updates
  final case class GuildDiscovered(
    guildInfo: GuildInfo,
    voiceChannels: immutable.Seq[(String, String, Int)], // (id, name, position)
    textChannels: immutable.Seq[(String, String, Int)]
  ) extends Command

  // Room state updates (from child RoomActors)
  private[actors] final case class RoomStateUpdate(
    channelId: String,
    users: immutable.Seq[UserNode],
    furniture: immutable.Seq[FurnitureNode]
  ) extends Command

  // Queries
  final case class GetGuildScene(replyTo: ActorRef[GuildSceneGraph]) extends Command
  final case class SubscribeToUpdates(subscriber: ActorRef[GuildSceneGraph]) extends Command
  final case class UnsubscribeFromUpdates(subscriber: ActorRef[GuildSceneGraph]) extends Command

  def apply(llmConfig: LLMConfig): Behavior[Command] =
    Behaviors.setup(context => new GuildManager(context, llmConfig))
}

class GuildManager private (
  context: ActorContext[GuildManager.Command],
  llmConfig: LLMConfig
) extends AbstractBehavior[GuildManager.Command](context) with LazyLogging {

  import GuildManager._

  private var guildInfo: Option[GuildInfo] = None
  private var channelNames: Map[String, String] = Map.empty // channelId -> name
  private var roomPositions: Map[String, RoomPosition] = Map.empty
  private var roomActors: Map[String, ActorRef[RoomActor.Command]] = Map.empty
  private var textChannelMetas: immutable.Seq[RoomMeta] = immutable.Seq.empty
  private var subscribers: immutable.Set[ActorRef[GuildSceneGraph]] = immutable.Set.empty
  private var cachedUsers: Map[String, immutable.Seq[UserNode]] = Map.empty
  private var cachedFurniture: Map[String, immutable.Seq[FurnitureNode]] = Map.empty

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case Initialize(guildId, replyTo) =>
        logger.info(s"GuildManager initializing for guild $guildId")
        // Guild discovery happens asynchronously via DiscordBot -> GuildDiscovered
        replyTo ! InitResponse(true, "GuildManager initialized, waiting for guild discovery")
        this

      case GuildDiscovered(info, voiceChannels, txtChannels) =>
        handleGuildDiscovered(info, voiceChannels, txtChannels)
        this

      case UserJoinedVoiceChannel(userId, channelId, username, displayName, avatarUrl, activity) =>
        handleUserJoinedVoiceChannel(userId, channelId, username, displayName, avatarUrl, activity)
        this

      case UserLeftVoiceChannel(userId, channelId) =>
        handleUserLeftVoiceChannel(userId, channelId)
        this

      case UserActivityChanged(userId, channelId, activity) =>
        handleUserActivityChanged(userId, channelId, activity)
        this

      case UserSpeakingChanged(userId, channelId, isSpeaking) =>
        handleUserSpeakingChanged(userId, channelId, isSpeaking)
        this

      case RoomStateUpdate(channelId, users, furniture) =>
        handleRoomStateUpdate(channelId, users, furniture)
        this

      case GetGuildScene(replyTo) =>
        replyTo ! buildGuildScene()
        this

      case SubscribeToUpdates(subscriber) =>
        subscribers = subscribers + subscriber
        subscriber ! buildGuildScene()
        this

      case UnsubscribeFromUpdates(subscriber) =>
        subscribers = subscribers - subscriber
        this
    }
  }

  private def handleGuildDiscovered(
    info: GuildInfo,
    voiceChannels: immutable.Seq[(String, String, Int)],
    txtChannels: immutable.Seq[(String, String, Int)]
  ): Unit = {
    logger.info(s"Guild discovered: ${info.name} (${voiceChannels.size} voice channels, ${txtChannels.size} text channels)")
    guildInfo = Some(info)

    // Store channel names for later use in RoomData
    channelNames = voiceChannels.map { case (id, name, _) => id -> name }.toMap

    // Build text channel metas
    textChannelMetas = txtChannels.map { case (id, name, _) =>
      RoomMeta(id, name, ChannelType.Text, userCount = 0)
    }

    // Compute room positions using grid layout strategy
    roomPositions = RoomLayoutStrategy.computePositions(
      voiceChannels.map { case (id, _, pos) => id -> pos }
    )

    // Create RoomActor for each voice channel
    voiceChannels.foreach { case (id, name, pos) =>
      val roomConfig = RoomConfig(
        id = id,
        name = name,
        dimensions = RoomDimensions(20, 4, 20),
        maxUsers = 20
      )
      val roomRef = context.spawn(
        RoomActor(roomConfig, llmConfig, context.self),
        s"room-$id"
      )
      roomActors = roomActors + (id -> roomRef)
      logger.info(s"Created RoomActor for channel '$name' at position ${roomPositions.get(id)}")
    }

    // Initial broadcast (empty state)
    broadcastGuildScene()
  }

  private def handleUserJoinedVoiceChannel(
    userId: String,
    channelId: String,
    username: String,
    displayName: String,
    avatarUrl: String,
    activity: Option[UserActivity]
  ): Unit = {
    roomActors.get(channelId) match {
      case Some(ref) =>
        val userNode = UserNode(
          id = userId,
          username = username,
          displayName = displayName,
          avatar = avatarUrl,
          activity = activity,
          position = Vector3D(0, 0, 0),
          rotation = Vector3D(0, 0, 0),
          isSpeaking = false
        )
        ref ! RoomActor.UserJoined(userNode)
      case None =>
        logger.warn(s"User $userId joined unknown channel $channelId")
    }
  }

  private def handleUserLeftVoiceChannel(userId: String, channelId: String): Unit = {
    roomActors.get(channelId) match {
      case Some(ref) =>
        ref ! RoomActor.UserLeft(userId)
      case None =>
        logger.warn(s"User $userId left unknown channel $channelId")
    }
  }

  private def handleUserActivityChanged(userId: String, channelId: String, activity: Option[UserActivity]): Unit = {
    roomActors.get(channelId).foreach { ref =>
      ref ! RoomActor.UserActivityChanged(userId, activity)
    }
  }

  private def handleUserSpeakingChanged(userId: String, channelId: String, isSpeaking: Boolean): Unit = {
    roomActors.get(channelId).foreach { ref =>
      ref ! RoomActor.UserSpeakingChanged(userId, isSpeaking)
    }
  }

  private def handleRoomStateUpdate(
    channelId: String,
    users: immutable.Seq[UserNode],
    furniture: immutable.Seq[FurnitureNode]
  ): Unit = {
    cachedUsers = cachedUsers + (channelId -> users)
    cachedFurniture = cachedFurniture + (channelId -> furniture)
    broadcastGuildScene()
  }

  private def broadcastGuildScene(): Unit = {
    val scene = buildGuildScene()
    subscribers.foreach { sub =>
      try {
        sub ! scene
      } catch {
        case _: Exception =>
          subscribers = subscribers - sub
      }
    }
  }

  private def buildGuildScene(): GuildSceneGraph = {
    val info = guildInfo.getOrElse(GuildInfo("unknown", "Unknown Server"))

    val rooms = roomPositions.map { case (channelId, pos) =>
      val name = channelNames.getOrElse(channelId, channelId)
      val users = cachedUsers.getOrElse(channelId, immutable.Seq.empty).map { u =>
        // Offset user positions by room position (room-local -> world-space)
        u.copy(position = Vector3D(u.position.x + pos.x, u.position.y, u.position.z + pos.z))
      }
      val furniture = cachedFurniture.getOrElse(channelId, immutable.Seq.empty).map { f =>
        f.copy(position = Vector3D(f.position.x + pos.x, f.position.y, f.position.z + pos.z))
      }
      RoomData(channelId, name, ChannelType.Voice, pos, users, furniture)
    }.toSeq

    GuildSceneGraph.create(info, rooms, textChannelMetas)
  }
}
