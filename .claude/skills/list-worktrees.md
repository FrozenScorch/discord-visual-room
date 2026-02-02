---
description: List all active Claude worktree sessions and their status
---

# List Active Worktrees

Show all spawned Claude sessions with their status, branches, and latest commits.

## Usage

```powershell
.claude/scripts/list-worktrees.ps1
```

## What It Shows

For each active session:
- Session name
- Branch name
- Worktree path
- Status (active/completed/blocked)
- Latest commit (if available)
- Task preview

## When to Use

- Before spawning new worktrees (to see what's already active)
- When checking on parallel session progress
- Before merging (to verify which sessions are complete)

## Example Output

```
🌳 Active Claude Worktree Sessions
==================================================

  📌 user-auth-oauth
     Branch: feat/user-auth-oauth
     Path:   C:\Users\...\worktrees\user-auth-oauth
     Status: active
     Latest: a1b2c3d Add OAuth login page

  📌 payment-stripe
     Branch: feat/payment-stripe
     Path:   C:\Users\...\worktrees\payment-stripe
     Status: active
     Latest: e5f6g7h Integrate Stripe checkout

Commands:
  Merge a worktree:  .claude/scripts/merge-worktree.ps1 -Name <name>
  Cleanup a worktree: .claude/scripts/cleanup-worktree.ps1 -Name <name>
```
