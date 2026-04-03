import { writable } from 'svelte/store';
import type { SceneGraph } from '../types';

const defaultScene: SceneGraph = {
  version: '0',
  timestamp: 0,
  users: [],
  furniture: [],
  room: {
    id: 'default',
    name: 'Discord Visual Room',
    dimensions: {
      width: 20,
      height: 4,
      depth: 20
    },
    maxUsers: 20
  }
};

function createSceneGraphStore() {
  const { subscribe, set, update } = writable<SceneGraph>(defaultScene);
  return {
    subscribe,
    set,
    update
  };
}

export const sceneGraph = createSceneGraphStore();
