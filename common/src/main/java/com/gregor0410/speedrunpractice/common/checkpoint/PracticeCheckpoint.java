package com.gregor0410.speedrunpractice.common.checkpoint;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Serializable practice snapshot (plan section 23). Only the necessary data:
 * practice, seed, dimension, position, player, scenario and timer snapshots.
 * Never the whole server.
 */
public final class PracticeCheckpoint {
    private final PracticeId practice;
    private final long seed;
    private final PracticeDimension dimension;
    private final PracticePosition position;
    private final PlayerSnapshot player;
    private final ScenarioSnapshot scenario;
    private final TimerSnapshot timer;

    public PracticeCheckpoint(PracticeId practice, long seed, PracticeDimension dimension,
                              PracticePosition position, PlayerSnapshot player,
                              ScenarioSnapshot scenario, TimerSnapshot timer) {
        if (practice == null || dimension == null || position == null || player == null || timer == null) {
            throw new IllegalArgumentException("checkpoint core fields must not be null");
        }
        this.practice = practice;
        this.seed = seed;
        this.dimension = dimension;
        this.position = position;
        this.player = player;
        this.scenario = scenario;
        this.timer = timer;
    }

    public PracticeId practice() {
        return practice;
    }

    public long seed() {
        return seed;
    }

    public PracticeDimension dimension() {
        return dimension;
    }

    public PracticePosition position() {
        return position;
    }

    public PlayerSnapshot player() {
        return player;
    }

    /** Null when the scenario keeps no extra state. */
    public ScenarioSnapshot scenario() {
        return scenario;
    }

    public TimerSnapshot timer() {
        return timer;
    }

    /** Health, food, XP, inventory, effects and selected slot. */
    public static final class PlayerSnapshot {
        private final PracticePosition position;
        private final double health;
        private final int food;
        private final float saturation;
        private final int xpLevel;
        private final int xpPoints;
        private final Loadout inventory;
        private final List<String> effects;
        private final int selectedSlot;

        public PlayerSnapshot(PracticePosition position, double health, int food, float saturation,
                              int xpLevel, int xpPoints, Loadout inventory,
                              List<String> effects, int selectedSlot) {
            if (position == null) {
                throw new IllegalArgumentException("position must not be null");
            }
            this.position = position;
            this.health = health;
            this.food = food;
            this.saturation = saturation;
            this.xpLevel = xpLevel;
            this.xpPoints = xpPoints;
            this.inventory = inventory;
            this.effects = effects == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(effects);
            this.selectedSlot = selectedSlot;
        }

        public PracticePosition position() {
            return position;
        }

        public double health() {
            return health;
        }

        public int food() {
            return food;
        }

        public float saturation() {
            return saturation;
        }

        public int xpLevel() {
            return xpLevel;
        }

        public int xpPoints() {
            return xpPoints;
        }

        public Loadout inventory() {
            return inventory;
        }

        public List<String> effects() {
            return effects;
        }

        public int selectedSlot() {
            return selectedSlot;
        }
    }

    /** Scenario-specific extra state, by scenario-defined string map. */
    public interface ScenarioSnapshot {
        String type();

        Map<String, String> data();
    }

    public static final class TimerSnapshot {
        private final long elapsedMs;
        private final boolean running;

        public TimerSnapshot(long elapsedMs, boolean running) {
            this.elapsedMs = Math.max(0L, elapsedMs);
            this.running = running;
        }

        public long elapsedMs() {
            return elapsedMs;
        }

        public boolean running() {
            return running;
        }
    }
}
