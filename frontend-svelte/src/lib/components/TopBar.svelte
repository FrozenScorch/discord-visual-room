<script lang="ts">
  let { roomName = 'Discord Visual Room', userCount = 0, faded = false }: {
    roomName?: string;
    userCount?: number;
    faded?: boolean;
  } = $props();
</script>

<div class="top-bar" class:faded>
  <div class="room-title">{roomName}</div>
  <div class="user-count">
    <span class="user-count-icon" aria-hidden="true">&#x1F464;</span>
    <span class="user-count-number">{userCount}</span>
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
