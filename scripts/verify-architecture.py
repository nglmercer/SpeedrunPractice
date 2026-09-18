#!/usr/bin/env python3
"""Offline architecture guard: shared modules must not import Minecraft/Fabric.

Mirrors the :common / :practices verifyNoMinecraftImports Gradle tasks for
environments without a JDK/Gradle. Exit 0 = clean, 1 = violations.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ["common/src/main/java", "practices/src/main/java",
           "seed-search/src/main/java", "test-support/src/main/java"]
PATTERN = re.compile(r"^\s*import\s+net\.(minecraft|fabricmc)\.")


def main():
    violations = []
    for module in MODULES:
        base = ROOT / module
        if not base.is_dir():
            print("skip (missing): %s" % module)
            continue
        for java in sorted(base.rglob("*.java")):
            for n, line in enumerate(java.read_text(encoding="utf-8").splitlines(), 1):
                if PATTERN.match(line):
                    violations.append("%s:%d: %s" % (java.relative_to(ROOT), n, line.strip()))
    if violations:
        print("ARCHITECTURE VIOLATIONS (shared code imports Minecraft/Fabric):")
        for v in violations:
            print("  " + v)
        return 1
    print("OK: no Minecraft/Fabric imports in shared modules.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
