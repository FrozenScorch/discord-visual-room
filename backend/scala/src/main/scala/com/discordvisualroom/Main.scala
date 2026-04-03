package com.discordvisualroom

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.discordvisualroom.actors.GuildManager
import com.discordvisualroom.discord.DiscordBot
import com.discordvisualroom.model._
import com.discordvisualroom.websocket.SceneGraphServer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.{Failure, Success}

/**
 * Main entry point for Discord Visual Room backend
 * Bootstraps actor system, GuildManager, Discord bot, and WebSocket server
 */
object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {
    logger.info("=" * 60)
    logger.info("Discord Visual Room Backend Starting...")
    logger.info("=" * 60)

    // Load configuration
    val config = loadConfiguration()

    // Create actor system
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "discord-visual-room")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    // Create GuildManager with LLM config (manages all rooms for the guild)
    val guildManager = system.systemActorOf(
      GuildManager(config.llm),
      "guild-manager"
    )

    // Start WebSocket server
    startWebSocketServer(config, guildManager)

    // Start Discord bot
    startDiscordBot(config, guildManager)

    // Add shutdown hook
    addShutdownHook(system)

    logger.info("=" * 60)
    logger.info("Discord Visual Room Backend Started Successfully")
    logger.info("Press ENTER to stop...")
    logger.info("=" * 60)

    // Wait for user input
    StdIn.readLine()

    logger.info("Shutting down...")
    system.terminate()
  }

  /**
   * Load configuration from environment variables or defaults
   */
  private def loadConfiguration(): AppConfig = {
    logger.info("Loading configuration...")

    val discordToken = sys.env.getOrElse(
      "DISCORD_TOKEN",
      throw new IllegalArgumentException("DISCORD_TOKEN environment variable is required")
    )

    val guildId = sys.env.getOrElse(
      "GUILD_ID",
      throw new IllegalArgumentException("GUILD_ID environment variable is required")
    )

    val llmBaseUrl = sys.env.getOrElse("LLM_BASE_URL", "http://192.168.68.62:1234")
    val llmTimeout = sys.env.getOrElse("LLM_TIMEOUT_MS", "5000").toInt
    val llmMaxRetries = sys.env.getOrElse("LLM_MAX_RETRIES", "2").toInt

    val wsHost = sys.env.getOrElse("WS_HOST", "0.0.0.0")
    val wsPort = sys.env.getOrElse("WS_PORT", "8080").toInt
    val wsPath = sys.env.getOrElse("WS_PATH", "/ws")

    AppConfig(
      discord = DiscordConfig(
        token = discordToken,
        guildId = guildId
      ),
      llm = LLMConfig(
        baseUrl = llmBaseUrl,
        timeoutMs = llmTimeout,
        maxRetries = llmMaxRetries
      ),
      websocket = WebSocketConfig(
        host = wsHost,
        port = wsPort,
        path = wsPath
      )
    )
  }

  /**
   * Start WebSocket server
   */
  private def startWebSocketServer(
    config: AppConfig,
    guildManager: akka.actor.typed.ActorRef[GuildManager.Command]
  )(implicit system: ActorSystem[Nothing], ec: ExecutionContextExecutor): Unit = {

    logger.info(s"Starting WebSocket server on ${config.websocket.host}:${config.websocket.port}")

    val bindingFuture = SceneGraphServer.start(
      host = config.websocket.host,
      port = config.websocket.port,
      guildManager = guildManager
    )

    bindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        logger.info(s"WebSocket server bound to ${address.getHostString}:${address.getPort}")
        logger.info(s"WebSocket endpoint: ws://${address.getHostString}:${address.getPort}/ws")
      case Failure(ex) =>
        logger.error("Failed to bind WebSocket server", ex)
        system.terminate()
    }
  }

  /**
   * Start Discord bot
   */
  private def startDiscordBot(
    config: AppConfig,
    guildManager: akka.actor.typed.ActorRef[GuildManager.Command]
  )(implicit ec: ExecutionContextExecutor): Unit = {

    logger.info("Starting Discord bot...")

    val botFuture = DiscordBot.start(
      token = config.discord.token,
      guildId = config.discord.guildId,
      guildManager = guildManager
    )

    botFuture.onComplete {
      case Success(_) =>
        logger.info("Discord bot started successfully")
      case Failure(ex) =>
        logger.error("Failed to start Discord bot", ex)
        logger.warn("Continuing without Discord bot integration...")
    }
  }

  /**
   * Add shutdown hook for graceful termination
   */
  private def addShutdownHook(system: ActorSystem[Nothing]): Unit = {
    sys.addShutdownHook {
      logger.info("Shutdown hook triggered")
      system.terminate()
    }
  }
}
