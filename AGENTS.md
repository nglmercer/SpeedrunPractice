# AGENTS.md — instructions for coding agents

Read `plan.md` first, then `ARCHITECTURE.md`. The plan is the exhaustive
checklist; this file is the operating procedure.

## Before changing code

1. Inspect the repo; read the existing implementation and its tests.
2. Identify which module owns the behavior (`common` vs `practices` vs a
   version adapter). Never put Minecraft logic in `common`.
3. State: what changes, owning module, API impact, affected versions, tests.

## Rules

- `common/`, `practices/`, `seed-search/` MUST NOT import `net.minecraft.*`
  or `net.fabricmc.*`. No version `if`s in shared logic; add an adapter
  method instead.
- Smallest complete change per task; end every task in a buildable state.
- Preserve working behavior; migrate `old -> adapter/interface -> new` and
  verify parity before deleting anything.
- Add tests where practical. Run the smallest relevant build first
  (`./gradlew :common:test`), then the full `./gradlew build`.
- No Ranked/multiplayer/online scope, ever (plan section 45).
- Never silently delete user config: back up, log a readable error, generate
  a safe default.

## Commit prefixes

`build:`, `core:`, `adapter:`, `practice:`, `seed:`, `gui:`, `config:`,
`test:`, `docs:`, `fix:`, `refactor:`.

## Report format

Changed files; implemented behavior; tests run + results; per-version build
results; known limitations; single highest-priority next task.
