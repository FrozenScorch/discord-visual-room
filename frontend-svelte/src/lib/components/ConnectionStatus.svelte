<script lang="ts">
  import type { ConnectionState } from '$lib/types';

  let { state = 'disconnected' }: { state?: ConnectionState } = $props();

  let label = $derived.by(() => {
    switch (state) {
      case 'connected':
        return 'Connected';
      case 'connecting':
        return 'Connecting...';
      case 'error':
        return 'Connection error';
      case 'disconnected':
        return 'Disconnected';
      default:
        return '';
    }
  });

  let dotClass = $derived(`status-${state}`);
</script>

<div class="connection-status {dotClass}" title={label}>
  <span class="status-dot-inner"></span>
  <span class="status-text">{label}</span>
</div>

<style>
  .connection-status {
    position: fixed;
    bottom: 16px;
    left: 16px;
    z-index: 100;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 14px;
    background: rgba(22, 22, 37, 0.75);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border-radius: 20px;
    border: 1px solid rgba(255, 255, 255, 0.08);
    font-size: 12px;
    color: #999;
    transition: border-color 0.3s ease;
  }

  .status-dot-inner {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #666;
    transition: background 0.3s ease, box-shadow 0.3s ease;
  }

  .status-text {
    font-weight: 500;
  }

  /* Connected */
  .status-connected {
    border-color: rgba(67, 181, 129, 0.3);
  }

  .status-connected .status-dot-inner {
    background: #43b581;
    box-shadow: 0 0 6px rgba(67, 181, 129, 0.5);
  }

  .status-connected .status-text {
    color: #43b581;
  }

  /* Connecting */
  .status-connecting .status-dot-inner {
    background: #faa61a;
    box-shadow: 0 0 6px rgba(250, 166, 26, 0.5);
    animation: pulse 1.5s ease-in-out infinite;
  }

  .status-connecting .status-text {
    color: #faa61a;
  }

  /* Error */
  .status-error {
    border-color: rgba(237, 66, 69, 0.3);
  }

  .status-error .status-dot-inner {
    background: #ed4245;
    box-shadow: 0 0 6px rgba(237, 66, 69, 0.5);
  }

  .status-error .status-text {
    color: #ed4245;
  }

  /* Disconnected */
  .status-disconnected .status-dot-inner {
    background: #666;
  }

  @keyframes pulse {
    0%,
    100% {
      opacity: 1;
    }
    50% {
      opacity: 0.4;
    }
  }
</style>
