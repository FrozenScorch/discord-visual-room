/**
 * Global error handler for frontend
 * Captures unhandled errors, promise rejections, and provides error reporting
 */

import type { ErrorContext, Alert, AlertSeverity } from '@discord-visual-room/monitoring';
import { getLogger } from '../logging';
import { getMetricsCollector } from '../metrics';

/**
 * Error handler configuration
 */
export interface ErrorHandlerConfig {
  /** Enable error capturing */
  enabled: boolean;
  /** Send errors to backend */
  sendToBackend: boolean;
  /** Backend endpoint for error reporting */
  backendEndpoint?: string;
  /** Show user-friendly error messages */
  showUserMessages: boolean;
  /** Maximum errors to store */
  maxStoredErrors: number;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: ErrorHandlerConfig = {
  enabled: true,
  sendToBackend: false,
  showUserMessages: true,
  maxStoredErrors: 100,
};

/**
 * Error severity mapping
 */
const ERROR_SEVERITY_MAP: Record<string, AlertSeverity> = {
  Error: 'error',
  TypeError: 'error',
  ReferenceError: 'error',
  SyntaxError: 'error',
  RangeError: 'error',
  URIError: 'warning',
  EvalError: 'error',
  Warning: 'warning',
};

/**
 * Global error handler
 */
export class ErrorHandler {
  private config: ErrorHandlerConfig;
  private logger = getLogger();
  private metrics = getMetricsCollector();
  private errorHistory: ErrorContext[] = [];
  private alertCallbacks: ((alert: Alert) => void)[] = [];

  constructor(config: Partial<ErrorHandlerConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };

    if (this.config.enabled) {
      this.setupGlobalHandlers();
    }
  }

  /**
   * Setup global error handlers
   */
  private setupGlobalHandlers(): void {
    // Handle uncaught errors
    window.addEventListener('error', (event) => {
      this.handleError(event.error || new Error(event.message), {
        component: 'global',
        message: event.message,
        filename: event.filename,
        line: event.lineno,
        column: event.colno,
      });
    });

    // Handle unhandled promise rejections
    window.addEventListener('unhandledrejection', (event) => {
      this.handleError(
        event.reason instanceof Error ? event.reason : new Error(String(event.reason)),
        {
          component: 'promise',
          type: 'UnhandledPromiseRejection',
        }
      );
    });

    // Handle resource loading errors
    window.addEventListener('error', (event) => {
      if (event.target !== window) {
        const target = event.target as HTMLElement;
        this.handleError(
          new Error(`Failed to load resource: ${target.tagName}`),
          {
            component: 'resource',
            tagName: target.tagName,
            src: (target as any).src || (target as any).href,
          }
        );
      }
    }, true);
  }

  /**
   * Handle an error
   */
  public handleError(error: Error | unknown, context?: Record<string, unknown>): void {
    const errorContext: ErrorContext = this.createErrorContext(error, context);

    // Log the error
    this.logger.error(errorContext.message, errorContext, context as any);

    // Add to metrics
    this.metrics.recordError(error instanceof Error ? error : new Error(String(error)), context);

    // Store in history
    this.addToHistory(errorContext);

    // Create alert
    const alert = this.createAlert(errorContext);
    this.notifyAlertCallbacks(alert);

    // Send to backend if configured
    if (this.config.sendToBackend && this.config.backendEndpoint) {
      this.sendErrorToBackend(errorContext).catch((err) => {
        this.logger.error('Failed to send error to backend', err);
      });
    }

    // Show user message if configured
    if (this.config.showUserMessages) {
      this.showUserMessage(errorContext);
    }
  }

  /**
   * Create error context from error
   */
  private createErrorContext(error: unknown, context?: Record<string, unknown>): ErrorContext {
    if (error instanceof Error) {
      return {
        type: error.name,
        message: error.message,
        stack: error.stack,
        component: (context?.component as string) || 'unknown',
        details: context,
      };
    }

    return {
      type: 'UnknownError',
      message: String(error),
      component: (context?.component as string) || 'unknown',
      details: context,
    };
  }

  /**
   * Add error to history
   */
  private addToHistory(errorContext: ErrorContext): void {
    this.errorHistory.push(errorContext);

    // Trim to max size
    if (this.errorHistory.length > this.config.maxStoredErrors) {
      this.errorHistory.splice(0, this.errorHistory.length - this.config.maxStoredErrors);
    }

    // Persist to localStorage
    try {
      localStorage.setItem('errorHistory', JSON.stringify(this.errorHistory));
    } catch (e) {
      // Ignore storage errors
    }
  }

  /**
   * Get error history
   */
  public getErrorHistory(): ErrorContext[] {
    return [...this.errorHistory];
  }

  /**
   * Clear error history
   */
  public clearErrorHistory(): void {
    this.errorHistory = [];
    try {
      localStorage.removeItem('errorHistory');
    } catch (e) {
      // Ignore storage errors
    }
  }

  /**
   * Create alert from error context
   */
  private createAlert(errorContext: ErrorContext): Alert {
    const severity = ERROR_SEVERITY_MAP[errorContext.type] || 'error';

    return {
      id: this.generateAlertId(),
      severity,
      title: `${errorContext.type}: ${errorContext.message}`,
      message: this.getUserFriendlyMessage(errorContext),
      component: errorContext.component || 'unknown',
      timestamp: Date.now(),
      resolved: false,
      context: errorContext.details,
    };
  }

  /**
   * Generate alert ID
   */
  private generateAlertId(): string {
    return `alert-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  }

  /**
   * Get user-friendly error message
   */
  private getUserFriendlyMessage(errorContext: ErrorContext): string {
    // Provide friendly messages for common errors
    const friendlyMessages: Record<string, string> = {
      NetworkError: 'Unable to connect to the server. Please check your internet connection.',
      WebSocketError: 'Connection to the server was lost. Trying to reconnect...',
      TypeError: 'An unexpected error occurred. Please refresh the page.',
    };

    return friendlyMessages[errorContext.type] || errorContext.message;
  }

  /**
   * Show user message for error
   */
  private showUserMessage(errorContext: ErrorContext): void {
    // Default implementation - can be overridden
    const message = this.getUserFriendlyMessage(errorContext);

    // Could show a toast notification here
    if (typeof console !== 'undefined' && console.warn) {
      console.warn(`User notification: ${message}`);
    }
  }

  /**
   * Send error to backend
   */
  private async sendErrorToBackend(errorContext: ErrorContext): Promise<void> {
    if (!this.config.backendEndpoint) {
      return;
    }

    try {
      const response = await fetch(this.config.backendEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          error: errorContext,
          userAgent: navigator.userAgent,
          url: window.location.href,
          timestamp: new Date().toISOString(),
        }),
      });

      if (!response.ok) {
        throw new Error(`Backend responded with status ${response.status}`);
      }
    } catch (err) {
      // Don't recurse - just log
      console.error('Failed to send error to backend:', err);
    }
  }

  /**
   * Register alert callback
   */
  public onAlert(callback: (alert: Alert) => void): () => void {
    this.alertCallbacks.push(callback);

    // Return unsubscribe function
    return () => {
      const index = this.alertCallbacks.indexOf(callback);
      if (index !== -1) {
        this.alertCallbacks.splice(index, 1);
      }
    };
  }

  /**
   * Notify all alert callbacks
   */
  private notifyAlertCallbacks(alert: Alert): void {
    this.alertCallbacks.forEach((callback) => {
      try {
        callback(alert);
      } catch (err) {
        console.error('Alert callback error:', err);
      }
    });
  }

  /**
   * Update configuration
   */
  public configure(config: Partial<ErrorHandlerConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Destroy error handler
   */
  public destroy(): void {
    this.alertCallbacks = [];
    // Note: We don't remove global event listeners as other code might need them
  }
}

/**
 * Global error handler instance
 */
let globalErrorHandler: ErrorHandler | null = null;

/**
 * Get or create global error handler
 */
export function getErrorHandler(): ErrorHandler {
  if (!globalErrorHandler) {
    globalErrorHandler = new ErrorHandler();
  }
  return globalErrorHandler;
}

/**
 * Set global error handler
 */
export function setErrorHandler(handler: ErrorHandler): void {
  globalErrorHandler = handler;
}

/**
 * Convenience function to handle an error
 */
export function handleError(error: Error | unknown, context?: Record<string, unknown>): void {
  getErrorHandler().handleError(error, context);
}

/**
 * Convenience function to register alert callback
 */
export function onAlert(callback: (alert: Alert) => void): () => void {
  return getErrorHandler().onAlert(callback);
}
