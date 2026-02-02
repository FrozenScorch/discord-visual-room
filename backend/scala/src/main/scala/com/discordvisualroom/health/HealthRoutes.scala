package com.discordvisualroom.health

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import com.discordvisualroom.metrics.MetricsRegistry

import scala.concurrent.ExecutionContextExecutor

/**
 * JSON serialization for health check types
 */
trait HealthCheckJsonProtocol {
  implicit val healthStatusFormat: RootJsonFormat[HealthStatus] = new RootJsonFormat[HealthStatus] {
    def write(status: HealthStatus): String = status.name
    def read(json: String): HealthStatus = HealthStatus.fromString(json)
  }

  implicit val componentHealthFormat: RootJsonFormat[ComponentHealth] = jsonFormat5(ComponentHealth)
  implicit val healthCheckResponseFormat: RootJsonFormat[HealthCheckResponse] = jsonFormat4(HealthCheckResponse)
}

/**
 * HTTP routes for health checks
 */
class HealthRoutes(
  healthChecker: HealthChecker
)(implicit system: ActorSystem[Nothing]) extends HealthCheckJsonProtocol {

  private implicit val ec: ExecutionContextExecutor = system.executionContext

  /**
   * Main health check endpoint
   * GET /health
   */
  val healthRoute: Route = path("health") {
    get {
      complete(healthChecker.checkAll())
    }
  } ~
  pathPrefix("health") {
    path(Segment) { componentName =>
      get {
        onSuccess(healthChecker.checkComponent(componentName)) {
          case Some(component) => complete(component)
          case None => complete(404, s"Component '$componentName' not found")
        }
      }
    }
  }

  /**
   * Quick health check (without detailed component checks)
   * GET /health/quick
   */
  val quickHealthRoute: Route = path("health" / "quick") {
    get {
      complete(healthChecker.quickStatus())
    }
  }

  /**
   * Liveness probe - for Kubernetes
   * GET /health/live
   */
  val livenessRoute: Route = path("health" / "live") {
    get {
      complete(Map("status" -> "alive"))
    }
  }

  /**
   * Readiness probe - for Kubernetes
   * GET /health/ready
   */
  val readinessRoute: Route = path("health" / "ready") {
    get {
      onSuccess(healthChecker.checkAll()) { response =>
        if (response.status == HealthStatus.Healthy) {
          complete(Map("status" -> "ready"))
        } else {
          complete(503, Map("status" -> "not_ready", "details" -> response))
        }
      }
    }
  }

  /**
   * Metrics endpoint (Prometheus format)
   * GET /metrics
   */
  val metricsRoute: Route = path("metrics") {
    get {
      MetricsRegistry.incrementCounter("http.requests.metrics")
      complete(MetricsRegistry.getPrometheusMetrics)
    }
  }

  /**
   * JSON metrics endpoint
   * GET /metrics/json
   */
  val jsonMetricsRoute: Route = path("metrics" / "json") {
    get {
      MetricsRegistry.incrementCounter("http.requests.metrics")
      complete(com.discordvisualroom.metrics.ApplicationMetrics.getMetricsSummary)
    }
  }

  /**
   * Combined routes
   */
  val routes: Route =
    healthRoute ~
    quickHealthRoute ~
    livenessRoute ~
    readinessRoute ~
    metricsRoute ~
    jsonMetricsRoute
}

/**
 * Factory for creating health routes
 */
object HealthRoutes {
  def apply(
    healthChecker: HealthChecker
  )(implicit system: ActorSystem[Nothing]): HealthRoutes = {
    new HealthRoutes(healthChecker)
  }
}
