package com.gregor0410.speedrunpractice.common.seeds;

import java.util.OptionalLong;

/** Seed provider contract (plan section 15). */
public interface SeedSource {
    OptionalLong nextSeed(SeedRequest request);

    OptionalLong previousSeed();

    OptionalLong currentSeed();
}
