# Commands

Root: `/practice`. Every action below is also reachable from the GUI; on
servers without GUI support the commands are the full interface.

## Practice control

```text
/practice start <type>     Start overworld|nether|bastion|fortress|blind_travel|
                           postblind|stronghold|end|onecycle|custom ...
/practice restart          New-seed reset (default)
/practice restart same     Same-seed reset
/practice restart new      New-seed reset
/practice stop             Stop the active practice
```

## Seeds

```text
/practice seed              Show the current seed
/practice seed <seed>      Practice a specific seed
/practice seed next         Next seed from the source
/practice seed previous    Previous seed
/practice seed favorite    Favorite the current seed

/practice seeds search <preset>   Search with a saved preset
/practice seeds cancel            Cancel the running search
/practice seeds results           Show search results
/practice seeds export            Export results
/practice seeds import            Import a seed list
```

## Loadouts

```text
/practice loadout list
/practice loadout save <name>     Save current inventory
/practice loadout apply <name>
/practice loadout delete <name>
```

## Checkpoints / stats / config

```text
/practice checkpoint save
/practice checkpoint load
/practice checkpoint clear

/practice stats
/practice stats <practice>
/practice stats reset

/practice config reload            Reload config + custom scenarios
```

## Short practice aliases

`/practice end|nether|overworld [bt]|postblind [maxDist]|stronghold [seed]`
are engine-owned shortcuts for `/practice start <type>` (plan section 2):
the optional seed starts that practice on a fixed seed, and postblind's
optional maxDist caps the stronghold distance (default 1000).

## Legacy spellings (legacy tree, kept)

`/practice seedlist reload|toggle`, `/practice <type> inventory <1-3>
select|save`, `/practice world`, `/practice revert <split>`, `/instaperch`,
and the bare `/practice seed [<seed>]` forms are still implemented by the
1.16.1 legacy command tree; see `PracticeCommands.buildTree()`.
