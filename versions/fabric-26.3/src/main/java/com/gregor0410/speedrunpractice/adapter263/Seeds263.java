package com.gregor0410.speedrunpractice.adapter263;

import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;

/**
 * Interim 26.3 seed analyzer (plan step 18 replaces it with real worldgen
 * math): unconstrained queries trivially match so random/list seed sources
 * and preset searches without filters work; anything asking for a biome,
 * structure, bastion type, ring or lava does not match. Pure Java, shared
 * by the live adapter and unit tests.
 */
public final class Seeds263 implements SeedAnalyzer {
    @Override
    public SeedAnalysis analyze(long seed, SeedQuery query) {
        return SeedAnalysis.of(matches(seed, query), null);
    }

    @Override
    public boolean matches(long seed, SeedQuery query) {
        if (query == null) {
            return false;
        }
        return query.requiredBiome() == null
                && query.requiredStructures().isEmpty()
                && query.bastionType() == null
                && query.strongholdRing() <= 0
                && !query.lavaRequired();
    }
}
