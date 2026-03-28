package com.discordvisualroom.llm

import com.discordvisualroom.model._
import com.typesafe.scalalogging.LazyLogging

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * LLM Client for communicating with llama.cpp server
 * Includes graceful fallback to deterministic layouts
 */
class LLMClient(config: LLMConfig) extends LazyLogging {

  private val httpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofMillis(config.timeoutMs.toLong))
    .build()

  /**
   * Generate furniture layout using LLM with fallback
   * Returns Either[Left=FallbackAssignments, Right=LLMAssignments]
   */
  def generateFurnitureLayout(
    users: Seq[UserNode],
    roomConfig: RoomConfig
  )(implicit ec: ExecutionContext): Future[Either[Seq[FurnitureAssignment], Seq[FurnitureAssignment]]] = {

    if (users.isEmpty) {
      logger.info("No users, returning empty layout")
      return Future.successful(Right(Seq.empty))
    }

    logger.info(s"Requesting LLM layout for ${users.size} users")

    val prompt = buildLLMPrompt(users, roomConfig)
    val requestBody = buildRequestJson(prompt)

    Future {
      try {
        val request = HttpRequest.newBuilder()
          .uri(URI.create(s"${config.baseUrl}/completion"))
          .header("Content-Type", "application/json")
          .timeout(Duration.ofMillis(config.timeoutMs.toLong))
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          parseLLMResponse(response.body(), users)
        } else {
          logger.warn(s"LLM returned HTTP ${response.statusCode()}")
          Left(LayoutGenerator.generateSmartFallbackLayout(users))
        }
      } catch {
        case ex: Exception =>
          logger.error(s"LLM request failed: ${ex.getMessage}")
          Left(LayoutGenerator.generateSmartFallbackLayout(users))
      }
    }
  }

  /**
   * Build the JSON request body for llama.cpp /completion endpoint
   */
  private def buildRequestJson(prompt: String): String = {
    val escapedPrompt = prompt
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

    s"""{"prompt": "$escapedPrompt", "n_predict": 2048, "temperature": 0.3, "top_p": 0.9, "stop": ["</s>"]}"""
  }

  /**
   * Build LLM prompt
   */
  private def buildLLMPrompt(users: Seq[UserNode], roomConfig: RoomConfig): String = {
    val usersJson = users.map { user =>
      val activityJson = user.activity.map(a =>
        s"""{"name": "${a.name}", "type": "${a.activityType.typeName}"}"""
      ).getOrElse("null")
      s"""{"id": "${user.id}", "username": "${user.username}", "activity": $activityJson}"""
    }.mkString("[", ", ", "]")

    s"""You are a furniture layout assistant. Given these users and their activities, assign ONE furniture type from the EXACT list below to each user.

VALID FURNITURE TYPES (use ONLY these):
- COMPUTER_DESK: For competitive gaming
- COUCH_2_SEATER: For casual co-op games
- COUCH_SINGLE: For solo/AFK users
- BAR_STOOL: For mobile/handheld games

Users and activities:
$usersJson

Return ONLY valid JSON array of assignments (no markdown, no extra text):
[{"userId": "...", "furniture": "COMPUTER_DESK"}, ...]

IMPORTANT: Only use the exact furniture types listed above. Do not invent new types."""
  }

  /**
   * Parse LLM response and validate
   */
  private def parseLLMResponse(
    response: String,
    users: Seq[UserNode]
  ): Either[Seq[FurnitureAssignment], Seq[FurnitureAssignment]] = {

    logger.debug(s"LLM response: $response")

    // Extract content field from llama.cpp response JSON
    val content = extractContentFromResponse(response)
    val jsonContent = extractJson(content)

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
   * Extract the "content" field from llama.cpp JSON response
   */
  private def extractContentFromResponse(response: String): String = {
    Try {
      import org.json4s._
      import org.json4s.jackson.JsonMethods._
      implicit val formats: Formats = DefaultFormats
      val json = parse(response)
      (json \ "content").extract[String]
    }.getOrElse(response) // If not JSON, treat whole response as content
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
      case _ =>
        // Try to find a JSON array in the response
        val arrayRegex = """\[\s*\{.*\}\s*\]""".r
        arrayRegex.findFirstIn(trimmed).getOrElse(trimmed)
    }
  }

  /**
   * Validate furniture assignments
   */
  private def validateAssignments(
    assignments: Seq[FurnitureAssignment],
    users: Seq[UserNode]
  ): Either[Seq[FurnitureAssignment], Seq[FurnitureAssignment]] = {

    val userIds = users.map(_.id).toSet
    val errors = scala.collection.mutable.ArrayBuffer[String]()

    assignments.foreach { assignment =>
      if (!userIds.contains(assignment.userId)) {
        errors += s"Unknown user ID: ${assignment.userId}"
      }
      if (FurnitureType.fromString(assignment.furniture).isEmpty) {
        errors += s"Invalid furniture type: ${assignment.furniture}"
      }
    }

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

    val validAssignments = assignments.filter { assignment =>
      FurnitureType.fromString(assignment.furniture).isDefined &&
        users.exists(_.id == assignment.userId)
    }

    val assignedUserIds = validAssignments.map(_.userId).toSet
    val unassignedUsers = users.filterNot(u => assignedUserIds.contains(u.id))

    val fallbackAssignments = unassignedUsers.map { user =>
      val furnitureType = user.activity match {
        case Some(activity) if activity.activityType == ActivityType.Playing =>
          FurnitureType.ComputerDesk.typeName
        case _ =>
          FurnitureType.CouchSingle.typeName
      }
      FurnitureAssignment(userId = user.id, furniture = furnitureType)
    }

    validAssignments ++ fallbackAssignments
  }
}

object LLMClient {
  def apply(config: LLMConfig): LLMClient = new LLMClient(config)

  def apply(): LLMClient = {
    val config = LLMConfig(
      baseUrl = "http://192.168.68.62:1234",
      timeoutMs = 5000,
      maxRetries = 2
    )
    new LLMClient(config)
  }
}
