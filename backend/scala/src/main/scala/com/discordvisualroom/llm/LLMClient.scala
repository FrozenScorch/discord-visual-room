package com.discordvisualroom.llm

import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging
import sttp.client3._
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * LLM Client for communicating with llama.cpp server
 * Includes graceful fallback to deterministic layouts
 */
class LLMClient(config: LLMConfig)(implicit ec: ExecutionContext)
  extends LazyLogging {

  private val backend: SttpBackend[Future, _] = AsyncHttpClientFutureBackend()

  /**
   * Generate furniture layout using LLM with fallback
   * Returns Either[Left=FallbackAssignments, Right=LLMAssignments]
   */
  def generateFurnitureLayout(
    users: Seq[UserNode],
    roomConfig: RoomConfig
  ): Future[Either[Seq[FurnitureAssignment], Seq[FurnitureAssignment]]] = {

    if (users.isEmpty) {
      logger.info("No users, returning empty layout")
      return Future.successful(Right(Seq.empty))
    }

    logger.info(s"Requesting LLM layout for ${users.size} users")

    val request = buildLLMRequest(users, roomConfig)

    // Try LLM with timeout
    val llmResponse = try {
      val basicRequest = basicRequest
        .post(uri"${config.baseUrl}/completion")
        .body(request)
        .timeout(config.timeoutMs)
        .response(asStringAlways)

      basicRequest.send(backend)
    } catch {
      case ex: Exception =>
        logger.error("LLM request failed", ex)
        return Future.successful(Left(LayoutGenerator.generateSmartFallbackLayout(users)))
    }

    llmResponse.map { response =>
      parseLLMResponse(response.body, users)
    }.recover {
      case ex: Exception =>
        logger.error("Failed to parse LLM response", ex)
        Left(LayoutGenerator.generateSmartFallbackLayout(users))
    }
  }

  /**
   * Build LLM prompt request
   */
  private def buildLLMRequest(users: Seq[UserNode], roomConfig: RoomConfig): String = {
    val usersJson = users.map { user =>
      s"""{"id": "${user.id}", "username": "${user.username}", "activity": ${user.activity.map(a =>
        s"""{"name": "${a.name}", "type": "${a.activityType.typeName}"}"""
      ).getOrElse("null")}}"""
    }.mkString("[", ", ", "]")

    val validFurniture = FurnitureType.allValues.map(_.typeName).mkString(", ")

    s"""You are a furniture layout assistant. Given these users and their activities, assign ONE furniture type from the EXACT list below to each user.
       |
       |VALID FURNITURE TYPES (use ONLY these):
       |- COMPUTER_DESK: For competitive gaming
       |- COUCH_2_SEATER: For casual co-op games
       |- COUCH_SINGLE: For solo/AFK users
       |- BAR_STOOL: For mobile/handheld games
       |
       |Users and activities:
       |$usersJson
       |
       |Room capacity: ${roomConfig.maxUsers}
       |Available furniture: $validFurniture
       |
       |Return ONLY valid JSON array of assignments (no markdown, no extra text):
       |[{"userId": "...", "furniture": "COMPUTER_DESK"}, ...]
       |
       |IMPORTANT: Only use the exact furniture types listed above. Do not invent new types.
       |""".stripMargin
  }

  /**
   * Parse LLM response and validate
   * Returns Left(fallback) if validation fails, Right(valid_assignments) if successful
   */
  private def parseLLMResponse(
    response: String,
    users: Seq[UserNode]
  ): Either[Seq[FurnitureAssignment], Seq[FurnitureAssignment]] = {

    logger.debug(s"LLM response: $response")

    // Extract JSON from response (handle markdown code blocks)
    val jsonContent = extractJson(response)

    // Parse JSON
    val assignmentsTry = Try {
      import org.json4s._
      import org.json4s.jackson.JsonMethods._

      implicit val formats: Formats = DefaultFormats

      val json = parse(jsonContent)
      json.extract[Seq[Map[String, String]]].map { obj =>
        FurnitureAssignment(
          userId = obj("userId"),
          furniture = obj("furniture")
        )
      }
    }

    assignmentsTry match {
      case Success(assignments) =>
        validateAssignments(assignments, users)

      case Failure(ex) =>
        logger.warn(s"Failed to parse LLM JSON response: ${ex.getMessage}")
        Left(LayoutGenerator.generateSmartFallbackLayout(users))
    }
  }

  /**
   * Extract JSON from response (handles markdown code blocks)
   */
  private def extractJson(response: String): String = {
    val trimmed = response.trim

    // Check for markdown code blocks
    val codeBlockRegex = """```(?:json)?\s*([\s\S]*?)\s*```""".r
    trimmed match {
      case codeBlockRegex(json) => json.trim
      case _ => trimmed
    }
  }

  /**
   * Validate furniture assignments
   * Ensures all user IDs exist and furniture types are valid
   */
  private def validateAssignments(
    assignments: Seq[FurnitureAssignment],
    users: Seq[UserNode]
  ): Either[Seq[FurnitureAssignment], Seq[FurnitureAssignment]] = {

    val userIds = users.map(_.id).toSet

    val errors = scala.collection.mutable.ArrayBuffer[String]()

    // Check each assignment
    assignments.foreach { assignment =>
      // Validate user ID exists
      if (!userIds.contains(assignment.userId)) {
        errors += s"Unknown user ID: ${assignment.userId}")
      }

      // Validate furniture type
      if (FurnitureType.fromString(assignment.furniture).isEmpty) {
        errors += s"Invalid furniture type: ${assignment.furniture}")
      }
    }

    // Check all users are assigned
    val assignedUserIds = assignments.map(_.userId).toSet
    val missingUsers = userIds -- assignedUserIds
    if (missingUsers.nonEmpty) {
      errors += s"Missing assignments for users: ${missingUsers.mkString(", ")}"
    }

    if (errors.nonEmpty) {
      logger.warn(s"LLM validation failed: ${errors.mkString("; ")}")
      Left(sanitizeAndComplete(assignments, users))
    } else {
      logger.info("LLM assignments validated successfully")
      Right(assignments)
    }
  }

  /**
   * Sanitize invalid assignments and complete missing ones
   */
  private def sanitizeAndComplete(
    assignments: Seq[FurnitureAssignment],
    users: Seq[UserNode]
  ): Seq[FurnitureAssignment] = {

    logger.info("Sanitizing and completing LLM assignments")

    // Keep valid assignments
    val validAssignments = assignments.filter { assignment =>
      FurnitureType.fromString(assignment.furniture).isDefined &&
        users.exists(_.id == assignment.userId)
    }

    // Find users without assignments
    val assignedUserIds = validAssignments.map(_.userId).toSet
    val unassignedUsers = users.filterNot(u => assignedUserIds.contains(u.id))

    // Generate fallback for unassigned users
    val fallbackAssignments = unassignedUsers.map { user =>
      val furnitureType = user.activity match {
        case Some(activity) if activity.activityType == ActivityType.Playing =>
          FurnitureType.ComputerDesk.typeName
        case _ =>
          FurnitureType.CouchSingle.typeName
      }

      FurnitureAssignment(
        userId = user.id,
        furniture = furnitureType
      )
    }

    validAssignments ++ fallbackAssignments
  }

  /**
   * Close the HTTP client
   */
  def close(): Unit = {
    Try(backend.close()).getOrElse(
      logger.warn("Failed to close HTTP backend")
    )
  }
}

object LLMClient {
  def apply(config: LLMConfig)(implicit ec: ExecutionContext): LLMClient =
    new LLMClient(config)

  def apply()(implicit ec: ExecutionContext): LLMClient = {
    val config = LLMConfig(
      baseUrl = "http://192.168.68.62:1234",
      timeoutMs = 5000,
      maxRetries = 2
    )
    new LLMClient(config)
  }
}
