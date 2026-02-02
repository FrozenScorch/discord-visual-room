/**
 * Prompt Builder for LLM layout generation
 * Constructs prompts with strict furniture type constraints
 */

import type { UserWithActivity } from "./types.js";
import { VALID_FURNITURE_TYPES } from "./types.js";

/**
 * Builds a prompt for the LLM to generate furniture assignments
 *
 * @param users - Array of users with their activities
 * @returns A formatted prompt string for the LLM
 */
export function buildPrompt(users: UserWithActivity[]): string {
  const userDataJson = JSON.stringify(
    users.map(u => ({
      id: u.id,
      username: u.username,
      displayName: u.displayName,
      activity: u.activity
    })),
    null,
    2
  );

  return `You are a furniture layout assistant. Given these users and their activities, assign ONE furniture type from the EXACT list below to each user.

VALID FURNITURE TYPES (use ONLY these):
- COMPUTER_DESK: For competitive gaming
- COUCH_2_SEATER: For casual co-op games
- COUCH_SINGLE: For solo/AFK users
- BAR_STOOL: For mobile/handheld games

CRITICAL RULES:
1. Use ONLY the exact furniture types listed above
2. Each user must be assigned exactly ONE furniture type
3. Every userId in the input must appear exactly once in your output
4. Return ONLY a valid JSON array - no explanations, no markdown formatting
5. Do NOT invent new furniture types or use variations (e.g., "DESK", "couch", "Beanbag Chair")

Users and activities:
${userDataJson}

Return ONLY valid JSON array of assignments (no markdown code blocks):
[{"userId": "...", "furniture": "COMPUTER_DESK"}, ...]`;
}

/**
 * Builds a simplified prompt for quick testing
 *
 * @param users - Array of users with their activities
 * @returns A simplified prompt string
 */
export function buildSimplePrompt(users: UserWithActivity[]): string {
  const userList = users
    .map((u, i) => `${i + 1}. ${u.displayName}${u.activity ? ` (${u.activity.name})` : ""}`)
    .join("\n");

  return `Assign furniture types to these users:

${userList}

Valid types: COMPUTER_DESK, COUCH_2_SEATER, COUCH_SINGLE, BAR_STOOL

Return JSON array: [{"userId": "${users[0]?.id || ""}", "furniture": "COMPUTER_DESK"}, ...]`;
}

/**
 * Extracts user activity summary for prompt context
 *
 * @param users - Array of users with their activities
 * @returns A summary of activities in the room
 */
export function getActivitySummary(users: UserWithActivity[]): string {
  const activities = new Map<string, number>();

  for (const user of users) {
    if (user.activity) {
      const key = user.activity.name || "Unknown";
      activities.set(key, (activities.get(key) || 0) + 1);
    }
  }

  if (activities.size === 0) {
    return "No active activities";
  }

  return Array.from(activities.entries())
    .map(([name, count]) => `${count}x ${name}`)
    .join(", ");
}
