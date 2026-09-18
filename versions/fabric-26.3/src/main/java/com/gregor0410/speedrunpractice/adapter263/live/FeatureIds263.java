package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Structure-id helpers for the 26.3 adapter: bare ids default to the
 * {@code minecraft} namespace, matching how presets and filters name
 * structures ({@code village} vs {@code minecraft:village}).
 */
public final class FeatureIds263 {
    private FeatureIds263() {
    }

    /** Strips the {@code minecraft:} namespace (if present) for comparisons. */
    public static String bare(String id) {
        if (id == null) {
            return "";
        }
        String clean = id.trim();
        if (clean.startsWith("minecraft:")) {
            return clean.substring("minecraft:".length());
        }
        return clean;
    }

    /** Parses a preset/filter id into a namespaced structure identifier. */
    public static Identifier identifier(String id) throws PracticeException {
        if (id == null || id.trim().isEmpty()) {
            throw new PracticeException("Empty structure id", "Empty structure id.");
        }
        String clean = id.trim().toLowerCase();
        if (!clean.contains(":")) {
            clean = "minecraft:" + clean;
        }
        if ("minecraft:ocean_monument".equals(clean)) {
            // 1.16.1 vocabulary: the structure is `monument` on modern versions.
            clean = "minecraft:monument";
        }
        Identifier parsed = Identifier.tryParse(clean);
        if (parsed == null) {
            throw new PracticeException("Bad structure id \"" + id + "\"",
                    "Bad structure id \"" + id + "\".");
        }
        return parsed;
    }

    /** Registry key for a preset/filter structure id. */
    public static ResourceKey<Structure> key(String id) throws PracticeException {
        return ResourceKey.create(Registries.STRUCTURE, identifier(id));
    }

    /** Structure-tag key for a preset/filter id (family lookups like `village`). */
    public static TagKey<Structure> tagKey(String id) throws PracticeException {
        return TagKey.create(Registries.STRUCTURE, identifier(id));
    }
}
