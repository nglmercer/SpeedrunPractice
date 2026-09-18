# Seed search

Two stages (plan section 18). Stage A rejects cheaply with seed math /
structure-position / biome checks through `SeedAnalyzer`; stage B verifies
survivors with real chunk generation via a version-side `Verifier`. Results
record seed, Minecraft version, matched filters, structure locations,
verification state, and timestamp.

## Sources (`SeedSource`)

`random`, `fixed`, `list` (legacy `speedrun-practice-seeds.txt` plus named
imports), `search`, `favorites`, `recent`, `imported`. Every source tracks
current/previous seeds for `/practice seed next|previous`.

## Filters

- Overworld: spawn biome/coordinates, village/shipwreck/buried-treasure/
  ruined-portal distance, lava availability, ocean/desert proximity.
- Nether: spawn coordinates, bastion distance/type, fortress distance,
  bastion+fortress combos, fortress-after-bastion distance.
- Stronghold: distance, ring, portal-room characteristics, deterministic eye
  data where available.
- Combined presets ship under `definitions/seed-filters/`, e.g.
  `housing-close-fortress.json`.

Filters compose with AND/OR. Adding one: implement `SeedFilters.Filter`
against `SeedAnalyzer` findings (pure Java, no Minecraft imports).

## Running and cancelling

Searches run off the game thread and are cancellable
(`SeedSearchTask.cancel()`); the UI exposes Cancel and the game never locks.
Heavy scans never touch world/player objects.

## Files (`config/speedrun-practice/seeds/`)

```text
favorites.json   tagged + noted favorite seeds
recent.json      recently used seeds
searches/        saved named searches (e.g. housing.json)
imports/         user seed lists (*.txt, one seed per line)
failed.json      seeds that failed verification (skipped by default)
```
