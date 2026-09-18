# Custom scenarios

Drop JSON files in `config/speedrun-practice/scenarios/`. They load at
startup and on `/practice config reload`. Schema:

```json
{
  "id": "bastion_housing_default",
  "type": "bastion",
  "displayName": "Housing Bastion Practice",
  "world": { "dimension": "nether" },
  "seed": {
    "source": "search",
    "filters": [ { "type": "bastion_type", "value": "housing" } ]
  },
  "spawn": { "type": "structure_exterior", "distance": 40 },
  "loadout": "bastion_default",
  "timer": { "start": "player_move", "stop": "scenario_complete" }
}
```

## Fields

- `id`: unique, lowercase letters/digits/`_`/`-`.
- `type`: one of `overworld`, `buried_treasure`, `nether`, `bastion`,
  `fortress`, `blind_travel`, `postblind`, `stronghold`, `end`, `onecycle`,
  `custom`.
- `world.dimension`: `overworld`, `nether`, `end`.
- `seed.source`: `random`, `fixed`, `list`, `search`, `favorites`, `recent`,
  `imported`. `seed.value` pins a fixed seed; `seed.list` names an import.
- `spawn.type`: `world_spawn`, `structure` (+`structure`), `structure_exterior`
  (+`structure`, `distance`), `random_nether` (+`distance`), `end_platform`,
  `custom` (+`x`,`y`,`z`).
- `loadout`: a loadout id from `definitions/loadouts/` or your saved presets.
- `timer.start`: `scenario_load`, `player_move`, `dimension_entry`,
  `portal_exit`, `manual`. `timer.stop`: `scenario_complete`,
  `dimension_entry`, `structure_reached`, `dragon_death`, `manual`.
- `settings`: free-form string map forwarded to the scenario.

## Errors

Bad JSON produces a readable error naming the file, field, and expected
value (e.g. `scenarios/foo.json: spawn.distance must be a number >= 0`).
The offending file is skipped; everything else still loads; the game never
crashes. Validate early with the `ScenarioLoader` unit tests as reference.
