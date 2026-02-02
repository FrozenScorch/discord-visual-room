#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Remove a worktree without merging (use after manual merge resolution).
.DESCRIPTION
    Removes a worktree and its branch after manual conflict resolution or
    when abandoning a worktree.
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$Name,

    [Parameter(Mandatory=$false)]
    [switch]$Abandon
)

# Get repository root
$repoRoot = git rev-parse --show-toplevel 2>$null
if (-not $repoRoot) {
    Write-Error "Not in a git repository"
    exit 1
}

$worktreesBase = Join-Path $repoRoot "..\worktrees"
$worktreePath = Join-Path $worktreesBase $Name
$branchName = "feat/$Name"

if ($Abandon) {
    Write-Warning "Abandoning worktree '$Name' - all unpushed changes will be lost!"
    $confirm = Read-Host "Type 'ABANDON' to confirm"
    if ($confirm -ne "ABANDON") {
        Write-Host "Cancelled."
        exit 0
    }
}

# Remove the worktree
Write-Host "[*] Removing worktree: $Name" -ForegroundColor Yellow

if (Test-Path $worktreePath) {
    git -C $repoRoot worktree remove $worktreePath 2>&1 | Out-Null
    Write-Host "[+] Worktree removed" -ForegroundColor Green
} else {
    Write-Host "[!] Worktree path not found (already removed?)" -ForegroundColor Yellow
}

# Delete the branch
$branchExists = git show-ref --verify --quiet "refs/heads/$branchName" 2>$null
if ($branchExists) {
    git -C $repoRoot branch -D $branchName 2>&1 | Out-Null
    Write-Host "[+] Branch deleted" -ForegroundColor Green
}

# Update session tracking
$sessionsFile = Join-Path $repoRoot ".claude\spawned-sessions.json"
if (Test-Path $sessionsFile) {
    $sessions = Get-Content $sessionsFile | ConvertFrom-Json
    $updatedSessions = $sessions | Where-Object { $_.name -ne $Name }
    $updatedSessions | ConvertTo-Json -Depth 10 | Out-File -FilePath $sessionsFile -Encoding UTF8
    Write-Host "[+] Session tracking updated" -ForegroundColor Green
}

Write-Host ""
if ($Abandon) {
    Write-Host "Worktree '$Name' abandoned." -ForegroundColor Red
} else {
    Write-Host "Worktree '$Name' cleaned up." -ForegroundColor Green
}
