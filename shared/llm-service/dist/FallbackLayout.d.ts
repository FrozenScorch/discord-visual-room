/**
 * Fallback Layout Algorithm
 * Deterministic mathematical layout that always works
 */
import type { FurnitureAssignment } from "./types.js";
/**
 * Grid configuration for fallback layout
 */
interface GridConfig {
    columns: number;
    spacing: number;
    startX: number;
    startZ: number;
}
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
export declare function generateLinearLayout(count: number, config?: Partial<GridConfig>): FurnitureAssignment[];
/**
 * Generates a circular layout for better visual presentation
 *
 * @param count - Number of users to place
 * @param radius - Radius of the circle (default: 5)
 * @returns Array of furniture assignments with positions
 */
export declare function generateCircularLayout(count: number, radius?: number): FurnitureAssignment[];
/**
 * Generates a layout with mixed furniture types based on index
 * Uses deterministic furniture type selection
 *
 * @param count - Number of users to place
 * @returns Array of furniture assignments with positions
 */
export declare function generateMixedLayout(count: number): FurnitureAssignment[];
/**
 * Generates a layout optimized for a specific activity type
 *
 * @param count - Number of users
 * @param activityType - Type of activity (e.g., "PLAYING")
 * @returns Array of furniture assignments with positions
 */
export declare function generateActivityBasedLayout(count: number, activityType?: string): FurnitureAssignment[];
/**
 * Assigns positions to an existing set of furniture assignments
 *
 * @param assignments - Array of assignments without positions
 * @returns Array with positions added
 */
export declare function assignPositions(assignments: Array<{
    userId: string;
    furniture: string;
}>): FurnitureAssignment[];
export {};
