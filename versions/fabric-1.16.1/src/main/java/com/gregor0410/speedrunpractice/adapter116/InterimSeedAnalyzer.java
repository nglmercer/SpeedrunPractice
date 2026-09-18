package com.gregor0410.speedrunpractice.adapter116;

import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;

/**
 * Interim seed analyzer (plan step 09 replaces it with SeedAnalyzer116):
 * unconstrained queries trivially match so random/list seed sources work;
 * anything asking for a biome or structure does not match until real 1.16.1
 * worldgen math lands. Pure Java, shared by the live adapter and unit tests.
 */
public final class InterimSeedAnalyzer implements SeedAnalyzer {
    @Override
    public SeedAnalysis analyze(long seed, SeedQuery query) {
        return SeedAnalysis.of(matches(seed, query), null);
    }

    @Override
    public boolean matches(long seed, SeedQuery query) {
        if (query == null) {
            return false;
        }
        return query.requiredBiome() == null && query.requiredStructures().isEmpty();
    }
}
