package com.discordvisualroom.actors

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import com.discordvisualroom.model._
import com.discordvisualroom.llm.LLMClient
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

/**
 * Unit tests for FurnitureManager Actor
 * Tests furniture layout generation, assignment, and fallback behavior
 */
class FurnitureManagerSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testKit: ActorTestKit = ActorTestKit()

  implicit val ec: ExecutionContextExecutor = testKit.system.executionContext

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  // ========================================
  // Test Data Helpers
  // ========================================

  def createTestUsers(count: Int): Seq[UserNode] = {
    (1 to count).map { i =>
      UserNode(
        id = s"user-$i",
        username = s"User$i",
        displayName = s"Test User $i",
        avatar = s"avatar$i.png",
        position = Vector3D.zero,
        rotation = Vector3D.zero,
        activity = Some(UserActivity(s"Game $i", ActivityType.Playing))
      )
    }
  }

  def createTestRoomConfig(): RoomConfig = {
    RoomConfig(
      id = "test-room",
      name = "Test Room",
      dimensions = RoomDimensions(width = 10.0, height = 3.0, depth = 10.0),
      maxUsers = 10
    )
  }

  // ========================================
  // Layout Generation Tests
  // ========================================

  "FurnitureManager" should "generate layout for zero users" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    val users = Seq.empty[UserNode]
    val roomConfig = createTestRoomConfig()

    furnitureManager ! FurnitureManager.GenerateLayout(users, roomConfig, probe.ref)

    val response = probe.receiveMessage()
    response.success should be(true)
    response.furniture should be(empty)
    response.usedFallback should be(false)
  }

  it should "generate layout for single user" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    val users = createTestUsers(1)
    val roomConfig = createTestRoomConfig()

    furnitureManager ! FurnitureManager.GenerateLayout(users, roomConfig, probe.ref)

    val response = probe.receiveMessage(5.seconds)
    response.success should be(true)
    response.furniture should have size 1
    response.furniture.head.assignedUserId should be(Some("user-1"))
  }

  it should "generate layout for multiple users" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    val users = createTestUsers(5)
    val roomConfig = createTestRoomConfig()

    furnitureManager ! FurnitureManager.GenerateLayout(users, roomConfig, probe.ref)

    val response = probe.receiveMessage(5.seconds)
    response.success should be(true)
    response.furniture should have size 5
  }

  it should "use fallback when LLM fails" in {
    val failingLLMClient = new FailingMockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(failingLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    val users = createTestUsers(3)
    val roomConfig = createTestRoomConfig()

    furnitureManager ! FurnitureManager.GenerateLayout(users, roomConfig, probe.ref)

    val response = probe.receiveMessage(5.seconds)
    response.success should be(true)
    response.furniture should not be empty
    response.usedFallback should be(true)
  }

  it should "use valid furniture types only" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    val users = createTestUsers(3)
    val roomConfig = createTestRoomConfig()

    furnitureManager ! FurnitureManager.GenerateLayout(users, roomConfig, probe.ref)

    val response = probe.receiveMessage(5.seconds)
    response.furniture.foreach { furniture =>
      FurnitureType.fromString(furniture.furnitureType.typeName).isDefined should be(true)
    }
  }

  // ========================================
  // Furniture Assignment Tests
  // ========================================

  it should "assign furniture to users correctly" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()

    val assignments = Seq(
      FurnitureAssignment("user-1", FurnitureType.ComputerDesk.typeName),
      FurnitureAssignment("user-2", FurnitureType.CouchSingle.typeName)
    )

    furnitureManager ! FurnitureManager.AssignFurniture(assignments, probe.ref)

    val response = probe.receiveMessage()
    response.success should be(true)
    response.count should be(2)
  }

  it should "set correct capacity for 2-seater couch" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    val assignments = Seq(
      FurnitureAssignment("user-1", FurnitureType.Couch2Seater.typeName)
    )

    furnitureManager ! FurnitureManager.AssignFurniture(assignments, probe.ref)
    probe.receiveMessage()

    furnitureManager ! FurnitureManager.GetFurniture(getProbe.ref)
    val response = getProbe.receiveMessage()

    response.furniture.head.capacity should be(2)
  }

  it should "set capacity 1 for non-couch furniture" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    val assignments = Seq(
      FurnitureAssignment("user-1", FurnitureType.ComputerDesk.typeName),
      FurnitureAssignment("user-2", FurnitureType.BarStool.typeName),
      FurnitureAssignment("user-3", FurnitureType.CouchSingle.typeName)
    )

    furnitureManager ! FurnitureManager.AssignFurniture(assignments, probe.ref)
    probe.receiveMessage()

    furnitureManager ! FurnitureManager.GetFurniture(getProbe.ref)
    val response = getProbe.receiveMessage()

    response.furniture.foreach(_.capacity should be(1))
  }

  // ========================================
  // Get Furniture Tests
  // ========================================

  it should "return current furniture" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    val assignments = Seq(
      FurnitureAssignment("user-1", FurnitureType.ComputerDesk.typeName)
    )

    furnitureManager ! FurnitureManager.AssignFurniture(assignments, assignProbe.ref)
    assignProbe.receiveMessage()

    furnitureManager ! FurnitureManager.GetFurniture(getProbe.ref)
    val response = getProbe.receiveMessage()

    response.furniture should have size 1
  }

  it should "return empty furniture initially" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    furnitureManager ! FurnitureManager.GetFurniture(probe.ref)
    val response = probe.receiveMessage()

    response.furniture should be(empty)
  }

  // ========================================
  // User Left Tests
  // ========================================

  it should "release furniture when user leaves" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val leaveProbe = testKit.createTestProbe[FurnitureManager.FurnitureReleasedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    val assignments = Seq(
      FurnitureAssignment("user-1", FurnitureType.ComputerDesk.typeName)
    )

    furnitureManager ! FurnitureManager.AssignFurniture(assignments, assignProbe.ref)
    assignProbe.receiveMessage()

    furnitureManager ! FurnitureManager.UserLeft("user-1", leaveProbe.ref)
    leaveProbe.receiveMessage().success should be(true)

    // Furniture should be removed (single-occupancy furniture with no user)
    furnitureManager ! FurnitureManager.GetFurniture(getProbe.ref)
    val response = getProbe.receiveMessage()
    response.furniture should be(empty)
  }

  it should "keep 2-seater couch when user leaves" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val leaveProbe = testKit.createTestProbe[FurnitureManager.FurnitureReleasedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    val assignments = Seq(
      FurnitureAssignment("user-1", FurnitureType.Couch2Seater.typeName)
    )

    furnitureManager ! FurnitureManager.AssignFurniture(assignments, assignProbe.ref)
    assignProbe.receiveMessage()

    furnitureManager ! FurnitureManager.UserLeft("user-1", leaveProbe.ref)
    leaveProbe.receiveMessage().success should be(true)

    // Couch should remain (capacity > 1)
    furnitureManager ! FurnitureManager.GetFurniture(getProbe.ref)
    val response = getProbe.receiveMessage()
    response.furniture should have size 1
    response.furniture.head.assignedUserId should be(None)
  }

  it should "handle user leaving with no furniture" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.FurnitureReleasedResponse]()

    furnitureManager ! FurnitureManager.UserLeft("nonexistent-user", probe.ref)
    probe.receiveMessage().success should be(true)
  }

  // ========================================
  // Clear All Furniture Tests
  // ========================================

  it should "clear all furniture" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    val assignments = Seq(
      FurnitureAssignment("user-1", FurnitureType.ComputerDesk.typeName),
      FurnitureAssignment("user-2", FurnitureType.Couch2Seater.typeName)
    )

    furnitureManager ! FurnitureManager.AssignFurniture(assignments, assignProbe.ref)
    assignProbe.receiveMessage()

    furnitureManager ! FurnitureManager.ClearAllFurniture

    // Small delay to ensure message is processed
    Thread.sleep(100)

    furnitureManager ! FurnitureManager.GetFurniture(getProbe.ref)
    val response = getProbe.receiveMessage()
    response.furniture should be(empty)
  }

  // ========================================
  // Position Calculation Tests
  // ========================================

  it should "calculate non-overlapping positions for furniture" in {
    val mockLLMClient = new MockLLMClient()
    val furnitureManager = testKit.spawn(FurnitureManager(mockLLMClient))
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    val users = createTestUsers(4)
    val roomConfig = createTestRoomConfig()

    furnitureManager ! FurnitureManager.GenerateLayout(users, roomConfig, probe.ref)
    probe.receiveMessage(5.seconds)

    furnitureManager ! FurnitureManager.GetFurniture(getProbe.ref)
    val response = getProbe.receiveMessage()

    val positions = response.furniture.map(_.position)
    // All positions should be unique
    positions.distinct should have size positions.size
  }

  // ========================================
  // Mock LLM Client for Testing
  // ========================================

  class MockLLMClient extends LLMClient(LLMConfig(
    baseUrl = "http://localhost:1234",
    timeoutMs = 5000,
    maxRetries = 1
  )) {
    override def generateFurnitureLayout(
      users: Seq[UserNode],
      roomConfig: RoomConfig
    ): scala.concurrent.Future[Either[Seq[FurnitureAssignment], Seq[FurnitureAssignment]]] = {
      import scala.concurrent.Future

      val assignments = users.zipWithIndex.map { case (user, index) =>
        val furnitureType = index % 4 match {
          case 0 => FurnitureType.ComputerDesk.typeName
          case 1 => FurnitureType.Couch2Seater.typeName
          case 2 => FurnitureType.CouchSingle.typeName
          case 3 => FurnitureType.BarStool.typeName
        }
        FurnitureAssignment(user.id, furnitureType)
      }

      Future.successful(Right(assignments))
    }
  }

  class FailingMockLLMClient extends LLMClient(LLMConfig(
    baseUrl = "http://localhost:1234",
    timeoutMs = 100,
    maxRetries = 0
  )) {
    override def generateFurnitureLayout(
      users: Seq[UserNode],
      roomConfig: RoomConfig
    ): scala.concurrent.Future[Either[Seq[FurnitureAssignment], Seq[FurnitureAssignment]]] = {
      import scala.concurrent.Future
      import scala.util.Try

      // Simulate LLM failure
      Future.failed(new Exception("LLM connection failed"))
    }
  }
}
