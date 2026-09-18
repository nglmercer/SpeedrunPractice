# Version status

Per-version runtime truth (plan section 79). A cell turns ✅ only after the
feature is exercised in the real game on that version — never from unit tests
or compilation alone (plan section 98). Until then the vocabulary is:

- `adapter pending` — the version adapter method still throws `pending()`
- `shared model implemented` — `common`/`practices` logic exists and is unit-tested
- `runtime unverified` — code exists but never ran in-game on that version

## New architecture (`PracticeRuntime` + `ScenarioEngine` + `AdapterSet`)

All three adapters are compilation-checked skeletons: every live-Minecraft
method throws `pending()` and `supports()` returns `false` for every
capability. Nothing below runs in-game yet on any version.

| Feature | 1.16.1 | 1.21.1 | 26.3 |
| ------- | ------ | ------ | ---- |
| Mod loads (new adapter entrypoint) | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice` commands register | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Practice world creation | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Practice world deletion/reset | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice start overworld` | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice start nether` | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice start bastion` | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice start fortress` | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice start blind_travel` | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice start postblind` | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice start stronghold` | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice start end` | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| `/practice start onecycle` | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Same-seed reset | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| New-seed reset | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Previous-seed reset | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Loadouts | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Checkpoints | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Timer | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Completion detection | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Statistics | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Seed list | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Seed search | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Favorites | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| GUI screens | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
| Keybinds | ❌ adapter pending | ❌ adapter pending | ❌ adapter pending |
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
custom `completion`/`requires` definitions, and cross-version loadout item
filtering. Region block snapshots are a data model only — live capture
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

## Legacy baseline (untouched root `src/` runtime, 1.16.1 only)

The pre-existing 1.16.1 Fabric mod remains the only playable jar until the
new 1.16.1 adapter reaches parity practice by practice (plan section 5).
Baseline capabilities live in `docs/original-feature-matrix.md`; nothing was
re-verified as part of the new-architecture work.

## Update protocol

When a feature is tested in-game on a version, flip its cell to ✅ and note
the date/build in the row or commit message. If in-game validation cannot run
(e.g. headless CI), mark the work `compiled/unverified`, never `working`
(plan section 96).
