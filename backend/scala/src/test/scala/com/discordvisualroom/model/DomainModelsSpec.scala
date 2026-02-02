package com.discordvisualroom.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalacheck.Gen

/**
 * Unit tests and property-based tests for DomainModels
 */
class DomainModelsSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  // ========================================
  // FurnitureType Tests
  // ========================================

  "FurnitureType" should "have exactly 4 valid types" in {
    FurnitureType.allValues should have size 4
  }

  it should "parse valid furniture types correctly" in {
    FurnitureType.fromString("COMPUTER_DESK") should be(Some(FurnitureType.ComputerDesk))
    FurnitureType.fromString("COUCH_2_SEATER") should be(Some(FurnitureType.Couch2Seater))
    FurnitureType.fromString("COUCH_SINGLE") should be(Some(FurnitureType.CouchSingle))
    FurnitureType.fromString("BAR_STOOL") should be(Some(FurnitureType.BarStool))
  }

  it should "return None for invalid furniture types" in {
    FurnitureType.fromString("BEANBAG_CHAIR") should be(None)
    FurnitureType.fromString("computer_desk") should be(None) // Case sensitive
    FurnitureType.fromString("") should be(None)
    FurnitureType.fromString("INVALID") should be(None)
  }

  it should "throw IllegalArgumentException for unsafeFromString with invalid type" in {
    an[IllegalArgumentException] should be thrownBy {
      FurnitureType.unsafeFromString("INVALID_TYPE")
    }
  }

  // Property-based tests
  it should "always return Some for valid furniture types" in {
    val validFurnitureGen = Gen.oneOf(
      "COMPUTER_DESK",
      "COUCH_2_SEATER",
      "COUCH_SINGLE",
      "BAR_STOOL"
    )

    forAll(validFurnitureGen) { furniture =>
      FurnitureType.fromString(furniture).isDefined shouldBe true
    }
  }

  // ========================================
  // ActivityType Tests
  // ========================================

  "ActivityType" should "parse valid activity types" in {
    ActivityType.fromString("PLAYING") should be(Some(ActivityType.Playing))
    ActivityType.fromString("STREAMING") should be(Some(ActivityType.Streaming))
    ActivityType.fromString("LISTENING") should be(Some(ActivityType.Listening))
    ActivityType.fromString("WATCHING") should be(Some(ActivityType.Watching))
    ActivityType.fromString("COMPETING") should be(Some(ActivityType.Competing))
  }

  it should "be case-insensitive" in {
    ActivityType.fromString("playing") should be(Some(ActivityType.Playing))
    ActivityType.fromString("Playing") should be(Some(ActivityType.Playing))
    ActivityType.fromString("STREAMING") should be(Some(ActivityType.Streaming))
  }

  // ========================================
  // Vector3D Tests
  // ========================================

  "Vector3D" should "add two vectors correctly" in {
    val v1 = Vector3D(1.0, 2.0, 3.0)
    val v2 = Vector3D(4.0, 5.0, 6.0)
    val result = v1 + v2

    result.x should be(5.0 +- 0.001)
    result.y should be(7.0 +- 0.001)
    result.z should be(9.0 +- 0.001)
  }

  it should "subtract two vectors correctly" in {
    val v1 = Vector3D(5.0, 7.0, 9.0)
    val v2 = Vector3D(1.0, 2.0, 3.0)
    val result = v1 - v2

    result.x should be(4.0 +- 0.001)
    result.y should be(5.0 +- 0.001)
    result.z should be(6.0 +- 0.001)
  }

  it should "multiply by scalar correctly" in {
    val v = Vector3D(2.0, 3.0, 4.0)
    val result = v * 2.5

    result.x should be(5.0 +- 0.001)
    result.y should be(7.5 +- 0.001)
    result.z should be(10.0 +- 0.001)
  }

  it should "calculate distance correctly" in {
    val v1 = Vector3D(0.0, 0.0, 0.0)
    val v2 = Vector3D(3.0, 4.0, 0.0)
    val distance = v1.distanceTo(v2)

    distance should be(5.0 +- 0.001) // 3-4-5 triangle
  }

  it should "have correct predefined vectors" in {
    Vector3D.zero should be(Vector3D(0, 0, 0))
    Vector3D.up should be(Vector3D(0, 1, 0))
    Vector3D.forward should be(Vector3D(0, 0, 1))
    Vector3D.right should be(Vector3D(1, 0, 0))
  }

  // Property-based test for vector operations
  it should "satisfy commutative property of addition" in {
    forAll { (x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) =>
      val v1 = Vector3D(x1, y1, z1)
      val v2 = Vector3D(x2, y2, z2)

      (v1 + v2) shouldBe (v2 + v1)
    }
  }

  it should "satisfy associative property of addition" in {
    forAll { (x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, x3: Double, y3: Double, z3: Double) =>
      val v1 = Vector3D(x1, y1, z1)
      val v2 = Vector3D(x2, y2, z2)
      val v3 = Vector3D(x3, y3, z3)

      ((v1 + v2) + v3) shouldBe (v1 + (v2 + v3))
    }
  }

  // ========================================
  // UserNode Tests
  // ========================================

  "UserNode" should "create user with default values" in {
    val user = UserNode(
      id = "123456789",
      username = "testuser",
      displayName = "Test User",
      avatar = "avatar.png",
      position = Vector3D.zero,
      rotation = Vector3D.zero
    )

    user.activity should be(None)
    user.isSpeaking should be(false)
    user.currentFurnitureId should be(None)
  }

  it should "create user with activity" in {
    val activity = UserActivity(
      name = "Test Game",
      activityType = ActivityType.Playing
    )

    val user = UserNode(
      id = "123456789",
      username = "testuser",
      displayName = "Test User",
      avatar = "avatar.png",
      position = Vector3D.zero,
      rotation = Vector3D.zero,
      activity = Some(activity)
    )

    user.activity should be(Some(activity))
  }

  // ========================================
  // FurnitureNode Tests
  // ========================================

  "FurnitureNode" should "correctly report availability" in {
    val unassignedFurniture = FurnitureNode(
      id = "furn-1",
      furnitureType = FurnitureType.ComputerDesk,
      position = Vector3D.zero,
      rotation = Vector3D.zero,
      assignedUserId = None
    )

    unassignedFurniture.isAvailable should be(true)
  }

  it should "be available if capacity > 1 even when assigned" in {
    val couch = FurnitureNode(
      id = "couch-1",
      furnitureType = FurnitureType.Couch2Seater,
      position = Vector3D.zero,
      rotation = Vector3D.zero,
      assignedUserId = Some("user-1"),
      capacity = 2
    )

    couch.isAvailable should be(true)
  }

  it should "not be available if single occupancy and assigned" in {
    val desk = FurnitureNode(
      id = "desk-1",
      furnitureType = FurnitureType.ComputerDesk,
      position = Vector3D.zero,
      rotation = Vector3D.zero,
      assignedUserId = Some("user-1"),
      capacity = 1
    )

    desk.isAvailable should be(false)
  }

  it should "assign user correctly" in {
    val furniture = FurnitureNode(
      id = "furn-1",
      furnitureType = FurnitureType.BarStool,
      position = Vector3D.zero,
      rotation = Vector3D.zero,
      assignedUserId = None
    )

    val assigned = furniture.assignUser("user-123")
    assigned.assignedUserId should be(Some("user-123"))
  }

  it should "unassign user correctly" in {
    val furniture = FurnitureNode(
      id = "furn-1",
      furnitureType = FurnitureType.BarStool,
      position = Vector3D.zero,
      rotation = Vector3D.zero,
      assignedUserId = Some("user-123")
    )

    val unassigned = furniture.unassignUser
    unassigned.assignedUserId should be(None)
  }

  // ========================================
  // SceneGraph Tests
  // ========================================

  "SceneGraph" should "create with current version" in {
    val scene = SceneGraph.create(
      users = Seq.empty,
      furniture = Seq.empty,
      room = RoomConfig(
        id = "room-1",
        name = "Test Room",
        dimensions = RoomDimensions(10, 3, 10),
        maxUsers = 10
      )
    )

    scene.version should be(SceneGraph.currentVersion)
    scene.timestamp should be > 0L
    scene.users should be(empty)
    scene.furniture should be(empty)
  }

  // ========================================
  // WSMessageType Tests
  // ========================================

  "WSMessageType" should "parse all message types correctly" in {
    WSMessageType.fromString("SCENE_UPDATE") should be(Some(WSMessageType.SceneUpdate))
    WSMessageType.fromString("USER_JOINED") should be(Some(WSMessageType.UserJoined))
    WSMessageType.fromString("USER_LEFT") should be(Some(WSMessageType.UserLeft))
    WSMessageType.fromString("USER_MOVED") should be(Some(WSMessageType.UserMoved))
    WSMessageType.fromString("ACTIVITY_CHANGED") should be(Some(WSMessageType.ActivityChanged))
    WSMessageType.fromString("ERROR") should be(Some(WSMessageType.Error))
  }

  it should "return None for invalid message types" in {
    WSMessageType.fromString("INVALID") should be(None)
    WSMessageType.fromString("") should be(None)
  }

  // ========================================
  // Property-based tests for furniture assignments
  // ========================================

  "FurnitureAssignment" should "have valid furniture type" in {
    val validFurnitureGen = Gen.oneOf(FurnitureType.allValues.map(_.typeName))

    forAll(Gen.uuid.str, validFurnitureGen) { (userId, furniture) =>
      val assignment = FurnitureAssignment(userId, furniture)
      assignment.userId should not be empty
      FurnitureType.fromString(assignment.furniture).isDefined shouldBe true
    }
  }
}
