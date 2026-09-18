package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.CommandAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutCompatibility;
import com.gregor0410.speedrunpractice.common.seeds.SeedResult;
import com.gregor0410.speedrunpractice.common.seeds.SeedSearchTask;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;

/**
 * Shared action dispatcher for the {@code /practice} command tree (plan
 * section 17). Version adapters register the tree built by
 * {@code PracticeCommands} and route invocations here; this class keeps
 * command behavior in one version-independent place instead of spreading it
 * across Brigadier callbacks.
 *
 * <p>Expected failures surface as {@link PracticeException} with user-facing
 * messages; the version layer shows {@code getUserMessage()} and returns 0.
 * Success returns 1 after sending readable feedback.
 */
public final class PracticeActionExecutor implements CommandAdapter.CommandExecutor {
    private final PracticeRuntime runtime;

    public PracticeActionExecutor(PracticeRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        this.runtime = runtime;
    }

    @Override
    public int execute(CommandAdapter.CommandContextView context) throws PracticeException {
        if (context == null) {
            throw new IllegalArgumentException("command context must not be null");
        }
        String action = context.action();
        if (action == null) {
            throw new PracticeException("Command arrived without an action id",
                    "Unknown command. Try /practice start <type> or /practice stop.");
        }
        if ("start".equals(action)) {
            return start(context);
        } else if ("restart.new".equals(action)) {
            runtime.reset(PracticeScenario.ResetMode.NEW_SEED);
            context.feedback("Restarted on new seed " + currentSeed() + ".");
            return 1;
        } else if ("restart.same".equals(action)) {
            runtime.reset(PracticeScenario.ResetMode.SAME_SEED);
            context.feedback("Restarted on seed " + currentSeed() + ".");
            return 1;
        } else if ("stop".equals(action)) {
            runtime.stop();
            context.feedback("Practice stopped.");
            return 1;
        } else if ("seed.show".equals(action)) {
            OptionalLong seed = runtime.currentSeed();
            context.feedback(seed.isPresent() ? "Current seed: " + seed.getAsLong() + "." : "No practice is running.");
            return 1;
        } else if ("seed.set".equals(action)) {
            long seed = longArg(context, action, "seed");
            runtime.restartOnSeed(seed);
            context.feedback("Restarted on seed " + seed + ".");
            return 1;
        } else if ("seed.next".equals(action)) {
            runtime.reset(PracticeScenario.ResetMode.NEW_SEED);
            context.feedback("Seed: " + currentSeed() + ".");
            return 1;
        } else if ("seed.previous".equals(action)) {
            runtime.reset(PracticeScenario.ResetMode.PREVIOUS_SEED);
            context.feedback("Seed: " + currentSeed() + ".");
            return 1;
        } else if ("seed.favorite".equals(action)) {
            context.feedback("Favorited seed " + runtime.favoriteCurrentSeed() + ".");
            return 1;
        } else if ("seeds.search".equals(action)) {
            return seedSearch(context);
        } else if ("seeds.cancel".equals(action)) {
            context.feedback(runtime.cancelSearch() ? "Seed search cancelled." : "No seed search is running.");
            return 1;
        } else if ("seeds.results".equals(action)) {
            context.feedback(searchSummary());
            return 1;
        } else if ("seeds.export".equals(action)) {
            return seedsExport(context);
        } else if ("seeds.import".equals(action)) {
            context.feedback("Drop a seed list into " + runtime.seedStore().dir().resolve("imports")
                    + "/<name>.txt (one seed per line, '#' comments allowed), then start with "
                    + "seed.source=imported and seed.list=<name>.");
            return 1;
        } else if ("loadout.list".equals(action)) {
            context.feedback(loadoutSummary());
            return 1;
        } else if ("loadout.save".equals(action)) {
            return loadoutSave(context);
        } else if ("loadout.apply".equals(action)) {
            return loadoutApply(context);
        } else if ("loadout.delete".equals(action)) {
            return loadoutDelete(context);
        } else if ("checkpoint.save".equals(action)) {
            runtime.saveCheckpoint();
            context.feedback("Checkpoint saved.");
            return 1;
        } else if ("checkpoint.load".equals(action)) {
            runtime.restoreCheckpoint();
            context.feedback("Checkpoint restored.");
            return 1;
        } else if ("checkpoint.clear".equals(action)) {
            runtime.clearCheckpoint();
            context.feedback("Checkpoint cleared.");
            return 1;
        } else if ("stats.show".equals(action)) {
            context.feedback(statsSummary(context));
            return 1;
        } else if ("stats.reset".equals(action)) {
            runtime.statistics().resetAll();
            context.feedback("Statistics reset.");
            return 1;
        } else if ("config.reload".equals(action)) {
            context.feedback("Reloaded " + runtime.reload() + ".");
            return 1;
        } else if (action.startsWith("legacy.")) {
            context.feedback("The legacy 1.16.1 command path owns this command; "
                    + "the new runtime leaves it untouched.");
            return 1;
        }
        throw new PracticeException("Unknown practice action: \"" + action + "\"",
                "Unknown command. Try /practice start <type> or /practice stop.");
    }

    private int start(CommandAdapter.CommandContextView context) throws PracticeException {
        String typeId = stringArg(context, "start", "type");
        PracticeType type;
        try {
            type = PracticeType.fromId(typeId);
        } catch (IllegalArgumentException bad) {
            throw new PracticeException("Unknown practice type: " + typeId,
                    "Unknown practice \"" + typeId + "\". Available: " + availableTypes() + ".");
        }
        PracticePlayer player = context.player();
        PracticeSession session = runtime.startPractice(type, new PracticeSettings(), player);
        context.feedback("Started " + type.displayName() + " on seed " + session.seed() + ".");
        return 1;
    }

    private int seedSearch(CommandAdapter.CommandContextView context) throws PracticeException {
        String preset = stringArg(context, "seeds.search", "preset");
        context.feedback(runtime.startPresetSearch(preset));
        return 1;
    }

    private int seedsExport(CommandAdapter.CommandContextView context) throws PracticeException {
        SeedSearchTask task = runtime.activeSearch();
        if (task == null) {
            throw new PracticeException("seeds.export without a search",
                    "No seed search has been started. Run /practice seeds search <preset> first.");
        }
        List<SeedResult> results = task.results();
        if (results.isEmpty()) {
            throw new PracticeException("seeds.export without results",
                    "The search has no results yet. Check /practice seeds results and retry.");
        }
        String name = "search-" + System.currentTimeMillis();
        Path file;
        try {
            file = runtime.seedStore().exportResults(name, results);
        } catch (IOException failure) {
            throw new PracticeException("Cannot export " + results.size() + " seeds: " + failure.getMessage(),
                    "Could not export results: " + failure.getMessage());
        }
        context.feedback("Exported " + results.size() + " seeds to " + file.getFileName() + ".");
        return 1;
    }

    private String searchSummary() {
        SeedSearchTask.SearchProgress progress = runtime.searchProgress();
        if (progress == null) {
            return "No seed search has been started.";
        }
        StringBuilder out = new StringBuilder("Search: ");
        out.append(progress.seedsTested()).append(" tested, ");
        out.append(progress.seedsMatched()).append(" matched");
        if (progress.seedsVerified() > 0) {
            out.append(", ").append(progress.seedsVerified()).append(" verified");
        }
        if (progress.seedsFailed() > 0) {
            out.append(", ").append(progress.seedsFailed()).append(" failed");
        }
        if (progress.finished()) {
            out.append(", finished");
        }
        if (progress.cancelled()) {
            out.append(", cancelled");
        }
        out.append('.');
        SeedSearchTask task = runtime.activeSearch();
        if (task != null) {
            List<SeedResult> results = task.results();
            if (!results.isEmpty()) {
                out.append(" Seeds:");
                int shown = Math.min(5, results.size());
                for (int i = 0; i < shown; i++) {
                    out.append(' ').append(results.get(i).seed());
                }
                if (results.size() > shown) {
                    out.append(" ...");
                }
            }
        }
        return out.toString();
    }

    private String loadoutSummary() {
        List<Loadout> loadouts = runtime.loadouts().list();
        if (loadouts.isEmpty()) {
            return "No loadouts saved yet.";
        }
        StringBuilder out = new StringBuilder("Loadouts:");
        for (Loadout loadout : loadouts) {
            out.append(' ').append(loadout.id());
        }
        return out.toString();
    }

    private int loadoutSave(CommandAdapter.CommandContextView context) throws PracticeException {
        String name = stringArg(context, "loadout.save", "name");
        Loadout captured = runtime.adapter().inventories().captureLoadout(context.player(), name);
        runtime.loadouts().save(captured);
        runtime.persistLoadouts();
        context.feedback("Saved loadout \"" + name + "\".");
        return 1;
    }

    private int loadoutApply(CommandAdapter.CommandContextView context) throws PracticeException {
        String name = stringArg(context, "loadout.apply", "name");
        Loadout loadout = runtime.loadouts().get(name);
        if (loadout == null) {
            throw new PracticeException("Unknown loadout: " + name,
                    "Loadout \"" + name + "\" does not exist. Check /practice loadout list.");
        }
        LoadoutCompatibility.Result filtered =
                LoadoutCompatibility.filter(runtime.adapter().registries(), loadout);
        for (String warning : filtered.warnings()) {
            context.feedback(warning);
        }
        runtime.adapter().players().applyLoadout(context.player(), filtered.loadout());
        context.feedback("Applied loadout \"" + name + "\".");
        return 1;
    }

    private int loadoutDelete(CommandAdapter.CommandContextView context) throws PracticeException {
        String name = stringArg(context, "loadout.delete", "name");
        if (!runtime.loadouts().delete(name)) {
            throw new PracticeException("Unknown loadout: " + name,
                    "Loadout \"" + name + "\" does not exist. Check /practice loadout list.");
        }
        runtime.persistLoadouts();
        context.feedback("Deleted loadout \"" + name + "\".");
        return 1;
    }

    private String statsSummary(CommandAdapter.CommandContextView context) throws PracticeException {
        PracticeId practice;
        if (context.hasArg("practice")) {
            practice = PracticeId.of(context.stringArg("practice"));
        } else if (runtime.engine().currentScenario() != null) {
            practice = runtime.engine().currentScenario().id();
        } else {
            throw new PracticeException("stats.show without a practice id and no active practice",
                    "No practice is running. Specify one: /practice stats <id>.");
        }
        int attempts = runtime.statistics().attempts(practice);
        int completed = runtime.statistics().completed(practice);
        OptionalLong best = runtime.statistics().personalBest(practice);
        return practice.value() + ": " + attempts + " attempts, " + completed + " completed, PB "
                + (best.isPresent() ? PracticeMenuModel.ResultsScreen.formatTime(best.getAsLong()) : "--") + ".";
    }

    private long currentSeed() throws PracticeException {
        OptionalLong seed = runtime.currentSeed();
        if (!seed.isPresent()) {
            throw new PracticeException("Reset left no active practice",
                    "No practice is running. Start one with /practice start <type>.");
        }
        return seed.getAsLong();
    }

    private static String availableTypes() {
        StringBuilder out = new StringBuilder();
        for (PracticeType type : ScenarioRegistry.types()) {
            if (type == PracticeType.CUSTOM) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(type.id());
        }
        return out.toString();
    }

    private static String stringArg(CommandAdapter.CommandContextView context, String action, String name)
            throws PracticeException {
        if (!context.hasArg(name)) {
            throw new PracticeException("Action \"" + action + "\" is missing argument \"" + name + "\"",
                    "Missing \"" + name + "\".");
        }
        return context.stringArg(name);
    }

    private static long longArg(CommandAdapter.CommandContextView context, String action, String name)
            throws PracticeException {
        if (!context.hasArg(name)) {
            throw new PracticeException("Action \"" + action + "\" is missing argument \"" + name + "\"",
                    "Missing \"" + name + "\".");
        }
        return context.longArg(name);
    }
}
