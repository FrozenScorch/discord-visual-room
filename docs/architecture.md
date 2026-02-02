# System Architecture

This document provides a comprehensive overview of the Discord Visual Room system architecture, including component interactions, data flow, technology choices, and scaling considerations.

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture Philosophy](#architecture-philosophy)
3. [Component Architecture](#component-architecture)
4. [Data Flow](#data-flow)
5. [Technology Choices](#technology-choices)
6. [Scaling Considerations](#scaling-considerations)
7. [Security Considerations](#security-considerations)

## System Overview

Discord Visual Room is a distributed system consisting of three main components:

1. **Backend Service** - Scala/Akka-based state management and Discord integration
2. **Frontend Renderer** - Three.js-based 3D visualization
3. **LLM Service** - TypeScript-based furniture layout generation

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Discord Visual Room System                    │
│                                                                       │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │
│  │   Discord    │    │   Backend    │    │  Frontend    │          │
│  │     API      │◄───│   Service    │───►│   Renderer   │          │
│  │              │    │   (Scala)    │    │  (Three.js)  │          │
│  └──────────────┘    └──────┬───────┘    └──────────────┘          │
│                             │                                         │
│                             │                                         │
│                             ▼                                         │
│                      ┌──────────────┐                               │
│                      │  LLM Service │                               │
│                      │ (TypeScript) │                               │
│                      └──────────────┘                               │
└──────────────────────────────────────────────────────────────────────┘
```

## Architecture Philosophy

### 1. Backend as Source of Truth

The frontend is a **dumb renderer** that displays whatever the backend sends. This separation of concerns provides:

- **Single source of truth** - Backend maintains authoritative state
- **Simplified frontend** - No complex state management logic
- **Easier debugging** - State issues are traced to backend
- **Consistency** - All clients see identical state

```typescript
// Frontend receives complete SceneGraph
interface SceneGraph {
  version: string;
  timestamp: number;
  users: UserNode[];
  furniture: FurnitureNode[];
  room: RoomConfig;
}
```

### 2. Elastic Environment

The 3D scene dynamically adapts to user count and activities:

- **User joins** → New furniture spawns
- **User leaves** → Unassigned furniture despawns
- **Activity changes** → Furniture may be reassigned
- **No fixed capacity** - System scales from 1 to N users

### 3. Graceful Degradation

The LLM is an **enhancement, not a dependency**:

- LLM fails → Fallback to deterministic grid layout
- LLM times out → System continues working
- LLM hallucinates → Validation rejects → Fallback
- **System NEVER crashes**

### 4. Actor Model Concurrency

The backend uses Akka Actors for:

- **Message-passing concurrency** - No shared mutable state
- **Fault tolerance** - Supervision strategies restart failed actors
- **Distributed systems ready** - Actors can be clustered
- **Backpressure handling** - Natural flow control

## Component Architecture

### Backend: Actor Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│                      RoomActor (Root)                        │
│  - Coordinates all room operations                          │
│  - Manages WebSocket subscribers                            │
│  - Broadcasts SceneGraph updates                            │
└──────┬──────────────────────────────────┬───────────────────┘
       │                                  │
       ▼                                  ▼
┌──────────────────┐            ┌──────────────────────┐
│   UserManager    │            │  FurnitureManager    │
│  - Tracks users  │            │  - Layout generation │
│  - Activities    │            │  - LLM integration   │
│  - Voice state   │            │  - Fallback logic    │
└──────────────────┘            └──────────┬───────────┘
                                           │
                                           ▼
                                  ┌─────────────────┐
                                  │   LLMClient     │
                                  │  - HTTP calls   │
                                  │  - Timeout      │
                                  │  - Retry logic  │
                                  └─────────────────┘
```

### RoomActor Responsibilities

```scala
object RoomActor {
  sealed trait Command

  // Lifecycle
  case class Initialize(config: RoomConfig, replyTo: ActorRef[InitializationResponse])

  // User events
  case class UserJoined(user: UserNode, replyTo: ActorRef[UserOperationResponse])
  case class UserLeft(userId: String, replyTo: ActorRef[UserOperationResponse])
  case class UserActivityChanged(userId: String, activity: Option[UserActivity])

  // Scene queries
  case object GetCurrentSceneGraph
  case class SubscribeToSceneUpdates(subscriber: ActorRef[SceneGraphUpdate])
}
```

### UserManager Responsibilities

- Maintains immutable list of active users
- Validates Discord user IDs (snowflake format)
- Updates user activities and speaking states
- Provides user snapshots for layout generation

### FurnitureManager Responsibilities

- Generates furniture layouts using LLM or fallback
- Validates furniture types against Asset Dictionary
- Manages furniture positions and assignments
- Releases furniture when users leave

### Frontend: Component Structure

```
┌─────────────────────────────────────────────────────────────┐
│                     Main Application                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   WSClient   │──│SceneRenderer │──│FurnitureFctry│      │
│  │              │  │              │  │              │      │
│  │ - Connect    │  │ - Three.js   │  │ - Mesh cache │      │
│  │ - Reconnect  │  │ - Camera     │  │ - Create     │      │
│  │ - Messages   │  │ - Lights     │  │ - Clone      │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Frontend Architecture Patterns

1. **Dumb Renderer Pattern**
   - No state management
   - Renders whatever SceneGraph backend sends
   - No business logic

2. **Factory Pattern**
   - FurnitureFactory creates and caches meshes
   - UserAvatar creates and manages avatar objects
   - Efficient cloning for multiple instances

3. **Observer Pattern**
   - WSClient notifies on scene updates
   - SceneRenderer updates entire scene atomically

## Data Flow

### 1. User Joins Voice Channel

```
Discord API
    │
    │ (voice state update)
    ▼
RoomActor
    │
    ├─► UserManager.TrackUser()
    │       │
    │       └─► Validate user ID
    │       └─► Add to user list
    │
    ├─► FurnitureManager.GenerateLayout()
    │       │
    │       ├─► Try LLM generation
    │       │       │
    │       │       ├─► Build prompt
    │       │       ├─► Call LLM API
    │       │       └─► Validate response
    │       │
    │       └─► If LLM fails: Fallback
    │               │
    │               └─► Generate grid layout
    │
    ├─► Generate SceneGraph
    │       │
    │       ├─► Collect users from UserManager
    │       ├─► Collect furniture from FurnitureManager
    │       └─► Assemble SceneGraph JSON
    │
    └─► Broadcast to WebSocket subscribers
            │
            └─► Frontend: WSClient.onSceneUpdate()
                    │
                    └─► SceneRenderer.updateScene()
                            │
                            ├─► Update furniture meshes
                            ├─► Update user avatars
                            └─► Render frame
```

### 2. Activity Changes (User starts game)

```
Discord API
    │
    │ (presence update)
    ▼
RoomActor
    │
    ├─► UserManager.UpdateActivity()
    │
    ├─► FurnitureManager.GenerateLayout()
    │       │
    │       └─► LLM may reassign furniture
    │           based on new activity type
    │
    └─► Broadcast SceneGraph update
            │
            └─► Frontend updates visualization
```

### 3. LLM Layout Generation Flow

```
FurnitureManager
    │
    │ GenerateLayout(users, room)
    ▼
LLMClient
    │
    ├─► Build prompt with users & activities
    │
    ├─► HTTP POST to llama.cpp
    │       │
    │       ├─► Timeout: 5 seconds
    │       ├─► Max retries: 3
    │       └─► Error: Throw LLMTimeoutError
    │
    └─► Parse response
            │
            └─► Validator.validateLLMResponse()
                    │
                    ├─► Check JSON format
                    ├─► Check all users assigned
                    ├─► Check furniture types valid
                    │
                    ├─► Valid: Return assignments
                    │
                    └─► Invalid: Return errors
                            │
                            └─► FallbackLayout.generateLinearLayout()
                                    │
                                    └─► Return deterministic layout
```

### 4. WebSocket Message Flow

```
Backend                               Frontend
    │                                    │
    │ SCENE_UPDATE (SceneGraph JSON)     │
    ├───────────────────────────────────►│
    │                                    │ WSClient.handleMessage()
    │                                    │   │
    │                                    │   └─► Parse JSON
    │                                    │       │
    │                                    │       └─► onSceneUpdate()
    │                                    │           │
    │                                    │           └─► updateScene()
    │                                    │               │
    │                                    │               ├─► Clear old objects
    │                                    │               ├─► Create new furniture
    │                                    │               ├─► Update users
    │                                    │               └─► Render
    │                                    │
    │ <── Ready for next update ─────────┤
```

## Technology Choices

### Backend: Scala + Akka

**Why Scala?**
- **Type safety** - Compile-time guarantees prevent runtime errors
- **Immutable data structures** - Default immutability prevents bugs
- **Pattern matching** - Elegant handling of complex message types
- **Functional programming** - Concise, maintainable code

**Why Akka Actors?**
- **Message-passing concurrency** - No race conditions, deadlocks
- **Fault tolerance** - Supervision strategies recover from failures
- **Distributed systems** - Akka Cluster for horizontal scaling
- **Backpressure** - Natural flow control prevents overload

**Why discorde4j?**
- **Reactive Streams** - Non-blocking Discord API calls
- **Type-safe** - Scala wrapper around Discord API
- **Gateway support** - Real-time voice and presence updates

### Frontend: Three.js + TypeScript

**Why Three.js?**
- **Mature ecosystem** - Well-documented, widely used
- **Performance** - WebGL-accelerated rendering
- **Flexibility** - Full control over 3D scene

**Why TypeScript?**
- **Type safety** - Catches errors at compile time
- **Shared types** - Backend types can be reused via ts2scala
- **Developer experience** - Better IDE support

### LLM Service: TypeScript

**Why separate service?**
- **Reusable** - Can be used by other projects
- **Testable** - Pure functions, easy to test
- **Maintainable** - Clear API boundaries

**Why llama.cpp?**
- **Local execution** - No API costs, no rate limits
- **Privacy** - Data never leaves network
- **Performance** - Optimized for CPU inference

## Scaling Considerations

### Vertical Scaling

**Current single-server deployment supports:**
- 10-50 concurrent voice channel users
- 100-200 WebSocket clients
- ~100 LLM requests per minute

**Bottlenecks:**
- CPU: LLM inference (local llama.cpp)
- Memory: Actor state, Three.js scene graphs
- Network: WebSocket message throughput

### Horizontal Scaling

**To scale to multiple rooms/servers:**

1. **Akka Cluster** - Distribute actors across nodes
   ```
   Node 1              Node 2
   ┌────────────┐    ┌────────────┐
   │ RoomActor  │    │ RoomActor  │
   │ UserManager│    │ UserManager│
   └────────────┘    └────────────┘
         │                  │
         └──────┬───────────┘
                ▼
         Shared State Store
   ```

2. **Load Balancer** - Distribute WebSocket connections
   ```
         Load Balancer
                │
       ┌────────┼────────┐
       │        │        │
   Backend 1 Backend 2 Backend 3
   ```

3. **Message Queue** - Broadcast updates to all nodes
   ```
   Publisher → Message Queue (Redis/Kafka)
                │
       ┌────────┼────────┐
       │        │        │
   Subscriber Subscr Subscr
   ```

### Performance Optimization

**Backend optimizations:**
- Batch WebSocket updates (send max 10fps)
- Use actor message batching
- Implement delta updates (send only changes)
- Cache LLM responses for similar user sets

**Frontend optimizations:**
- Use object pooling for frequently spawned objects
- Implement LOD (Level of Detail) for distant objects
- Use instanced rendering for repeated meshes
- Throttle render loop to 60fps max

## Security Considerations

### Discord Token Security

- **Never commit Discord bot token** to repository
- Use environment variables: `DISCORD_BOT_TOKEN`
- Rotate tokens regularly
- Use least-privilege bot permissions

### WebSocket Security

- **Implement authentication** (future)
  - Validate Discord user identity
  - Prevent unauthorized connections
- **Rate limiting** - Prevent abuse
- **Input validation** - Sanitize all messages

### LLM Server Security

- **Network isolation** - LLM server on internal network
- **No external API calls** - Prevent prompt injection attacks
- **Response validation** - Strict furniture type checking

### Data Privacy

- **No logging of user data** beyond Discord IDs
- **Avatar URLs** - Used directly from Discord CDN
- **Voice data** - Only presence state, not audio

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Production Server                        │
│  (192.168.68.56)                                            │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Docker Host Network                     │  │
│  │                                                       │  │
│  │  ┌──────────────┐        ┌──────────────┐          │  │
│  │  │   Backend    │        │   Frontend   │          │  │
│  │  │  Container   │        │   Container  │          │  │
│  │  │  :8080 (WS)  │        │   :8000 (HTTP)│          │  │
│  │  └──────────────┘        └──────────────┘          │  │
│  │                                                       │  │
│  │         │                              │              │  │
│  │         └──────────────┬───────────────┘              │  │
│  │                        │                              │  │
│  └────────────────────────┼──────────────────────────────┘  │
│                           │                                  │
└───────────────────────────┼──────────────────────────────────┘
                            │
                            │ HTTP
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              LLM Server (Windows PC)                         │
│  (192.168.68.62:1234)                                        │
│                                                               │
│  ┌──────────────┐                                            │
│  │ llama.cpp    │                                            │
│  │ HTTP Server  │                                            │
│  └──────────────┘                                            │
└─────────────────────────────────────────────────────────────┘
```

---

**Related Documentation:**
- [Backend Implementation](backend.md)
- [Frontend Implementation](frontend.md)
- [LLM Service](llm-service.md)
- [Deployment Guide](deployment.md)
