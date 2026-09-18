# ARCHITECTURE

One practice engine, one scenario system, one seed system, one statistics
system, three Minecraft adapters. This is not three separate mods and not a
multiplayer Ranked clone (plan sections 45, 54).

## Module layout

```text
:common                  pure Java 8, ZERO Minecraft imports (guarded by build)
:practices               version-independent scenarios, depends on :common
:seed-search             cancellable search + cache, depends on :common
:test-support            known seeds + fake adapters for tests
:versions:fabric-1.16.1  1.16.1 adapter set (primary speedrunning build)
:versions:fabric-1.21.1  1.21.1 adapter set (modern compatibility build)
:versions/fabric-26.3    26.3 adapter set (latest supported build)
(root project)           original Fabric Loom 1.16.1 runtime in src/ (untouched)
```

Dependency direction is strictly `common <- practices <- version modules`.
`common -> Minecraft/Fabric/mixins` is a build failure
(`:common:verifyNoMinecraftImports`, plus `scripts/verify-architecture.py`).

## Shared packages (`com.gregor0410.speedrunpractice.common`)

| Package     | Contents |
| ----------- | -------- |
| `api`       | Domain model: versions, practice ids/types, session, context, scenario lifecycle, exceptions |
| `adapter`   | Version interfaces: world, player, inventory, structure, portal, dragon, registry, command, GUI, timer + `Capability` |
| `engine`    | `ScenarioEngine`: one lifecycle + reset-mode handling for every practice |
| `checkpoint`| Checkpoint + snapshots + `CheckpointManager` |
| `timer`     | `PracticeTimer` abstraction + monotonic implementation |
| `stats`     | Attempt statistics, PB/average/median, JSON + CSV export |
| `config`    | Versioned config (`schemaVersion`) + v1 -> v2 migration with backup |
| `loadout`   | Named inventory presets + manager (save/duplicate/rename/delete/export/import) |
| `seeds`     | `SeedSource`s, `SeedQuery` builder, filters, two-stage search, `SeedStore` persistence |
| `scenario`  | Data-driven JSON definitions + validating loader (readable errors, never crashes) |
| `gui`       | Menu / scenario / results screen models (rendered by version `GuiAdapter`s) |
| `input`     | Configurable keybind actions + defaults |
| `commands`  | `/practice` command-tree model registered by version `CommandAdapter`s |
| `util`      | Dependency-free JSON (`SimpleJson`) + prefixed logging (`SpeedrunLogger`) |

## Version modules

Each version module implements `MinecraftAdapter` plus the eleven
sub-adapters and `SeedAnalyzer` against that version's mappings, and declares
support via `supports(Capability)`. GUI disables options whose capability is
unsupported instead of crashing. Mixins stay version-local:
`versions/fabric-<mc>/src/main/resources/mixins.json`.

## Threading

Background threads do seed math, parsing, stats, disk I/O. All Minecraft
state mutation returns to the game/server thread via the adapter (the legacy
1.16.1 path already does this with `server.execute(...)`; new adapters keep
that contract).

## Decisions

- `common` is dependency-free (own tiny JSON + logging) so no library availability
  can block a Minecraft target (plan section 34).
- SpeedRunIGT stays optional behind `TimerAdapter`; the internal monotonic
  timer always works without it.
- Legacy `src/` runtime is preserved untouched until each behavior reaches
  parity behind an adapter (plan section 5); see
  `docs/original-feature-matrix.md`.
