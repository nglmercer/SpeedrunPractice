# 26.3 headless server verification (plan steps 17/19, server side)

Runs the real 26.3 dedicated server with the mod and drives `/practice`
commands over RCON — no client, no display. Unlike 1.16.1 this runs on the
current JDK 25 toolchain.

## Prerequisites

- JDK 25 on `PATH` as `JAVA_HOME`.
- The mod's Gradle cache primed (`./gradlew build` once, needs network the
  first time for 26.3 artifacts).

## Setup

```powershell
# from the repo root ($run is the module run dir)
$run = 'versions/fabric-26.3/run'
Copy-Item scripts/verify-server-263/server.properties "$run/server.properties"
"eula=true" | Out-File -Encoding ascii "$run/eula.txt"
New-Item -ItemType Directory -Force `
  -Path "$run/config/speedrun-practice-new/seeds/searches" | Out-Null
Copy-Item scripts/verify-server-263/presets/*.json `
  "$run/config/speedrun-practice-new/seeds/searches/"
```

## Run

```powershell
./gradlew :versions:fabric-26.3:runServer --console=plain
# wait for "Done (...)" in the log, then from another shell:
powershell -ExecutionPolicy Bypass -Command `
  "& scripts/verify-server-263/rcon263.ps1 -Command 'practice seed','practice seeds search open','practice seeds results'"
# then:
powershell -ExecutionPolicy Bypass -Command `
  "& scripts/verify-server-263/rcon263.ps1 -Command 'practice seeds export','practice config reload'"
# stop when done:
powershell -ExecutionPolicy Bypass -Command `
  "& scripts/verify-server-263/rcon263.ps1 -Command 'stop'"
```

Expected (verified 2026-09-18, step 17a):

```text
practice seed                 → No practice is running.
practice seeds search open    → Seed search "open" started ...
practice seeds results        → Search: 2 tested, 2 matched, finished. Seeds: 1 2
practice seeds export         → Exported 2 seeds to search-*.json.
practice config reload        → Reloaded config, 0 custom scenarios, 0 loadouts.
practice start end            → That command needs a player. Run it in-game.
```

## Cross-checking predictions (once SeedAnalyzer263 lands)

Same procedure as 1.16.1 (`scripts/verify-server-116/README.md`):
reseed the server to a found seed and compare `/locate` output against the
exported `location.*` entries.

## What this does NOT verify

Anything needing a player (practices, resets, loadouts, checkpoints,
timer, stats, GUI, keybinds). Those stay `runtime unverified` in
`docs/version-status.md` until exercised in-game.
