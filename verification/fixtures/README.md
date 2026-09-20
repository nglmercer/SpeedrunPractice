# Canonical deterministic fixtures

Each `<version>.json` file is the only fixture consumed by verification tasks.
Rows must be marked `verified: true` only after a real dedicated-server run
has compared the recorded spawn, biome, structures, bastion type, stronghold
portal room, and lava result. A row marked false is intentionally rejected by
`FixtureValidator`; it is a visible gate, not an allowed placeholder.

The 1.21.1 collector can be rerun with
`./gradlew verifyHeadlessFixtures121` (PowerShell on Windows). It writes
`build/verification/1.21.1/fixture-candidate.json`; promote that candidate
only after reviewing the accompanying `report.json` and server log.
