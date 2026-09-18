package com.gregor0410.speedrunpractice.common;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * Guardrail test for plan section 2: {@code common} must never import
 * Minecraft/Fabric classes. Runs from the module directory in Gradle.
 */
public class ArchitectureTest {
    @Test
    public void noMinecraftImportsInCommon() throws Exception {
        Path base = Paths.get("src/main/java");
        assertTrue("expected to run from the :common module directory", Files.isDirectory(base));
        List<String> violations = new ArrayList<String>();
        Stream<Path> walk = Files.walk(base);
        try {
            for (Path file : (Iterable<Path>) walk.filter(new java.util.function.Predicate<Path>() {
                @Override
                public boolean test(Path path) {
                    return path.toString().endsWith(".java");
                }
            }).collect(java.util.stream.Collectors.toList())) {
                int lineNumber = 0;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    lineNumber++;
                    String trimmed = line.trim();
                    if (trimmed.startsWith("import net.minecraft.") || trimmed.startsWith("import net.fabricmc.")) {
                        violations.add(base.relativize(file) + ":" + lineNumber + ": " + trimmed);
                    }
                }
            }
        } finally {
            walk.close();
        }
        assertTrue("common/ imports Minecraft/Fabric classes: " + violations, violations.isEmpty());
    }
}
