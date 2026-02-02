# Monitoring & Observability - Quick Start

This guide will help you quickly set up and use the monitoring infrastructure in the Discord Visual Room project.

## Backend Setup

### 1. Basic Logging

Add logging to your components:

```scala
import com.discordvisualroom.logging.StructuredLogging

class MyComponent extends StructuredLogging {
  def doWork(): Unit = {
    // Simple log
    logger.info("Starting work")

    // Log with context
    logWithContext("INFO", "Work completed", Map(
      "duration" -> 100,
      "userId" -> "123"
    ))

    // Log error
    logError("Work failed", exception, Map("context" -> "value"))
  }
}
```

### 2. Request Tracking

Use MDC for request correlation:

```scala
import com.discordvisualroom.logging.MdcContext

// Set correlation ID for request
val correlationId = MdcContext.ensureCorrelationId()

// Execute with context
MdcContext.withUserId("user-123") {
  // All logs here will have userId in context
  processRequest()
}

// Or use withMdc for multiple values
MdcContext.withMdc(Map("requestId" -> "abc", "sessionId" -> "xyz")) {
  // All logs here will have both context values
  processRequest()
}
```

### 3. Recording Metrics

Track important operations:

```scala
import com.discordvisualroom.metrics.ApplicationMetrics

// LLM operations
ApplicationMetrics.LLM.recordRequest()
val result = llmClient.generateLayout()
ApplicationMetrics.LLM.recordSuccess(duration = 150)

// WebSocket events
ApplicationMetrics.WebSocket.recordConnection()
ApplicationMetrics.WebSocket.recordMessageReceived()

// Room state
ApplicationMetrics.Room.setUserCount(5)
ApplicationMetrics.Room.recordSceneUpdate(duration = 10)
```

### 4. Health Checks

Register health checks for your components:

```scala
import com.discordvisualroom.health._

val healthChecker = new HealthChecker()

// Register component health checks
healthChecker.register(new CustomHealthCheck(
  name = "my-component",
  checkFn = () => Future {
    // Check component health
    if (isHealthy) {
      HealthCheckResult(isHealthy = true, status = HealthStatus.Healthy)
    } else {
      HealthCheckResult(isHealthy = false, status = HealthStatus.Unhealthy)
    }
  }
))

// Expose health routes in your HTTP server
val healthRoutes = HealthRoutes(healthChecker)
```

## Frontend Setup

### 1. Initialize Monitoring

Set up monitoring at app startup:

```typescript
import { getLogger } from './logging';
import { getMetricsCollector } from './metrics';
import { getErrorHandler } from './monitoring';

// Initialize logger
const logger = getLogger();
logger.configure({
  minLevel: import.meta.env.PROD ? LogLevel.INFO : LogLevel.DEBUG,
  enableConsole: true,
  enableStorage: true,
});

// Initialize metrics
const metrics = getMetricsCollector();

// Initialize error handler
const errorHandler = getErrorHandler();
```

### 2. Add Logging

```typescript
// Create a child logger for your component
const componentLogger = logger.createChild('MyComponent');

// Log events
componentLogger.info('Component initialized', { config });
componentLogger.debug('Processing data', { dataId: '123' });
componentLogger.warn('High latency detected', { latency: 500 });

// Log errors
try {
  await riskyOperation();
} catch (error) {
  componentLogger.error('Operation failed', error, { context });
}
```

### 3. Track Metrics

```typescript
// Track operations
metrics.startTimer('myOperation');
await doWork();
const duration = metrics.stopTimer('myOperation', 'operationDuration');

// Record events
metrics.incrementCounter('requestsTotal');
metrics.setGauge('activeConnections', 5);
metrics.recordHistogram('responseTime', 150);

// Get metrics summary
const summary = metrics.getMetricsSummary();
console.log('Metrics:', summary);
```

### 4. Handle Errors

```typescript
import { handleError, onAlert } from './monitoring';

// Handle errors
try {
  await operation();
} catch (error) {
  handleError(error, {
    component: 'MyComponent',
    operation: 'processData',
  });
}

// Subscribe to alerts
const unsubscribe = onAlert((alert) => {
  if (alert.severity === 'critical') {
    // Show notification to user
    showErrorNotification(alert.message);
  }
});

// Cleanup
unsubscribe();
```

## Common Patterns

### Timing Operations

**Backend:**
```scala
val startTime = System.currentTimeMillis()
val result = performOperation()
val duration = System.currentTimeMillis() - startTime
ApplicationMetrics.LLM.recordSuccess(duration)
```

**Frontend:**
```typescript
const result = await metrics.recordOperation(
  'loadScene',
  'sceneLoadTime',
  async () => await loadScene()
);
```

### Error Handling

**Backend:**
```scala
try {
  riskyOperation()
} catch {
  case ex: Exception =>
    logError("Operation failed", ex)
    ApplicationMetrics.Errors.recordError("MyComponent", ex.getClass.getSimpleName)
    throw ex
}
```

**Frontend:**
```typescript
try {
  await riskyOperation();
} catch (error) {
  handleError(error, { component: 'MyComponent' });
  throw error;
}
```

### State Changes

**Backend:**
```scala
// On state change
users = users + (userId -> user)
ApplicationMetrics.Room.setUserCount(users.size)
logger.info(s"User joined, count: ${users.size}")
```

**Frontend:**
```typescript
// On state update
metrics.updateRoomMetrics(
  sceneGraph.users.length,
  sceneGraph.furniture.length
);
```

## Viewing Logs and Metrics

### Backend

```bash
# View logs
tail -f logs/discord-visual-room-all.log

# View errors
tail -f logs/discord-visual-room-error.log

# Check health
curl http://localhost:8000/health

# View metrics
curl http://localhost:8000/metrics
```

### Frontend

```javascript
// View stored logs
const logger = getLogger();
console.log(logger.getStoredLogs());

// View metrics
const metrics = getMetricsCollector();
console.log(metrics.getMetricsSummary());

// Export data
console.log(logger.exportLogs());
console.log(metrics.exportMetrics());
```

## Environment Variables

**Backend:**
- `LOG_LEVEL` - Minimum log level (DEBUG, INFO, WARN, ERROR)
- `LOG_FORMAT` - Log format (json or text)
- `LOG_DIR` - Directory for log files (default: logs)

**Frontend:**
- `VITE_LOG_LEVEL` - Minimum log level
- `VITE_LOG_TO_CONSOLE` - Enable console logging (true/false)
- `VITE_ENABLE_METRICS` - Enable metrics collection (true/false)

## Troubleshooting

### No logs appearing

- Check log level configuration
- Verify log directory exists and is writable
- Check console for logging errors

### Metrics not recording

- Verify metrics collector is initialized
- Check if sampling rate is too low
- Ensure metrics are enabled in config

### Health check failing

- Check component-specific logs
- Verify health check is registered
- Test component independently

For more detailed information, see [monitoring.md](./monitoring.md).
