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

  // Text channel events (from DiscordBot)
  final case class UserTextActivity(
    userId: String,
    username: String,
    displayName: String,
    avatarUrl: String,
    channelId: String,
    activity: Option[UserActivity]
  ) extends Command

  final case class UserTextPresenceUpdate(
    userId: String,
    activity: Option[UserActivity]
  ) extends Command

  // Internal
  private[actors] final case object PruneInactiveTextUsers extends Command

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

  // Text channel tracking: channelId -> Map[userId -> (UserNode, lastSeenEpochMs)]
  private var textChannelUsers: Map[String, Map[String, (UserNode, Long)]] = Map.empty
  private var textChannelIds: Set[String] = Set.empty
  private var pruneTimerScheduled = false
  private val TEXT_CHANNEL_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

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

      case UserTextActivity(userId, username, displayName, avatarUrl, channelId, activity) =>
        handleUserTextActivity(userId, username, displayName, avatarUrl, channelId, activity)
        this

      case UserTextPresenceUpdate(userId, activity) =>
        handleUserTextPresenceUpdate(userId, activity)
        this

      case PruneInactiveTextUsers =>
        handlePruneInactiveTextUsers()
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
    channelNames = (voiceChannels ++ txtChannels).map { case (id, name, _) => id -> name }.toMap

    // Store text channel IDs
    textChannelIds = txtChannels.map { case (id, _, _) => id }.toSet

    // Build text channel metas
    textChannelMetas = txtChannels.map { case (id, name, _) =>
      RoomMeta(id, name, ChannelType.Text, userCount = 0)
    }

    // Compute room positions using grid layout strategy
    roomPositions = RoomLayoutStrategy.computePositions(
      voiceChannels.map { case (id, _, pos) => id -> pos }
    ) ++ RoomLayoutStrategy.computeTextChannelPositions(
      txtChannels.map { case (id, _, pos) => id -> pos },
      voiceChannels.size
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

    // Voice channel rooms (existing logic)
    val voiceRooms = roomPositions.collect { case (channelId, pos) if roomActors.contains(channelId) =>
      val name = channelNames.getOrElse(channelId, channelId)
      val users = cachedUsers.getOrElse(channelId, immutable.Seq.empty).map { u =>
        u.copy(position = Vector3D(u.position.x + pos.x, u.position.y, u.position.z + pos.z))
      }
      val furniture = cachedFurniture.getOrElse(channelId, immutable.Seq.empty).map { f =>
        f.copy(position = Vector3D(f.position.x + pos.x, f.position.y, f.position.z + pos.z))
      }
      RoomData(channelId, name, ChannelType.Voice, pos, users, furniture)
    }.toSeq

    // Text channel rooms (users who recently sent messages)
    val textRooms = textChannelUsers.flatMap { case (channelId, activeUsers) =>
      roomPositions.get(channelId).map { pos =>
        val name = channelNames.getOrElse(channelId, channelId)
        val usersList = activeUsers.values.toSeq.sortBy(_._2).reverse.map { case (userNode, _) =>
          userNode
        }
        // Position users in circular layout
        val users = usersList.zipWithIndex.map { case (userNode, index) =>
          val n = usersList.size
          val angle = (2.0 * math.Pi * index) / n
          val radius = math.max(3.0, n * 0.8)
          userNode.copy(
            position = Vector3D(pos.x + math.cos(angle) * radius, 0.65, pos.z + math.sin(angle) * radius)
          )
        }
        val furniture = generateTextChannelFurniture(channelId, users, pos)
        RoomData(channelId, name, ChannelType.Text, pos, users, furniture)
      }
    }.toSeq

    // Empty text channels stay in roomsMeta
    val activeTextIds = textChannelUsers.keySet
    val emptyTextMetas = textChannelMetas.filterNot(m => activeTextIds.contains(m.id))

    GuildSceneGraph.create(info, voiceRooms ++ textRooms, emptyTextMetas)
  }

  // ── Text channel handlers ──────────────────────────────────────────────

  private def handleUserTextActivity(
    userId: String, username: String, displayName: String,
    avatarUrl: String, channelId: String, activity: Option[UserActivity]
  ): Unit = {
    if (!textChannelIds.contains(channelId)) return

    val userNode = UserNode(
      id = userId, username = username, displayName = displayName,
      avatar = avatarUrl, position = Vector3D(0, 0, 0), rotation = Vector3D(0, 0, 0),
      activity = activity, isSpeaking = false
    )

    val now = System.currentTimeMillis()
    val channelUsers = textChannelUsers.getOrElse(channelId, Map.empty)
    textChannelUsers = textChannelUsers.updated(channelId, channelUsers.updated(userId, (userNode, now)))

    ensurePruneTimer()
    broadcastGuildScene()
  }

  private def handleUserTextPresenceUpdate(userId: String, activity: Option[UserActivity]): Unit = {
    var changed = false
    val updated = scala.collection.mutable.Map.empty[String, Map[String, (UserNode, Long)]]
    textChannelUsers.foreach { case (chId, users) =>
      users.get(userId) match {
        case Some((userNode, ts)) =>
          changed = true
          updated(chId) = users.updated(userId, (userNode.copy(activity = activity), ts))
        case None =>
          updated(chId) = users
      }
    }
    textChannelUsers = updated.toMap
    if (changed) broadcastGuildScene()
  }

  private def handlePruneInactiveTextUsers(): Unit = {
    pruneTimerScheduled = false
    val now = System.currentTimeMillis()
    var changed = false

    val pruned = textChannelUsers.map { case (chId, users) =>
      val active = users.filter { case (_, (_, lastSeen)) =>
        now - lastSeen < TEXT_CHANNEL_TIMEOUT_MS
      }
      if (active.size != users.size) changed = true
      (chId, active)
    }.filter { case (_, users) => users.nonEmpty }

    textChannelUsers = pruned.toMap

    if (changed) {
      broadcastGuildScene()
    }
    // Reschedule if there are still text channel users
    if (textChannelUsers.values.exists(_.nonEmpty)) {
      ensurePruneTimer()
    }
  }

  private def ensurePruneTimer(): Unit = {
    if (!pruneTimerScheduled) {
      pruneTimerScheduled = true
      import akka.actor.typed.scaladsl.AskPattern._
      import scala.concurrent.duration._
      context.scheduleOnce(1.minute, context.self, PruneInactiveTextUsers)
    }
  }

  private def generateTextChannelFurniture(
    channelId: String,
    users: immutable.Seq[UserNode],
    roomPos: RoomPosition
  ): immutable.Seq[FurnitureNode] = {
    users.zipWithIndex.map { case (user, index) =>
      val n = users.size
      val angle = (2.0 * math.Pi * index) / n
      val radius = math.max(3.0, n * 0.8)
      FurnitureNode(
        id = s"tf-$channelId-$index",
        furnitureType = FurnitureType.CouchSingle,
        position = Vector3D(roomPos.x + math.cos(angle) * radius, 0, roomPos.z + math.sin(angle) * radius),
        rotation = Vector3D(0, -angle + math.Pi, 0),
        assignedUserId = Some(user.id),
        capacity = 1
      )
    }
  }
}
