/**
 * LLM Layout Generation Service
 *
 * Main service interface for generating furniture layouts using LLM
 * with graceful fallback to deterministic algorithms.
 *
 * Flow:
 * 1. Try LLM generation
 * 2. Validate response against Asset Dictionary
 * 3. If timeout/error/invalid → use fallback
 * 4. Fallback ALWAYS returns valid result
 * 5. System NEVER crashes
 */
import type { UserWithActivity, ValidatedLayout } from "./types.js";
import type { LLMConfig } from "./types.js";
/**
 * Main service for generating layouts with LLM and fallback support
 */
export declare class LLMLayoutService {
    #private;
    private readonly client;
    private readonly config;
    constructor(config?: Partial<LLMConfig>);
    /**
     * Generate a furniture layout for the given users
     *
     * This method implements graceful degradation:
     * - Attempts LLM generation first
     * - Falls back to deterministic layout on any failure
     * - Never throws, always returns a valid result
     *
     * @param users - Array of users with their activities
     * @param roomCapacity - Maximum capacity of the room (for layout planning)
     * @returns Validated layout with assignments and metadata
     */
    generateLayout(users: UserWithActivity[], roomCapacity: number): Promise<ValidatedLayout>;
    /**
     * Check if the LLM server is healthy
     */
    healthCheck(): Promise<boolean>;
    /**
     * Get the current configuration
     */
    getConfig(): LLMConfig;
}
/**
 * Convenience function to generate a layout without instantiating the service
 *
 * @param users - Array of users with their activities
 * @param roomCapacity - Maximum capacity of the room
 * @param config - Optional LLM configuration
 * @returns Validated layout with assignments and metadata
 */
export declare function generateLayout(users: UserWithActivity[], roomCapacity: number, config?: Partial<LLMConfig>): Promise<ValidatedLayout>;
export * from "./types.js";
export * from "./LLMClient.js";
export * from "./PromptBuilder.js";
export * from "./Validator.js";
export * from "./FallbackLayout.js";
