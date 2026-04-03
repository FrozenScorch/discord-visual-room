package com.discordvisualroom.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._

/**
 * Thread-safe metrics registry for collecting application metrics
 * Supports counters, gauges, and histograms
 */
object MetricsRegistry {

  // Counter metrics (increment-only)
  private val counters = TrieMap.empty[String, AtomicLong]

  // Gauge metrics (can go up and down)
  private val gauges = TrieMap.empty[String, AtomicLong]

  // Histogram metrics (distribution of values)
  private val histograms = TrieMap.empty[String, Histogram]

  /**
   * Increment a counter metric
   */
  def incrementCounter(name: String, value: Long = 1): Unit = {
    counters.getOrElseUpdate(name, new AtomicLong(0)).addAndGet(value)
  }

  /**
   * Get counter value
   */
  def getCounter(name: String): Long = {
    counters.get(name).map(_.get()).getOrElse(0L)
  }

  /**
   * Reset a counter to zero
   */
  def resetCounter(name: String): Unit = {
    counters.get(name).foreach(_.set(0))
  }

  /**
   * Set a gauge metric
   */
  def setGauge(name: String, value: Long): Unit = {
    gauges.getOrElseUpdate(name, new AtomicLong(0)).set(value)
  }

  /**
   * Increment a gauge metric
   */
  def incrementGauge(name: String, value: Long = 1): Unit = {
    gauges.getOrElseUpdate(name, new AtomicLong(0)).addAndGet(value)
  }

  /**
   * Decrement a gauge metric
   */
  def decrementGauge(name: String, value: Long = 1): Unit = {
    gauges.getOrElseUpdate(name, new AtomicLong(0)).addAndGet(-value)
  }

  /**
   * Get gauge value
   */
  def getGauge(name: String): Long = {
    gauges.get(name).map(_.get()).getOrElse(0L)
  }

  /**
   * Record a value in a histogram
   */
  def recordHistogram(name: String, value: Double): Unit = {
    histograms.getOrElseUpdate(name, new Histogram()).record(value)
  }

  /**
   * Record a timing (duration in milliseconds)
   */
  def recordTiming(name: String, durationMs: Long): Unit = {
    recordHistogram(name, durationMs.toDouble)
  }

  /**
   * Get histogram statistics
   */
  def getHistogramStats(name: String): Option[HistogramStats] = {
    histograms.get(name).map(_.getStats())
  }

  /**
   * Reset a histogram
   */
  def resetHistogram(name: String): Unit = {
    histograms.remove(name)
  }

  /**
   * Get all metrics as a map
   */
  def getAllMetrics: Map[String, MetricValue] = {
    val allMetrics = scala.collection.mutable.Map.empty[String, MetricValue]

    counters.foreach { case (name, value) =>
      allMetrics.put(name, MetricValue("counter", value.get()))
    }

    gauges.foreach { case (name, value) =>
      allMetrics.put(name, MetricValue("gauge", value.get()))
    }

    histograms.foreach { case (name, hist) =>
      val stats = hist.getStats()
      allMetrics.put(name, MetricValue("histogram", stats.count, Some(stats)))
    }

    allMetrics.toMap
  }

  /**
   * Reset all metrics
   */
  def resetAll(): Unit = {
    counters.clear()
    gauges.clear()
    histograms.clear()
  }

  /**
   * Get metrics in Prometheus format
   */
  def getPrometheusMetrics: String = {
    val sb = new StringBuilder()

    // Counters
    counters.foreach { case (name, value) =>
      sb.append(s"""# TYPE ${sanitizeName(name)} counter\n""")
      sb.append(s"""${sanitizeName(name)} ${value.get()}\n\n""")
    }

    // Gauges
    gauges.foreach { case (name, value) =>
      sb.append(s"""# TYPE ${sanitizeName(name)} gauge\n""")
      sb.append(s"""${sanitizeName(name)} ${value.get()}\n\n""")
    }

    // Histograms
    histograms.foreach { case (name, hist) =>
      val stats = hist.getStats()
      val sanitizedName = sanitizeName(name)
      sb.append(s"""# TYPE ${sanitizedName}_count counter\n""")
      sb.append(s"""${sanitizedName}_count ${stats.count}\n""")
      sb.append(s"""# TYPE ${sanitizedName}_sum gauge\n""")
      sb.append(s"""${sanitizedName}_sum ${stats.sum}\n""")
      sb.append(s"""# TYPE ${sanitizedName}_avg gauge\n""")
      sb.append(s"""${sanitizedName}_avg ${stats.average}\n""")
      sb.append(s"""# TYPE ${sanitizedName}_min gauge\n""")
      sb.append(s"""${sanitizedName}_min ${stats.min}\n""")
      sb.append(s"""# TYPE ${sanitizedName}_max gauge\n""")
      sb.append(s"""${sanitizedName}_max ${stats.max}\n""")
      sb.append(s"""# TYPE ${sanitizedName}_p50 gauge\n""")
      sb.append(s"""${sanitizedName}_p50 ${stats.p50}\n""")
      sb.append(s"""# TYPE ${sanitizedName}_p95 gauge\n""")
      sb.append(s"""${sanitizedName}_p95 ${stats.p95}\n""")
      sb.append(s"""# TYPE ${sanitizedName}_p99 gauge\n""")
      sb.append(s"""${sanitizedName}_p99 ${stats.p99}\n\n""")
    }

    sb.toString()
  }

  /**
   * Sanitize metric name for Prometheus
   */
  private def sanitizeName(name: String): String = {
    name.toLowerCase.replace("-", "_").replace(" ", "_").replace("/", "_")
  }
}

/**
 * Histogram implementation for tracking value distributions
 */
class Histogram(private val maxSize: Int = 1000) {
  private val values = java.util.Collections.synchronizedList(new java.util.ArrayList[Double]())
  private val count = new AtomicLong(0)
  private val sum = new AtomicLong(0)

  def record(value: Double): Unit = synchronized {
    values.add(value)
    count.incrementAndGet()
    sum.addAndGet(value.toLong)

    // Keep only the most recent values
    if (values.size() > maxSize) {
      values.remove(0)
    }
  }

  def getStats(): HistogramStats = synchronized {
    if (values.isEmpty) {
      HistogramStats(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    } else {
      val sorted = values.toArray().map(_.asInstanceOf[Double]).sorted
      val size = sorted.length

      HistogramStats(
        count = count.get(),
        sum = sum.get(),
        average = if (size > 0) sorted.sum / size else 0.0,
        min = sorted.head,
        max = sorted.last,
        p50 = percentile(sorted, 50),
        p95 = percentile(sorted, 95),
        p99 = percentile(sorted, 99)
      )
    }
  }

  private def percentile(sorted: Array[Double], p: Int): Double = {
    val index = (p / 100.0 * (sorted.length - 1)).toInt
    sorted(index)
  }
}

/**
 * Histogram statistics
 */
case class HistogramStats(
  count: Long,
  sum: Long,
  average: Double,
  min: Double,
  max: Double,
  p50: Double,
  p95: Double,
  p99: Double
)

/**
 * Metric value wrapper
 */
case class MetricValue(
  metricType: String,
  value: Long,
  histogramStats: Option[HistogramStats] = None
)
