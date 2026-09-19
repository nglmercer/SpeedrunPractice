# SpeedrunPractice — Fix All Remaining Issues

## Goal

Deliver fully working Fabric mods for:

```text
Minecraft 1.16.1
Minecraft 1.21.1
Minecraft 26.3
```

Single-player practice only.

Do not add multiplayer, Ranked, matchmaking, accounts, or online services.

## Important

GitHub Actions failures are currently caused by account/billing infrastructure and are **not an implementation blocker**.

Do not spend time debugging hosted Actions.

Validate with:

```text
local Gradle builds
unit tests
architecture guards
jar inspection
real Minecraft testing
dedicated-server testing where applicable
```

## Current progress checkpoint (2026-09-19)

Implementation order is currently at step 09. Stage-B lava verification now
has production paths in all three version adapters and is being checked
against real generated worlds. The parity work is not complete yet: temporary
headless probes and diagnostics remain during false-positive/false-negative
investigation, so no lava capability or runtime-status row should be marked
verified until those probes pass and are removed.

---

# 1. Finish and Verify 1.16.1

The new 1.16.1 runtime already exists:

```text
Runtime116
AdapterSet116
LiveAdapter116
LiveWorlds
LivePlayers
LiveInventories
LiveStructures
LivePortals
LiveDragons
LiveCommands
LiveGui
LiveTimer
SeedAnalyzer116
EventPoller116
client screens/keybinds
```

Tasks:

```text
[ ] run real Minecraft 1.16.1 client with compatible JDK
[ ] test every /practice command
[ ] test every practice
[ ] test GUI
[ ] test keybinds
[ ] test timer triggers
[ ] test completion events
[ ] test checkpoints
[ ] test same/new/previous seed reset
[ ] test seed search
[ ] test loadouts
[ ] test statistics persistence
```

Fix every runtime bug discovered.

After verification, enable only capabilities proven to work:

```java
supports(Capability.X) == true
```

Do not enable capabilities merely because code compiles.

---

# 2. Remove 1.16.1 Legacy Duplication

Once new-runtime parity is verified:

```text
remove unnecessary legacy direct practice execution
keep only reusable legacy internals needed by LiveAdapter116
route user-facing practice operations through PracticeRuntime
```

Avoid two competing practice engines.

---

# 3. Finish 26.3 World Handling

Current `LiveWorlds263` only binds to the existing vanilla world.

This is incorrect for requested practice seeds.

Fix:

```text
Practice seed
→ actual generated practice world using that seed
```

Implement:

```text
createPracticeWorld(seed)
deletePracticeWorld(world)
resetPracticeWorld(world, seed)
```

Requirements:

```text
requested seed matches actual world seed
isolated practice state
Overworld/Nether/End linkage
safe cleanup
same-seed reset
new-seed reset
previous-seed reset
no stale worlds
```

Do not merely store a different seed in the handle.

---

# 4. Finish AdapterSet263

Implement all remaining production `pending()` paths.

Required:

```text
StructureAdapter
- locateNearest
- locate
- structure metadata

PortalAdapter
- createNetherPortal
- linkPortals

DragonAdapter
- resetFight
- forcePerch
- hasLivingDragon

GuiAdapter
- main menu
- scenario setup
- results

TimerAdapter
- real integration if external timer support exists
```

Replace interim registry behavior with real registry access:

```text
itemExists
maxStackSize
```

After completion:

```bash
grep -R "throw pending" versions/fabric-26.3/src/main
```

must return nothing on supported practice paths.

---

# 5. Finish 26.3 Client Features

Add real:

```text
GUI screens
keybindings
client initializer
results UI
seed search UI
loadout UI
stats UI
```

Keybinds:

```text
open practice menu
restart same seed
restart new seed
previous seed
save checkpoint
load checkpoint
stop practice
```

---

# 6. Verify 26.3 Practices

Test all:

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

Verify actual setup and completion behavior.

Do not mark a practice supported because commands register.

---

# 7. Finish 26.3 Seed Search

Current real analyzer covers useful worldgen data.

Complete missing filters:

```text
bastion type
lava availability
any remaining structure metadata
```

Implement Stage-B verification where chunk generation is required.

Remove the current rejection for:

```text
lava=true
```

once real verification exists.

Reuse one `SeedAnalysis` per seed.

Do not repeat expensive analysis per filter.

---

# 8. Fully Port 1.21.1

Current 1.21.1 is still a plain Java skeleton.

Convert it into a real Fabric module.

Required build:

```text
Java 21
Fabric Loom
Minecraft 1.21.1
correct mappings
Fabric Loader
Fabric API
fabric.mod.json
version-local mixins
```

Use the existing compatibility branch only as reference.

Do not merge incomplete/stub implementations wholesale.

---

# 9. Implement LiveAdapter121

Mirror the completed architecture used by 1.16.1/26.3.

Create:

```text
Runtime121
LiveAdapter121
LiveWorlds121
LivePlayers121
LiveInventories121
LiveStructures121
LivePortals121
LiveDragons121
LiveRegistries121
LiveCommands121
LiveGui121
LiveTimer121
EventPoller121
SeedAnalyzer121
client initializer
screens
keybinds
```

Then make `AdapterSet121` a delegation shell like 116/263.

Remove every:

```java
throw pending(...)
```

from normal supported paths.

---

# 10. Verify Scenario Semantics

Review every scenario against actual gameplay.

### Bastion

Implement distinct:

```text
outside
entrance
route
random_exterior
portal_exit
```

with safe positions and real bastion subtype handling.

### Fortress

Implement distinct:

```text
find
enter
blaze
navigation
exit
```

### Blind Travel

Must actually perform:

```text
Nether start
→ portal creation/use
→ Overworld exit
→ nearest stronghold comparison
→ distance/error result
```

### Post Blind

Finish based on configured target:

```text
stronghold
portal room
manual
```

### Stronghold

Use real portal-room detection.

### End / One Cycle

Verify:

```text
dragon reset
fight state
force perch
completion once
correct loadout
```

---

# 11. Safe Spawn Logic

All versions must avoid unsafe teleports.

Implement/version-test:

```text
solid floor
two-block headroom
no lava
no fire
inside world bounds
```

Never blindly teleport to structure Y values.

---

# 12. Complete Checkpoints

Verify real capture/restore of:

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

If seed/dimension differs:

```text
recreate correct world
→ restore player
```

Add bounded block-region restoration where required for:

```text
beds
portals
obsidian
dragon setup
```

---

# 13. Complete Seed Search UX

Already implemented:

```text
search preset execution
results
export
seed.filters parsing
```

Now ensure all versions support them correctly.

Test:

```text
/practice seeds search <preset>
/practice seeds results
/practice seeds cancel
/practice seeds export
```

No game-thread blocking.

No leaked worker threads.

---

# 14. Add Verified Seed Fixtures

Current known-seed data is not sufficient.

Add at least:

```text
5 verified seeds for 1.16.1
5 verified seeds for 1.21.1
5 verified seeds for 26.3
```

Verify in the actual target Minecraft version.

Include:

```text
structure positions
bastion type where possible
stronghold data
biome data
```

Only use:

```json
"verified": true
```

after real checking.

---

# 15. Capability Flags

After runtime tests, update:

```java
supports(Capability capability)
```

Capability may be `true` only when:

```text
implemented
compiled
runtime-tested
```

Examples:

```text
CUSTOM_DIMENSION_RUNTIME
FAST_WORLD_RESET
BASTION_TYPE_QUERY
DRAGON_FORCE_PERCH
PORTAL_STATE_CAPTURE
STRUCTURE_METADATA_SEARCH
```

---

# 16. Documentation Cleanup

Keep:

```text
docs/version-status.md
versions/README.md
README.md
```

aligned with reality.

Use:

```text
✅ verified
runtime unverified
adapter pending
```

Do not call partial versions fully supported.

Fix stale statements whenever implementation changes.

---

# 17. Local Validation

Ignore hosted GitHub Actions billing failures.

Run locally:

```bash
./gradlew build
```

plus version-specific builds.

Also run:

```bash
scripts/verify-architecture.sh
scripts/verify-supported-versions.sh
```

or PowerShell equivalents.

Validate built jars contain:

```text
fabric.mod.json
correct entrypoint
mixins
PracticeRuntime
version adapter
shared engine
```

---

# 18. Runtime Verification Matrix

For each Minecraft version verify:

```text
[ ] mod launches
[ ] /practice registers
[ ] Overworld
[ ] Buried Treasure
[ ] Nether
[ ] Bastion
[ ] Fortress
[ ] Blind Travel
[ ] Post Blind
[ ] Stronghold
[ ] End
[ ] One Cycle
[ ] custom scenario
[ ] same seed
[ ] new seed
[ ] previous seed
[ ] checkpoints
[ ] loadouts
[ ] timer
[ ] completion
[ ] stats
[ ] seed search
[ ] favorites
[ ] GUI
[ ] keybinds
```

---

# 19. Implementation Order

Follow:

```text
01 verify/fix 1.16.1 runtime
02 finish 1.16.1 parity
03 verified 1.16.1 seeds
04 fix 26.3 real seeded worlds
05 finish 26.3 structures
06 finish 26.3 portals
07 finish 26.3 dragon
08 finish 26.3 GUI/keybinds
09 finish 26.3 seed Stage-B analysis
10 verify all 26.3 practices
11 verified 26.3 seeds
12 convert 1.21.1 to Loom/Fabric
13 implement LiveAdapter121
14 implement SeedAnalyzer121
15 implement 1.21.1 GUI/keybinds/events
16 verify all 1.21.1 practices
17 verified 1.21.1 seeds
18 enable verified capabilities
19 documentation cleanup
20 final local builds + release jars
```

---

# 20. Agent Rules

Continue autonomously through the list.

For every task:

```text
inspect
implement
test
build
runtime-verify when possible
fix discovered issues
update version-status
continue
```

Do not stop merely because compilation passes.

Never:

```text
fake world seeds
ship pending adapters
use fake test adapters in production
mark unverified features working
put Minecraft imports in common/
block seed search on the Minecraft main thread
```

---

# Definition of Done

All three versions must support:

```text
launch
→ open practice UI
→ choose practice
→ choose seed/search
→ create real seeded practice world
→ run scenario
→ timer works
→ completion detected
→ stats saved
→ retry same/new/previous
```

with no normal production path containing:

```text
throw pending(...)
fake adapters
placeholder world behavior
unimplemented commands
```
