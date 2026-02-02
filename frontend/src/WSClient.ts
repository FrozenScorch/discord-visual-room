import type { SceneUpdateMessage, ConnectionState, WSMessage } from './types';

/**
 * WebSocket client for receiving SceneGraph updates from backend
 *
 * Architecture: DUMB RENDERER
 * - Maintains NO state
 * - Receives complete scene state from backend
 * - Passes all data to SceneRenderer
 */
export class WSClient {
  private ws: WebSocket | null = null;
  private wsUrl: string;
  private reconnectAttempts: number = 0;
  private maxReconnectAttempts: number = 10;
  private reconnectDelay: number = 1000;
  private reconnectTimeout: number | null = null;
  private state: ConnectionState = 'disconnected';

  // Callbacks
  private onConnectCallback?: () => void;
  private onDisconnectCallback?: () => void;
  private onErrorCallback?: (error: Error) => void;
  private onSceneUpdateCallback?: (message: SceneUpdateMessage) => void;

  constructor(wsUrl: string) {
    this.wsUrl = wsUrl;
  }

  /**
   * Connect to WebSocket server
   */
  public connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.CONNECTING || this.ws.readyState === WebSocket.OPEN)) {
      console.log('WebSocket already connected or connecting');
      return;
    }

    this.setState('connecting');
    console.log(`Connecting to WebSocket: ${this.wsUrl}`);

    try {
      this.ws = new WebSocket(this.wsUrl);

      this.ws.onopen = this.handleOpen.bind(this);
      this.ws.onmessage = this.handleMessage.bind(this);
      this.ws.onerror = this.handleError.bind(this);
      this.ws.onclose = this.handleClose.bind(this);
    } catch (error) {
      console.error('Failed to create WebSocket:', error);
      this.handleConnectionError(new Error('Failed to create WebSocket connection'));
    }
  }

  /**
   * Disconnect from WebSocket server
   */
  public disconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.ws) {
      this.reconnectAttempts = this.maxReconnectAttempts; // Prevent auto-reconnect
      this.ws.close();
      this.ws = null;
    }
  }

  /**
   * Set callback for connection established
   */
  public onConnect(callback: () => void): void {
    this.onConnectCallback = callback;
  }

  /**
   * Set callback for disconnection
   */
  public onDisconnect(callback: () => void): void {
    this.onDisconnectCallback = callback;
  }

  /**
   * Set callback for errors
   */
  public onError(callback: (error: Error) => void): void {
    this.onErrorCallback = callback;
  }

  /**
   * Set callback for scene updates
   */
  public onSceneUpdate(callback: (message: SceneUpdateMessage) => void): void {
    this.onSceneUpdateCallback = callback;
  }

  /**
   * Get current connection state
   */
  public getState(): ConnectionState {
    return this.state;
  }

  /**
   * Handle WebSocket open event
   */
  private handleOpen(): void {
    console.log('WebSocket connected');
    this.setState('connected');
    this.reconnectAttempts = 0;

    if (this.onConnectCallback) {
      this.onConnectCallback();
    }
  }

  /**
   * Handle incoming WebSocket message
   */
  private handleMessage(event: MessageEvent): void {
    try {
      const message: WSMessage = JSON.parse(event.data);

      // Handle different message types
      switch (message.type) {
        case 'SCENE_UPDATE':
          this.handleSceneUpdate(message as SceneUpdateMessage);
          break;

        case 'USER_JOINED':
        case 'USER_LEFT':
        case 'USER_MOVED':
        case 'ACTIVITY_CHANGED':
          // These are handled via SCENE_UPDATE in dumb renderer architecture
          console.log(`Received ${message.type} (will be handled by SCENE_UPDATE)`);
          break;

        case 'ERROR':
          console.error('Server error:', message.payload);
          if (this.onErrorCallback) {
            this.onErrorCallback(new Error(String(message.payload)));
          }
          break;

        default:
          console.warn('Unknown message type:', (message as WSMessage).type);
      }
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error);
    }
  }

  /**
   * Handle SCENE_UPDATE message
   */
  private handleSceneUpdate(message: SceneUpdateMessage): void {
    if (this.onSceneUpdateCallback) {
      this.onSceneUpdateCallback(message);
    }
  }

  /**
   * Handle WebSocket error
   */
  private handleError(event: Event): void {
    console.error('WebSocket error:', event);
    this.handleConnectionError(new Error('WebSocket connection error'));
  }

  /**
   * Handle WebSocket close
   */
  private handleClose(event: CloseEvent): void {
    const wasConnected = this.state === 'connected';
    this.setState('disconnected');

    if (wasConnected && this.onDisconnectCallback) {
      this.onDisconnectCallback();
    }

    // Attempt to reconnect if not intentionally disconnected
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.scheduleReconnect();
    } else {
      console.error('Max reconnect attempts reached');
      this.setState('error');
    }
  }

  /**
   * Schedule reconnection attempt
   */
  private scheduleReconnect(): void {
    this.reconnectAttempts++;

    // Exponential backoff
    const delay = Math.min(this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1), 30000);

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

    this.reconnectTimeout = window.setTimeout(() => {
      this.connect();
    }, delay);
  }

  /**
   * Handle connection error
   */
  private handleConnectionError(error: Error): void {
    console.error('Connection error:', error);
    this.setState('error');

    if (this.onErrorCallback) {
      this.onErrorCallback(error);
    }

    // Schedule reconnect
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.scheduleReconnect();
    }
  }

  /**
   * Set connection state
   */
  private setState(newState: ConnectionState): void {
    if (this.state !== newState) {
      console.log(`WebSocket state: ${this.state} -> ${newState}`);
      this.state = newState;
    }
  }

  /**
   * Send a message to the server (if needed for future features)
   */
  public send(type: string, payload: unknown): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const message: WSMessage = {
        type: type as any,
        timestamp: Date.now(),
        payload,
      };
      this.ws.send(JSON.stringify(message));
    } else {
      console.warn('Cannot send message: WebSocket not connected');
    }
  }
}
