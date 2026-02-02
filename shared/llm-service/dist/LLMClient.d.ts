/**
 * HTTP Client for llama.cpp server
 * Handles connection, timeout, and retry logic
 */
import type { LLMConfig } from "./types.js";
/**
 * Error thrown when LLM request times out
 */
export declare class LLMTimeoutError extends Error {
    constructor(message: string);
}
/**
 * Error thrown when LLM request fails for other reasons
 */
export declare class LLMRequestError extends Error {
    readonly cause?: Error | undefined;
    constructor(message: string, cause?: Error | undefined);
}
/**
 * HTTP Client for llama.cpp server
 */
export declare class LLMClient {
    #private;
    private readonly config;
    constructor(config?: Partial<LLMConfig>);
    /**
     * Call LLM with prompt, timeout handling, and retry logic
     *
     * @param prompt - The prompt to send to the LLM
     * @returns The LLM's response text
     * @throws {LLMTimeoutError} If request times out after all retries
     * @throws {LLMRequestError} If request fails for other reasons
     */
    callLLM(prompt: string): Promise<string>;
    /**
     * Check if LLM server is accessible
     *
     * @returns true if server is reachable, false otherwise
     */
    healthCheck(): Promise<boolean>;
}
