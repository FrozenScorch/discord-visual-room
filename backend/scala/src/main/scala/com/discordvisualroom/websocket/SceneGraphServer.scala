package com.discordvisualroom.websocket

import akka.Done
import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source, BroadcastHub, Keep, MergeHub}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.util.Timeout
import com.discordvisualroom.actors.RoomActor
import com.discordvisualroom.model._
import com.discordvisualroom.serialization.JsonSerializers
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * WebSocket server for streaming SceneGraph updates to frontend clients
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

    Http()
      .newServerAt(host, port)
      .bind(route)
  }

  /**
   * Create HTTP route with WebSocket endpoint and CORS
   */
  private def createRoute(roomActor: ActorRef[RoomActor.Command])(implicit system: ActorSystem[Nothing]): Route = {
    // CORS headers for frontend
    respondWithHeaders(
      akka.http.scaladsl.model.headers.`Access-Control-Allow-Origin`.*,
      akka.http.scaladsl.model.headers.`Access-Control-Allow-Methods`(
        akka.http.scaladsl.model.HttpMethods.GET,
        akka.http.scaladsl.model.HttpMethods.OPTIONS
      )
    ) {
      path("ws") {
        get {
          handleWebSocketMessages(createWebSocketFlow(roomActor))
        }
      } ~
      path("health") {
        get {
          complete(StatusCodes.OK, "OK")
        }
      } ~
      path("scene") {
        get {
          implicit val timeout: Timeout = 3.seconds
          val sceneFuture = roomActor.ask(ref => RoomActor.GetCurrentSceneGraph(ref))
          onSuccess(sceneFuture) { sceneUpdate =>
            val json = wrapSceneUpdateMessage(sceneUpdate.sceneGraph)
            complete(StatusCodes.OK, json)
          }
        }
      }
    }
  }

  /**
   * Create a WebSocket flow for a single client connection.
   *
   * Uses an ActorRef-based Source to push scene updates to the client.
   * The actor subscribes to the RoomActor for scene updates.
   */
  private def createWebSocketFlow(
    roomActor: ActorRef[RoomActor.Command]
  )(implicit system: ActorSystem[Nothing]): Flow[Message, Message, Any] = {

    // Create an actor-backed source that will push messages to the WebSocket
    val (outActorUntyped, outSource) = Source
      .actorRef[RoomActor.SceneGraphUpdate](
        completionMatcher = PartialFunction.empty,
        failureMatcher = PartialFunction.empty,
        bufferSize = 64,
        overflowStrategy = OverflowStrategy.dropHead
      )
      .preMaterialize()

    // Convert untyped ActorRef to typed ActorRef[SceneGraphUpdate]
    val outActor: ActorRef[RoomActor.SceneGraphUpdate] = outActorUntyped.toTyped

    // Subscribe this client's output actor to room updates
    roomActor ! RoomActor.SubscribeToSceneUpdates(outActor)

    // Convert SceneGraphUpdate to WebSocket TextMessage with proper envelope
    val outMessages: Source[Message, NotUsed] = outSource.map { sceneUpdate =>
      val json = wrapSceneUpdateMessage(sceneUpdate.sceneGraph)
      TextMessage.Strict(json): Message
    }

    // Incoming messages from client (we mostly ignore these in dumb-renderer arch)
    val inSink: Sink[Message, Future[Done]] = Sink.foreach[Message] {
      case TextMessage.Strict(text) =>
        logger.debug(s"Received client message: $text")
      case TextMessage.Streamed(textStream) =>
        textStream.runFold("")(_ + _).foreach { text =>
          logger.debug(s"Received streamed client message: $text")
        }(system.executionContext)
      case _ =>
        logger.debug("Received binary message (ignoring)")
    }.mapMaterializedValue { future =>
      // When client disconnects, unsubscribe
      future.onComplete { _ =>
        logger.info("WebSocket client disconnected, unsubscribing")
        roomActor ! RoomActor.UnsubscribeFromSceneUpdates(outActor)
      }(system.executionContext)
      future
    }

    Flow.fromSinkAndSource(inSink, outMessages)
  }

  /**
   * Wrap a SceneGraph in the WebSocket message envelope the frontend expects:
   * {"type": "SCENE_UPDATE", "timestamp": ..., "payload": {...}}
   */
  private def wrapSceneUpdateMessage(sceneGraph: SceneGraph): String = {
    val sceneJson = JsonSerializers.writeSceneGraph(sceneGraph)
    s"""{"type":"SCENE_UPDATE","timestamp":${System.currentTimeMillis()},"payload":$sceneJson}"""
  }
}
