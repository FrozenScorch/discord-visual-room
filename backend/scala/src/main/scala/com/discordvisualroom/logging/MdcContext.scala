package com.discordvisualroom.logging

import org.slf4j.MDC

import scala.util.{Using, Try}

/**
 * MDC (Mapped Diagnostic Context) management for request tracking
 * Provides thread-safe context management for distributed tracing
 */
object MdcContext {

  private val CorrelationIdKey = "correlationId"
  private val TraceIdKey = "traceId"
  private val UserIdKey = "userId"
  private val ComponentKey = "component"
  private val RequestIdKey = "requestId"

  /**
   * Set correlation ID for current thread
   */
  def setCorrelationId(correlationId: String): Unit = {
    MDC.put(CorrelationIdKey, correlationId)
  }

  /**
   * Get correlation ID for current thread
   */
  def getCorrelationId: Option[String] = Option(MDC.get(CorrelationIdKey))

  /**
   * Set trace ID for distributed tracing
   */
  def setTraceId(traceId: String): Unit = {
    MDC.put(TraceIdKey, traceId)
  }

  /**
   * Get trace ID for current thread
   */
  def getTraceId: Option[String] = Option(MDC.get(TraceIdKey))

  /**
   * Set user ID
   */
  def setUserId(userId: String): Unit = {
    MDC.put(UserIdKey, userId)
  }

  /**
   * Get user ID
   */
  def getUserId: Option[String] = Option(MDC.get(UserIdKey))

  /**
   * Set component name
   */
  def setComponent(component: String): Unit = {
    MDC.put(ComponentKey, component)
  }

  /**
   * Get component name
   */
  def getComponent: Option[String] = Option(MDC.get(ComponentKey))

  /**
   * Set request ID
   */
  def setRequestId(requestId: String): Unit = {
    MDC.put(RequestIdKey, requestId)
  }

  /**
   * Get request ID
   */
  def getRequestId: Option[String] = Option(MDC.get(RequestIdKey))

  /**
   * Set multiple context values at once
   */
  def setContext(context: Map[String, String]): Unit = {
    context.foreach { case (key, value) =>
      MDC.put(key, value)
    }
  }

  /**
   * Get all context values
   */
  def getContext: Map[String, String] = {
    Option(MDC.getCopyOfContextMap).map(_.asScala.toMap).getOrElse(Map.empty)
  }

  /**
   * Clear all MDC context
   */
  def clear(): Unit = {
    MDC.clear()
  }

  /**
   * Remove specific keys
   */
  def remove(keys: String*): Unit = {
    keys.foreach(MDC.remove)
  }

  /**
   * Execute a block with MDC context
   */
  def withMdc[T](context: Map[String, String])(fn: => T): T = {
    val previousContext = getContext
    try {
      setContext(context)
      fn
    } finally {
      clear()
      setContext(previousContext)
    }
  }

  /**
   * Execute a block with correlation ID
   */
  def withCorrelationId[T](correlationId: String)(fn: => T): T = {
    val previous = getCorrelationId
    try {
      setCorrelationId(correlationId)
      fn
    } finally {
      previous.foreach(setCorrelationId).getOrElse(remove(CorrelationIdKey))
    }
  }

  /**
   * Execute a block with user ID
   */
  def withUserId[T](userId: String)(fn: => T): T = {
    val previous = getUserId
    try {
      setUserId(userId)
      fn
    } finally {
      previous.foreach(setUserId).getOrElse(remove(UserIdKey))
    }
  }

  /**
   * Execute a block with component
   */
  def withComponent[T](component: String)(fn: => T): T = {
    val previous = getComponent
    try {
      setComponent(component)
      fn
    } finally {
      previous.foreach(setComponent).getOrElse(remove(ComponentKey))
    }
  }

  /**
   * Create a new correlation ID if one doesn't exist
   */
  def ensureCorrelationId(): String = {
    getCorrelationId.getOrElse {
      val newId = StructuredLogging.generateCorrelationId()
      setCorrelationId(newId)
      newId
    }
  }
}
