# Client smoke checklist

Final manual layer (plan phase 14). Everything else must already be green via
automated verification (`./gradlew verifyAll`) before this checklist runs.
Only client-specific behavior needs a human-driven Minecraft client:

- GUI rendering
- button clicks
- keybind registration
- key presses
- screen transitions

Repeat once per supported version. Check the box only after observing the real
behavior in-game on that version.

## Minecraft 1.16.1

```text
[ ] Client starts with the 1.16.1 mod jar in mods/
[ ] Practice menu opens (keybind + command)
[ ] Scenario setup screen opens
[ ] Start works (world creates, player teleports, timer starts)
[ ] Results screen opens after completion
[ ] Restart button works (same seed resets without a stale world)
[ ] Keybinds work (open menu, reset, checkpoint capture/restore)
```

## Minecraft 1.21.1

```text
[ ] Client starts with the 1.21.1 mod jar in mods/
[ ] Practice menu opens (keybind + command)
[ ] Scenario setup screen opens
[ ] Start works (world creates, player teleports, timer starts)
[ ] Results screen opens after completion
[ ] Restart button works (same seed resets without a stale world)
[ ] Keybinds work (open menu, reset, checkpoint capture/restore)
```

## Minecraft 26.3

```text
[ ] Client starts with the 26.3 mod jar in mods/
[ ] Practice menu opens (keybind + command)
[ ] Scenario setup screen opens
[ ] Start works (world creates, player teleports, timer starts)
[ ] Results screen opens after completion
[ ] Restart button works (same seed resets without a stale world)
[ ] Keybinds work (open menu, reset, checkpoint capture/restore)
```

## Notes

- Record the date, jar version, and any failure in `docs/version-status.md`
  when a row is checked off; never flip a shared status cell from this
  checklist alone without naming the observed build.
- If a step fails, file the repro (version, scenario, seed, screen) and add or
  extend the matching automated suite first — GUI fixes without a covering
  server-side contract tend to regress silently.
