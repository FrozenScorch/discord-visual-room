package com.discordvisualroom.actors

import akka.actor.typed.{ActorRef, Behavior, PostStop, PreRestart, SupervisorStrategy}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, Routers}
import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable

/**
 * RoomActor - Main room state manager
 * Coordinates UserManager and FurnitureManager
 * Broadcasts SceneGraph updates to WebSocket clients
 */
object RoomActor extends LazyLogging {
  sealed trait Command

  // Initialization
  final case class Initialize(config: RoomConfig, replyTo: ActorRef[InitializationResponse]) extends Command

  // User management
  final case class UserJoined(user: UserNode, replyTo: ActorRef[UserOperationResponse]) extends Command
  final case class UserLeft(userId: String, replyTo: ActorRef[UserOperationResponse]) extends Command
  final case class UserActivityChanged(userId: String, activity: Option[UserActivity]) extends Command
  final case class UserSpeakingChanged(userId: String, isSpeaking: Boolean) extends Command

  // Scene graph queries
  final case object GetCurrentSceneGraph extends Command
  final case class SubscribeToSceneUpdates(subscriber: ActorRef[SceneGraphUpdate]) extends Command
  final case class UnsubscribeFromSceneUpdates(subscriber: ActorRef[SceneGraphUpdate]) extends Command

  // Internal messages
  private final case class LayoutCompleted(success: Boolean) extends Command
  private final case class NotifySubscribers() extends Command

  // Response types
  final case class InitializationResponse(success: Boolean, message: String)
  final case class UserOperationResponse(success: Boolean, userId: String, message: String)
  final case class SceneGraphUpdate(sceneGraph: SceneGraph)

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new RoomActor(context))

  def apply(userManager: ActorRef[UserManager.Command], furnitureManager: ActorRef[FurnitureManager.Command]): Behavior[Command] =
    Behaviors.setup(context => new RoomActor(context, Some(userManager), Some(furnitureManager)))
}

class RoomActor(
  context: ActorContext[RoomActor.Command],
  userManagerOpt: Option[ActorRef[UserManager.Command]] = None,
  furnitureManagerOpt: Option[ActorRef[FurnitureManager.Command]] = None
) extends AbstractBehavior[RoomActor.Command](context) with LazyLogging {

  import RoomActor._
  import UserManager._
  import FurnitureManager._

  // Child actors
  private val userManager: ActorRef[UserManager.Command] = userManagerOpt.getOrElse(
    context.spawn(UserManager(), "user-manager")
  )

  private val furnitureManager: ActorRef[FurnitureManager.Command] = furnitureManagerOpt.getOrElse(
    context.spawn(
      FurnitureManager(LLMClient()),
      "furniture-manager",
      SupervisorStrategy.restart
    )
  )

  // Room state
  private var roomConfig: Option[RoomConfig] = None
  private var pendingLayoutUpdate: Boolean = false

  // Scene update subscribers (WebSocket clients)
  private var subscribers: immutable.Set[ActorRef[SceneGraphUpdate]] = immutable.Set.empty

  // Lifecycle hooks
  override def preStart: Unit = {
    logger.info("RoomActor starting")
  }

  override def postStop: Unit = {
    logger.info("RoomActor stopped")
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    logger.debug(s"RoomActor received: ${msg.getClass.getSimpleName}")
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

      case GetCurrentSceneGraph =>
        handleGetCurrentSceneGraph()

      case SubscribeToSceneUpdates(subscriber) =>
        handleSubscribe(subscriber)

      case UnsubscribeFromSceneUpdates(subscriber) =>
        handleUnsubscribe(subscriber)

      case LayoutCompleted(success) =>
        handleLayoutCompleted(success)

      case NotifySubscribers =>
        handleNotifySubscribers()
    }
    this
  }

  private def handleInitialize(config: RoomConfig, replyTo: ActorRef[InitializationResponse]): Unit = {
    logger.info(s"Initializing room: ${config.name} (${config.id})")
    roomConfig = Some(config)

    replyTo ! InitializationResponse(
      success = true,
      message = s"Room ${config.name} initialized successfully"
    )

    // Broadcast initial empty scene
    broadcastSceneUpdate()
  }

  private def handleUserJoined(user: UserNode, replyTo: ActorRef[UserOperationResponse]): Unit = {
    logger.info(s"User joined: ${user.username} (${user.id})")

    // Ask UserManager to track the user
    context.ask(userManager, (ref: ActorRef[UserAddedResponse]) => TrackUser(user, ref)) {
      case Success(UserAddedResponse(true, _)) =>
        UserJoinedInternal(user, replyTo)
      case Success(UserAddedResponse(false, _)) =>
        UserJoinFailed(user.id, "Failed to track user", replyTo)
      case Failure(ex) =>
        UserJoinFailed(user.id, ex.getMessage, replyTo)
    }

    // Switch to a behavior that handles the response
    context.self ! UserJoinedInternal(user, replyTo)
  }

  // Internal messages for async handling
  private case class UserJoinedInternal(user: UserNode, replyTo: ActorRef[UserOperationResponse]) extends Command
  private case class UserJoinFailed(userId: String, reason: String, replyTo: ActorRef[UserOperationResponse]) extends Command

  private def handleUserJoinedInternal(user: UserNode, replyTo: ActorRef[UserOperationResponse]): Behavior[Command] = {
    replyTo ! UserOperationResponse(success = true, user.id, s"User ${user.username} joined")

    // Trigger furniture layout update
    triggerLayoutUpdate()

    // Broadcast updated scene
    broadcastSceneUpdate()

    this
  }

  private def handleUserJoinFailed(userId: String, reason: String, replyTo: ActorRef[UserOperationResponse]): Behavior[Command] = {
    logger.error(s"User join failed for $userId: $reason")
    replyTo ! UserOperationResponse(success = false, userId, reason)
    this
  }

  private def handleUserLeft(userId: String, replyTo: ActorRef[UserOperationResponse]): Unit = {
    logger.info(s"User left: $userId")

    // Untrack user and release furniture
    context.pipeToSelf(
      context.ask(userManager, (ref: ActorRef[UserRemovedResponse]) => UntrackUser(userId, ref)) {
        case Success(UserRemovedResponse(true, _)) => true
        case _ => false
      }
    ) {
      case Success(userRemoved) =>
        if (userRemoved) {
          // Also release furniture
          furnitureManager ! UserLeft(userId, context.spawnAnonymous(
            Behaviors.receiveMessage[FurnitureReleasedResponse] {
              case FurnitureReleasedResponse(true) =>
                logger.info(s"Furniture released for user $userId")
                broadcastSceneUpdate()
                Behaviors.stopped
            }
          ))
        }
        LayoutCompleted(success = true)
      case Failure(ex) =>
        logger.error("Failed to process user left", ex)
        LayoutCompleted(success = false)
    }

    replyTo ! UserOperationResponse(success = true, userId, s"User $userId left")
  }

  private def handleUserActivityChanged(userId: String, activity: Option[UserActivity]): Unit = {
    logger.info(s"User activity changed: $userId -> ${activity.map(_.name).getOrElse("None")}")

    context.ask(userManager, (ref: ActorRef[ActivityUpdatedResponse]) => UpdateActivity(userId, activity, ref)) {
      case Success(ActivityUpdatedResponse(true, _)) =>
        logger.info(s"Activity updated for $userId")
        // Trigger layout update as furniture may change based on activity
        triggerLayoutUpdate()
        NotifySubscribers()
      case _ =>
        logger.warn(s"Failed to update activity for $userId")
        Behaviors.same
    }

    broadcastSceneUpdate()
  }

  private def handleUserSpeakingChanged(userId: String, isSpeaking: Boolean): Unit = {
    logger.debug(s"User speaking state changed: $userId -> $isSpeaking")
    userManager ! UpdateSpeakingState(userId, isSpeaking)
    broadcastSceneUpdate()
  }

  private def handleGetCurrentSceneGraph(): Unit = {
    // This would be used in ask pattern
    logger.debug("Scene graph requested")
  }

  private def handleSubscribe(subscriber: ActorRef[SceneGraphUpdate]): Unit = {
    logger.info(s"New subscriber: ${subscriber.path.name}")
    subscribers += subscriber

    // Send current scene immediately
    context.self ! NotifySubscribers
  }

  private def handleUnsubscribe(subscriber: ActorRef[SceneGraphUpdate]): Unit = {
    logger.info(s"Unsubscribing: ${subscriber.path.name}")
    subscribers -= subscriber
  }

  private def handleLayoutCompleted(success: Boolean): Unit = {
    logger.info(s"Layout update completed: $success")
    pendingLayoutUpdate = false
    broadcastSceneUpdate()
  }

  private def handleNotifySubscribers(): Unit = {
    subscribers.foreach(_ ! generateSceneGraph())
  }

  /**
   * Trigger furniture layout update
   */
  private def triggerLayoutUpdate(): Unit = {
    if (pendingLayoutUpdate) {
      logger.debug("Layout update already pending, skipping")
      return
    }

    pendingLayoutUpdate = true

    // Get current users and trigger layout generation
    context.ask(userManager, GetActiveUsers) {
      case Success(users: Seq[UserNode] @unchecked) =>
        roomConfig match {
          case Some(config) =>
            context.ask(furnitureManager, (ref: ActorRef[LayoutGeneratedResponse]) =>
              GenerateLayout(users, config, ref)
            ) {
              case Success(LayoutGeneratedResponse(true, _, _)) =>
                LayoutCompleted(success = true)
              case _ =>
                LayoutCompleted(success = false)
            }
            Behaviors.same
          case None =>
            logger.warn("Room not initialized")
            LayoutCompleted(success = false)
            Behaviors.same
        }
      case _ =>
        logger.error("Failed to get users for layout")
        LayoutCompleted(success = false)
        Behaviors.same
    }
  }

  /**
   * Generate current scene graph
   */
  private def generateSceneGraph(): SceneGraphUpdate = {
    // In a real implementation, we'd query the actors for current state
    // For now, construct from what we have
    val scene = roomConfig match {
      case Some(config) =>
        SceneGraph.create(
          users = Seq.empty, // Would be populated from UserManager
          furniture = Seq.empty, // Would be populated from FurnitureManager
          room = config
        )
      case None =>
        SceneGraph.create(
          users = Seq.empty,
          furniture = Seq.empty,
          room = RoomConfig(
            id = "uninitialized",
            name = "Uninitialized",
            dimensions = RoomDimensions(10, 3, 10),
            maxUsers = 10
          )
        )
    }

    SceneGraphUpdate(scene)
  }

  /**
   * Broadcast scene graph to all subscribers
   */
  private def broadcastSceneUpdate(): Unit = {
    if (subscribers.nonEmpty) {
      logger.debug(s"Broadcasting scene update to ${subscribers.size} subscribers")
      context.self ! NotifySubscribers
    }
  }
}
