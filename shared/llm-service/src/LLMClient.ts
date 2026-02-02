/**
 * HTTP Client for llama.cpp server
 * Handles connection, timeout, and retry logic
 */

import type { LLMConfig } from "./types.js";

/**
 * Error thrown when LLM request times out
 */
export class LLMTimeoutError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "LLMTimeoutError";
  }
}

/**
 * Error thrown when LLM request fails for other reasons
 */
export class LLMRequestError extends Error {
  constructor(message: string, public readonly cause?: Error) {
    super(message);
    this.name = "LLMRequestError";
  }
}

/**
 * Raw LLM API response structure
 */
interface LLMApiResponse {
  content: string;
  model: string;
  tokens_predicted?: number;
  tokens_evaluated?: number;
}

/**
 * Request payload for llama.cpp completion API
 */
interface LLMCompletionRequest {
  prompt: string;
  n_predict?: number;
  temperature?: number;
  top_p?: number;
  stop?: string[];
}

/**
 * HTTP Client for llama.cpp server
 */
export class LLMClient {
  private readonly config: LLMConfig;

  constructor(config: Partial<LLMConfig> = {}) {
    this.config = {
      ...config,
      baseURL: config.baseURL || "http://192.168.68.62:1234",
      timeout: config.timeout || 5000,
      maxRetries: config.maxRetries !== undefined ? config.maxRetries : 1,
      model: config.model || "llama.cpp"
    };
  }

  /**
   * Call LLM with prompt, timeout handling, and retry logic
   *
   * @param prompt - The prompt to send to the LLM
   * @returns The LLM's response text
   * @throws {LLMTimeoutError} If request times out after all retries
   * @throws {LLMRequestError} If request fails for other reasons
   */
  async callLLM(prompt: string): Promise<string> {
    let lastError: Error | null = null;

    for (let attempt = 0; attempt <= this.config.maxRetries; attempt++) {
      try {
        return await this.#attemptLLMCall(prompt);
      } catch (error) {
        lastError = error as Error;

        // If it's a timeout and we have retries left, try again
        if (error instanceof LLMTimeoutError && attempt < this.config.maxRetries) {
          console.warn(`LLM timeout on attempt ${attempt + 1}/${this.config.maxRetries + 1}, retrying...`);
          continue;
        }

        // For other errors or final attempt, break
        break;
      }
    }

    // All retries exhausted
    if (lastError instanceof LLMTimeoutError) {
      throw new LLMTimeoutError(
        `LLM request timed out after ${this.config.maxRetries + 1} attempts`
      );
    }

    throw new LLMRequestError(
      `LLM request failed: ${lastError?.message || "Unknown error"}`,
      lastError || undefined
    );
  }

  /**
   * Single attempt to call LLM with timeout
   */
  async #attemptLLMCall(prompt: string): Promise<string> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.config.timeout);

    try {
      const requestBody: LLMCompletionRequest = {
        prompt,
        n_predict: 2048, // Max tokens to generate
        temperature: 0.7, // Balanced creativity
        top_p: 0.9,
        stop: ["</s>"] // Stop sequence
      };

      const response = await fetch(`${this.config.baseURL}/completion`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(requestBody),
        signal: controller.signal
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data: LLMApiResponse = await response.json();

      if (!data.content || typeof data.content !== "string") {
        throw new Error("Invalid response: missing or invalid content field");
      }

      return data.content.trim();
    } catch (error) {
      if (error instanceof Error) {
        // Check for AbortError (timeout)
        if (error.name === "AbortError") {
          throw new LLMTimeoutError(
            `LLM request timed out after ${this.config.timeout}ms`
          );
        }

        // Network errors
        if (error.message.includes("ECONNREFUSED") || error.message.includes("fetch")) {
          throw new LLMRequestError(
            `Failed to connect to LLM server at ${this.config.baseURL}`,
            error
          );
        }
      }

      throw new LLMRequestError(
        `Unexpected error during LLM request: ${error instanceof Error ? error.message : "Unknown error"}`,
        error instanceof Error ? error : undefined
      );
    } finally {
      clearTimeout(timeoutId);
    }
  }

  /**
   * Check if LLM server is accessible
   *
   * @returns true if server is reachable, false otherwise
   */
  async healthCheck(): Promise<boolean> {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2000);

      const response = await fetch(`${this.config.baseURL}/health`, {
        method: "GET",
        signal: controller.signal
      });

      clearTimeout(timeoutId);
      return response.ok;
    } catch {
      return false;
    }
  }
}
