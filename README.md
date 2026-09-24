# SpeedrunPractice

[![CI Build](https://github.com/nglmercer/SpeedrunPractice/actions/workflows/build.yml/badge.svg)](https://github.com/nglmercer/SpeedrunPractice/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/nglmercer/SpeedrunPractice)](https://github.com/nglmercer/SpeedrunPractice/releases/latest)

A mod designed to streamline the practicing process for Minecraft speedrunners. Includes end practice, post blind practice, nether practice, and overworld practice, as well as the ability to change structure generation settings like nether structure region size and what bastion types can spawn.

Single-player only: no ranked matchmaking, ratings, multiplayer racing, accounts, or online services.

Forked from [Gregor0410/SpeedrunPractice](https://github.com/Gregor0410/SpeedrunPractice) (upstream); this repo is being rebuilt on one shared practice engine with per-version adapters.

## Supported versions

| Minecraft | Role | Status |
| --------- | ---- | ------ |
| 1.16.1 | Primary speedrunning build | Playable (legacy runtime) |
| 1.21.1 | Modern compatibility build | Server-side verified headless; client smoke pending |
| 26.3 | Latest supported build | Server-side verified headless; client smoke pending |

Releases currently ship only the legacy 1.16.1 jar. All three new adapters are wired (no `pending()` left) and server-side verified on headless dedicated servers; per-version release jars return once the client smoke checklist passes per version (see [docs/version-status.md](docs/version-status.md) and [docs/client-smoke.md](docs/client-smoke.md)). Until then, the playable 1.16.1 experience is the legacy runtime described below.

## Features

- **End Practice:** Practice the dragon fight with customizable settings.
- **Nether Practice:** Practice nether navigation and structure finding.
- **Post Blind Practice:** Practice the stronghold search from a random location.
- **Overworld Practice:** Practice overworld starts and specific structures like Buried Treasure.
- **Inventory Management:** Save and load custom inventories for each practice type.
- **Customizable Structure Generation:** Change bastion types and nether structure frequency.
- **Seed List:** Practice on a specific set of seeds from a file.

Coming with the new engine (shared code done, server-side verified headless, client smoke pending): Bastion, Fortress, Blind Travel, Stronghold, and One Cycle scenarios, named loadouts, filtered seed search, checkpoints, timers, statistics, and data-driven custom scenarios via JSON. See [docs/practices.md](docs/practices.md), [docs/seed-search.md](docs/seed-search.md), [docs/loadouts.md](docs/loadouts.md), and [docs/custom-scenarios.md](docs/custom-scenarios.md).

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

The new unified `/practice` tree (start/restart, seeds search, loadouts, checkpoints, stats) is specified in [docs/commands.md](docs/commands.md) and lands with the version adapters; legacy spellings above stay as aliases.

## Seed List Feature

To use a specific list of seeds:
1. Create a file named `speedrun-practice-seeds.txt` in your `config` folder.
2. Add one seed per line.
3. In-game, run `/practice seedlist reload` to load the seeds.
4. Enable the feature via the settings menu or `/practice seedlist toggle`.
5. Every time you start a practice without providing a specific seed, the mod will cycle through your list.

## Building from source

Requires JDK 17 for shared modules and 1.16.1, JDK 21 for 1.21.1, and JDK 25 for 26.3. Shared modules compile offline; only the Loom version modules download Minecraft artifacts (needs network).

```bash
./gradlew :common:build :practices:build :seed-search:build :test-support:build
./gradlew :versions:fabric-1.16.1:build :versions:fabric-1.21.1:build :versions:fabric-26.3:build
./gradlew build   # everything, incl. the 1.16.1 Fabric mod (build/libs of its module)
```

On Windows use `.\gradlew.bat` instead of `./gradlew`. The offline architecture guard (no JDK needed) is `sh scripts/verify-architecture.sh`, or `powershell -File scripts/verify-architecture.ps1` on Windows.

## Docs

- [ARCHITECTURE.md](ARCHITECTURE.md) - module map for developers
- [CONTRIBUTING.md](CONTRIBUTING.md) - workflow and commit rules
- [docs/development.md](docs/development.md) - builds, tests, adding features
- [docs/compatibility.md](docs/compatibility.md) - per-version capability matrix
- [docs/practices.md](docs/practices.md), [docs/seed-search.md](docs/seed-search.md), [docs/loadouts.md](docs/loadouts.md), [docs/custom-scenarios.md](docs/custom-scenarios.md), [docs/commands.md](docs/commands.md) - feature guides

## License

MIT, preserved from upstream - see [LICENSE](LICENSE).
