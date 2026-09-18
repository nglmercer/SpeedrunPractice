package com.gregor0410.speedrunpractice.common.api;

import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything a scenario needs for one run: session, adapter, world, player,
 * settings and seed, plus an attribute bag for cross-cutting services
 * (loadouts, seed store) installed by the engine.
 */
public final class PracticeContext {
    private final PracticeSession session;
    private final MinecraftAdapter adapter;
    private final PracticePlayer player;
    private final PracticeSettings settings;
    private final Map<String, Object> attributes = new HashMap<String, Object>();
    private PracticeWorld world;
    private long seed;

    public PracticeContext(PracticeSession session, MinecraftAdapter adapter,
                           PracticeWorld world, PracticePlayer player,
                           PracticeSettings settings, long seed) {
        this.session = session;
        this.adapter = adapter;
        this.world = world;
        this.player = player;
        this.settings = settings == null ? new PracticeSettings() : settings;
        this.seed = seed;
    }

    public PracticeSession session() {
        return session;
    }

    public MinecraftAdapter adapter() {
        return adapter;
    }

    public PracticeWorld world() {
        return world;
    }

    public void setWorld(PracticeWorld world) {
        this.world = world;
    }

    public PracticePlayer player() {
        return player;
    }

    public PracticeSettings settings() {
        return settings;
    }

    public long seed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
        if (session != null) {
            session.setSeed(seed);
        }
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
}
