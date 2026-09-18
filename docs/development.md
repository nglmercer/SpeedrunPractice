# Development

## Builds

```bash
./gradlew :common:test                                # smallest check first
./gradlew :common:build :practices:build :seed-search:build :test-support:build
./gradlew :versions:fabric-1.16.1:build :versions:fabric-1.21.1:build :versions:fabric-26.3:build
sh scripts/verify-architecture.sh                   # offline import guard (no JDK needed)
# Windows: powershell -File scripts/verify-architecture.ps1
./gradlew build                                       # everything incl. legacy Loom (needs network)
```

Shared modules have no dependencies and compile offline; only the root Loom
project downloads Minecraft artifacts.

## Tests

- `common`: JSON, seed sources/queries/filters, two-stage search, scenario
  loader, timer, stats, loadouts, checkpoints, config migration, seed store,
  plus the `ArchitectureTest` import guard.
- `practices`: `ScenarioEngineTest` runs every registry scenario plus engine
  reset/checkpoint flows against the `ScenarioTestHarness` fake adapter.
- Known-seed tables live in `test-data/<version>/`; `KnownSeeds` loads them
  and `verified:false` rows are explicitly unchecked placeholders.

## Adding things

- Practice: JSON in `definitions/practices/` if the schema fits, else a
  `PracticeScenario` in `:practices` + `ScenarioRegistry` entry + test-data
  rows + `docs/practices.md`.
- Filter: implement `SeedFilters.Filter` on analyzer findings; add a case to
  `SeedFiltersTest`.
- Minecraft version: new `versions/fabric-<mc>/` module implementing
  `MinecraftAdapter`; never copy practice code — fix the abstraction.
- Command/GUI surface: extend the `PracticeCommands` tree /
  `PracticeMenuModel` first, then wire each version's adapter.

See `CONTRIBUTING.md` for workflow/commit rules and `ARCHITECTURE.md` for
the module map. Single-player scope only: no ranked/multiplayer/online work.
