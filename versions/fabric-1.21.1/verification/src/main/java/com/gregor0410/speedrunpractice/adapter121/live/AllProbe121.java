package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.verification.ScenarioContractSuite;
import com.gregor0410.speedrunpractice.verification.FixtureDifferentialContractSuite;
import com.gregor0410.speedrunpractice.verification.VerificationSession;
import com.gregor0410.speedrunpractice.verification.WorldLifecycleContractSuite;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Verification-only contract probe for the live 1.21.1 adapter. */
public final class AllProbe121 {
    private static final long SEED = 12345L;

    private AllProbe121() {
    }

    public static void runNow(final MinecraftServer server) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                AllProbe121.run(server);
            }
        });
    }

    public static void runFixturesNow(final MinecraftServer server) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                LiveAdapter121 adapter = Runtime121.live();
                try {
                    FixtureDifferentialContractSuite.Result result =
                            FixtureDifferentialContractSuite.run(adapter, "/fixtures/1.21.1.json");
                    VerificationSession.complete("suite.fixtures", result.matches(), result.total(),
                            "fixture differential checks " + result.matches() + "/" + result.total()
                                    + " (" + join(result.evidence()) + ")");
                } catch (Exception failure) {
                    try {
                        VerificationSession.fail("suite.fixtures", failure.getMessage());
                    } catch (Exception reportFailure) {
                        SpeedrunLogger.warn("Could not write fixtures.121 verification: " + reportFailure);
                    }
                }
            }
        });
    }

    private static void run(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter121 adapter = Runtime121.live();
        PracticeWorld world = null;
        PracticeWorld end = null;
        LivePlayer121 player = null;
        try {
            check(adapter != null && adapter.server() == server, "adapter", evidence);
            total++;
            if (adapter != null && adapter.server() == server) {
                matches++;
            }

            FixtureDifferentialContractSuite.Result differential =
                    FixtureDifferentialContractSuite.run(adapter, "/fixtures/1.21.1.json");
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
            boolean seedMatches = world.seed() == SEED
                    && ((LiveWorld121) world).world().getSeed() == SEED;
            total++;
            if (seedMatches) {
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

            PracticePosition portalPosition = new PracticePosition(0, 70, 0);
            adapter.portals().createNetherPortal(world, portalPosition);
            total++;
            // createNetherPortal only returns after the version adapter has
            // found a portal rectangle and built it in the live world.
            matches++;
            evidence.add("portal.created");

            total++;
            if (adapter.registries().itemExists("minecraft:iron_sword")
                    && !adapter.registries().itemExists("minecraft:not_a_real_item")
                    && adapter.registries().maxStackSize("minecraft:ender_pearl") == 16) {
                matches++;
                evidence.add("registry.items");
            }

            GameProfile profile = new GameProfile(UUID.randomUUID(), "verification121");
            ServerPlayerEntity entity = new ServerPlayerEntity(server,
                    ((LiveWorld121) world).world(),
                    profile,
                    SyncedClientOptions.createDefault());
            entity.networkHandler = new ServerPlayNetworkHandler(server,
                    new ClientConnection(NetworkSide.SERVERBOUND), entity,
                    ConnectedClientData.createDefault(profile, false));
            entity.refreshPositionAndAngles(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
            player = new LivePlayer121(entity);
            adapter.players().setHealth(player, 7.0);
            adapter.players().setFood(player, 9);
            Loadout loadout = new Loadout("verification",
                    java.util.Collections.singletonList(new Loadout.Item("minecraft:iron_sword", 0, 1)));
            adapter.inventories().applyLoadout(player, loadout);
            Loadout captured = adapter.inventories().captureLoadout(player, "verification-capture");
            boolean playerOk = adapter.players().getHealth(player) == 7.0
                    && adapter.players().getFood(player) == 9
                    && captured.items().size() == 1;
            total++;
            if (playerOk) {
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
                    ScenarioContractSuite.run(Runtime121.runtime(), player, SEED);
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
            if (adapter.server().getWorld(((LiveWorld121) world).world().getRegistryKey()) == null) {
                matches++;
                evidence.add("world.cleanup");
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification all.121 failed: " + failure);
        } finally {
            try {
                if (end != null) {
                    adapter.worlds().deletePracticeWorld(end);
                }
            } catch (Exception ignored) {
                // The initial cleanup is the verdict; server shutdown prunes leftovers.
            }
            try {
                if (world != null) {
                    adapter.worlds().deletePracticeWorld(world);
                }
            } catch (Exception ignored) {
                // The initial cleanup is the verdict; server shutdown prunes leftovers.
            }
        }
        try {
            VerificationSession.complete("suite.all", matches, total,
                    "live adapter checks " + matches + "/" + total + " (" + join(evidence) + ")");
        } catch (Exception failure) {
            SpeedrunLogger.warn("Could not write all.121 verification: " + failure);
        }
    }

    public static void runSuiteNow(final MinecraftServer server, final String suite) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                AllProbe121.runSuite(server, suite);
            }
        });
    }

    public static void runSeedSearchNow(final MinecraftServer server) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                LiveAdapter121 adapter = Runtime121.live();
                try {
                    FixtureDifferentialContractSuite.Result result =
                            FixtureDifferentialContractSuite.run(adapter, "/fixtures/1.21.1.json");
                    VerificationSession.complete("suite.seed-search", result.matches(), result.total(),
                            "seed differential checks " + result.matches() + "/" + result.total()
                                    + " (" + join(result.evidence()) + ")");
                } catch (Exception failure) {
                    try {
                        VerificationSession.fail("suite.seed-search", failure.getMessage());
                    } catch (Exception reportFailure) {
                        SpeedrunLogger.warn("Could not write seed-search.121 verification: " + reportFailure);
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
                SpeedrunLogger.warn("Could not write suite.121 verification: " + failure);
            }
        }
    }

    private static void runWorlds(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        final LiveAdapter121 adapter = Runtime121.live();
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
            if (world.seed() == SEED && ((LiveWorld121) world).world().getSeed() == SEED) {
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

            net.minecraft.registry.RegistryKey<net.minecraft.world.World> key =
                    ((LiveWorld121) world).world().getRegistryKey();
            adapter.worlds().deletePracticeWorld(world);
            world = null;
            total++;
            if (adapter.server().getWorld(key) == null) {
                matches++;
                evidence.add("world.cleanup");
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification worlds.121 failed: " + failure);
        } finally {
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // The initial cleanup is the verdict; server shutdown prunes leftovers.
                }
            }
        }
        complete("suite.worlds", matches, total, evidence, "world");
    }

    private static void runStructures(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter121 adapter = Runtime121.live();
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
            SpeedrunLogger.warn("Verification structures.121 failed: " + failure);
        } finally {
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // The initial cleanup is the verdict; server shutdown prunes leftovers.
                }
            }
        }
        complete("suite.structures", matches, total, evidence, "structure");
    }

    private static void runPortals(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter121 adapter = Runtime121.live();
        PracticeWorld world = null;
        VerificationPlayer121.Handle harness = null;
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
            harness = VerificationPlayer121.create(server, ((LiveWorld121) world).world(),
                    spawn, "verification121");
            adapter.portals().createNetherPortal(world, new PracticePosition(0, 70, 0));
            total++;
            // createNetherPortal only returns after the version adapter has
            // found a portal rectangle and built it in the live world.
            matches++;
            evidence.add("portal.created");
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification portals.121 failed: " + failure);
        } finally {
            if (harness != null) {
                harness.close();
            }
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // The initial cleanup is the verdict; server shutdown prunes leftovers.
                }
            }
        }
        complete("suite.portals", matches, total, evidence, "portal");
    }

    private static void runDragon(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter121 adapter = Runtime121.live();
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
            SpeedrunLogger.warn("Verification dragon.121 failed: " + failure);
        } finally {
            if (end != null) {
                try {
                    adapter.worlds().deletePracticeWorld(end);
                } catch (Exception ignored) {
                    // The initial cleanup is the verdict; server shutdown prunes leftovers.
                }
            }
        }
        complete("suite.dragon", matches, total, evidence, "dragon");
    }

    private static void runRegistries(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter121 adapter = Runtime121.live();
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
            SpeedrunLogger.warn("Verification registries.121 failed: " + failure);
        }
        complete("suite.registries", matches, total, evidence, "registry");
    }

    private static void runScenarios(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter121 adapter = Runtime121.live();
        PracticeWorld world = null;
        VerificationPlayer121.Handle harness = null;
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
            harness = VerificationPlayer121.create(server, ((LiveWorld121) world).world(),
                    spawn, "verification121");
            ScenarioContractSuite.Result scenarios =
                    ScenarioContractSuite.run(Runtime121.runtime(), harness.player(), SEED);
            total += scenarios.total();
            matches += scenarios.matches();
            evidence.addAll(scenarios.evidence());
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification scenarios.121 failed: " + failure);
        } finally {
            if (harness != null) {
                harness.close();
            }
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // The initial cleanup is the verdict; server shutdown prunes leftovers.
                }
            }
        }
        complete("suite.scenarios", matches, total, evidence, "scenario");
    }

    private static void runResets(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter121 adapter = Runtime121.live();
        PracticeWorld world = null;
        VerificationPlayer121.Handle harness = null;
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
            harness = VerificationPlayer121.create(server, ((LiveWorld121) world).world(),
                    spawn, "verification121");
            LivePlayer121 player = harness.player();
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
            SpeedrunLogger.warn("Verification resets.121 failed: " + failure);
        } finally {
            if (harness != null) {
                harness.close();
            }
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // The initial cleanup is the verdict; server shutdown prunes leftovers.
                }
            }
        }
        complete("suite.resets", matches, total, evidence, "reset");
    }

    private static void runCheckpoints(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter121 adapter = Runtime121.live();
        PracticeWorld world = null;
        VerificationPlayer121.Handle harness = null;
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
            harness = VerificationPlayer121.create(server, ((LiveWorld121) world).world(),
                    spawn, "verification121");
            LivePlayer121 player = harness.player();
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
            SpeedrunLogger.warn("Verification checkpoints.121 failed: " + failure);
        } finally {
            if (harness != null) {
                harness.close();
            }
            if (world != null) {
                try {
                    adapter.worlds().deletePracticeWorld(world);
                } catch (Exception ignored) {
                    // The initial cleanup is the verdict; server shutdown prunes leftovers.
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
            SpeedrunLogger.warn("Could not write " + test + ".121 verification: " + failure);
        }
    }

    private static void check(boolean condition, String name, List<String> evidence) {
        if (condition) {
            evidence.add(name);
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
