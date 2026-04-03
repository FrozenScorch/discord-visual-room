package com.discordvisualroom.health

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsString, JsValue, RootJsonFormat, RootJsonWriter}
import com.discordvisualroom.metrics.MetricsRegistry
import com.discordvisualroom.metrics.HistogramStats

import java.time.Instant
import scala.concurrent.ExecutionContextExecutor

/**
 * JSON serialization for health check types
 */
trait HealthCheckJsonProtocol {
  implicit val instantFormat: RootJsonFormat[Instant] = new RootJsonFormat[Instant] {
    def write(instant: Instant) = JsString(instant.toString)
    def read(value: JsValue) = value match {
      case JsString(s) => Instant.parse(s)
      case _ => spray.json.deserializationError("Expected ISO instant string")
    }
  }

  implicit val healthStatusFormat: RootJsonFormat[HealthStatus] = new RootJsonFormat[HealthStatus] {
    def write(status: HealthStatus): JsValue = JsString(status.name)
    def read(json: JsValue): HealthStatus = json match {
      case JsString(s) => HealthStatus.fromString(s)
      case _ => spray.json.deserializationError("Expected HealthStatus string")
    }
  }

  implicit val componentHealthFormat: RootJsonFormat[ComponentHealth] = jsonFormat5(ComponentHealth)
  implicit val healthCheckResponseFormat: RootJsonFormat[HealthCheckResponse] = jsonFormat4(HealthCheckResponse)

  // Writer for Map[String, Any] used in metrics responses (handles nested maps)
  implicit val anyMapWriter: RootJsonWriter[Map[String, Any]] = new RootJsonWriter[Map[String, Any]] {
    def write(m: Map[String, Any]): spray.json.JsObject = {
      spray.json.JsObject(m.map { case (k, v) =>
        k -> writeAny(v)
      })
    }

    private def writeAny(value: Any): spray.json.JsValue = value match {
      case s: String => JsString(s)
      case n: Int => spray.json.JsNumber(n)
      case n: Long => spray.json.JsNumber(n)
      case n: Double => spray.json.JsNumber(n)
      case b: Boolean => spray.json.JsBoolean(b)
      case m: Map[_, _] => write(m.asInstanceOf[Map[String, Any]])
      case opt: Option[_] => opt match {
        case Some(inner) => writeAny(inner)
        case None => spray.json.JsNull
      }
      case hs: HistogramStats => spray.json.JsObject(
        "count" -> spray.json.JsNumber(hs.count),
        "sum" -> spray.json.JsNumber(hs.sum),
        "average" -> spray.json.JsNumber(hs.average),
        "min" -> spray.json.JsNumber(hs.min),
        "max" -> spray.json.JsNumber(hs.max),
        "p50" -> spray.json.JsNumber(hs.p50),
        "p95" -> spray.json.JsNumber(hs.p95),
        "p99" -> spray.json.JsNumber(hs.p99)
      )
      case other => JsString(other.toString)
    }
  }
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
          complete(503, Map[String, Any]("status" -> "not_ready", "details" -> response))
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
