# Practices

All practices run solo and locally through one lifecycle
(`prepare -> start -> tick* -> stop`, plus `reset` with a `ResetMode`).
Every practice supports same-seed, new-seed, previous-seed, checkpoint, and
full reset.

## Overworld (`overworld`)

- Random spawn, village / shipwreck / buried-treasure / lava-pool /
  portal-entry starts, or a custom structure start via `spawn.structure`.
- Settings: `spawn.mode`, `spawn.structure`, `loadout`.
- Completion (`overworld.goal`, default `enter_nether`): `reach_structure`
  (needs `spawn.structure` + `overworld.completeRadius`), `obtain_item`
  (needs `overworld.item` + `overworld.count`), `manual`.

## Buried Treasure (`buried_treasure`)

- Teleports near a buried treasure with beach-biome preference (legacy
  behavior preserved); falls back to spawn when none is found in range.
- Spawns `buried_treasure.spawnOffset` (default 12) blocks from the chest;
  reaching the chest within `buried_treasure.completeRadius` (default 4)
  finishes. Chest-open detection needs a version inventory event (pending).

## Nether (`nether`)

- Portal-exit start, navigation, bastion/fortress locating, bastion <->
  fortress routes, blaze and pearl-collection setups via loadouts.
- Completion (`nether.goal`, default `exit_nether`): `reach_bastion`,
  `reach_fortress`, `obtain_pearls` (`nether.targetPearls`, default 16),
  `obtain_blaze_rods` (`nether.targetRods`, default 8), `manual`.

## Bastion (`bastion`)

- Types: housing, stables, treasure, bridge, random.
- Start modes: outside, entrance, route start, random exterior, portal exit.
- Settings: `bastion.type`, `spawn.mode`, `spawn.distance`, loadout, armor,
  blocks, pickaxe, food, health, difficulty, piglin state.
- Completion (`bastion.goal`, default `obtain_target_pearls`):
  `bastion.targetPearls` (default 16), `leave_bastion`, `reach_exit`,
  `manual`.

## Fortress (`fortress`)

- Find, enter, blaze collection, navigation, exit practice.
- Settings: `fortress.mode` (`find|enter|blaze|navigation|exit`).
- Completion differs per mode: `find` enters the area, `enter`/`navigation`
  reach the fortress, `blaze` holds `fortress.targetRods` (default 8),
  `exit` leaves after entering.

## Blind Travel (`blind_travel`)

- Random nether coordinate, target-distance control, portal-construction
  setup, blind conversion, overworld stronghold-distance readout.
- Settings: `blind.targetDistance`, `blind.givePortalMats`.
- Completion: leaving the Nether finishes and records `blind.exit`,
  `blind.stronghold`, `blind.distance` on the run context.

## Post Blind (`postblind`)

- Random post-blind position with min/max stronghold distance, eye count,
  inventory presets, triangulation-friendly spawns.
- Settings: `postblind.minDist`, `postblind.maxDist`, `postblind.eyes`.
- Completion (`postblind.goal`, default `reach_stronghold`):
  `enter_stronghold`, `reach_portal_room` (portal-room metadata when the
  version provides it), `manual`.

## Stronghold (`stronghold`)

- Entry, navigation, portal-room search, portal-room start, pre-eye setup,
  custom eye counts.
- Settings: `stronghold.mode` (`entry|navigation|portal_search|portal_room`),
  `stronghold.eyes`.
- Completion: `navigation`/`portal_search` reach the portal room (stairs
  fallback), `portal_room` enters the End portal, `entry` via
  `stronghold.goal` (`manual` default, `reach_stronghold`).

## End (`end`)

- End entry, tower practice, dragon fight, bed cycle, instaperch, custom
  inventory, random tower states (via PTLib on 1.16.1).
- Completion: the practice world's dragon dies (fires exactly once).

## One Cycle (`onecycle`)

- End setup tuned for the one-cycle attempt: fixed loadout, dragon perch
  setup where the version supports `DRAGON_FORCE_PERCH`.
- Completion: the practice world's dragon dies (fires exactly once).

## Custom (`custom`)

- User JSON in `config/speedrun-practice/scenarios/`; see
  `docs/custom-scenarios.md`. Invalid JSON yields a readable chat/log error
  and never crashes the game.
