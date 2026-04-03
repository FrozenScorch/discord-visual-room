package com.discordvisualroom.actors

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.discordvisualroom.model._
import com.discordvisualroom.llm.{LLMClient, LayoutGenerator}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

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
  final case class UserJoined(user: UserNode) extends Command
  final case class UserLeft(userId: String) extends Command
  final case class UserActivityChanged(userId: String, activity: Option[UserActivity]) extends Command
  final case class UserSpeakingChanged(userId: String, isSpeaking: Boolean) extends Command

  // Scene graph queries
  final case class GetCurrentSceneGraph(replyTo: ActorRef[SceneGraphUpdate]) extends Command
  final case class SubscribeToSceneUpdates(subscriber: ActorRef[SceneGraphUpdate]) extends Command
  final case class UnsubscribeFromSceneUpdates(subscriber: ActorRef[SceneGraphUpdate]) extends Command

  // Internal messages
  private[actors] final case class UsersSnapshot(users: immutable.Seq[UserNode]) extends Command
  private[actors] final case class FurnitureSnapshot(furniture: immutable.Seq[FurnitureNode]) extends Command
  private[actors] final case class LayoutCompleted(success: Boolean) extends Command
  private[actors] final case object NotifySubscribers extends Command

  // Response types
  final case class InitializationResponse(success: Boolean, message: String)
  final case class SceneGraphUpdate(sceneGraph: SceneGraph)

  def apply(llmConfig: LLMConfig): Behavior[Command] =
    Behaviors.setup(context => new RoomActor(context, llmConfig, None))

  /**
   * Create a RoomActor with a parent GuildManager that receives state updates.
   * Used when RoomActor is managed by GuildManager for multi-room guild visualization.
   * The roomConfig is set immediately (no separate Initialize message needed).
   */
  def apply(
    roomConfig: RoomConfig,
    llmConfig: LLMConfig,
    parentRef: ActorRef[GuildManager.Command]
  ): Behavior[Command] = Behaviors.setup(context => {
    val actor = new RoomActor(context, llmConfig, Some(parentRef))
    actor.roomConfig = Some(roomConfig)
    actor
  })
}

class RoomActor(
  context: ActorContext[RoomActor.Command],
  llmConfig: LLMConfig,
  parentRef: Option[ActorRef[GuildManager.Command]]
) extends AbstractBehavior[RoomActor.Command](context) with LazyLogging {

  import RoomActor._

  private implicit val ec: ExecutionContextExecutor = context.executionContext

  /**
   * Notify parent GuildManager of current state change.
   * Safe to call even when parentRef is None (standalone mode).
   */
  private def notifyParent(): Unit = {
    parentRef.foreach { ref =>
      val config = roomConfig.getOrElse(
        RoomConfig(id = "uninitialized", name = "Uninitialized", dimensions = RoomDimensions(10, 3, 10), maxUsers = 10)
      )
      // Position users at their assigned furniture before reporting
      val positionedUsers = cachedUsers.map { user =>
        val furniturePos = cachedFurniture
          .find(_.assignedUserId.contains(user.id))
          .map(f => f.position.copy(y = 0, z = f.position.z + 1.0))
        user.copy(
          position = furniturePos.getOrElse(user.position),
          currentFurnitureId = cachedFurniture.find(_.assignedUserId.contains(user.id)).map(_.id)
        )
      }
      ref ! GuildManager.RoomStateUpdate(config.id, positionedUsers, cachedFurniture)
    }
  }

  // Child actors
  private val userManager: ActorRef[UserManager.Command] = context.spawn(
    UserManager(),
    "user-manager"
  )

  private val furnitureManager: ActorRef[FurnitureManager.Command] = context.spawn(
    Behaviors.supervise(FurnitureManager(new LLMClient(llmConfig))).onFailure(SupervisorStrategy.restart),
    "furniture-manager"
  )

  // Room state
  private var roomConfig: Option[RoomConfig] = None
  private var pendingLayoutUpdate: Boolean = false

  // Cached state from child actors (updated on every change)
  private var cachedUsers: immutable.Seq[UserNode] = immutable.Seq.empty
  private var cachedFurniture: immutable.Seq[FurnitureNode] = immutable.Seq.empty

  // Scene update subscribers (WebSocket clients)
  private var subscribers: immutable.Set[ActorRef[SceneGraphUpdate]] = immutable.Set.empty

  override def onMessage(msg: Command): Behavior[Command] = {
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

      case GetCurrentSceneGraph(replyTo) =>
        replyTo ! generateSceneGraph()

      case SubscribeToSceneUpdates(subscriber) =>
        handleSubscribe(subscriber)

      case UnsubscribeFromSceneUpdates(subscriber) =>
        handleUnsubscribe(subscriber)

      case UsersSnapshot(users) =>
        cachedUsers = users
        broadcastSceneUpdate()

      case FurnitureSnapshot(furniture) =>
        cachedFurniture = furniture
        broadcastSceneUpdate()

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

    broadcastSceneUpdate()
  }

  private def handleUserJoined(user: UserNode): Unit = {
    logger.info(s"User joined: ${user.username} (${user.id})")

    // Track user in UserManager
    userManager ! UserManager.TrackUser(user, context.messageAdapter[UserManager.UserAddedResponse] { response =>
      if (response.success) {
        logger.info(s"User ${user.username} tracked successfully")
      } else {
        logger.warn(s"Failed to track user ${user.username}")
      }
      // Request fresh user list
      userManager ! UserManager.GetActiveUsers(context.messageAdapter[UserManager.ActiveUsersResponse] { usersResp =>
        UsersSnapshot(usersResp.users)
      })
      // Trigger layout update
      triggerLayoutUpdate()
      NotifySubscribers
    })

    // Optimistically add to cache for immediate UI update
    if (!cachedUsers.exists(_.id == user.id)) {
      cachedUsers = cachedUsers :+ user
    }
    broadcastSceneUpdate()
  }

  private def handleUserLeft(userId: String): Unit = {
    logger.info(s"User left: $userId")

    // Remove from UserManager
    userManager ! UserManager.UntrackUser(userId, context.messageAdapter[UserManager.UserRemovedResponse] { _ =>
      // Request fresh user list
      userManager ! UserManager.GetActiveUsers(context.messageAdapter[UserManager.ActiveUsersResponse] { usersResp =>
        UsersSnapshot(usersResp.users)
      })
      NotifySubscribers
    })

    // Release furniture
    furnitureManager ! FurnitureManager.UserLeft(userId, context.messageAdapter[FurnitureManager.FurnitureReleasedResponse] { _ =>
      furnitureManager ! FurnitureManager.GetFurniture(context.messageAdapter[FurnitureManager.CurrentFurnitureResponse] { resp =>
        FurnitureSnapshot(resp.furniture.to(immutable.Seq))
      })
      NotifySubscribers
    })

    // Optimistically remove from cache
    cachedUsers = cachedUsers.filterNot(_.id == userId)
    cachedFurniture = cachedFurniture.filterNot(_.assignedUserId.contains(userId))
    broadcastSceneUpdate()
  }

  private def handleUserActivityChanged(userId: String, activity: Option[UserActivity]): Unit = {
    logger.info(s"User activity changed: $userId -> ${activity.map(_.name).getOrElse("None")}")

    userManager ! UserManager.UpdateActivity(userId, activity, context.messageAdapter[UserManager.ActivityUpdatedResponse] { response =>
      if (response.success) {
        // Get fresh user list
        userManager ! UserManager.GetActiveUsers(context.messageAdapter[UserManager.ActiveUsersResponse] { usersResp =>
          UsersSnapshot(usersResp.users)
        })
        // Re-layout since activity changed
        triggerLayoutUpdate()
      }
      NotifySubscribers
    })

    // Optimistically update cache
    cachedUsers = cachedUsers.map { u =>
      if (u.id == userId) u.copy(activity = activity) else u
    }
    broadcastSceneUpdate()
  }

  private def handleUserSpeakingChanged(userId: String, isSpeaking: Boolean): Unit = {
    userManager ! UserManager.UpdateSpeakingState(userId, isSpeaking)

    // Optimistically update cache and broadcast immediately (speaking is latency-sensitive)
    cachedUsers = cachedUsers.map { u =>
      if (u.id == userId) u.copy(isSpeaking = isSpeaking) else u
    }
    broadcastSceneUpdate()
  }

  private def handleSubscribe(subscriber: ActorRef[SceneGraphUpdate]): Unit = {
    logger.info(s"New subscriber: ${subscriber.path.name}")
    subscribers += subscriber

    // Send current scene immediately
    subscriber ! generateSceneGraph()
  }

  private def handleUnsubscribe(subscriber: ActorRef[SceneGraphUpdate]): Unit = {
    logger.info(s"Unsubscribing: ${subscriber.path.name}")
    subscribers -= subscriber
  }

  private def handleLayoutCompleted(success: Boolean): Unit = {
    logger.info(s"Layout update completed: $success")
    pendingLayoutUpdate = false

    // Fetch updated furniture from FurnitureManager
    furnitureManager ! FurnitureManager.GetFurniture(context.messageAdapter[FurnitureManager.CurrentFurnitureResponse] { resp =>
      FurnitureSnapshot(resp.furniture.to(immutable.Seq))
    })
  }

  private def handleNotifySubscribers(): Unit = {
    val update = generateSceneGraph()
    subscribers.foreach(_ ! update)
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

    val users = cachedUsers
    roomConfig match {
      case Some(config) =>
        furnitureManager ! FurnitureManager.GenerateLayout(
          users,
          config,
          context.messageAdapter[FurnitureManager.LayoutGeneratedResponse] { response =>
            LayoutCompleted(response.success)
          }
        )
      case None =>
        logger.warn("Room not initialized, cannot generate layout")
        pendingLayoutUpdate = false
    }
  }

  /**
   * Generate current scene graph from cached state
   */
  private def generateSceneGraph(): SceneGraphUpdate = {
    val config = roomConfig.getOrElse(
      RoomConfig(
        id = "uninitialized",
        name = "Uninitialized",
        dimensions = RoomDimensions(10, 3, 10),
        maxUsers = 10
      )
    )

    // Position users at their assigned furniture
    val positionedUsers = cachedUsers.map { user =>
      val furniturePos = cachedFurniture
        .find(_.assignedUserId.contains(user.id))
        .map(f => f.position.copy(y = 0, z = f.position.z + 1.0)) // Slightly in front of furniture
      user.copy(
        position = furniturePos.getOrElse(user.position),
        currentFurnitureId = cachedFurniture.find(_.assignedUserId.contains(user.id)).map(_.id)
      )
    }

    SceneGraphUpdate(
      SceneGraph.create(
        users = positionedUsers,
        furniture = cachedFurniture,
        room = config
      )
    )
  }

  /**
   * Broadcast scene graph to all subscribers and notify parent GuildManager
   */
  private def broadcastSceneUpdate(): Unit = {
    notifyParent()
    if (subscribers.nonEmpty) {
      context.self ! NotifySubscribers
    }
  }
}
