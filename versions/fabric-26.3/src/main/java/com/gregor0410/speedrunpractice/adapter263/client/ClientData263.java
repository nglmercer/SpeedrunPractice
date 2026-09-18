package com.gregor0410.speedrunpractice.adapter263.client;

import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Snapshot helpers for screen models. Call on the server thread. */
final class ClientData263 {
    private ClientData263() {
    }

    static List<String> loadoutIds(PracticeRuntime runtime) {
        List<String> ids = new ArrayList<String>();
        for (Loadout loadout : runtime.loadouts().list()) {
            ids.add(loadout.id());
        }
        Collections.sort(ids);
        return ids;
    }

    static List<String> customIds(PracticeRuntime runtime) {
        List<String> ids = new ArrayList<String>(runtime.customScenarios().keySet());
        Collections.sort(ids);
        return ids;
    }
}
