#!/bin/sh
# Offline architecture guard: shared modules must not import Minecraft/Fabric.
#
# Mirrors the :common / :practices verifyNoMinecraftImports Gradle tasks for
# environments without a JDK/Gradle. Needs only POSIX sh + grep + sed, so it
# runs on any Linux/macOS checkout and on CI with no extra runtime.
# Windows twin: scripts/verify-architecture.ps1 (same checks, same output).
# Exit 0 = clean, 1 = violations.
set -u

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT" || { echo 'cannot cd to repo root' >&2; exit 1; }

found=0
output=""
for module in \
    common/src/main/java \
    practices/src/main/java \
    seed-search/src/main/java \
    test-support/src/main/java
do
    if [ ! -d "$module" ]; then
        echo "skip (missing): $module"
        continue
    fi
    hits=$(grep -rn --include='*.java' -E \
        '^[[:space:]]*import[[:space:]]+net\.(minecraft|fabricmc)\.' \
        "$module" || true)
    if [ -n "$hits" ]; then
        # Reformat grep's "path:line:text" to "path:line: text" for parity
        # with the PowerShell twin.
        hits=$(printf '%s\n' "$hits" | sed 's/^\([^:]*:[0-9][0-9]*:\)/\1 /')
        if [ -z "$output" ]; then
            output="$hits"
        else
            output="$output
$hits"
        fi
        found=1
    fi
done

if [ "$found" -ne 0 ]; then
    echo 'ARCHITECTURE VIOLATIONS (shared code imports Minecraft/Fabric):'
    printf '%s\n' "$output" | sed 's/^/  /'
    exit 1
fi

echo 'OK: no Minecraft/Fabric imports in shared modules.'
exit 0
