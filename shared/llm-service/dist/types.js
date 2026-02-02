"use strict";
/**
 * Local types for LLM Service
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.DEFAULT_LLM_CONFIG = exports.VALID_FURNITURE_TYPES = void 0;
exports.isValidFurnitureType = isValidFurnitureType;
/**
 * Strict asset dictionary - ONLY these furniture types are valid
 */
exports.VALID_FURNITURE_TYPES = [
    "COMPUTER_DESK",
    "COUCH_2_SEATER",
    "COUCH_SINGLE",
    "BAR_STOOL"
];
/**
 * Type guard for valid furniture types
 */
function isValidFurnitureType(type) {
    return exports.VALID_FURNITURE_TYPES.includes(type);
}
/**
 * Default LLM configuration
 */
exports.DEFAULT_LLM_CONFIG = {
    baseURL: "http://192.168.68.62:1234",
    timeout: 5000, // 5 seconds
    maxRetries: 1,
    model: "llama.cpp"
};
