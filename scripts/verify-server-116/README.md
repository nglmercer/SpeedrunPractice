# 1.16.1 headless server verification (plan step 11, server side)

Runs the real 1.16.1 dedicated server with the mod and drives `/practice`
commands over RCON — no client, no display. This exercises, in the actual
game: mod load, entrypoint wiring, Brigadier registration, and
`SeedAnalyzer116` against live worldgen configs and biome sources.

## Prerequisites

- JDK 8–17 on `PATH` as `JAVA_HOME` (the 1.16.1 loader/mixin stack does
  **not** boot on Java 25: `MixinService.initService` dies with
  `NoClassDefFoundError: net/minecraft/launchwrapper/LaunchClassLoader`
  before any mod code runs).
- The mod's Gradle cache primed (`./gradlew build` once).

## Setup

```powershell
# from the repo root ($run is the module run dir)
$run = 'versions/fabric-1.16.1/run'
Copy-Item scripts/verify-server-116/server.properties "$run/server.properties"
"eula=true" | Out-File -Encoding ascii "$run/eula.txt"
New-Item -ItemType Directory -Force `
  -Path "$run/config/speedrun-practice-new/seeds/searches" | Out-Null
Copy-Item scripts/verify-server-116/presets/*.json `
  "$run/config/speedrun-practice-new/seeds/searches/"
```

## Run

```powershell
$env:JAVA_HOME = '<jdk17>'
./gradlew :versions:fabric-1.16.1:runServer --console=plain
# wait for "Done (...)" in the log, then from another shell:
powershell -ExecutionPolicy Bypass -Command `
  "& scripts/verify-server-116/rcon116.ps1 -Command 'practice seeds search village','practice seeds results'"
# ...repeat results until finished, then:
powershell -ExecutionPolicy Bypass -Command `
  "& scripts/verify-server-116/rcon116.ps1 -Command 'practice seeds export'"
```

## Cross-checking predictions (correctness, not just execution)

1. Read the export at
   `versions/fabric-1.16.1/run/config/speedrun-practice-new/seeds/exports/search-*.json`:
   it holds the found seed plus predicted structure XZ locations.
2. Stop the server, set `level-seed=<found seed>` in
   `versions/fabric-1.16.1/run/server.properties`, delete
   `versions/fabric-1.16.1/run/world*`, restart.
3. From the predicted village doorstep the game must agree:
   `execute positioned <X> 64 <Z> run locate Village` should report the
   same spot a few blocks away (prediction is the start-chunk center;
   `/locate` reports the start — within ~16 blocks is a pass).
4. Same for `locate Stronghold`. For bastions, `/locate` cannot confirm the
   subtype; subtype correctness additionally needs chunk inspection in a
   real client (still open).

## What this does NOT verify

Practices start/complete, resets, loadouts, checkpoints, timer, stats,
GUI and keybinds all need a player/client. Those stay `runtime unverified`
in `docs/version-status.md` until exercised in-game. Nothing here marks a
seed `verified` either — that needs the plan section 14 procedure.
