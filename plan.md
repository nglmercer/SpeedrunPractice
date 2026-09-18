# Speedrun Practice — LLM Implementation Workflow

## 0. Mission

Build a **single-player Minecraft speedrun practice suite** based on the existing `SpeedrunPractice` project.

Base project:

```text
https://github.com/nglmercer/SpeedrunPractice
```

The project must remain focused on **solo practice**.

Do NOT implement:

* MCSR Ranked matchmaking
* Elo/rating systems
* multiplayer racing
* opponent synchronization
* remote match servers
* account authentication
* anti-cheat
* spectator synchronization
* seasons
* queues
* ranked APIs
* result submission to Ranked
* any dependency on MCSR Ranked servers

The final mod should provide a local experience similar to the useful practice portions of speedrunning tools:

* fast practice setup
* filtered/searchable seeds
* scenario presets
* practice checkpoints
* instant reset
* split-specific practice
* timing
* statistics
* configurable inventories
* custom practice definitions

---

# 1. Supported Minecraft Versions

Maintain exactly three Minecraft version targets.

```text
1.16.1
1.21.1
26.3
```

Version roles:

```text
1.16.1 = primary speedrunning build
1.21.1 = modern compatibility/reference build
26.3   = latest supported build
```

Do NOT create three unrelated forks.

Use one repository containing:

```text
shared/common code
+
three Minecraft-specific adapters
```

The build should produce three separate jars.

Example:

```text
speedrun-practice-1.16.1-x.y.z.jar
speedrun-practice-1.21.1-x.y.z.jar
speedrun-practice-26.3-x.y.z.jar
```

---

# 2. Core Architecture Rule

The most important architecture constraint is:

```text
common/ MUST NOT import net.minecraft.*
```

Minecraft code belongs only in version-specific modules.

Target architecture:

```text
speedrun-practice/
│
├── common/
│   ├── api/
│   ├── engine/
│   ├── scenario/
│   ├── seeds/
│   ├── loadout/
│   ├── checkpoint/
│   ├── timer/
│   ├── stats/
│   └── config/
│
├── practices/
│   ├── overworld/
│   ├── buried-treasure/
│   ├── nether/
│   ├── bastion/
│   ├── fortress/
│   ├── blind/
│   ├── postblind/
│   ├── stronghold/
│   ├── end/
│   ├── onecycle/
│   └── custom/
│
├── versions/
│   ├── fabric-1.16.1/
│   ├── fabric-1.21.1/
│   └── fabric-26.3/
│
├── definitions/
│   ├── practices/
│   ├── loadouts/
│   └── seed-filters/
│
├── docs/
├── scripts/
└── .github/
```

If necessary, adapt the directory names to Gradle conventions, but preserve the separation.

---

# 3. Development Philosophy

Always prefer:

```text
shared abstraction
```

over:

```text
version checks everywhere
```

Avoid:

```java
if (mcVersion == ...)
```

inside common practice logic.

Instead use interfaces.

Example:

```java
public interface MinecraftAdapter {
    GameVersion version();

    PracticeWorld createWorld(
        long seed,
        PracticeWorldOptions options
    );

    void destroyWorld(PracticeWorld world);

    void teleport(
        PracticePlayer player,
        PracticePosition position
    );

    void applyLoadout(
        PracticePlayer player,
        Loadout loadout
    );

    StructureResult locateStructure(
        PracticeWorld world,
        StructureQuery query
    );
}
```

Implement separately:

```text
MinecraftAdapter116
MinecraftAdapter121
MinecraftAdapter263
```

---

# 4. Important Rules for Coding Agents

Before changing code:

1. Inspect the repository.
2. Read existing implementation.
3. Identify which module owns the behavior.
4. Avoid duplicate systems.
5. Preserve already-working behavior.
6. Add tests where practical.
7. Run relevant builds/tests.
8. Do not rewrite unrelated code.
9. Do not silently remove existing functionality.
10. Document architectural decisions.

Never make a massive rewrite in one commit.

Implement features incrementally.

Each phase must end in a buildable state.

---

# 5. Existing Functionality To Preserve

The original project already contains behavior for areas such as:

```text
Overworld practice
Nether practice
Post-blind practice
Stronghold practice
End practice
Buried Treasure practice
Inventory/loadout saving
Seed lists
Seed selection
Practice worlds
Revert/autosave functionality
SpeedRunIGT integration
Structure-generation settings
```

Do not remove these until equivalent functionality exists in the new architecture.

When replacing existing code:

```text
old implementation
    ↓
adapter/interface
    ↓
new implementation
```

Verify parity before deleting the old implementation.

---

# 6. Phase 1 — Baseline Repository

## Goal

Get the existing 1.16.1 project reproducibly building.

Tasks:

```text
[ ] Clone/fork project
[ ] Confirm license
[ ] Preserve MIT license
[ ] Update project metadata
[ ] Rename organization/package only if necessary
[ ] Confirm Gradle wrapper works
[ ] Build original 1.16.1 jar
[ ] Record known compiler warnings
[ ] Record current commands
[ ] Record current mixins
[ ] Record current config structure
[ ] Record current practice types
[ ] Add CONTRIBUTING.md
[ ] Add ARCHITECTURE.md
[ ] Add this AGENTS.md
```

Create:

```text
docs/original-feature-matrix.md
```

Containing:

| Feature           | Existing | Migrated | Tested |
| ----------------- | -------: | -------: | -----: |
| End practice      |      Yes |       No |     No |
| Nether            |      Yes |       No |     No |
| Postblind         |      Yes |       No |     No |
| Overworld         |      Yes |       No |     No |
| Buried Treasure   |      Yes |       No |     No |
| Stronghold        |      Yes |       No |     No |
| Seed list         |      Yes |       No |     No |
| Inventory presets |      Yes |       No |     No |
| Revert            |      Yes |       No |     No |

Acceptance criteria:

```text
./gradlew build
```

must pass for the original 1.16.1 implementation.

Do not begin modern-version support before this succeeds.

---

# 7. Phase 2 — Create Multi-Module Build

Introduce a Gradle multi-project architecture.

Suggested modules:

```text
:common
:practices
:versions:fabric-1.16.1
:versions:fabric-1.21.1
:versions:fabric-26.3
```

Optional modules:

```text
:seed-search
:practice-api
:test-support
```

The dependency direction must be:

```text
common
 ↑
practices
 ↑
version modules
```

Never:

```text
common -> Minecraft
common -> Fabric
common -> mixins
```

Acceptance criteria:

```text
common compiles independently
1.16.1 module builds
modern modules may initially contain skeletons
```

---

# 8. Phase 3 — Core Domain API

Create stable non-Minecraft domain objects.

Suggested types:

```java
GameVersion
PracticeId
PracticeType
PracticeSession
PracticeContext
PracticeState
PracticeResult
PracticeResultStatus

PracticeWorld
PracticePlayer
PracticePosition
PracticeDimension

PracticeScenario
PracticeSettings
PracticePreset

Loadout
LoadoutItem
InventorySlot

SeedSource
SeedQuery
SeedCandidate
SeedResult

Checkpoint
CheckpointManager

PracticeTimer
PracticeStatistics
```

Avoid exposing Minecraft classes through these APIs.

Bad:

```java
ServerPlayerEntity getPlayer();
```

Good:

```java
PracticePlayer player();
```

---

# 9. Phase 4 — Adapter API

Create version interfaces.

At minimum:

```text
MinecraftAdapter
WorldAdapter
PlayerAdapter
InventoryAdapter
StructureAdapter
PortalAdapter
DragonAdapter
RegistryAdapter
CommandAdapter
GuiAdapter
TimerAdapter
```

Potential API:

```java
public interface WorldAdapter {

    PracticeWorld createPracticeWorld(
        long seed,
        PracticeWorldOptions options
    ) throws PracticeException;

    void deletePracticeWorld(
        PracticeWorld world
    );

    void resetPracticeWorld(
        PracticeWorld world,
        long seed,
        PracticeWorldOptions options
    );
}
```

Structure abstraction:

```java
public interface StructureAdapter {

    Optional<StructureLocation> locateNearest(
        PracticeWorld world,
        StructureQuery query
    );

    List<StructureLocation> locate(
        PracticeWorld world,
        StructureQuery query,
        int limit
    );
}
```

Player abstraction:

```java
public interface PlayerAdapter {

    void teleport(
        PracticePlayer player,
        PracticePosition position
    );

    void setHealth(
        PracticePlayer player,
        double health
    );

    void setFood(
        PracticePlayer player,
        int food
    );

    void clearEffects(
        PracticePlayer player
    );

    void applyLoadout(
        PracticePlayer player,
        Loadout loadout
    );
}
```

---

# 10. Phase 5 — Migrate Existing 1.16.1 Behavior

Do not add many new features yet.

Move existing functionality behind adapters.

Recommended migration order:

```text
1. Seed manager
2. Configuration
3. Inventories/loadouts
4. Player teleportation
5. Practice world creation
6. Overworld practice
7. Nether practice
8. Stronghold
9. Postblind
10. End practice
11. Buried Treasure
12. Revert/checkpoints
13. Dragon utilities
```

After each migration:

```text
build
test
manual sanity check
commit
```

Do not migrate everything in one change.

---

# 11. Phase 6 — Scenario Engine

Every practice should use one lifecycle.

Example:

```java
public interface PracticeScenario {

    PracticeId id();

    void prepare(
        PracticeContext context
    );

    void start(
        PracticeContext context
    );

    PracticeTickResult tick(
        PracticeContext context
    );

    void reset(
        PracticeContext context,
        ResetMode mode
    );

    void stop(
        PracticeContext context
    );
}
```

Reset modes:

```java
SAME_SEED
NEW_SEED
PREVIOUS_SEED
CHECKPOINT
FULL_RESET
```

A scenario must not directly manipulate Minecraft internals.

---

# 12. Phase 7 — Practice Modules

Implement these practice categories.

## Overworld

```text
random spawn
village
shipwreck
buried treasure
lava pool
portal entry
custom structure start
```

## Nether

```text
portal exit
navigation
bastion locating
fortress locating
bastion → fortress
fortress → bastion
blaze practice
pearl collection setups
```

## Bastion

Support:

```text
Housing
Stables
Treasure
Bridge
Random
```

Possible start modes:

```text
outside bastion
entrance
known route start
random valid exterior position
portal exit nearby
```

Configuration:

```text
loadout
armor
blocks
pickaxe
food
health
difficulty
piglin state
randomized item quantities
```

## Fortress

Support:

```text
find fortress
enter fortress
blaze collection
navigation
exit practice
```

## Blind Travel

Support:

```text
random nether coordinate
specified target distance
portal construction setup
blind conversion
overworld stronghold distance
```

## Post-Blind

Support:

```text
random post-blind position
max/min stronghold distance
eye count
inventory presets
stronghold triangulation
```

## Stronghold

Support:

```text
stronghold entry
navigation
portal room search
portal room start
pre-eye setup
custom eye counts
```

## End

Support:

```text
end entry
tower practice
dragon fight
bed cycle
one-cycle
instaperch
custom inventory
random tower states
```

## Custom

User-defined scenario.

---

# 13. Phase 8 — Data-Driven Practice Definitions

Where possible, practice variants should be defined through JSON.

Example:

```json
{
  "id": "bastion_housing_default",
  "type": "bastion",
  "displayName": "Housing Bastion Practice",

  "world": {
    "dimension": "nether"
  },

  "seed": {
    "source": "search",
    "filters": [
      {
        "type": "bastion_type",
        "value": "housing"
      }
    ]
  },

  "spawn": {
    "type": "structure_exterior",
    "distance": 40
  },

  "loadout": "bastion_default",

  "timer": {
    "start": "player_move",
    "stop": "scenario_complete"
  }
}
```

Create schema validation.

Bad JSON should produce a readable error.

Never crash Minecraft because a user scenario has invalid JSON.

---

# 14. Phase 9 — Loadout System

Create named loadouts.

Example:

```json
{
  "id": "bastion_default",
  "items": [
    {
      "item": "minecraft:iron_pickaxe",
      "slot": 0,
      "count": 1
    },
    {
      "item": "minecraft:bread",
      "slot": 1,
      "count": 16
    }
  ]
}
```

Features:

```text
[ ] named presets
[ ] save current inventory
[ ] duplicate preset
[ ] rename preset
[ ] delete preset
[ ] export preset
[ ] import preset
[ ] version-aware item handling
```

Do not store Minecraft NBT directly in shared domain models unless wrapped behind an adapter.

---

# 15. Phase 10 — Seed Source System

Create:

```java
public interface SeedSource {

    OptionalLong nextSeed(
        SeedRequest request
    );

    OptionalLong previousSeed();

    OptionalLong currentSeed();
}
```

Implement:

```text
RandomSeedSource
FixedSeedSource
SeedListSource
SearchSeedSource
FavoriteSeedSource
RecentSeedSource
ImportedSeedSource
```

---

# 16. Phase 11 — Seed Search Engine

This is a major feature.

Create query objects such as:

```java
SeedQuery.builder()
    .version(GameVersion.MC_1_16_1)
    .requireBiome(...)
    .requireStructure(...)
    .maxDistance(...)
    .build();
```

Do NOT put actual structure-generation implementation inside common.

Use:

```java
SeedAnalyzer
```

with implementation per Minecraft target.

Example:

```java
public interface SeedAnalyzer {

    SeedAnalysis analyze(
        long seed,
        SeedQuery query
    );

    boolean matches(
        long seed,
        SeedQuery query
    );
}
```

---

# 17. Seed Search Filters

Implement filters incrementally.

## Overworld

```text
spawn biome
spawn coordinates
village distance
shipwreck distance
buried treasure distance
ruined portal distance
lava source availability
ocean proximity
desert proximity
```

## Nether

```text
nether spawn coordinates
bastion distance
bastion type
fortress distance
bastion + fortress combination
fortress after bastion distance
terrain constraints
```

## Stronghold

```text
stronghold distance
stronghold ring
portal room characteristics
eye count where deterministically available
```

## Combined

Examples:

```text
good overworld + housing bastion
close bastion + close fortress
bad blind practice
far blind practice
stronghold navigation
specific bastion type + fortress range
```

---

# 18. Two-Stage Seed Searching

Use two stages.

## Stage A — Fast Filter

Avoid full world generation when possible.

```text
seed math
structure-position calculations
biome checks
cheap rejection tests
```

## Stage B — Verification

For surviving seeds:

```text
create/generate required chunks
inspect actual target structures
verify assumptions
return verified result
```

A seed search result must record:

```java
seed
minecraftVersion
matchedFilters
structureLocations
verificationState
searchTimestamp
```

---

# 19. Seed Search Persistence

Store results locally.

Suggested structure:

```text
config/speedrun-practice/
└── seeds/
    ├── favorites.json
    ├── recent.json
    ├── searches/
    │   ├── housing.json
    │   └── close-fortress.json
    └── imports/
        └── user-list.txt
```

Support:

```text
favorites
tags
notes
failed seeds
recent seeds
search history
named collections
```

---

# 20. Phase 12 — Commands

Maintain a clear root command.

```text
/practice
```

Suggested commands:

```text
/practice start <type>
/practice restart
/practice restart same
/practice restart new
/practice stop

/practice seed
/practice seed <seed>
/practice seed next
/practice seed previous
/practice seed favorite

/practice seeds search <preset>
/practice seeds cancel
/practice seeds results
/practice seeds export
/practice seeds import

/practice loadout list
/practice loadout save <name>
/practice loadout apply <name>
/practice loadout delete <name>

/practice checkpoint save
/practice checkpoint load
/practice checkpoint clear

/practice stats
/practice stats <practice>
/practice stats reset

/practice config reload
```

Keep legacy aliases where reasonable.

---

# 21. Phase 13 — Practice GUI

Create a simple GUI.

Main screen:

```text
Speedrun Practice

[ Overworld ]
[ Nether ]
[ Bastion ]
[ Fortress ]
[ Blind Travel ]
[ Post Blind ]
[ Stronghold ]
[ End ]
[ One Cycle ]
[ Custom ]
```

Scenario screen:

```text
Practice: Housing Bastion

Seed Source:
    Search

Loadout:
    Bastion Default

Bastion Type:
    Housing

Fortress:
    Required

Fortress Distance:
    200 - 500

[ Start ]
```

After completion:

```text
Time: 01:42.317
PB:   01:39.114

[ Retry Same Seed ]
[ New Seed ]
[ Previous Seed ]
[ Main Menu ]
```

---

# 22. Phase 14 — Keybinds

Provide configurable keybinds.

Suggested defaults:

```text
Restart same seed
Restart new seed
Previous seed
Save checkpoint
Load checkpoint
Stop practice
Open practice menu
```

Do not hardcode keys without user configuration.

---

# 23. Phase 15 — Checkpoints

Checkpoint state should include only necessary data.

Possible fields:

```java
record PracticeCheckpoint(
    PracticeId practice,
    long seed,
    PracticeDimension dimension,
    PracticePosition position,
    PlayerSnapshot player,
    ScenarioSnapshot scenario,
    TimerSnapshot timer
) {}
```

Player snapshot:

```text
position
rotation
health
food
saturation
experience
inventory
armor
offhand
effects
selected slot
```

Scenario-specific state goes through:

```text
ScenarioSnapshot
```

Avoid serializing the entire Minecraft server.

---

# 24. Phase 16 — Timer

Implement an internal timer abstraction.

Start conditions:

```text
scenario load
first player movement
dimension entry
portal exit
manual start
```

Stop conditions:

```text
practice-specific completion
dimension entry
structure reached
dragon death
manual stop
```

Use monotonic timing where possible.

Support optional SpeedRunIGT integration through an adapter.

SpeedRunIGT must not be required for the basic practice system.

---

# 25. Phase 17 — Statistics

Store statistics locally.

Track:

```text
attempts
completed attempts
completion percentage
PB
average
median
best segment
recent attempts
seed
practice type
scenario preset
Minecraft version
reset reason
```

Suggested data structure:

```json
{
  "practice": "bastion_housing",
  "version": "1.16.1",
  "attempts": 42,
  "completed": 31,
  "pbMs": 87321
}
```

Provide:

```text
JSON export
CSV export
```

No cloud account is required.

---

# 26. Phase 18 — 1.21.1 Port

Do not begin until:

```text
1.16.1 architecture is stable
```

Implement:

```text
MinecraftAdapter121
WorldAdapter121
PlayerAdapter121
StructureAdapter121
PortalAdapter121
DragonAdapter121
RegistryAdapter121
GuiAdapter121
```

Rules:

```text
Do not modify common just to fix mapping names.
Do not leak 1.21 classes into shared code.
Do not copy complete practice implementations.
```

If practice code must be copied, stop and improve the abstraction.

---

# 27. Phase 19 — 26.3 Port

Repeat the adapter implementation.

Expect major changes around:

```text
registries
world generation
dimension creation
structures
commands
screen APIs
network internals
server lifecycle
mixins
```

Do not try to make mixin targets identical across all versions.

Version-specific mixins are expected.

Example:

```text
versions/fabric-1.16.1/src/main/resources/mixins.json
versions/fabric-1.21.1/src/main/resources/mixins.json
versions/fabric-26.3/src/main/resources/mixins.json
```

---

# 28. Phase 20 — Version Capability System

Some functionality may differ between versions.

Represent this explicitly.

Example:

```java
public enum Capability {
    CUSTOM_DIMENSION_RUNTIME,
    FAST_WORLD_RESET,
    BASTION_TYPE_QUERY,
    DRAGON_FORCE_PERCH,
    PORTAL_STATE_CAPTURE,
    STRUCTURE_METADATA_SEARCH
}
```

Adapter exposes:

```java
boolean supports(Capability capability);
```

Avoid crashing if a feature is unavailable.

GUI should disable unsupported options.

---

# 29. Testing Strategy

Create three test layers.

## Unit Tests

Test common code:

```text
seed sources
scenario state
stats calculations
configuration
loadouts
checkpoint serialization
query builders
```

## Adapter Tests

Test interfaces against target versions where practical.

## Known Seed Integration Tests

Maintain known seeds for each version.

Example:

```text
test-data/
├── 1.16.1/
│   ├── structures.json
│   └── scenarios.json
├── 1.21.1/
└── 26.3/
```

For each known seed verify:

```text
expected bastion
expected fortress
expected stronghold
expected spawn
scenario initializes
player loadout correct
reset works
```

---

# 30. CI

Create GitHub Actions.

Run:

```text
common tests
1.16.1 build
1.21.1 build
26.3 build
format/lint
```

Release workflow should generate:

```text
3 jars
checksums
release notes
```

Do not publish a release if one supported version fails.

---

# 31. Commit Workflow

LLM agents should use small commits.

Suggested commit prefixes:

```text
build:
core:
adapter:
practice:
seed:
gui:
config:
test:
docs:
fix:
refactor:
```

Examples:

```text
core: add practice scenario lifecycle

adapter: migrate 1.16 world creation

practice: add housing bastion scenario

seed: add structure distance filters

test: add known 1.16.1 fortress seeds
```

Do not combine unrelated work.

---

# 32. Per-Task Agent Workflow

For every implementation task, follow this exact workflow.

## Step 1 — Inspect

Identify:

```text
existing implementation
relevant modules
relevant interfaces
existing tests
version-specific dependencies
```

## Step 2 — State Intended Change

Before coding, define:

```text
What is changing?
Which module owns it?
Does common API need changing?
Which versions are affected?
How will it be tested?
```

## Step 3 — Implement Minimum Complete Change

Do not add speculative unrelated features.

## Step 4 — Compile

Run the smallest relevant build first.

Example:

```bash
./gradlew :common:test
```

Then:

```bash
./gradlew :versions:fabric-1.16.1:build
```

## Step 5 — Tests

Add or update tests.

## Step 6 — Full Build

Before considering work complete:

```bash
./gradlew build
```

or equivalent aggregate task.

## Step 7 — Report

Return:

```text
Changed files
Implemented behavior
Tests run
Build results
Known limitations
Next recommended task
```

---

# 33. Bug-Fix Workflow

When fixing a bug:

```text
1. Reproduce.
2. Identify owning layer.
3. Add regression test if practical.
4. Fix smallest responsible component.
5. Verify all three supported versions if shared code changed.
```

Never patch symptoms in GUI code if the bug belongs to the scenario engine.

Never patch version-specific code inside `common`.

---

# 34. Dependency Policy

Prefer minimal dependencies.

Dependencies must have:

```text
clear purpose
compatible license
active availability
version support
```

Avoid tying the core to libraries existing only for 1.16.1.

Wrap optional integrations behind interfaces.

Example:

```text
SpeedRunIGTIntegration
```

instead of directly importing SpeedRunIGT throughout the project.

---

# 35. Configuration Migration

Existing users should not lose settings without warning.

Create versioned configuration.

Example:

```json
{
  "schemaVersion": 2
}
```

Provide migration:

```text
v1 -> v2
v2 -> future versions
```

On failure:

```text
back up original config
log readable error
generate safe default
```

Never silently delete user configuration.

---

# 36. Logging

Use meaningful logging.

Good:

```text
[SpeedrunPractice] Searching seeds with preset housing-close-fortress
[SpeedrunPractice] Seed 1234 rejected: fortress distance 812 > 500
[SpeedrunPractice] Loaded scenario bastion_housing
```

Avoid logging every game tick.

Use debug mode for verbose seed-search diagnostics.

---

# 37. Performance Requirements

Practice reset should feel immediate where technically possible.

Avoid:

```text
unbounded chunk generation
blocking UI indefinitely
scanning millions of seeds on main game thread
saving unnecessary worlds
keeping old worlds loaded
```

Heavy seed searches should use safe worker execution.

All Minecraft state mutations must return to the correct game/server thread.

---

# 38. Seed Search Cancellation

Search must support cancellation.

Architecture:

```java
interface SeedSearchTask {

    SearchProgress progress();

    boolean isFinished();

    void cancel();

    List<SeedResult> results();
}
```

UI should expose:

```text
Cancel Search
```

Never lock the game indefinitely while searching.

---

# 39. Thread Safety

Do not manipulate Minecraft world/player objects from arbitrary worker threads.

Background threads may perform:

```text
pure seed math
parsing
statistics
filter calculations
disk serialization
```

Minecraft interaction must use the appropriate client/server executor.

---

# 40. Error Handling

Expected failures should use domain exceptions.

Example:

```java
PracticeException
ScenarioLoadException
SeedSearchException
AdapterException
CheckpointException
```

Show useful messages to the user.

Bad:

```text
NullPointerException
```

Good:

```text
Unable to start Bastion Practice:
No bastion satisfying the selected filter was found.
```

---

# 41. Documentation

Maintain:

```text
README.md
AGENTS.md
ARCHITECTURE.md
CONTRIBUTING.md

docs/
├── practices.md
├── seed-search.md
├── custom-scenarios.md
├── loadouts.md
├── commands.md
├── compatibility.md
└── development.md
```

README should focus on users.

ARCHITECTURE should focus on developers.

AGENTS should focus on coding agents.

---

# 42. Definition of Done for a Feature

A feature is complete only when:

```text
[ ] implementation exists
[ ] configuration exists if necessary
[ ] command/UI access exists
[ ] errors are handled
[ ] tests added where practical
[ ] build succeeds
[ ] no Minecraft imports leaked into common
[ ] documentation updated
[ ] version compatibility considered
```

---

# 43. Definition of Done for a Version

A Minecraft version is considered supported only when:

```text
[ ] project launches
[ ] menu works
[ ] commands register
[ ] world creation works
[ ] same-seed reset works
[ ] new-seed reset works
[ ] inventory/loadouts work
[ ] seed list works
[ ] seed search works
[ ] Overworld practice works
[ ] Nether practice works
[ ] Bastion practice works
[ ] Fortress practice works
[ ] Blind practice works
[ ] Postblind works
[ ] Stronghold works
[ ] End works
[ ] One Cycle works
[ ] checkpoints work
[ ] statistics work
```

---

# 44. v1.0 Required Feature Matrix

| Feature              |   1.16.1 |   1.21.1 |     26.3 |
| -------------------- | -------: | -------: | -------: |
| Overworld Practice   | Required | Required | Required |
| Buried Treasure      | Required | Required | Required |
| Nether Practice      | Required | Required | Required |
| Bastion Practice     | Required | Required | Required |
| Fortress Practice    | Required | Required | Required |
| Blind Travel         | Required | Required | Required |
| Post Blind           | Required | Required | Required |
| Stronghold           | Required | Required | Required |
| End Practice         | Required | Required | Required |
| One Cycle            | Required | Required | Required |
| Seed Lists           | Required | Required | Required |
| Seed Search          | Required | Required | Required |
| Loadouts             | Required | Required | Required |
| Same Seed Reset      | Required | Required | Required |
| New Seed Reset       | Required | Required | Required |
| Checkpoints          | Required | Required | Required |
| Timer                | Required | Required | Required |
| Statistics           | Required | Required | Required |
| Custom Scenario JSON | Required | Required | Required |

---

# 45. Explicit Non-Goals

Never expand scope to include:

```text
MCSR Ranked clone
public servers
ranked matchmaking
player-vs-player races
rating algorithms
leaderboard service
online accounts
OAuth
remote seed distribution
remote world hosting
anti-cheat
match arbitration
season system
spectator networking
```

A local leaderboard/statistics page is acceptable.

A remote competitive platform is not.

---

# 46. Recommended Implementation Order

Follow approximately this order:

```text
01 baseline 1.16.1 build

02 multi-module Gradle structure

03 common domain API

04 adapter interfaces

05 migrate seed manager

06 migrate configuration

07 migrate loadouts

08 migrate player operations

09 migrate world creation

10 migrate Overworld practice

11 migrate Nether practice

12 migrate Stronghold practice

13 migrate Postblind

14 migrate End practice

15 migrate Buried Treasure

16 checkpoint framework

17 scenario engine

18 timer

19 statistics

20 data-driven scenarios

21 Bastion practice

22 Fortress practice

23 Blind Travel practice

24 One Cycle practice

25 seed source abstraction

26 seed search engine

27 seed search filters

28 search cache

29 seed collections

30 GUI redesign

31 keybinds

32 custom scenario editor/loading

33 known-seed test suite

34 port 1.21.1

35 stabilize 1.21.1

36 port 26.3

37 stabilize 26.3

38 CI matrix

39 documentation

40 v1.0 release
```

Do not skip directly to modern versions before completing the shared architecture.

---

# 47. Agent Task Template

When starting a coding task, write an internal task specification in this form:

```markdown
## Task

Implement:

<feature>

### Target module

<module>

### Existing behavior

<what exists today>

### Desired behavior

<what should happen>

### API changes

<interfaces/classes changed>

### Version impact

- 1.16.1:
- 1.21.1:
- 26.3:

### Tests

<tests required>

### Acceptance criteria

- [ ]
- [ ]
- [ ]
```

Then implement it.

---

# 48. Agent Completion Template

After implementation report:

```markdown
## Completed

### Changed

- file
- file
- file

### Behavior

Describe what now works.

### Tests

- command
- result

### Builds

- 1.16.1:
- 1.21.1:
- 26.3:

### Limitations

List remaining limitations.

### Next Task

State the single highest-priority next implementation step.
```

---

# 49. Architecture Guardrail

Before creating a new class, ask:

```text
Is this Minecraft-independent?

YES
    -> common/practices

NO
    -> appropriate version adapter
```

Before adding an `if` for Minecraft version, ask:

```text
Can this behavior be represented by an adapter method?
```

Usually the answer should be yes.

---

# 50. Final Product Goal

The expected user flow is:

```text
Launch Minecraft
↓
Open Speedrun Practice
↓
Choose scenario
↓
Choose seed source
↓
Choose loadout
↓
Start
↓
Practice
↓
See timer/result
↓
Retry same seed
or
Generate another matching seed
```

Example:

```text
Practice
    Bastion

Type
    Housing

Seed Source
    Search

Fortress Requirement
    200-500 blocks

Loadout
    Ranked-like Bastion

Timer Start
    First Movement

[ START ]
```

After completion:

```text
Housing Bastion

Time
    01:42.317

PB
    01:39.114

Attempts
    37

[ Retry Same Seed ]
[ New Matching Seed ]
[ Previous Seed ]
[ Save Seed ]
```

Everything must operate locally.

No multiplayer infrastructure is required.

---

# 51. First LLM Task

The first coding agent working on this repository should NOT immediately implement Bastion practice or seed searching.

Its first instruction is:

```text
Inspect the existing repository completely.

Document:
- build system
- dependencies
- Java version
- Fabric/Loom versions
- package structure
- entrypoints
- commands
- configurations
- mixins
- world-generation manipulation
- seed handling
- inventory handling
- SpeedRunIGT integration
- existing practice types

Create:

docs/original-architecture.md
docs/original-feature-matrix.md

Then verify that the untouched 1.16.1 project builds.

Only after the baseline build succeeds,
begin the multi-module architecture migration.
```

---

# 52. Autonomous Workflow Instruction

If working autonomously across multiple tasks:

```text
Continue through IMPLEMENTATION ORDER sequentially.

For each step:

1. inspect
2. implement
3. test
4. build
5. document
6. commit

Do not ask for confirmation between normal implementation steps.

Stop only when:

- blocked by unavailable external dependency
- a required design decision cannot be inferred
- credentials/secrets are required
- a destructive migration would affect user data
- repository state makes safe continuation impossible

Otherwise continue with the next highest-priority task.
```

---

# 53. Quality Priority

Priorities are:

```text
1. correctness
2. reproducibility
3. architecture
4. practice-reset speed
5. user experience
6. feature count
```

Do not sacrifice architecture just to make one version work faster.

The project should remain maintainable when a fourth Minecraft version is eventually added, even though v1.0 officially supports only three.

---

# 54. Core Principle

The project is:

```text
one practice engine
+
one scenario system
+
one seed system
+
one statistics system
+
three Minecraft adapters
```

It is NOT:

```text
three separate mods
```

and it is NOT:

```text
a multiplayer Ranked clone
```
