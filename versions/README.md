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

| Capability | 1.16.1 | 1.21.1 | 26.3 |
| ---------- | :----: | :----: | :--: |
| `CUSTOM_DIMENSION_RUNTIME` | yes | yes | no (pending) |
| `FAST_WORLD_RESET` | yes | no (pending) | no (pending) |
| `BASTION_TYPE_QUERY` | yes | yes | no (pending) |
| `DRAGON_FORCE_PERCH` | yes | yes | no (pending) |
| `PORTAL_STATE_CAPTURE` | yes | no (pending) | no (pending) |
| `STRUCTURE_METADATA_SEARCH` | no | yes | no (pending) |

GUI/commands consult `supports()` and disable or explain unsupported options.

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

Do not unify mixin targets across versions; per-version mixins are expected.
Do not touch `common` just to fix mapping names.
