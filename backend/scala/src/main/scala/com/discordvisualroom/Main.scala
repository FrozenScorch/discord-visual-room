package com.discordvisualroom

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.discordvisualroom.actors.RoomActor
import com.discordvisualroom.discord.{DiscordBot, DiscordBotConfig}
import com.discordvisualroom.model._
import com.discordvisualroom.websocket.SceneGraphServer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

/**
 * Main entry point for Discord Visual Room backend
 * Bootstraps actor system, Discord bot, and WebSocket server
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

    // Create RoomActor
    val roomActor = system.systemActorOf(
      RoomActor(),
      "room-actor"
    )

    // Initialize room
    initializeRoom(config, roomActor)

    // Start WebSocket server
    startWebSocketServer(config, roomActor)

    // Start Discord bot
    startDiscordBot(config, roomActor)

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

    val voiceChannelId = sys.env.getOrElse(
      "VOICE_CHANNEL_ID",
      throw new IllegalArgumentException("VOICE_CHANNEL_ID environment variable is required")
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

    val roomName = sys.env.getOrElse("ROOM_NAME", "Visual Room")
    val roomWidth = sys.env.getOrElse("ROOM_WIDTH", "20").toDouble
    val roomHeight = sys.env.getOrElse("ROOM_HEIGHT", "4").toDouble
    val roomDepth = sys.env.getOrElse("ROOM_DEPTH", "20").toDouble
    val roomMaxUsers = sys.env.getOrElse("ROOM_MAX_USERS", "20").toInt

    AppConfig(
      discord = DiscordConfig(
        token = discordToken,
        voiceChannelId = voiceChannelId,
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
      ),
      room = RoomConfig(
        id = voiceChannelId,
        name = roomName,
        dimensions = RoomDimensions(roomWidth, roomHeight, roomDepth),
        maxUsers = roomMaxUsers
      )
    )
  }

  /**
   * Initialize room with configuration
   */
  private def initializeRoom(config: AppConfig, roomActor: akka.actor.typed.ActorRef[RoomActor.Command]): Unit = {
    implicit val timeout: Timeout = 5.seconds
    import akka.actor.typed.scaladsl.AskPattern._

    logger.info(s"Initializing room: ${config.room.name}")

    val initFuture = roomActor.ask(replyTo => RoomActor.Initialize(config.room, replyTo))

    initFuture.onComplete {
      case Success(RoomActor.InitializationResponse(true, message)) =>
        logger.info(s"Room initialized: $message")

      case Success(RoomActor.InitializationResponse(false, message)) =>
        logger.error(s"Failed to initialize room: $message")

      case Failure(ex) =>
        logger.error("Failed to initialize room", ex)
    }
  }

  /**
   * Start WebSocket server
   */
  private def startWebSocketServer(
    config: AppConfig,
    roomActor: akka.actor.typed.ActorRef[RoomActor.Command]
  )(implicit system: ActorSystem[Nothing], ec: ExecutionContextExecutor): Unit = {

    logger.info(s"Starting WebSocket server on ${config.websocket.host}:${config.websocket.port}")

    val bindingFuture = SceneGraphServer.start(
      host = config.websocket.host,
      port = config.websocket.port,
      roomActor = roomActor
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
    roomActor: akka.actor.typed.ActorRef[RoomActor.Command]
  )(implicit ec: ExecutionContextExecutor): Unit = {

    logger.info("Starting Discord bot...")

    val botFuture = DiscordBot.start(
      token = config.discord.token,
      voiceChannelId = config.discord.voiceChannelId,
      guildId = config.discord.guildId,
      roomActor = roomActor
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
      logger.info("Actor system terminated")
    }
  }
}

/**
 * Development runner with optional arguments
 */
object DevRunner extends LazyLogging {

  def main(args: Array[String]): Unit = {
    // Set development defaults if not in environment
    if (sys.env.get("DISCORD_TOKEN").isEmpty) {
      logger.warn("DISCORD_TOKEN not set, using dummy token for testing")
      sys.props.put("DISCORD_TOKEN", "dummy-token-for-testing")
    }

    if (sys.env.get("VOICE_CHANNEL_ID").isEmpty) {
      logger.warn("VOICE_CHANNEL_ID not set, using dummy ID for testing")
      sys.props.put("VOICE_CHANNEL_ID", "123456789")
    }

    if (sys.env.get("GUILD_ID").isEmpty) {
      logger.warn("GUILD_ID not set, using dummy ID for testing")
      sys.props.put("GUILD_ID", "987654321")
    }

    // Run main application
    Main.main(args)
  }
}

/**
 * Test runner for local development without Discord
 */
object TestRunner extends LazyLogging {

  def main(args: Array[String]): Unit = {
    logger.info("Running in test mode (no Discord integration)")

    // Set test configuration
    sys.props.put("DISCORD_TOKEN", "test-token")
    sys.props.put("VOICE_CHANNEL_ID", "test-channel")
    sys.props.put("GUILD_ID", "test-guild")
    sys.props.put("WS_HOST", "localhost")
    sys.props.put("WS_PORT", "8080")

    Main.main(args)
  }
}
