<script lang="ts">
  import { onMount } from 'svelte';

  export interface GuildOption {
    id: string;
    name: string;
    icon: string | null;
    memberCount: number;
  }

  let {
    apiUrl = `http://${window.location.hostname}:9050`,
    onSelect,
  }: {
    apiUrl?: string;
    onSelect: (guild: GuildOption) => void;
  } = $props();

  let guilds: GuildOption[] = $state([]);
  let loading = $state(true);
  let error = $state('');
  let selectedId = $state('');
  let open = $state(false);

  onMount(async () => {
    try {
      const res = await fetch(`${apiUrl}/api/guilds`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      guilds = data.guilds || [];
      if (guilds.length === 0) {
        error = 'No guilds found. Make sure the bot is invited to a server.';
      }
    } catch (e) {
      error = `Failed to fetch guilds: ${(e as Error).message}`;
    } finally {
      loading = false;
    }
  });

  function handleSelect(guild: GuildOption) {
    selectedId = guild.id;
    open = false;

    // Tell backend to switch to this guild
    fetch(`${apiUrl}/api/guilds/select`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ guildId: guild.id }),
    }).catch((e) => console.error('Failed to select guild:', e));

    onSelect(guild);
  }

  let selectedGuild = $derived(guilds.find((g) => g.id === selectedId));
</script>

<div class="guild-selector">
  {#if loading}
    <div class="loading">
      <div class="spinner"></div>
      <span>Discovering servers...</span>
    </div>
  {:else if error}
    <div class="error">{error}</div>
  {:else}
    <div class="selector-container">
      <div class="label">Select a server to visualize</div>

      <div class="dropdown" class:open>
        <button
          class="dropdown-trigger"
          onclick={() => (open = !open)}
          onblur={() => setTimeout(() => (open = false), 150)}
        >
          {#if selectedGuild}
            <div class="selected-guild">
              {#if selectedGuild.icon}
                <img src={selectedGuild.icon} alt="" class="guild-icon" />
              {:else}
                <div class="guild-icon-placeholder">
                  {selectedGuild.name.charAt(0).toUpperCase()}
                </div>
              {/if}
              <span class="guild-name">{selectedGuild.name}</span>
              <span class="member-badge">{selectedGuild.memberCount} members</span>
            </div>
          {:else}
            <span class="placeholder">Choose a server...</span>
          {/if}
          <svg class="chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 9l6 6 6-6" />
          </svg>
        </button>

        {#if open}
          <div class="dropdown-menu">
            {#each guilds as guild (guild.id)}
              <button
                class="dropdown-item"
                class:active={guild.id === selectedId}
                onclick={() => handleSelect(guild)}
              >
                {#if guild.icon}
                  <img src={guild.icon} alt="" class="guild-icon" />
                {:else}
                  <div class="guild-icon-placeholder">
                    {guild.name.charAt(0).toUpperCase()}
                  </div>
                {/if}
                <div class="guild-info">
                  <div class="guild-name">{guild.name}</div>
                  <div class="guild-members">{guild.memberCount} members</div>
                </div>
              </button>
            {/each}
          </div>
        {/if}
      </div>
    </div>
  {/if}
</div>

<style>
  .guild-selector {
    position: fixed;
    inset: 0;
    z-index: 200;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(14, 14, 24, 0.95);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
  }

  .loading,
  .error {
    text-align: center;
    color: #b8b8e0;
    font-size: 16px;
    padding: 24px;
  }

  .error {
    color: #ed4245;
    max-width: 400px;
  }

  .spinner {
    width: 24px;
    height: 24px;
    border: 2px solid rgba(88, 101, 242, 0.3);
    border-top-color: #5865F2;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    margin: 0 auto 12px;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  .selector-container {
    width: 380px;
    max-width: 90vw;
  }

  .label {
    display: block;
    font-size: 13px;
    font-weight: 600;
    color: #9999bb;
    letter-spacing: 0.5px;
    text-transform: uppercase;
    margin-bottom: 10px;
  }

  .dropdown {
    position: relative;
  }

  .dropdown-trigger {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    background: rgba(40, 40, 60, 0.6);
    border: 1px solid rgba(88, 101, 242, 0.3);
    border-radius: 12px;
    color: #e0e0f0;
    font-size: 15px;
    cursor: pointer;
    transition: border-color 0.2s ease, background 0.2s ease;
  }

  .dropdown-trigger:hover {
    border-color: rgba(88, 101, 242, 0.6);
    background: rgba(40, 40, 60, 0.8);
  }

  .dropdown.open .dropdown-trigger {
    border-color: #5865F2;
  }

  .placeholder {
    color: #666680;
  }

  .selected-guild {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 1;
    min-width: 0;
  }

  .guild-icon {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .guild-icon-placeholder {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: linear-gradient(135deg, #5865F2, #eb459e);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: 700;
    color: white;
    flex-shrink: 0;
  }

  .guild-name {
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .member-badge {
    font-size: 12px;
    color: #9999bb;
    padding: 2px 8px;
    background: rgba(88, 101, 242, 0.15);
    border-radius: 10px;
    flex-shrink: 0;
  }

  .chevron {
    width: 18px;
    height: 18px;
    color: #9999bb;
    flex-shrink: 0;
    margin-left: 8px;
    transition: transform 0.2s ease;
  }

  .dropdown.open .chevron {
    transform: rotate(180deg);
  }

  .dropdown-menu {
    position: absolute;
    top: calc(100% + 6px);
    left: 0;
    right: 0;
    background: rgba(30, 30, 48, 0.98);
    border: 1px solid rgba(88, 101, 242, 0.3);
    border-radius: 12px;
    padding: 6px;
    max-height: 300px;
    overflow-y: auto;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
    z-index: 10;
  }

  .dropdown-item {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    background: transparent;
    border: none;
    border-radius: 8px;
    color: #e0e0f0;
    cursor: pointer;
    transition: background 0.15s ease;
    text-align: left;
  }

  .dropdown-item:hover {
    background: rgba(88, 101, 242, 0.15);
  }

  .dropdown-item.active {
    background: rgba(88, 101, 242, 0.25);
  }

  .dropdown-item .guild-info {
    min-width: 0;
    flex: 1;
  }

  .dropdown-item .guild-name {
    font-size: 14px;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .dropdown-item .guild-members {
    font-size: 12px;
    color: #9999bb;
    margin-top: 2px;
  }
</style>
