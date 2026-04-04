package com.discordvisualroom.discord

import akka.actor.typed.ActorRef
import com.discordvisualroom.actors.GuildManager
import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging
import discord4j.common.util.Snowflake
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.presence.Activity
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.PresenceUpdateEvent

import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Try

/**
 * Discord bot integration using discord4j
 * Listens to voice channel events and tracks user activities
 * Auto-discovers all channels from the guild and routes events to GuildManager
 *
 * Supports runtime guild selection: bot starts without a guild, frontend selects one via /api/guilds
 */
object DiscordBot extends LazyLogging {

  /** Shared gateway client reference for REST queries (guild listing) */
  private val gatewayRef: AtomicReference[discord4j.core.GatewayDiscordClient] =
    new AtomicReference[discord4j.core.GatewayDiscordClient]()

  /** Currently active guild ID (set at startup or via SelectGuild) */
  private val activeGuildId: AtomicReference[String] = new AtomicReference[String]()

  /** Get the gateway client (for REST queries) */
  def getGatewayClient: Option[discord4j.core.GatewayDiscordClient] =
    Option(gatewayRef.get())

  /** Get the currently active guild ID */
  def getActiveGuildId: Option[String] = Option(activeGuildId.get())

  /**
   * List all guilds the bot is a member of
   * Returns Seq[(id, name, iconUrl, memberCount)]
   */
  def listAvailableGuilds(): immutable.Seq[(String, String, Option[String], Int)] = {
    val gateway = gatewayRef.get()
    if (gateway == null) return Seq.empty

    Try {
      val guilds = gateway.getGuilds.collectList().block()
      if (guilds == null) return Seq.empty

      guilds.asScala.toSeq.map { g =>
        val icon = Option(g.getIconUrl(discord4j.rest.util.Image.Format.PNG)).flatMap(_.toScala)
        val memberCount = Option(g.getMemberCount).map(_.intValue()).getOrElse(0)
        (g.getId.asString(), g.getName, icon, memberCount)
      }.sortBy(_._2) // sort by name
    }.recover { case e =>
      logger.error(s"Failed to list guilds: ${e.getMessage}", e)
      Seq.empty
    }.get
  }

  /**
   * Create and start the Discord bot
   * If guildId is provided, immediately discovers that guild
   * If not, waits for frontend to select via SelectGuild
   */
  def start(
    token: String,
    guildId: Option[String],
    guildManager: ActorRef[GuildManager.Command]
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

      gatewayRef.set(gateway)
      logger.info("Discord bot connected successfully")

      val eventDispatcher = gateway.getEventDispatcher

      // Handle ready event
      eventDispatcher.on(classOf[ReadyEvent]).subscribe { event =>
        val self = event.getSelf
        logger.info(s"Bot logged in as: ${self.getUsername}#${self.getDiscriminator}")
        promise.trySuccess(())

        // Auto-discover guild if GUILD_ID was provided
        guildId.foreach { gid =>
          activeGuildId.set(gid)
          discoverGuild(gateway, gid, guildManager)
        }
      }

      // Handle voice state updates (filter by active guild)
      eventDispatcher.on(classOf[VoiceStateUpdateEvent]).subscribe { event =>
        val currentGuild = activeGuildId.get()
        if (currentGuild != null) handleVoiceStateUpdate(event, currentGuild, guildManager)
      }

      // Handle presence updates (filter by active guild)
      eventDispatcher.on(classOf[PresenceUpdateEvent]).subscribe { event =>
        val currentGuild = activeGuildId.get()
        if (currentGuild != null) handlePresenceUpdate(event, currentGuild, guildManager)
      }

      // Initial sync if GUILD_ID was provided
      guildId.foreach { gid =>
        syncInitialVoiceState(gateway, gid, guildManager)
      }

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
   * Select a guild to visualize (called from REST endpoint)
   */
  def selectGuild(guildId: String, guildManager: ActorRef[GuildManager.Command])(implicit ec: ExecutionContext): Future[Unit] = {
    val gateway = gatewayRef.get()
    if (gateway == null) {
      Future.failed(new RuntimeException("Discord bot not connected"))
    } else {
      activeGuildId.set(guildId)
      logger.info(s"Switching to guild: $guildId")
      discoverGuild(gateway, guildId, guildManager)
      syncInitialVoiceState(gateway, guildId, guildManager)
      Future.successful(())
    }
  }

  /**
   * Discover the guild: fetch all channels, roles, members, and send GuildDiscovered to GuildManager
   */
  private def discoverGuild(
    gateway: discord4j.core.GatewayDiscordClient,
    guildId: String,
    guildManager: ActorRef[GuildManager.Command]
  ): Unit = {
    Try {
      val guild = gateway.getGuildById(Snowflake.of(guildId)).block()
      if (guild == null) {
        logger.error(s"Could not find guild: $guildId")
        return
      }

      val name = guild.getName
      val iconUrl = guild.getIconUrl(discord4j.rest.util.Image.Format.PNG).toScala
      val guildSnowflake = guild.getId.asString()

      logger.info(s"Discovered guild: $name (id=$guildSnowflake)")

      // Fetch all channels
      val allChannels = guild.getChannels.collectList().block()
      val channels = if (allChannels != null) allChannels.asScala.toSeq else Seq.empty

      val voiceChannels: immutable.Seq[(String, String, Int)] = channels.flatMap { ch =>
        ch.getType match {
          case discord4j.core.`object`.entity.channel.Channel.Type.GUILD_VOICE =>
            val pos = Option(ch.getPosition.block()).map(_.intValue()).getOrElse(0)
            Some((ch.getId.asString(), ch.getName, pos))
          case _ => None
        }
      }.sortBy(_._3)

      val textChannels: immutable.Seq[(String, String, Int)] = channels.flatMap { ch =>
        ch.getType match {
          case discord4j.core.`object`.entity.channel.Channel.Type.GUILD_TEXT =>
            val pos = Option(ch.getPosition.block()).map(_.intValue()).getOrElse(0)
            Some((ch.getId.asString(), ch.getName, pos))
          case _ => None
        }
      }.sortBy(_._3)

      logger.info(s"Found ${voiceChannels.size} voice channels, ${textChannels.size} text channels")

      // Build GuildInfo
      val onlineCount = Try {
        val members = guild.getMembers.collectList().block()
        if (members != null) {
          members.asScala.count { m =>
            val presence = m.getPresence.block()
            presence != null && !presence.getStatus.equals(discord4j.core.`object`.presence.Status.OFFLINE)
          }
        } else 0
      }.getOrElse(0)

      val roles = Try(guild.getRoles.collectList().block()).toOption.map(_.asScala.toSeq).getOrElse(Seq.empty)
      val guildRoles: immutable.Seq[GuildRole] = roles.map { r =>
        val colorInt = r.getColor.getRGB
        val position = r.getPosition.block()
        GuildRole(r.getId.asString(), r.getName, colorInt, if (position != null) position else 0)
      }

      val guildInfo = GuildInfo(
        id = guildSnowflake,
        name = name,
        icon = iconUrl,
        roles = guildRoles,
        onlineMemberCount = onlineCount
      )

      guildManager ! GuildManager.GuildDiscovered(guildInfo, voiceChannels, textChannels)
      logger.info(s"GuildDiscovered sent to GuildManager for '$name'")

    }.recover {
      case ex: Exception =>
        logger.error(s"Failed to discover guild $guildId", ex)
    }
  }

  /**
   * Handle voice state update events - route to GuildManager for any voice channel
   */
  private def handleVoiceStateUpdate(
    event: VoiceStateUpdateEvent,
    guildId: String,
    guildManager: ActorRef[GuildManager.Command]
  ): Unit = {

    Try {
      val newState = event.getCurrent
      val oldState = event.getOld

      // Only handle events for our guild
      val eventGuildId = newState.getGuildId.asString()
      if (eventGuildId != guildId) return

      val newChannelId = newState.getChannelId.toScala.map(_.asString())
      val oldChannelId = oldState.toScala.flatMap(_.getChannelId.toScala.map(_.asString()))

      val userId = newState.getUserId.asString()

      // User joined a voice channel (newChannelId is set, oldChannelId is None or different)
      if (newChannelId.isDefined && newChannelId != oldChannelId) {
        val chId = newChannelId.get
        logger.info(s"User $userId joined voice channel $chId")
        Try {
          val member = newState.getMember.block()
          if (member != null) {
            handleUserJoinedVoiceChannel(member, chId, guildManager)
          }
        }.recover {
          case ex: Exception =>
            logger.error(s"Failed to get member for user $userId", ex)
        }
      }

      // User left a voice channel (oldChannelId is set, newChannelId is None or different)
      if (oldChannelId.isDefined && oldChannelId != newChannelId) {
        val chId = oldChannelId.get
        logger.info(s"User $userId left voice channel $chId")
        guildManager ! GuildManager.UserLeftVoiceChannel(userId, chId)
      }

      // Speaking state change - detect mute/deafen changes for users in voice
      val wasSpeaking = oldState.toScala.exists(s => !s.isSelfMuted && !s.isSelfDeaf)
      val isNowSpeaking = !newState.isSelfMuted && !newState.isSelfDeaf

      if (wasSpeaking != isNowSpeaking && newChannelId.isDefined) {
        guildManager ! GuildManager.UserSpeakingChanged(userId, newChannelId.get, isNowSpeaking)
      }

    }.recover {
      case ex: Exception =>
        logger.error("Error handling voice state update", ex)
    }
  }

  /**
   * Handle presence update events (activity changes)
   */
  private def handlePresenceUpdate(
    event: PresenceUpdateEvent,
    guildId: String,
    guildManager: ActorRef[GuildManager.Command]
  ): Unit = {
    Try {
      // Only handle events for our guild
      val eventGuildId: String = event.getGuildId.asString()
      if (eventGuildId != guildId) return

      val userId = event.getUserId.asString()

      val presence = event.getCurrent
      if (presence == null) return

      val activities = presence.getActivities.asScala.toSeq
      val activity = activities.headOption.map { discordActivity =>
        UserActivity(
          name = discordActivity.getName,
          activityType = mapActivityType(discordActivity.getType),
          details = Option(discordActivity.getDetails).flatMap(d => Option(d.toString)),
          state = Option(discordActivity.getState).flatMap(s => Option(s.toString))
        )
      }

      // Find which voice channel the user is in (from current voice states)
      // We rely on the voice state to have the channel; if not in voice, ignore activity change
      // The GuildManager will look up the user's channel internally
      val currentGuild = event.getClient.getGuildById(Snowflake.of(guildId)).block()
      if (currentGuild == null) return

      val voiceStates = currentGuild.getVoiceStates.collectList().block()
      val userVoiceState = if (voiceStates != null) {
        voiceStates.asScala.find(_.getUserId.asString() == userId)
      } else None

      val channelIdOpt = userVoiceState.flatMap(vs => vs.getChannelId.toScala.map(_.asString()))

      channelIdOpt.foreach { channelId =>
        guildManager ! GuildManager.UserActivityChanged(userId, channelId, activity)
      }

    }.recover {
      case ex: Exception =>
        logger.error("Error handling presence update", ex)
    }
  }

  /**
   * Handle user joining a voice channel
   */
  private def handleUserJoinedVoiceChannel(member: Member, channelId: String, guildManager: ActorRef[GuildManager.Command]): Unit = {
    Try {
      val userId = member.getId.asString()
      val username = member.getUsername
      val displayName = member.getDisplayName
      val avatarUrl = member.getAvatarUrl
      val activity = extractActivity(member)

      guildManager ! GuildManager.UserJoinedVoiceChannel(
        userId = userId,
        channelId = channelId,
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl,
        activity = activity
      )
    }.recover {
      case ex: Exception =>
        logger.error(s"Failed to handle user joined voice channel", ex)
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
   * Sync initial voice state - iterate ALL existing voice states and send them as joins to GuildManager
   */
  private def syncInitialVoiceState(
    gateway: discord4j.core.GatewayDiscordClient,
    guildId: String,
    guildManager: ActorRef[GuildManager.Command]
  ): Unit = {

    logger.info("Syncing initial voice state for guild...")

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

      val usersInVoice = voiceStates.asScala.filter { vs =>
        vs.getChannelId.toScala.isDefined
      }

      logger.info(s"Found ${usersInVoice.size} users in voice channels across guild")

      usersInVoice.foreach { voiceState =>
        Try {
          val channelId = voiceState.getChannelId.toScala.map(_.asString()).get
          val member = voiceState.getMember.block()
          if (member != null) {
            val userId = member.getId.asString()
            val username = member.getUsername
            val displayName = member.getDisplayName
            val avatarUrl = member.getAvatarUrl
            val activity = extractActivity(member)

            guildManager ! GuildManager.UserJoinedVoiceChannel(
              userId = userId,
              channelId = channelId,
              username = username,
              displayName = displayName,
              avatarUrl = avatarUrl,
              activity = activity
            )
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
