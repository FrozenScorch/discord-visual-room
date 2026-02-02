package com.discordvisualroom.discord

import com.discordvisualroom.actors.RoomActor
import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging
import discord4j.common.util.Snowflake
import discord4j.core.{DiscordClient, DiscordClientBuilder}
import discord4j.core.`object`.entity.{Member, User}
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.presence.{Activity, Presence}
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.message.MessageCreateEvent

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * Discord bot integration using discorde4j
 * Listens to voice channel events and tracks user activities
 */
object DiscordBot extends LazyLogging {

  /**
   * Create and start the Discord bot
   */
  def start(
    token: String,
    voiceChannelId: String,
    guildId: String,
    roomActor: RoomActor.Command
  )(implicit ec: ExecutionContext): Future[DiscordClient] = {

    logger.info("Starting Discord bot...")

    val client = DiscordClientBuilder.create(token).build()

    // Login and register event handlers
    client.login().flatMap { gateway =>
      logger.info("Discord bot connected successfully")

      // Event dispatcher
      val eventDispatcher = gateway.getEventDispatcher

      // Handle ready event
      eventDispatcher.on(classOf[ReadyEvent]).subscribe { event =>
        val self = event.getSelf
        logger.info(s"Bot logged in as: ${self.getUsername}#${self.getDiscriminator}")
      }

      // Handle voice state updates (users joining/leaving voice channel)
      eventDispatcher.on(classOf[VoiceStateUpdateEvent]).subscribe { event =>
        handleVoiceStateUpdate(event, voiceChannelId, guildId, roomActor)
      }

      // Initial sync of users in voice channel
      syncInitialVoiceState(gateway, voiceChannelId, guildId, roomActor)

      Future.successful(client)
    }
  }

  /**
   * Handle voice state update events
   */
  private def handleVoiceStateUpdate(
    event: VoiceStateUpdateEvent,
    targetVoiceChannelId: String,
    guildId: String,
    roomActor: RoomActor.Command
  ): Unit = {

    val oldState = event.getOld
    val newState = event.getCurrent

    val voiceChannelId = newState.getChannelId.map(_.asString()).orElse(oldState.flatMap(_.getChannelId.map(_.asString())))

    // Check if event is for our target voice channel
    if (voiceChannelId.contains(targetVoiceChannelId)) {
      val userId = newState.getUserId.asString()
      val member = newState.getMember

      if (newState.getChannelId.map(_.asString()).contains(targetVoiceChannelId)) {
        // User joined the voice channel
        logger.info(s"User joined voice channel: $userId")
        member.flatMap(_.toFuture).foreach { m =>
          handleUserJoined(m, roomActor)
        }
      } else {
        // User left the voice channel
        logger.info(s"User left voice channel: $userId")
        handleUserLeft(userId, roomActor)
      }
    }
  }

  /**
   * Handle user joining voice channel
   */
  private def handleUserJoined(member: Member, roomActor: RoomActor.Command): Unit = {
    val user = member.getUser
    val userId = user.getId.asString()

    val userNode = UserNode(
      id = userId,
      username = user.getUsername,
      displayName = member.getDisplayName,
      avatar = user.getAvatarUrl(s"https://cdn.discordapp.com/avatars/$userId/%s.png"),
      position = Vector3D(0, 0, 0),
      rotation = Vector3D(0, 0, 0),
      activity = extractActivity(member),
      isSpeaking = false
    )

    // Send to RoomActor
    roomActor match {
      case actorRef: akka.actor.typed.ActorRef[_] =>
        actorRef.asInstanceOf[akka.actor.typed.ActorRef[RoomActor.Command]] ! RoomActor.UserJoined(userNode, null)
      case _ =>
        logger.warn("RoomActor is not an ActorRef, cannot send message")
    }
  }

  /**
   * Handle user leaving voice channel
   */
  private def handleUserLeft(userId: String, roomActor: RoomActor.Command): Unit = {
    roomActor match {
      case actorRef: akka.actor.typed.ActorRef[_] =>
        actorRef.asInstanceOf[akka.actor.typed.ActorRef[RoomActor.Command]] ! RoomActor.UserLeft(userId, null)
      case _ =>
        logger.warn("RoomActor is not an ActorRef, cannot send message")
    }
  }

  /**
   * Extract user activity from Discord presence
   */
  private def extractActivity(member: Member): Option[UserActivity] = {
    Try(member.getPresence.block()).toOption.flatMap { presence =>
      presence match {
        case Presence.ONLINE =>
          val activity = member.getActivities
          if (!activity.isEmpty) {
            val discordActivity = activity.get(0)
            Some(UserActivity(
              name = discordActivity.getName,
              activityType = mapActivityType(discordActivity.getType),
              details = Option(discordActivity.getDetails).map(_.toString),
              state = Option(discordActivity.getState).map(_.toString)
            ))
          } else {
            None
          }
        case _ =>
          None
      }
    }
  }

  /**
   * Map Discord activity type to our ActivityType
   */
  private def mapActivityType(discordType: Activity.Type): ActivityType = {
    discordType match {
      case Activity.Type.PLAYING => ActivityType.Playing
      case Activity.Type.STREAMING => ActivityType.Streaming
      case Activity.Type.LISTENING => ActivityType.Listening
      case Activity.Type.WATCHING => ActivityType.Watching
      case Activity.Type.COMPETING => ActivityType.Competing
      case _ => ActivityType.Playing // Default
    }
  }

  /**
   * Sync initial voice state (users already in the channel when bot starts)
   */
  private def syncInitialVoiceState(
    gateway: discord4j.gateway.GatewayDiscordClient,
    voiceChannelId: String,
    guildId: String,
    roomActor: RoomActor.Command
  ): Unit = {

    logger.info("Syncing initial voice state...")

    Try {
      val guild = gateway.getGuildById(Snowflake.of(guildId)).block()
      val voiceChannel = guild.getChannelById(Snowflake.of(voiceChannelId)).block()

      voiceChannel.asVoiceChannel().flatMap { vc =>
        vc.getVoiceStates.flatMap(_.toFuture())
      }.foreach { voiceStates =>
        logger.info(s"Found ${voiceStates.size()} users in voice channel")

        voiceStates.asScala.foreach { voiceState =>
          val memberFuture = voiceState.getMember.toFuture()
          memberFuture.foreach { member =>
            handleUserJoined(member, roomActor)
          }
        }
      }
    }.recover {
      case ex: Exception =>
        logger.error("Failed to sync initial voice state", ex)
    }
  }

  /**
   * Activity monitoring service
   * Periodically polls for activity changes
   */
  def startActivityMonitoring(
    gateway: discord4j.gateway.GatewayDiscordClient,
    guildId: String,
    voiceChannelId: String,
    roomActor: RoomActor.Command
  )(implicit ec: ExecutionContext): Future[_] = {

    import scala.concurrent.duration._

    // Check for activity changes every 30 seconds
    Future {
      Thread.sleep(30000)
      syncInitialVoiceState(gateway, voiceChannelId, guildId, roomActor)
    }
  }

  /**
   * Listen for activity change events
   */
  def listenToActivityChanges(
    client: DiscordClient,
    voiceChannelId: String,
    roomActor: RoomActor.Command
  )(implicit ec: ExecutionContext): Unit = {

    client.getEventDispatcher.on(classOf[MessageCreateEvent])
      .subscribe { event =>
        // Could trigger activity updates on messages if needed
        val userId = event.getMessage.getAuthor.map(_.getId.asString())
        logger.debug(s"Message from user: $userId")
      }
  }
}

/**
 * Configuration for Discord bot
 */
case class DiscordBotConfig(
  token: String,
  voiceChannelId: String,
  guildId: String,
  commandPrefix: String = "!"
)

/**
 * Actor-based wrapper for Discord bot
 */
class DiscordBotActor(
  config: DiscordBotConfig,
  roomActor: akka.actor.typed.ActorRef[RoomActor.Command]
) extends LazyLogging {

  private var client: Option[DiscordClient] = None

  def start()(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Starting Discord bot actor...")

    DiscordBot.start(
      token = config.token,
      voiceChannelId = config.voiceChannelId,
      guildId = config.guildId,
      roomActor = roomActor
    ).map { discordClient =>
      client = Some(discordClient)
      logger.info("Discord bot actor started successfully")
    }
  }

  def stop(): Unit = {
    logger.info("Stopping Discord bot actor...")
    client.foreach(_.logout().subscribe())
    client = None
  }
}
