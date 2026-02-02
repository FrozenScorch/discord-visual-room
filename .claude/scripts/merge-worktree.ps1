#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Merge a worktree branch back to main and clean up the worktree.
.DESCRIPTION
    Merges a completed worktree branch to its parent, then removes the worktree
    and updates session tracking.
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$Name,

    [Parameter(Mandatory=$false)]
    [string]$TargetBranch = "main",

    [Parameter(Mandatory=$false)]
    [switch]$Force
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

# Check if worktree exists
if (-not (Test-Path $worktreePath)) {
    Write-Error "Worktree '$Name' not found at: $worktreePath"
    exit 1
}

# Verify the branch exists
$branchExists = git show-ref --verify --quiet "refs/heads/$branchName" 2>$null
if (-not $branchExists) {
    Write-Error "Branch '$branchName' does not exist"
    exit 1
}

# Check for uncommitted changes
$uncommitted = git -C $worktreePath status --porcelain
if ($uncommitted -and -not $Force) {
    Write-Warning "Uncommitted changes in worktree '$Name'"
    Write-Host $uncommitted
    $continue = Read-Host "Continue anyway? (y/n)"
    if ($continue -ne "y") {
        Write-Host "Aborted. Commit your changes first."
        exit 0
    }
}

Write-Host "[*] Merging worktree: $Name" -ForegroundColor Cyan
Write-Host "    Branch: $branchName -> $TargetBranch" -ForegroundColor DarkGray

# Switch to target branch in the main repo
Write-Host ""
Write-Host "[*] Switching to $TargetBranch in main repo..." -ForegroundColor Yellow
git -C $repoRoot checkout $TargetBranch 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to checkout $TargetBranch"
    exit 1
}

# Pull latest changes
Write-Host "[*] Pulling latest changes..." -ForegroundColor Yellow
git -C $repoRoot pull origin $TargetBranch 2>&1 | Out-Null

# Merge the feature branch
Write-Host "[*] Merging $branchName..." -ForegroundColor Yellow
$mergeResult = git -C $repoRoot merge --no-ff $branchName -m "Merge feat/$Name: $Name" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Error "Merge failed! Resolve conflicts in the main repo."
    Write-Host $mergeResult
    Write-Host ""
    Write-Host "After resolving conflicts, run:" -ForegroundColor Yellow
    Write-Host "  .claude/scripts/cleanup-worktree.ps1 -Name '$Name'" -ForegroundColor White
    exit 1
}

Write-Host "[+] Merge successful!" -ForegroundColor Green

# Push the merge
Write-Host "[*] Pushing to origin..." -ForegroundColor Yellow
git -C $repoRoot push origin $TargetBranch 2>&1 | Out-Null

# Remove the worktree
Write-Host ""
Write-Host "[*] Cleaning up worktree..." -ForegroundColor Yellow
git -C $repoRoot worktree remove $worktreePath 2>&1 | Out-Null

# Delete the branch
git -C $repoRoot branch -d $branchName 2>&1 | Out-Null

# Update session tracking
$sessionsFile = Join-Path $repoRoot ".claude\spawned-sessions.json"
if (Test-Path $sessionsFile) {
    $sessions = Get-Content $sessionsFile | ConvertFrom-Json
    $updatedSessions = $sessions | Where-Object { $_.name -ne $Name }
    $updatedSessions | ConvertTo-Json -Depth 10 | Out-File -FilePath $sessionsFile -Encoding UTF8
}

Write-Host "[+] Worktree '$Name' merged and cleaned up!" -ForegroundColor Green
Write-Host ""
Write-Host "Branch $branchName has been merged into $TargetBranch" -ForegroundColor DarkGray
