#Requires -Version 3.0
<#
.SYNOPSIS
Supported-version guard (plan sections 7, 72, 98).

A version module whose adapter still contains `throw pending(...)` on any
practice path must not claim support via `supports()`. If `return true`
appears inside an AdapterSet's or LiveAdapter's supports() body, that
module must contain zero `throw pending` sites; otherwise the check fails.
Skeletons
(supports() returns false everywhere) always pass.
Windows twin of scripts/verify-supported-versions.sh (same checks).
Exit 0 = clean, 1 = violations.
#>
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$failed = $false

foreach ($dir in Get-ChildItem -Path (Join-Path $root 'versions') -Directory | Sort-Object -Property Name) {
    # A support claim is `return true` inside a supports() body, in the shell
    # or its live delegate (shells delegate, so the claim usually lives in
    # LiveAdapter); other interim optimistic answers do not count.
    $claims = $false
    $scanned = @()
    $scanned += @(Get-ChildItem -Path $dir.FullName -Filter 'AdapterSet*.java' -Recurse -File)
    $scanned += @(Get-ChildItem -Path $dir.FullName -Filter 'LiveAdapter*.java' -Recurse -File)
    foreach ($adapter in $scanned) {
        $inSupports = $false
        $opens = 0
        $closes = 0
        foreach ($raw in [IO.File]::ReadAllLines($adapter.FullName)) {
            $line = $raw -replace '//.*', ''
            if (-not $inSupports -and $line -match 'boolean\s+supports\s*\(') {
                $inSupports = $true
            }
            if ($inSupports) {
                $opens += ([regex]::Matches($line, '\{')).Count
                $closes += ([regex]::Matches($line, '\}')).Count
                if ($line -match 'return\s+true') { $claims = $true }
                if ($opens -gt 0 -and $opens -eq $closes) { break }
            }
        }
        if ($claims) { break }
    }
    if ($claims) {
        $src = Join-Path $dir.FullName 'src'
        $pending = @()
        if (Test-Path $src -PathType Container) {
            $pending = @(Get-ChildItem -Path $src -Filter '*.java' -Recurse -File |
                Select-String -Pattern 'throw pending' -SimpleMatch |
                ForEach-Object { '{0}:{1}: {2}' -f $_.Path.Substring($root.Length + 1), $_.LineNumber,
                    $_.Line.Trim() } |
                Sort-Object)
        }
        if ($pending.Count -gt 0) {
            Write-Output ('SUPPORT VIOLATION: {0} claims support while pending() remains:' -f $dir.Name)
            foreach ($p in $pending) { Write-Output "  $p" }
            $failed = $true
        } else {
            Write-Output ('OK: {0} claims support with no pending() sites.' -f $dir.Name)
        }
    } else {
        Write-Output ('OK: {0} claims no support.' -f $dir.Name)
    }
}

if ($failed) { exit 1 } else { exit 0 }
