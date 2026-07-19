param(
    [string]$UpstreamRemote = "upstream",
    [string]$UpstreamBranch = "master",
    [string]$TargetBranch = "",
    [switch]$Push,
    [switch]$PreviewOnly,
    [switch]$AllowDirty
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Invoke-Git {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$GitArgs
    )
    & git @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "git $($GitArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Get-GitOutput {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$GitArgs
    )
    $output = & git @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "git $($GitArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
    return $output
}

$repoRoot = (Get-GitOutput rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath $repoRoot

$currentBranch = (Get-GitOutput branch --show-current).Trim()
if ([string]::IsNullOrWhiteSpace($currentBranch)) {
    throw "Current HEAD is detached. Switch to a custom branch before syncing upstream."
}

if ([string]::IsNullOrWhiteSpace($TargetBranch)) {
    $TargetBranch = $currentBranch
}

$remotes = @(Get-GitOutput remote)
if ($remotes -notcontains $UpstreamRemote) {
    throw "Missing remote '$UpstreamRemote'. Add it with: git remote add upstream https://github.com/power721/alist-tvbox.git"
}

if (-not $AllowDirty) {
    $dirty = @(Get-GitOutput status --porcelain)
    if ($dirty.Count -gt 0) {
        Write-Host "Working tree is not clean. Commit or stash local changes first." -ForegroundColor Yellow
        Invoke-Git status --short --branch
        exit 1
    }
}

Write-Host "Repository: $repoRoot"
Write-Host "Target branch: $TargetBranch"
Write-Host "Upstream source: $UpstreamRemote/$UpstreamBranch"

Invoke-Git fetch $UpstreamRemote --prune
if ($remotes -contains "origin") {
    Invoke-Git fetch origin --prune
}

if ($TargetBranch -ne $currentBranch) {
    Invoke-Git switch $TargetBranch
}

$upstreamRef = "$UpstreamRemote/$UpstreamBranch"
$aheadBehind = Get-GitOutput rev-list --left-right --count "$TargetBranch...$upstreamRef"
Write-Host "Ahead/behind against upstream: $aheadBehind"

if ($PreviewOnly) {
    Write-Host "Preview only. Commits from upstream that are not in '$TargetBranch':"
    & git log --oneline "$TargetBranch..$upstreamRef"
    exit $LASTEXITCODE
}

try {
    Invoke-Git merge --no-ff --no-edit $upstreamRef
} catch {
    Write-Host ""
    Write-Host "Upstream merge stopped because conflicts need manual resolution." -ForegroundColor Yellow
    Write-Host "Resolve the conflicted files, then run:"
    Write-Host "  git add <resolved-files>"
    Write-Host "  git merge --continue"
    Write-Host ""
    Invoke-Git status --short --branch
    exit 2
}

if ($Push) {
    Invoke-Git push origin $TargetBranch
}

Invoke-Git status --short --branch
Write-Host "Sync complete. Custom branch '$TargetBranch' now contains the latest '$upstreamRef'."
