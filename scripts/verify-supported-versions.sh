#!/bin/sh
# Supported-version guard (plan sections 7, 72, 98): a version module whose
# adapter still contains `throw pending(...)` on any practice path must not
# claim support via `supports()`. Concretely, if `return true` appears inside
# an AdapterSet's or LiveAdapter's supports() body, that module must contain
# zero `throw pending` sites; otherwise CI fails. Skeletons (supports()
# returns false everywhere) always pass.
# Windows twin: scripts/verify-supported-versions.ps1 (same checks).
# Exit 0 = clean, 1 = violations.
set -u

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT" || { echo 'cannot cd to repo root' >&2; exit 1; }

failed=0
for dir in versions/*/; do
    [ -d "$dir" ] || continue
    name=$(basename "$dir")
    # A support claim is `return true` inside a supports() body, in the shell
    # or its live delegate; other interim optimistic answers do not count.
    claims="no"
    for adapter in $(find "$dir" \( -name 'AdapterSet*.java' -o -name 'LiveAdapter*.java' \) -print 2>/dev/null); do
        claims=$(awk '{sub(/\/\/.*/, "")} /boolean supports\(/{in_sup=1} in_sup==1{opens+=gsub(/\{/,"{"); closes+=gsub(/\}/,"}"); if (/return[[:space:]]+true/) found=1; if (opens>0 && opens==closes) exit} END{print (found==1 ? "yes" : "no")}' "$adapter")
        [ "$claims" = "yes" ] && break
    done
    if [ "$claims" = "yes" ]; then
        pending=$(grep -rn 'throw pending' "$dir/src" 2>/dev/null || true)
        if [ -n "$pending" ]; then
            echo "SUPPORT VIOLATION: $name claims support while pending() remains:"
            printf '%s\n' "$pending" | sed 's/^/  /'
            failed=1
        else
            echo "OK: $name claims support with no pending() sites."
        fi
    else
        echo "OK: $name claims no support."
    fi
done

exit "$failed"
