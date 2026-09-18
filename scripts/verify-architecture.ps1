#Requires -Version 3.0
<#
.SYNOPSIS
Offline architecture guard: shared modules must not import Minecraft/Fabric.

Mirrors the :common / :practices verifyNoMinecraftImports Gradle tasks for
environments without a JDK/Gradle. Windows twin of
scripts/verify-architecture.sh (same checks, same output); runs on
Windows PowerShell and PowerShell 7, so no extra runtime is required.
Exit 0 = clean, 1 = violations.
#>
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$modules = @(
    'common/src/main/java',
    'practices/src/main/java',
    'seed-search/src/main/java',
    'test-support/src/main/java'
)
$pattern = '^\s*import\s+net\.(minecraft|fabricmc)\.'
$violations = @()

foreach ($module in $modules) {
    $base = Join-Path $root $module
    if (-not (Test-Path $base -PathType Container)) {
        Write-Output "skip (missing): $module"
        continue
    }
    $files = Get-ChildItem -Path $base -Filter '*.java' -Recurse -File | Sort-Object -Property FullName
    foreach ($file in $files) {
        $n = 0
        foreach ($line in [IO.File]::ReadAllLines($file.FullName)) {
            $n++
            if ($line -cmatch $pattern) {
                $rel = $file.FullName.Substring($root.Length + 1)
                $violations += ('{0}:{1}: {2}' -f $rel, $n, $line.Trim())
            }
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Output 'ARCHITECTURE VIOLATIONS (shared code imports Minecraft/Fabric):'
    foreach ($v in $violations) {
        Write-Output "  $v"
    }
    exit 1
}

Write-Output 'OK: no Minecraft/Fabric imports in shared modules.'
exit 0
