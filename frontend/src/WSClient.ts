import { io, Socket } from 'socket.io-client';
import type { SceneUpdateMessage, ConnectionState, WSMessage } from './types';

/**
 * Socket.IO client for receiving SceneGraph updates from backend
 *
 * Architecture: DUMB RENDERER
 * - Maintains NO state
 * - Receives complete scene state from backend
 * - Passes all data to SceneRenderer
 *
 * Uses Socket.IO to match the Node.js backend transport layer.
 */
export class WSClient {
  private socket: Socket | null = null;
  private serverUrl: string;
  private state: ConnectionState = 'disconnected';

  // Callbacks
  private onConnectCallback?: () => void;
  private onDisconnectCallback?: () => void;
  private onErrorCallback?: (error: Error) => void;
  private onSceneUpdateCallback?: (message: SceneUpdateMessage) => void;

  constructor(serverUrl: string) {
    // Socket.IO uses http:// URLs, not ws://
    // Strip trailing /ws if present (Socket.IO handles its own path)
    this.serverUrl = serverUrl.replace(/\/ws$/, '').replace(/^ws(s?):\/\//, 'http$1://');
  }

  /**
   * Connect to Socket.IO server
   */
  public connect(): void {
    if (this.socket && this.socket.connected) {
      console.log('Socket.IO already connected');
      return;
    }

    if (this.socket) {
      // If socket exists but is disconnected, clean it up
      this.socket.removeAllListeners();
      this.socket = null;
    }

    this.setState('connecting');
    console.log(`Connecting to Socket.IO server: ${this.serverUrl}`);

    try {
      this.socket = io(this.serverUrl, {
        transports: ['websocket', 'polling'],
        reconnection: true,
        reconnectionAttempts: 10,
        reconnectionDelay: 1000,
        reconnectionDelayMax: 30000,
      });

      this.socket.on('connect', this.handleConnect.bind(this));
      this.socket.on('disconnect', this.handleDisconnect.bind(this));
      this.socket.on('connect_error', this.handleConnectError.bind(this));
      this.socket.on('SCENE_UPDATE', this.handleSceneUpdateEvent.bind(this));
      this.socket.on('ERROR', this.handleErrorEvent.bind(this));
    } catch (error) {
      console.error('Failed to create Socket.IO client:', error);
      this.handleConnectionError(new Error('Failed to create Socket.IO connection'));
    }
  }

  /**
   * Disconnect from Socket.IO server
   */
  public disconnect(): void {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = null;
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
   * Handle Socket.IO connect event
   */
  private handleConnect(): void {
    console.log(`Socket.IO connected (id: ${this.socket?.id})`);
    this.setState('connected');

    if (this.onConnectCallback) {
      this.onConnectCallback();
    }
  }

  /**
   * Handle SCENE_UPDATE event from backend
   *
   * Backend emits: io.emit('SCENE_UPDATE', { type, timestamp, payload })
   * Socket.IO delivers the data object directly as the first argument.
   */
  private handleSceneUpdateEvent(data: unknown): void {
    try {
      const parsed = typeof data === 'string' ? JSON.parse(data) : data;

      if (parsed && parsed.type === 'SCENE_UPDATE' && parsed.payload) {
        const message: SceneUpdateMessage = {
          type: 'SCENE_UPDATE',
          timestamp: parsed.timestamp || Date.now(),
          payload: parsed.payload,
        };
        this.handleSceneUpdate(message);
        return;
      }

      // Handle raw SceneGraph (no wrapper) as fallback
      if (parsed && parsed.version && parsed.users !== undefined && parsed.furniture !== undefined) {
        const message: SceneUpdateMessage = {
          type: 'SCENE_UPDATE',
          timestamp: parsed.timestamp || Date.now(),
          payload: parsed,
        };
        this.handleSceneUpdate(message);
        return;
      }

      console.warn('Unrecognized SCENE_UPDATE payload format:', parsed);
    } catch (error) {
      console.error('Failed to parse SCENE_UPDATE message:', error);
    }
  }

  /**
   * Handle ERROR event from backend
   */
  private handleErrorEvent(data: unknown): void {
    console.error('Server error event:', data);
    if (this.onErrorCallback) {
      this.onErrorCallback(new Error(String(data)));
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
   * Handle Socket.IO disconnect event
   */
  private handleDisconnect(reason: string): void {
    const wasConnected = this.state === 'connected';
    this.setState('disconnected');
    console.log(`Socket.IO disconnected: ${reason}`);

    if (wasConnected && this.onDisconnectCallback) {
      this.onDisconnectCallback();
    }
  }

  /**
   * Handle Socket.IO connection error
   */
  private handleConnectError(error: Error): void {
    console.error('Socket.IO connection error:', error.message);
    this.handleConnectionError(new Error(`Socket.IO connection error: ${error.message}`));
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
  }

  /**
   * Set connection state
   */
  private setState(newState: ConnectionState): void {
    if (this.state !== newState) {
      console.log(`Socket.IO state: ${this.state} -> ${newState}`);
      this.state = newState;
    }
  }

  /**
   * Send a message to the server (for PING, GUILD_SELECT, etc.)
   */
  public send(type: string, payload: unknown): void {
    if (this.socket && this.socket.connected) {
      const message: WSMessage = {
        type: type as any,
        timestamp: Date.now(),
        payload,
      };
      this.socket.emit(type, message);
    } else {
      console.warn('Cannot send message: Socket.IO not connected');
    }
  }
}
