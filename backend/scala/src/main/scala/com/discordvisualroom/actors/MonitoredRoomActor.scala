package com.discordvisualroom.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.discordvisualroom.logging.{StructuredLogging, MdcContext}
import com.discordvisualroom.metrics.ApplicationMetrics
import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging

/**
 * Example: RoomActor with integrated monitoring
 * Shows how to add logging and metrics to existing actors
 */
object MonitoredRoomActor extends LazyLogging {
  sealed trait Command

  // Existing commands...
  final case class Initialize(config: RoomConfig, replyTo: ActorRef[InitializationResponse]) extends Command
  final case class UserJoined(user: UserNode, replyTo: ActorRef[UserOperationResponse]) extends Command
  final case class UserLeft(userId: String, replyTo: ActorRef[UserOperationResponse]) extends Command
  final case class UserActivityChanged(userId: String, activity: Option[UserActivity]) extends Command
  final case class UserSpeakingChanged(userId: String, isSpeaking: Boolean) extends Command

  final case class InitializationResponse(success: Boolean, message: String)
  final case class UserOperationResponse(success: Boolean, userId: String, message: String)

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new MonitoredRoomActor(context))
}

class MonitoredRoomActor(
  context: ActorContext[MonitoredRoomActor.Command]
) extends AbstractBehavior[MonitoredRoomActor.Command](context) with StructuredLogging {

  import MonitoredRoomActor._

  // Room state
  private var roomConfig: Option[RoomConfig] = None
  private var users: Map[String, UserNode] = Map.empty

  override def onMessage(msg: Command): Behavior[Command] = {
    // Generate correlation ID for each message
    val correlationId = StructuredLogging.generateCorrelationId()

    MdcContext.withCorrelationId(correlationId) {
      MdcContext.withComponent("RoomActor") {
        logOperation(s"Processing ${msg.getClass.getSimpleName}", Map.empty) {
          msg match {
            case Initialize(config, replyTo) =>
              handleInitialize(config, replyTo)

            case UserJoined(user, replyTo) =>
              handleUserJoined(user, replyTo)

            case UserLeft(userId, replyTo) =>
              handleUserLeft(userId, replyTo)

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

    // Record metrics
    ApplicationMetrics.Room.setUserCount(0)
    ApplicationMetrics.Room.setFurnitureCount(0)

    replyTo ! InitializationResponse(
      success = true,
      message = s"Room ${config.name} initialized successfully"
    )
  }

  private def handleUserJoined(user: UserNode, replyTo: ActorRef[UserOperationResponse]): Unit = {
    logger.info("User joined", Map("userId" -> user.id, "username" -> user.username))

    // Track user with MDC
    MdcContext.setUserId(user.id)

    try {
      users = users + (user.id -> user)

      // Update metrics
      ApplicationMetrics.Room.incrementUserCount()

      // Log with context
      logWithContext("INFO", s"User ${user.username} added to room", Map(
        "userCount" -> users.size,
        "userId" -> user.id
      ))

      replyTo ! UserOperationResponse(
        success = true,
        user.id,
        s"User ${user.username} joined"
      )

      // Trigger scene update
      ApplicationMetrics.Room.recordSceneUpdate(0)

    } finally {
      MdcContext.remove("userId")
    }
  }

  private def handleUserLeft(userId: String, replyTo: ActorRef[UserOperationResponse]): Unit = {
    logger.info("User left", Map("userId" -> userId))

    users.get(userId) match {
      case Some(user) =>
        users = users - userId

        // Update metrics
        ApplicationMetrics.Room.decrementUserCount()

        logWithContext("INFO", s"User ${user.username} removed from room", Map(
          "userCount" -> users.size,
          "userId" -> userId
        ))

        replyTo ! UserOperationResponse(
          success = true,
          userId,
          s"User $userId left"
        )

        ApplicationMetrics.Room.recordSceneUpdate(0)

      case None =>
        logger.warn(s"User not found: $userId")
        ApplicationMetrics.Errors.recordError("RoomActor", "UserNotFound")

        replyTo ! UserOperationResponse(
          success = false,
          userId,
          s"User $userId not found"
        )
    }
  }

  private def handleUserActivityChanged(userId: String, activity: Option[UserActivity]): Unit = {
    logger.debug("User activity changed", Map(
      "userId" -> userId,
      "activity" -> activity.map(_.name).getOrElse("None")
    ))

    users.get(userId) match {
      case Some(user) =>
        users = users.updated(userId, user.copy(activity = activity))

        // Record Discord activity update metric
        ApplicationMetrics.Discord.recordActivityUpdate()

        logWithContext("DEBUG", "Activity updated", Map(
          "userId" -> userId,
          "activityName" -> activity.map(_.name).getOrElse("None")
        ))

      case None =>
        logger.warn(s"User not found for activity update: $userId")
    }
  }

  private def handleUserSpeakingChanged(userId: String, isSpeaking: Boolean): Unit = {
    logger.debug("User speaking state changed", Map(
      "userId" -> userId,
      "isSpeaking" -> isSpeaking
    ))

    users.get(userId) match {
      case Some(user) =>
        users = users.updated(userId, user.copy(isSpeaking = isSpeaking))

        // Record Discord speaking update metric
        ApplicationMetrics.Discord.recordSpeakingUpdate()

      case None =>
        logger.warn(s"User not found for speaking update: $userId")
    }
  }
}
