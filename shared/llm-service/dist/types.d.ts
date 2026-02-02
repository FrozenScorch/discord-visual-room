/**
 * Local types for LLM Service
 */
import type { FurnitureType } from "@discord-visual-room/types";
/**
 * User with activity data for layout generation
 */
export interface UserWithActivity {
    id: string;
    username: string;
    displayName: string;
    activity?: {
        name: string;
        type: "PLAYING" | "STREAMING" | "LISTENING" | "WATCHING" | "COMPETING";
    };
}
/**
 * Furniture assignment from LLM or fallback
 */
export interface FurnitureAssignment {
    userId: string;
    furniture: FurnitureType;
    position: {
        x: number;
        y: number;
        z: number;
    };
}
/**
 * Validated layout result
 */
export interface ValidatedLayout {
    assignments: FurnitureAssignment[];
    source: "llm" | "fallback";
    validationErrors: string[];
    fallbackReason?: string;
}
/**
 * LLM response validation result
 */
export interface ValidationResult {
    valid: boolean;
    errors: string[];
    assignments?: FurnitureAssignment[];
}
/**
 * Strict asset dictionary - ONLY these furniture types are valid
 */
export declare const VALID_FURNITURE_TYPES: readonly FurnitureType[];
/**
 * Type guard for valid furniture types
 */
export declare function isValidFurnitureType(type: string): type is FurnitureType;
/**
 * LLM API configuration
 */
export interface LLMConfig {
    baseURL: string;
    timeout: number;
    maxRetries: number;
    model: string;
}
/**
 * Default LLM configuration
 */
export declare const DEFAULT_LLM_CONFIG: LLMConfig;
