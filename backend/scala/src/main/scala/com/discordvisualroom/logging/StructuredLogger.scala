package com.discordvisualroom.logging

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MDC

import scala.jdk.CollectionConverters._

/**
 * Structured logging with MDC (Mapped Diagnostic Context) support
 * Provides consistent log format with correlation IDs and contextual information
 */
trait StructuredLogging extends LazyLogging {

  /**
   * Add MDC context for the duration of a block
   */
  def withMDC[T](context: Map[String, String])(fn: => T): T = {
    context.foreach { case (key, value) =>
      MDC.put(key, value)
    }
    try {
      fn
    } finally {
      context.keys.foreach(key => MDC.remove(key))
    }
  }

  /**
   * Add a single MDC key-value pair
   */
  def withMDC[T](key: String, value: String)(fn: => T): T = {
    MDC.put(key, value)
    try {
      fn
    } finally {
      MDC.remove(key)
    }
  }

  /**
   * Log with context as JSON
   */
  def logWithContext(level: String, message: String, context: Map[String, Any]): Unit = {
    val contextStr = context.map { case (k, v) => s""""$k":${formatValue(v)}""" }.mkString("{", ",", "}")
    val fullMessage = s"$message | Context: $contextStr"

    level match {
      case "ERROR" => logger.error(fullMessage)
      case "WARN"  => logger.warn(fullMessage)
      case "INFO"  => logger.info(fullMessage)
      case "DEBUG" => logger.debug(fullMessage)
      case "TRACE" => logger.trace(fullMessage)
      case _       => logger.info(fullMessage)
    }
  }

  /**
   * Log error with context
   */
  def logError(message: String, throwable: Throwable, context: Map[String, Any] = Map.empty): Unit = {
    val contextStr = if (context.nonEmpty) {
      context.map { case (k, v) => s""""$k":${formatValue(v)}""" }.mkString(" | ", ", ", "")
    } else ""
    logger.error(s"$message$contextStr", throwable)
  }

  /**
   * Log operation with timing
   */
  def logOperation[T](
    operationName: String,
    context: Map[String, Any] = Map.empty
  )(fn: => T): T = {
    val startTime = System.currentTimeMillis()
    logger.debug(s"Starting operation: $operationName")

    try {
      val result = fn
      val duration = System.currentTimeMillis() - startTime
      val contextWithTiming = context + ("duration" -> duration) + ("status" -> "success")
      logWithContext("INFO", s"Operation completed: $operationName", contextWithTiming)
      result
    } catch {
      case ex: Throwable =>
        val duration = System.currentTimeMillis() - startTime
        val contextWithTiming = context + ("duration" -> duration) + ("status" -> "error")
        logError(s"Operation failed: $operationName", ex, contextWithTiming)
        throw ex
    }
  }

  /**
   * Format value for JSON logging
   */
  private def formatValue(value: Any): String = value match {
    case s: String => s""""$s""""
    case n: Number => n.toString
    case b: Boolean => b.toString
    case seq: Seq[_] => seq.map(formatValue).mkString("[", ",", "]")
    case map: Map[_, _] => map.map {
      case (k, v) => s""""$k":${formatValue(v)}"""
    }.mkString("{", ",", "}")
    case Some(v) => formatValue(v)
    case None => "null"
    case other => s""""$other""""
  }
}

/**
 * Companion object providing utility methods
 */
object StructuredLogging {
  /**
   * Generate a unique correlation ID
   */
  def generateCorrelationId(): String = {
    java.util.UUID.randomUUID().toString.replace("-", "")
  }

  /**
   * Generate a trace ID for distributed tracing
   */
  def generateTraceId(): String = {
    java.util.UUID.randomUUID().toString.replace("-", "")
  }

  /**
   * Extract user ID from context
   */
  def getUserId(context: Map[String, Any]): Option[String] = {
    context.get("userId").map(_.toString)
  }

  /**
   * Create standard log context
   */
  def createLogContext(
    requestId: Option[String] = None,
    userId: Option[String] = None,
    component: Option[String] = None,
    additional: Map[String, Any] = Map.empty
  ): Map[String, Any] = {
    var context = Map.empty[String, Any]

    requestId.foreach(id => context = context + ("requestId" -> id))
    userId.foreach(id => context = context + ("userId" -> id))
    component.foreach(name => context = context + ("component" -> name))

    context ++ additional
  }
}
