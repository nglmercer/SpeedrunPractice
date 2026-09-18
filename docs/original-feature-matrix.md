# Original feature matrix

Baseline from the untouched 1.16.1 `src/` runtime. "Migrated" means an
equivalent exists behind the new adapter architecture (`common` + `practices`
+ version adapters); the legacy path is preserved until parity is verified
in-game per practice.

| Feature | Existing | Migrated | Tested |
| ------- | -------: | -------: | -----: |
| End practice | Yes | Yes (`EndScenario`) | Unit (harness) |
| Nether practice | Yes | Yes (`NetherScenario`) | Unit (harness) |
| Postblind | Yes | Yes (`PostBlindScenario`) | Unit (harness) |
| Overworld | Yes | Yes (`OverworldScenario`) | Unit (harness) |
| Buried Treasure | Yes | Yes (`BuriedTreasureScenario`) | Unit (harness) |
| Stronghold | Yes | Yes (`StrongholdScenario`) | Unit (harness) |
| Seed list | Yes | Yes (`SeedListSource`, `SeedStore`) | Yes |
| Inventory presets | Yes | Yes (`Loadout` + `LoadoutManager`) | Yes |
| Revert | Yes (Delorean autosave) | Partial (`CheckpointManager`; world rollback still legacy-only) | Yes (manager) |
| SpeedRunIGT integration | Yes | Yes (`TimerAdapter`, optional) | No (needs game) |
| Structure-gen settings | Yes (PTLib) | Partial (query/filter model; generation still legacy/PTLib) | No (needs game) |
| Bastion practice | No | Yes (`BastionScenario`, new) | Unit (harness) |
| Fortress practice | No | Yes (`FortressScenario`, new) | Unit (harness) |
| Blind Travel | No | Yes (`BlindTravelScenario`, new) | Unit (harness) |
| One Cycle | No | Yes (`OneCycleScenario`, new) | Unit (harness) |
| Custom scenario JSON | No | Yes (`ScenarioLoader` + `CustomScenario`) | Yes |
| Seed search + filters | No | Yes (two-stage engine + filters) | Yes (fake analyzer) |
| Checkpoints | No | Yes (`CheckpointManager`) | Yes |
| Internal timer | No | Yes (`PracticeTimer`) | Yes |
| Statistics | No | Yes (`PracticeStatistics`, JSON+CSV) | Yes |
| GUI | No (Cloth settings only) | Partial (screen models; rendering per version) | No (needs game) |
| Keybinds | No | Yes (configurable actions) | Partial |

Parity checklist per migrated practice: launches, menu works, commands
register, world creation, same/new-seed reset, loadouts, seed list/search —
see plan section 43. Items marked "needs game" require a live client and are
tracked as follow-up manual QA.
