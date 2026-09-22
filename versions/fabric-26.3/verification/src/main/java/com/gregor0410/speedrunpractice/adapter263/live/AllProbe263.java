package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.verification.ScenarioContractSuite;
import com.gregor0410.speedrunpractice.verification.FixtureDifferentialContractSuite;
import com.gregor0410.speedrunpractice.verification.VerificationSession;
import com.gregor0410.speedrunpractice.verification.WorldLifecycleContractSuite;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Verification-only contract probe for the live 26.3 adapter. */
public final class AllProbe263 {
    private static final long SEED = 12345L;

    private AllProbe263() {
    }

    public static void runNow(final MinecraftServer server) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                AllProbe263.run(server);
            }
        });
    }

    public static void runFixturesNow(final MinecraftServer server) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                LiveAdapter263 adapter = Runtime263.live();
                try {
                    FixtureDifferentialContractSuite.Result result =
                            FixtureDifferentialContractSuite.run(adapter, "/fixtures/26.3.json");
                    VerificationSession.complete("suite.fixtures", result.matches(), result.total(),
                            "fixture differential checks " + result.matches() + "/" + result.total()
                                    + " (" + join(result.evidence()) + ")");
                } catch (Exception failure) {
                    try {
                        VerificationSession.fail("suite.fixtures", failure.getMessage());
                    } catch (Exception reportFailure) {
                        SpeedrunLogger.warn("Could not write fixtures.263 verification: " + reportFailure);
                    }
                }
            }
        });
    }

    private static void run(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter263 adapter = Runtime263.live();
        PracticeWorld world = null;
        PracticeWorld end = null;
        LivePlayer263 player = null;
        try {
            total++;
            if (adapter != null && adapter.server() == server) {
                matches++;
                evidence.add("adapter");
            }

            FixtureDifferentialContractSuite.Result differential =
                    FixtureDifferentialContractSuite.run(adapter, "/fixtures/26.3.json");
            total += differential.total();
            matches += differential.matches();
            evidence.addAll(differential.evidence());

            WorldLifecycleContractSuite.Result lifecycle = WorldLifecycleContractSuite.run(
                    adapter, new WorldLifecycleContractSuite.LeakCheck() {
                        @Override
                        public boolean isClean() {
                            return adapter.trackedPracticeWorlds() == 0;
                        }
                    });
            total += lifecycle.total();
            matches += lifecycle.matches();
            evidence.addAll(lifecycle.evidence());

            world = adapter.worlds().createPracticeWorld(SEED,
                    com.gregor0410.speedrunpractice.common.adapter.WorldAdapter.PracticeWorldOptions
                            .builder(PracticeDimension.OVERWORLD).build());
            total++;
            if (world.seed() == SEED && ((LiveWorld263) world).level().getSeed() == SEED) {
                matches++;
                evidence.add("world.seed=" + SEED);
            }
            PracticePosition spawn = adapter.worlds().spawnPosition(world);
            total++;
            if (spawn != null) {
                matches++;
                evidence.add("world.spawn=" + spawn.blockX() + "," + spawn.blockY()
                        + "," + spawn.blockZ());
            }

            Optional<StructureAdapter.StructureLocation> village = adapter.structures()
                    .locateNearest(world, StructureAdapter.StructureQuery.builder("village")
                            .radius(5000).build());
            total++;
            if (village.isPresent()) {
                matches++;
                evidence.add("structure.village=" + village.get().position().blockX() + ","
                        + village.get().position().blockZ());
            }

            adapter.portals().createNetherPortal(world, new PracticePosition(0, 70, 0));
            total++;
            matches++;
            evidence.add("portal.created");

            total++;
            if (adapter.registries().itemExists("minecraft:iron_sword")
                    && !adapter.registries().itemExists("minecraft:not_a_real_item")
                    && adapter.registries().maxStackSize("minecraft:ender_pearl") == 16) {
                matches++;
                evidence.add("registry.items");
            }

            GameProfile profile = new GameProfile(UUID.randomUUID(), "verification263");
            ServerPlayer entity = new ServerPlayer(server, ((LiveWorld263) world).level(),
                    profile,
                    ClientInformation.createDefault());
            entity.connection = new ServerGamePacketListenerImpl(server,
                    new Connection(PacketFlow.SERVERBOUND), entity,
                    CommonListenerCookie.createInitial(profile, false));
            entity.snapTo(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
            player = new LivePlayer263(entity);
            adapter.players().setHealth(player, 7.0);
            adapter.players().setFood(player, 9);
            Loadout loadout = new Loadout("verification",
                    java.util.Collections.singletonList(new Loadout.Item("minecraft:iron_sword", 0, 1)));
            adapter.inventories().applyLoadout(player, loadout);
            Loadout captured = adapter.inventories().captureLoadout(player, "verification-capture");
            total++;
            if (adapter.players().getHealth(player) == 7.0
                    && adapter.players().getFood(player) == 9
                    && captured.items().size() == 1) {
                matches++;
                evidence.add("player.inventory");
            }
            adapter.players().resetPlayer(player);
            total++;
            if (adapter.players().getHealth(player) > 19.9
                    && adapter.players().getFood(player) == 20) {
                matches++;
                evidence.add("player.reset");
            }
            total++;
            if (adapter.players().capturePlayerState(player) != null) {
                matches++;
                evidence.add("checkpoint.capture");
            }

            ScenarioContractSuite.Result scenarios =
                    ScenarioContractSuite.run(Runtime263.runtime(), player, SEED);
            total += scenarios.total();
            matches += scenarios.matches();
            evidence.addAll(scenarios.evidence());

            end = adapter.worlds().createPracticeWorld(SEED,
                    com.gregor0410.speedrunpractice.common.adapter.WorldAdapter.PracticeWorldOptions
                            .builder(PracticeDimension.END).build());
            adapter.dragons().resetFight(end);
            total++;
            if (!adapter.dragons().hasLivingDragon(end)) {
                matches++;
                evidence.add("dragon.reset");
            }

            total++;
            if (!adapter.timer().isAvailable()) {
                matches++;
                evidence.add("timer.unavailable=expected");
            }

            adapter.worlds().deletePracticeWorld(end);
            adapter.worlds().deletePracticeWorld(world);
            total++;
            if (adapter.server().getLevel(((LiveWorld263) world).level().dimension()) == null) {
                matches++;
                evidence.add("world.cleanup");
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification all.263 failed: " + failure);
        } finally {
            try {
                if (end != null) {
                    adapter.worlds().deletePracticeWorld(end);
                }
            } catch (Exception ignored) {
                // Server shutdown prunes any leftover practice level.
            }
            try {
                if (world != null) {
                    adapter.worlds().deletePracticeWorld(world);
                }
            } catch (Exception ignored) {
                // Server shutdown prunes any leftover practice level.
            }
        }
        try {
            VerificationSession.complete("suite.all", matches, total,
                    "live adapter checks " + matches + "/" + total + " (" + join(evidence) + ")");
        } catch (Exception failure) {
            SpeedrunLogger.warn("Could not write all.263 verification: " + failure);
        }
    }

    public static void runSuiteNow(final MinecraftServer server, final String suite) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                AllProbe263.runSuite(server, suite);
            }
        });
    }

    public static void runSeedSearchNow(final MinecraftServer server) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                LiveAdapter263 adapter = Runtime263.live();
                try {
                    FixtureDifferentialContractSuite.Result result =
                            FixtureDifferentialContractSuite.run(adapter, "/fixtures/26.3.json");
                    VerificationSession.complete("suite.seed-search", result.matches(), result.total(),
                            "seed differential checks " + result.matches() + "/" + result.total()
                                    + " (" + join(result.evidence()) + ")");
                } catch (Exception failure) {
                    try {
                        VerificationSession.fail("suite.seed-search", failure.getMessage());
                    } catch (Exception reportFailure) {
                        SpeedrunLogger.warn("Could not write seed-search.263 verification: " + reportFailure);
                    }
                }
            }
        });
    }

    private static void runSuite(MinecraftServer server, String suite) {
        if ("worlds".equals(suite)) {
            runWorlds(server);
        } else if ("structures".equals(suite)) {
            runStructures(server);
        } else if ("portals".equals(suite)) {
            runPortals(server);
        } else if ("dragon".equals(suite)) {
            runDragon(server);
        } else if ("registries".equals(suite)) {
            runRegistries(server);
        } else if ("seed-search".equals(suite)) {
            runSeedSearchNow(server);
        } else if ("scenarios".equals(suite)) {
            runScenarios(server);
        } else if ("resets".equals(suite)) {
            runResets(server);
        } else if ("checkpoints".equals(suite)) {
            runCheckpoints(server);
        } else {
            try {
                VerificationSession.fail("suite." + suite, "unknown suite " + suite);
            } catch (Exception failure) {
                SpeedrunLogger.warn("Could not write suite.263 verification: " + failure);
            }
        }
    }

    private static void runWorlds(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        final LiveAdapter263 adapter = Runtime263.live();
        PracticeWorld world = null;
        try {
            total++;
            if (adapter == null || adapter.server() != server) {
                complete("suite.worlds", matches, total, evidence, "world");
                return;
            }
            matches++;
            evidence.add("adapter");

            WorldLifecycleContractSuite.Result lifecycle = WorldLifecycleContractSuite.run(
                    adapter, new WorldLifecycleContractSuite.LeakCheck() {
                        @Override
                        public boolean isClean() {
                            return adapter.trackedPracticeWorlds() == 0;
                        }
                    });
            total += lifecycle.total();
            matches += lifecycle.matches();
            evidence.addAll(lifecycle.evidence());

            world = adapter.worlds().createPracticeWorld(SEED,
                    com.gregor0410.speedrunpractice.common.adapter.WorldAdapter.PracticeWorldOptions
                            .builder(PracticeDimension.OVERWORLD).build());
            total++;
            if (world.seed() == SEED && ((LiveWorld263) world).level().getSeed() == SEED) {
                matches++;
                evidence.add("world.seed=" + SEED);
            }
            PracticePosition spawn = adapter.worlds().spawnPosition(world);
            total++;
            if (spawn != null) {
                matches++;
                evidence.add("world.spawn=" + spawn.blockX() + "," + spawn.blockY()
                        + "," + spawn.blockZ());
            }

            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                    ((LiveWorld263) world).level().dimension();
            adapter.worlds().deletePracticeWorld(world);
            world = null;
            total++;
            if (adapter.server().getLevel(key) == null) {
                matches++;
                evidence.add("world.cleanup");
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification worlds.263 failed: " + failure);
        } finally {
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // Server shutdown prunes any leftover practice level.
                }
            }
        }
        complete("suite.worlds", matches, total, evidence, "world");
    }

    private static void runStructures(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter263 adapter = Runtime263.live();
        PracticeWorld world = null;
        try {
            total++;
            if (adapter == null || adapter.server() != server) {
                complete("suite.structures", matches, total, evidence, "structure");
                return;
            }
            matches++;
            evidence.add("adapter");
            world = adapter.worlds().createPracticeWorld(SEED,
                    com.gregor0410.speedrunpractice.common.adapter.WorldAdapter.PracticeWorldOptions
                            .builder(PracticeDimension.OVERWORLD).build());
            Optional<StructureAdapter.StructureLocation> village = adapter.structures()
                    .locateNearest(world, StructureAdapter.StructureQuery.builder("village")
                            .radius(5000).build());
            total++;
            if (village.isPresent()) {
                matches++;
                evidence.add("structure.village=" + village.get().position().blockX() + ","
                        + village.get().position().blockZ());
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification structures.263 failed: " + failure);
        } finally {
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // Server shutdown prunes any leftover practice level.
                }
            }
        }
        complete("suite.structures", matches, total, evidence, "structure");
    }

    private static void runPortals(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter263 adapter = Runtime263.live();
        PracticeWorld world = null;
        VerificationPlayer263.Handle harness = null;
        try {
            total++;
            if (adapter == null || adapter.server() != server) {
                complete("suite.portals", matches, total, evidence, "portal");
                return;
            }
            matches++;
            evidence.add("adapter");
            world = adapter.worlds().createPracticeWorld(SEED,
                    com.gregor0410.speedrunpractice.common.adapter.WorldAdapter.PracticeWorldOptions
                            .builder(PracticeDimension.OVERWORLD).build());
            PracticePosition spawn = adapter.worlds().spawnPosition(world);
            harness = VerificationPlayer263.create(server, ((LiveWorld263) world).level(),
                    spawn, "verification263");
            adapter.portals().createNetherPortal(world, new PracticePosition(0, 70, 0));
            total++;
            matches++;
            evidence.add("portal.created");
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification portals.263 failed: " + failure);
        } finally {
            if (harness != null) {
                harness.close();
            }
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // Server shutdown prunes any leftover practice level.
                }
            }
        }
        complete("suite.portals", matches, total, evidence, "portal");
    }

    private static void runDragon(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter263 adapter = Runtime263.live();
        PracticeWorld end = null;
        try {
            total++;
            if (adapter == null || adapter.server() != server) {
                complete("suite.dragon", matches, total, evidence, "dragon");
                return;
            }
            matches++;
            evidence.add("adapter");
            end = adapter.worlds().createPracticeWorld(SEED,
                    com.gregor0410.speedrunpractice.common.adapter.WorldAdapter.PracticeWorldOptions
                            .builder(PracticeDimension.END).build());
            adapter.dragons().resetFight(end);
            total++;
            if (!adapter.dragons().hasLivingDragon(end)) {
                matches++;
                evidence.add("dragon.reset");
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification dragon.263 failed: " + failure);
        } finally {
            if (end != null) {
                try {
                    adapter.worlds().deletePracticeWorld(end);
                } catch (Exception ignored) {
                    // Server shutdown prunes any leftover practice level.
                }
            }
        }
        complete("suite.dragon", matches, total, evidence, "dragon");
    }

    private static void runRegistries(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter263 adapter = Runtime263.live();
        try {
            total++;
            if (adapter == null || adapter.server() != server) {
                complete("suite.registries", matches, total, evidence, "registry");
                return;
            }
            matches++;
            evidence.add("adapter");
            total++;
            if (adapter.registries().itemExists("minecraft:iron_sword")
                    && !adapter.registries().itemExists("minecraft:not_a_real_item")
                    && adapter.registries().maxStackSize("minecraft:ender_pearl") == 16) {
                matches++;
                evidence.add("registry.items");
            }
            // Static adapter contracts live here: the SpeedRunIGT bridge is
            // intentionally unavailable on a dedicated server.
            total++;
            if (!adapter.timer().isAvailable()) {
                matches++;
                evidence.add("timer.unavailable=expected");
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification registries.263 failed: " + failure);
        }
        complete("suite.registries", matches, total, evidence, "registry");
    }

    private static void runScenarios(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter263 adapter = Runtime263.live();
        PracticeWorld world = null;
        VerificationPlayer263.Handle harness = null;
        try {
            total++;
            if (adapter == null || adapter.server() != server) {
                complete("suite.scenarios", matches, total, evidence, "scenario");
                return;
            }
            matches++;
            evidence.add("adapter");
            world = adapter.worlds().createPracticeWorld(SEED,
                    com.gregor0410.speedrunpractice.common.adapter.WorldAdapter.PracticeWorldOptions
                            .builder(PracticeDimension.OVERWORLD).build());
            PracticePosition spawn = adapter.worlds().spawnPosition(world);
            harness = VerificationPlayer263.create(server, ((LiveWorld263) world).level(),
                    spawn, "verification263");
            ScenarioContractSuite.Result scenarios =
                    ScenarioContractSuite.run(Runtime263.runtime(), harness.player(), SEED);
            total += scenarios.total();
            matches += scenarios.matches();
            evidence.addAll(scenarios.evidence());
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification scenarios.263 failed: " + failure);
        } finally {
            if (harness != null) {
                harness.close();
            }
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // Server shutdown prunes any leftover practice level.
                }
            }
        }
        complete("suite.scenarios", matches, total, evidence, "scenario");
    }

    private static void runResets(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter263 adapter = Runtime263.live();
        PracticeWorld world = null;
        VerificationPlayer263.Handle harness = null;
        try {
            total++;
            if (adapter == null || adapter.server() != server) {
                complete("suite.resets", matches, total, evidence, "reset");
                return;
            }
            matches++;
            evidence.add("adapter");
            world = adapter.worlds().createPracticeWorld(SEED,
                    com.gregor0410.speedrunpractice.common.adapter.WorldAdapter.PracticeWorldOptions
                            .builder(PracticeDimension.OVERWORLD).build());
            PracticePosition spawn = adapter.worlds().spawnPosition(world);
            harness = VerificationPlayer263.create(server, ((LiveWorld263) world).level(),
                    spawn, "verification263");
            LivePlayer263 player = harness.player();
            adapter.players().setHealth(player, 7.0);
            adapter.players().setFood(player, 9);
            adapter.players().resetPlayer(player);
            total++;
            if (adapter.players().getHealth(player) > 19.9
                    && adapter.players().getFood(player) == 20) {
                matches++;
                evidence.add("player.reset");
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification resets.263 failed: " + failure);
        } finally {
            if (harness != null) {
                harness.close();
            }
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // Server shutdown prunes any leftover practice level.
                }
            }
        }
        complete("suite.resets", matches, total, evidence, "reset");
    }

    private static void runCheckpoints(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter263 adapter = Runtime263.live();
        PracticeWorld world = null;
        VerificationPlayer263.Handle harness = null;
        try {
            total++;
            if (adapter == null || adapter.server() != server) {
                complete("suite.checkpoints", matches, total, evidence, "checkpoint");
                return;
            }
            matches++;
            evidence.add("adapter");
            world = adapter.worlds().createPracticeWorld(SEED,
                    com.gregor0410.speedrunpractice.common.adapter.WorldAdapter.PracticeWorldOptions
                            .builder(PracticeDimension.OVERWORLD).build());
            PracticePosition spawn = adapter.worlds().spawnPosition(world);
            harness = VerificationPlayer263.create(server, ((LiveWorld263) world).level(),
                    spawn, "verification263");
            LivePlayer263 player = harness.player();
            adapter.players().setHealth(player, 7.0);
            adapter.players().setFood(player, 9);
            Loadout loadout = new Loadout("verification",
                    java.util.Collections.singletonList(new Loadout.Item("minecraft:iron_sword", 0, 1)));
            adapter.inventories().applyLoadout(player, loadout);
            Loadout captured = adapter.inventories().captureLoadout(player, "verification-capture");
            total++;
            if (adapter.players().getHealth(player) == 7.0
                    && adapter.players().getFood(player) == 9
                    && captured.items().size() == 1) {
                matches++;
                evidence.add("player.inventory");
            }
            total++;
            if (adapter.players().capturePlayerState(player) != null) {
                matches++;
                evidence.add("checkpoint.capture");
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification checkpoints.263 failed: " + failure);
        } finally {
            if (harness != null) {
                harness.close();
            }
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // Server shutdown prunes any leftover practice level.
                }
            }
        }
        complete("suite.checkpoints", matches, total, evidence, "checkpoint");
    }

    private static void complete(String test, int matches, int total,
                                 List<String> evidence, String label) {
        try {
            VerificationSession.complete(test, matches, total,
                    label + " checks " + matches + "/" + total + " (" + join(evidence) + ")");
        } catch (Exception failure) {
            SpeedrunLogger.warn("Could not write " + test + ".263 verification: " + failure);
        }
    }

    private static String join(List<String> evidence) {
        if (evidence.isEmpty()) {
            return "no evidence";
        }
        StringBuilder out = new StringBuilder();
        for (String item : evidence) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(item);
        }
        return out.toString();
    }
}
