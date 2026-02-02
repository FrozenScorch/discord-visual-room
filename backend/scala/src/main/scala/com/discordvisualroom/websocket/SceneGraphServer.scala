package com.discordvisualroom.websocket

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.{StatusCodes, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.discordvisualroom.actors.RoomActor
import com.discordvisualroom.model.SceneGraph
import com.discordvisualroom.serialization.JsonSerializers._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * WebSocket server for streaming SceneGraph updates to frontend clients
 * Uses Akka Streams for efficient bidirectional communication
 */
object SceneGraphServer extends LazyLogging {

  /**
   * Start the WebSocket server
   */
  def start(
    host: String,
    port: Int,
    roomActor: ActorRef[RoomActor.Command]
  )(implicit system: ActorSystem[Nothing], ec: ExecutionContext): Future[Http.ServerBinding] = {

    logger.info(s"Starting WebSocket server on $host:$port")

    val route = createRoute(roomActor)

    val bindingFuture = Http()
      .newServerAt(host, port)
      .bind(route)

    bindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        logger.info(s"WebSocket server online at ws://${address.getHostString}:${address.getPort}")

      case Failure(ex) =>
        logger.error("Failed to bind WebSocket server", ex)
        system.terminate()
    }

    bindingFuture
  }

  /**
   * Create HTTP route with WebSocket endpoint
   */
  private def createRoute(roomActor: ActorRef[RoomActor.Command]): Route = {
    path("ws") {
      get {
        handleWebSocketMessages(handleWebSocket(roomActor))
      }
    } ~
    path("health") {
      get {
        complete(StatusCodes.OK, "OK")
      }
    } ~
    path("scene") {
      get {
        // REST endpoint for current scene (for polling fallback)
        onSuccess(getCurrentScene(roomActor)) { sceneGraph =>
          complete(StatusCodes.OK, writeSceneGraph(sceneGraph))
        }
      }
    }
  }

  /**
   * Handle WebSocket connection flow
   */
  private def handleWebSocket(
    roomActor: ActorRef[RoomActor.Command]
  )(implicit system: ActorSystem[Nothing], ec: ExecutionContext): Flow[Message, Message, Any] = {

    // Create a unique client actor for this connection
    val clientActor = system.systemActorOf(
      WebSocketClientActor.behavior(roomActor),
      s"ws-client-${java.util.UUID.randomUUID()}"
    )

    // Incoming messages from client -> actor
    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] {
        case TextMessage.Strict(text) =>
          logger.debug(s"Received WebSocket message: $text")
          // Handle client messages if needed (e.g., ping/pong, commands)
          clientActor ! WebSocketClientActor.HandleClientMessage(text)

        case TextMessage.Streamed(textStream) =>
          textStream.runFold("")(_ + _).foreach { text =>
            logger.debug(s"Received streamed WebSocket message: $text")
            clientActor ! WebSocketClientActor.HandleClientMessage(text)
          }

        case _ =>
          logger.debug("Received binary WebSocket message (ignoring)")
      }

    // Outgoing messages from actor -> client
    val outgoing: Source[Message, NotUsed] =
      ActorSource.actorRef[SceneGraph](
        completionMatcher = {
          case WebSocketClientActor.StreamComplete => StatusCodes.Success
        },
        failureMatcher = {
          case WebSocketClientActor.StreamFailed(ex) => StatusCodes.ServerError
        },
        bufferSize = 100,
        overflowStrategy = akka.stream.OverflowStrategy.dropHead
      ).map(sceneGraph => TextMessage.Strict(writeSceneGraph(sceneGraph)))

    Flow.fromSinkAndSource(incoming, outgoing)
  }

  /**
   * Get current scene graph via ask pattern
   */
  private def getCurrentScene(
    roomActor: ActorRef[RoomActor.Command]
  )(implicit system: ActorSystem[Nothing], timeout: Timeout = 3.seconds): Future[SceneGraph] = {

    import RoomActor._

    roomActor.ask(replyTo => GetCurrentSceneGraph).map {
      case sceneUpdate: SceneGraphUpdate => sceneUpdate.sceneGraph
      case _ =>
        // Return empty scene if something goes wrong
        SceneGraph.create(
          users = Seq.empty,
          furniture = Seq.empty,
          room = com.discordvisualroom.model.RoomConfig(
            id = "unknown",
            name = "Unknown",
            dimensions = com.discordvisualroom.model.RoomDimensions(10, 3, 10),
            maxUsers = 10
          )
        )
    }
  }
}

/**
 * WebSocket client actor - handles per-connection state
 */
object WebSocketClientActor extends LazyLogging {
  sealed trait Command
  case class HandleClientMessage(message: String) extends Command
  case class NewSceneGraph(sceneGraph: SceneGraph) extends Command
  case object StreamComplete extends Command
  case class StreamFailed(ex: Throwable) extends Command

  def behavior(
    roomActor: ActorRef[RoomActor.Command]
  ): akka.actor.typed.Behavior[Command] =
    akka.actor.typed.scaladsl.Behaviors.setup { context =>
      // Subscribe to room updates
      roomActor ! RoomActor.SubscribeToSceneUpdates(context.self)

      akka.actor.typed.scaladsl.Behaviors.receiveMessage {
        case HandleClientMessage(message) =>
          // Handle incoming messages from client
          logger.debug(s"Client message: $message")
          akka.actor.typed.scaladsl.Behaviors.same

        case NewSceneGraph(sceneGraph) =>
          // Forward scene graph to stream
          context.self ! sceneGraph
          akka.actor.typed.scaladsl.Behaviors.same

        case SceneGraphUpdate =>
          // This would be handled by the actor source
          akka.actor.typed.scaladsl.Behaviors.same

        case StreamComplete =>
          // Unsubscribe from room updates
          roomActor ! RoomActor.UnsubscribeFromSceneUpdates(context.self)
          logger.info("WebSocket stream completed")
          akka.actor.typed.scaladsl.Behaviors.stopped

        case StreamFailed(ex) =>
          roomActor ! RoomActor.UnsubscribeFromSceneUpdates(context.self)
          logger.error("WebSocket stream failed", ex)
          akka.actor.typed.scaladsl.Behaviors.stopped
      }
    }
}
