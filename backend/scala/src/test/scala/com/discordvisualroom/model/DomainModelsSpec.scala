package com.discordvisualroom.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Functional unit tests for domain models.
 * No mocks, no property-based generation - tests real behavior.
 */
class DomainModelsSpec extends AnyFlatSpec with Matchers {

  // ========================================
  // FurnitureType
  // ========================================

  "FurnitureType" should "have exactly 4 valid types" in {
    FurnitureType.allValues should have size 4
  }

  it should "parse all valid furniture type strings" in {
    FurnitureType.fromString("COMPUTER_DESK") should be(Some(FurnitureType.ComputerDesk))
    FurnitureType.fromString("COUCH_2_SEATER") should be(Some(FurnitureType.Couch2Seater))
    FurnitureType.fromString("COUCH_SINGLE") should be(Some(FurnitureType.CouchSingle))
    FurnitureType.fromString("BAR_STOOL") should be(Some(FurnitureType.BarStool))
  }

  it should "return None for invalid types" in {
    FurnitureType.fromString("BEANBAG_CHAIR") should be(None)
    FurnitureType.fromString("computer_desk") should be(None) // Case sensitive
    FurnitureType.fromString("") should be(None)
    FurnitureType.fromString("INVALID") should be(None)
  }

  it should "throw on unsafeFromString with invalid type" in {
    an[IllegalArgumentException] should be thrownBy {
      FurnitureType.unsafeFromString("INVALID_TYPE")
    }
  }

  it should "round-trip all types through typeName and fromString" in {
    FurnitureType.allValues.foreach { ft =>
      FurnitureType.fromString(ft.typeName) should be(Some(ft))
    }
  }

  // ========================================
  // ActivityType
  // ========================================

  "ActivityType" should "parse all valid activity types" in {
    ActivityType.fromString("PLAYING") should be(Some(ActivityType.Playing))
    ActivityType.fromString("STREAMING") should be(Some(ActivityType.Streaming))
    ActivityType.fromString("LISTENING") should be(Some(ActivityType.Listening))
    ActivityType.fromString("WATCHING") should be(Some(ActivityType.Watching))
    ActivityType.fromString("COMPETING") should be(Some(ActivityType.Competing))
  }

  it should "be case-insensitive" in {
    ActivityType.fromString("playing") should be(Some(ActivityType.Playing))
    ActivityType.fromString("Playing") should be(Some(ActivityType.Playing))
  }

  // ========================================
  // Vector3D
  // ========================================

  "Vector3D" should "add vectors" in {
    val result = Vector3D(1, 2, 3) + Vector3D(4, 5, 6)
    result should be(Vector3D(5, 7, 9))
  }

  it should "subtract vectors" in {
    val result = Vector3D(5, 7, 9) - Vector3D(1, 2, 3)
    result should be(Vector3D(4, 5, 6))
  }

  it should "multiply by scalar" in {
    val result = Vector3D(2, 3, 4) * 2.5
    result.x should be(5.0 +- 0.001)
    result.y should be(7.5 +- 0.001)
    result.z should be(10.0 +- 0.001)
  }

  it should "calculate distance (3-4-5 triangle)" in {
    Vector3D(0, 0, 0).distanceTo(Vector3D(3, 4, 0)) should be(5.0 +- 0.001)
  }

  it should "have correct predefined vectors" in {
    Vector3D.zero should be(Vector3D(0, 0, 0))
    Vector3D.up should be(Vector3D(0, 1, 0))
    Vector3D.forward should be(Vector3D(0, 0, 1))
    Vector3D.right should be(Vector3D(1, 0, 0))
  }

  // ========================================
  // UserNode
  // ========================================

  "UserNode" should "have sensible defaults" in {
    val user = UserNode(
      id = "123", username = "test", displayName = "Test",
      avatar = "a.png", position = Vector3D.zero, rotation = Vector3D.zero
    )
    user.activity should be(None)
    user.isSpeaking should be(false)
    user.currentFurnitureId should be(None)
  }

  // ========================================
  // FurnitureNode
  // ========================================

  "FurnitureNode" should "report available when unassigned" in {
    val f = FurnitureNode("f1", FurnitureType.ComputerDesk, Vector3D.zero, Vector3D.zero)
    f.isAvailable should be(true)
  }

  it should "report unavailable when assigned (capacity 1)" in {
    val f = FurnitureNode("f1", FurnitureType.ComputerDesk, Vector3D.zero, Vector3D.zero,
      assignedUserId = Some("u1"), capacity = 1)
    f.isAvailable should be(false)
  }

  it should "report available when assigned but capacity > 1" in {
    val f = FurnitureNode("f1", FurnitureType.Couch2Seater, Vector3D.zero, Vector3D.zero,
      assignedUserId = Some("u1"), capacity = 2)
    f.isAvailable should be(true)
  }

  it should "assign and unassign users" in {
    val f = FurnitureNode("f1", FurnitureType.BarStool, Vector3D.zero, Vector3D.zero)
    val assigned = f.assignUser("u1")
    assigned.assignedUserId should be(Some("u1"))
    assigned.unassignUser.assignedUserId should be(None)
  }

  // ========================================
  // SceneGraph
  // ========================================

  "SceneGraph" should "create with version and timestamp" in {
    val scene = SceneGraph.create(
      users = Seq.empty, furniture = Seq.empty,
      room = RoomConfig("r1", "Room", RoomDimensions(10, 3, 10), 10)
    )
    scene.version should be(SceneGraph.currentVersion)
    scene.timestamp should be > 0L
  }

  // ========================================
  // WSMessageType
  // ========================================

  "WSMessageType" should "parse all valid types" in {
    WSMessageType.fromString("SCENE_UPDATE") should be(Some(WSMessageType.SceneUpdate))
    WSMessageType.fromString("USER_JOINED") should be(Some(WSMessageType.UserJoined))
    WSMessageType.fromString("USER_LEFT") should be(Some(WSMessageType.UserLeft))
    WSMessageType.fromString("ERROR") should be(Some(WSMessageType.Error))
  }

  it should "return None for invalid types" in {
    WSMessageType.fromString("INVALID") should be(None)
    WSMessageType.fromString("") should be(None)
  }
}
