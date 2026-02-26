# SpeedrunPractice

[![CI Build](https://github.com/Gregor0410/SpeedrunPractice/actions/workflows/build.yml/badge.svg)](https://github.com/Gregor0410/SpeedrunPractice/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/Gregor0410/SpeedrunPractice)](https://github.com/Gregor0410/SpeedrunPractice/releases/latest)

A mod designed to streamline the practicing process for Minecraft speedrunners. Includes end practice, post blind practice, nether practice, and overworld practice, as well as the ability to change structure generation settings like nether structure region size and what bastion types can spawn.

## Features

- **End Practice:** Practice the dragon fight with customizable settings.
- **Nether Practice:** Practice nether navigation and structure finding.
- **Post Blind Practice:** Practice the stronghold search from a random location.
- **Overworld Practice:** Practice overworld starts and specific structures like Buried Treasure.
- **Inventory Management:** Save and load custom inventories for each practice type.
- **Customizable Structure Generation:** Change bastion types and nether structure frequency.
- **Seed List:** Practice on a specific set of seeds from a file.

## Settings

The settings menu can be opened by pressing the button marked **"Speedrun Practice Options"** in the options menu. The settings are saved to `config/speedrun-practice.json`.

## Commands

### Practice Commands

- `/practice end [seed]` - Teleports the player to the end dimension.
- `/practice nether [seed]` - Teleports the player to the nether dimension of a new world.
- `/practice postblind [maxDist] [seed]` - Teleports the player to a random location at least `maxDist` away from a stronghold in a new world.
- `/practice overworld [seed]` - Teleports the player to the overworld spawn of a new world.
- `/practice overworld bt [seed]` - Teleports the player to a Buried Treasure in a new world.
- `/practice stronghold [seed]` - Teleports the player to a stronghold in a new world.

### Inventory Commands

- `/practice <practiceType> inventory <slot> select` - Sets the inventory slot (1-3) to be used for the specified practice type.
- `/practice <practiceType> inventory <slot> save` - Saves the current inventory to the given slot of the given practice type.

### Seed Commands

- `/practice seed` - Display the current practice seed.
- `/practice seed <seed>` - Set the practice seed.
- `/practice seedlist reload` - Reloads the seed list from `config/speedrun-practice-seeds.txt`.
- `/practice seedlist toggle` - Toggles the use of the seed list.

### Utility Commands

- `/instaperch` - Causes the dragon to enter the perch phase.
- `/practice world` - Displays the RegistryKey of the current world.
- `/practice revert <split>` - Reverts to a previous state (if using auto-save).

## Seed List Feature

To use a specific list of seeds:
1. Create a file named `speedrun-practice-seeds.txt` in your `config` folder.
2. Add one seed per line.
3. In-game, run `/practice seedlist reload` to load the seeds.
4. Enable the feature via the settings menu or `/practice seedlist toggle`.
5. Every time you start a practice without providing a specific seed, the mod will cycle through your list.
