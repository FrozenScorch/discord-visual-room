"use strict";
/**
 * HTTP Client for llama.cpp server
 * Handles connection, timeout, and retry logic
 */
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _LLMClient_instances, _LLMClient_attemptLLMCall;
Object.defineProperty(exports, "__esModule", { value: true });
exports.LLMClient = exports.LLMRequestError = exports.LLMTimeoutError = void 0;
/**
 * Error thrown when LLM request times out
 */
class LLMTimeoutError extends Error {
    constructor(message) {
        super(message);
        this.name = "LLMTimeoutError";
    }
}
exports.LLMTimeoutError = LLMTimeoutError;
/**
 * Error thrown when LLM request fails for other reasons
 */
class LLMRequestError extends Error {
    constructor(message, cause) {
        super(message);
        this.cause = cause;
        this.name = "LLMRequestError";
    }
}
exports.LLMRequestError = LLMRequestError;
/**
 * HTTP Client for llama.cpp server
 */
class LLMClient {
    constructor(config = {}) {
        _LLMClient_instances.add(this);
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
    async callLLM(prompt) {
        let lastError = null;
        for (let attempt = 0; attempt <= this.config.maxRetries; attempt++) {
            try {
                return await __classPrivateFieldGet(this, _LLMClient_instances, "m", _LLMClient_attemptLLMCall).call(this, prompt);
            }
            catch (error) {
                lastError = error;
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
            throw new LLMTimeoutError(`LLM request timed out after ${this.config.maxRetries + 1} attempts`);
        }
        throw new LLMRequestError(`LLM request failed: ${lastError?.message || "Unknown error"}`, lastError || undefined);
    }
    /**
     * Check if LLM server is accessible
     *
     * @returns true if server is reachable, false otherwise
     */
    async healthCheck() {
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 2000);
            const response = await fetch(`${this.config.baseURL}/health`, {
                method: "GET",
                signal: controller.signal
            });
            clearTimeout(timeoutId);
            return response.ok;
        }
        catch {
            return false;
        }
    }
}
exports.LLMClient = LLMClient;
_LLMClient_instances = new WeakSet(), _LLMClient_attemptLLMCall = 
/**
 * Single attempt to call LLM with timeout
 */
async function _LLMClient_attemptLLMCall(prompt) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.config.timeout);
    try {
        const requestBody = {
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
        const data = await response.json();
        if (!data.content || typeof data.content !== "string") {
            throw new Error("Invalid response: missing or invalid content field");
        }
        return data.content.trim();
    }
    catch (error) {
        if (error instanceof Error) {
            // Check for AbortError (timeout)
            if (error.name === "AbortError") {
                throw new LLMTimeoutError(`LLM request timed out after ${this.config.timeout}ms`);
            }
            // Network errors
            if (error.message.includes("ECONNREFUSED") || error.message.includes("fetch")) {
                throw new LLMRequestError(`Failed to connect to LLM server at ${this.config.baseURL}`, error);
            }
        }
        throw new LLMRequestError(`Unexpected error during LLM request: ${error instanceof Error ? error.message : "Unknown error"}`, error instanceof Error ? error : undefined);
    }
    finally {
        clearTimeout(timeoutId);
    }
};
