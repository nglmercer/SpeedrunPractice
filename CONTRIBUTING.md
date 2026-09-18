# CONTRIBUTING

## Ground rules

1. `common/`, `practices/`, `seed-search/` MUST NOT import `net.minecraft.*`,
   `net.fabricmc.*`, or use mixins. The build fails if they do.
2. Shared abstraction over version checks: no `if (mcVersion == ...)` in
   shared logic. Add an adapter method instead.
3. Small commits with the plan prefixes: `build:`, `core:`, `adapter:`,
   `practice:`, `seed:`, `gui:`, `config:`, `test:`, `docs:`, `fix:`,
   `refactor:`.
4. Each change ends in a buildable state; never rewrite unrelated code and
   never silently remove working behavior.

## Workflow per task

```bash
./gradlew :common:test                                # smallest relevant build first
./gradlew :common:build :practices:build :seed-search:build
sh scripts/verify-architecture.sh                   # offline import guard
# Windows: powershell -File scripts/verify-architecture.ps1
./gradlew build                                       # full build (needs network for Loom)
```

Add or update tests with the change, then report: changed files, behavior,
tests run, build results, limitations, next task.

## Adding a practice

1. Add the variant to `definitions/practices/` as JSON if it fits the schema.
2. Implement `PracticeScenario` in `:practices` using only adapter calls.
3. Register it in `ScenarioRegistry`.
4. Add known-seed rows under `test-data/<version>/`.
5. Document it in `docs/practices.md`.

## Adding a Minecraft version

New code goes in a new `versions/fabric-<mc>/` module implementing
`MinecraftAdapter`. If a practice must be copied to support it, stop and fix
the abstraction instead. Declare gaps with `Capability` + `supports()`.

## Non-goals (will be rejected)

Ranked matchmaking, Elo, multiplayer racing, remote servers, accounts,
anti-cheat, spectator sync, seasons, queues, ranked APIs. Local practice only.
