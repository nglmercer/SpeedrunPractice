# SpeedrunPractice — Complete Implementation Plan

## Purpose

This document defines the required work to turn the current repository from:

```text
working legacy 1.16.1 mod
+
shared architecture/model code
+
stub version adapters
```

into:

```text
one functional shared practice engine
+
fully implemented Minecraft adapters
+
three real Fabric mod builds
```

Target versions:

```text
Minecraft 1.16.1
Minecraft 1.21.1
Minecraft 26.3
```

The project is strictly:

```text
single-player speedrun practice
```

Do NOT implement:

```text
MCSR Ranked
matchmaking
Elo
multiplayer racing
accounts
remote servers
anti-cheat
online leaderboards
race synchronization
```

---

# 1. Current Repository Reality

Do not assume the current `versions/` modules are working Minecraft mods.

Current state:

```text
root src/
    real working legacy 1.16.1 Fabric mod

common/
    mostly implemented shared domain layer

practices/
    shared scenario setup logic

seed-search/
    search orchestration

versions/fabric-1.16.1/
    plain Java adapter skeleton

versions/fabric-1.21.1/
    plain Java adapter skeleton

versions/fabric-26.3/
    plain Java adapter skeleton
```

The new version adapters currently contain methods like:

```java
throw pending("createPracticeWorld");
throw pending("teleport");
throw pending("locate");
throw pending("applyLoadout");
```

These must be replaced by actual Minecraft implementations.

---

# 2. Critical Development Rule

Do NOT begin by implementing 1.21.1 or 26.3.

First make the new architecture completely functional on:

```text
Minecraft 1.16.1
```

using the already-working legacy implementation as the behavioral reference.

Required order:

```text
legacy 1.16.1
↓
functional AdapterSet116
↓
new shared runtime works completely on 1.16.1
↓
port same adapter contracts to 1.21.1
↓
port same adapter contracts to 26.3
```

---

# 3. Required Architecture

Final runtime architecture:

```text
Fabric entrypoint
        ↓
PracticeRuntime
        ↓
ScenarioEngine
        ↓
PracticeScenario
        ↓
MinecraftAdapter
        ↓
version-specific implementation
```

Example:

```text
1.16.1
Fabric116Entrypoint
    ↓
PracticeRuntime
    ↓
ScenarioEngine
    ↓
AdapterSet116

1.21.1
Fabric121Entrypoint
    ↓
PracticeRuntime
    ↓
ScenarioEngine
    ↓
AdapterSet121

26.3
Fabric263Entrypoint
    ↓
PracticeRuntime
    ↓
ScenarioEngine
    ↓
AdapterSet263
```

---

# 4. Create `PracticeRuntime`

Create a central shared runtime owner.

Suggested class:

```java
public final class PracticeRuntime {
    private final MinecraftAdapter adapter;
    private final ScenarioRegistry scenarios;
    private final ScenarioEngine engine;
    private final SeedStore seedStore;
    private final LoadoutManager loadouts;
    private final CheckpointManager checkpoints;
    private final PracticeTimer timer;
    private final PracticeStatistics statistics;

    private SeedSource activeSeedSource;
    private SeedSearchTask activeSearch;
}
```

Responsibilities:

```text
initialize shared systems
load configuration
load scenarios
load loadouts
own active scenario
own active seed source
start/stop/reset scenarios
manage seed searches
manage checkpoints
manage statistics
expose command actions
expose GUI actions
```

Do not put Minecraft classes inside this shared class.

---

# 5. Runtime Bootstrap

Each Minecraft version must create exactly one runtime.

Example abstraction:

```java
public final class SpeedrunPracticeBootstrap {

    public static PracticeRuntime create(
        MinecraftAdapter adapter,
        Path configDir
    ) {
        ...
    }
}
```

Version entrypoint does:

```java
MinecraftAdapter adapter = new AdapterSet116(...);

PracticeRuntime runtime =
    SpeedrunPracticeBootstrap.create(
        adapter,
        configDirectory
    );
```

Store runtime somewhere version code can safely reach.

Do NOT use uncontrolled global static state for everything.

---

# 6. Phase A — Repair CI First

Before further feature work, make CI correctly represent what is actually supported.

Current CI incorrectly treats plain Java adapter jars as Minecraft version jars.

Change CI to have these categories:

```text
shared-tests
legacy-116-runtime
new-116-adapter
121-runtime
263-runtime
```

Until a version is actually ported:

```text
DO NOT upload its java-library jar as a playable mod
```

Temporary acceptable CI:

```yaml
1. shared engine tests
2. legacy 1.16.1 Fabric build
3. adapter compilation checks
```

Later replace adapter compilation with actual Loom builds.

---

# 7. Fix Capability Reporting

Current adapters may return:

```java
supports(...) == true
```

while the method itself throws:

```java
pending(...)
```

This is invalid.

Rule:

```text
Capability may return true ONLY when:
- implementation exists
- it has been compiled
- it has been tested in the target version
```

Until then:

```java
return false;
```

Apply immediately to:

```text
AdapterSet116
AdapterSet121
AdapterSet263
```

---

# 8. Phase B — Complete AdapterSet116

This is the highest-priority implementation task.

Use the existing root 1.16.1 mod as the implementation source.

Do NOT rewrite working Minecraft behavior unnecessarily.

Replace every `pending()` implementation.

---

# 9. AdapterSet116 — WorldAdapter

Implement:

```java
createPracticeWorld(...)
deletePracticeWorld(...)
resetPracticeWorld(...)
spawnPosition(...)
```

Use existing legacy classes such as:

```text
MinecraftServerMixin
IMinecraftServer
PracticeWorld
ServerWorldAccess
existing world creation methods
existing practice dimension logic
```

Required behavior:

```text
create isolated practice world
support OVERWORLD
support NETHER
support END
use requested seed
honor structures enabled/disabled
return actual spawn position
delete world cleanly
avoid leaking worlds/chunks
```

Reset behavior:

```text
SAME_SEED
    destroy/reset practice world
    recreate with same seed

NEW_SEED
    recreate with new seed

PREVIOUS_SEED
    recreate with previous seed
```

Acceptance:

```text
start Overworld practice
reset same seed
reset new seed
start Nether
start End
no stale world remains
```

---

# 10. AdapterSet116 — PlayerAdapter

Implement:

```java
teleport
setHealth
setFood
clearEffects
applyLoadout
resetPlayer
getPosition
getHealth
getFood
getWorld
```

Use actual:

```text
ServerPlayerEntity
PlayerInventory
StatusEffectInstance
ServerWorld
```

`PracticePlayer` should wrap a version-neutral handle.

Do NOT store direct Minecraft classes in `common`.

Possible version object:

```java
final class PlayerHandle116 implements PracticePlayer {
    private final UUID playerId;
}
```

Resolve the actual player through server state.

---

# 11. AdapterSet116 — InventoryAdapter

Implement:

```java
applyLoadout
captureLoadout
clear
```

Support:

```text
hotbar
main inventory
armor
offhand
item count
NBT where necessary
selected slot
```

Registry validation must be real.

Replace:

```java
return id != null;
```

with actual 1.16.1 item registry lookup.

Replace:

```java
return 64;
```

with the actual item's max stack size.

---

# 12. AdapterSet116 — StructureAdapter

Implement:

```java
locateNearest
locate
```

Required structures:

```text
village
shipwreck
buried_treasure
ruined_portal
bastion_remnant
fortress
stronghold
```

`StructureLocation.metadata()` must support relevant data.

Examples:

```text
bastion.type
portal_room
stronghold.ring
```

Never fabricate metadata.

If metadata is unavailable:

```text
omit it
```

and capability must reflect that.

---

# 13. Bastion Type Detection

Implement bastion type detection for 1.16.1.

Required categories:

```text
housing
stables
treasure
bridge
```

Store:

```java
metadata.put("bastion.type", type);
```

Verify against real generated bastions.

Do not classify from structure coordinates alone unless the algorithm is verified.

---

# 14. AdapterSet116 — PortalAdapter

Implement:

```java
createNetherPortal
linkPortals
```

Use existing portal logic where possible.

Required:

```text
create valid obsidian frame
ignite portal
support overworld/nether linking
restore portal state during relevant practice
```

Do not place fake portal positions without checking world state.

---

# 15. AdapterSet116 — DragonAdapter

Implement:

```java
resetFight
forcePerch
hasLivingDragon
```

Reuse existing dragon mixin/accessor behavior where possible.

Required End practice:

```text
dragon spawns/reset correctly
crystals/fight state correct
force perch works when enabled
completion detects dragon death
```

Only report:

```text
DRAGON_FORCE_PERCH = true
```

after real testing.

---

# 16. AdapterSet116 — CommandAdapter

The current implementation only logs the command tree.

Replace with real Brigadier registration.

Map:

```text
PracticeCommands.Node
```

to actual Fabric/Minecraft commands.

Commands must execute runtime actions.

Required:

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

/practice loadout list
/practice loadout save <name>
/practice loadout apply <name>
/practice loadout delete <name>

/practice checkpoint save
/practice checkpoint load
/practice checkpoint clear

/practice stats
/practice config reload
```

Keep legacy commands as aliases.

---

# 17. Command Execution Layer

Do not place command behavior inside Brigadier callbacks.

Create shared action dispatcher:

```java
public final class PracticeActionExecutor {

    public CommandResult execute(
        String action,
        CommandArguments args,
        PracticePlayer player
    );
}
```

Example:

```text
"restart.same"
    ↓
runtime.restart(SAME_SEED)

"checkpoint.save"
    ↓
runtime.saveCheckpoint()

"seed.favorite"
    ↓
runtime.favoriteCurrentSeed()
```

---

# 18. AdapterSet116 — GuiAdapter

Implement actual GUI screens.

Required screens:

```text
PracticeMainScreen
PracticeScenarioScreen
PracticeResultsScreen
SeedSearchScreen
LoadoutScreen
StatisticsScreen
```

Minimum v1:

```text
main practice selector
scenario options
Start button
results
same seed
new seed
previous seed
```

Do not block release waiting for fancy styling.

Functional first.

---

# 19. Keybind Integration

`PracticeKeybinds` is currently only a model.

Register actual Fabric client keybindings.

Required actions:

```text
restart same seed
restart new seed
previous seed
save checkpoint
load checkpoint
stop practice
open practice menu
```

Process key presses from client tick events.

Ensure each key triggers once per press.

---

# 20. Phase C — Fix ScenarioEngine Timing

Current engine always starts the timer immediately.

This must be changed.

Support actual start conditions:

```text
SCENARIO_LOAD
PLAYER_MOVE
DIMENSION_ENTRY
PORTAL_EXIT
MANUAL
```

Support stop conditions:

```text
SCENARIO_COMPLETE
DIMENSION_ENTRY
STRUCTURE_REACHED
DRAGON_DEATH
MANUAL
```

Do not call:

```java
startTimer();
```

unconditionally after `scenario.start()`.

Instead:

```java
timer.reset();

if (condition == SCENARIO_LOAD) {
    startTimer();
}
```

Other conditions start from events.

---

# 21. Add Practice Event System

Create shared events.

Example:

```java
public interface PracticeEvent {
}
```

Events:

```text
PlayerMovedEvent
DimensionChangedEvent
PortalExitEvent
StructureEnteredEvent
DragonKilledEvent
InventoryChangedEvent
BlockPlacedEvent
EntityKilledEvent
ManualTimerStartEvent
ManualTimerStopEvent
```

Add:

```java
PracticeScenario.onEvent(
    PracticeContext context,
    PracticeEvent event
)
```

or equivalent event handler system.

Version adapters translate actual Minecraft events into these shared events.

---

# 22. Scenario Completion Conditions

Nine scenarios currently never finish.

Implement completion logic for all.

---

# 23. Overworld Completion

Configurable completion conditions may include:

```text
enter_nether
reach_structure
obtain_item
manual
```

Default recommended:

```text
enter_nether
```

When player enters Nether:

```java
TickResult.finished();
```

or event equivalent.

---

# 24. Buried Treasure Completion

Default completion:

```text
loot buried treasure chest
```

Possible simpler implementation:

```text
reach chest location
+
open target chest
```

Prefer detecting interaction/opening of the actual target chest.

Do not finish merely because player reaches the chunk.

---

# 25. Nether Practice Completion

Possible modes:

```text
reach_bastion
reach_fortress
obtain_pearls
obtain_blaze_rods
exit_nether
manual
```

Scenario setting:

```text
nether.goal
```

---

# 26. Bastion Completion

Support:

```text
manual
leave_bastion
obtain_target_pearls
reach_exit
```

Recommended default:

```text
obtain_target_pearls
```

Track configured pearl threshold.

Example:

```text
bastion.targetPearls=16
```

---

# 27. Fortress Completion

Modes:

```text
find
enter
blaze
navigation
exit
```

Each needs different completion behavior.

Examples:

```text
find
    player enters fortress bounding area

blaze
    player obtains configured blaze rods

exit
    player leaves fortress after entering
```

Do not treat all modes identically.

---

# 28. Blind Travel Completion

Blind Travel needs actual route semantics.

Required:

```text
start in Nether
give portal materials/loadout
player builds/uses portal
detect Overworld exit
calculate distance to nearest stronghold
finish on portal exit
```

Record result data:

```text
exit coordinates
stronghold coordinates
distance/error
```

Do not simply teleport the player somewhere and call it blind travel.

---

# 29. Post-Blind Completion

Possible default:

```text
reach stronghold
```

Optional:

```text
enter stronghold
reach portal room
manual
```

Track target stronghold used for this attempt.

---

# 30. Stronghold Completion

Modes:

```text
entry
navigation
portal_search
portal_room
```

Suggested completions:

```text
navigation
    reach portal room

portal_search
    reach portal room

portal_room
    enter End portal

entry
    configurable/manual
```

Need real portal-room detection.

---

# 31. End Completion

Already based on dragon death.

Keep:

```text
dragon death
```

but ensure:

```text
dragon fight state reset
target dragon belongs to practice world
completion fires only once
```

---

# 32. One Cycle Completion

Dragon death is valid completion.

Need additionally:

```text
real forced perch when enabled
valid one-cycle loadout
bed counts
obsidian setup
dragon fight reset
```

---

# 33. Custom Scenario Completion

Current custom scenario parser understands:

```text
timer.stop
```

but does not implement it.

Implement:

```text
manual
dimension_entry
structure_reached
dragon_death
scenario_complete
```

Add custom settings such as:

```json
{
  "completion": {
    "type": "structure_reached",
    "structure": "stronghold"
  }
}
```

or normalized equivalent.

---

# 34. Fix Practice Spawn Logic

Current spawn logic contains placeholders.

---

# 35. Bastion Spawn Modes

Implement real differences:

```text
outside
entrance
route
random_exterior
portal_exit
```

Do NOT make these all aliases.

`random_exterior`:

```text
pick random safe point around structure
ensure passable block
ensure solid ground
avoid lava
```

`entrance`:

```text
detect useful exterior approach point
```

`route`:

```text
allow route preset metadata
```

---

# 36. Fortress Spawn Modes

Implement real behavior:

```text
find
    spawn outside view/range

enter
    spawn immediately outside entry

blaze
    spawn at valid blaze practice location

navigation
    spawn at configured internal point

exit
    spawn inside fortress
```

---

# 37. Safe Teleporting

Never blindly teleport to:

```text
structure Y coordinate
```

without safety checks.

Implement:

```java
SafePosition findSafePosition(
    world,
    desiredPosition
)
```

Check:

```text
solid floor
two blocks of headroom
not lava
not fire
inside world bounds
```

Version-specific implementation.

---

# 38. Phase D — Finish Checkpoints

Current checkpoints are incomplete.

Checkpoint must capture:

```text
seed
dimension
position
rotation
health
food
saturation
XP
level
inventory
armor
offhand
selected hotbar slot
status effects
timer state
scenario state
```

Add adapter snapshot APIs if needed.

Example:

```java
PlayerSnapshot capturePlayerState(...);

void restorePlayerState(...);
```

---

# 39. Scenario Snapshot

Every scenario may optionally persist internal state.

Add:

```java
interface ScenarioSnapshot {
}
```

and:

```java
ScenarioSnapshot captureState(
    PracticeContext context
);

void restoreState(
    PracticeContext context,
    ScenarioSnapshot snapshot
);
```

Needed for:

```text
target structure
target chest
target stronghold
entered-fortress state
timer trigger state
completion progress
```

---

# 40. Checkpoint World Consistency

If checkpoint seed/dimension differs from current practice world:

```text
recreate correct world first
then restore player
```

Do not teleport a player into a world generated from the wrong seed.

---

# 41. Optional World Snapshot Layer

For scenarios needing local block restoration:

```text
portal frames
beds
obsidian
dragon fight
chests
```

implement a bounded world snapshot.

Do NOT serialize the entire Minecraft world.

Example:

```java
WorldRegionSnapshot captureRegion(
    center,
    radius
);
```

Only use where required.

---

# 42. Phase E — Complete Statistics

Current statistics are in-memory only.

Implement persistent statistics.

Path:

```text
config/speedrun-practice/stats.json
```

Persist:

```text
attempts
completed
PB
recent times
reset reason
seed
Minecraft version
preset
```

Write safely:

```text
temporary file
↓
atomic replace
```

Do not corrupt existing stats on crash.

---

# 43. Statistics Per Version

Do not mix incompatible world-generation results.

Key statistics by:

```text
practice
preset
Minecraft version
```

Example:

```text
bastion/housing/1.16.1
bastion/housing/1.21.1
```

---

# 44. Phase F — Fix Seed Search Architecture

Current search orchestration is useful.

Actual analyzers are not implemented.

---

# 45. Analyze Each Seed Once

Current filters may repeatedly call:

```java
analyzer.analyze(seed, query)
```

Change architecture.

Preferred:

```java
SeedAnalysis analysis =
    analyzer.analyze(seed, query);

for (Filter filter : filters) {
    filter.matches(seed, analysis, query);
}
```

Change interface:

```java
boolean matches(
    long seed,
    SeedAnalysis analysis,
    SeedQuery query
);
```

Do not recalculate structure data for every filter.

---

# 46. SeedAnalyzer116

Implement actual 1.16.1 analyzer first.

Required findings:

```text
biome.spawn

structure.village.distance
structure.shipwreck.distance
structure.buried_treasure.distance
structure.ruined_portal.distance
structure.bastion_remnant.distance
structure.fortress.distance
structure.stronghold.distance

location.<structure>

bastion.type
stronghold.ring
lava.available
```

Only expose findings that can be determined correctly.

---

# 47. Two-Stage Search

Stage A:

```text
cheap deterministic calculations
biome/structure start checks
distance math
```

Stage B:

```text
verify surviving candidates against actual Minecraft
```

Never perform Stage B on arbitrary worker threads if it manipulates Minecraft worlds.

Use:

```text
background math worker
↓
queue candidate
↓
server thread verification
↓
return result
```

---

# 48. Seed Search Progress

Expose:

```text
tested
matched
verified
failed
rate
elapsed
cancelled
finished
```

UI and command:

```text
/practice seeds results
```

must show current status.

---

# 49. Seed Search Cancellation

Cancellation must be checked:

```text
between candidates
before verification
after verification
```

Stopping a search must release its worker executor.

Do not leak daemon threads across repeated searches.

---

# 50. Verified Seed Fixtures

Delete the assumption that illustrative test data proves anything.

For each version, collect actual verified seeds.

Minimum:

```text
5 known seeds per version
```

Each should contain as many verified facts as possible.

Example:

```json
{
  "seed": 123456789,
  "verified": true,
  "structures": {
    "fortress": {...},
    "bastion_remnant": {...}
  }
}
```

Do not mark:

```text
verified:true
```

without checking against the actual target Minecraft version.

---

# 51. Remove Placeholder 26.3 Coordinates

Values like:

```text
0,64,0
```

used as illustrative expected structures must not be treated as test expectations.

Replace with:

```text
verified actual coordinates
```

or remove the row.

---

# 52. Phase G — Proper 1.16.1 New Runtime Integration

Once adapters and shared engine work:

```text
root legacy command path
```

must be replaced gradually by:

```text
PracticeRuntime
```

Do not delete legacy code immediately.

Use migration approach:

```text
legacy operation
↓
adapter delegates to it
↓
runtime calls adapter
↓
behavior verified
↓
legacy direct call removed
```

---

# 53. 1.16.1 Migration Acceptance

Before porting another version, all of these must work using the NEW engine:

```text
[ ] /practice start overworld
[ ] /practice start nether
[ ] /practice start bastion
[ ] /practice start fortress
[ ] /practice start blind_travel
[ ] /practice start postblind
[ ] /practice start stronghold
[ ] /practice start end
[ ] /practice start onecycle

[ ] same seed reset
[ ] new seed reset
[ ] previous seed

[ ] loadouts
[ ] checkpoints
[ ] timer
[ ] completion
[ ] stats
[ ] seed list
[ ] seed search
[ ] favorites
[ ] GUI
[ ] keybinds
```

Do not begin 1.21.1 until this list is substantially green.

---

# 54. Phase H — Convert `fabric-1.16.1` Into Real Fabric Module

After behavior migration, move the runtime build from root into:

```text
versions/fabric-1.16.1
```

It must contain:

```text
build.gradle
src/main/java/
src/main/resources/fabric.mod.json
src/main/resources/*.mixins.json
```

Root project should eventually become aggregator-only.

Target:

```text
root
    no direct Minecraft implementation

versions/fabric-1.16.1
    complete Minecraft implementation
```

---

# 55. Multi-Version Gradle Strategy

Do not force one Loom generation onto all Minecraft versions if incompatible.

Recommended:

```text
shared modules
    standard Java projects

version modules
    independent Fabric/Loom configurations
```

If Gradle plugin incompatibility prevents one root build:

```text
use included builds/composite builds
```

Example:

```text
platforms/
├── mc-1.16.1/
├── mc-1.21.1/
└── mc-26.3/
```

with shared modules consumed as project/artifact dependencies.

Correctness is more important than forcing a single Gradle plugin version.

---

# 56. Phase I — 1.21.1 Port

There is already useful work on:

```text
compat/1.21.1-11537535791694781714
```

Do NOT merge that branch wholesale.

Recover only useful migration knowledge/code:

```text
Java 21 setup
Fabric API changes
Text API changes
Registry changes
screen API changes
mixin target updates
1.21.1 fabric.mod.json requirements
```

Then implement through:

```text
AdapterSet121
```

---

# 57. AdapterSet121

Must implement same contracts as 116.

Required:

```text
WorldAdapter121
PlayerAdapter121
InventoryAdapter121
StructureAdapter121
PortalAdapter121
DragonAdapter121
RegistryAdapter121
CommandAdapter121
GuiAdapter121
TimerAdapter121
SeedAnalyzer121
```

No shared scenario should need rewriting.

If a scenario requires Minecraft 1.21 imports:

```text
architecture failure
```

Move that logic into adapter.

---

# 58. 1.21.1 Build Requirements

Use:

```text
Java 21
modern Fabric Loader
Fabric API
appropriate Loom for 1.21.1
correct Yarn mappings
```

Module must produce a real remapped Fabric jar.

Verify jar contains:

```text
fabric.mod.json
entrypoint
mixins
shared engine classes or bundled shared jars
```

---

# 59. 1.21.1 Entrypoint

Create a real initializer.

Example:

```java
public final class SpeedrunPractice121
        implements ModInitializer {

    @Override
    public void onInitialize() {
        AdapterSet121 adapter = ...;
        PracticeRuntime runtime = ...;

        registerCommands(runtime);
        registerServerEvents(runtime);
    }
}
```

Client initializer handles:

```text
GUI
keybinds
client events
```

---

# 60. Phase J — 26.3 Port

Treat 26.3 as a separate generation.

Do not copy 1.21.1 build configuration blindly.

Use its required modern Java/Fabric toolchain.

Create:

```text
AdapterSet263
Fabric263Entrypoint
Fabric263ClientEntrypoint
version-local mixins
```

---

# 61. 26.3 Names/Mappings

Do not assume old Yarn class names still apply.

Keep all version-specific names inside:

```text
versions/fabric-26.3
```

Never modify `common` just because 26.3 renamed:

```text
ServerPlayerEntity
RegistryKey
ServerWorld
Structure
```

The adapter exists precisely to isolate these changes.

---

# 62. Phase K — GUI Parity

Each target should expose equivalent user behavior.

Exact visual code may differ.

Common GUI models remain shared.

Version-specific screen rendering belongs in adapter/client module.

Minimum parity:

```text
main menu
practice type
scenario options
seed source
loadout
start
results
retry same
new seed
previous seed
```

---

# 63. Phase L — Actual Configuration

Unify shared configuration.

Recommended root:

```text
config/speedrun-practice/
```

Files:

```text
config.json
loadouts/
scenarios/
seeds/
stats.json
```

Support legacy migration:

```text
config/speedrun-practice.json
config/speedrun-practice-seeds.txt
```

Never silently delete old config.

---

# 64. Configuration Versioning

Use:

```json
{
  "schemaVersion": 2
}
```

Migration system:

```text
legacy
↓
schema 1
↓
schema 2
```

Always back up before destructive migration.

---

# 65. Phase M — Loadout Persistence

Loadouts need disk persistence.

Directory:

```text
config/speedrun-practice/loadouts/
```

Example:

```text
bastion_default.json
onecycle_default.json
postblind_default.json
```

Support:

```text
save
load
delete
rename
import
export
```

---

# 66. Item Compatibility

Loadout parser must handle version differences.

If item exists:

```text
apply
```

If unavailable:

```text
warn clearly
skip item
```

Do not crash entire practice.

---

# 67. Phase N — Custom Scenario Runtime

Current JSON parser is not enough.

Implement full runtime interpretation for:

```text
seed source
seed filters
spawn mode
loadout
timer start
timer stop
completion
dimension
settings
```

Custom definitions should not require writing Java.

---

# 68. Custom Scenario Validation

Validate:

```text
unknown structure
unknown item
unsupported spawn type
unsupported timer condition
missing custom coordinates
invalid ranges
unsupported capability
```

Display useful error.

Bad config must not crash Minecraft.

---

# 69. Phase O — Threading

Seed math may run off-thread.

Minecraft world operations may not.

Define:

```java
public interface GameThreadExecutor {
    void server(Runnable runnable);
    void client(Runnable runnable);
}
```

or version equivalent.

Rules:

```text
pure math
    worker thread

world generation
player mutation
registry access if unsafe
GUI mutation
    correct Minecraft thread
```

---

# 70. Avoid Main-Thread Search Freezes

Never run:

```text
1,000,000 seed loop
```

on server tick thread.

Search must be asynchronous.

Verification must be batched.

Example:

```text
worker:
    find 100 cheap candidates

server:
    verify 1-5 candidates per tick/batch
```

Keep game responsive.

---

# 71. Phase P — Error Handling

No user should see:

```text
NullPointerException
UnsupportedOperationException
pending the port
```

in normal supported-version gameplay.

Convert failures to:

```java
PracticeException
```

with:

```text
technical message
user-friendly message
```

Example:

```text
No fortress found within 10,000 blocks.
Try another seed or increase search radius.
```

---

# 72. Remove `pending()` Before Version Support

Version cannot be marked supported if any normal practice path contains:

```java
throw pending(...)
```

CI should search for it.

Example guard:

```text
grep -R "throw pending" versions/<supported-version>
```

Fail release if found.

---

# 73. Phase Q — Tests

Shared unit tests:

```text
ScenarioEngine
timer
stats
seed sources
query builders
scenario parser
loadouts
config migration
checkpoint serialization
```

---

# 74. Adapter Contract Tests

Create common adapter test suite.

Example:

```java
abstract class MinecraftAdapterContractTest {
    abstract MinecraftAdapter adapter();

    testRegistry();
    testLoadout();
    testWorldLifecycle();
    testTeleport();
    testStructureLookup();
}
```

Run through version-specific integration environment where possible.

---

# 75. Runtime Smoke Tests

Each supported version must verify:

```text
Minecraft starts
mod initializes
commands register
practice runtime initializes
world can be created
player can teleport
world can be deleted
```

A jar compiling is not enough.

---

# 76. Jar Validation

CI must inspect output jar.

Required:

```text
fabric.mod.json exists
entrypoint class exists
mixin file exists when required
shared runtime available
version metadata correct
Minecraft dependency correct
Java dependency correct
```

---

# 77. CI Matrix

Final desired CI:

```text
shared

1.16.1
    compile
    test
    remapJar
    jar validation
    smoke launch

1.21.1
    compile
    test
    remapJar
    jar validation
    smoke launch

26.3
    compile
    test
    jar/remap build
    jar validation
    smoke launch
```

---

# 78. Release Criteria

Do not release a target jar unless:

```text
[ ] loads in target Minecraft
[ ] Fabric recognizes mod
[ ] no missing mixin errors
[ ] commands work
[ ] GUI opens
[ ] Overworld works
[ ] Nether works
[ ] Bastion works
[ ] Fortress works
[ ] Blind Travel works
[ ] Post Blind works
[ ] Stronghold works
[ ] End works
[ ] One Cycle works
[ ] same seed reset works
[ ] new seed works
[ ] previous seed works
[ ] checkpoints work
[ ] loadouts work
[ ] seed search works
[ ] statistics work
```

---

# 79. Required Version Status File

Create:

```text
docs/version-status.md
```

Example:

```markdown
| Feature | 1.16.1 | 1.21.1 | 26.3 |
|---|---|---|---|
| Launch | ✅ | ❌ | ❌ |
| Commands | ✅ | ❌ | ❌ |
| GUI | ✅ | ❌ | ❌ |
| World creation | ✅ | ❌ | ❌ |
...
```

Only mark ✅ after actual runtime test.

Never mark based only on compilation.

---

# 80. Remove Misleading Documentation

Do not say:

```text
supported
implemented
working
```

when only an interface/model exists.

Use:

```text
planned
shared model implemented
adapter pending
runtime unverified
```

until actually tested.

---

# 81. Required Work Order

Coding agents must follow this order:

```text
01 fix CI truthfulness

02 fix capability reporting

03 create PracticeRuntime

04 connect shared runtime to 1.16.1

05 implement WorldAdapter116

06 implement PlayerAdapter116

07 implement InventoryAdapter116

08 implement StructureAdapter116

09 implement PortalAdapter116

10 implement DragonAdapter116

11 implement RegistryAdapter116

12 implement CommandAdapter116

13 implement GuiAdapter116

14 implement 1.16.1 keybinds

15 add practice event system

16 implement timer start/stop events

17 finish Overworld completion

18 finish Buried Treasure completion

19 finish Nether completion

20 finish Bastion setup + completion

21 finish Fortress modes + completion

22 finish Blind Travel

23 finish Post Blind

24 finish Stronghold

25 finish End

26 finish One Cycle

27 finish custom scenario runtime

28 complete checkpoints

29 persist statistics

30 implement SeedAnalyzer116

31 optimize seed analysis caching

32 add verified 1.16.1 fixtures

33 migrate root legacy runtime fully into version module

34 validate entire new engine on 1.16.1

35 convert 1.21.1 module to real Fabric/Loom

36 salvage useful compat branch changes

37 implement AdapterSet121

38 implement 1.21.1 GUI/commands/keybinds/events

39 implement SeedAnalyzer121

40 verify 1.21.1 fixtures

41 full 1.21.1 runtime testing

42 create modern 26.3 Fabric build

43 implement AdapterSet263

44 implement 26.3 GUI/commands/keybinds/events

45 implement SeedAnalyzer263

46 verify 26.3 fixtures

47 full 26.3 testing

48 final CI matrix

49 release packaging

50 documentation cleanup
```

Do not reorder this substantially without documenting why.

---

# 82. LLM Per-Task Workflow

Before each task:

```markdown
## Current task

### Goal
Describe exactly one implementation objective.

### Existing code
List the files already responsible.

### Missing behavior
Describe what currently fails/stubs.

### Target files
List expected files to change.

### Version impact
- 1.16.1:
- 1.21.1:
- 26.3:

### Acceptance criteria
- [ ]
- [ ]
- [ ]

### Tests
List commands/tests.
```

Then inspect before editing.

---

# 83. Mandatory Inspection Rule

Never assume an API from another Minecraft version.

Before implementation:

```text
inspect actual mappings
inspect existing version code
inspect current dependency version
inspect existing mixins
```

Do not invent Minecraft methods/classes.

---

# 84. No Fake Implementations in Production

The following belong only in test-support:

```text
FakeAdapter
FakeWorld
FakePlayer
synthetic structure locations
illustrative structure positions
```

Production code may never silently use them.

---

# 85. No Silent Feature Fallback

Bad:

```text
No buried treasure found.
Starting at spawn instead.
```

For required-object scenarios, prefer:

```text
practice start fails
new matching seed requested
```

If the user explicitly allows fallback, then fallback is acceptable.

---

# 86. Seed-Aware Scenario Creation

Practice scenarios that require a structure must validate before world setup completes.

Example:

```text
Bastion Practice
↓
seed selected
↓
verify bastion exists
↓
verify requested type
↓
create/start scenario
```

Do not start invalid practice and warn afterward.

---

# 87. Practice Preset Validation

Before start:

```text
check capabilities
check seed requirements
check loadout validity
check structure availability
check version compatibility
```

Return readable errors before mutating game state.

---

# 88. Cleanup Guarantee

Every practice must clean up after:

```text
completion
manual stop
reset
exception
world exit
client disconnect
server stop
```

No orphan worlds.

No leaked worker threads.

No stale static references.

---

# 89. Exception-Safe Scenario Start

Current pattern must be strengthened.

Use:

```java
try {
    prepare();
    start();
} catch (...) {
    cleanupPartialScenario();
    throw ...;
}
```

If `start()` fails after world creation:

```text
delete created world
reset runtime state
stop timer
```

---

# 90. Runtime State Machine

Enforce:

```text
IDLE
PREPARING
RUNNING
PAUSED
COMPLETED
STOPPING
STOPPED
FAILED
```

Avoid invalid transitions.

Example:

```text
COMPLETED -> reset
```

should be explicitly supported rather than accidentally rejected.

---

# 91. Same Seed Reset Optimization

After correctness:

```text
same seed reset
```

should avoid unnecessary expensive full regeneration when safe.

Possible:

```text
world snapshot restoration
chunk cleanup
new temporary world instance
```

But correctness first.

---

# 92. Version-Specific Feature Degradation

If 26.3 cannot initially support a niche feature:

```text
disable it explicitly
```

Do not fake behavior.

Example:

```text
Force Perch unavailable on 26.3
```

is better than:

```text
supports=true
→ exception
```

---

# 93. Final Repository Layout

Desired:

```text
SpeedrunPractice/
├── common/
├── practices/
├── seed-search/
├── test-support/
│
├── versions/
│   ├── fabric-1.16.1/
│   │   ├── build.gradle
│   │   └── src/main/
│   │
│   ├── fabric-1.21.1/
│   │   ├── build.gradle
│   │   └── src/main/
│   │
│   └── fabric-26.3/
│       ├── build.gradle
│       └── src/main/
│
├── definitions/
├── test-data/
├── docs/
└── .github/
```

Eventually remove the duplicated legacy root runtime after migration.

---

# 94. Definition of Fully Implemented

The project is complete only when a user can install the appropriate jar for any of the three versions and perform:

```text
Minecraft
↓
Speedrun Practice menu
↓
Bastion
↓
Housing
↓
Seed Search
↓
Start
↓
practice runs correctly
↓
timer starts at configured event
↓
completion detected
↓
result recorded
↓
Retry Same Seed
↓
New Matching Seed
```

with no:

```text
pending()
fake adapters
manual developer setup
external server
multiplayer service
```

---

# 95. First Task To Execute Now

The next coding agent should start with:

```text
TASK:
Make the new architecture genuinely functional on Minecraft 1.16.1.

DO NOT port 1.21.1 or 26.3 yet.

1. Create PracticeRuntime.
2. Connect it to the working root 1.16.1 Fabric initializer.
3. Implement AdapterSet116 incrementally by delegating to existing
   working legacy code.
4. Replace false capability claims.
5. Register one new shared command:
      /practice start end
6. Make EndScenario run through ScenarioEngine and AdapterSet116.
7. Make timer + completion work.
8. Test in Minecraft 1.16.1.
9. Only then migrate the next scenario.

The first milestone is successful when:
    /practice start end
runs entirely through the new architecture
and the legacy direct EndPractice path is no longer required for that command.
```

---

# 96. Autonomous Agent Instruction

When operating autonomously:

```text
Continue through the implementation order.

Do not ask for permission between ordinary implementation steps.

Never declare a feature complete based only on compilation.

For every completed feature:

1. inspect existing code
2. implement production behavior
3. add/update tests
4. compile
5. run relevant runtime/smoke validation
6. update docs/version-status.md
7. commit

If actual Minecraft runtime validation cannot be performed,
mark the feature "compiled/unverified", not "working".
```

---

# 97. Completion Report Format

After each implementation task return:

```markdown
## Completed

### Implemented
- ...

### Changed files
- ...

### Removed stubs
- ...

### Tests
- command: result

### Runtime validation
- Minecraft version:
- action tested:
- result:

### Still pending
- ...

### Next task
- ...
```

---

# 98. Absolute Guardrails

Never:

```text
pretend plain Java jars are Fabric mods
mark fake data verified
claim runtime support from unit tests
copy version-specific Minecraft code into common
return true from unsupported capabilities
leave throw pending() in supported runtime
perform large seed search on Minecraft main thread
silently fall back to unrelated practice setup
```

Always:

```text
implement one working reference version first
keep Minecraft APIs inside version modules
test actual gameplay behavior
use verified seed fixtures
maintain version-specific build tooling
keep the project single-player
```

---

# 99. Core Implementation Principle

The target is:

```text
one real practice engine
+
one real scenario system
+
one real seed-search system
+
one real statistics/checkpoint system
+
three real Minecraft adapters
```

Not:

```text
one working mod
+
two jars containing interface stubs
```
