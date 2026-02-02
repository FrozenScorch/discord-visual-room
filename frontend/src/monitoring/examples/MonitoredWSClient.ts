/**
 * Example: WebSocket Client with integrated monitoring
 * Shows how to add logging and metrics to existing components
 */

import { WSClient } from '../WSClient';
import { getLogger } from '../logging';
import { getMetricsCollector } from '../metrics';
import { handleError } from '../monitoring';
import type { ConnectionState, SceneUpdateMessage } from '../types';

const logger = getLogger();
const metrics = getMetricsCollector();
const wsLogger = logger.createChild('WebSocket');

/**
 * Monitored WebSocket Client
 * Wraps WSClient with monitoring and error handling
 */
export class MonitoredWSClient extends WSClient {
  constructor(wsUrl: string) {
    super(wsUrl);
    this.setupMonitoring();
  }

  /**
   * Setup monitoring callbacks
   */
  private setupMonitoring(): void {
    // Monitor connection state changes
    this.onConnect(() => this.handleConnect());
    this.onDisconnect(() => this.handleDisconnect());
    this.onError((error) => this.handleError(error));
    this.onSceneUpdate((message) => this.handleSceneUpdate(message));
  }

  /**
   * Handle connection established
   */
  private handleConnect(): void {
    wsLogger.info('WebSocket connected', {
      url: this['wsUrl'],
    });

    // Record metrics
    metrics.recordWebSocketConnection();

    // Clear any previous errors
    metrics.setGauge('wsActiveConnections', metrics.getMetrics().wsActiveConnections + 1);
  }

  /**
   * Handle connection closed
   */
  private handleDisconnect(): void {
    wsLogger.warn('WebSocket disconnected', {
      state: this.getState(),
    });

    // Record metrics
    metrics.recordWebSocketDisconnection();
  }

  /**
   * Handle error
   */
  private handleError(error: Error): void {
    wsLogger.error('WebSocket error', error, {
      state: this.getState(),
      url: this['wsUrl'],
    });

    // Record error
    handleError(error, {
      component: 'WebSocket',
      operation: 'connection',
    });

    // Record error metric
    metrics.incrementCounter('wsMessagesErrorsTotal' as any);
  }

  /**
   * Handle scene update message
   */
  private handleSceneUpdate(message: SceneUpdateMessage): void {
    const startTime = performance.now();

    try {
      wsLogger.debug('Scene update received', {
        userCount: message.sceneGraph.users.length,
        furnitureCount: message.sceneGraph.furniture.length,
      });

      // Record metrics
      metrics.recordWebSocketMessageReceived();
      metrics.recordHistogram('wsMessageLatency' as any, startTime - message.timestamp);

      // Update room metrics
      metrics.updateRoomMetrics(
        message.sceneGraph.users.length,
        message.sceneGraph.furniture.length
      );

      // Record scene update timing
      const processingTime = performance.now() - startTime;
      metrics.recordSceneUpdate(processingTime);

      wsLogger.debug('Scene update processed', {
        duration: processingTime,
      });
    } catch (error) {
      wsLogger.error('Failed to process scene update', error as Error, {
        message,
      });

      handleError(error, {
        component: 'WebSocket',
        operation: 'processSceneUpdate',
      });
    }
  }

  /**
   * Connect with monitoring
   */
  public connect(): void {
    wsLogger.info('Connecting to WebSocket', {
      url: this['wsUrl'],
    });

    try {
      super.connect();
    } catch (error) {
      wsLogger.error('Failed to connect', error as Error);
      handleError(error, {
        component: 'WebSocket',
        operation: 'connect',
      });
      throw error;
    }
  }

  /**
   * Disconnect with monitoring
   */
  public disconnect(): void {
    wsLogger.info('Disconnecting WebSocket');

    try {
      super.disconnect();

      // Flush metrics before disconnecting
      metrics.recordHistogram('wsSessionDuration' as any, 0); // TODO: Track actual session duration
    } catch (error) {
      wsLogger.error('Failed to disconnect', error as Error);
      handleError(error, {
        component: 'WebSocket',
        operation: 'disconnect',
      });
    }
  }

  /**
   * Send message with monitoring
   */
  public send(type: string, payload: unknown): void {
    wsLogger.debug('Sending message', { type });

    try {
      metrics.startTimer('wsMessageSend');
      super.send(type, payload);
      metrics.recordWebSocketMessageSent();

      const duration = metrics.stopTimer('wsMessageSend');
      wsLogger.debug('Message sent', { type, duration });
    } catch (error) {
      wsLogger.error('Failed to send message', error as Error, { type });
      handleError(error, {
        component: 'WebSocket',
        operation: 'sendMessage',
        data: { type },
      });
    }
  }
}

/**
 * Usage example
 */
export function createMonitoredWebSocket(wsUrl: string): MonitoredWSClient {
  const client = new MonitoredWSClient(wsUrl);

  // Setup alert notifications
  const { onAlert } = require('../monitoring');

  onAlert((alert) => {
    if (alert.component === 'WebSocket') {
      console.error('WebSocket Alert:', alert);

      // Show user notification for critical errors
      if (alert.severity === 'critical') {
        // Show notification to user
        showNotification(alert.title, alert.message);
      }
    }
  });

  return client;
}

/**
 * Helper to show notifications (implement based on UI framework)
 */
function showNotification(title: string, message: string): void {
  // TODO: Implement based on UI framework
  console.log('Notification:', title, message);
}
