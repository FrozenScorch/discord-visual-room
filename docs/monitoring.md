# Monitoring & Observability Guide

This document describes the monitoring, logging, and observability infrastructure for the Discord Visual Room project.

## Table of Contents

1. [Overview](#overview)
2. [Log Format Specification](#log-format-specification)
3. [Available Metrics](#available-metrics)
4. [Health Checks](#health-checks)
5. [Backend Monitoring](#backend-monitoring)
6. [Frontend Monitoring](#frontend-monitoring)
7. [Debugging Guide](#debugging-guide)
8. [Configuration](#configuration)

## Overview

The Discord Visual Room project includes comprehensive monitoring and observability features:

- **Structured Logging**: JSON-formatted logs with contextual information
- **Metrics Collection**: Counters, gauges, and histograms for performance tracking
- **Health Checks**: HTTP endpoints for monitoring system health
- **Error Tracking**: Centralized error handling and reporting

## Log Format Specification

### Log Entry Structure

All logs follow this structured JSON format:

```json
{
  "timestamp": "2026-02-02T00:00:00.000Z",
  "level": "INFO",
  "service": "backend",
  "component": "LLMClient",
  "message": "LLM request completed",
  "context": {
    "requestId": "abc-123",
    "userId": "user-456",
    "duration": 234
  }
}
```

### Log Levels

| Level | Description | Usage |
|-------|-------------|-------|
| `ERROR` | Error conditions | Failures that need attention |
| `WARN` | Warning conditions | Potential issues that don't stop execution |
| `INFO` | Informational | Normal operation messages |
| `DEBUG` | Debug information | Detailed diagnostics for development |
| `TRACE` | Trace information | Very detailed flow tracing |

### Log Context Fields

Common context fields included in logs:

- `requestId`: Unique identifier for the request (correlation ID)
- `userId`: Discord user ID
- `traceId`: Distributed tracing identifier
- `component`: Service or component name
- `duration`: Operation duration in milliseconds
- `status`: Operation status (success/failure)

## Available Metrics

### LLM Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `llm.requests.total` | Counter | Total LLM requests made |
| `llm.requests.success` | Counter | Successful LLM requests |
| `llm.requests.failure` | Counter | Failed LLM requests |
| `llm.fallbacks.total` | Counter | Fallback activations |
| `llm.response_time` | Histogram | LLM response time distribution |

### WebSocket Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `ws.connections.total` | Counter | Total WebSocket connections |
| `ws.connections.active` | Gauge | Currently active connections |
| `ws.disconnections.total` | Counter | Total disconnections |
| `ws.reconnections.total` | Counter | Reconnection attempts |
| `ws.messages.received` | Counter | Messages received from server |
| `ws.messages.sent` | Counter | Messages sent to server |
| `ws.messages.errors` | Counter | WebSocket errors |
| `ws.message_latency` | Histogram | Message round-trip latency |

### Room Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `room.users.active` | Gauge | Currently active users |
| `room.users.total` | Counter | Total users seen |
| `room.furniture.count` | Gauge | Current furniture count |
| `room.scene_updates.total` | Counter | Scene graph updates |
| `room.scene_update_time` | Histogram | Scene update duration |
| `room.layouts_generated.total` | Counter | Layout generations |
| `room.layout_generation_time` | Histogram | Layout generation duration |
| `room.layouts_fallback.total` | Counter | Layout fallback activations |

### Discord Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `discord.voice_state_updates.total` | Counter | Voice state updates |
| `discord.activity_updates.total` | Counter | Activity updates |
| `discord.speaking_updates.total` | Counter | Speaking state updates |
| `discord.errors.total` | Counter | Discord errors |

### Error Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `errors.total` | Counter | Total errors |
| `errors.{component}` | Counter | Errors by component |
| `errors.{component}.{errorType}` | Counter | Errors by type |

## Health Checks

### Health Endpoints

#### Overall Health Check

```
GET /health
```

Returns overall system health:

```json
{
  "status": "healthy",
  "components": {
    "discord": {
      "name": "discord",
      "status": "healthy",
      "details": {
        "status": "connected"
      },
      "lastCheck": "2026-02-02T00:00:00Z"
    },
    "llm": {
      "name": "llm",
      "status": "healthy",
      "details": {
        "responseTime": 150,
        "url": "http://localhost:1234"
      },
      "lastCheck": "2026-02-02T00:00:00Z"
    },
    "websocket": {
      "name": "websocket",
      "status": "healthy",
      "details": {
        "activeConnections": "5",
        "status": "listening"
      },
      "lastCheck": "2026-02-02T00:00:00Z"
    }
  },
  "timestamp": "2026-02-02T00:00:00Z",
  "version": "1.0.0"
}
```

#### Component Health Check

```
GET /health/{component}
```

Returns health of a specific component (e.g., `llm`, `discord`, `websocket`).

#### Quick Health Check

```
GET /health/quick
```

Returns quick health status without detailed component checks.

#### Liveness Probe (Kubernetes)

```
GET /health/live
```

Simple liveness check - always returns `{"status": "alive"}`.

#### Readiness Probe (Kubernetes)

```
GET /health/ready
```

Returns readiness status. Returns 503 if system is not ready.

### Health Status Values

| Status | Description |
|--------|-------------|
| `healthy` | Component is functioning normally |
| `degraded` | Component is functioning but with issues |
| `unhealthy` | Component is not functioning |

## Backend Monitoring

### Logging

The backend uses SLF4J with Logback for structured logging:

```scala
import com.discordvisualroom.logging.StructuredLogging

class MyComponent extends StructuredLogging {
  def doSomething(): Unit = {
    // Log with context
    logWithContext("INFO", "Operation completed", Map(
      "userId" -> "123",
      "duration" -> 234
    ))

    // Log error
    logError("Operation failed", exception, Map("context" -> "value"))

    // Log operation with timing
    logOperation("expensiveOperation", Map("input" -> "value")) {
      // Do work
    }
  }
}
```

### MDC Context

Use MDC for request tracking:

```scala
import com.discordvisualroom.logging.MdcContext

// Set correlation ID
MdcContext.setCorrelationId("request-123")

// Execute with context
MdcContext.withUserId("user-456") {
  // All logs in this block will have userId context
}

// Clear context
MdcContext.clear()
```

### Metrics Collection

Record application metrics:

```scala
import com.discordvisualroom.metrics.ApplicationMetrics

// LLM metrics
ApplicationMetrics.LLM.recordRequest()
ApplicationMetrics.LLM.recordSuccess(durationMs = 150)
ApplicationMetrics.LLM.recordFailure(durationMs = 5000, "timeout")
ApplicationMetrics.LLM.recordFallback()

// WebSocket metrics
ApplicationMetrics.WebSocket.recordConnection()
ApplicationMetrics.WebSocket.recordMessageReceived()
ApplicationMetrics.WebSocket.recordLatency(latencyMs = 50)

// Room metrics
ApplicationMetrics.Room.setUserCount(5)
ApplicationMetrics.Room.recordSceneUpdate(durationMs = 10)
```

### Health Checks

Register health checks:

```scala
import com.discordvisualroom.health._

val healthChecker = new HealthChecker()

// Register built-in health checks
healthChecker.register(new DiscordHealthCheck(() => discordClient.isConnected))
healthChecker.register(new LLMHealthCheck("http://localhost:1234"))
healthChecker.register(new WebSocketHealthCheck(() => wsConnectionCount))

// Check all components
val healthFuture = healthChecker.checkAll()

// Check specific component
val componentFuture = healthChecker.checkComponent("llm")
```

## Frontend Monitoring

### Logging

Use the structured logger:

```typescript
import { getLogger } from './logging';

const logger = getLogger();

// Basic logging
logger.info('User joined', { userId: '123', username: 'test' });
logger.warn('High latency detected', { latency: 500 });
logger.error('Connection failed', error, { endpoint: '/ws' });

// With correlation ID
logger.setCorrelationId('request-123');
logger.info('Processing request');

// Operation timing
await logger.logOperation('loadScene', async () => {
  return await loadSceneData();
}, { sceneId: 'main' });

// Child logger with component prefix
const wsLogger = logger.createChild('WebSocket');
wsLogger.debug('Message received', { type: 'SCENE_UPDATE' });
```

### Metrics Collection

Track metrics:

```typescript
import { getMetricsCollector } from './metrics';

const metrics = getMetricsCollector();

// Counters
metrics.incrementCounter('wsMessagesReceivedTotal');
metrics.incrementCounter('errorsTotal');

// Gauges
metrics.setGauge('activeUsers', 5);
metrics.setGauge('furnitureCount', 8);

// Histograms
metrics.recordHistogram('wsMessageLatency', 45);
metrics.recordHistogram('sceneUpdateTime', 15);

// Timers
metrics.startTimer('sceneLoad');
// ... do work ...
const duration = metrics.stopTimer('sceneLoad', 'sceneUpdateTime');

// Operation tracking
await metrics.recordOperation('llmRequest', 'llmResponseTime', async () => {
  return await fetchLLMResponse(prompt);
});

// WebSocket metrics
metrics.recordWebSocketConnection();
metrics.recordWebSocketMessageSent();
metrics.recordWebSocketMessageLatency(latencyMs);

// Get metrics summary
const summary = metrics.getMetricsSummary();
console.log('Metrics:', summary);
```

### Error Handling

Global error handler:

```typescript
import { handleError, onAlert } from './monitoring';

// Handle errors
try {
  await riskyOperation();
} catch (error) {
  handleError(error, {
    component: 'SceneRenderer',
    operation: 'loadMesh',
  });
}

// Subscribe to alerts
const unsubscribe = onAlert((alert) => {
  console.error('Alert:', alert);
  if (alert.severity === 'critical') {
    // Show notification to user
    showNotification(alert.title, alert.message);
  }
});

// Unsubscribe when done
unsubscribe();
```

## Debugging Guide

### Enabling Debug Logs

#### Backend

Set log level in `application.conf`:

```hocon
akka {
  loglevel = "DEBUG"
}
```

Or via environment variable:

```bash
LOG_LEVEL=DEBUG sbt run
```

#### Frontend

Configure logger:

```typescript
logger.configure({
  minLevel: LogLevel.DEBUG,
  enableConsole: true,
  enableStorage: true,
});
```

### Viewing Logs

#### Backend Logs

Logs are written to:
- `logs/discord-visual-room-all.log` - All logs
- `logs/discord-visual-room-error.log` - Error logs only

View logs:
```bash
tail -f logs/discord-visual-room-all.log
```

Filter by level:
```bash
grep "ERROR" logs/discord-visual-room-all.log
```

#### Frontend Logs

Logs are stored in `localStorage`:

```javascript
// Get stored logs
const logger = getLogger();
const logs = logger.getStoredLogs();
console.table(logs);

// Export logs
const exported = logger.exportLogs();
console.log(exported);

// Clear logs
logger.clearStoredLogs();
```

### Monitoring Metrics

#### Backend Metrics

Access metrics in Prometheus format:
```bash
curl http://localhost:8000/metrics
```

Access metrics as JSON:
```bash
curl http://localhost:8000/metrics/json
```

#### Frontend Metrics

Access metrics:
```javascript
const metrics = getMetricsCollector();
const summary = metrics.getMetricsSummary();
console.log(summary);
```

Export metrics:
```javascript
const exported = metrics.exportMetrics();
console.log(exported);
```

### Health Check Status

Check system health:
```bash
curl http://localhost:8000/health
```

Check specific component:
```bash
curl http://localhost:8000/health/llm
```

### Common Issues

#### High LLM Response Time

1. Check LLM metrics:
   ```bash
   curl http://localhost:8000/metrics | grep llm_response_time
   ```

2. Check LLM service health:
   ```bash
   curl http://localhost:8000/health/llm
   ```

3. Review error logs for LLM failures:
   ```bash
   grep "llm" logs/discord-visual-room-error.log
   ```

#### WebSocket Connection Issues

1. Check WebSocket metrics:
   ```javascript
   const metrics = getMetricsCollector();
   console.log('Active connections:', metrics.getMetrics().wsActiveConnections);
   console.log('Reconnections:', metrics.getMetrics().wsReconnectionsTotal);
   ```

2. Check backend WebSocket health:
   ```bash
   curl http://localhost:8000/health/websocket
   ```

3. Review frontend logs:
   ```javascript
   const logs = getLogger().getStoredLogs();
   const wsLogs = logs.filter(l => l.component === 'WebSocket');
   console.table(wsLogs);
   ```

#### Memory Issues

1. Check memory health:
   ```bash
   curl http://localhost:8000/health/memory
   ```

2. Monitor JVM memory in logs

3. Review metrics for memory trends

## Configuration

### Backend Configuration

**logback.xml** options:

```xml
<!-- Log format: json or text -->
<property name="LOG_FORMAT" value="json"/>

<!-- Log directory -->
<property name="LOG_DIR" value="logs"/>

<!-- Log rotation -->
<maxFileSize>100MB</maxFileSize>
<maxHistory>30</maxHistory>
```

### Frontend Configuration

**Logger config:**

```typescript
const logger = getLogger();
logger.configure({
  minLevel: import.meta.env.PROD ? LogLevel.INFO : LogLevel.DEBUG,
  enableConsole: true,
  enableStorage: true,
  maxStorageEntries: 1000,
  service: 'frontend',
  production: import.meta.env.PROD,
});
```

**Metrics config:**

```typescript
const metrics = new MetricsCollector({
  enabled: true,
  maxHistorySize: 1000,
  sampleRate: 1.0,
  persistMetrics: true,
  flushInterval: 60000,
});
```

**Error handler config:**

```typescript
const errorHandler = new ErrorHandler({
  enabled: true,
  sendToBackend: false,
  showUserMessages: true,
  maxStoredErrors: 100,
});
```

## Best Practices

1. **Use appropriate log levels**: ERROR for failures, WARN for issues, INFO for normal operations
2. **Include correlation IDs**: Trace requests across service boundaries
3. **Add context**: Include relevant data (userId, requestId, duration)
4. **Monitor metrics**: Regularly review metrics for anomalies
5. **Set up alerts**: Configure alerts for critical failures
6. **Review logs periodically**: Check error logs for patterns
7. **Test health checks**: Ensure health endpoints work correctly
8. **Use structured logging**: Maintain consistent log format
9. **Sample high-volume metrics**: Use sampling for frequently recorded metrics
10. **Rotate logs**: Prevent disk space issues with log rotation
