package com.discordvisualroom.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.discordvisualroom.logging.{StructuredLogging, MdcContext}
import com.discordvisualroom.metrics.ApplicationMetrics
import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging

/**
 * Example: RoomActor with integrated monitoring
 * Shows how to add logging and metrics to existing actors.
 * This is a standalone example - the production RoomActor is separate.
 */
object MonitoredRoomActor extends LazyLogging {
  sealed trait Command

  final case class Initialize(config: RoomConfig, replyTo: ActorRef[InitializationResponse]) extends Command
  final case class UserJoined(user: UserNode) extends Command
  final case class UserLeft(userId: String) extends Command
  final case class UserActivityChanged(userId: String, activity: Option[UserActivity]) extends Command
  final case class UserSpeakingChanged(userId: String, isSpeaking: Boolean) extends Command

  final case class InitializationResponse(success: Boolean, message: String)

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new MonitoredRoomActor(context))
}

class MonitoredRoomActor(
  context: ActorContext[MonitoredRoomActor.Command]
) extends AbstractBehavior[MonitoredRoomActor.Command](context) with StructuredLogging {

  import MonitoredRoomActor._

  private var roomConfig: Option[RoomConfig] = None
  private var users: Map[String, UserNode] = Map.empty

  override def onMessage(msg: Command): Behavior[Command] = {
    val correlationId = StructuredLogging.generateCorrelationId()

    MdcContext.withCorrelationId(correlationId) {
      MdcContext.withComponent("RoomActor") {
        logOperation(s"Processing ${msg.getClass.getSimpleName}", Map.empty) {
          msg match {
            case Initialize(config, replyTo) =>
              handleInitialize(config, replyTo)
            case UserJoined(user) =>
              handleUserJoined(user)
            case UserLeft(userId) =>
              handleUserLeft(userId)
            case UserActivityChanged(userId, activity) =>
              handleUserActivityChanged(userId, activity)
            case UserSpeakingChanged(userId, isSpeaking) =>
              handleUserSpeakingChanged(userId, isSpeaking)
          }
        }
      }
    }

    this
  }

  private def handleInitialize(config: RoomConfig, replyTo: ActorRef[InitializationResponse]): Unit = {
    logger.info("Initializing room", Map("roomId" -> config.id, "name" -> config.name))
    roomConfig = Some(config)
    ApplicationMetrics.Room.setUserCount(0)
    ApplicationMetrics.Room.setFurnitureCount(0)
    replyTo ! InitializationResponse(success = true, message = s"Room ${config.name} initialized successfully")
  }

  private def handleUserJoined(user: UserNode): Unit = {
    logger.info("User joined", Map("userId" -> user.id, "username" -> user.username))
    MdcContext.setUserId(user.id)
    try {
      users = users + (user.id -> user)
      ApplicationMetrics.Room.incrementUserCount()
      logWithContext("INFO", s"User ${user.username} added to room", Map(
        "userCount" -> users.size,
        "userId" -> user.id
      ))
      ApplicationMetrics.Room.recordSceneUpdate(0)
    } finally {
      MdcContext.remove("userId")
    }
  }

  private def handleUserLeft(userId: String): Unit = {
    logger.info("User left", Map("userId" -> userId))
    users.get(userId) match {
      case Some(user) =>
        users = users - userId
        ApplicationMetrics.Room.decrementUserCount()
        logWithContext("INFO", s"User ${user.username} removed from room", Map(
          "userCount" -> users.size,
          "userId" -> userId
        ))
        ApplicationMetrics.Room.recordSceneUpdate(0)
      case None =>
        logger.warn(s"User not found: $userId")
        ApplicationMetrics.Errors.recordError("RoomActor", "UserNotFound")
    }
  }

  private def handleUserActivityChanged(userId: String, activity: Option[UserActivity]): Unit = {
    users.get(userId) match {
      case Some(user) =>
        users = users.updated(userId, user.copy(activity = activity))
        ApplicationMetrics.Discord.recordActivityUpdate()
      case None =>
        logger.warn(s"User not found for activity update: $userId")
    }
  }

  private def handleUserSpeakingChanged(userId: String, isSpeaking: Boolean): Unit = {
    users.get(userId) match {
      case Some(user) =>
        users = users.updated(userId, user.copy(isSpeaking = isSpeaking))
        ApplicationMetrics.Discord.recordSpeakingUpdate()
      case None =>
        logger.warn(s"User not found for speaking update: $userId")
    }
  }
}
