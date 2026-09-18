package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Structure location queries without leaking chunk/generator classes. */
public interface StructureAdapter {
    Optional<StructureLocation> locateNearest(PracticeWorld world, StructureQuery query) throws PracticeException;

    List<StructureLocation> locate(PracticeWorld world, StructureQuery query, int limit) throws PracticeException;

    /** What to look for, where from, and how far. */
    final class StructureQuery {
        private final String structureId;
        private final PracticePosition center;
        private final int radius;

        private StructureQuery(String structureId, PracticePosition center, int radius) {
            this.structureId = structureId;
            this.center = center;
            this.radius = radius;
        }

        public static Builder builder(String structureId) {
            return new Builder(structureId);
        }

        public String structureId() {
            return structureId;
        }

        /** Null means "from world spawn". */
        public PracticePosition center() {
            return center;
        }

        public int radius() {
            return radius;
        }

        public static final class Builder {
            private final String structureId;
            private PracticePosition center;
            private int radius = 10000;

            private Builder(String structureId) {
                if (structureId == null || structureId.trim().isEmpty()) {
                    throw new IllegalArgumentException("structureId must not be empty");
                }
                this.structureId = structureId.trim();
            }

            public Builder center(PracticePosition center) {
                this.center = center;
                return this;
            }

            public Builder radius(int radius) {
                if (radius < 0) {
                    throw new IllegalArgumentException("radius must be >= 0");
                }
                this.radius = radius;
                return this;
            }

            public StructureQuery build() {
                return new StructureQuery(structureId, center, radius);
            }
        }
    }

    /** One located structure plus optional version metadata (bastion type, portal room...). */
    final class StructureLocation {
        /**
         * Bastion subtype ({@code treasure}, {@code bridge},
         * {@code housing}, {@code stables}) when the version can read it.
         */
        public static final String BASTION_TYPE_KEY = "bastion_type";
        /** Stronghold portal-room center as {@code "x,y,z"}. */
        public static final String PORTAL_ROOM_KEY = "portal_room";

        private final String structureId;
        private final PracticePosition position;
        private final Map<String, String> metadata;

        public StructureLocation(String structureId, PracticePosition position, Map<String, String> metadata) {
            if (structureId == null || position == null) {
                throw new IllegalArgumentException("structureId and position must not be null");
            }
            this.structureId = structureId;
            this.position = position;
            this.metadata = metadata == null
                    ? Collections.<String, String>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
        }

        public String structureId() {
            return structureId;
        }

        public PracticePosition position() {
            return position;
        }

        public Map<String, String> metadata() {
            return metadata;
        }
    }
}
