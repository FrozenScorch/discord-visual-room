/**
 * WebSocket client for receiving SceneGraph updates from backend
 *
 * Ported from ../frontend/src/WSClient.ts for Svelte integration.
 * Instead of callbacks, this module updates Svelte stores directly.
 *
 * Architecture: DUMB RENDERER
 * - Maintains NO state
 * - Receives complete scene state from backend
 * - Passes all data to Svelte stores
 */

import { connection } from './stores/connection';
import { sceneGraph } from './stores/sceneGraph';
import type { SceneUpdateMessage, ConnectionState, WSMessage } from './types';

let ws: WebSocket | null = null;
let wsUrl: string = 'ws://localhost:8080/ws';
let reconnectAttempts: number = 0;
const maxReconnectAttempts: number = 10;
const reconnectDelay: number = 1000;
let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;

/**
 * Connect to the WebSocket server
 */
export function connect(url?: string): void {
  if (url) {
    wsUrl = url;
  }

  if (ws && (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN)) {
    console.log('[WS] Already connected or connecting');
    return;
  }

  connection.set('connecting');
  console.log(`[WS] Connecting to ${wsUrl}`);

  try {
    ws = new WebSocket(wsUrl);

    ws.onopen = handleOpen;
    ws.onmessage = handleMessage;
    ws.onerror = handleError;
    ws.onclose = handleClose;
  } catch (error) {
    console.error('[WS] Failed to create WebSocket:', error);
    handleConnectionError(new Error('Failed to create WebSocket connection'));
  }
}

/**
 * Disconnect from the WebSocket server
 */
export function disconnect(): void {
  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout);
    reconnectTimeout = null;
  }

  if (ws) {
    reconnectAttempts = maxReconnectAttempts; // Prevent auto-reconnect
    ws.close();
    ws = null;
  }

  connection.set('disconnected');
}

/**
 * Send a message to the server (for future features)
 */
export function send(type: string, payload: unknown): void {
  if (ws && ws.readyState === WebSocket.OPEN) {
    const message: WSMessage = {
      type: type as WSMessage['type'],
      timestamp: Date.now(),
      payload,
    };
    ws.send(JSON.stringify(message));
  } else {
    console.warn('[WS] Cannot send message: WebSocket not connected');
  }
}

/**
 * Get current WebSocket ready state
 */
export function getState(): ConnectionState {
  return ws?.readyState === WebSocket.OPEN
    ? 'connected'
    : ws?.readyState === WebSocket.CONNECTING
      ? 'connecting'
      : 'disconnected';
}

// ─── Internal handlers ──────────────────────────────────────────────────────

function handleOpen(): void {
  console.log('[WS] Connected');
  connection.set('connected');
  reconnectAttempts = 0;
}

function handleMessage(event: MessageEvent): void {
  try {
    const parsed = JSON.parse(event.data);

    // Handle wrapped message format: {"type": "SCENE_UPDATE", "timestamp": ..., "payload": {...}}
    if (parsed.type === 'SCENE_UPDATE' && parsed.payload) {
      const message: SceneUpdateMessage = {
        type: 'SCENE_UPDATE',
        timestamp: parsed.timestamp || Date.now(),
        payload: parsed.payload,
      };
      handleSceneUpdate(message);
      return;
    }

    // Handle other wrapped message types
    if (parsed.type) {
      const message = parsed as WSMessage;
      switch (message.type) {
        case 'USER_JOINED':
        case 'USER_LEFT':
        case 'USER_MOVED':
        case 'ACTIVITY_CHANGED':
          console.log(`[WS] Received ${message.type} (handled by SCENE_UPDATE)`);
          break;
        case 'ERROR':
          console.error('[WS] Server error:', message.payload);
          break;
        default:
          console.warn('[WS] Unknown message type:', message.type);
      }
      return;
    }

    // Handle raw SceneGraph (no wrapper) as fallback
    if (parsed.version && parsed.users !== undefined && parsed.furniture !== undefined) {
      const message: SceneUpdateMessage = {
        type: 'SCENE_UPDATE',
        timestamp: parsed.timestamp || Date.now(),
        payload: parsed,
      };
      handleSceneUpdate(message);
      return;
    }

    console.warn('[WS] Unrecognized message format:', parsed);
  } catch (error) {
    console.error('[WS] Failed to parse message:', error);
  }
}

function handleSceneUpdate(message: SceneUpdateMessage): void {
  sceneGraph.set(message.payload);
}

function handleError(_event: Event): void {
  console.error('[WS] WebSocket error:', _event);
  handleConnectionError(new Error('WebSocket connection error'));
}

function handleClose(_event: CloseEvent): void {
  const prevState = getState();
  connection.set('disconnected');

  // Attempt to reconnect if not intentionally disconnected
  if (reconnectAttempts < maxReconnectAttempts) {
    scheduleReconnect();
  } else {
    console.error('[WS] Max reconnect attempts reached');
    connection.set('error');
  }
}

function scheduleReconnect(): void {
  reconnectAttempts++;

  // Exponential backoff, capped at 30s
  const delay = Math.min(reconnectDelay * Math.pow(2, reconnectAttempts - 1), 30000);

  console.log(`[WS] Reconnecting in ${delay}ms (attempt ${reconnectAttempts}/${maxReconnectAttempts})`);

  reconnectTimeout = setTimeout(() => {
    connect();
  }, delay);
}

function handleConnectionError(error: Error): void {
  console.error('[WS] Connection error:', error);
  connection.set('error');

  // Schedule reconnect
  if (reconnectAttempts < maxReconnectAttempts) {
    scheduleReconnect();
  }
}
