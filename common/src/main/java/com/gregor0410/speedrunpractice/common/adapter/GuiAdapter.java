package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeResult;

/**
 * Practice GUI entry points. Screen content models live in shared
 * {@code gui} code; each version renders them with its own Screen API.
 */
public interface GuiAdapter {
    void openMainMenu(PracticePlayer player) throws PracticeException;

    void openScenarioScreen(PracticePlayer player, PracticePreset preset) throws PracticeException;

    void openResultsScreen(PracticePlayer player, PracticeResult result) throws PracticeException;

    /** False on dedicated servers / unsupported environments; commands still work. */
    boolean isAvailable();
}
