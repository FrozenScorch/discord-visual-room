<script lang="ts">
  import UserAvatarItem from './UserAvatarItem.svelte';
  import type { UserNode } from '$lib/types';

  let { users }: { users: UserNode[] } = $props();
</script>

{#each users as user (user.id)}
  <UserAvatarItem node={user} />
{/each}
