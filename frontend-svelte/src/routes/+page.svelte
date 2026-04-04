<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { sceneGraph } from '$lib/stores/sceneGraph';
  import { connection } from '$lib/stores/connection';
  import { connect, disconnect } from '$lib/wsClient';
  import GuildScene from '$lib/components/GuildScene.svelte';
  import GuildSelector from '$lib/components/GuildSelector.svelte';
  import TopBar from '$lib/components/TopBar.svelte';
  import ConnectionStatus from '$lib/components/ConnectionStatus.svelte';
  import type { GuildSceneGraph, ViewMode, CameraTarget } from '$lib/types';

  let apiUrl = 'http://localhost:9000';
  let wsUrl = 'ws://localhost:9000/ws';
  let guildSelected = $state(false);
  let currentScene: GuildSceneGraph = $state({
    version: '2.0.0',
    timestamp: 0,
    guild: { id: 'default', name: 'Discord Visual Room', roles: [], onlineMemberCount: 0 },
    rooms: [],
    roomsMeta: [],
  });
  let connectionState: 'disconnected' | 'connecting' | 'connected' | 'error' = $state('disconnected');
  let viewMode: ViewMode = $state('overview');
  let focusedRoom: CameraTarget | null = $state(null);
  let topBarFaded = $state(false);

  // Subscribe to stores
  const unsubscribeScene = sceneGraph.subscribe((value) => {
    currentScene = value;
    // Once we get a real scene with rooms, mark as selected
    if (value.guild.id !== 'default') {
      guildSelected = true;
    }
  });
  const unsubscribeConnection = connection.subscribe((value) => {
    connectionState = value;
    if (value === 'connected') {
      setTimeout(() => {
        topBarFaded = true;
      }, 3000);
    }
  });

  function handleGuildSelect(guild: { id: string; name: string }) {
    guildSelected = true;
    // Connect WebSocket to receive scene updates
    connect(wsUrl);
  }

  function handleBackToOverview() {
    viewMode = 'overview';
    focusedRoom = null;
    topBarFaded = false;
  }

  onMount(() => {
    // Don't connect WebSocket until guild is selected
    // (backend needs to know which guild to visualize)
  });

  onDestroy(() => {
    disconnect();
    unsubscribeScene();
    unsubscribeConnection();
  });
</script>

{#if !guildSelected}
  <!-- Guild selection overlay -->
  <GuildSelector {apiUrl} onSelect={handleGuildSelect} />
{:else}
  <!-- 3D visualization -->
  <GuildScene
    bind:viewMode
    bind:focusedRoom
    sceneData={currentScene}
  />

  <TopBar
    guildName={currentScene.guild.name}
    onlineCount={currentScene.guild.onlineMemberCount || currentScene.rooms.reduce((s, r) => s + r.users.length, 0)}
    {viewMode}
    {focusedRoom}
    onBack={handleBackToOverview}
    faded={topBarFaded}
  />

  <ConnectionStatus state={connectionState} />
{/if}
