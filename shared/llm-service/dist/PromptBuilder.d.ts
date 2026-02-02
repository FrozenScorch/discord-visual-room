/**
 * Prompt Builder for LLM layout generation
 * Constructs prompts with strict furniture type constraints
 */
import type { UserWithActivity } from "./types.js";
/**
 * Builds a prompt for the LLM to generate furniture assignments
 *
 * @param users - Array of users with their activities
 * @returns A formatted prompt string for the LLM
 */
export declare function buildPrompt(users: UserWithActivity[]): string;
/**
 * Builds a simplified prompt for quick testing
 *
 * @param users - Array of users with their activities
 * @returns A simplified prompt string
 */
export declare function buildSimplePrompt(users: UserWithActivity[]): string;
/**
 * Extracts user activity summary for prompt context
 *
 * @param users - Array of users with their activities
 * @returns A summary of activities in the room
 */
export declare function getActivitySummary(users: UserWithActivity[]): string;
