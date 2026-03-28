package com.discordvisualroom.actors

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import com.discordvisualroom.model._
import com.discordvisualroom.llm.{LLMClient, LayoutGenerator}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

/**
 * Functional unit tests for FurnitureManager Actor
 * Tests real actor behavior with the actual LayoutGenerator fallback paths.
 * No mocks - uses the real FurnitureManager with an unreachable LLM endpoint
 * so fallback layout generation is exercised end-to-end.
 */
class FurnitureManagerSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testKit: ActorTestKit = ActorTestKit()

  implicit val ec: ExecutionContextExecutor = testKit.system.executionContext

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  // Use a real LLMClient pointed at an unreachable address.
  // This forces the fallback layout path every time - which is
  // the deterministic code path we can functionally verify.
  private val llmConfig = LLMConfig(
    baseUrl = "http://127.0.0.1:1", // unreachable port
    timeoutMs = 200,
    maxRetries = 0
  )

  private def spawnManager(): akka.actor.typed.ActorRef[FurnitureManager.Command] =
    testKit.spawn(FurnitureManager(new LLMClient(llmConfig)))

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
  // Layout Generation (via fallback)
  // ========================================

  "FurnitureManager" should "return empty furniture for zero users" in {
    val fm = spawnManager()
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    fm ! FurnitureManager.GenerateLayout(Seq.empty, createTestRoomConfig(), probe.ref)

    val response = probe.receiveMessage(3.seconds)
    response.success should be(true)
    response.furniture should be(empty)
  }

  it should "generate exactly one furniture piece for one user" in {
    val fm = spawnManager()
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    fm ! FurnitureManager.GenerateLayout(createTestUsers(1), createTestRoomConfig(), probe.ref)

    val response = probe.receiveMessage(5.seconds)
    response.success should be(true)
    response.furniture should have size 1
    response.furniture.head.assignedUserId should be(Some("user-1"))
  }

  it should "generate one furniture piece per user" in {
    val fm = spawnManager()
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()
    val users = createTestUsers(5)

    fm ! FurnitureManager.GenerateLayout(users, createTestRoomConfig(), probe.ref)

    val response = probe.receiveMessage(5.seconds)
    response.success should be(true)
    response.furniture should have size 5

    // Every user should have furniture assigned
    val assignedIds = response.furniture.flatMap(_.assignedUserId).toSet
    assignedIds should be(users.map(_.id).toSet)
  }

  it should "only use valid furniture types from the Asset Dictionary" in {
    val fm = spawnManager()
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    fm ! FurnitureManager.GenerateLayout(createTestUsers(4), createTestRoomConfig(), probe.ref)

    val response = probe.receiveMessage(5.seconds)
    response.furniture.foreach { f =>
      FurnitureType.allValues should contain(f.furnitureType)
    }
  }

  it should "fall back gracefully when LLM is unreachable" in {
    val fm = spawnManager()
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    fm ! FurnitureManager.GenerateLayout(createTestUsers(3), createTestRoomConfig(), probe.ref)

    val response = probe.receiveMessage(5.seconds)
    response.success should be(true)
    response.usedFallback should be(true)
    response.furniture should have size 3
  }

  // ========================================
  // Direct Furniture Assignment
  // ========================================

  it should "assign furniture directly from a list of assignments" in {
    val fm = spawnManager()
    val probe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()

    val assignments = Seq(
      FurnitureAssignment("user-1", FurnitureType.ComputerDesk.typeName),
      FurnitureAssignment("user-2", FurnitureType.CouchSingle.typeName)
    )

    fm ! FurnitureManager.AssignFurniture(assignments, probe.ref)

    val response = probe.receiveMessage()
    response.success should be(true)
    response.count should be(2)
  }

  it should "set capacity=2 for COUCH_2_SEATER" in {
    val fm = spawnManager()
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    fm ! FurnitureManager.AssignFurniture(
      Seq(FurnitureAssignment("user-1", FurnitureType.Couch2Seater.typeName)),
      assignProbe.ref
    )
    assignProbe.receiveMessage()

    fm ! FurnitureManager.GetFurniture(getProbe.ref)
    val furniture = getProbe.receiveMessage().furniture

    furniture should have size 1
    furniture.head.capacity should be(2)
  }

  it should "set capacity=1 for non-couch furniture" in {
    val fm = spawnManager()
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    fm ! FurnitureManager.AssignFurniture(Seq(
      FurnitureAssignment("u1", FurnitureType.ComputerDesk.typeName),
      FurnitureAssignment("u2", FurnitureType.BarStool.typeName),
      FurnitureAssignment("u3", FurnitureType.CouchSingle.typeName)
    ), assignProbe.ref)
    assignProbe.receiveMessage()

    fm ! FurnitureManager.GetFurniture(getProbe.ref)
    getProbe.receiveMessage().furniture.foreach(_.capacity should be(1))
  }

  // ========================================
  // GetFurniture
  // ========================================

  it should "return empty furniture initially" in {
    val fm = spawnManager()
    val probe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    fm ! FurnitureManager.GetFurniture(probe.ref)
    probe.receiveMessage().furniture should be(empty)
  }

  it should "return furniture after assignment" in {
    val fm = spawnManager()
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    fm ! FurnitureManager.AssignFurniture(
      Seq(FurnitureAssignment("u1", FurnitureType.ComputerDesk.typeName)),
      assignProbe.ref
    )
    assignProbe.receiveMessage()

    fm ! FurnitureManager.GetFurniture(getProbe.ref)
    getProbe.receiveMessage().furniture should have size 1
  }

  // ========================================
  // User Leave
  // ========================================

  it should "remove single-seat furniture when user leaves" in {
    val fm = spawnManager()
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val leaveProbe = testKit.createTestProbe[FurnitureManager.FurnitureReleasedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    fm ! FurnitureManager.AssignFurniture(
      Seq(FurnitureAssignment("user-1", FurnitureType.ComputerDesk.typeName)),
      assignProbe.ref
    )
    assignProbe.receiveMessage()

    fm ! FurnitureManager.UserLeft("user-1", leaveProbe.ref)
    leaveProbe.receiveMessage().success should be(true)

    fm ! FurnitureManager.GetFurniture(getProbe.ref)
    getProbe.receiveMessage().furniture should be(empty)
  }

  it should "keep 2-seater couch (unassigned) when user leaves" in {
    val fm = spawnManager()
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val leaveProbe = testKit.createTestProbe[FurnitureManager.FurnitureReleasedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    fm ! FurnitureManager.AssignFurniture(
      Seq(FurnitureAssignment("user-1", FurnitureType.Couch2Seater.typeName)),
      assignProbe.ref
    )
    assignProbe.receiveMessage()

    fm ! FurnitureManager.UserLeft("user-1", leaveProbe.ref)
    leaveProbe.receiveMessage().success should be(true)

    fm ! FurnitureManager.GetFurniture(getProbe.ref)
    val remaining = getProbe.receiveMessage().furniture
    remaining should have size 1
    remaining.head.assignedUserId should be(None)
  }

  it should "handle leave for non-existent user gracefully" in {
    val fm = spawnManager()
    val probe = testKit.createTestProbe[FurnitureManager.FurnitureReleasedResponse]()

    fm ! FurnitureManager.UserLeft("ghost-user", probe.ref)
    probe.receiveMessage().success should be(true)
  }

  // ========================================
  // ClearAll
  // ========================================

  it should "clear all furniture" in {
    val fm = spawnManager()
    val assignProbe = testKit.createTestProbe[FurnitureManager.FurnitureAssignedResponse]()
    val getProbe = testKit.createTestProbe[FurnitureManager.CurrentFurnitureResponse]()

    fm ! FurnitureManager.AssignFurniture(Seq(
      FurnitureAssignment("u1", FurnitureType.ComputerDesk.typeName),
      FurnitureAssignment("u2", FurnitureType.Couch2Seater.typeName)
    ), assignProbe.ref)
    assignProbe.receiveMessage()

    fm ! FurnitureManager.ClearAllFurniture

    // Give actor time to process fire-and-forget message
    Thread.sleep(100)

    fm ! FurnitureManager.GetFurniture(getProbe.ref)
    getProbe.receiveMessage().furniture should be(empty)
  }

  // ========================================
  // Position Uniqueness
  // ========================================

  it should "place furniture at non-overlapping positions" in {
    val fm = spawnManager()
    val probe = testKit.createTestProbe[FurnitureManager.LayoutGeneratedResponse]()

    fm ! FurnitureManager.GenerateLayout(createTestUsers(6), createTestRoomConfig(), probe.ref)

    val response = probe.receiveMessage(5.seconds)
    val positions = response.furniture.map(_.position)
    positions.distinct should have size positions.size
  }
}
