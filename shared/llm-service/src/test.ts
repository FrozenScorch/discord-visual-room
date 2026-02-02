/**
 * Test suite for LLM Service
 * Demonstrates usage and validates functionality
 */

import { LLMLayoutService, generateLayout, generateLinearLayout, buildPrompt, validateLLMResponse } from "./index.js";
import type { UserWithActivity } from "./types.js";

/**
 * Test data sample users
 */
const testUsers: UserWithActivity[] = [
  {
    id: "user1",
    username: "gamer123",
    displayName: "Gamer123",
    activity: {
      name: "Valorant",
      type: "PLAYING"
    }
  },
  {
    id: "user2",
    username: "chillvibes",
    displayName: "ChillVibes",
    activity: {
      name: "Spotify",
      type: "LISTENING"
    }
  },
  {
    id: "user3",
    username: "mobilegamer",
    displayName: "MobileGamer",
    activity: {
      name: "Pokemon GO",
      type: "PLAYING"
    }
  }
];

/**
 * Test the complete flow
 */
export async function testLLMService() {
  console.log("=== LLM Layout Service Test ===\n");

  // Test 1: Generate layout with LLM (will fallback if server unavailable)
  console.log("Test 1: Generate layout (with LLM + fallback)");
  try {
    const result = await generateLayout(testUsers, 10);
    console.log(`Source: ${result.source}`);
    console.log(`Assignments: ${result.assignments.length}`);
    console.log(`Fallback reason: ${result.fallbackReason || "N/A"}\n`);

    result.assignments.forEach((assignment, i) => {
      console.log(`  ${i + 1}. User: ${assignment.userId}, Furniture: ${assignment.furniture}, Position: (${assignment.position.x}, ${assignment.position.z})`);
    });
  } catch (error) {
    console.error("Test failed:", error);
  }

  // Test 2: Fallback layout only
  console.log("\nTest 2: Fallback layout (deterministic)");
  const fallbackLayout = generateLinearLayout(5);
  fallbackLayout.forEach((item, i) => {
    console.log(`  ${i + 1}. Position: (${item.position.x}, ${item.position.z}) - ${item.furniture}`);
  });

  // Test 3: Prompt building
  console.log("\nTest 3: Prompt building");
  const prompt = buildPrompt(testUsers);
  console.log(`Prompt length: ${prompt.length} characters`);
  console.log(`Prompt preview:\n${prompt.substring(0, 300)}...\n`);

  // Test 4: Validation
  console.log("Test 4: Response validation");
  const validResponse = JSON.stringify([
    { userId: "user1", furniture: "COMPUTER_DESK" },
    { userId: "user2", furniture: "COUCH_SINGLE" },
    { userId: "user3", furniture: "BAR_STOOL" }
  ]);
  const validResult = validateLLMResponse(validResponse, ["user1", "user2", "user3"]);
  console.log(`Valid response: ${validResult.valid}`);

  const invalidResponse = JSON.stringify([
    { userId: "user1", furniture: "INVALID_DESK" },
    { userId: "user2", furniture: "COUCH_SINGLE" }
  ]);
  const invalidResult = validateLLMResponse(invalidResponse, ["user1", "user2", "user3"]);
  console.log(`Invalid response: ${invalidResult.valid}`);
  console.log(`Errors: ${invalidResult.errors.join(", ")}\n`);

  // Test 5: Health check
  console.log("Test 5: LLM server health check");
  const service = new LLMLayoutService();
  const isHealthy = await service.healthCheck();
  console.log(`Server healthy: ${isHealthy}\n`);

  console.log("=== Tests Complete ===");
}

/**
 * Run tests if this file is executed directly
 */
if (import.meta.url === `file://${process.argv[1]}`) {
  testLLMService().catch(console.error);
}
