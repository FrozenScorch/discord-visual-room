package com.discordvisualroom.actors

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.discordvisualroom.model._
import com.discordvisualroom.llm.{LLMClient, LayoutGenerator}
import com.typesafe.scalalogging.LazyLogging

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * FurnitureManager - Manages furniture placement and assignments
 * Generates layouts using LLM with graceful fallback
 */
object FurnitureManager extends LazyLogging {
  sealed trait Command

  final case class GenerateLayout(
    users: Seq[UserNode],
    roomConfig: RoomConfig,
    replyTo: ActorRef[LayoutGeneratedResponse]
  ) extends Command

  final case class AssignFurniture(
    assignments: Seq[FurnitureAssignment],
    replyTo: ActorRef[FurnitureAssignedResponse]
  ) extends Command

  final case class GetFurniture(replyTo: ActorRef[CurrentFurnitureResponse]) extends Command
  final case class UserLeft(userId: String, replyTo: ActorRef[FurnitureReleasedResponse]) extends Command
  final case object ClearAllFurniture extends Command

  final case class LayoutGeneratedResponse(
    success: Boolean,
    furniture: Seq[FurnitureNode],
    usedFallback: Boolean = false
  )

  final case class FurnitureAssignedResponse(success: Boolean, count: Int)
  final case class CurrentFurnitureResponse(furniture: Seq[FurnitureNode])
  final case class FurnitureReleasedResponse(success: Boolean)

  def apply(llmClient: LLMClient): Behavior[Command] =
    Behaviors.setup(context => new FurnitureManager(context, llmClient))
}

class FurnitureManager(
  context: ActorContext[FurnitureManager.Command],
  llmClient: LLMClient
) extends AbstractBehavior[FurnitureManager.Command](context) with LazyLogging {

  import FurnitureManager._

  private implicit val ec: ExecutionContextExecutor = context.executionContext
  private implicit val scheduler: Scheduler = context.system.scheduler

  // Current furniture in the room
  private var furniture: Seq[FurnitureNode] = Seq.empty

  override def onMessage(msg: Command): Behavior[Command] = {
    logger.debug(s"FurnitureManager received: ${msg.getClass.getSimpleName}")
    msg match {
      case GenerateLayout(users, roomConfig, replyTo) =>
        handleGenerateLayout(users, roomConfig, replyTo)
      case AssignFurniture(assignments, replyTo) =>
        handleAssignFurniture(assignments, replyTo)
      case GetFurniture(replyTo) =>
        handleGetFurniture(replyTo)
      case UserLeft(userId, replyTo) =>
        handleUserLeft(userId, replyTo)
      case ClearAllFurniture =>
        handleClearAll()
    }
    this
  }

  private def handleGenerateLayout(
    users: Seq[UserNode],
    roomConfig: RoomConfig,
    replyTo: ActorRef[LayoutGeneratedResponse]
  ): Unit = {
    if (users.isEmpty) {
      logger.info("No users, clearing all furniture")
      furniture = Seq.empty
      replyTo ! LayoutGeneratedResponse(success = true, furniture = Seq.empty, usedFallback = false)
      return
    }

    logger.info(s"Generating layout for ${users.size} users")

    // Try LLM first, with fallback
    val layoutFuture = llmClient.generateFurnitureLayout(users, roomConfig)

    context.pipeToSelf(layoutFuture) {
      case Success(result) =>
        result match {
          case Right(assignments) =>
            logger.info(s"LLM generated ${assignments.size} furniture assignments")
            ApplyValidatedLayout(assignments, replyTo, usedFallback = false)
          case Left(fallbackAssignments) =>
            logger.warn("LLM failed or returned invalid response, using fallback layout")
            ApplyValidatedLayout(fallbackAssignments, replyTo, usedFallback = true)
        }
      case Failure(ex) =>
        logger.error("LLM request failed, using fallback layout", ex)
        val fallbackAssignments = LayoutGenerator.generateLinearLayout(users.size)
        ApplyValidatedLayout(fallbackAssignments, replyTo, usedFallback = true)
    }
  }

  // Internal message to apply layout
  private case class ApplyValidatedLayout(
    assignments: Seq[FurnitureAssignment],
    replyTo: ActorRef[LayoutGeneratedResponse],
    usedFallback: Boolean
  ) extends Command

  private def handleAssignFurniture(
    assignments: Seq[FurnitureAssignment],
    replyTo: ActorRef[FurnitureAssignedResponse]
  ): Unit = {
    logger.info(s"Assigning ${assignments.size} furniture pieces")

    furniture = assignments.zipWithIndex.map { case (assignment, index) =>
      val position = calculatePosition(index, assignments.size)
      val furnitureType = FurnitureType.unsafeFromString(assignment.furniture)

      FurnitureNode(
        id = s"furniture-${UUID.randomUUID().toString}",
        furnitureType = furnitureType,
        position = position,
        rotation = Vector3D(0, 0, 0),
        assignedUserId = Some(assignment.userId),
        capacity = furnitureType match {
          case FurnitureType.Couch2Seater => 2
          case _ => 1
        }
      )
    }

    logger.info(s"Created ${furniture.size} furniture nodes")
    replyTo ! FurnitureAssignedResponse(success = true, furniture.size)
  }

  private def handleGetFurniture(replyTo: ActorRef[CurrentFurnitureResponse]): Unit = {
    replyTo ! CurrentFurnitureResponse(furniture)
  }

  private def handleUserLeft(userId: String, replyTo: ActorRef[FurnitureReleasedResponse]): Unit = {
    val originalSize = furniture.size
    furniture = furniture.map { f =>
      if (f.assignedUserId.contains(userId)) {
        f.unassignUser
      } else {
        f
      }
    }.filterNot(f => f.assignedUserId.isEmpty && f.capacity == 1) // Remove unassigned single-occupancy furniture

    val removedCount = originalSize - furniture.size
    logger.info(s"User $userId left, removed $removedCount unassigned furniture pieces")
    replyTo ! FurnitureReleasedResponse(success = true)
  }

  private def handleClearAll(): Unit = {
    logger.info("Clearing all furniture")
    furniture = Seq.empty
  }

  /**
   * Calculate position for furniture in a grid layout
   */
  private def calculatePosition(index: Int, total: Int): Vector3D = {
    val gridSize = math.ceil(math.sqrt(total)).toInt
    val spacing = 3.0 // meters between furniture
    val row = index / gridSize
    val col = index % gridSize

    // Center the grid
    val offsetX = (gridSize - 1) * spacing / 2.0
    val offsetZ = (gridSize - 1) * spacing / 2.0

    Vector3D(
      x = col * spacing - offsetX,
      y = 0, // Ground level
      z = row * spacing - offsetZ
    )
  }

  /**
   * Get current furniture (for internal use by RoomActor)
   */
  def getCurrentFurniture: Seq[FurnitureNode] = furniture

  /**
   * Find furniture by user assignment
   */
  def findFurnitureByUser(userId: String): Option[FurnitureNode] =
    furniture.find(_.assignedUserId.contains(userId))

  /**
   * Find furniture by ID
   */
  def findFurnitureById(furnitureId: String): Option[FurnitureNode] =
    furniture.find(_.id == furnitureId)
}
