# Version status

Per-version runtime truth (plan section 79). A cell turns ✅ only after the
feature is exercised in the real game on that version — never from unit tests
or compilation alone (plan section 98). Until then the vocabulary is:

- `adapter pending` — the version adapter method still throws `pending()`
- `shared model implemented` — `common`/`practices` logic exists and is unit-tested
- `runtime unverified` — code exists but never ran in-game on that version

## New architecture (`PracticeRuntime` + `ScenarioEngine` + `AdapterSet`)

1.16.1 runs the new runtime for real: `Runtime116` wires the shared engine
to `LiveAdapter116` (worlds, players, inventories, structures, portals,
dragon, registry, commands, timer) inside the 1.16.1 module jar, and
`AdapterSet116` is a delegation shell with no `pending()` left. 1.21.1 is
still a compilation-checked skeleton. 26.3 wires the same runtime shape
(`Runtime263` → `AdapterSet263` → `LiveAdapter263`); its entrypoint,
command registration, search/export/reload dispatch and error paths are
verified on a headless dedicated server (2026-09-18, `scripts/verify-server-263`),
while everything needing a player is still unverified.

| Feature | 1.16.1 | 1.21.1 | 26.3 |
| ------- | ------ | ------ | ---- |
| Mod loads (new adapter entrypoint) | ✅ dedicated server 2026-09-18 (JDK 17, Loader 0.13.2) | ❌ adapter pending | ✅ dedicated server 2026-09-18 |
| `/practice` commands register | ✅ dedicated server 2026-09-18 (17 shared children; searches, exports and alias routing driven over RCON) | ❌ adapter pending | ✅ dedicated server 2026-09-18 |
| Short practice aliases route to engine | ✅ dedicated server 2026-09-18 (all 9 alias forms reach the engine; actual starts need a player) | ❌ adapter pending | runtime unverified |
| Practice world creation | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Practice world deletion/reset | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `/practice start overworld` | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `/practice start nether` | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `/practice start bastion` | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `/practice start fortress` | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `/practice start blind_travel` | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `/practice start postblind` | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `/practice start stronghold` | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `/practice start end` | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `/practice start onecycle` | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Same-seed reset | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| New-seed reset | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Previous-seed reset | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Loadouts | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Checkpoints | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Timer | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Completion detection | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Statistics | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Seed list | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Seed search | ✅ dedicated server 2026-09-18 (village/stronghold/treasure/fortress/bastion + biome searches and exports on 5 seeds, all doorstep-cross-checked via /locate; lava presets need a stage-B chunk verifier) | ❌ adapter pending | ✅ real analyzer verified on dedicated server 2026-09-18 (biome/village/stronghold searches + export + /locate cross-checks; bastion-type and lava presets mismatch by design) |
| Verified seed fixtures (plan section 14) | ✅ 5 seeds (12345, 20001–20004) with structure positions, spawn, spawn biome and bastion type in `test-data/1.16.1/structures.json` | ❌ | 5 seeds recorded 2026-09-18 per the fixture method note (not re-checked this session) |
| Favorites | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| GUI screens | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| Keybinds | runtime unverified | ❌ adapter pending | ❌ adapter pending |
| `CUSTOM_DIMENSION_RUNTIME` | ❌ | ❌ | ❌ |
| `FAST_WORLD_RESET` | ❌ | ❌ | ❌ |
| `BASTION_TYPE_QUERY` | ❌ | ❌ | ❌ |
| `DRAGON_FORCE_PERCH` | ❌ | ❌ | ❌ |
| `PORTAL_STATE_CAPTURE` | ❌ | ❌ | ❌ |
| `STRUCTURE_METADATA_SEARCH` | ❌ | ❌ | ❌ |

Every row above is `shared model implemented` and covered by shared unit
tests; the ❌ marks the missing version half only. Shared-side, the engine
now also owns: timer start/stop conditions with event dispatch, per-scenario
completion conditions, the exception-safe start/reset state machine
(`STOPPING`/`FAILED`), preset validation before world creation, full player
+ scenario checkpoint snapshots with seed/dimension world consistency,
persistent `stats.json` with per-version/preset slices, analyze-once seed
search with verified/failed progress, cancellable search thread ownership,
runnable JSON search presets with result export, scenario `seed.filters`
wiring, custom `completion`/`requires` definitions, and cross-version
loadout item filtering. 1.16.1 seed analysis covers spawn biome, structure
positions/distances, bastion subtype and stronghold rings; lava is not
emitted (it needs generated chunks) and lava-constrained searches fail
fast with a readable error until a stage-B verifier exists. Region block snapshots are a data model only — live capture
needs version block APIs. No row flips to ✅ until exercised in-game.

## Shared unit tests (in-memory harness, no Minecraft)

| Suite | Status |
| ----- | ------ |
| `:common` tests (config, seeds, stats, timer, loadouts, checkpoints, scenarios, events) | ✅ run in CI (`shared-tests`) |
| `:practices` tests (engine + scenarios + `PracticeRuntime` on the fake adapter) | ✅ run in CI (`shared-tests`) |
| `:seed-search` tests (cancellable search lifecycle) | ✅ run in CI (`shared-tests`) |
| `:versions:fabric-1.16.1` capability guard (`supports()` claims nothing) | ✅ run in CI (`new-116-adapter`) |
| `:versions:fabric-1.21.1` capability guard (`supports()` claims nothing) | ✅ run in CI (`121-runtime`) |
| `:versions:fabric-26.3` capability guard (`supports()` claims nothing) | ✅ run in CI (`263-runtime`) |
| Supported-version guard (no `supports() == true` while `pending()` remains) | ✅ run in CI (`adapter-compilation`) |
| Architecture guard (no `net.minecraft`/`net.fabricmc` imports in shared modules) | ✅ run in CI + Gradle `check` |

These prove shared logic only. They never flip a runtime cell above to ✅.

Stage-4 local verification (2026-09-18, offline): full
`./gradlew build --offline` green (exit 0), including a forced
`--rerun-tasks` run: 41 suites / 291 tests / 0 failures / 0 errors /
0 skipped; `scripts/verify-architecture.ps1` exit 0 (no Minecraft/Fabric
imports in shared modules); `scripts/verify-supported-versions.ps1`
exit 0 (all three adapters still claim no capability — skeleton state).

## Legacy baseline (migrated into `:versions:fabric-1.16.1`, 1.16.1 only)

The pre-existing 1.16.1 Fabric mod code now lives in the version module next
to the new live adapter. Plan section 2 cut the six practice spellings
(`end`, `nether`, `overworld` + `bt`, `postblind`, `stronghold`) over to
the engine and deleted their legacy executors; the legacy tree keeps only
seed/seedlist/inventory/world/revert/`instaperch` plus the shared world,
config and mixin internals the live adapter reuses. Baseline capabilities
live in `docs/original-feature-matrix.md`; practice starts still need a
player/client to verify.

## Server-side verification harness (green on 1.16.1)

`scripts/verify-server-116/` drives a headless 1.16.1 dedicated server over
RCON (`practice seeds search/results/export`, alias routing checks) plus a
`/locate` doorstep cross-check procedure for prediction correctness. Green
since 2026-09-18 on JDK 17 (`$HOME/.jdks/jdk-17.0.20.1+1`, Loader 0.13.2):
5 fixture seeds fully cross-checked, RCON source confirmed at world spawn,
and the vanilla `/locate` walk quirk documented (console-centered global
locates can return a farther structure; doorstep checks are the verdict —
see the `test-data/1.16.1/structures.json` note).

## Update protocol

When a feature is tested in-game on a version, flip its cell to ✅ and note
the date/build in the row or commit message. If in-game validation cannot run
(e.g. headless CI), mark the work `compiled/unverified`, never `working`
(plan section 96).
