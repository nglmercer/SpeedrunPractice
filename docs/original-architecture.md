# Original architecture (pre-migration baseline, 1.16.1)

Single-module Fabric mod, Java 8, Gradle 7.3 wrapper, `fabric-loom
0.10-SNAPSHOT`. All paths below are the untouched `src/` runtime.

## Build / dependencies

- `minecraft_version=1.16.1`, `yarn_mappings=1.16.1+build.21`,
  `loader_version=0.12.12`, `fabric_version=0.18.0+build.387-1.16.1`
  (`gradle.properties`).
- Bundled deps: fabric-api-base, fabric-commands-v0, fabric-command-api-v1,
  fabric-resource-loader-v0, PTLib (`Gregor0410:PTLib`), Cloth Config 2
  (`config-2:4.6.0`), compileOnly SpeedRunIGT, Guava/Gson via Minecraft.
- License: MIT (`LICENSE`), preserved.
- `fabric.mod.json`: id `speedrun-practice`, entrypoint
  `com.gregor0410.speedrunpractice.SpeedrunPractice`, mixins
  `SpeedrunPractice.mixins.json`, breaks `lazystronghold<1.1.0` and
  `speedrunigt<10.3`.

## Package structure

```text
com.gregor0410.speedrunpractice
├── SpeedrunPractice      ModInitializer: loads config, seed list, PTLib config,
│                         registers commands, update check, optional IGT hookup
├── SeedManager           seed-list file (config/speedrun-practice-seeds.txt)
│                         with round-robin index; else random longs
├── AutoSaveStater        advancement-driven split snapshots for /practice revert
├── SpeedrunIGTInterface  reflection hook: resetTimer + TimerState save/restore
├── UpdateChecker         background GitHub releases poll
├── IMinecraftServer      createEndPracticeWorld / createLinkedPracticeWorld
├── command/              Command (dispatcher), Practice (shared helpers),
│                         End/Nether/Overworld/PostBlind/Stronghold/
│                         BuriedTreasure/Revert practice commands
├── config/ModConfig      JSON config + Cloth Config screen, 3 inventory slots
│                         per practice, bastion/dragon/tower settings via PTLib
├── world/PracticeWorld   ServerWorld subclass with own seed + linked
│                         overworld/nether/end registry keys
└── mixin/                17 server mixins + 1 client mixin (list below)
```

## Commands (existing)

`/practice end|nether|overworld [bt]|postblind [maxDist]|stronghold [seed]`,
`/practice seed [seed]`, `/practice seedlist reload|toggle`,
`/practice <type> inventory <1-3> select|save`, `/practice world`,
`/practice revert <split>`, `/instaperch` (forces dragon perch via `/data`).

Core helper is `Practice.linkedPracticeWorldPractice(...)`: creates linked
practice worlds, revokes advancements, then on the server thread teleports,
resets the player (health/XP/food/effects/velocity/air/credits), applies the
saved inventory, and restarts the IGT timer. End practice additionally resets
dragon-fight NBT and builds the spawn platform.

## Config (`config/speedrun-practice.json`)

`practiceInventories` (NBT strings per practice x 3 slots),
`practiceSlots`, PTLib `ptConfig` (bastion toggles/rarity, dragon type,
tower toggles, structure regions), `defaultMaxDist`, `calcMode`,
`deletePracticeWorlds`, `postBlindSpawnChunks`, `caveSpawns`,
`randomisePostBlindInventory`, `useSeedList`.

## Mixins

World-routing: `MinecraftServerMixin` (implements `IMinecraftServer`;
creates/deletes practice worlds), `EntityMixin` (nether-portal targets),
`EndPortalBlockMixin` (end-portal targets), `AbstractFireBlockMixin`,
`DebugHudMixin` (client), `KeyboardMixin` (F3+C calc mode) map practice
worlds back to vanilla keys. Accessors: `DimensionTypeAccess`,
`EnderDragonFightAccess`, `LevelPropertiesAccess`, `MinecraftServerAccess`,
`ServerPlayerEntityAccess`, `ServerWorldAccess`,
`ThreadedAnvilChunkStorageAccess`.
Features: `PlayerAdvancementTrackerMixin` (split snapshots),
`PlayerManagerMixin` (join/welcome), `ServerPlayerEntityMixin`,
`PortalForcerMixin`, `OptionsScreenMixin` (settings button).

## World generation / seeds / inventories / IGT

- Practice worlds are real `ServerWorld`s with a custom seed and linked
  dimensions; post-blind picks a seeded-Random point `maxDist` from the
  stronghold, stronghold practice walks structure starts to the portal room,
  buried treasure locates the structure then a nearby beach biome.
- Structure generation (bastion types/rarity, nether region size, towers,
  dragon type) is delegated to PTLib config.
- Inventories are NBT-string lists with 3 save slots per practice; post-blind
  can top up via the piglin-bartering loot table.
- SpeedRunIGT is optional (`compileOnlyApi`, loaded via reflection) and only
  reset on practice start; revert restores its `TimerState`.

## Practice types today

End, Nether, Post-blind, Overworld (+Buried Treasure mode), Stronghold, plus
seed list, inventory presets, revert/autosave, IGT integration, structure-gen
settings. Bastion/Fortress/Blind/One-Cycle dedicated scenarios, seed search,
checkpoints, internal timer/stats, keybinds, and the GUI do not exist yet.
