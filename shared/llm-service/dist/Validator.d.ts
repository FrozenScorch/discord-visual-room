/**
 * Response Parser & Validator for LLM responses
 * Validates against Asset Dictionary with strict type checking
 */
import type { ValidationResult } from "./types.js";
/**
 * Parses and validates LLM JSON response
 *
 * @param response - Raw JSON string from LLM
 * @param userIds - Expected user IDs that must be assigned
 * @returns Validation result with errors or valid assignments
 */
export declare function validateLLMResponse(response: string, userIds: string[]): ValidationResult;
/**
 * Validates that all furniture types in assignments are valid
 *
 * @param assignments - Array of furniture assignments
 * @returns Validation result
 */
export declare function validateFurnitureTypes(assignments: Array<{
    furniture: string;
}>): ValidationResult;
/**
 * Checks if a response looks like it might contain JSON but needs cleaning
 *
 * @param response - Raw response from LLM
 * @returns true if response appears to need cleaning
 */
export declare function needsCleaning(response: string): boolean;
/**
 * Attempts to extract JSON from a response that might have extra text
 *
 * @param response - Raw response from LLM
 * @returns Extracted JSON string or null if not found
 */
export declare function extractJSON(response: string): string | null;
