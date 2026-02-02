package com.discordvisualroom.llm

import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging

/**
 * Fallback layout generator when LLM is unavailable
 * Generates deterministic grid-based layouts
 */
object LayoutGenerator extends LazyLogging {

  /**
   * Generate a linear grid layout of COMPUTER_DESK furniture
   * This is the ultimate fallback when everything else fails
   *
   * @param count Number of furniture pieces to generate
   * @return Sequence of furniture assignments
   */
  def generateLinearLayout(count: Int): Seq[FurnitureAssignment] = {
    logger.info(s"Generating linear fallback layout for $count users")

    (0 until count).map { index =>
      FurnitureAssignment(
        userId = s"user-$index", // Will be mapped to actual user IDs
        furniture = FurnitureType.ComputerDesk.typeName
      )
    }
  }

  /**
   * Generate a smart layout based on user activities
   * Maps activities to appropriate furniture types
   *
   * @param users Users in the room
   * @return Sequence of furniture assignments
   */
  def generateSmartFallbackLayout(users: Seq[UserNode]): Seq[FurnitureAssignment] = {
    logger.info(s"Generating smart fallback layout for ${users.size} users")

    users.map { user =>
      val furnitureType = user.activity match {
        case Some(activity) =>
          activity.activityType match {
            case ActivityType.Playing =>
              // Gaming - assign based on game name heuristics
              if (isCompetitiveGame(activity.name)) {
                FurnitureType.ComputerDesk
              } else if (isCoopGame(activity.name)) {
                FurnitureType.Couch2Seater
              } else if (isMobileGame(activity.name)) {
                FurnitureType.BarStool
              } else {
                FurnitureType.ComputerDesk
              }

            case ActivityType.Streaming | ActivityType.Watching =>
              FurnitureType.CouchSingle

            case ActivityType.Listening =>
              FurnitureType.CouchSingle

            case ActivityType.Competing =>
              FurnitureType.ComputerDesk
          }

        case None =>
          // No activity - solo/AFK
          FurnitureType.CouchSingle
      }

      FurnitureAssignment(
        userId = user.id,
        furniture = furnitureType.typeName
      )
    }
  }

  /**
   * Generate a grouped layout for co-op players
   * Players on couches are grouped together
   *
   * @param users Users in the room
   * @return Sequence of furniture assignments with positions
   */
  def generateGroupedLayout(users: Seq[UserNode]): Seq[FurnitureAssignment] = {
    logger.info(s"Generating grouped layout for ${users.size} users")

    val coopPlayers = users.filter(u =>
      u.activity.exists(a =>
        a.activityType == ActivityType.Playing && isCoopGame(a.name)
      )
    )

    val otherPlayers = users.filterNot(u => coopPlayers.exists(_.id == u.id))

    // Group co-op players on 2-seater couches
    val couchAssignments = coopPlayers.grouped(2).flatMap { group =>
      group.map { user =>
        FurnitureAssignment(
          userId = user.id,
          furniture = FurnitureType.Couch2Seater.typeName
        )
      }
    }

    // Others get individual desks or stools
    val otherAssignments = otherPlayers.map { user =>
      val furnitureType = user.activity match {
        case Some(activity) if isMobileGame(activity.name) => FurnitureType.BarStool
        case _ => FurnitureType.ComputerDesk
      }

      FurnitureAssignment(
        userId = user.id,
        furniture = furnitureType.typeName
      )
    }

    couchAssignments.toSeq ++ otherAssignments
  }

  /**
   * Check if game appears to be competitive (FPS, MOBA, competitive)
   */
  private def isCompetitiveGame(gameName: String): Boolean = {
    val competitiveKeywords = Seq(
      "valorant", "cs:go", "csgo", "overwatch", "apex", "league of legends",
      "dota", "rocket league", "rainbow six", "call of duty", "fortnite",
      "pubg", "teamfight", "hearthstone", "starcraft", "street fighter",
      "tekken", "fighting", "shooter", "moba", "competitive", "ranked"
    )

    competitiveKeywords.exists(keyword =>
      gameName.toLowerCase.contains(keyword)
    )
  }

  /**
   * Check if game appears to be co-op (can be played on a couch)
   */
  private def isCoopGame(gameName: String): Boolean = {
    val coopKeywords = Seq(
      "it takes two", "overcooked", "cuphead", "minecraft", "stardew",
      "rocket league", "fortnite", "fall guys", "human fall flat",
      "moving out", "unnatural", "broforce", "castle crashers",
      "battletoads", "diablo", "divinity", "outward", "minecraft dungeons",
      "phasmophobia", "raft", "trine", "way out", "a way out"
    )

    coopKeywords.exists(keyword =>
      gameName.toLowerCase.contains(keyword)
    )
  }

  /**
   * Check if game appears to be mobile/handheld
   */
  private def isMobileGame(gameName: String): Boolean = {
    val mobileKeywords = Seq(
      "pokemon go", "among us", "clash", "pubg mobile", "call of duty mobile",
      "genshin", "honkai", "pokemon unite", "legends of runeterra",
      "magic arena", "apex mobile", "fall guys mobile", "roblox",
      "minecraft earth", "niantic", "mobile game"
    )

    mobileKeywords.exists(keyword =>
      gameName.toLowerCase.contains(keyword)
    )
  }

  /**
   * Calculate position for furniture in a grid
   */
  def calculateGridPosition(index: Int, total: Int, spacing: Double = 3.0): Vector3D = {
    val gridSize = math.ceil(math.sqrt(total.toDouble)).toInt
    val row = index / gridSize
    val col = index % gridSize

    val offsetX = (gridSize - 1) * spacing / 2.0
    val offsetZ = (gridSize - 1) * spacing / 2.0

    Vector3D(
      x = col * spacing - offsetX,
      y = 0,
      z = row * spacing - offsetZ
    )
  }

  /**
   * Calculate position for grouped layout (couches together, desks in grid)
   */
  def calculateGroupedPositions(assignments: Seq[FurnitureAssignment]): Seq[(String, Vector3D)] = {
    val couchSpacing = 4.0 // Couches need more space
    val deskSpacing = 3.0

    val (couchAssignments, otherAssignments) = assignments.partition(_.furniture == FurnitureType.Couch2Seater.typeName)

    val couchPositions = couchAssignments.zipWithIndex.map { case (assignment, index) =>
      val row = index / 2
      val col = index % 2
      assignment.userId -> Vector3D(
        x = col * couchSpacing - couchSpacing / 2,
        y = 0,
        z = row * couchSpacing
      )
    }

    val otherPositions = otherAssignments.zipWithIndex.map { case (assignment, index) =>
      val pos = calculateGridPosition(index, otherAssignments.size, deskSpacing)
      assignment.userId -> Vector3D(
        x = pos.x + 5, // Offset desks to the right
        y = 0,
        z = pos.z
      )
    }

    couchPositions ++ otherPositions
  }
}
