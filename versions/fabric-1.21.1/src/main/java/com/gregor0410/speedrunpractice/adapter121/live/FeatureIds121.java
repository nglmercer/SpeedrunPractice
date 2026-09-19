package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.Structure;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Mapping-free half of the 1.21.1 structure-id vocabulary: the same preset
 * ids, aliases and dimension homes the 1.16.1/26.3 adapters use, expressed
 * as pure strings so the skeleton module stays compilable offline. The Loom
 * port adds the {@code Identifier}/{@code ResourceKey}/{@code TagKey}
 * overloads on top of {@link #namespaced(String)} without changing these
 * contracts.
 */
public final class FeatureIds121 {
    private static final Set<String> NETHER_HOME = netherHome();
    private static final Set<String> END_HOME = endHome();

    private FeatureIds121() {
    }

    private static Set<String> netherHome() {
        Set<String> home = new HashSet<String>();
        home.add("bastion_remnant");
        home.add("fortress");
        home.add("nether_fossil");
        return Collections.unmodifiableSet(home);
    }

    private static Set<String> endHome() {
        Set<String> home = new HashSet<String>();
        home.add("end_city");
        return Collections.unmodifiableSet(home);
    }

    /** Trims, lowercases, and strips the {@code minecraft:} namespace for comparisons. */
    public static String bare(String id) {
        if (id == null) {
            return "";
        }
        String clean = id.trim().toLowerCase();
        if (clean.startsWith("minecraft:")) {
            return clean.substring("minecraft:".length());
        }
        return clean;
    }

    /**
     * Parses a preset/filter id into a namespaced {@code namespace:path}
     * string: trimmed, lowercased, defaulting to the {@code minecraft}
     * namespace, with the 1.16.1 {@code ocean_monument} alias resolved to
     * {@code minecraft:monument}. Charset validation stays at the mapping
     * boundary (the port's {@code Identifier} overload); this method only
     * rejects blank input.
     */
    public static String namespaced(String id) throws PracticeException {
        if (id == null || id.trim().isEmpty()) {
            throw new PracticeException("Empty structure id", "Empty structure id.");
        }
        String clean = id.trim().toLowerCase();
        if (!clean.contains(":")) {
            clean = "minecraft:" + clean;
        }
        if ("minecraft:ocean_monument".equals(clean)) {
            clean = "minecraft:monument";
        }
        return clean;
    }

    /** Parses a preset/filter id into a namespaced structure identifier. */
    public static Identifier identifier(String id) throws PracticeException {
        return Identifier.of(namespaced(id));
    }

    /** Registry key for a preset/filter structure id. */
    public static RegistryKey<Structure> key(String id) throws PracticeException {
        return RegistryKey.of(RegistryKeys.STRUCTURE, identifier(id));
    }

    /** Structure-tag key for a preset/filter id (family lookups like `village`). */
    public static TagKey<Structure> tagKey(String id) throws PracticeException {
        return TagKey.of(RegistryKeys.STRUCTURE, identifier(id));
    }

    /** Which dimension generates the structure (for generator resolution). */
    public static PracticeDimension homeDimension(String structureId) {
        String id = bare(structureId);
        if (NETHER_HOME.contains(id)) {
            return PracticeDimension.NETHER;
        }
        if (END_HOME.contains(id)) {
            return PracticeDimension.END;
        }
        return PracticeDimension.OVERWORLD;
    }

    /** True for bastion ids (any namespace/case), for subtype filtering. */
    public static boolean isBastion(String id) {
        return "bastion_remnant".equals(bare(id));
    }

    /** True for stronghold ids (any namespace/case), for ring filtering. */
    public static boolean isStronghold(String id) {
        return "stronghold".equals(bare(id));
    }
}
