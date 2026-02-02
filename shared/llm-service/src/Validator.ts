/**
 * Response Parser & Validator for LLM responses
 * Validates against Asset Dictionary with strict type checking
 */

import type { ValidationResult, FurnitureAssignment } from "./types.js";
import { VALID_FURNITURE_TYPES, isValidFurnitureType } from "./types.js";

/**
 * Parses and validates LLM JSON response
 *
 * @param response - Raw JSON string from LLM
 * @param userIds - Expected user IDs that must be assigned
 * @returns Validation result with errors or valid assignments
 */
export function validateLLMResponse(
  response: string,
  userIds: string[]
): ValidationResult {
  const errors: string[] = [];
  const seenUserIds = new Set<string>();
  const assignments: FurnitureAssignment[] = [];

  // Clean response - remove markdown code blocks if present
  const cleanedResponse = response
    .replace(/^```json\s*/i, "")
    .replace(/^```\s*/i, "")
    .replace(/```\s*$/i, "")
    .trim();

  // Step 1: Check if response is empty
  if (!cleanedResponse) {
    return {
      valid: false,
      errors: ["Empty response from LLM"]
    };
  }

  // Step 2: Parse JSON
  let parsed: unknown;
  try {
    parsed = JSON.parse(cleanedResponse);
  } catch (error) {
    return {
      valid: false,
      errors: [`Invalid JSON: ${error instanceof Error ? error.message : "Parse error"}`]
    };
  }

  // Step 3: Validate it's an array
  if (!Array.isArray(parsed)) {
    return {
      valid: false,
      errors: [`Response is not an array, got: ${typeof parsed}`]
    };
  }

  // Step 4: Validate each assignment
  const userIdSet = new Set(userIds);

  for (let i = 0; i < parsed.length; i++) {
    const item = parsed[i];

    // Check if item is an object
    if (typeof item !== "object" || item === null) {
      errors.push(`Assignment ${i} is not an object`);
      continue;
    }

    // Check userId field
    if (!("userId" in item) || typeof item.userId !== "string") {
      errors.push(`Assignment ${i} missing or invalid userId field`);
      continue;
    }

    const userId = item.userId as string;

    // Check if userId is in expected list
    if (!userIdSet.has(userId)) {
      errors.push(`Assignment ${i} has unknown userId: ${userId}`);
      continue;
    }

    // Check for duplicate userIds
    if (seenUserIds.has(userId)) {
      errors.push(`Duplicate userId: ${userId}`);
      continue;
    }
    seenUserIds.add(userId);

    // Check furniture field
    if (!("furniture" in item) || typeof item.furniture !== "string") {
      errors.push(`Assignment ${i} missing or invalid furniture field`);
      continue;
    }

    const furniture = item.furniture as string;

    // Validate furniture type against Asset Dictionary
    if (!isValidFurnitureType(furniture)) {
      errors.push(
        `Assignment ${i} has invalid furniture type: "${furniture}". ` +
        `Valid types are: ${VALID_FURNITURE_TYPES.join(", ")}`
      );
      continue;
    }

    // Valid assignment - add to result
    assignments.push({
      userId,
      furniture: furniture,
      position: { x: 0, y: 0, z: 0 } // Will be set by layout algorithm
    });
  }

  // Step 5: Check that all expected users were assigned
  const missingUsers = userIds.filter(id => !seenUserIds.has(id));
  if (missingUsers.length > 0) {
    errors.push(`Missing assignments for users: ${missingUsers.join(", ")}`);
  }

  // Return result
  if (errors.length > 0) {
    return {
      valid: false,
      errors
    };
  }

  return {
    valid: true,
    errors: [],
    assignments
  };
}

/**
 * Validates that all furniture types in assignments are valid
 *
 * @param assignments - Array of furniture assignments
 * @returns Validation result
 */
export function validateFurnitureTypes(
  assignments: Array<{ furniture: string }>
): ValidationResult {
  const errors: string[] = [];

  for (let i = 0; i < assignments.length; i++) {
    const { furniture } = assignments[i];

    if (!isValidFurnitureType(furniture)) {
      errors.push(
        `Assignment ${i} has invalid furniture type: "${furniture}". ` +
        `Valid types are: ${VALID_FURNITURE_TYPES.join(", ")}`
      );
    }
  }

  return {
    valid: errors.length === 0,
    errors
  };
}

/**
 * Checks if a response looks like it might contain JSON but needs cleaning
 *
 * @param response - Raw response from LLM
 * @returns true if response appears to need cleaning
 */
export function needsCleaning(response: string): boolean {
  const trimmed = response.trim();
  return (
    trimmed.startsWith("```") ||
    trimmed.includes("```json") ||
    trimmed.includes("```JSON")
  );
}

/**
 * Attempts to extract JSON from a response that might have extra text
 *
 * @param response - Raw response from LLM
 * @returns Extracted JSON string or null if not found
 */
export function extractJSON(response: string): string | null {
  // Try to find JSON array pattern
  const arrayMatch = response.match(/\[\s*\{.*\}\s*\]/s);
  if (arrayMatch) {
    return arrayMatch[0];
  }

  // Try to find code block
  const codeBlockMatch = response.match(/```(?:json)?\s*(\[[\s\S]*?\])\s*```/);
  if (codeBlockMatch) {
    return codeBlockMatch[1];
  }

  return null;
}
