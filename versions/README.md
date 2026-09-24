# Version adapters

## Mapping

| Gradle module | Minecraft | Role |
| ------------- | --------- | ---- |
| `:versions:fabric-1.16.1` | 1.16.1 | Primary speedrunning build |
| `:versions:fabric-1.21.1` | 1.21.1 | Modern compatibility/reference build |
| `:versions/fabric-26.3` | 26.3 | Latest supported build |

Each module implements `MinecraftAdapter` (`AdapterSet116/121/263`) with the
eleven sub-adapters plus `SeedAnalyzer`, and reports gaps via
`supports(Capability)`. The 1.16.1 module is a real Fabric/Loom mod (legacy
runtime + new live adapter, migrated from root `src/` in plan step 12);
adapter methods that still need live Minecraft calls fail with a domain
`AdapterException` carrying a user-facing message, never an NPE.

## Capability matrix

A capability returns `true` only after its implementation exists, compiles,
and passes in-game testing on that version (plan sections 7 and 98).

| Capability | 1.16.1 | 1.21.1 | 26.3 |
| ---------- | :----: | :----: | :--: |
| `CUSTOM_DIMENSION_RUNTIME` | yes (headless 2026-09-22) | yes (headless 2026-09-19) | yes (headless 2026-09-18) |
| `FAST_WORLD_RESET` | no (rebuild-only by design) | no (rebuild-only by design) | no (rebuild-only by design) |
| `BASTION_TYPE_QUERY` | yes (headless 2026-09-22 differential) | yes (headless 2026-09-19) | yes (headless 2026-09-18) |
| `DRAGON_FORCE_PERCH` | no | no (player-gated) | no (player-gated) |
| `PORTAL_STATE_CAPTURE` | no | no (no API) | no (no API) |
| `STRUCTURE_METADATA_SEARCH` | no | no | no |

GUI/commands consult `supports()` and disable or explain unsupported options.
Per-version runtime status lives in `docs/version-status.md`.

## Loom wiring

1.16.1, 1.21.1 and 26.3 are all full Loom modules: each `AdapterSet`
delegates to a live `adapter*/live/` implementation (`LiveAdapter*` +
sub-adapters + `Runtime*` bootstrap) in its module's `src/`, and each
module builds a remapped playable jar. Reference wiring per module:

```text
versions/fabric-<mc>/
├── build.gradle            (add loom plugin + minecraft/mappings/loader deps)
└── src/main/
    ├── java/.../           (move AdapterSet* onto real mappings)
    └── resources/
        ├── fabric.mod.json (per-version id/version/depends)
        └── mixins.json     (version-local mixins only)
```

`CommandAdapter.register` already retains the shared tree plus its executor
(`AdapterSet*.registeredCommands()` / `commandExecutor()`), so the entrypoint
can build Brigadier nodes from the last registration once mappings land
instead of rebuilding that state.

## 1.21.1 port state

The 1.21.1 module is a full Loom module (Java 21, yarn mappings) with every
slice live behind the `AdapterSet121` shell: entrypoint, commands, worlds,
players, inventories, structures, portals, dragon, registries, GUI +
keybinds, timer bridge, and `SeedAnalyzer121`. No `pending()` remains.
Headless-verified on the real 1.21.1 dedicated server 2026-09-19: worlds
8/8, structures 8/8, portals 3/3 plus 4-direction pig travel, dragons 5/5,
registries 3/3, seeds 7/7 against live `locateStructure` (walk-order
first-hit, bytecode-verified), GUI/timer server behavior 4/4. The
2026-09-22 per-suite sweep re-verified every suite headless (see
`docs/version-status.md`), including engine practice starts via the
harness player; literal command parsing still needs a real player, and
screens/keybind presses still need a client. The 26.3 probe exposed a main-seed presence
gate on practice worlds (same bug shape on 1.21.1, confirmed in bytecode);
`ServerWorldSeedMixin121` redirects it to the practice seed, and seeds
re-probed 7/7 afterwards with identical positions.

Do not unify mixin targets across versions; per-version mixins are expected.
Do not touch `common` just to fix mapping names.

## 26.3 port state

`LiveAdapter263` (behind the `AdapterSet263` shell) delegates to live
slices for every sub-adapter plus `SeedAnalyzer263`, with no `pending()`
left; `supports()` claims `CUSTOM_DIMENSION_RUNTIME` + `BASTION_TYPE_QUERY`
(headless-verified 2026-09-18). Runtime truth per feature lives in
`docs/version-status.md`; engine practice starts are verified headless via
the harness player, literal command parsing still needs a real player,
and screens/keybind presses still need a client.
2026-09-19: `SeedAnalyzer263` got the walk-order first-hit fix (same root
cause as 1.21.1, both bytecode-verified) plus a public `spawnCenter` API
for the seed-dependent 26.3 spawn, and probed 19/19 against live locate on
3 seeds; the probe also exposed the main-seed presence gate, fixed by
`ServerLevelSeedMixin263` (see `docs/version-status.md`).
