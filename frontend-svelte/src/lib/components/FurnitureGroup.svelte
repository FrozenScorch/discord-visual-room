<script lang="ts">
  import FurnitureItem from './FurnitureItem.svelte';
  import type { FurnitureNode } from '$lib/types';

  let { furniture }: { furniture: FurnitureNode[] } = $props();
</script>

{#each furniture as item (item.id)}
  <FurnitureItem node={item} />
{/each}
