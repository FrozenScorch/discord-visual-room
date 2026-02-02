#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Spawn a new Claude Code session in a git worktree for parallel work.
.DESCRIPTION
    Creates a git worktree, opens VS Code in a new window, and saves context
    for the Claude session to consume. Tracks the spawned session for cleanup.
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$Name,

    [Parameter(Mandatory=$true)]
    [string]$Task,

    [Parameter(Mandatory=$false)]
    [string]$BaseBranch = "main",

    [Parameter(Mandatory=$false)]
    [switch]$SkipPermissions
)

# Get repository root
$repoRoot = git rev-parse --show-toplevel 2>$null
if (-not $repoRoot) {
    Write-Error "Not in a git repository"
    exit 1
}

# Create worktrees directory if it doesn't exist
$worktreesBase = Join-Path $repoRoot "..\worktrees"
if (-not (Test-Path $worktreesBase)) {
    New-Item -ItemType Directory -Path $worktreesBase -Force | Out-Null
}

$worktreePath = Join-Path $worktreesBase $Name

# Check if worktree already exists
if (Test-Path $worktreePath) {
    Write-Warning "Worktree '$Name' already exists at: $worktreePath"
    $continue = Read-Host "Continue anyway? (y/n)"
    if ($continue -ne "y") {
        exit 0
    }
}

# Create branch name
$branchName = "feat/$Name"

# Create the worktree
Write-Host "[*] Creating worktree: $Name" -ForegroundColor Cyan
Write-Host "    Branch: $branchName" -ForegroundColor DarkGray
Write-Host "    Path: $worktreePath" -ForegroundColor DarkGray

$createResult = git worktree add $worktreePath -b $branchName 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create worktree: $createResult"
    exit 1
}

# Save task context
$contextFile = Join-Path $worktreePath ".claude\session-task.md"
$claudeDir = Split-Path $contextFile -Parent
if (-not (Test-Path $claudeDir)) {
    New-Item -ItemType Directory -Path $claudeDir -Force | Out-Null
}

$taskContent = @"
# Claude Task for: $Name

**Branch:** $branchName
**Parent Branch:** $BaseBranch
**Spawned From:** $repoRoot
**Created:** $(Get-Date -Format "o")

---

## Your Task

$Task

---

## Instructions

1. Read and understand the task above
2. Work on the feature in this isolated worktree
3. Commit your changes with clear messages
4. When complete, notify the main session (this worktree will be merged and cleaned up)

## Notes

- You are in a git worktree - changes here are isolated from the main session
- The main session is in: $repoRoot
- Work on independent features to avoid merge conflicts
"@

$taskContent | Out-File -FilePath $contextFile -Encoding UTF8

# Track this spawned session
$sessionsFile = Join-Path $repoRoot ".claude\spawned-sessions.json"
$sessionId = [Guid]::NewGuid().ToString()

$sessionEntry = @{
    id = $sessionId
    name = $Name
    path = $worktreePath
    branch = $branchName
    parentBranch = $BaseBranch
    createdAt = (Get-Date -Format "o")
    task = $Task
    status = "active"
}

if (Test-Path $sessionsFile) {
    $sessions = @(Get-Content $sessionsFile | ConvertFrom-Json)
    if ($sessions.Count -eq 0) {
        $sessions = @($sessionEntry)
    } else {
        $sessions = @($sessions) + @($sessionEntry)
    }
} else {
    $sessions = @($sessionEntry)
}

# Ensure directory exists
$sessionsDir = Split-Path $sessionsFile -Parent
if (-not (Test-Path $sessionsDir)) {
    New-Item -ItemType Directory -Path $sessionsDir -Force | Out-Null
}

$sessions | ConvertTo-Json -Depth 10 | Out-File -FilePath $sessionsFile -Encoding UTF8

# Open VS Code in new window
Write-Host "[*] Opening VS Code..." -ForegroundColor Yellow
code --new-window $worktreePath

# Build the claude command
$claudeCmd = "claude"
if ($SkipPermissions) {
    $claudeCmd += " --dangerously-skip-permissions"
}

# Output summary
Write-Host ""
Write-Host "[+] Worktree spawned successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Session ID: $sessionId" -ForegroundColor DarkGray
Write-Host "Worktree:   $worktreePath" -ForegroundColor DarkGray
Write-Host "Branch:     $branchName" -ForegroundColor DarkGray
Write-Host ""
Write-Host "In the new VS Code window, run:" -ForegroundColor Yellow
Write-Host "  $claudeCmd" -ForegroundColor White
Write-Host ""
Write-Host "Then share the task from .claude/session-task.md" -ForegroundColor Yellow
Write-Host ""
Write-Host "To merge this worktree when complete:" -ForegroundColor Yellow
Write-Host "  .claude/scripts/merge-worktree.ps1 -Name '$Name'" -ForegroundColor White
