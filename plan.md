# SpeedrunPractice — Test-First Completion Plan

## Primary Goal

Finish and validate:

```text
Minecraft 1.16.1
Minecraft 1.21.1
Minecraft 26.3
```

But **DO NOT continue normal feature development first**.

Current biggest problem is validation cost:

```text
code change
→ launch Minecraft
→ create world
→ reproduce state
→ manually test
→ discover another issue
→ edit
→ repeat
```

Replace this workflow with:

```text
code change
→ automated unit tests
→ adapter contract tests
→ headless Minecraft verification
→ JSON verification report
→ only final client smoke test
```

GitHub Actions billing/status is irrelevant for this task.

Use local testing.

---

# PHASE 0 — Freeze Feature Development

Before changing more gameplay code:

```text
[x] stop adding new features
[x] do not add temporary probes to production runtime
[x] freeze common adapter contracts
[x] freeze scenario interfaces
[x] create automated verification infrastructure
```

No large refactors until the verification system works.

---

# PHASE 1 — Build One Verification Framework

Create:

```text
verification/
├── common/
├── fixtures/
├── reports/
└── scripts/

versions/fabric-1.16.1/
└── verification/

versions/fabric-1.21.1/
└── verification/

versions/fabric-26.3/
└── verification/
```

Verification code must NOT ship inside release jars.

Do not put temporary probes in:

```text
src/main/
Runtime116
Runtime121
Runtime263
```

---

# PHASE 2 — Remove Current Production Probes

Immediately remove production startup calls like:

```java
TempLavaProbe116.arm();
TempLavaProbe121.arm();
TempLavaProbe263.arm();
```

Move all:

```text
TempLavaProbe*
TempSurfProbe*
TempSubstrateProbe*
TempBasinProbe*
PROBELAVA*
temporary diagnostic logging
```

into verification-only code.

Production launch must never automatically execute test scans.

---

# PHASE 3 — Testing Pyramid

Use 5 validation levels.

## L0 — Pure Unit Tests

No Minecraft.

Test:

```text
ScenarioEngine
PracticeRuntime state transitions
timer conditions
completion conditions
statistics
seed stores
search presets
filters
loadouts
checkpoint serialization
config migration
custom scenarios
command action dispatch
seed-search lifecycle
```

These must be fast.

Target:

```text
seconds, not minutes
```

---

## L1 — Adapter Contract Tests

Each version must run the same contract suite.

Example:

```java
interface AdapterContract {
    testRegistry();
    testInventoryRoundTrip();
    testWorldLifecycle();
    testTeleport();
    testStructureLookup();
    testPortalCreation();
    testCheckpointRoundTrip();
}
```

Implement once and run against:

```text
Adapter116
Adapter121
Adapter263
```

Do not duplicate expectations between versions unless behavior genuinely differs.

---

# PHASE 4 — Headless Real-Minecraft Test Harness

This is the most important part.

Create a verification entrypoint for each version that can run inside:

```text
dedicated Minecraft server
+
real Fabric mod
+
real mappings/APIs
```

It should automatically:

```text
boot server
create practice world
execute test
record result
cleanup world
continue next test
shutdown server
```

No human input.

---

# PHASE 5 — Verification Command

Add a verification-only command:

```text
/practiceverify run
```

NOT included in release jars.

It should execute suites like:

```text
worlds
structures
portals
dragon
registries
seed-search
scenarios
resets
checkpoints
all
```

Example:

```text
/practiceverify run worlds
/practiceverify run seeds
/practiceverify run all
```

---

# PHASE 6 — Machine-Readable Report

Never rely only on console logs.

Every run writes:

```text
build/verification/<version>/report.json
```

Example:

```json
{
  "version": "1.21.1",
  "passed": 42,
  "failed": 0,
  "tests": [
    {
      "name": "world.create.seed",
      "status": "PASS"
    }
  ]
}
```

Also create:

```text
summary.txt
```

with readable failures.

A failed verification must return non-zero exit status.

---

# PHASE 7 — Deterministic Test Seeds

Create one canonical fixture format:

```text
verification/fixtures/
├── 1.16.1.json
├── 1.21.1.json
└── 26.3.json
```

Minimum:

```text
5 verified seeds/version
```

Each should record useful facts:

```text
seed
spawn
spawn biome
village
buried treasure
ruined portal
bastion
bastion type
fortress
stronghold
lava
```

Do not keep separate contradictory fixture formats.

Consolidate current 26.3 verified data.

Add missing 1.21.1 fixtures.

---

# PHASE 8 — World Lifecycle Tests

Automatically test:

```text
create seed A
assert actual world seed == A

delete world
assert removed

create seed A
reset same seed
assert seed == A

reset seed B
assert seed == B

delete
assert no leaked practice worlds
```

Run repeatedly:

```text
50-100 cycles
```

to detect lifecycle leaks.

This test is critical.

---

# PHASE 9 — Seed Analyzer Differential Tests

For every fixture:

```text
SeedAnalyzer prediction
vs
real generated Minecraft result
```

Compare:

```text
spawn
biome
structure coordinates
bastion type
stronghold
lava
```

Use actual Minecraft structure lookup/generation as truth.

Fail automatically on mismatch.

Do not rely on eyeballing logs.

---

# PHASE 10 — Stage-B Lava Tests

Stage-B is expensive and complex.

Separate it from normal unit tests.

Create:

```text
verifyLava116
verifyLava121
verifyLava263
```

Test known:

```text
positive seeds
negative seeds
edge cases
```

For each:

```text
Stage-B prediction
→ generate real chunks
→ scan real blocks
→ compare
```

Only after this passes:

```text
remove temporary diagnostics
```

Production Stage-B must contain no:

```text
TEMP
PROBE
debug-only behavior
```

---

# PHASE 11 — Headless Player Harness

Many remaining features need a player.

Do NOT require a human for every test.

Create a verification-only controlled server player/test player.

It should allow tests to call the real:

```text
PlayerAdapter
InventoryAdapter
ScenarioEngine
PracticeRuntime
```

Test:

```text
teleport
health
food
effects
inventory
loadouts
checkpoint capture
checkpoint restore
scenario start
scenario reset
statistics
completion
```

The player harness is only verification infrastructure.

Do not use FakeMinecraftAdapter for these integration tests.

---

# PHASE 12 — Scenario Integration Tests

For each scenario:

```text
start
verify setup
simulate/reach required state
emit real/shared event
verify completion
verify result
verify cleanup
```

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

Test every scenario on all three versions where supported.

---

# PHASE 13 — Scenario Tests Should Avoid Full Gameplay

Do NOT automate an entire speedrun.

Test the important boundaries.

Example End:

```text
create End practice
→ verify dragon exists/reset
→ trigger dragon death state
→ poll engine
→ assert COMPLETED
```

Example Blind Travel:

```text
create practice
→ simulate/perform portal exit
→ calculate nearest stronghold
→ assert recorded error/distance
```

Example Bastion:

```text
seed known bastion
→ start scenario
→ assert player spawn/setup
→ assert expected bastion metadata
```

This makes validation fast.

---

# PHASE 14 — Client Tests Are Last Layer Only

Do not use client/manual testing for everything.

Only client-specific features require it:

```text
GUI rendering
button clicks
keybind registration
key presses
screen transitions
```

Create a tiny final smoke checklist.

For each version:

```text
[ ] Minecraft client starts
[ ] menu opens
[ ] scenario setup screen opens
[ ] Start works
[ ] results screen opens
[ ] restart button works
[ ] keybinds work
```

Everything else should already have automated verification.

---

# PHASE 15 — Create Gradle Verification Tasks

Target workflow:

```bash
./gradlew test
./gradlew verify116
./gradlew verify121
./gradlew verify263
./gradlew verifyAll
```

`verifyAll` should:

```text
run shared tests
run adapter contracts
build all 3 mods
launch version verification servers
run suites
collect reports
fail if any test fails
```

Do not depend on GitHub Actions.

---

# PHASE 16 — Fast Development Loop

Developers/LLMs should normally run only relevant tests.

Example:

World change:

```bash
./gradlew testWorldContracts
./gradlew verify121Worlds
```

Seed change:

```bash
./gradlew testSeedSearch
./gradlew verify121Seeds
```

Before commit:

```bash
./gradlew test
./gradlew verify<changed-version>
```

Before release:

```bash
./gradlew verifyAll
```

---

# PHASE 17 — Prevent Merge Conflicts

Do NOT let multiple LLMs edit shared core simultaneously.

Use ownership:

```text
Agent A → common/practices
Agent B → fabric-1.16.1
Agent C → fabric-1.21.1
Agent D → fabric-26.3
Agent E → verification/fixtures/docs
```

Rules:

```text
one writer per subsystem
small commits
no unrelated formatting
no mass renames
no shared-file edits unless required
```

Freeze adapter contracts before parallel version work.

Parallelize only version-local implementations.

---

# PHASE 18 — One Task = One Commit

Every LLM task should be small.

Bad:

```text
finish 1.21.1
```

Good:

```text
Implement and verify LivePortals121.createNetherPortal.
```

Commit format:

```text
adapter121: implement portal creation

Test:
./gradlew verify121Portals
PASS 4/4
```

---

# PHASE 19 — Completion Report Required

Every coding task must end with:

```markdown
## Completed

### Changed
- ...

### Automated tests
- command:
- result:

### Minecraft verification
- version:
- suite:
- passed:
- failed:

### Production stubs
- remaining:

### Next task
- ...
```

No vague:

```text
should work
probably fixed
compile successful
```

---

# PHASE 20 — After Test Infrastructure Exists

Only then finish remaining project work.

Order:

```text
01 build verification framework
02 move temporary probes out of production
03 deterministic fixtures
04 world lifecycle tests
05 seed differential tests
06 Stage-B tests
07 player harness
08 scenario integration tests
09 verify/fix 1.16.1
10 verify/fix 1.21.1
11 verify/fix 26.3
12 client smoke tests
13 capability flags
14 documentation
15 release cleanup
```

---

# PHASE 21 — Current Known Cleanup

Fix current HEAD specifically:

```text
[x] remove TempLavaProbe116.arm()
[x] remove TempLavaProbe121.arm()
[x] remove TempLavaProbe263.arm()

[x] move Temp*Probe classes to verification code
[x] remove PROBELAVA logging
[x] remove TEMP diagnostics

[x] verify latest Stage-B changes again

[x] add 5 verified 1.21.1 fixtures
[x] consolidate 26.3 fixtures

[x] update docs/version-status.md
```

---

# PHASE 22 — Final Verification

A version is complete only when:

```text
build PASS
unit tests PASS
adapter contracts PASS
headless Minecraft tests PASS
seed fixtures PASS
scenario tests PASS
world cleanup PASS
client smoke PASS
```

---

# Definition of Done

The final automated command should be:

```bash
./gradlew verifyAll
```

and output something equivalent to:

```text
Shared tests        PASS
Minecraft 1.16.1   PASS
Minecraft 1.21.1   PASS
Minecraft 26.3     PASS

World lifecycle     PASS
Seed analyzers      PASS
Stage-B lava        PASS
Portals             PASS
Dragon              PASS
Loadouts            PASS
Checkpoints         PASS
Scenarios           PASS
Statistics          PASS

0 failures
```

After this, only a small final manual client smoke test should be necessary.

# Critical Rule

From now on:

```text
NO NEW COMPLEX FEATURE
WITHOUT AN AUTOMATED TEST THAT CAN PROVE IT.
```
