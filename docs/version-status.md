# Version status

Per-version runtime truth (plan section 79). A cell turns ✅ only after the
feature is exercised in the real game on that version — never from unit tests
or compilation alone (plan section 98). Until then the vocabulary is:

- `adapter pending` — the version adapter method still throws `pending()`
- `shared model implemented` — `common`/`practices` logic exists and is unit-tested
- `runtime unverified` — code exists but never ran in-game on that version

## Verification-framework status (current)

The repository now has a verification-only source set and jar for each
supported version. Release jars are checked by `verifyReleaseJar116`,
`verifyReleaseJar121`, and `verifyReleaseJar263`; probes and verification
entrypoints are not packaged into production jars. The canonical fixtures live
under `verification/fixtures/` and reject any row that is not explicitly
verified.

The clean 26.3 headless lava gate passed on 2026-09-19:
`verifyHeadless263` produced `build/verification/26.3/report.json` with
`suite.started` and `lava.stage-b` passing across the five canonical seeds
plus the positive control seed `63`. The same gate passed on 1.21.1 after a
fresh dedicated-server run: six comparisons (`12345`, `20001`–`20004`, and
positive control `12`) all logged `MATCH=true` and `DONE match=6/6`.

The 1.16.1 gate also passed on JDK 17: two report checks passed, and all ten
lava comparisons logged `MATCH=true` with `DONE match=10/10`. The seed `17`
case was corrected by preserving generation-step ordering across the scanned
chunk set, which prevents a later cross-chunk decoration write from changing
an earlier lake candidate. Both the 1.16.1 and 1.21.1 canonical fixtures now
contain five verified rows. The 1.21.1 rows were collected by a dedicated
server fixture run and include spawn, biome, village, stronghold portal-room,
fortress, bastion subtype, and lava metadata. The 1.16.1 rows use the live
structure table plus the fresh Stage-B lava gate. All runs used fresh
server logs/RCON, generated real practice chunks, and left no verification
server listening after shutdown. The `all` command is registered on every
version, but its remaining world/structure/portal/player/scenario suites still
report an explicit not-implemented failure rather than claiming coverage.

## New architecture (`PracticeRuntime` + `ScenarioEngine` + `AdapterSet`)

1.16.1 runs the new runtime for real: `Runtime116` wires the shared engine
to `LiveAdapter116` (worlds, players, inventories, structures, portals,
dragon, registry, commands, timer) inside the 1.16.1 module jar, and
`AdapterSet116` is a delegation shell with no `pending()` left. 1.21.1 is a
full Loom module with every slice live (`Runtime121` → `AdapterSet121` →
`LiveAdapter121`, no `pending()` left); all server-side slices are verified
on a headless dedicated server (2026-09-19, temp-probe runs, probes removed
after passing), while everything needing a player or a client window is
still unverified. 26.3 wires the same runtime shape (`Runtime263` →
`AdapterSet263` → `LiveAdapter263`); its entrypoint, command registration,
search/export/reload dispatch and error paths are verified on a headless
dedicated server (2026-09-18, `scripts/verify-server-263`), while everything
needing a player is still unverified.

| Feature | 1.16.1 | 1.21.1 | 26.3 |
| ------- | ------ | ------ | ---- |
| Mod loads (new adapter entrypoint) | ✅ dedicated server 2026-09-18 (JDK 17, Loader 0.13.2) | ✅ dedicated server 2026-09-19 (runtime armed, 17-child tree registered) | ✅ dedicated server 2026-09-18 |
| `/practice` commands register | ✅ dedicated server 2026-09-18 (17 shared children; searches, exports and alias routing driven over RCON) | ✅ dedicated server 2026-09-19 (17 shared children; dispatch needs a player) | ✅ dedicated server 2026-09-18 |
| Short practice aliases route to engine | ✅ dedicated server 2026-09-18 (all 9 alias forms reach the engine; actual starts need a player) | runtime unverified (routing is shared-model; starts need a player) | runtime unverified |
| Practice world creation | runtime unverified | ✅ dedicated server 2026-09-19 (seeded triples incl. KNOWN_SEED; worlds probe 8/8) | ✅ dedicated server 2026-09-18 (seeded triples for 5 fixture seeds, archived RCON logs) |
| Practice world deletion/reset | runtime unverified (same reset-aliasing fix as 26.3 applied; needs a player to verify) | ✅ dedicated server 2026-09-19 (rebuild reset + triple deletion; worlds probe 8/8) | ✅ dedicated server 2026-09-18 (reset reseed 424242→424243, deletion leaves zero practice levels) |
| `/practice start overworld` | runtime unverified | runtime unverified (adapter paths verified headless; engine routing needs a player) | ❌ adapter pending |
| `/practice start nether` | runtime unverified | runtime unverified (adapter paths verified headless; engine routing needs a player) | ❌ adapter pending |
| `/practice start bastion` | runtime unverified | runtime unverified (adapter paths verified headless; engine routing needs a player) | ❌ adapter pending |
| `/practice start fortress` | runtime unverified | runtime unverified (adapter paths verified headless; engine routing needs a player) | ❌ adapter pending |
| `/practice start blind_travel` | runtime unverified | runtime unverified (adapter paths verified headless; engine routing needs a player) | ❌ adapter pending |
| `/practice start postblind` | runtime unverified | runtime unverified (adapter paths verified headless; engine routing needs a player) | ❌ adapter pending |
| `/practice start stronghold` | runtime unverified | runtime unverified (adapter paths verified headless; engine routing needs a player) | ❌ adapter pending |
| `/practice start end` | runtime unverified | runtime unverified (adapter paths verified headless; engine routing needs a player) | ❌ adapter pending |
| Nether portals (create/link) | runtime unverified | ✅ dedicated server 2026-09-19 (portals 3/3 plus 4-direction pig travel headless) | ✅ dedicated server 2026-09-18 (linked pair built headless; portal blocks confirmed overworld + nether) |
| Dragon fight (reset/perch/query) | runtime unverified | ✅ dedicated server 2026-09-19 (dragons 5/5; no living dragon without a player in the end) | ✅ dedicated server 2026-09-18 (reset/query/perch-error paths exercised; no live dragon without a player in the end) |
| Registry item lookups | runtime unverified | ✅ dedicated server 2026-09-19 (registries 3/3 headless) | ✅ dedicated server 2026-09-18 (sword exists, pearl stacks to 16, bogus id rejected) |
| `/practice start onecycle` | runtime unverified | runtime unverified (adapter paths verified headless; engine routing needs a player) | ❌ adapter pending |
| Same-seed reset | runtime unverified | runtime unverified (adapter reset path verified headless; engine routing needs a player + active practice) | runtime unverified (adapter reset path verified headless; engine routing needs a player + active practice) |
| New-seed reset | runtime unverified | runtime unverified (adapter reset path verified headless; engine routing needs a player + active practice) | runtime unverified (adapter reset path verified headless; engine routing needs a player + active practice) |
| Previous-seed reset | runtime unverified | runtime unverified (adapter reset path verified headless; engine routing needs a player + active practice) | runtime unverified (adapter reset path verified headless; engine routing needs a player + active practice) |
| Loadouts | runtime unverified | runtime unverified (adapter capture/apply need a player) | ❌ adapter pending |
| Checkpoints | runtime unverified | runtime unverified (need a player + active practice) | ❌ adapter pending |
| Timer | runtime unverified | ✅ dedicated server 2026-09-19 (bridge contract: unavailable no-op 4/4; shared monotonic timer is the real timer) | ❌ adapter pending |
| Completion detection | runtime unverified | runtime unverified | ❌ adapter pending |
| Statistics | runtime unverified | runtime unverified | ❌ adapter pending |
| Seed list | runtime unverified | runtime unverified | ❌ adapter pending |
| Seed search | ✅ dedicated server 2026-09-18 (village/stronghold/treasure/fortress/bastion + biome searches and exports on 5 seeds, all doorstep-cross-checked via /locate; lava presets need a stage-B chunk verifier) | ✅ real analyzer verified on dedicated server 2026-09-19 (biome/village/stronghold/fortress/bastion+type/end-city vs live locate 7/7, re-probed 7/7 after the practice-seed gate fix; lava/unknown mismatch by design) | ✅ real analyzer verified on dedicated server 2026-09-19 (walk-order first-hit fix; 19/19 vs live locate on 3 seeds incl. bastion-type round-trips; lava via Stage-B separately) |
| Verified seed fixtures (plan section 14) | ✅ 5 seeds (12345, 20001–20004) with structure positions, spawn, spawn biome and bastion type in `test-data/1.16.1/structures.json` | ✅ 5 seeds (12345, 20001–20004), including spawn, biome, lava, portal-room and bastion metadata in `verification/fixtures/1.21.1.json` | ✅ 5 seeds, all 16 fields each corroborated against the archived live-server log |
| Favorites | runtime unverified | runtime unverified | ❌ adapter pending |
| GUI screens | runtime unverified | ✅ server behavior 2026-09-19 (unavailable + foreign/live-handle guards 4/4 headless); screens need a client | ❌ adapter pending |
| Keybinds | runtime unverified | runtime unverified (client entrypoint wired; presses need a client) | ❌ adapter pending |
| `CUSTOM_DIMENSION_RUNTIME` | ❌ | ✅ tested 2026-09-19 (seeded triples created/reset/deleted headless) | ✅ tested 2026-09-18 (seeded triples created/deleted headless) |
| `FAST_WORLD_RESET` | ❌ | ❌ (rebuild-only by design) | ❌ (rebuild-only by design) |
| `BASTION_TYPE_QUERY` | ❌ | ✅ tested 2026-09-19 (housing/bridge metadata from live starts) | ✅ tested 2026-09-18 (bridge/stables metadata from live starts) |
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
| `:versions:fabric-1.16.1` capability delegation (`AdapterSet` forwards `supports()` to live) | ✅ run in CI (`new-116-adapter`) |
| `:versions:fabric-1.21.1` capability delegation (`AdapterSet` forwards `supports()` to live) | ✅ run in CI (`121-runtime`) |
| `:versions:fabric-26.3` capability delegation (`AdapterSet` forwards `supports()` to live) | ✅ run in CI (`263-runtime`) |
| Supported-version guard (no `supports() == true` while `pending()` remains) | ✅ run in CI (`adapter-compilation`) |
| Architecture guard (no `net.minecraft`/`net.fabricmc` imports in shared modules) | ✅ run in CI + Gradle `check` |

These prove shared logic only. They never flip a runtime cell above to ✅.

Stage-4 local verification (2026-09-18, offline): full
`./gradlew build --offline` green (exit 0), including a forced
`--rerun-tasks` run: 41 suites / 291 tests / 0 failures / 0 errors /
0 skipped; `scripts/verify-architecture.ps1` exit 0 (no Minecraft/Fabric
imports in shared modules); `scripts/verify-supported-versions.ps1` exit 0
(the script scans `AdapterSet` files only, which delegate: the 26.3 live
adapter tested-claims `CUSTOM_DIMENSION_RUNTIME` + `BASTION_TYPE_QUERY`).

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

## Headless verification suites (2026-09-19)

The registered `practiceverify run all` contract suite now passes on a real
dedicated server for every supported version: 1.16.1 (12/12 on JDK 17),
1.21.1 (12/12), and 26.3 (12/12). The checks exercise the version-local live
adapter, practice-world seed/spawn/cleanup, village lookup, portal creation,
registry access, player inventory/reset/checkpoint state, End dragon reset,
and the explicitly unavailable timer contract. The report also confirms that
the verification entrypoint starts and the server shuts down cleanly.

These are server-side adapter contracts, not client GUI or keybind smoke
tests. The client checklist in `plan.md` remains open until a Minecraft
client can be driven through menu, setup, start, results, restart, and
keybind flows.

The aggregate Gradle task accepts the legacy server's Java home explicitly:
`./gradlew verifyAll -PspeedrunPracticeServerJavaHome116=<JDK-17-home>`.
The 1.21.1 and 26.3 server tasks use the normal Gradle Java runtime.

Earlier temporary 1.21.1 in-game probes also passed on the real server:
worlds 8/8, players/inventories boot checks, structures 8/8, portals 3/3 plus
4-direction pig travel, dragons 5/5, registries 3/3, seeds 7/7, and GUI/timer
server behavior 4/4. The seeds probe cross-checked every prediction against
live `locateStructure` on seed `-5442079527854560511` and RCON-verified
vanilla `/locate` truth.

Key finding: vanilla locate is **first-hit-in-walk-order, not nearest**
(rings expand `0..bound`, border cells in `dx`/`dz` order, first verifying
candidate wins — confirmed against 1.21.1 and 26.3 bytecode, same quirk the
1.16.1 harness documents). The 1.21.1 analyzer mirrors the walk exactly
(seeds re-probed 7/7 after the gate fix below, identical positions).

## Practice-seed gate fix + 26.3 seeds re-verification (2026-09-19)

The 26.3 analyzer got the same first-hit fix, then probed 19/19 against
live `findNearestMapStructure` on seeds `12345`, `-5442079527854560511`
and `424242` (5 structures + a bastion-type round-trip per seed, plus the
unknown-id contract; temp probe removed after passing). Two things surfaced
on the way there:

- 26.3 spawn prediction is seed-dependent (`NoiseBasedChunkGenerator`
  searches a spawn target instead of sitting on the origin), so the probe
  reads its centers from the new `SeedAnalyzer263.spawnCenter` API.
- The live presence gate (`StructureCheck`/`StructureLocator`) is built
  with the **server-global seed**, while chunk generation and the analyzer
  use the **practice seed** — one borderline village proved it (gate said
  `START_NOT_PRESENT`, the chunk recorded a valid start). Fixed by a small
  mixin redirecting the global seed to the constructing practice seed
  (`ServerLevelSeedMixin263`, mirrored as `ServerWorldSeedMixin121`;
  also covers the dragon fight and random sequences). 1.21.1 was
  re-probed 7/7 after its mirror fix with identical positions.

## Update protocol

When a feature is tested in-game on a version, flip its cell to ✅ and note
the date/build in the row or commit message. If in-game validation cannot run
(e.g. headless CI), mark the work `compiled/unverified`, never `working`
(plan section 96).
