package com.discordvisualroom.actors

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import com.discordvisualroom.model._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

/**
 * Unit tests for UserManager Actor
 * Tests user tracking, activity updates, and speaking state management
 */
class UserManagerSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // ========================================
  // Test Kit Setup
  // ========================================

  val testKit: ActorTestKit = ActorTestKit()

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  // ========================================
  // Test Data Helpers
  // ========================================

  def createTestUser(
    id: String = "123456789",
    username: String = "testuser",
    displayName: String = "Test User"
  ): UserNode = {
    UserNode(
      id = id,
      username = username,
      displayName = displayName,
      avatar = "avatar.png",
      position = Vector3D.zero,
      rotation = Vector3D.zero
    )
  }

  def createTestActivity(name: String = "Test Game"): UserActivity = {
    UserActivity(
      name = name,
      activityType = ActivityType.Playing
    )
  }

  // ========================================
  // User Tracking Tests
  // ========================================

  "UserManager" should "track a new user successfully" in {
    val userManager = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    val user = createTestUser()

    userManager ! UserManager.TrackUser(user, probe.ref)

    val response = probe.receiveMessage()
    response.success should be(true)
    response.userId should be(user.id)
  }

  it should "reject tracking user with invalid ID" in {
    val userManager = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    val invalidUser = createTestUser(id = "invalid-id-with-letters")

    userManager ! UserManager.TrackUser(invalidUser, probe.ref)

    val response = probe.receiveMessage()
    response.success should be(false)
  }

  it should "reject tracking user with empty ID" in {
    val userManager = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    val invalidUser = createTestUser(id = "")

    userManager ! UserManager.TrackUser(invalidUser, probe.ref)

    val response = probe.receiveMessage()
    response.success should be(false)
  }

  it should "track multiple users" in {
    val userManager = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    val users = (1 to 5).map { i =>
      createTestUser(id = s"$i" * 9, username = s"user$i")
    }

    users.foreach { user =>
      userManager ! UserManager.TrackUser(user, probe.ref)
      val response = probe.receiveMessage()
      response.success should be(true)
    }
  }

  it should "update user if already tracked" in {
    val userManager = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    val user = createTestUser(username = "original_name")
    userManager ! UserManager.TrackUser(user, probe.ref)
    probe.receiveMessage().success should be(true)

    val updatedUser = user.copy(username = "updated_name")
    userManager ! UserManager.TrackUser(updatedUser, probe.ref)
    val response = probe.receiveMessage()
    response.success should be(true)
  }

  // ========================================
  // User Untracking Tests
  // ========================================

  it should "untrack an existing user" in {
    val userManager = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val removeProbe = testKit.createTestProbe[UserManager.UserRemovedResponse]()

    val user = createTestUser()

    // First track the user
    userManager ! UserManager.TrackUser(user, addProbe.ref)
    addProbe.receiveMessage().success should be(true)

    // Then untrack
    userManager ! UserManager.UntrackUser(user.id, removeProbe.ref)
    val response = removeProbe.receiveMessage()

    response.success should be(true)
    response.userId should be(user.id)
  }

  it should "fail to untrack non-existent user" in {
    val userManager = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserRemovedResponse]()

    userManager ! UserManager.UntrackUser("nonexistent-user", probe.ref)
    val response = probe.receiveMessage()

    response.success should be(false)
  }

  it should "handle sequential add and remove operations" in {
    val userManager = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val removeProbe = testKit.createTestProbe[UserManager.UserRemovedResponse]()

    val user1 = createTestUser(id = "111111111", username = "user1")
    val user2 = createTestUser(id = "222222222", username = "user2")

    // Add both users
    userManager ! UserManager.TrackUser(user1, addProbe.ref)
    addProbe.receiveMessage().success should be(true)

    userManager ! UserManager.TrackUser(user2, addProbe.ref)
    addProbe.receiveMessage().success should be(true)

    // Remove user1
    userManager ! UserManager.UntrackUser(user1.id, removeProbe.ref)
    removeProbe.receiveMessage().success should be(true)

    // Verify user2 is still tracked by removing successfully
    userManager ! UserManager.UntrackUser(user2.id, removeProbe.ref)
    removeProbe.receiveMessage().success should be(true)
  }

  // ========================================
  // Activity Update Tests
  // ========================================

  it should "update activity for existing user" in {
    val userManager = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val activityProbe = testKit.createTestProbe[UserManager.ActivityUpdatedResponse]()

    val user = createTestUser()

    // Track user first
    userManager ! UserManager.TrackUser(user, addProbe.ref)
    addProbe.receiveMessage()

    // Update activity
    val activity = createTestActivity("League of Legends")
    userManager ! UserManager.UpdateActivity(user.id, Some(activity), activityProbe.ref)

    val response = activityProbe.receiveMessage()
    response.success should be(true)
    response.userId should be(user.id)
  }

  it should "fail to update activity for non-existent user" in {
    val userManager = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.ActivityUpdatedResponse]()

    val activity = createTestActivity()
    userManager ! UserManager.UpdateActivity("nonexistent", Some(activity), probe.ref)

    val response = probe.receiveMessage()
    response.success should be(false)
  }

  it should "clear activity when setting to None" in {
    val userManager = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val activityProbe = testKit.createTestProbe[UserManager.ActivityUpdatedResponse]()

    val user = createTestUser()

    // Track user with activity
    val userWithActivity = user.copy(activity = Some(createTestActivity()))
    userManager ! UserManager.TrackUser(userWithActivity, addProbe.ref)
    addProbe.receiveMessage()

    // Clear activity
    userManager ! UserManager.UpdateActivity(user.id, None, activityProbe.ref)

    val response = activityProbe.receiveMessage()
    response.success should be(true)
  }

  // ========================================
  // Speaking State Tests
  // ========================================

  it should "update speaking state for existing user" in {
    val userManager = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    val user = createTestUser()

    // Track user first
    userManager ! UserManager.TrackUser(user, addProbe.ref)
    addProbe.receiveMessage()

    // Update speaking state (no response for this message)
    userManager ! UserManager.UpdateSpeakingState(user.id, isSpeaking = true)

    // Should not throw or fail
    succeed
  }

  it should "handle speaking state update for non-existent user gracefully" in {
    val userManager = testKit.spawn(UserManager())

    // Should not throw or fail
    userManager ! UserManager.UpdateSpeakingState("nonexistent", isSpeaking = true)

    succeed
  }

  // ========================================
  // Get Active Users Tests
  // ========================================

  it should "return active users list" in {
    val userManager = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val usersProbe = testKit.createTestProbe[UserManager.ActiveUsersResponse]()

    // Add a user
    val user = createTestUser()
    userManager ! UserManager.TrackUser(user, addProbe.ref)
    addProbe.receiveMessage()

    // Get active users
    userManager ! UserManager.GetActiveUsers(usersProbe.ref)
    val response = usersProbe.receiveMessage()
    response.users should have size 1
    response.users.head.id should be(user.id)
  }

  // ========================================
  // Concurrent Operations Tests
  // ========================================

  it should "handle concurrent user joins" in {
    val userManager = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    val users = (1 to 10).map { i =>
      createTestUser(id = s"$i" * 9, username = s"user$i")
    }

    // Send all track requests concurrently
    users.foreach { user =>
      userManager ! UserManager.TrackUser(user, probe.ref)
    }

    // All should succeed
    (1 to 10).foreach { _ =>
      val response = probe.receiveMessage(3.seconds)
      response.success should be(true)
    }
  }

  it should "handle concurrent activity updates" in {
    val userManager = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val activityProbe = testKit.createTestProbe[UserManager.ActivityUpdatedResponse]()

    val user = createTestUser()

    // Track user first
    userManager ! UserManager.TrackUser(user, addProbe.ref)
    addProbe.receiveMessage()

    // Send multiple activity updates
    val activities = (1 to 5).map { i =>
      createTestActivity(s"Game $i")
    }

    activities.foreach { activity =>
      userManager ! UserManager.UpdateActivity(user.id, Some(activity), activityProbe.ref)
    }

    // All should succeed
    (1 to 5).foreach { _ =>
      val response = activityProbe.receiveMessage(3.seconds)
      response.success should be(true)
    }
  }

  // ========================================
  // ID Validation Tests
  // ========================================

  "UserManager.isValidUserId" should "accept valid Discord snowflake IDs" in {
    UserManager.isValidUserId("123456789012345678") should be(true)
    UserManager.isValidUserId("1") should be(true)
    UserManager.isValidUserId("999999999999999999") should be(true)
  }

  it should "reject invalid IDs" in {
    UserManager.isValidUserId("") should be(false)
    UserManager.isValidUserId("abc123") should be(false)
    UserManager.isValidUserId("123-456") should be(false)
    UserManager.isValidUserId(null) should be(false)
  }
}
