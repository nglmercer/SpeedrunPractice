# SpeedrunPractice — Remaining Implementation Tasks

## Goal

Finish the project so these are real, playable Fabric mods:

```text
1.16.1
1.21.1
26.3
```

Current truth:

```text
Legacy 1.16.1 = playable
Shared engine = mostly implemented
New adapters = still stubbed
1.21.1 = not playable
26.3 = Fabric skeleton only
CI = failing
```

Do not claim support until tested in-game.

---

# 1. Fix CI First

Latest CI is failing.

Required:

* make `shared-tests` pass
* keep adapter skeleton jars out of releases
* build real Fabric jars only for actually supported versions
* fail CI if a supported adapter still contains `throw pending(...)`

Definition of done:

```text
GitHub Actions = green
```

---

# 2. Finish 1.16.1 New Runtime Before Other Ports

Do not port 1.21.1/26.3 first.

Wire:

```text
SpeedrunPractice initializer
→ PracticeRuntime
→ ScenarioEngine
→ AdapterSet116
→ real Minecraft 1.16.1
```

Use existing working legacy code instead of rewriting working behavior.

First milestone:

```text
/practice start end
```

must run entirely through the new architecture.

---

# 3. Implement All AdapterSet116 Stubs

Remove every production:

```java
throw pending(...)
```

Implement:

```text
WorldAdapter
- createPracticeWorld
- deletePracticeWorld
- resetPracticeWorld
- spawnPosition

PlayerAdapter
- teleport
- health
- food
- effects
- reset
- position
- world
- full checkpoint capture/restore

InventoryAdapter
- apply
- capture
- clear

StructureAdapter
- locateNearest
- locate
- structure metadata

PortalAdapter
- create portal
- link portals

DragonAdapter
- reset fight
- force perch
- detect living dragon

RegistryAdapter
- real item lookup
- real stack limits

CommandAdapter
- real Brigadier commands

GuiAdapter
- real screens

SeedAnalyzer
- actual Minecraft seed analysis
```

Only return `supports(capability) == true` after real runtime validation.

---

# 4. Register Real Commands

Replace command-model logging with actual Brigadier registration.

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
/practice seeds export

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

Keep legacy aliases.

---

# 5. Finish Seed Search

Current missing pieces:

```text
search preset execution
result export
scenario seed.filters wiring
real per-version analyzers
verified seed fixtures
```

Implement:

```text
ScenarioDefinition
→ PracticeSettings
→ SeedQuery
→ filters
→ SeedAnalyzer
→ search result
```

Parse:

```text
biome
structure distances
bastion type
stronghold
lava
custom constraints
```

Do not re-analyze a seed multiple times.

---

# 6. Implement SeedAnalyzer116

Required findings:

```text
spawn biome
village
shipwreck
buried treasure
ruined portal
bastion
bastion type
fortress
stronghold
stronghold ring
lava availability
structure positions/distances
```

Use actual 1.16.1 worldgen logic.

---

# 7. Finish Practice Behavior

All practices must have real setup + completion.

Required:

```text
Overworld
Buried Treasure
Nether
Bastion
Fortress
Blind Travel
Post Blind
Stronghold
End
One Cycle
Custom
```

Do not use fake placeholder behavior.

Examples:

```text
Bastion random_exterior
→ real safe randomized exterior position

Fortress blaze mode
→ real blaze-practice location

Blind Travel
→ Nether start → portal exit → stronghold error result

Stronghold
→ real portal-room targeting

End / One Cycle
→ real dragon reset and completion
```

---

# 8. Complete Event/Timer Wiring

Shared event model exists.

Connect real Minecraft events:

```text
player movement
dimension change
portal exit
structure reached
dragon killed
inventory changed
manual timer start/stop
```

Respect:

```text
timer.start
timer.stop
completion
```

Do not always start timer immediately.

---

# 9. Finish Checkpoints

Implement real full snapshots:

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
selected slot
effects
timer
scenario state
```

If checkpoint world differs:

```text
recreate correct world first
then restore player
```

---

# 10. Finish GUI + Keybinds

Implement real Fabric screens:

```text
main menu
scenario setup
results
seed search
loadouts
stats
```

Implement real keybindings:

```text
open menu
restart same
restart new
previous seed
save checkpoint
load checkpoint
stop practice
```

---

# 11. Finish 1.16.1 Migration

Before moving on, verify all of this in Minecraft:

```text
[ ] all practices start
[ ] all practices complete
[ ] same seed reset
[ ] new seed reset
[ ] previous seed
[ ] loadouts
[ ] checkpoints
[ ] timer
[ ] stats
[ ] seed list
[ ] seed search
[ ] favorites
[ ] GUI
[ ] keybinds
```

Then move root legacy code into:

```text
versions/fabric-1.16.1
```

Root becomes aggregator-only.

---

# 12. Port 1.21.1

Convert module from:

```text
java-library
```

to real Fabric/Loom.

Use:

```text
Java 21
correct Loom
Fabric Loader
Fabric API
1.21.1 mappings
fabric.mod.json
version-local mixins
```

Implement:

```text
AdapterSet121
real entrypoint
commands
events
GUI
keybinds
SeedAnalyzer121
```

Reuse useful API migration work from the old compatibility branch, but do not merge its incomplete runtime wholesale.

---

# 13. Finish 26.3

26.3 already has Fabric/Loom skeleton.

Now wire:

```text
SpeedrunPractice263
→ PracticeRuntime
→ real AdapterSet263
```

Implement:

```text
worlds
players
inventory
structures
portals
dragon
registry
commands
GUI
events
keybinds
SeedAnalyzer263
```

Do not leave the entrypoint as logging-only.

---

# 14. Verified Seed Tests

Current fixtures are unverified.

Add at least:

```text
5 verified seeds per Minecraft version
```

Never mark:

```json
"verified": true
```

without checking in the actual target version.

---

# 15. Runtime Tests

A version is not supported because it compiles.

For every version verify:

```text
Minecraft starts
mod loads
commands register
world creates
player teleports
structures locate
practice completes
reset works
checkpoint works
seed search works
GUI opens
```

Update:

```text
docs/version-status.md
```

only after real testing.

---

# 16. Remove Remaining Production Stubs

Before release:

```bash
grep -R "throw pending" versions/
grep -R "not implemented" common practices seed-search versions
grep -R "cannot run yet" common practices seed-search versions
```

Supported versions must have no normal-path stubs.

Known current unfinished commands include:

```text
/practice seeds search
/practice seeds export
```

Fix them.

---

# 17. Final CI

Final CI must build real mod outputs:

```text
shared-tests

1.16.1
- compile
- tests
- Fabric build
- jar validation
- smoke launch

1.21.1
- compile
- tests
- Fabric build
- jar validation
- smoke launch

26.3
- compile
- tests
- Fabric build
- jar validation
- smoke launch
```

Inspect jars for:

```text
fabric.mod.json
entrypoint
mixins
shared runtime
correct Minecraft version
```

---

# 18. Required Implementation Order

Follow exactly:

```text
01 fix CI
02 wire PracticeRuntime into 1.16.1
03 implement AdapterSet116
04 real commands
05 real events/timers
06 finish all scenario behavior
07 checkpoints
08 GUI/keybinds
09 SeedAnalyzer116
10 seed search + export
11 verified 1.16.1 tests
12 migrate legacy root runtime
13 real 1.21.1 Fabric module
14 AdapterSet121
15 SeedAnalyzer121
16 verify 1.21.1
17 finish AdapterSet263
18 SeedAnalyzer263
19 verify 26.3
20 final CI/release
```

---

# 19. Agent Rules

For every task:

```text
inspect existing code
implement smallest complete production change
add tests
compile
run relevant runtime validation
update docs/version-status.md
commit
continue
```

Never:

```text
claim working from unit tests alone
ship plain Java adapter jars as mods
leave fake production behavior
mark unverified seeds verified
put Minecraft imports in common/
return true for unsupported capabilities
```

---

# 20. Definition of Done

Project is complete when all three versions can do:

```text
launch Minecraft
→ open Practice menu
→ choose practice
→ choose seed/search filters
→ start
→ run correct scenario
→ timer triggers correctly
→ completion detected
→ stats saved
→ retry same/new/previous seed
```

with no production:

```text
pending()
fake adapters
placeholder world logic
unimplemented commands
```
