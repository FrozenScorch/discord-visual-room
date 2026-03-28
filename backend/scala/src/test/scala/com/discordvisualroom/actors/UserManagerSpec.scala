package com.discordvisualroom.actors

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import com.discordvisualroom.model._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

/**
 * Functional unit tests for UserManager Actor
 * No mocks - tests the real actor behavior end-to-end.
 */
class UserManagerSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testKit: ActorTestKit = ActorTestKit()

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  def createUser(
    id: String = "123456789",
    username: String = "testuser",
    displayName: String = "Test User"
  ): UserNode = UserNode(
    id = id,
    username = username,
    displayName = displayName,
    avatar = "avatar.png",
    position = Vector3D.zero,
    rotation = Vector3D.zero
  )

  def createActivity(name: String = "Test Game"): UserActivity =
    UserActivity(name = name, activityType = ActivityType.Playing)

  // ========================================
  // Track User
  // ========================================

  "UserManager" should "track a new user" in {
    val um = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    um ! UserManager.TrackUser(createUser(), probe.ref)

    val resp = probe.receiveMessage()
    resp.success should be(true)
    resp.userId should be("123456789")
  }

  it should "reject user with non-numeric ID" in {
    val um = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    um ! UserManager.TrackUser(createUser(id = "abc-invalid"), probe.ref)
    probe.receiveMessage().success should be(false)
  }

  it should "reject user with empty ID" in {
    val um = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    um ! UserManager.TrackUser(createUser(id = ""), probe.ref)
    probe.receiveMessage().success should be(false)
  }

  it should "track multiple users" in {
    val um = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    (1 to 5).foreach { i =>
      um ! UserManager.TrackUser(createUser(id = s"${i}11111111", username = s"user$i"), probe.ref)
      probe.receiveMessage().success should be(true)
    }
  }

  it should "update existing user when tracked again" in {
    val um = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    um ! UserManager.TrackUser(createUser(username = "original"), probe.ref)
    probe.receiveMessage().success should be(true)

    um ! UserManager.TrackUser(createUser(username = "updated"), probe.ref)
    probe.receiveMessage().success should be(true)
  }

  // ========================================
  // Untrack User
  // ========================================

  it should "untrack an existing user" in {
    val um = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val removeProbe = testKit.createTestProbe[UserManager.UserRemovedResponse]()

    um ! UserManager.TrackUser(createUser(), addProbe.ref)
    addProbe.receiveMessage()

    um ! UserManager.UntrackUser("123456789", removeProbe.ref)
    val resp = removeProbe.receiveMessage()
    resp.success should be(true)
    resp.userId should be("123456789")
  }

  it should "fail to untrack non-existent user" in {
    val um = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.UserRemovedResponse]()

    um ! UserManager.UntrackUser("nonexistent", probe.ref)
    probe.receiveMessage().success should be(false)
  }

  // ========================================
  // GetActiveUsers
  // ========================================

  it should "return empty list when no users tracked" in {
    val um = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.ActiveUsersResponse]()

    um ! UserManager.GetActiveUsers(probe.ref)
    probe.receiveMessage().users should be(empty)
  }

  it should "return all tracked users" in {
    val um = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val usersProbe = testKit.createTestProbe[UserManager.ActiveUsersResponse]()

    um ! UserManager.TrackUser(createUser(id = "111111111", username = "alice"), addProbe.ref)
    addProbe.receiveMessage()
    um ! UserManager.TrackUser(createUser(id = "222222222", username = "bob"), addProbe.ref)
    addProbe.receiveMessage()

    um ! UserManager.GetActiveUsers(usersProbe.ref)
    val users = usersProbe.receiveMessage().users
    users should have size 2
    users.map(_.username).toSet should be(Set("alice", "bob"))
  }

  it should "reflect removals in active users list" in {
    val um = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val removeProbe = testKit.createTestProbe[UserManager.UserRemovedResponse]()
    val usersProbe = testKit.createTestProbe[UserManager.ActiveUsersResponse]()

    um ! UserManager.TrackUser(createUser(id = "111111111"), addProbe.ref)
    addProbe.receiveMessage()
    um ! UserManager.TrackUser(createUser(id = "222222222"), addProbe.ref)
    addProbe.receiveMessage()

    um ! UserManager.UntrackUser("111111111", removeProbe.ref)
    removeProbe.receiveMessage()

    um ! UserManager.GetActiveUsers(usersProbe.ref)
    val users = usersProbe.receiveMessage().users
    users should have size 1
    users.head.id should be("222222222")
  }

  // ========================================
  // Activity Updates
  // ========================================

  it should "update activity for existing user" in {
    val um = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val actProbe = testKit.createTestProbe[UserManager.ActivityUpdatedResponse]()

    um ! UserManager.TrackUser(createUser(), addProbe.ref)
    addProbe.receiveMessage()

    um ! UserManager.UpdateActivity("123456789", Some(createActivity("Valorant")), actProbe.ref)
    val resp = actProbe.receiveMessage()
    resp.success should be(true)
  }

  it should "fail activity update for non-existent user" in {
    val um = testKit.spawn(UserManager())
    val probe = testKit.createTestProbe[UserManager.ActivityUpdatedResponse]()

    um ! UserManager.UpdateActivity("ghost", Some(createActivity()), probe.ref)
    probe.receiveMessage().success should be(false)
  }

  it should "clear activity when set to None" in {
    val um = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()
    val actProbe = testKit.createTestProbe[UserManager.ActivityUpdatedResponse]()

    um ! UserManager.TrackUser(
      createUser().copy(activity = Some(createActivity())),
      addProbe.ref
    )
    addProbe.receiveMessage()

    um ! UserManager.UpdateActivity("123456789", None, actProbe.ref)
    actProbe.receiveMessage().success should be(true)
  }

  // ========================================
  // Speaking State
  // ========================================

  it should "accept speaking state update for existing user" in {
    val um = testKit.spawn(UserManager())
    val addProbe = testKit.createTestProbe[UserManager.UserAddedResponse]()

    um ! UserManager.TrackUser(createUser(), addProbe.ref)
    addProbe.receiveMessage()

    // Fire-and-forget - should not crash
    um ! UserManager.UpdateSpeakingState("123456789", isSpeaking = true)
    um ! UserManager.UpdateSpeakingState("123456789", isSpeaking = false)

    // Verify actor is still alive by sending another message
    val usersProbe = testKit.createTestProbe[UserManager.ActiveUsersResponse]()
    um ! UserManager.GetActiveUsers(usersProbe.ref)
    usersProbe.receiveMessage().users should have size 1
  }

  it should "handle speaking update for non-existent user gracefully" in {
    val um = testKit.spawn(UserManager())

    // Should not crash
    um ! UserManager.UpdateSpeakingState("ghost", isSpeaking = true)

    val probe = testKit.createTestProbe[UserManager.ActiveUsersResponse]()
    um ! UserManager.GetActiveUsers(probe.ref)
    probe.receiveMessage().users should be(empty)
  }

  // ========================================
  // ID Validation
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
