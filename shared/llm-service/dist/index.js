"use strict";
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
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __exportStar = (this && this.__exportStar) || function(m, exports) {
    for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports, p)) __createBinding(exports, m, p);
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _LLMLayoutService_instances, _LLMLayoutService_tryLLMGeneration, _LLMLayoutService_generateFallbackLayout;
Object.defineProperty(exports, "__esModule", { value: true });
exports.LLMLayoutService = void 0;
exports.generateLayout = generateLayout;
const types_js_1 = require("./types.js");
const LLMClient_js_1 = require("./LLMClient.js");
const PromptBuilder_js_1 = require("./PromptBuilder.js");
const Validator_js_1 = require("./Validator.js");
const FallbackLayout_js_1 = require("./FallbackLayout.js");
/**
 * Main service for generating layouts with LLM and fallback support
 */
class LLMLayoutService {
    constructor(config = {}) {
        _LLMLayoutService_instances.add(this);
        this.config = { ...types_js_1.DEFAULT_LLM_CONFIG, ...config };
        this.client = new LLMClient_js_1.LLMClient(this.config);
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
    async generateLayout(users, roomCapacity) {
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
            const llmResult = await __classPrivateFieldGet(this, _LLMLayoutService_instances, "m", _LLMLayoutService_tryLLMGeneration).call(this, users);
            if (llmResult.valid) {
                return {
                    assignments: llmResult.assignments,
                    source: "llm",
                    validationErrors: []
                };
            }
            // LLM returned invalid response - use fallback
            console.warn(`LLM validation failed: ${llmResult.errors.join(", ")}`);
            return __classPrivateFieldGet(this, _LLMLayoutService_instances, "m", _LLMLayoutService_generateFallbackLayout).call(this, users, `LLM validation failed: ${llmResult.errors.join("; ")}`);
        }
        catch (error) {
            // LLM failed completely - use fallback
            const reason = error instanceof LLMClient_js_1.LLMTimeoutError
                ? "LLM request timed out"
                : error instanceof LLMClient_js_1.LLMRequestError
                    ? `LLM request failed: ${error.message}`
                    : `Unexpected error: ${error instanceof Error ? error.message : "Unknown"}`;
            console.warn(`LLM generation failed: ${reason}`);
            return __classPrivateFieldGet(this, _LLMLayoutService_instances, "m", _LLMLayoutService_generateFallbackLayout).call(this, users, reason);
        }
    }
    /**
     * Check if the LLM server is healthy
     */
    async healthCheck() {
        return this.client.healthCheck();
    }
    /**
     * Get the current configuration
     */
    getConfig() {
        return { ...this.config };
    }
}
exports.LLMLayoutService = LLMLayoutService;
_LLMLayoutService_instances = new WeakSet(), _LLMLayoutService_tryLLMGeneration = 
/**
 * Attempt to generate layout using LLM
 */
async function _LLMLayoutService_tryLLMGeneration(users) {
    // Build prompt
    const prompt = (0, PromptBuilder_js_1.buildPrompt)(users);
    const userIds = users.map(u => u.id);
    // Call LLM
    const response = await this.client.callLLM(prompt);
    // Validate response
    const validationResult = (0, Validator_js_1.validateLLMResponse)(response, userIds);
    if (!validationResult.valid) {
        return {
            valid: false,
            errors: validationResult.errors
        };
    }
    // Add positions to assignments
    const assignmentsWithPositions = (0, FallbackLayout_js_1.assignPositions)(validationResult.assignments);
    return {
        valid: true,
        assignments: assignmentsWithPositions,
        errors: []
    };
}, _LLMLayoutService_generateFallbackLayout = function _LLMLayoutService_generateFallbackLayout(users, reason) {
    const baseLayout = (0, FallbackLayout_js_1.generateLinearLayout)(users.length);
    // Map actual user IDs to the layout
    const assignments = baseLayout.map((item, index) => ({
        ...item,
        userId: users[index].id
    }));
    return {
        assignments,
        source: "fallback",
        validationErrors: [],
        fallbackReason: reason
    };
};
/**
 * Convenience function to generate a layout without instantiating the service
 *
 * @param users - Array of users with their activities
 * @param roomCapacity - Maximum capacity of the room
 * @param config - Optional LLM configuration
 * @returns Validated layout with assignments and metadata
 */
async function generateLayout(users, roomCapacity, config) {
    const service = new LLMLayoutService(config);
    return service.generateLayout(users, roomCapacity);
}
// Export all types and utilities
__exportStar(require("./types.js"), exports);
__exportStar(require("./LLMClient.js"), exports);
__exportStar(require("./PromptBuilder.js"), exports);
__exportStar(require("./Validator.js"), exports);
__exportStar(require("./FallbackLayout.js"), exports);
