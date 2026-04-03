package com.discordvisualroom.health

import java.time.Instant
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}

/**
 * Health status enumeration
 */
sealed trait HealthStatus {
  def name: String
}

object HealthStatus {
  case object Healthy extends HealthStatus { val name: String = "healthy" }
  case object Degraded extends HealthStatus { val name: String = "degraded" }
  case object Unhealthy extends HealthStatus { val name: String = "unhealthy" }

  def fromString(value: String): HealthStatus = value.toLowerCase match {
    case "healthy" => Healthy
    case "degraded" => Degraded
    case "unhealthy" => Unhealthy
    case _ => Unhealthy
  }
}

/**
 * Component health information
 */
case class ComponentHealth(
  name: String,
  status: HealthStatus,
  lastCheck: Instant = Instant.now(),
  details: Map[String, String] = Map.empty,
  error: Option[String] = None
)

/**
 * Overall health check response
 */
case class HealthCheckResponse(
  status: HealthStatus,
  components: Map[String, ComponentHealth],
  timestamp: Instant = Instant.now(),
  version: String = "1.0.0"
)

/**
 * Health check result
 */
case class HealthCheckResult(
  isHealthy: Boolean,
  status: HealthStatus,
  details: Map[String, String] = Map.empty,
  error: Option[String] = None
)

/**
 * Base trait for health checks
 */
trait HealthCheck {
  def name: String
  def check()(implicit ec: ExecutionContext): Future[HealthCheckResult]
}

/**
 * Health checker that aggregates multiple component health checks
 */
class HealthChecker(version: String = "1.0.0") {

  private var healthChecks: immutable.Seq[HealthCheck] = immutable.Seq.empty

  /**
   * Register a health check
   */
  def register(check: HealthCheck): Unit = {
    healthChecks = healthChecks :+ check
  }

  /**
   * Unregister a health check by name
   */
  def unregister(name: String): Unit = {
    healthChecks = healthChecks.filterNot(_.name == name)
  }

  /**
   * Check health of all registered components
   */
  def checkAll()(implicit ec: ExecutionContext): Future[HealthCheckResponse] = {
    if (healthChecks.isEmpty) {
      Future.successful(HealthCheckResponse(
        status = HealthStatus.Healthy,
        components = Map.empty,
        version = version
      ))
    } else {
      Future.sequence(
        healthChecks.map(check => check.check().map(result => check.name -> result))
      ).map { results =>
        val resultsMap = results.toMap
        val componentHealthMap = resultsMap.map { case (name, result) =>
          name -> ComponentHealth(
            name = name,
            status = result.status,
            details = result.details,
            error = result.error
          )
        }

        val overallStatus = determineOverallStatus(componentHealthMap.values.toSeq)

        HealthCheckResponse(
          status = overallStatus,
          components = componentHealthMap,
          version = version
        )
      }
    }
  }

  /**
   * Check a specific component
   */
  def checkComponent(name: String)(implicit ec: ExecutionContext): Future[Option[ComponentHealth]] = {
    healthChecks.find(_.name == name) match {
      case Some(check) =>
        check.check().map { result =>
          Some(ComponentHealth(
            name = name,
            status = result.status,
            details = result.details,
            error = result.error
          ))
        }
      case None =>
        Future.successful(None)
    }
  }

  /**
   * Determine overall health status from component statuses
   */
  private def determineOverallStatus(components: immutable.Seq[ComponentHealth]): HealthStatus = {
    if (components.isEmpty) {
      HealthStatus.Healthy
    } else if (components.exists(_.status == HealthStatus.Unhealthy)) {
      HealthStatus.Unhealthy
    } else if (components.exists(_.status == HealthStatus.Degraded)) {
      HealthStatus.Degraded
    } else {
      HealthStatus.Healthy
    }
  }

  /**
   * Get quick health status (without detailed checks)
   */
  def quickStatus(): HealthCheckResponse = {
    HealthCheckResponse(
      status = HealthStatus.Healthy,
      components = Map.empty,
      version = version
    )
  }
}

/**
 * Health check for LLM service
 */
class LLMHealthCheck(llmUrl: String, timeoutMs: Long = 5000) extends HealthCheck {
  override val name: String = "llm"

  override def check()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    import java.net.http.{HttpClient, HttpRequest, HttpResponse}
    import java.net.URI
    import java.time.Duration

    val startTime = System.currentTimeMillis()

    try {
      val client = HttpClient.newHttpClient()
      val request = HttpRequest.newBuilder()
        .uri(URI.create(s"$llmUrl/health"))
        .timeout(Duration.ofMillis(timeoutMs))
        .GET()
        .build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      val duration = System.currentTimeMillis() - startTime

      response.statusCode() match {
        case 200 =>
          Future.successful(HealthCheckResult(
            isHealthy = true,
            status = HealthStatus.Healthy,
            details = Map(
              "responseTime" -> duration.toString,
              "url" -> llmUrl
            )
          ))
        case code =>
          Future.successful(HealthCheckResult(
            isHealthy = false,
            status = HealthStatus.Unhealthy,
            details = Map("statusCode" -> code.toString),
            error = Some(s"LLM service returned status $code")
          ))
      }
    } catch {
      case ex: Exception =>
        Future.successful(HealthCheckResult(
          isHealthy = false,
          status = HealthStatus.Unhealthy,
          error = Some(s"LLM service check failed: ${ex.getMessage}")
        ))
    }
  }
}

/**
 * Health check for Discord connection
 */
class DiscordHealthCheck(isConnected: () => Boolean) extends HealthCheck {
  override val name: String = "discord"

  override def check()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    Future {
      if (isConnected()) {
        HealthCheckResult(
          isHealthy = true,
          status = HealthStatus.Healthy,
          details = Map("status" -> "connected")
        )
      } else {
        HealthCheckResult(
          isHealthy = false,
          status = HealthStatus.Unhealthy,
          error = Some("Discord client is not connected")
        )
      }
    }
  }
}

/**
 * Health check for WebSocket service
 */
class WebSocketHealthCheck(activeConnections: () => Int) extends HealthCheck {
  override val name: String = "websocket"

  override def check()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    Future {
      val connections = activeConnections()
      HealthCheckResult(
        isHealthy = true,
        status = HealthStatus.Healthy,
        details = Map(
          "activeConnections" -> connections.toString,
          "status" -> "listening"
        )
      )
    }
  }
}

/**
 * Health check for database (if applicable)
 */
class DatabaseHealthCheck(isConnected: () => Boolean) extends HealthCheck {
  override val name: String = "database"

  override def check()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    Future {
      if (isConnected()) {
        HealthCheckResult(
          isHealthy = true,
          status = HealthStatus.Healthy,
          details = Map("status" -> "connected")
        )
      } else {
        HealthCheckResult(
          isHealthy = false,
          status = HealthStatus.Unhealthy,
          error = Some("Database connection failed")
        )
      }
    }
  }
}

/**
 * Memory health check - warns if memory usage is high
 */
class MemoryHealthCheck(thresholdPercent: Double = 85.0) extends HealthCheck {
  override val name: String = "memory"

  override def check()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    Future {
      val runtime = Runtime.getRuntime
      val maxMemory = runtime.maxMemory()
      val usedMemory = runtime.totalMemory() - runtime.freeMemory()
      val usagePercent = (usedMemory.toDouble / maxMemory.toDouble) * 100

      val (status, error) = if (usagePercent > thresholdPercent) {
        (HealthStatus.Degraded, Some(s"Memory usage is ${f"$usagePercent%.2f"}%"))
      } else {
        (HealthStatus.Healthy, None)
      }

      val healthStatus: HealthStatus = status
      HealthCheckResult(
        isHealthy = healthStatus != HealthStatus.Unhealthy,
        status = healthStatus,
        details = Map(
          "usedMemory" -> (usedMemory / 1024 / 1024).toString,
          "maxMemory" -> (maxMemory / 1024 / 1024).toString,
          "usagePercent" -> f"$usagePercent%.2f"
        ),
        error = error
      )
    }
  }
}

/**
 * Custom health check for user-defined checks
 */
class CustomHealthCheck(
  override val name: String,
  checkFn: () => Future[HealthCheckResult]
) extends HealthCheck {
  override def check()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    checkFn()
  }
}
