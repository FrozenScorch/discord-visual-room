/**
 * Metrics collector for frontend
 * Tracks performance, errors, and usage metrics
 */

import type { Metrics } from '@discord-visual-room/monitoring';

/**
 * Metrics collector configuration
 */
export interface MetricsCollectorConfig {
  /** Enable metrics collection */
  enabled: boolean;
  /** Maximum history for histogram metrics */
  maxHistorySize: number;
  /** Sampling rate for metrics (0-1) */
  sampleRate: number;
  /** Persist metrics to localStorage */
  persistMetrics: boolean;
  /** Flush interval in milliseconds */
  flushInterval: number;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: MetricsCollectorConfig = {
  enabled: true,
  maxHistorySize: 1000,
  sampleRate: 1.0,
  persistMetrics: true,
  flushInterval: 60000, // 1 minute
};

/**
 * Metrics collector implementation
 */
export class MetricsCollector {
  private config: MetricsCollectorConfig;
  private metrics: Metrics;
  private timers: Map<string, number>;
  private flushTimer: number | null;

  constructor(config: Partial<MetricsCollectorConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.metrics = this.createEmptyMetrics();
    this.timers = new Map();
    this.flushTimer = null;

    if (this.config.persistMetrics) {
      this.loadMetrics();
    }

    // Start periodic flush
    this.startFlushTimer();
  }

  /**
   * Create empty metrics object
   */
  private createEmptyMetrics(): Metrics {
    return {
      llmRequestsTotal: 0,
      llmFallbacksTotal: 0,
      wsConnectionsTotal: 0,
      wsDisconnectionsTotal: 0,
      wsMessagesReceivedTotal: 0,
      wsMessagesSentTotal: 0,
      errorsTotal: 0,
      activeUsers: 0,
      furnitureCount: 0,
      wsActiveConnections: 0,
      llmPendingRequests: 0,
      llmResponseTime: [],
      wsMessageLatency: [],
      sceneUpdateTime: [],
    };
  }

  /**
   * Increment a counter metric
   */
  public incrementCounter(metricName: keyof Metrics): void {
    if (!this.shouldSample()) return;

    const value = this.metrics[metricName] as number;
    (this.metrics[metricName] as number) = value + 1;
  }

  /**
   * Set a gauge metric value
   */
  public setGauge(metricName: keyof Metrics, value: number): void {
    if (!this.shouldSample()) return;

    (this.metrics[metricName] as number) = value;
  }

  /**
   * Record a histogram value
   */
  public recordHistogram(metricName: keyof Metrics, value: number): void {
    if (!this.shouldSample()) return;

    const histogram = this.metrics[metricName] as number[];
    histogram.push(value);

    // Trim to max size
    if (histogram.length > this.config.maxHistorySize) {
      histogram.splice(0, histogram.length - this.config.maxHistorySize);
    }
  }

  /**
   * Start a timer for a metric
   */
  public startTimer(metricName: string): void {
    this.timers.set(metricName, performance.now());
  }

  /**
   * Stop a timer and record the duration
   */
  public stopTimer(metricName: string, histogramName?: keyof Metrics): number {
    const startTime = this.timers.get(metricName);
    if (startTime === undefined) {
      console.warn(`Timer "${metricName}" was not started`);
      return 0;
    }

    const duration = performance.now() - startTime;
    this.timers.delete(metricName);

    if (histogramName) {
      this.recordHistogram(histogramName, duration);
    }

    return duration;
  }

  /**
   * Record an operation with timing
   */
  public async recordOperation<T>(
    _operationName: string,
    histogramName: keyof Metrics,
    fn: () => Promise<T> | T
  ): Promise<T> {
    const startTime = performance.now();

    try {
      const result = await fn();
      const duration = performance.now() - startTime;
      this.recordHistogram(histogramName, duration);
      return result;
    } catch (error) {
      const duration = performance.now() - startTime;
      this.recordHistogram(histogramName, duration);
      this.incrementCounter('errorsTotal');
      throw error;
    }
  }

  /**
   * WebSocket metrics
   */
  public recordWebSocketConnection(): void {
    this.incrementCounter('wsConnectionsTotal');
    this.incrementCounter('wsActiveConnections');
  }

  public recordWebSocketDisconnection(): void {
    this.incrementCounter('wsDisconnectionsTotal');
    this.setGauge('wsActiveConnections', Math.max(0, this.metrics.wsActiveConnections - 1));
  }

  public recordWebSocketMessageReceived(): void {
    this.incrementCounter('wsMessagesReceivedTotal');
  }

  public recordWebSocketMessageSent(): void {
    this.incrementCounter('wsMessagesSentTotal');
  }

  public recordWebSocketMessageLatency(latencyMs: number): void {
    this.recordHistogram('wsMessageLatency', latencyMs);
  }

  /**
   * Room metrics
   */
  public updateRoomMetrics(userCount: number, furnitureCount: number): void {
    this.setGauge('activeUsers', userCount);
    this.setGauge('furnitureCount', furnitureCount);
  }

  public recordSceneUpdate(durationMs: number): void {
    this.incrementCounter('sceneUpdatesTotal' as any);
    this.recordHistogram('sceneUpdateTime', durationMs);
  }

  /**
   * Error metrics
   */
  public recordError(error: Error, context?: Record<string, unknown>): void {
    this.incrementCounter('errorsTotal');

    // Log error context if provided
    if (context) {
      console.error('Error recorded:', { error, context });
    }
  }

  /**
   * Get all metrics
   */
  public getMetrics(): Metrics {
    return { ...this.metrics };
  }

  /**
   * Get metrics summary with calculated statistics
   */
  public getMetricsSummary(): Record<string, unknown> {
    return {
      counters: {
        llmRequestsTotal: this.metrics.llmRequestsTotal,
        llmFallbacksTotal: this.metrics.llmFallbacksTotal,
        wsConnectionsTotal: this.metrics.wsConnectionsTotal,
        wsDisconnectionsTotal: this.metrics.wsDisconnectionsTotal,
        wsMessagesReceivedTotal: this.metrics.wsMessagesReceivedTotal,
        wsMessagesSentTotal: this.metrics.wsMessagesSentTotal,
        errorsTotal: this.metrics.errorsTotal,
      },
      gauges: {
        activeUsers: this.metrics.activeUsers,
        furnitureCount: this.metrics.furnitureCount,
        wsActiveConnections: this.metrics.wsActiveConnections,
        llmPendingRequests: this.metrics.llmPendingRequests,
      },
      histograms: {
        llmResponseTime: this.calculateHistogramStats(this.metrics.llmResponseTime),
        wsMessageLatency: this.calculateHistogramStats(this.metrics.wsMessageLatency),
        sceneUpdateTime: this.calculateHistogramStats(this.metrics.sceneUpdateTime),
      },
    };
  }

  /**
   * Calculate histogram statistics
   */
  private calculateHistogramStats(values: number[]): Record<string, number> {
    if (values.length === 0) {
      return { count: 0, average: 0, min: 0, max: 0, p50: 0, p95: 0, p99: 0 };
    }

    const sorted = [...values].sort((a, b) => a - b);
    const count = sorted.length;
    const sum = sorted.reduce((a, b) => a + b, 0);

    return {
      count,
      average: sum / count,
      min: sorted[0],
      max: sorted[count - 1],
      p50: sorted[Math.floor(count * 0.5)],
      p95: sorted[Math.floor(count * 0.95)],
      p99: sorted[Math.floor(count * 0.99)],
    };
  }

  /**
   * Reset all metrics
   */
  public resetMetrics(): void {
    this.metrics = this.createEmptyMetrics();
    this.timers.clear();

    if (this.config.persistMetrics) {
      this.saveMetrics();
    }
  }

  /**
   * Save metrics to localStorage
   */
  private saveMetrics(): void {
    try {
      localStorage.setItem('metrics', JSON.stringify(this.metrics));
    } catch (e) {
      console.warn('Failed to save metrics to localStorage', e);
    }
  }

  /**
   * Load metrics from localStorage
   */
  private loadMetrics(): void {
    try {
      const metricsJson = localStorage.getItem('metrics');
      if (metricsJson) {
        const loadedMetrics = JSON.parse(metricsJson);
        this.metrics = { ...this.createEmptyMetrics(), ...loadedMetrics };
      }
    } catch (e) {
      console.warn('Failed to load metrics from localStorage', e);
    }
  }

  /**
   * Start periodic flush timer
   */
  private startFlushTimer(): void {
    if (this.flushTimer !== null) {
      return;
    }

    this.flushTimer = window.setInterval(() => {
      if (this.config.persistMetrics) {
        this.saveMetrics();
      }
    }, this.config.flushInterval);
  }

  /**
   * Stop flush timer
   */
  public stopFlushTimer(): void {
    if (this.flushTimer !== null) {
      clearInterval(this.flushTimer);
      this.flushTimer = null;
    }
  }

  /**
   * Check if metric should be sampled
   */
  private shouldSample(): boolean {
    return Math.random() < this.config.sampleRate;
  }

  /**
   * Export metrics as JSON
   */
  public exportMetrics(): string {
    return JSON.stringify(this.getMetricsSummary(), null, 2);
  }

  /**
   * Destroy the metrics collector
   */
  public destroy(): void {
    this.stopFlushTimer();
    if (this.config.persistMetrics) {
      this.saveMetrics();
    }
  }
}

/**
 * Global metrics collector instance
 */
let globalMetricsCollector: MetricsCollector | null = null;

/**
 * Get or create global metrics collector
 */
export function getMetricsCollector(): MetricsCollector {
  if (!globalMetricsCollector) {
    globalMetricsCollector = new MetricsCollector();
  }
  return globalMetricsCollector;
}

/**
 * Set global metrics collector
 */
export function setMetricsCollector(collector: MetricsCollector): void {
  globalMetricsCollector = collector;
}
