---
description: Merge a completed worktree back to main and clean it up
---

# Merge Worktree

Merge a completed worktree branch back to main and clean up the worktree.

## When to Use

Use when a spawned Claude session has completed its work and the changes are ready to be integrated.

## Process

### 1. Verify Completion
Ask the user to confirm the work is complete:
```
"Is the work in [feature-name] ready to merge? Any final changes needed?"

Check that:
- All code is written
- Tests pass
- Changes are committed
- No merge conflicts expected"
```

### 2. Merge and Cleanup
```powershell
.claude/scripts/merge-worktree.ps1 -Name "feature-name"
```

This will:
- Switch to main branch
- Pull latest changes
- Merge the feature branch
- Push to origin
- Remove the worktree
- Delete the feature branch
- Update session tracking

### 3. Handle Conflicts
If merge fails:
```
"Merge has conflicts. Resolve them in the main repo, then run:
   .claude/scripts/cleanup-worktree.ps1 -Name 'feature-name'"

The worktree will remain until conflicts are resolved."
```

## Force Merge (Uncommitted Changes)

If there are uncommitted changes but you want to proceed anyway:
```powershell
.claude/scripts/merge-worktree.ps1 -Name "feature-name" -Force
```

## Abandon a Worktree

If the work should be discarded:
```powershell
.claude/scripts/cleanup-worktree.ps1 -Name "feature-name" -Abandon
```

This will delete the worktree and branch without merging.
