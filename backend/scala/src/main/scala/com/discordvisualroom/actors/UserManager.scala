package com.discordvisualroom.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable

/**
 * UserManager - Tracks user join/leave, activity changes
 * Maintains the canonical list of active users in the voice channel
 */
object UserManager extends LazyLogging {
  sealed trait Command

  final case class TrackUser(user: UserNode, replyTo: ActorRef[UserAddedResponse]) extends Command
  final case class UntrackUser(userId: String, replyTo: ActorRef[UserRemovedResponse]) extends Command
  final case class UpdateActivity(userId: String, activity: Option[UserActivity], replyTo: ActorRef[ActivityUpdatedResponse]) extends Command
  final case class UpdateSpeakingState(userId: String, isSpeaking: Boolean) extends Command
  final case class GetActiveUsers(replyTo: ActorRef[ActiveUsersResponse]) extends Command

  final case class UserAddedResponse(success: Boolean, userId: String)
  final case class UserRemovedResponse(success: Boolean, userId: String)
  final case class ActivityUpdatedResponse(success: Boolean, userId: String)
  final case class ActiveUsersResponse(users: immutable.Seq[UserNode])

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new UserManager(context))

  /**
   * Validate Discord user ID format (snowflake ID)
   */
  def isValidUserId(userId: String): Boolean = {
    userId != null && userId.nonEmpty && userId.forall(_.isDigit)
  }
}

class UserManager(context: ActorContext[UserManager.Command])
  extends AbstractBehavior[UserManager.Command](context) with LazyLogging {

  import UserManager._

  // Active users map: userId -> UserNode
  private var users: immutable.Seq[UserNode] = immutable.Seq.empty

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case TrackUser(user, replyTo) =>
        handleTrackUser(user, replyTo)
      case UntrackUser(userId, replyTo) =>
        handleUntrackUser(userId, replyTo)
      case UpdateActivity(userId, activity, replyTo) =>
        handleUpdateActivity(userId, activity, replyTo)
      case UpdateSpeakingState(userId, isSpeaking) =>
        handleUpdateSpeakingState(userId, isSpeaking)
      case GetActiveUsers(replyTo) =>
        handleGetActiveUsers(replyTo)
    }
    this
  }

  private def handleTrackUser(user: UserNode, replyTo: ActorRef[UserAddedResponse]): Unit = {
    if (!isValidUserId(user.id)) {
      logger.warn(s"Invalid user ID format: ${user.id}")
      replyTo ! UserAddedResponse(success = false, user.id)
      return
    }

    // Check if user already exists
    if (users.exists(_.id == user.id)) {
      logger.info(s"User ${user.username} (${user.id}) already tracked, updating")
      updateUser(user)
    } else {
      logger.info(s"Tracking new user: ${user.username} (${user.id})")
      users = users :+ user
    }

    replyTo ! UserAddedResponse(success = true, user.id)
    logger.debug(s"Active user count: ${users.size}")
  }

  private def handleUntrackUser(userId: String, replyTo: ActorRef[UserRemovedResponse]): Unit = {
    users.find(_.id == userId) match {
      case Some(user) =>
        logger.info(s"Untracking user: ${user.username} ($userId)")
        users = users.filterNot(_.id == userId)
        replyTo ! UserRemovedResponse(success = true, userId)
        logger.debug(s"Active user count: ${users.size}")
      case None =>
        logger.warn(s"Attempted to untrack non-existent user: $userId")
        replyTo ! UserRemovedResponse(success = false, userId)
    }
  }

  private def handleUpdateActivity(
    userId: String,
    activity: Option[UserActivity],
    replyTo: ActorRef[ActivityUpdatedResponse]
  ): Unit = {
    users.find(_.id == userId) match {
      case Some(existingUser) =>
        val updatedUser = existingUser.copy(activity = activity)
        users = users.map(u => if (u.id == userId) updatedUser else u)
        logger.info(s"Updated activity for ${existingUser.username}: ${activity.map(_.name).getOrElse("None")}")
        replyTo ! ActivityUpdatedResponse(success = true, userId)
      case None =>
        logger.warn(s"Attempted to update activity for non-existent user: $userId")
        replyTo ! ActivityUpdatedResponse(success = false, userId)
    }
  }

  private def handleUpdateSpeakingState(userId: String, isSpeaking: Boolean): Unit = {
    users.find(_.id == userId) match {
      case Some(existingUser) =>
        val updatedUser = existingUser.copy(isSpeaking = isSpeaking)
        users = users.map(u => if (u.id == userId) updatedUser else u)
        logger.debug(s"Updated speaking state for ${existingUser.username}: $isSpeaking")
      case None =>
        logger.warn(s"Attempted to update speaking state for non-existent user: $userId")
    }
  }

  private def handleGetActiveUsers(replyTo: ActorRef[ActiveUsersResponse]): Unit = {
    logger.debug(s"Returning active users: ${users.size}")
    replyTo ! ActiveUsersResponse(users)
  }

  private def updateUser(user: UserNode): Unit = {
    users = users.map { existingUser =>
      if (existingUser.id == user.id) user else existingUser
    }
  }
}
