<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import type { Snippet } from 'svelte';
  import { sceneGraph } from '$lib/stores/sceneGraph';
  import { connection } from '$lib/stores/connection';
  import { connect, disconnect } from '$lib/wsClient';
  import Scene from '$lib/components/Scene.svelte';
  import FurnitureGroup from '$lib/components/FurnitureGroup.svelte';
  import UserGroup from '$lib/components/UserGroup.svelte';
  import TopBar from '$lib/components/TopBar.svelte';
  import ConnectionStatus from '$lib/components/ConnectionStatus.svelte';
  import type { SceneGraph } from '$lib/types';

  let wsUrl = 'ws://localhost:8080/ws';
  let currentScene: SceneGraph = $state({
    version: '0',
    timestamp: 0,
    users: [],
    furniture: [],
    room: {
      id: 'default',
      name: 'Discord Visual Room',
      dimensions: { width: 20, height: 4, depth: 20 },
      maxUsers: 20,
    },
  });
  let connectionState: 'disconnected' | 'connecting' | 'connected' | 'error' = $state('disconnected');
  let topBarFaded = $state(false);

  // Subscribe to stores
  const unsubscribeScene = sceneGraph.subscribe((value) => {
    currentScene = value;
  });
  const unsubscribeConnection = connection.subscribe((value) => {
    connectionState = value;
    if (value === 'connected') {
      setTimeout(() => {
        topBarFaded = true;
      }, 3000);
    }
  });

  onMount(() => {
    connect(wsUrl);
  });

  onDestroy(() => {
    disconnect();
    unsubscribeScene();
    unsubscribeConnection();
  });
</script>

<Scene sceneData={currentScene}>
  {#snippet children()}
    <FurnitureGroup furniture={currentScene.furniture} />
    <UserGroup users={currentScene.users} />
  {/snippet}
</Scene>

<TopBar roomName={currentScene.room.name} userCount={currentScene.users.length} faded={topBarFaded} />

<ConnectionStatus state={connectionState} />
