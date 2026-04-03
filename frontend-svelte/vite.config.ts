import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [sveltekit()],
	server: {
		port: 8000,
		host: true
	},
	build: {
		sourcemap: true
	}
});
