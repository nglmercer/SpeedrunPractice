# Practices

All practices run solo and locally through one lifecycle
(`prepare -> start -> tick* -> stop`, plus `reset` with a `ResetMode`).
Every practice supports same-seed, new-seed, previous-seed, checkpoint, and
full reset.

## Overworld (`overworld`)

- Random spawn, village / shipwreck / buried-treasure / lava-pool /
  portal-entry starts, or a custom structure start via `spawn.structure`.
- Settings: `spawn.mode`, `spawn.structure`, `loadout`.

## Buried Treasure (`buried_treasure`)

- Teleports near a buried treasure with beach-biome preference (legacy
  behavior preserved); falls back to spawn when none is found in range.

## Nether (`nether`)

- Portal-exit start, navigation, bastion/fortress locating, bastion <->
  fortress routes, blaze and pearl-collection setups via loadouts.

## Bastion (`bastion`)

- Types: housing, stables, treasure, bridge, random.
- Start modes: outside, entrance, route start, random exterior, portal exit.
- Settings: `bastion.type`, `spawn.mode`, `spawn.distance`, loadout, armor,
  blocks, pickaxe, food, health, difficulty, piglin state.

## Fortress (`fortress`)

- Find, enter, blaze collection, navigation, exit practice.
- Settings: `fortress.mode` (`find|enter|blaze|navigation|exit`).

## Blind Travel (`blind_travel`)

- Random nether coordinate, target-distance control, portal-construction
  setup, blind conversion, overworld stronghold-distance readout.
- Settings: `blind.targetDistance`, `blind.givePortalMats`.

## Post Blind (`postblind`)

- Random post-blind position with min/max stronghold distance, eye count,
  inventory presets, triangulation-friendly spawns.
- Settings: `postblind.minDist`, `postblind.maxDist`, `postblind.eyes`.

## Stronghold (`stronghold`)

- Entry, navigation, portal-room search, portal-room start, pre-eye setup,
  custom eye counts.
- Settings: `stronghold.mode` (`entry|navigation|portal_search|portal_room`),
  `stronghold.eyes`.

## End (`end`)

- End entry, tower practice, dragon fight, bed cycle, instaperch, custom
  inventory, random tower states (via PTLib on 1.16.1).

## One Cycle (`onecycle`)

- End setup tuned for the one-cycle attempt: fixed loadout, dragon perch
  setup where the version supports `DRAGON_FORCE_PERCH`.

## Custom (`custom`)

- User JSON in `config/speedrun-practice/scenarios/`; see
  `docs/custom-scenarios.md`. Invalid JSON yields a readable chat/log error
  and never crashes the game.
