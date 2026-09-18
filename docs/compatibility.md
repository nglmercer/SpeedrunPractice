# Compatibility

## Supported versions

| Minecraft | Module | Role | Java | Gradle/JDK to build |
| --------- | ------ | ---- | ---- | ------------------- |
| 1.16.1 | `:versions:fabric-1.16.1` (+ legacy root runtime) | Primary speedrunning build | 8 (game), 17 (build) | Gradle 7.3 wrapper |
| 1.21.1 | `:versions:fabric-1.21.1` | Modern compatibility build | 21 (game) | Gradle 8 + JDK 21 for Loom wiring |
| 26.3 | `:versions/fabric-26.3` | Latest supported build | 21+ (game) | Gradle 8 + modern JDK for Loom wiring |

Shared modules (`common`, `practices`, `seed-search`, `test-support`) are
Java 8 bytecode with zero dependencies and build anywhere, including offline.

## v1.0 feature matrix (target)

Overworld, Buried Treasure, Nether, Bastion, Fortress, Blind Travel,
Post Blind, Stronghold, End, One Cycle, Seed Lists, Seed Search, Loadouts,
Same/New-Seed Reset, Checkpoints, Timer, Statistics, Custom Scenario JSON:
required on all three versions (plan section 44).

## Known gaps

- The 1.21.1 and 26.3 adapters are skeletons: live-world methods raise a
  readable `AdapterException` until ported against those mappings.
  Capability-gated UI (`supports(Capability)`) disables what is missing.
- Modern Loom wiring needs Gradle 8 + JDK 21; the repo wrapper stays on
  Gradle 7.3 until the 1.16.1 legacy runtime migrates.
- `STRUCTURE_METADATA_SEARCH` (portal rooms, deterministic eye data) is only
  claimed on 1.21.1 so far; stronghold portal-room mode falls back to the
  stairs start elsewhere.
- Known-seed table rows are `verified:false` until checked in-game per
  version; the loader and assertions are ready.
