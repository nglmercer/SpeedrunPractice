# Version adapters

## Mapping

| Gradle module | Minecraft | Role |
| ------------- | --------- | ---- |
| `:versions:fabric-1.16.1` | 1.16.1 | Primary speedrunning build |
| `:versions:fabric-1.21.1` | 1.21.1 | Modern compatibility/reference build |
| `:versions/fabric-26.3` | 26.3 | Latest supported build |

Each module implements `MinecraftAdapter` (`AdapterSet116/121/263`) with the
eleven sub-adapters plus `SeedAnalyzer`, and reports gaps via
`supports(Capability)`. The legacy 1.16.1 Loom runtime in root `src/` stays
authoritative in-game until each adapter method reaches parity; adapter
methods that still need live Minecraft calls fail with a domain
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

## Loom wiring (follow-up)

The version modules currently compile as plain Java so shared code stays
verifiable offline. Full in-game wiring per module:

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

Do not unify mixin targets across versions; per-version mixins are expected.
Do not touch `common` just to fix mapping names.
