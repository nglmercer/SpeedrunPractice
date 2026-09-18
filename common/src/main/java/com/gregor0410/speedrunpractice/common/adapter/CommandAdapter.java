package com.gregor0410.speedrunpractice.common.adapter;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.commands.PracticeCommands;

/**
 * Registers the shared {@code /practice} command tree on the version's
 * dispatcher. Argument parsing stays version-side; shared code sees only the
 * typed {@link CommandContextView}.
 */
public interface CommandAdapter {
    void register(PracticeCommands.Node root, CommandExecutor executor) throws PracticeException;

    /** Shared-code view of one command invocation. */
    interface CommandContextView {
        /** Action id of the matched node (e.g. "restart.same"). */
        String action();

        PracticePlayer player() throws PracticeException;

        boolean hasArg(String name);

        String stringArg(String name);

        long longArg(String name);

        int intArg(String name);

        void feedback(String message);
    }

    /** Handles one invocation; returns the Brigadier-style result code. */
    interface CommandExecutor {
        int execute(CommandContextView context) throws PracticeException;
    }
}
