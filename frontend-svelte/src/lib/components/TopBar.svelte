<script lang="ts">
  import type { ViewMode, CameraTarget } from '$lib/types';

  let {
    guildName = 'Discord Visual Room',
    onlineCount = 0,
    viewMode = 'overview',
    focusedRoom = null,
    onBack = () => {},
    faded = false,
  }: {
    guildName?: string;
    onlineCount?: number;
    viewMode?: ViewMode;
    focusedRoom?: CameraTarget | null;
    onBack?: () => void;
    faded?: boolean;
  } = $props();

  let title = $derived(viewMode === 'room' && focusedRoom ? focusedRoom.roomName : guildName);
  let showBack = $derived(viewMode === 'room');
</script>

<div class="top-bar" class:faded>
  <div class="left">
    {#if showBack}
      <button class="back-btn" onclick={onBack} title="Back to overview">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 12H5" />
          <path d="M12 19l-7-7 7-7" />
        </svg>
      </button>
    {/if}
    <div class="room-title">{title}</div>
  </div>
  <div class="user-count">
    <span class="user-count-icon" aria-hidden="true">&#x1F464;</span>
    <span class="user-count-number">{onlineCount}</span>
  </div>
</div>

<style>
  .top-bar {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    z-index: 100;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 24px;
    background: rgba(22, 22, 37, 0.75);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border-bottom: 1px solid rgba(88, 101, 242, 0.15);
    transition: opacity 1.5s ease;
  }

  .top-bar.faded {
    opacity: 0.3;
  }

  .top-bar:hover {
    opacity: 1 !important;
  }

  .left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .back-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border: 1px solid rgba(88, 101, 242, 0.3);
    border-radius: 8px;
    background: rgba(88, 101, 242, 0.1);
    color: #b8b8e0;
    cursor: pointer;
    transition: background 0.2s ease, border-color 0.2s ease;
  }

  .back-btn:hover {
    background: rgba(88, 101, 242, 0.25);
    border-color: rgba(88, 101, 242, 0.5);
  }

  .room-title {
    font-size: 16px;
    font-weight: 600;
    color: #e0e0f0;
    letter-spacing: 0.3px;
  }

  .user-count {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 12px;
    background: rgba(88, 101, 242, 0.2);
    border-radius: 16px;
    border: 1px solid rgba(88, 101, 242, 0.3);
  }

  .user-count-icon {
    font-size: 14px;
  }

  .user-count-number {
    font-size: 14px;
    font-weight: 600;
    color: #b8b8e0;
  }
</style>
