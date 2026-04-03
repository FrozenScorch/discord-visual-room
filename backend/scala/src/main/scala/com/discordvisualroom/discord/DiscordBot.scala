package com.discordvisualroom.discord

import akka.actor.typed.ActorRef
import com.discordvisualroom.actors.RoomActor
import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging
import discord4j.common.util.Snowflake
import discord4j.core.{DiscordClient, DiscordClientBuilder}
import discord4j.core.`object`.entity.{Member, User}
import discord4j.core.`object`.presence.{Activity, Presence}
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.{Failure, Success, Try}

/**
 * Discord bot integration using discord4j
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
    roomActor: ActorRef[RoomActor.Command]
  )(implicit ec: ExecutionContext): Future[Unit] = {

    logger.info("Starting Discord bot...")

    val promise = Promise[Unit]()

    try {
      val client = DiscordClientBuilder.create(token).build()

      val gateway = client.login().block()
      if (gateway == null) {
        promise.failure(new RuntimeException("Failed to connect to Discord gateway"))
        return promise.future
      }

      logger.info("Discord bot connected successfully")

      val eventDispatcher = gateway.getEventDispatcher

      // Handle ready event
      eventDispatcher.on(classOf[ReadyEvent]).subscribe { event =>
        val self = event.getSelf
        logger.info(s"Bot logged in as: ${self.getUsername}#${self.getDiscriminator}")
        promise.trySuccess(())
      }

      // Handle voice state updates (users joining/leaving voice channel)
      eventDispatcher.on(classOf[VoiceStateUpdateEvent]).subscribe { event =>
        handleVoiceStateUpdate(event, voiceChannelId, guildId, roomActor)
      }

      // Initial sync of users in voice channel
      syncInitialVoiceState(gateway, voiceChannelId, guildId, roomActor)

      // Ensure promise completes even if ReadyEvent was already fired
      Future {
        Thread.sleep(5000)
        promise.trySuccess(())
      }

      promise.future
    } catch {
      case ex: Exception =>
        logger.error("Failed to start Discord bot", ex)
        promise.tryFailure(ex)
        promise.future
    }
  }

  /**
   * Handle voice state update events
   */
  private def handleVoiceStateUpdate(
    event: VoiceStateUpdateEvent,
    targetVoiceChannelId: String,
    guildId: String,
    roomActor: ActorRef[RoomActor.Command]
  ): Unit = {

    Try {
      val newState = event.getCurrent
      val oldState = event.getOld

      val newChannelId = newState.getChannelId.toScala.map(_.asString())
      val oldChannelId = oldState.toScala.flatMap(_.getChannelId.toScala.map(_.asString()))

      val userId = newState.getUserId.asString()

      // User joined our target channel
      if (newChannelId.contains(targetVoiceChannelId) && !oldChannelId.contains(targetVoiceChannelId)) {
        logger.info(s"User $userId joined voice channel $targetVoiceChannelId")
        Try {
          val member = newState.getMember.block()
          if (member != null) {
            handleUserJoined(member, roomActor)
          }
        }.recover {
          case ex: Exception =>
            logger.error(s"Failed to get member for user $userId", ex)
        }
      }
      // User left our target channel
      else if (oldChannelId.contains(targetVoiceChannelId) && !newChannelId.contains(targetVoiceChannelId)) {
        logger.info(s"User $userId left voice channel $targetVoiceChannelId")
        roomActor ! RoomActor.UserLeft(userId)
      }
    }.recover {
      case ex: Exception =>
        logger.error("Error handling voice state update", ex)
    }
  }

  /**
   * Handle user joining voice channel
   */
  private def handleUserJoined(member: Member, roomActor: ActorRef[RoomActor.Command]): Unit = {
    Try {
      val userId = member.getId.asString()
      val username = member.getUsername
      val displayName = member.getDisplayName
      val avatarUrl = member.getAvatarUrl

      val userNode = UserNode(
        id = userId,
        username = username,
        displayName = displayName,
        avatar = avatarUrl,
        position = Vector3D(0, 0, 0),
        rotation = Vector3D(0, 0, 0),
        activity = extractActivity(member),
        isSpeaking = false
      )

      roomActor ! RoomActor.UserJoined(userNode)
    }.recover {
      case ex: Exception =>
        logger.error(s"Failed to create UserNode for member", ex)
    }
  }

  /**
   * Extract user activity from Discord presence
   */
  private def extractActivity(member: Member): Option[UserActivity] = {
    Try {
      val presence = member.getPresence.block()
      if (presence == null) return None

      val activities = presence.getActivities.asScala
      activities.headOption.map { discordActivity =>
        UserActivity(
          name = discordActivity.getName,
          activityType = mapActivityType(discordActivity.getType),
          details = Option(discordActivity.getDetails).flatMap(d => Option(d.toString)),
          state = Option(discordActivity.getState).flatMap(s => Option(s.toString))
        )
      }
    }.getOrElse(None)
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
      case _ => ActivityType.Playing
    }
  }

  /**
   * Sync initial voice state (users already in the channel when bot starts)
   */
  private def syncInitialVoiceState(
    gateway: discord4j.core.GatewayDiscordClient,
    voiceChannelId: String,
    guildId: String,
    roomActor: ActorRef[RoomActor.Command]
  ): Unit = {

    logger.info("Syncing initial voice state...")

    Try {
      val guild = gateway.getGuildById(Snowflake.of(guildId)).block()
      if (guild == null) {
        logger.error(s"Could not find guild: $guildId")
        return
      }

      val voiceStates = guild.getVoiceStates.collectList().block()
      if (voiceStates == null) {
        logger.warn("No voice states found")
        return
      }

      val channelUsers = voiceStates.asScala.filter { vs =>
        vs.getChannelId.toScala.map(_.asString()).contains(voiceChannelId)
      }

      logger.info(s"Found ${channelUsers.size} users in voice channel $voiceChannelId")

      channelUsers.foreach { voiceState =>
        Try {
          val member = voiceState.getMember.block()
          if (member != null) {
            handleUserJoined(member, roomActor)
          }
        }.recover {
          case ex: Exception =>
            logger.error(s"Failed to sync member", ex)
        }
      }
    }.recover {
      case ex: Exception =>
        logger.error("Failed to sync initial voice state", ex)
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
