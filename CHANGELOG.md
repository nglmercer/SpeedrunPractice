# Changelog

## Unreleased — full plan verification

- Every `practiceverify run` suite is implemented on 1.16.1, 1.21.1 and
  26.3 (`worlds`, `structures`, `portals`, `dragon`, `registries`,
  `seed-search`, `scenarios`, `resets`, `checkpoints`, plus `lava`,
  `fixtures`, `all`), with one Gradle task per version × suite
  (`verify<116|121|263><Worlds|…|Checkpoints>`) for the fast dev loop.
- Reusable verification-only harness player per version
  (`VerificationPlayer116/121/263`); player suites exercise the real
  player/inventory/scenario paths with no human input.
- Linux/macOS headless runner (`scripts/verify-headless.sh`, python3 RCON)
  mirroring `verify-headless.ps1`; both bootstrap EULA + RCON + watchdog
  settings, use distinct game ports, and fail fast when the server dies.
- Corrected the stale 1.16.1 seed-20003 buried-treasure fixture row
  (live lookup + analyzer agree on `41,409`); differential mismatches now
  print the analyzer prediction.
- Fixed 1.16.1 shutdown-mixin NPE on early startup failure.
- New client smoke checklist (`docs/client-smoke.md`); version status
  updated with the 2026-09-22 per-suite sweep.

## 2.0.0 — Practice suite v1.0 milestone

New architecture: one practice engine + scenario/seed/statistics systems with
three Minecraft adapters (1.16.1 primary, 1.21.1 modern, 26.3 latest).

- Multi-module Gradle build (`common`, `practices`, `seed-search`,
  `test-support`, per-version adapters); `common` provably free of
  `net.minecraft.*` imports.
- Shared domain API + eleven adapter interfaces with explicit `Capability`
  support flags.
- All legacy 1.16.1 practices (Overworld, Buried Treasure, Nether,
  Post-blind, Stronghold, End) available behind the scenario engine; legacy
  runtime preserved untouched.
- New practices: Bastion, Fortress, Blind Travel, One Cycle, data-driven
  Custom scenarios with validating JSON loader.
- Seed sources (random/fixed/list/search/favorites/recent/imported),
  two-stage cancellable seed search with composable filters, local
  persistence (favorites/recents/searches/imports/failed).
- Named loadouts (save/duplicate/rename/delete/export/import) with legacy
  v1 inventory migration.
- Checkpoints, monotonic internal timer (SpeedRunIGT optional), local
  statistics with JSON/CSV export.
- `/practice` command tree, practice GUI screen models, configurable
  keybinds, versioned config with backup-on-failure migration.
- Known-seed test tables, CI matrix building all modules + three jars with
  checksums, full docs set.

## 1.4.0

Legacy single-module 1.16.1 release (preserved in `src/`).
