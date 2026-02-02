# LLM Layout Service

Intelligent furniture layout generation for Discord Visual Room with graceful fallback to deterministic algorithms.

## Overview

This service provides AI-powered furniture assignment based on user activities, with guaranteed reliability through fallback mechanisms. It connects to a local llama.cpp server to generate layouts, but will seamlessly fall back to a deterministic grid algorithm if the LLM is unavailable or returns invalid responses.

## Features

- **LLM-Powered Layout**: Uses local llama.cpp for intelligent furniture assignment
- **Strict Validation**: Validates all furniture types against the exact Asset Dictionary
- **Graceful Degradation**: Falls back to deterministic algorithm on any failure
- **Zero Crashes**: System ALWAYS returns a valid result
- **Type Safe**: Full TypeScript support with comprehensive type definitions
- **Configurable**: Customizable timeout, retry logic, and LLM endpoint

## Installation

```bash
npm install @discord-visual-room/llm-service
```

## Quick Start

```typescript
import { generateLayout } from '@discord-visual-room/llm-service';

const users = [
  {
    id: 'user1',
    username: 'gamer123',
    displayName: 'Gamer123',
    activity: { name: 'Valorant', type: 'PLAYING' }
  },
  {
    id: 'user2',
    username: 'chillvibes',
    displayName: 'ChillVibes',
    activity: { name: 'Spotify', type: 'LISTENING' }
  }
];

const result = await generateLayout(users, 10);

console.log(result.source); // 'llm' or 'fallback'
console.log(result.assignments); // Array of furniture assignments
console.log(result.fallbackReason); // undefined if LLM succeeded
```

## Architecture

### Flow

```
1. Generate Prompt
   ↓
2. Call LLM (with timeout + retry)
   ↓
3. Validate Response
   ├─ Valid → Return LLM assignments
   └─ Invalid → Use fallback algorithm
   ↓
4. Fallback (deterministic grid)
   ↓
5. Return ValidatedLayout (ALWAYS succeeds)
```

### Asset Dictionary (Valid Furniture Types)

The system ONLY accepts these exact furniture types:

- `COMPUTER_DESK` - Competitive gaming
- `COUCH_2_SEATER` - Casual co-op
- `COUCH_SINGLE` - Solo/AFK users
- `BAR_STOOL` - Mobile/handheld games

Any other furniture type (e.g., "Beanbag Chair", "DESK", "couch") will be rejected.

## API Reference

### Main Functions

#### `generateLayout(users, roomCapacity, config?)`

Generate a furniture layout with automatic fallback.

**Parameters:**
- `users: UserWithActivity[]` - Array of users with their activities
- `roomCapacity: number` - Maximum capacity of the room
- `config?: Partial<LLMConfig>` - Optional LLM configuration

**Returns:** `Promise<ValidatedLayout>`

**Example:**
```typescript
const layout = await generateLayout(users, 10, {
  baseURL: 'http://192.168.68.62:1234',
  timeout: 5000
});
```

### Classes

#### `LLMLayoutService`

Main service class for advanced usage.

```typescript
import { LLMLayoutService } from '@discord-visual-room/llm-service';

const service = new LLMLayoutService({
  baseURL: 'http://192.168.68.62:1234',
  timeout: 5000,
  maxRetries: 1
});

const result = await service.generateLayout(users, 10);
const isHealthy = await service.healthCheck();
```

### Utility Functions

#### `buildPrompt(users)`

Build a prompt for the LLM.

```typescript
import { buildPrompt } from '@discord-visual-room/llm-service';

const prompt = buildPrompt(users);
```

#### `validateLLMResponse(response, userIds)`

Validate an LLM response.

```typescript
import { validateLLMResponse } from '@discord-visual-room/llm-service';

const result = validateLLMResponse(rawJson, expectedUserIds);
if (!result.valid) {
  console.error('Validation errors:', result.errors);
}
```

#### `generateLinearLayout(count, config?)`

Generate a deterministic fallback layout.

```typescript
import { generateLinearLayout } from '@discord-visual-room/llm-service';

const layout = generateLinearLayout(5, {
  columns: 4,
  spacing: 3,
  startX: -6,
  startZ: -6
});
```

## Types

### `ValidatedLayout`

```typescript
interface ValidatedLayout {
  assignments: FurnitureAssignment[];
  source: 'llm' | 'fallback';
  validationErrors: string[];
  fallbackReason?: string;
}
```

### `FurnitureAssignment`

```typescript
interface FurnitureAssignment {
  userId: string;
  furniture: FurnitureType;
  position: { x: number; y: number; z: number };
}
```

### `UserWithActivity`

```typescript
interface UserWithActivity {
  id: string;
  username: string;
  displayName: string;
  activity?: {
    name: string;
    type: 'PLAYING' | 'STREAMING' | 'LISTENING' | 'WATCHING' | 'COMPETING';
  };
}
```

### `LLMConfig`

```typescript
interface LLMConfig {
  baseURL: string;      // Default: 'http://192.168.68.62:1234'
  timeout: number;      // Default: 5000 (ms)
  maxRetries: number;   // Default: 1
  model: string;        // Default: 'llama.cpp'
}
```

## Error Handling

The service implements graceful degradation and never throws:

```typescript
const result = await generateLayout(users, 10);

// Check source
if (result.source === 'fallback') {
  console.warn('Used fallback:', result.fallbackReason);
}

// Always valid assignments
result.assignments.forEach(assignment => {
  console.log(`${assignment.userId} → ${assignment.furniture}`);
});
```

## Validation Rules

The service enforces strict validation:

1. **JSON Format**: Response must be valid JSON array
2. **Required Fields**: Each entry must have `userId` (string) and `furniture` (FurnitureType)
3. **Exact Types**: Furniture must EXACTLY match one of the 4 valid types
4. **Unique Assignments**: Each userId must be assigned exactly once
5. **Complete Coverage**: All expected users must have assignments

Any violation → validation error → automatic fallback

## Fallback Algorithm

The fallback uses a deterministic grid layout:

```
Column:   0    1    2    3
Row 0: [0]  [1]  [2]  [3]
Row 1: [4]  [5]  [6]  [7]
Row 2: [8]  [9] [10] [11]
```

**Formula:**
- `x = startX + (index % columns) * spacing`
- `z = startZ + (index / columns) * spacing`

**Default Config:**
- Columns: 4
- Spacing: 3 units
- Start: (-6, -6) - centered in room
- Furniture: COMPUTER_DESK (always valid)

## Testing

Run the test suite:

```bash
cd shared/llm-service
npm run build
npm run test
```

## Development

```bash
# Watch mode
npm run dev

# Build
npm run build

# Clean
npm run clean
```

## License

MIT
