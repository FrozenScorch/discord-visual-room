/**
 * Shared monitoring and observability types
 * Used across backend and frontend for consistent logging and metrics
 */

/**
 * Log levels for structured logging
 */
export enum LogLevel {
  ERROR = 'ERROR',
  WARN = 'WARN',
  INFO = 'INFO',
  DEBUG = 'DEBUG',
  TRACE = 'TRACE',
}

/**
 * Structured log entry format
 */
export interface LogEntry {
  /** ISO 8601 timestamp */
  timestamp: string;
  /** Log level */
  level: LogLevel;
  /** Service name (backend, frontend) */
  service: string;
  /** Component generating the log */
  component: string;
  /** Log message */
  message: string;
  /** Additional context */
  context?: LogContext;
  /** Error details if applicable */
  error?: ErrorContext;
}

/**
 * Log context for structured data
 */
export interface LogContext {
  /** Request correlation ID */
  requestId?: string;
  /** User ID */
  userId?: string;
  /** Duration in milliseconds */
  duration?: number;
  /** Additional key-value pairs */
  [key: string]: unknown;
}

/**
 * Error context for structured error reporting
 */
export interface ErrorContext {
  /** Error type/name */
  type: string;
  /** Error message */
  message: string;
  /** Stack trace */
  stack?: string;
  /** Component where error occurred */
  component?: string;
  /** Additional error context */
  details?: Record<string, unknown>;
}

/**
 * Metric types
 */
export enum MetricType {
  COUNTER = 'counter',
  GAUGE = 'gauge',
  HISTOGRAM = 'histogram',
}

/**
 * Metric definition
 */
export interface Metric {
  /** Metric name */
  name: string;
  /** Metric type */
  type: MetricType;
  /** Metric value */
  value: number;
  /** Metric labels/tags */
  labels?: Record<string, string>;
  /** Timestamp */
  timestamp: number;
}

/**
 * Metrics collection
 */
export interface Metrics {
  // Counters
  llmRequestsTotal: number;
  llmFallbacksTotal: number;
  wsConnectionsTotal: number;
  wsDisconnectionsTotal: number;
  wsMessagesReceivedTotal: number;
  wsMessagesSentTotal: number;
  errorsTotal: number;

  // Gauges
  activeUsers: number;
  furnitureCount: number;
  wsActiveConnections: number;
  llmPendingRequests: number;

  // Histograms (arrays of values)
  llmResponseTime: number[];
  wsMessageLatency: number[];
  sceneUpdateTime: number[];
}

/**
 * Health check status
 */
export enum HealthStatus {
  HEALTHY = 'healthy',
  DEGRADED = 'degraded',
  UNHEALTHY = 'unhealthy',
}

/**
 * Component health status
 */
export interface ComponentHealth {
  /** Component name */
  name: string;
  /** Health status */
  status: HealthStatus;
  /** Additional details */
  details?: Record<string, unknown>;
  /** Last check timestamp */
  lastCheck: number;
  /** Error message if unhealthy */
  error?: string;
}

/**
 * Overall health check response
 */
export interface HealthCheck {
  /** Overall system status */
  status: HealthStatus;
  /** Individual component statuses */
  components: Record<string, ComponentHealth>;
  /** ISO 8601 timestamp */
  timestamp: string;
  /** Service version */
  version: string;
}

/**
 * LLM-specific metrics
 */
export interface LLMMetrics {
  requestsTotal: number;
  failuresTotal: number;
  fallbacksTotal: number;
  averageResponseTime: number;
  lastRequestTime?: number;
  lastFailureTime?: number;
  successRate: number;
}

/**
 * WebSocket-specific metrics
 */
export interface WebSocketMetrics {
  activeConnections: number;
  totalConnections: number;
  messagesReceived: number;
  messagesSent: number;
  averageLatency: number;
  reconnectsTotal: number;
  errorsTotal: number;
}

/**
 * Room-specific metrics
 */
export interface RoomMetrics {
  activeUsers: number;
  totalUsers: number;
  furnitureCount: number;
  sceneUpdates: number;
  lastUpdateTime?: number;
}

/**
 * Alert severity levels
 */
export enum AlertSeverity {
  INFO = 'info',
  WARNING = 'warning',
  ERROR = 'error',
  CRITICAL = 'critical',
}

/**
 * Alert notification
 */
export interface Alert {
  /** Alert ID */
  id: string;
  /** Severity level */
  severity: AlertSeverity;
  /** Alert title */
  title: string;
  /** Alert message */
  message: string;
  /** Component that triggered the alert */
  component: string;
  /** Timestamp */
  timestamp: number;
  /** Resolved status */
  resolved: boolean;
  /** Additional context */
  context?: Record<string, unknown>;
}

/**
 * Performance tracking data
 */
export interface PerformanceData {
  /** Operation name */
  operation: string;
  /** Start time */
  startTime: number;
  /** End time */
  endTime: number;
  /** Duration in milliseconds */
  duration: number;
  /** Success status */
  success: boolean;
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Request tracking for distributed tracing
 */
export interface RequestTrace {
  /** Unique request ID */
  requestId: string;
  /** Parent request ID (if part of a chain) */
  parentRequestId?: string;
  /** Trace ID for distributed tracing */
  traceId?: string;
  /** Span ID for this operation */
  spanId: string;
  /** Operation name */
  operation: string;
  /** Start time */
  startTime: number;
  /** End time */
  endTime?: number;
  /** Child spans */
  children?: RequestTrace[];
  /** Tags/labels */
  tags?: Record<string, string>;
}

/**
 * Monitoring configuration
 */
export interface MonitoringConfig {
  /** Enable logging */
  loggingEnabled: boolean;
  /** Minimum log level */
  logLevel: LogLevel;
  /** Enable metrics collection */
  metricsEnabled: boolean;
  /** Enable health checks */
  healthCheckEnabled: boolean;
  /** Enable performance tracking */
  performanceTrackingEnabled: boolean;
  /** Enable error tracking */
  errorTrackingEnabled: boolean;
  /** Sampling rate for traces (0-1) */
  traceSampleRate: number;
  /** Maximum entries in memory */
  maxLogEntries: number;
  maxMetricHistory: number;
}
