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

## Capability matrix (initial)

No adapter claims any capability yet: `supports()` returns `false` for every
`Capability` on all three versions (plan sections 7 and 98). A capability may
return `true` only after its implementation exists, compiles, and passes
in-game testing on that version.

| Capability | 1.16.1 | 1.21.1 | 26.3 |
| ---------- | :----: | :----: | :--: |
| `CUSTOM_DIMENSION_RUNTIME` | no (adapter pending) | no (adapter pending) | no (adapter pending) |
| `FAST_WORLD_RESET` | no (adapter pending) | no (adapter pending) | no (adapter pending) |
| `BASTION_TYPE_QUERY` | no (adapter pending) | no (adapter pending) | no (adapter pending) |
| `DRAGON_FORCE_PERCH` | no (adapter pending) | no (adapter pending) | no (adapter pending) |
| `PORTAL_STATE_CAPTURE` | no (adapter pending) | no (adapter pending) | no (adapter pending) |
| `STRUCTURE_METADATA_SEARCH` | no (adapter pending) | no (adapter pending) | no (adapter pending) |

GUI/commands consult `supports()` and disable or explain unsupported options.
Per-version runtime status lives in `docs/version-status.md`.

## Loom wiring

1.16.1 is a full Loom module: `AdapterSet116` delegates to the live
`adapter116/live/` implementation (`LiveAdapter116` + sub-adapters +
`Runtime116` bootstrap) in this module's `src/`, and the module builds the
remapped playable jar. The 1.21.1 module still compiles as plain Java so
shared code stays verifiable offline. Full in-game wiring per remaining
module:

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

The 1.21.1 module already carries the mapping-free slices of the port:
`adapter121/RegistryIds` (item-id normalization) and
`adapter121/live/FeatureIds121` (preset-id vocabulary, aliases, dimension
homes, bastion/stronghold predicates), both unit-tested. The Loom conversion
itself is blocked offline: the Gradle cache holds Minecraft/intermediary/
yarn artifacts only for 1.16.1 and 26.3, plus the 1.16.1-era and 26.3-era
loader/fabric-api lines — no 1.21.1 artifacts. The online step is: add the
loom plugin + `minecraft`/`mappings`/loader/fabric-api deps for 1.21.1 to
`versions/fabric-1.21.1/build.gradle` (Java 21), then port the live slices
(entrypoint, commands, events, worlds, players, inventories, structures,
portals, dragon, GUI, keybinds, `SeedAnalyzer121`) against 1.21.1 mappings,
mirroring `adapter116/live/` and `adapter263/live/`. Until then the module
stays plain Java so `:versions:fabric-1.21.1:build --offline` keeps passing.

Do not unify mixin targets across versions; per-version mixins are expected.
Do not touch `common` just to fix mapping names.

## 26.3 port state

`LiveAdapter263` (behind the `AdapterSet263` shell) delegates to live
slices for commands (`LiveCommands263`), seeds (`SeedAnalyzer263`, real
worldgen analysis), worlds (`LiveWorlds263`/`LiveWorld263`), players
(`LivePlayers263`/`LivePlayer263`), inventories (`LiveInventories263`),
an interim `RegistryIds`-based registry, and event polling
(`EventPoller263`). Structures, portals, dragon and GUI still
`throw pending(...)` (`LiveAdapter263.java` lines 74-127), and `supports()`
still claims nothing — verified by
`scripts/verify-supported-versions.ps1` (exit 0, 2026-09-18). Runtime
truth per feature lives in `docs/version-status.md`; anything needing a
player is still unverified.
