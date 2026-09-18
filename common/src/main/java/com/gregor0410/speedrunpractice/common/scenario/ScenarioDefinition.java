package com.gregor0410.speedrunpractice.common.scenario;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data-driven practice definition (plan section 13). Validated on load with
 * readable errors; see {@code docs/custom-scenarios.md} for the schema.
 */
public final class ScenarioDefinition {
    public static final Set<String> SEED_SOURCES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "random", "fixed", "list", "search", "favorites", "recent", "imported")));
    public static final Set<String> SPAWN_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "world_spawn", "structure", "structure_exterior", "random_nether", "end_platform", "custom")));
    public static final Set<String> TIMER_STARTS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "scenario_load", "player_move", "dimension_entry", "portal_exit", "manual")));
    public static final Set<String> TIMER_STOPS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "scenario_complete", "dimension_entry", "structure_reached", "dragon_death", "manual")));
    public static final Set<String> COMPLETION_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "manual", "dimension_entry", "structure_reached", "dragon_death", "scenario_complete")));

    private final PracticeId id;
    private final PracticeType type;
    private final String displayName;
    private final PracticeDimension dimension;
    private final String seedSource;
    private final Long fixedSeed;
    private final String seedList;
    private final List<SeedFilterSpec> seedFilters;
    private final String spawnType;
    private final String spawnStructure;
    private final int spawnDistance;
    private final Double spawnX;
    private final Double spawnY;
    private final Double spawnZ;
    private final String loadout;
    private final String timerStart;
    private final String timerStop;
    private final String completionType;
    private final String completionDimension;
    private final String completionStructure;
    private final int completionRadius;
    private final String requiresCapability;
    private final Map<String, String> settings;

    private ScenarioDefinition(PracticeId id, PracticeType type, String displayName, PracticeDimension dimension,
                               String seedSource, Long fixedSeed, String seedList, List<SeedFilterSpec> seedFilters,
                               String spawnType, String spawnStructure, int spawnDistance,
                               Double spawnX, Double spawnY, Double spawnZ,
                               String loadout, String timerStart, String timerStop,
                               String completionType, String completionDimension, String completionStructure,
                               int completionRadius, String requiresCapability,
                               Map<String, String> settings) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.dimension = dimension;
        this.seedSource = seedSource;
        this.fixedSeed = fixedSeed;
        this.seedList = seedList;
        this.seedFilters = Collections.unmodifiableList(seedFilters);
        this.spawnType = spawnType;
        this.spawnStructure = spawnStructure;
        this.spawnDistance = spawnDistance;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.loadout = loadout;
        this.timerStart = timerStart;
        this.timerStop = timerStop;
        this.completionType = completionType;
        this.completionDimension = completionDimension;
        this.completionStructure = completionStructure;
        this.completionRadius = completionRadius;
        this.requiresCapability = requiresCapability;
        this.settings = Collections.unmodifiableMap(settings);
    }

    /**
     * One {@code seed.filters[]} entry, used with {@code seed.source=search}.
     * Valid types: {@code biome=<id>}, {@code structure=<id>[:<maxDistance>]},
     * {@code bastionType=<housing|stables|treasure|bridge>},
     * {@code strongholdRing=<n>=1>} and {@code lava=<true|false>}.
     */
    public static final class SeedFilterSpec {
        private final String type;
        private final String value;

        public SeedFilterSpec(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public String type() {
            return type;
        }

        public String value() {
            return value;
        }
    }

    public PracticeId id() {
        return id;
    }

    public PracticeType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    public PracticeDimension dimension() {
        return dimension;
    }

    public String seedSource() {
        return seedSource;
    }

    public Long fixedSeed() {
        return fixedSeed;
    }

    public String seedList() {
        return seedList;
    }

    public List<SeedFilterSpec> seedFilters() {
        return seedFilters;
    }

    public String spawnType() {
        return spawnType;
    }

    public String spawnStructure() {
        return spawnStructure;
    }

    public int spawnDistance() {
        return spawnDistance;
    }

    public Double spawnX() {
        return spawnX;
    }

    public Double spawnY() {
        return spawnY;
    }

    public Double spawnZ() {
        return spawnZ;
    }

    public String loadout() {
        return loadout;
    }

    public String timerStart() {
        return timerStart;
    }

    public String timerStop() {
        return timerStop;
    }

    /** Completion type; {@code manual} (never auto-finishes) when unspecified. */
    public String completionType() {
        return completionType;
    }

    /** Target dimension for {@code dimension_entry}; null otherwise. */
    public String completionDimension() {
        return completionDimension;
    }

    /** Target structure for {@code structure_reached}; null otherwise. */
    public String completionStructure() {
        return completionStructure;
    }

    /** Finish radius for {@code structure_reached}. */
    public int completionRadius() {
        return completionRadius;
    }

    /** Required {@code Capability} name; null when the definition needs none. */
    public String requiresCapability() {
        return requiresCapability;
    }

    public Map<String, String> settings() {
        return settings;
    }

    /** Flattens the definition into runnable scenario settings. */
    public PracticeSettings toSettings() {
        PracticeSettings out = new PracticeSettings(settings);
        out.set("preset", id.value());
        out.set("dimension", dimension.id());
        out.set("seed.source", seedSource);
        if (fixedSeed != null) {
            out.set("seed.value", String.valueOf(fixedSeed));
        }
        if (seedList != null) {
            out.set("seed.list", seedList);
        }
        StringBuilder filters = new StringBuilder();
        for (int i = 0; i < seedFilters.size(); i++) {
            if (i > 0) {
                filters.append(',');
            }
            filters.append(seedFilters.get(i).type()).append('=').append(seedFilters.get(i).value());
        }
        if (filters.length() > 0) {
            out.set("seed.filters", filters.toString());
        }
        out.set("spawn.type", spawnType);
        if (spawnStructure != null) {
            out.set("spawn.structure", spawnStructure);
        }
        out.set("spawn.distance", String.valueOf(spawnDistance));
        if (spawnX != null) {
            out.set("spawn.x", String.valueOf(spawnX));
            out.set("spawn.y", String.valueOf(spawnY));
            out.set("spawn.z", String.valueOf(spawnZ));
        }
        if (loadout != null) {
            out.set("loadout", loadout);
        }
        out.set("timer.start", timerStart);
        out.set("timer.stop", timerStop);
        out.set("completion.type", completionType);
        if (completionDimension != null) {
            out.set("completion.dimension", completionDimension);
        }
        if (completionStructure != null) {
            out.set("completion.structure", completionStructure);
        }
        out.set("completion.radius", String.valueOf(completionRadius));
        if (requiresCapability != null) {
            out.set("requires", requiresCapability);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static ScenarioDefinition fromMap(Map<String, Object> map, String origin)
            throws PracticeException.ScenarioLoadException {
        String id = string(map.get("id"), null);
        if (id == null || !id.matches("[a-z0-9_\\-]+")) {
            throw error(origin, "id must use lowercase letters, digits, '_' or '-'");
        }
        PracticeType type;
        try {
            type = PracticeType.fromId(string(map.get("type"), null));
        } catch (IllegalArgumentException bad) {
            throw error(origin, "type must be one of overworld, buried_treasure, nether, bastion, fortress,"
                    + " blind_travel, postblind, stronghold, end, onecycle, custom");
        }
        String displayName = string(map.get("displayName"), id);
        Map<String, Object> world = object(map.get("world"), null);
        PracticeDimension dimension = PracticeDimension.OVERWORLD;
        if (world != null && world.get("dimension") != null) {
            try {
                dimension = PracticeDimension.fromId(string(world.get("dimension"), null));
            } catch (IllegalArgumentException bad) {
                throw error(origin, "world.dimension must be overworld, nether or end");
            }
        }
        Map<String, Object> seed = object(map.get("seed"), null);
        String seedSource = "random";
        Long fixedSeed = null;
        String seedList = null;
        List<SeedFilterSpec> seedFilters = new ArrayList<SeedFilterSpec>();
        if (seed != null) {
            seedSource = string(seed.get("source"), "random");
            if (!SEED_SOURCES.contains(seedSource)) {
                throw error(origin, "seed.source must be one of " + SEED_SOURCES);
            }
            if (seed.get("value") != null) {
                if (!(seed.get("value") instanceof Number)) {
                    throw error(origin, "seed.value must be a number");
                }
                fixedSeed = ((Number) seed.get("value")).longValue();
            }
            if ("fixed".equals(seedSource) && fixedSeed == null) {
                throw error(origin, "seed.source \"fixed\" needs seed.value");
            }
            if (seed.get("list") != null) {
                seedList = string(seed.get("list"), null);
            }
            Object rawFilters = seed.get("filters");
            if (rawFilters != null) {
                if (!(rawFilters instanceof List)) {
                    throw error(origin, "seed.filters must be an array");
                }
                List<?> list = (List<?>) rawFilters;
                for (int i = 0; i < list.size(); i++) {
                    if (!(list.get(i) instanceof Map)) {
                        throw error(origin, "seed.filters[" + i + "] must be an object");
                    }
                    Map<String, Object> spec = (Map<String, Object>) list.get(i);
                    String filterType = string(spec.get("type"), null);
                    String filterValue = string(spec.get("value"), null);
                    if (filterType == null || filterValue == null) {
                        throw error(origin, "seed.filters[" + i + "] needs \"type\" and \"value\"");
                    }
                    seedFilters.add(new SeedFilterSpec(filterType, filterValue));
                }
            }
        }
        Map<String, Object> spawn = object(map.get("spawn"), null);
        String spawnType = "world_spawn";
        String spawnStructure = null;
        int spawnDistance = 0;
        Double spawnX = null;
        Double spawnY = null;
        Double spawnZ = null;
        if (spawn != null) {
            spawnType = string(spawn.get("type"), "world_spawn");
            if (!SPAWN_TYPES.contains(spawnType)) {
                throw error(origin, "spawn.type must be one of " + SPAWN_TYPES);
            }
            if (spawn.get("structure") != null) {
                spawnStructure = string(spawn.get("structure"), null);
            }
            if (("structure".equals(spawnType) || "structure_exterior".equals(spawnType)) && spawnStructure == null) {
                throw error(origin, "spawn.type \"" + spawnType + "\" needs spawn.structure");
            }
            if (spawn.get("distance") != null) {
                if (!(spawn.get("distance") instanceof Number) || ((Number) spawn.get("distance")).intValue() < 0) {
                    throw error(origin, "spawn.distance must be a number >= 0");
                }
                spawnDistance = ((Number) spawn.get("distance")).intValue();
            }
            if ("custom".equals(spawnType)) {
                spawnX = number(spawn.get("x"), origin, "spawn.x");
                spawnY = number(spawn.get("y"), origin, "spawn.y");
                spawnZ = number(spawn.get("z"), origin, "spawn.z");
            }
        }
        String loadout = string(map.get("loadout"), null);
        Map<String, Object> timer = object(map.get("timer"), null);
        String timerStart = "player_move";
        String timerStop = "scenario_complete";
        if (timer != null) {
            timerStart = string(timer.get("start"), timerStart);
            timerStop = string(timer.get("stop"), timerStop);
            if (!TIMER_STARTS.contains(timerStart)) {
                throw error(origin, "timer.start must be one of " + TIMER_STARTS);
            }
            if (!TIMER_STOPS.contains(timerStop)) {
                throw error(origin, "timer.stop must be one of " + TIMER_STOPS);
            }
        }
        Map<String, Object> completion = object(map.get("completion"), null);
        String completionType = "manual";
        String completionDimension = null;
        String completionStructure = null;
        int completionRadius = 8;
        if (completion != null) {
            completionType = string(completion.get("type"), "manual");
            if (!COMPLETION_TYPES.contains(completionType)) {
                throw error(origin, "completion.type must be one of " + COMPLETION_TYPES);
            }
            if ("dimension_entry".equals(completionType)) {
                completionDimension = string(completion.get("dimension"), null);
                if (completionDimension == null) {
                    throw error(origin, "completion.type \"dimension_entry\" needs completion.dimension");
                }
                try {
                    PracticeDimension.fromId(completionDimension);
                } catch (IllegalArgumentException bad) {
                    throw error(origin, "completion.dimension must be overworld, nether or end");
                }
            }
            if ("structure_reached".equals(completionType)) {
                completionStructure = string(completion.get("structure"), null);
                if (completionStructure == null || completionStructure.trim().isEmpty()) {
                    throw error(origin, "completion.type \"structure_reached\" needs completion.structure");
                }
                completionStructure = completionStructure.trim();
            }
            if (completion.get("radius") != null) {
                if (!(completion.get("radius") instanceof Number)
                        || ((Number) completion.get("radius")).intValue() < 0) {
                    throw error(origin, "completion.radius must be a number >= 0");
                }
                completionRadius = ((Number) completion.get("radius")).intValue();
            }
        }
        String requiresCapability = string(map.get("requires"), null);
        if (requiresCapability != null) {
            try {
                requiresCapability = com.gregor0410.speedrunpractice.common.adapter.Capability
                        .valueOf(requiresCapability.trim().toUpperCase()).name();
            } catch (IllegalArgumentException bad) {
                throw error(origin, "requires must name a Capability: "
                        + java.util.Arrays.asList(
                                com.gregor0410.speedrunpractice.common.adapter.Capability.values()));
            }
        }
        Map<String, String> settings = new LinkedHashMap<String, String>();
        Map<String, Object> rawSettings = object(map.get("settings"), null);
        if (rawSettings != null) {
            for (Map.Entry<String, Object> entry : rawSettings.entrySet()) {
                settings.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return new ScenarioDefinition(PracticeId.of(id), type, displayName, dimension, seedSource, fixedSeed,
                seedList, seedFilters, spawnType, spawnStructure, spawnDistance, spawnX, spawnY, spawnZ,
                loadout, timerStart, timerStop, completionType, completionDimension, completionStructure,
                completionRadius, requiresCapability, settings);
    }

    private static PracticeException.ScenarioLoadException error(String origin, String detail) {
        return new PracticeException.ScenarioLoadException(origin + ": " + detail, origin + ": " + detail);
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, Map<String, Object> fallback) {
        return value instanceof Map ? (Map<String, Object>) value : fallback;
    }

    private static Double number(Object value, String origin, String field)
            throws PracticeException.ScenarioLoadException {
        if (!(value instanceof Number)) {
            throw error(origin, field + " must be a number");
        }
        return ((Number) value).doubleValue();
    }
}
