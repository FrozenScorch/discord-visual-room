import { writable } from 'svelte/store';
import type { ConnectionState } from '../types';

function createConnectionStore() {
  const { subscribe, set, update } = writable<ConnectionState>('disconnected');
  return {
    subscribe,
    set,
    update
  };
}

export const connection = createConnectionStore();
