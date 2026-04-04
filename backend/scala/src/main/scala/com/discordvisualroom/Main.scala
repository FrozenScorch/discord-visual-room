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
import java.nio.file.{Files, Paths}

/**
 * Main entry point for Discord Visual Room backend
 * Bootstraps actor system, GuildManager, Discord bot, and WebSocket server
 */
object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {
    // Load .env from project root before reading any configuration
    loadDotEnv()

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
    if (config.discord.guildId.isDefined) {
      logger.info(s"Visualizing guild: ${config.discord.guildId.get}")
    } else {
      logger.info("No GUILD_ID set — guild selection available at /api/guilds")
    }
    logger.info("Press ENTER to stop (or send SIGTERM)...")
    logger.info("=" * 60)

    // Wait indefinitely until shutdown signal
    // In Docker, SIGTERM triggers the shutdown hook.
    // In local dev, ENTER on stdin triggers shutdown.
    try {
      StdIn.readLine()
    } catch {
      case _: Exception => // EOF in Docker (no stdin) — block until SIGTERM
        Thread.sleep(Long.MaxValue)
    }

    logger.info("Shutting down...")
    system.terminate()
  }

  /**
   * Load configuration from environment variables or defaults
   */
  private def loadConfiguration(): AppConfig = {
    logger.info("Loading configuration...")

    def envOrProp(key: String): Option[String] =
      Option(System.getProperty(key)).orElse(sys.env.get(key))

    def envOrPropOrElse(key: String, default: String): String =
      envOrProp(key).getOrElse(default)

    val discordToken = envOrProp("DISCORD_BOT_TOKEN").getOrElse(
      throw new IllegalArgumentException("DISCORD_BOT_TOKEN environment variable is required")
    )

    val guildId = envOrProp("GUILD_ID")

    val llmBaseUrl = envOrPropOrElse("LLM_API_URL", "http://192.168.68.62:1234")
    val llmTimeout = envOrPropOrElse("LLM_TIMEOUT_MS", "5000").toInt
    val llmMaxRetries = envOrPropOrElse("LLM_MAX_RETRIES", "2").toInt

    val wsHost = envOrPropOrElse("WS_HOST", "0.0.0.0")
    val wsPort = envOrPropOrElse("WS_PORT", "9050").toInt
    val wsPath = envOrPropOrElse("WS_PATH", "/ws")

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

  /**
   * Load .env file from project root into system properties.
   * Walks up from the working directory to find .env.
   */
  private def loadDotEnv(): Unit = {
    val candidates = Stream.iterate(Paths.get("").toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .map(_.resolve(".env"))

    candidates.find(p => Files.exists(p)).foreach { envPath =>
      logger.info(s"Loading .env from ${envPath}")
      val lines = Files.readAllLines(envPath)
      lines.forEach { line =>
        val trimmed = line.trim
        if (trimmed.nonEmpty && !trimmed.startsWith("#")) {
          val eqIdx = trimmed.indexOf('=')
          if (eqIdx > 0) {
            val key = trimmed.substring(0, eqIdx).trim
            val value = trimmed.substring(eqIdx + 1).trim
              .stripPrefix("\"").stripSuffix("\"")
              .stripPrefix("'").stripSuffix("'")
            if (sys.env.get(key).isEmpty) {
              System.setProperty(key, value)
            }
          }
        }
      }
    }
  }
}
