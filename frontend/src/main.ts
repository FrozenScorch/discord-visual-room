/**
 * Main entry point for Discord Visual Room Frontend
 *
 * Architecture: DUMB RENDERER
 * - All state comes from backend via WebSocket
 * - Frontend only renders what it receives
 * - No local state management
 */

import { SceneRenderer } from './SceneRenderer';
import { WSClient } from './WSClient';
import type { SceneUpdateMessage } from './types';

// Configuration
// WebSocket URL - ensure it ends with /ws to match backend route
const rawWsUrl = import.meta.env.VITE_WS_URL || import.meta.env.VITE_FRONTEND_WS_URL || 'ws://localhost:8080/ws';
const WS_URL = rawWsUrl.endsWith('/ws') ? rawWsUrl : `${rawWsUrl}/ws`;

/**
 * Application class that manages the frontend
 */
class DiscordVisualRoomApp {
  private renderer: SceneRenderer | null = null;
  private wsClient: WSClient | null = null;
  private container: HTMLElement;
  private statusElement: HTMLElement;
  private topBar: HTMLElement | null;
  private userCountEl: HTMLElement | null;

  constructor() {
    this.container = document.getElementById('canvas-container')!;
    if (!this.container) {
      throw new Error('Canvas container not found');
    }

    this.statusElement = document.getElementById('connection-status')!;
    this.topBar = document.getElementById('top-bar');
    this.userCountEl = document.getElementById('user-count-number');

    this.init();
  }

  /**
   * Initialize the application
   */
  private init(): void {
    // Create renderer
    this.renderer = new SceneRenderer(this.container, {
      wsUrl: WS_URL,
      cameraFov: 60,
      cameraNear: 0.1,
      cameraFar: 1000,
      lerpFactor: 0.1,
    });

    // Create WebSocket client
    this.wsClient = new WSClient(WS_URL);

    // Set up event handlers
    this.setupWebSocketHandlers();

    // Connect to WebSocket
    this.updateStatus('connecting', 'Connecting to server...');
    this.wsClient.connect();

    console.log('Discord Visual Room initialized');
  }

  /**
   * Set up WebSocket event handlers
   */
  private setupWebSocketHandlers(): void {
    if (!this.wsClient) return;

    // Connection established
    this.wsClient.onConnect(() => {
      console.log('Connected to server');
      this.updateStatus('connected', 'Connected to Discord Visual Room');
    });

    // Disconnected
    this.wsClient.onDisconnect(() => {
      console.log('Disconnected from server');
      this.updateStatus('disconnected', 'Disconnected from server');
    });

    // Error
    this.wsClient.onError((error) => {
      console.error('WebSocket error:', error);
      this.updateStatus('error', `Connection error: ${error.message}`);
    });

    // Scene update
    this.wsClient.onSceneUpdate((message: SceneUpdateMessage) => {
      if (this.renderer) {
        this.renderer.updateScene(message.payload);
        // Update user count badge
        if (this.userCountEl) {
          this.userCountEl.textContent = String(message.payload.users.length);
        }
      }
    });
  }

  /**
   * Update connection status display
   */
  private updateStatus(state: string, message: string): void {
    if (this.statusElement) {
      const statusText = this.statusElement.querySelector('.status-text');
      if (statusText) statusText.textContent = message;
      this.statusElement.className = `status-dot ${state}`;

      // Fade top bar after connection
      if (state === 'connected' && this.topBar) {
        setTimeout(() => {
          this.topBar?.classList.add('faded');
        }, 3000);
      }
    }

    // Also log to console
    console.log(`[${state.toUpperCase()}]`, message);
  }

  /**
   * Cleanup on page unload
   */
  public dispose(): void {
    if (this.wsClient) {
      this.wsClient.disconnect();
    }
    if (this.renderer) {
      this.renderer.dispose();
    }
  }
}

// Initialize app when DOM is ready
let app: DiscordVisualRoomApp | null = null;

function main(): void {
  try {
    app = new DiscordVisualRoomApp();
  } catch (error) {
    console.error('Failed to initialize application:', error);
    const statusElement = document.getElementById('connection-status');
    if (statusElement) {
      statusElement.textContent = `Failed to initialize: ${error}`;
      statusElement.className = 'status error';
    }
  }
}

// Wait for DOM to be ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', main);
} else {
  main();
}

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
  if (app) {
    app.dispose();
  }
});

// Export for debugging
(window as any).discordVisualRoomApp = app;
