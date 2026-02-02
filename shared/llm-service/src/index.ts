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

import type { UserWithActivity, ValidatedLayout, FurnitureAssignment } from "./types.js";
import type { LLMConfig } from "./types.js";
import { DEFAULT_LLM_CONFIG } from "./types.js";
import { LLMClient, LLMTimeoutError, LLMRequestError } from "./LLMClient.js";
import { buildPrompt } from "./PromptBuilder.js";
import { validateLLMResponse } from "./Validator.js";
import { generateLinearLayout, assignPositions } from "./FallbackLayout.js";

/**
 * Main service for generating layouts with LLM and fallback support
 */
export class LLMLayoutService {
  private readonly client: LLMClient;
  private readonly config: LLMConfig;

  constructor(config: Partial<LLMConfig> = {}) {
    this.config = { ...DEFAULT_LLM_CONFIG, ...config };
    this.client = new LLMClient(this.config);
  }

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
  async generateLayout(
    users: UserWithActivity[],
    roomCapacity: number
  ): Promise<ValidatedLayout> {
    // Edge case: no users
    if (users.length === 0) {
      return {
        assignments: [],
        source: "fallback",
        validationErrors: [],
        fallbackReason: "No users to place"
      };
    }

    // Step 1: Try LLM generation
    try {
      const llmResult = await this.#tryLLMGeneration(users);

      if (llmResult.valid) {
        return {
          assignments: llmResult.assignments!,
          source: "llm",
          validationErrors: []
        };
      }

      // LLM returned invalid response - use fallback
      console.warn(`LLM validation failed: ${llmResult.errors.join(", ")}`);
      return this.#generateFallbackLayout(users, `LLM validation failed: ${llmResult.errors.join("; ")}`);
    } catch (error) {
      // LLM failed completely - use fallback
      const reason = error instanceof LLMTimeoutError
        ? "LLM request timed out"
        : error instanceof LLMRequestError
        ? `LLM request failed: ${error.message}`
        : `Unexpected error: ${error instanceof Error ? error.message : "Unknown"}`;

      console.warn(`LLM generation failed: ${reason}`);
      return this.#generateFallbackLayout(users, reason);
    }
  }

  /**
   * Attempt to generate layout using LLM
   */
  async #tryLLMGeneration(users: UserWithActivity[]): Promise<{
    valid: boolean;
    assignments?: FurnitureAssignment[];
    errors: string[];
  }> {
    // Build prompt
    const prompt = buildPrompt(users);
    const userIds = users.map(u => u.id);

    // Call LLM
    const response = await this.client.callLLM(prompt);

    // Validate response
    const validationResult = validateLLMResponse(response, userIds);

    if (!validationResult.valid) {
      return {
        valid: false,
        errors: validationResult.errors
      };
    }

    // Add positions to assignments
    const assignmentsWithPositions = assignPositions(validationResult.assignments!);

    return {
      valid: true,
      assignments: assignmentsWithPositions,
      errors: []
    };
  }

  /**
   * Generate fallback layout (always succeeds)
   */
  #generateFallbackLayout(
    users: UserWithActivity[],
    reason: string
  ): ValidatedLayout {
    const baseLayout = generateLinearLayout(users.length);

    // Map actual user IDs to the layout
    const assignments: FurnitureAssignment[] = baseLayout.map((item, index) => ({
      ...item,
      userId: users[index].id
    }));

    return {
      assignments,
      source: "fallback",
      validationErrors: [],
      fallbackReason: reason
    };
  }

  /**
   * Check if the LLM server is healthy
   */
  async healthCheck(): Promise<boolean> {
    return this.client.healthCheck();
  }

  /**
   * Get the current configuration
   */
  getConfig(): LLMConfig {
    return { ...this.config };
  }
}

/**
 * Convenience function to generate a layout without instantiating the service
 *
 * @param users - Array of users with their activities
 * @param roomCapacity - Maximum capacity of the room
 * @param config - Optional LLM configuration
 * @returns Validated layout with assignments and metadata
 */
export async function generateLayout(
  users: UserWithActivity[],
  roomCapacity: number,
  config?: Partial<LLMConfig>
): Promise<ValidatedLayout> {
  const service = new LLMLayoutService(config);
  return service.generateLayout(users, roomCapacity);
}

// Export all types and utilities
export * from "./types.js";
export * from "./LLMClient.js";
export * from "./PromptBuilder.js";
export * from "./Validator.js";
export * from "./FallbackLayout.js";
