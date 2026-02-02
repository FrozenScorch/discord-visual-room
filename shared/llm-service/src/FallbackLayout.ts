/**
 * Fallback Layout Algorithm
 * Deterministic mathematical layout that always works
 */

import type { FurnitureAssignment } from "./types.js";
import { VALID_FURNITURE_TYPES } from "./types.js";

/**
 * Grid configuration for fallback layout
 */
interface GridConfig {
  columns: number;
  spacing: number;
  startX: number;
  startZ: number;
}

const DEFAULT_GRID_CONFIG: GridConfig = {
  columns: 4, // 4 desks per row
  spacing: 3, // 3 units between desks
  startX: -6, // Center the grid
  startZ: -6
} as const;

/**
 * Generates a deterministic linear layout with COMPUTER_DESK nodes in a grid
 *
 * This is a mathematical fallback that ALWAYS succeeds:
 * - Places users in a 4-column grid
 * - Uses only COMPUTER_DESK furniture type (always valid)
 * - Calculates positions deterministically
 *
 * @param count - Number of users to place
 * @param config - Optional grid configuration
 * @returns Array of furniture assignments with positions
 */
export function generateLinearLayout(
  count: number,
  config: Partial<GridConfig> = {}
): FurnitureAssignment[] {
  const gridConfig = { ...DEFAULT_GRID_CONFIG, ...config };
  const assignments: FurnitureAssignment[] = [];

  for (let i = 0; i < count; i++) {
    // Calculate grid position
    const column = i % gridConfig.columns;
    const row = Math.floor(i / gridConfig.columns);

    // Calculate world coordinates
    const x = gridConfig.startX + column * gridConfig.spacing;
    const z = gridConfig.startZ + row * gridConfig.spacing;

    assignments.push({
      userId: `user-${i}`, // Placeholder ID, will be replaced
      furniture: "COMPUTER_DESK", // Always valid - in Asset Dictionary
      position: { x, y: 0, z }
    });
  }

  return assignments;
}

/**
 * Generates a circular layout for better visual presentation
 *
 * @param count - Number of users to place
 * @param radius - Radius of the circle (default: 5)
 * @returns Array of furniture assignments with positions
 */
export function generateCircularLayout(
  count: number,
  radius: number = 5
): FurnitureAssignment[] {
  const assignments: FurnitureAssignment[] = [];
  const angleStep = (2 * Math.PI) / count;

  for (let i = 0; i < count; i++) {
    const angle = i * angleStep;
    const x = Math.cos(angle) * radius;
    const z = Math.sin(angle) * radius;

    assignments.push({
      userId: `user-${i}`,
      furniture: "COMPUTER_DESK",
      position: { x, y: 0, z }
    });
  }

  return assignments;
}

/**
 * Generates a layout with mixed furniture types based on index
 * Uses deterministic furniture type selection
 *
 * @param count - Number of users to place
 * @returns Array of furniture assignments with positions
 */
export function generateMixedLayout(count: number): FurnitureAssignment[] {
  const baseLayout = generateLinearLayout(count);
  const furnitureTypes = VALID_FURNITURE_TYPES as readonly string[];

  return baseLayout.map((assignment, index) => ({
    ...assignment,
    // Rotate through furniture types deterministically
    furniture: furnitureTypes[index % furnitureTypes.length] as any
  }));
}

/**
 * Generates a layout optimized for a specific activity type
 *
 * @param count - Number of users
 * @param activityType - Type of activity (e.g., "PLAYING")
 * @returns Array of furniture assignments with positions
 */
export function generateActivityBasedLayout(
  count: number,
  activityType?: string
): FurnitureAssignment[] {
  // Default to computer desks for gaming
  const baseLayout = generateLinearLayout(count);

  // If no activity info, use COMPUTER_DESK
  if (!activityType) {
    return baseLayout;
  }

  // For competitive gaming, all desks
  if (activityType === "PLAYING") {
    return baseLayout.map(a => ({ ...a, furniture: "COMPUTER_DESK" as any }));
  }

  // For streaming, mix of desks and couches
  if (activityType === "STREAMING") {
    return baseLayout.map((a, i) => ({
      ...a,
      furniture: i === 0 ? "COMPUTER_DESK" as any : "COUCH_SINGLE" as any
    }));
  }

  // For listening/watching, couches
  if (activityType === "LISTENING" || activityType === "WATCHING") {
    return baseLayout.map(a => ({ ...a, furniture: "COUCH_SINGLE" as any }));
  }

  // Default fallback
  return baseLayout;
}

/**
 * Assigns positions to an existing set of furniture assignments
 *
 * @param assignments - Array of assignments without positions
 * @returns Array with positions added
 */
export function assignPositions(
  assignments: Array<{ userId: string; furniture: string }>
): FurnitureAssignment[] {
  const layout = generateLinearLayout(assignments.length);

  return assignments.map((assignment, index) => ({
    userId: assignment.userId,
    furniture: assignment.furniture as any,
    position: layout[index].position
  }));
}
