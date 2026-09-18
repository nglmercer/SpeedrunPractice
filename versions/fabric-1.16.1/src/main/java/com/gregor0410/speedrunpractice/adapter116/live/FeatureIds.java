package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared structure-id vocabulary for the live 1.16.1 adapter: id to
 * {@link StructureFeature} plus each structure's home dimension (used both
 * for generator resolution in live lookups and for seed analysis).
 */
final class FeatureIds {
    private static final Map<String, StructureFeature<?>> FEATURES = features();
    private static final Set<String> NETHER_HOME = netherHome();
    private static final Set<String> END_HOME = endHome();

    private FeatureIds() {
    }

    private static Map<String, StructureFeature<?>> features() {
        Map<String, StructureFeature<?>> map = new HashMap<String, StructureFeature<?>>();
        map.put("village", StructureFeature.VILLAGE);
        map.put("minecraft:village", StructureFeature.VILLAGE);
        map.put("shipwreck", StructureFeature.SHIPWRECK);
        map.put("minecraft:shipwreck", StructureFeature.SHIPWRECK);
        map.put("buried_treasure", StructureFeature.BURIED_TREASURE);
        map.put("minecraft:buried_treasure", StructureFeature.BURIED_TREASURE);
        map.put("ruined_portal", StructureFeature.RUINED_PORTAL);
        map.put("minecraft:ruined_portal", StructureFeature.RUINED_PORTAL);
        map.put("bastion_remnant", StructureFeature.BASTION_REMNANT);
        map.put("minecraft:bastion_remnant", StructureFeature.BASTION_REMNANT);
        map.put("fortress", StructureFeature.FORTRESS);
        map.put("minecraft:fortress", StructureFeature.FORTRESS);
        map.put("stronghold", StructureFeature.STRONGHOLD);
        map.put("minecraft:stronghold", StructureFeature.STRONGHOLD);
        map.put("mineshaft", StructureFeature.MINESHAFT);
        map.put("minecraft:mineshaft", StructureFeature.MINESHAFT);
        map.put("ocean_ruin", StructureFeature.OCEAN_RUIN);
        map.put("minecraft:ocean_ruin", StructureFeature.OCEAN_RUIN);
        map.put("desert_pyramid", StructureFeature.DESERT_PYRAMID);
        map.put("minecraft:desert_pyramid", StructureFeature.DESERT_PYRAMID);
        map.put("jungle_pyramid", StructureFeature.JUNGLE_PYRAMID);
        map.put("minecraft:jungle_pyramid", StructureFeature.JUNGLE_PYRAMID);
        map.put("igloo", StructureFeature.IGLOO);
        map.put("minecraft:igloo", StructureFeature.IGLOO);
        map.put("swamp_hut", StructureFeature.SWAMP_HUT);
        map.put("minecraft:swamp_hut", StructureFeature.SWAMP_HUT);
        map.put("mansion", StructureFeature.MANSION);
        map.put("minecraft:mansion", StructureFeature.MANSION);
        map.put("monument", StructureFeature.MONUMENT);
        map.put("ocean_monument", StructureFeature.MONUMENT);
        map.put("minecraft:ocean_monument", StructureFeature.MONUMENT);
        map.put("pillager_outpost", StructureFeature.PILLAGER_OUTPOST);
        map.put("minecraft:pillager_outpost", StructureFeature.PILLAGER_OUTPOST);
        map.put("nether_fossil", StructureFeature.NETHER_FOSSIL);
        map.put("minecraft:nether_fossil", StructureFeature.NETHER_FOSSIL);
        map.put("end_city", StructureFeature.END_CITY);
        map.put("minecraft:end_city", StructureFeature.END_CITY);
        return Collections.unmodifiableMap(map);
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

    /** Resolves a user-facing structure id, failing with a helpful message. */
    static StructureFeature<?> resolve(String structureId) throws PracticeException {
        if (structureId == null) {
            throw new IllegalArgumentException("structureId must not be null");
        }
        StructureFeature<?> feature = FEATURES.get(structureId.trim().toLowerCase());
        if (feature == null) {
            throw new PracticeException("Unknown structure \"" + structureId + "\"",
                    "Unknown structure \"" + structureId + "\". Try village, shipwreck,"
                            + " buried_treasure, ruined_portal, bastion_remnant, fortress or stronghold.");
        }
        return feature;
    }

    /** Which dimension generates the structure (for generator resolution). */
    static PracticeDimension homeDimension(String structureId) {
        String id = bare(structureId);
        if (NETHER_HOME.contains(id)) {
            return PracticeDimension.NETHER;
        }
        if (END_HOME.contains(id)) {
            return PracticeDimension.END;
        }
        return PracticeDimension.OVERWORLD;
    }

    /** Strips namespace/lowercase for comparisons. */
    static String bare(String id) {
        String clean = id == null ? "" : id.trim().toLowerCase();
        if (clean.startsWith("minecraft:")) {
            clean = clean.substring("minecraft:".length());
        }
        return clean;
    }
}
