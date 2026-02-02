#!/usr/bin/env pwsh
<#
.SYNOPSIS
    List all active Claude worktree sessions.
.DESCRIPTION
    Shows all spawned Claude sessions with their status, branches, and tasks.
#>

# Get repository root
$repoRoot = git rev-parse --show-toplevel 2>$null
if (-not $repoRoot) {
    Write-Error "Not in a git repository"
    exit 1
}

$sessionsFile = Join-Path $repoRoot ".claude\spawned-sessions.json"

Write-Host ""
Write-Host "Active Claude Worktree Sessions" -ForegroundColor Cyan
Write-Host ("=" * 50) -ForegroundColor DarkGray

if (-not (Test-Path $sessionsFile)) {
    Write-Host "No active sessions found." -ForegroundColor DarkGray
    exit 0
}

$sessions = Get-Content $sessionsFile | ConvertFrom-Json

if ($sessions.Count -eq 0) {
    Write-Host "No active sessions found." -ForegroundColor DarkGray
    exit 0
}

foreach ($session in $sessions) {
    $statusColor = switch ($session.status) {
        "active" { "Green" }
        "completed" { "Blue" }
        "blocked" { "Red" }
        default { "Gray" }
    }

    Write-Host ""
    Write-Host "  [$($session.name)]" -ForegroundColor $statusColor
    Write-Host "    Branch: $($session.branch)" -ForegroundColor DarkGray
    Write-Host "    Path:   $($session.path)" -ForegroundColor DarkGray
    Write-Host "    Status: $($session.status)" -ForegroundColor DarkGray

    # Check if worktree still exists
    if (Test-Path $session.path) {
        # Get commit info
        try {
            $latestCommit = git -C $session.path log -1 --format="%h %s" 2>$null
            if ($latestCommit) {
                Write-Host "    Latest: $latestCommit" -ForegroundColor DarkGray
            }
        } catch {}
    } else {
        Write-Host "    [!] Worktree path not found (stale entry)" -ForegroundColor Yellow
    }

    if ($session.task) {
        $taskPreview = $session.task -split "`n" | Select-Object -First 1
        if ($taskPreview.Length -gt 50) {
            $taskPreview = $taskPreview.Substring(0, 47) + "..."
        }
        Write-Host "    Task:  $taskPreview" -ForegroundColor DarkGray
    }
}

Write-Host ""
Write-Host "Commands:" -ForegroundColor Yellow
Write-Host "  Merge a worktree:  .claude/scripts/merge-worktree.ps1 -Name 'name'" -ForegroundColor DarkGray
Write-Host "  Cleanup a worktree: .claude/scripts/cleanup-worktree.ps1 -Name 'name'" -ForegroundColor DarkGray
Write-Host ""
