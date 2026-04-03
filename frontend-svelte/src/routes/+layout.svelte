<script lang="ts">
  import '../app.css';
  import favicon from '$lib/assets/favicon.svg';

  let { children } = $props();
</script>

<svelte:head>
  <link rel="icon" href={favicon} />
  <meta name="description" content="Discord Visual Room - 3D visualization of Discord voice channels" />
  <title>Discord Visual Room</title>
</svelte:head>

{@render children()}
