# Loadouts

Named inventory presets stored as JSON (shared model) and applied by the
version `InventoryAdapter` (which owns NBT). Shared code never stores raw
NBT.

```json
{
  "id": "bastion_default",
  "items": [
    { "item": "minecraft:iron_pickaxe", "slot": 0, "count": 1 },
    { "item": "minecraft:bread", "slot": 1, "count": 16 }
  ]
}
```

## Operations

List, save-current-inventory, apply, duplicate, rename, delete, export,
import — via `/practice loadout ...` and the scenario screen. Item ids are
namespaced (`minecraft:...`); ids that do not exist on the running version
are reported by `RegistryAdapter` and skipped with a warning instead of
failing the whole preset.

## Slots

`0-35` main inventory, `100-103` armor (boots..helmet, legacy-compatible),
`-106` offhand. Counts clamp to the version stack size.

## Migration

Legacy v1 configs keep `practiceInventories` (NBT strings x 3 slots per
practice) and keep working; `ConfigMigrator` carries them into v2 untouched,
where they appear as `<practice>_slot_<1-3>` presets alongside JSON loadouts.
