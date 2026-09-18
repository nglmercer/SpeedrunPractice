package com.gregor0410.speedrunpractice.common.loadout;

import com.gregor0410.speedrunpractice.common.api.PracticeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named-preset store: list/save/duplicate/rename/delete/export/import
 * (plan section 14). Versions persist via config files; this manager owns
 * the semantics.
 */
public interface LoadoutManager {
    List<Loadout> list();

    /** Null when the id is unknown. */
    Loadout get(String id);

    void save(Loadout loadout);

    void duplicate(String id, String newId);

    void rename(String id, String newId);

    /** False when the id was unknown. */
    boolean delete(String id);

    String exportJson(String id);

    Loadout importLoadout(String json) throws PracticeException;

    final class InMemoryLoadoutManager implements LoadoutManager {
        private final Map<String, Loadout> loadouts = new LinkedHashMap<String, Loadout>();

        @Override
        public synchronized List<Loadout> list() {
            return Collections.unmodifiableList(new ArrayList<Loadout>(loadouts.values()));
        }

        @Override
        public synchronized Loadout get(String id) {
            return id == null ? null : loadouts.get(id);
        }

        @Override
        public synchronized void save(Loadout loadout) {
            if (loadout == null) {
                throw new IllegalArgumentException("loadout must not be null");
            }
            loadouts.put(loadout.id(), loadout);
        }

        @Override
        public synchronized void duplicate(String id, String newId) {
            Loadout existing = require(id);
            if (newId == null || newId.trim().isEmpty()) {
                throw new IllegalArgumentException("new loadout id must not be empty");
            }
            if (loadouts.containsKey(newId)) {
                throw new IllegalArgumentException("Loadout already exists: " + newId);
            }
            loadouts.put(newId, new Loadout(newId, existing.items()));
        }

        @Override
        public synchronized void rename(String id, String newId) {
            Loadout existing = require(id);
            if (newId == null || newId.trim().isEmpty()) {
                throw new IllegalArgumentException("new loadout id must not be empty");
            }
            if (!id.equals(newId) && loadouts.containsKey(newId)) {
                throw new IllegalArgumentException("Loadout already exists: " + newId);
            }
            loadouts.remove(id);
            loadouts.put(newId, new Loadout(newId, existing.items()));
        }

        @Override
        public synchronized boolean delete(String id) {
            return loadouts.remove(id) != null;
        }

        @Override
        public synchronized String exportJson(String id) {
            return require(id).toJson(true);
        }

        @Override
        public synchronized Loadout importLoadout(String json) throws PracticeException {
            Loadout loadout = Loadout.fromJson(json);
            loadouts.put(loadout.id(), loadout);
            return loadout;
        }

        private Loadout require(String id) {
            Loadout loadout = id == null ? null : loadouts.get(id);
            if (loadout == null) {
                throw new IllegalArgumentException("Unknown loadout: " + id);
            }
            return loadout;
        }
    }
}
