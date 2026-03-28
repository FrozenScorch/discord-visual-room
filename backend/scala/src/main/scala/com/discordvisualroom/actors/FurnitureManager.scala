package com.discordvisualroom.actors

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.discordvisualroom.model._
import com.discordvisualroom.llm.{LLMClient, LayoutGenerator}
import com.typesafe.scalalogging.LazyLogging

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
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

  // Internal
  private[actors] final case class ApplyLayout(
    assignments: Seq[FurnitureAssignment],
    users: Seq[UserNode],
    replyTo: ActorRef[LayoutGeneratedResponse],
    usedFallback: Boolean
  ) extends Command

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

  // Current furniture in the room
  private var furniture: Seq[FurnitureNode] = Seq.empty

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case GenerateLayout(users, roomConfig, replyTo) =>
        handleGenerateLayout(users, roomConfig, replyTo)
      case ApplyLayout(assignments, users, replyTo, usedFallback) =>
        handleApplyLayout(assignments, users, replyTo, usedFallback)
      case AssignFurniture(assignments, replyTo) =>
        handleAssignFurniture(assignments, replyTo)
      case GetFurniture(replyTo) =>
        replyTo ! CurrentFurnitureResponse(furniture)
      case UserLeft(userId, replyTo) =>
        handleUserLeft(userId, replyTo)
      case ClearAllFurniture =>
        furniture = Seq.empty
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
      case Success(Right(assignments)) =>
        logger.info(s"LLM generated ${assignments.size} furniture assignments")
        ApplyLayout(assignments, users, replyTo, usedFallback = false)
      case Success(Left(fallbackAssignments)) =>
        logger.warn("LLM failed or returned invalid response, using fallback layout")
        ApplyLayout(fallbackAssignments, users, replyTo, usedFallback = true)
      case Failure(ex) =>
        logger.error("LLM request failed, using fallback layout", ex)
        val fallbackAssignments = LayoutGenerator.generateSmartFallbackLayout(users)
        ApplyLayout(fallbackAssignments, users, replyTo, usedFallback = true)
    }
  }

  private def handleApplyLayout(
    assignments: Seq[FurnitureAssignment],
    users: Seq[UserNode],
    replyTo: ActorRef[LayoutGeneratedResponse],
    usedFallback: Boolean
  ): Unit = {
    furniture = assignments.zipWithIndex.map { case (assignment, index) =>
      val position = calculatePosition(index, assignments.size)
      val furnitureType = FurnitureType.fromString(assignment.furniture).getOrElse(FurnitureType.ComputerDesk)

      FurnitureNode(
        id = s"furniture-${UUID.randomUUID().toString.take(8)}",
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

    logger.info(s"Applied layout: ${furniture.size} furniture nodes (fallback=$usedFallback)")
    replyTo ! LayoutGeneratedResponse(success = true, furniture = furniture, usedFallback = usedFallback)
  }

  private def handleAssignFurniture(
    assignments: Seq[FurnitureAssignment],
    replyTo: ActorRef[FurnitureAssignedResponse]
  ): Unit = {
    furniture = assignments.zipWithIndex.map { case (assignment, index) =>
      val position = calculatePosition(index, assignments.size)
      val furnitureType = FurnitureType.fromString(assignment.furniture).getOrElse(FurnitureType.ComputerDesk)

      FurnitureNode(
        id = s"furniture-${UUID.randomUUID().toString.take(8)}",
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

    replyTo ! FurnitureAssignedResponse(success = true, furniture.size)
  }

  private def handleUserLeft(userId: String, replyTo: ActorRef[FurnitureReleasedResponse]): Unit = {
    val originalSize = furniture.size
    // Remove furniture assigned to the leaving user (single-occupancy)
    // Unassign from multi-occupancy
    furniture = furniture.flatMap { f =>
      if (f.assignedUserId.contains(userId)) {
        if (f.capacity > 1) {
          Some(f.unassignUser) // Keep multi-seat furniture
        } else {
          None // Remove single-seat furniture
        }
      } else {
        Some(f)
      }
    }

    val removedCount = originalSize - furniture.size
    logger.info(s"User $userId left, removed $removedCount furniture pieces")
    replyTo ! FurnitureReleasedResponse(success = true)
  }

  /**
   * Calculate position for furniture in a circular layout
   */
  private def calculatePosition(index: Int, total: Int): Vector3D = {
    if (total == 1) {
      return Vector3D(0, 0, 0)
    }

    val radius = math.max(3.0, total * 1.2)
    val angle = (2 * math.Pi * index) / total
    Vector3D(
      x = math.cos(angle) * radius,
      y = 0,
      z = math.sin(angle) * radius
    )
  }
}
