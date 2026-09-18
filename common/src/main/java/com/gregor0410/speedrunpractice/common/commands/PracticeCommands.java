package com.gregor0410.speedrunpractice.common.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared {@code /practice} command-tree model (plan section 20). Versions
 * register it through {@code CommandAdapter}; legacy 1.16.1 spellings are
 * kept as aliases.
 */
public final class PracticeCommands {
    private PracticeCommands() {
    }

    public enum ArgType {
        STRING,
        WORD,
        LONG,
        INT
    }

    public static final class Arg {
        private final String name;
        private final ArgType type;
        private final boolean optional;

        public Arg(String name, ArgType type, boolean optional) {
            this.name = name;
            this.type = type;
            this.optional = optional;
        }

        public String name() {
            return name;
        }

        public ArgType type() {
            return type;
        }

        public boolean optional() {
            return optional;
        }
    }

    public static final class Node {
        private final String name;
        private final String description;
        private final List<Arg> args = new ArrayList<Arg>();
        private final Map<String, Node> children = new LinkedHashMap<String, Node>();
        private String action;

        private Node(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public static Node literal(String name, String description) {
            return new Node(name, description);
        }

        public Node action(String action) {
            this.action = action;
            return this;
        }

        public Node arg(String name, ArgType type, boolean optional) {
            args.add(new Arg(name, type, optional));
            return this;
        }

        public Node then(Node child) {
            children.put(child.name, child);
            return this;
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public List<Arg> args() {
            return Collections.unmodifiableList(args);
        }

        public Map<String, Node> children() {
            return Collections.unmodifiableMap(children);
        }

        public String action() {
            return action;
        }
    }

    /** Full tree: new commands plus legacy aliases. */
    public static Node buildTree() {
        Node root = Node.literal("practice", "Speedrun Practice root command");

        root.then(Node.literal("start", "Start a practice")
                .then(Node.literal("<type>", "Practice type id")
                        .arg("type", ArgType.WORD, false).action("start")));
        root.then(Node.literal("restart", "Restart with a new seed").action("restart.new")
                .then(Node.literal("same", "Restart on the same seed").action("restart.same"))
                .then(Node.literal("new", "Restart on a new seed").action("restart.new")));
        root.then(Node.literal("stop", "Stop the active practice").action("stop"));

        root.then(Node.literal("seed", "Show the current seed").action("seed.show")
                .then(Node.literal("<seed>", "Use a specific seed").arg("seed", ArgType.LONG, false).action("seed.set"))
                .then(Node.literal("next", "Next seed from the source").action("seed.next"))
                .then(Node.literal("previous", "Previous seed").action("seed.previous"))
                .then(Node.literal("favorite", "Favorite the current seed").action("seed.favorite")));

        Node seeds = Node.literal("seeds", "Seed search");
        seeds.then(Node.literal("search", "Search with a saved preset")
                .then(Node.literal("<preset>", "Preset name").arg("preset", ArgType.WORD, false).action("seeds.search")));
        seeds.then(Node.literal("cancel", "Cancel the running search").action("seeds.cancel"));
        seeds.then(Node.literal("results", "Show search results").action("seeds.results"));
        seeds.then(Node.literal("export", "Export results").action("seeds.export"));
        seeds.then(Node.literal("import", "Import a seed list").action("seeds.import"));
        root.then(seeds);

        Node loadout = Node.literal("loadout", "Inventory presets");
        loadout.then(Node.literal("list", "List presets").action("loadout.list"));
        loadout.then(Node.literal("save", "Save current inventory")
                .then(Node.literal("<name>", "Preset name").arg("name", ArgType.WORD, false).action("loadout.save")));
        loadout.then(Node.literal("apply", "Apply a preset")
                .then(Node.literal("<name>", "Preset name").arg("name", ArgType.WORD, false).action("loadout.apply")));
        loadout.then(Node.literal("delete", "Delete a preset")
                .then(Node.literal("<name>", "Preset name").arg("name", ArgType.WORD, false).action("loadout.delete")));
        root.then(loadout);

        Node checkpoint = Node.literal("checkpoint", "Practice checkpoints");
        checkpoint.then(Node.literal("save", "Save a checkpoint").action("checkpoint.save"));
        checkpoint.then(Node.literal("load", "Load the checkpoint").action("checkpoint.load"));
        checkpoint.then(Node.literal("clear", "Clear the checkpoint").action("checkpoint.clear"));
        root.then(checkpoint);

        root.then(Node.literal("stats", "Show statistics").action("stats.show")
                .then(Node.literal("<practice>", "Practice id").arg("practice", ArgType.WORD, false).action("stats.show"))
                .then(Node.literal("reset", "Reset statistics").action("stats.reset")));
        root.then(Node.literal("config", "Configuration")
                .then(Node.literal("reload", "Reload config + scenarios").action("config.reload")));

        // Legacy aliases from the 1.16.1 runtime.
        root.then(Node.literal("end", "Legacy: end practice").action("legacy.end")
                .arg("seed", ArgType.LONG, true));
        root.then(Node.literal("nether", "Legacy: nether practice").action("legacy.nether")
                .arg("seed", ArgType.LONG, true));
        root.then(Node.literal("overworld", "Legacy: overworld practice").action("legacy.overworld")
                .arg("seed", ArgType.LONG, true)
                .then(Node.literal("bt", "Legacy: buried treasure").action("legacy.bt").arg("seed", ArgType.LONG, true)));
        root.then(Node.literal("postblind", "Legacy: post-blind practice").action("legacy.postblind")
                .arg("maxDist", ArgType.INT, true).arg("seed", ArgType.LONG, true));
        root.then(Node.literal("stronghold", "Legacy: stronghold practice").action("legacy.stronghold")
                .arg("seed", ArgType.LONG, true));
        root.then(Node.literal("seedlist", "Legacy: seed list")
                .then(Node.literal("reload", "Reload the seed list").action("legacy.seedlist.reload"))
                .then(Node.literal("toggle", "Toggle the seed list").action("legacy.seedlist.toggle")));
        root.then(Node.literal("world", "Show the current world key").action("legacy.world"));
        root.then(Node.literal("revert", "Legacy: revert to a split")
                .then(Node.literal("<split>", "Split name").arg("split", ArgType.WORD, false).action("legacy.revert")));
        return root;
    }
}
