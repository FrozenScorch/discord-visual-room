package com.discordvisualroom.metrics

import com.discordvisualroom.logging.StructuredLogging
import com.typesafe.scalalogging.LazyLogging

/**
 * Application-specific metrics collection
 * Provides high-level metrics for LLM, WebSocket, and Room operations
 */
object ApplicationMetrics extends LazyLogging {

  /**
   * LLM Metrics
   */
  object LLM {
    def recordRequest(): Unit = {
      MetricsRegistry.incrementCounter("llm.requests.total")
    }

    def recordSuccess(durationMs: Long): Unit = {
      MetricsRegistry.incrementCounter("llm.requests.success")
      MetricsRegistry.recordTiming("llm.response_time", durationMs)
    }

    def recordFailure(durationMs: Long, error: String): Unit = {
      MetricsRegistry.incrementCounter("llm.requests.failure")
      MetricsRegistry.recordTiming("llm.response_time", durationMs)
      logger.error(s"LLM request failed: $error")
    }

    def recordFallback(): Unit = {
      MetricsRegistry.incrementCounter("llm.fallbacks.total")
      logger.warn("LLM fallback activated")
    }

    def getRequestMetrics: Map[String, Long] = Map(
      "total" -> MetricsRegistry.getCounter("llm.requests.total"),
      "success" -> MetricsRegistry.getCounter("llm.requests.success"),
      "failure" -> MetricsRegistry.getCounter("llm.requests.failure"),
      "fallbacks" -> MetricsRegistry.getCounter("llm.fallbacks.total")
    )

    def getResponseTimeStats: Option[HistogramStats] = {
      MetricsRegistry.getHistogramStats("llm.response_time")
    }
  }

  /**
   * WebSocket Metrics
   */
  object WebSocket {
    def recordConnection(): Unit = {
      MetricsRegistry.incrementCounter("ws.connections.total")
      MetricsRegistry.incrementGauge("ws.connections.active")
      logger.info("WebSocket connection established")
    }

    def recordDisconnection(): Unit = {
      MetricsRegistry.incrementCounter("ws.disconnections.total")
      MetricsRegistry.decrementGauge("ws.connections.active")
      logger.info("WebSocket connection closed")
    }

    def recordReconnection(): Unit = {
      MetricsRegistry.incrementCounter("ws.reconnections.total")
      logger.warn("WebSocket reconnection occurred")
    }

    def recordMessageReceived(): Unit = {
      MetricsRegistry.incrementCounter("ws.messages.received")
    }

    def recordMessageSent(): Unit = {
      MetricsRegistry.incrementCounter("ws.messages.sent")
    }

    def recordMessageError(): Unit = {
      MetricsRegistry.incrementCounter("ws.messages.errors")
    }

    def recordLatency(latencyMs: Long): Unit = {
      MetricsRegistry.recordTiming("ws.message_latency", latencyMs)
    }

    def getConnectionMetrics: Map[String, Long] = Map(
      "active" -> MetricsRegistry.getGauge("ws.connections.active"),
      "total" -> MetricsRegistry.getCounter("ws.connections.total"),
      "disconnections" -> MetricsRegistry.getCounter("ws.disconnections.total"),
      "reconnections" -> MetricsRegistry.getCounter("ws.reconnections.total")
    )

    def getMessageMetrics: Map[String, Long] = Map(
      "received" -> MetricsRegistry.getCounter("ws.messages.received"),
      "sent" -> MetricsRegistry.getCounter("ws.messages.sent"),
      "errors" -> MetricsRegistry.getCounter("ws.messages.errors")
    )

    def getActiveConnections: Long = {
      MetricsRegistry.getGauge("ws.connections.active")
    }
  }

  /**
   * Room Metrics
   */
  object Room {
    def setUserCount(count: Int): Unit = {
      MetricsRegistry.setGauge("room.users.active", count)
    }

    def incrementUserCount(): Unit = {
      MetricsRegistry.incrementGauge("room.users.active")
      MetricsRegistry.incrementCounter("room.users.total")
    }

    def decrementUserCount(): Unit = {
      MetricsRegistry.decrementGauge("room.users.active")
    }

    def setFurnitureCount(count: Int): Unit = {
      MetricsRegistry.setGauge("room.furniture.count", count)
    }

    def recordSceneUpdate(durationMs: Long): Unit = {
      MetricsRegistry.incrementCounter("room.scene_updates.total")
      MetricsRegistry.recordTiming("room.scene_update_time", durationMs)
    }

    def recordLayoutGenerated(durationMs: Long): Unit = {
      MetricsRegistry.incrementCounter("room.layouts_generated.total")
      MetricsRegistry.recordTiming("room.layout_generation_time", durationMs)
    }

    def recordLayoutFallback(): Unit = {
      MetricsRegistry.incrementCounter("room.layouts_fallback.total")
    }

    def getUserMetrics: Map[String, Long] = Map(
      "active" -> MetricsRegistry.getGauge("room.users.active"),
      "total" -> MetricsRegistry.getCounter("room.users.total")
    )

    def getFurnitureCount: Long = {
      MetricsRegistry.getGauge("room.furniture.count")
    }
  }

  /**
   * Discord Metrics
   */
  object Discord {
    def recordVoiceStateUpdate(): Unit = {
      MetricsRegistry.incrementCounter("discord.voice_state_updates.total")
    }

    def recordActivityUpdate(): Unit = {
      MetricsRegistry.incrementCounter("discord.activity_updates.total")
    }

    def recordSpeakingUpdate(): Unit = {
      MetricsRegistry.incrementCounter("discord.speaking_updates.total")
    }

    def recordError(error: String): Unit = {
      MetricsRegistry.incrementCounter("discord.errors.total")
      logger.error(s"Discord error: $error")
    }

    def getVoiceStateUpdates: Long = {
      MetricsRegistry.getCounter("discord.voice_state_updates.total")
    }
  }

  /**
   * Error Metrics
   */
  object Errors {
    def recordError(component: String, errorType: String): Unit = {
      MetricsRegistry.incrementCounter(s"errors.$component.$errorType")
      MetricsRegistry.incrementCounter("errors.total")
    }

    def recordError(component: String): Unit = {
      recordError(component, "unknown")
    }

    def getTotalErrors: Long = {
      MetricsRegistry.getCounter("errors.total")
    }

    def getComponentErrors(component: String): Long = {
      MetricsRegistry.getCounter(s"errors.$component") +
      MetricsRegistry.getCounter(s"errors.$component.unknown")
    }
  }

  /**
   * Performance Metrics
   */
  object Performance {
    def recordOperation(operation: String, durationMs: Long): Unit = {
      MetricsRegistry.recordTiming(s"performance.$operation.duration", durationMs)
    }

    def recordSlowOperation(operation: String, durationMs: Long, thresholdMs: Long = 1000): Unit = {
      recordOperation(operation, durationMs)
      if (durationMs > thresholdMs) {
        MetricsRegistry.incrementCounter(s"performance.$operation.slow")
        logger.warn(s"Slow operation detected: $operation took ${durationMs}ms")
      }
    }

    def getOperationStats(operation: String): Option[HistogramStats] = {
      MetricsRegistry.getHistogramStats(s"performance.$operation.duration")
    }
  }

  /**
   * Get all application metrics summary
   */
  def getMetricsSummary: Map[String, Any] = Map(
    "llm" -> Map(
      "requests" -> LLM.getRequestMetrics,
      "responseTime" -> LLM.getResponseTimeStats.map(stats => Map(
        "average" -> stats.average,
        "min" -> stats.min,
        "max" -> stats.max,
        "p95" -> stats.p95,
        "p99" -> stats.p99
      ))
    ),
    "websocket" -> Map(
      "connections" -> WebSocket.getConnectionMetrics,
      "messages" -> WebSocket.getMessageMetrics
    ),
    "room" -> Map(
      "users" -> Room.getUserMetrics,
      "furniture" -> Room.getFurnitureCount
    ),
    "errors" -> Map(
      "total" -> Errors.getTotalErrors
    )
  )
}
