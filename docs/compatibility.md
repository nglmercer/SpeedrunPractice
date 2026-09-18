# Compatibility

## Target versions

| Minecraft | Module | Role | Status | Java | Gradle/JDK to build |
| --------- | ------ | ---- | ------ | ---- | ------------------- |
| 1.16.1 | legacy root runtime (`src/`) | Primary speedrunning build | Playable (legacy path) | 8 (game), 17 (build) | Gradle wrapper |
| 1.16.1 | `:versions:fabric-1.16.1` | New-architecture adapter | Adapter pending, runtime unverified | 8 (game), 17 (build) | Wrapper (plain Java) |
| 1.21.1 | `:versions:fabric-1.21.1` | Modern compatibility build | Adapter pending, runtime unverified | 21 (game) | Gradle 8 + JDK 21 for Loom wiring |
| 26.3 | `:versions/fabric-26.3` | Latest supported build | Adapter pending, runtime unverified | 21+ (game) | Gradle 8 + modern JDK for Loom wiring |

Only the legacy 1.16.1 root runtime is playable today; the three adapter
modules are compilation-checked skeletons (see `docs/version-status.md`).

Shared modules (`common`, `practices`, `seed-search`, `test-support`) are
Java 8 bytecode with zero dependencies and build anywhere, including offline.

## v1.0 feature matrix (target)

Overworld, Buried Treasure, Nether, Bastion, Fortress, Blind Travel,
Post Blind, Stronghold, End, One Cycle, Seed Lists, Seed Search, Loadouts,
Same/New-Seed Reset, Checkpoints, Timer, Statistics, Custom Scenario JSON:
required on all three versions (plan section 44).

## Known gaps

- All three version adapters are skeletons: live-world methods raise a
  readable `AdapterException` until implemented against those mappings, and
  `supports()` returns `false` for every capability (plan section 7).
  Capability-gated UI (`supports(Capability)`) disables what is missing.
- Modern Loom wiring needs Gradle 8 + JDK 21; the legacy 1.16.1 Loom
  runtime keeps its own pinned toolchain until it migrates.
- `STRUCTURE_METADATA_SEARCH` (portal rooms, deterministic eye data) is
  claimed nowhere yet; stronghold portal-room mode falls back to the stairs
  start until an adapter implements and verifies it.
- Known-seed table rows are `verified:false` until checked in-game per
  version; the loader and assertions are ready.
