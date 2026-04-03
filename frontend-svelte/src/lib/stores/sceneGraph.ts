import { writable } from 'svelte/store';
import type { GuildSceneGraph, RoomData } from '../types';

const defaultGuild: GuildSceneGraph = {
  version: '2.0.0',
  timestamp: 0,
  guild: {
    id: 'default',
    name: 'Discord Visual Room',
    roles: [],
    onlineMemberCount: 0,
  },
  rooms: [],
  roomsMeta: [],
};

function createSceneGraphStore() {
  const { subscribe, set, update } = writable<GuildSceneGraph>(defaultGuild);
  return {
    subscribe,
    set,
    update,
  };
}

export const sceneGraph = createSceneGraphStore();

/** Helper: get a specific room by id from the current store value */
export function getRoomById(rooms: RoomData[], roomId: string): RoomData | undefined {
  return rooms.find((r) => r.id === roomId);
}
