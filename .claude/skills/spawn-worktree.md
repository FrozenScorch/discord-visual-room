---
description: Spawn a parallel Claude session in a new git worktree for isolated work
---

# Spawn Parallel Worktree

You are an orchestrator. When you identify opportunities for parallel work, spawn a new Claude session in a git worktree.

## When to Use

**Spawn a worktree when:**
- Two or more **completely independent** features need to be implemented
- Features have **no shared files** or dependencies
- The user has **sufficient API quota** for multiple sessions
- Each task would take **>15 minutes** of work
- The user **agrees** to parallel work

**DO NOT spawn when:**
- Tasks share files (will cause merge conflicts)
- Small quick fixes (< 15 min)
- One task depends on the other
- User hasn't agreed to parallel work

## Process

### 1. Confirm First
Always ask the user before spawning:
```
"I've identified two independent tasks that can be done in parallel:
  - Main session: [describe task A]
  - Parallel session: [describe task B]

Should I spawn a worktree for task B so we can work simultaneously?"
```

### 2. Spawn the Worktree
```powershell
.claude/scripts/spawn-claude-worktree.ps1 -Name "feature-name" -Task "Detailed task description with all context"
```

### 3. Report to User
After spawning, tell the user:
```
"✅ Spawned parallel session for [feature]
   - Worktree: ../worktrees/feature-name
   - Branch: feat/feature-name
   - Session ID: [id]

A new VS Code window has opened. In that window, run:
   claude --dangerously-skip-permissions

Then share the task from .claude/session-task.md

I'll continue working on [remaining task] here."
```

### 4. Continue Working
Stay in the main session and continue with your assigned task. Check on the spawned session periodically by asking the user for updates.

## Naming Convention

Use descriptive, kebab-case names:
- ✅ `user-auth-oauth`
- ✅ `payment-stripe-integration`
- ✅ `admin-dashboard-users`
- ❌ `feature1`
- ❌ `stuff`
- ❌ `temp-branch`

## Task Context

When spawning, include **all relevant context** in the task parameter:
- What needs to be built
- Existing patterns to follow
- Files to reference
- Testing requirements
- Acceptance criteria

## After Completion

When a spawned session is complete, instruct the user:
```
"Great work on [feature]! To merge it back:
   .claude/scripts/merge-worktree.ps1 -Name 'feature-name'"

This will merge the branch to main and clean up the worktree."
```

## Tracking

To see all active worktrees:
```powershell
.claude/scripts/list-worktrees.ps1
```

This shows all spawned sessions with their status and latest commits.
