package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;

/** Practice-world lifecycle, owned by the version adapter. */
public interface WorldAdapter {
    PracticeWorld createPracticeWorld(long seed, PracticeWorldOptions options) throws PracticeException;

    void deletePracticeWorld(PracticeWorld world) throws PracticeException;

    void resetPracticeWorld(PracticeWorld world, long seed, PracticeWorldOptions options) throws PracticeException;

    /** World-spawn position used as the default teleport target / search origin. */
    PracticePosition spawnPosition(PracticeWorld world) throws PracticeException;

    /** Creation options; kept minimal so every version can honour them. */
    final class PracticeWorldOptions {
        private final PracticeDimension dimension;
        private final boolean generateStructures;
        private final boolean bonusChest;

        private PracticeWorldOptions(PracticeDimension dimension, boolean generateStructures, boolean bonusChest) {
            this.dimension = dimension;
            this.generateStructures = generateStructures;
            this.bonusChest = bonusChest;
        }

        public static Builder builder(PracticeDimension dimension) {
            return new Builder(dimension);
        }

        public PracticeDimension dimension() {
            return dimension;
        }

        public boolean generateStructures() {
            return generateStructures;
        }

        public boolean bonusChest() {
            return bonusChest;
        }

        public static final class Builder {
            private final PracticeDimension dimension;
            private boolean generateStructures = true;
            private boolean bonusChest;

            private Builder(PracticeDimension dimension) {
                if (dimension == null) {
                    throw new IllegalArgumentException("dimension must not be null");
                }
                this.dimension = dimension;
            }

            public Builder generateStructures(boolean generate) {
                this.generateStructures = generate;
                return this;
            }

            public Builder bonusChest(boolean bonus) {
                this.bonusChest = bonus;
                return this;
            }

            public PracticeWorldOptions build() {
                return new PracticeWorldOptions(dimension, generateStructures, bonusChest);
            }
        }
    }
}
