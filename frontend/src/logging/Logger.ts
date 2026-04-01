/**
 * Structured logger for frontend
 * Provides consistent logging with levels and context
 */

import { LogLevel } from '@discord-visual-room/monitoring';
import type { LogEntry, LogContext, ErrorContext } from '@discord-visual-room/monitoring';

/**
 * Logger configuration
 */
export interface LoggerConfig {
  /** Minimum log level */
  minLevel: LogLevel;
  /** Enable console output */
  enableConsole: boolean;
  /** Enable local storage persistence */
  enableStorage: boolean;
  /** Maximum log entries in storage */
  maxStorageEntries: number;
  /** Service name */
  service: string;
  /** Enable in production */
  production: boolean;
}

/**
 * Default logger configuration
 */
const DEFAULT_CONFIG: LoggerConfig = {
  minLevel: LogLevel.INFO,
  enableConsole: true,
  enableStorage: true,
  maxStorageEntries: 1000,
  service: 'frontend',
  production: false,
};

/**
 * Structured logger implementation
 */
export class Logger {
  private config: LoggerConfig;
  private currentCorrelationId: string | null = null;

  constructor(config: Partial<LoggerConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Update logger configuration
   */
  public configure(config: Partial<LoggerConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Set correlation ID for request tracking
   */
  public setCorrelationId(correlationId: string): void {
    this.currentCorrelationId = correlationId;
  }

  /**
   * Clear correlation ID
   */
  public clearCorrelationId(): void {
    this.currentCorrelationId = null;
  }

  /**
   * Generate a correlation ID
   */
  public generateCorrelationId(): string {
    return `${Date.now()}-${Math.random().toString(36).substring(2, 15)}`;
  }

  /**
   * Log at TRACE level
   */
  public trace(message: string, context?: LogContext): void {
    this.log(LogLevel.TRACE, message, context);
  }

  /**
   * Log at DEBUG level
   */
  public debug(message: string, context?: LogContext): void {
    this.log(LogLevel.DEBUG, message, context);
  }

  /**
   * Log at INFO level
   */
  public info(message: string, context?: LogContext): void {
    this.log(LogLevel.INFO, message, context);
  }

  /**
   * Log at WARN level
   */
  public warn(message: string, context?: LogContext): void {
    this.log(LogLevel.WARN, message, context);
  }

  /**
   * Log at ERROR level
   */
  public error(message: string, error?: Error | ErrorContext, context?: LogContext): void {
    const errorContext: ErrorContext | undefined =
      error instanceof Error
        ? {
            type: error.name,
            message: error.message,
            stack: error.stack,
          }
        : error;

    this.log(LogLevel.ERROR, message, context, errorContext);
  }

  /**
   * Core logging method
   */
  private log(
    level: LogLevel,
    message: string,
    context?: LogContext,
    error?: ErrorContext
  ): void {
    // Check log level
    if (!this.shouldLog(level)) {
      return;
    }

    // Create log entry
    const entry: LogEntry = {
      timestamp: new Date().toISOString(),
      level,
      service: this.config.service,
      component: (context?.component as string) || 'frontend',
      message,
      context: {
        ...context,
        correlationId: this.currentCorrelationId || context?.correlationId,
      },
      error,
    };

    // Console output
    if (this.config.enableConsole) {
      this.logToConsole(entry);
    }

    // Storage persistence
    if (this.config.enableStorage) {
      this.logToStorage(entry);
    }
  }

  /**
   * Check if message should be logged based on level
   */
  private shouldLog(level: LogLevel): boolean {
    const levels = [LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR];
    const currentLevelIndex = levels.indexOf(this.config.minLevel);
    const messageLevelIndex = levels.indexOf(level);
    return messageLevelIndex >= currentLevelIndex;
  }

  /**
   * Log to console with appropriate formatting
   */
  private logToConsole(entry: LogEntry): void {
    const levelStyles = {
      [LogLevel.TRACE]: 'color: gray',
      [LogLevel.DEBUG]: 'color: blue',
      [LogLevel.INFO]: 'color: green',
      [LogLevel.WARN]: 'color: orange',
      [LogLevel.ERROR]: 'color: red; font-weight: bold',
    };

    const style = levelStyles[entry.level];

    // In production, use simple console methods
    if (this.config.production) {
      const consoleMethod = this.getConsoleMethod(entry.level);
      consoleMethod(`[${entry.level}] ${entry.message}`, entry.context || '', entry.error || '');
      return;
    }

    // Development: styled console output
    const contextStr = entry.context ? ` ${JSON.stringify(entry.context)}` : '';
    const errorStr = entry.error ? ` | Error: ${entry.error.message}` : '';

    console.log(
      `%c[${entry.timestamp}] [${entry.level}] [${entry.component}]%c ${entry.message}${contextStr}${errorStr}`,
      style,
      ''
    );

    // Log stack trace separately for errors
    if (entry.error?.stack) {
      console.error(entry.error.stack);
    }
  }

  /**
   * Get appropriate console method for log level
   */
  private getConsoleMethod(level: LogLevel): (...args: unknown[]) => void {
    switch (level) {
      case LogLevel.TRACE:
      case LogLevel.DEBUG:
        return console.debug.bind(console);
      case LogLevel.INFO:
        return console.info.bind(console);
      case LogLevel.WARN:
        return console.warn.bind(console);
      case LogLevel.ERROR:
        return console.error.bind(console);
      default:
        return console.log.bind(console);
    }
  }

  /**
   * Persist log to localStorage
   */
  private logToStorage(entry: LogEntry): void {
    try {
      const storageKey = 'logs';
      const logs = this.getStoredLogs();

      // Add new entry
      logs.push(entry);

      // Trim to max entries
      if (logs.length > this.config.maxStorageEntries) {
        logs.splice(0, logs.length - this.config.maxStorageEntries);
      }

      localStorage.setItem(storageKey, JSON.stringify(logs));
    } catch (e) {
      // Silently fail if storage is unavailable
      console.warn('Failed to persist log to localStorage', e);
    }
  }

  /**
   * Get stored logs from localStorage
   */
  public getStoredLogs(): LogEntry[] {
    try {
      const storageKey = 'logs';
      const logsJson = localStorage.getItem(storageKey);
      return logsJson ? JSON.parse(logsJson) : [];
    } catch (e) {
      console.warn('Failed to retrieve logs from localStorage', e);
      return [];
    }
  }

  /**
   * Clear stored logs
   */
  public clearStoredLogs(): void {
    try {
      localStorage.removeItem('logs');
    } catch (e) {
      console.warn('Failed to clear logs from localStorage', e);
    }
  }

  /**
   * Export logs as JSON
   */
  public exportLogs(): string {
    const logs = this.getStoredLogs();
    return JSON.stringify(logs, null, 2);
  }

  /**
   * Log operation with timing
   */
  public async logOperation<T>(
    operationName: string,
    fn: () => Promise<T> | T,
    context?: LogContext
  ): Promise<T> {
    const startTime = Date.now();
    this.debug(`Starting operation: ${operationName}`, context);

    try {
      const result = await fn();
      const duration = Date.now() - startTime;
      this.info(`Operation completed: ${operationName}`, { ...context, duration });
      return result;
    } catch (error) {
      const duration = Date.now() - startTime;
      this.error(
        `Operation failed: ${operationName}`,
        error instanceof Error ? error : { type: 'Error', message: String(error) },
        { ...context, duration }
      );
      throw error;
    }
  }

  /**
   * Create a child logger with a component prefix
   */
  public createChild(component: string): ChildLogger {
    return new ChildLogger(this, component);
  }
}

/**
 * Child logger with predefined component
 */
export class ChildLogger {
  constructor(private parent: Logger, private component: string) {}

  public trace(message: string, context?: LogContext): void {
    this.parent.trace(message, { ...context, component: this.component });
  }

  public debug(message: string, context?: LogContext): void {
    this.parent.debug(message, { ...context, component: this.component });
  }

  public info(message: string, context?: LogContext): void {
    this.parent.info(message, { ...context, component: this.component });
  }

  public warn(message: string, context?: LogContext): void {
    this.parent.warn(message, { ...context, component: this.component });
  }

  public error(message: string, error?: Error | ErrorContext, context?: LogContext): void {
    this.parent.error(message, error, { ...context, component: this.component });
  }

  public async logOperation<T>(
    operationName: string,
    fn: () => Promise<T> | T,
    context?: LogContext
  ): Promise<T> {
    return this.parent.logOperation(operationName, fn, { ...context, component: this.component });
  }
}

/**
 * Global logger instance
 */
let globalLogger: Logger | null = null;

/**
 * Get or create global logger instance
 */
export function getLogger(): Logger {
  if (!globalLogger) {
    const isProduction = import.meta.env.PROD || import.meta.env.MODE === 'production';
    globalLogger = new Logger({
      production: isProduction,
      minLevel: isProduction ? LogLevel.INFO : LogLevel.DEBUG,
    });
  }
  return globalLogger;
}

/**
 * Set global logger instance
 */
export function setLogger(logger: Logger): void {
  globalLogger = logger;
}
