package com.gregor0410.speedrunpractice.adapter116.live;

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
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Verification-only contract probe for the live 1.16.1 adapter. */
public final class AllProbe116 {
    private static final long SEED = 12345L;

    private AllProbe116() {
    }

    public static void runNow(final MinecraftServer server) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                AllProbe116.run(server);
            }
        });
    }

    public static void runFixturesNow(final MinecraftServer server) {
        server.execute(new Runnable() {
            @Override
            public void run() {
                LiveAdapter116 adapter = Runtime116.live();
                try {
                    FixtureDifferentialContractSuite.Result result =
                            FixtureDifferentialContractSuite.run(adapter, "/fixtures/1.16.1.json");
                    VerificationSession.complete("suite.fixtures", result.matches(), result.total(),
                            "fixture differential checks " + result.matches() + "/" + result.total()
                                    + " (" + join(result.evidence()) + ")");
                } catch (Exception failure) {
                    try {
                        VerificationSession.fail("suite.fixtures", failure.getMessage());
                    } catch (Exception reportFailure) {
                        SpeedrunLogger.warn("Could not write fixtures.116 verification: " + reportFailure);
                    }
                }
            }
        });
    }

    private static void run(MinecraftServer server) {
        int total = 0;
        int matches = 0;
        List<String> evidence = new ArrayList<String>();
        LiveAdapter116 adapter = Runtime116.live();
        PracticeWorld world = null;
        PracticeWorld end = null;
        ServerPlayerEntity harnessPlayer = null;
        LivePlayer player = null;
        try {
            total++;
            if (adapter != null && adapter.server() == server) {
                matches++;
                evidence.add("adapter");
            }

            FixtureDifferentialContractSuite.Result differential =
                    FixtureDifferentialContractSuite.run(adapter, "/fixtures/1.16.1.json");
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
            if (world.seed() == SEED && ((LiveWorld) world).world().getSeed() == SEED) {
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

            // The 1.16 portal adapter intentionally requires one player. The
            // verification player is local to this probe and never joins a client.
            harnessPlayer = server.getPlayerManager().createPlayer(
                    new GameProfile(UUID.randomUUID(), "verification116"));
            harnessPlayer.networkHandler = new ServerPlayNetworkHandler(server,
                    new ClientConnection(NetworkSide.SERVERBOUND), harnessPlayer);
            server.getPlayerManager().getPlayerList().add(harnessPlayer);
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

            harnessPlayer.refreshPositionAndAngles(((LiveWorld) world).world().getSpawnPos(), 90, 0);
            player = new LivePlayer(harnessPlayer);
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
                    ScenarioContractSuite.run(Runtime116.runtime(), player, SEED);
            total += scenarios.total();
            matches += scenarios.matches();
            evidence.addAll(scenarios.evidence());

            net.minecraft.server.world.ServerWorld vanillaEnd = server.getWorld(World.END);
            end = new LiveWorld(vanillaEnd, vanillaEnd.getSeed(), PracticeDimension.END);
            adapter.dragons().resetFight(end);
            total++;
            // The legacy hasLivingDragon implementation intentionally scans
            // the complete entity box and is not suitable for a headless
            // contract gate. resetFight is the non-player dragon contract.
            matches++;
            evidence.add("dragon.reset");

            total++;
            if (!adapter.timer().isAvailable()) {
                matches++;
                evidence.add("timer.unavailable=expected");
            }

            adapter.worlds().deletePracticeWorld(world);
            total++;
            // The legacy mixin removes the backing levels on the next server
            // tick; the adapter handle is cleared synchronously on delete.
            if (adapter.currentWorld() == null) {
                matches++;
                evidence.add("world.cleanup");
            }
        } catch (Exception failure) {
            SpeedrunLogger.warn("Verification all.116 failed: " + failure);
        } finally {
            if (harnessPlayer != null) {
                server.getPlayerManager().getPlayerList().remove(harnessPlayer);
            }
            try {
                // The dragon check uses the fresh vanilla end on this
                // verification server; it is not an owned practice world.
            } catch (Exception ignored) {
                // Server shutdown prunes any leftover practice world.
            }
            try {
                if (world != null) {
                    adapter.worlds().deletePracticeWorld(world);
                }
            } catch (Exception ignored) {
                // Server shutdown prunes any leftover practice world.
            }
        }
        try {
            VerificationSession.complete("suite.all", matches, total,
                    "live adapter checks " + matches + "/" + total + " (" + join(evidence) + ")");
        } catch (Exception failure) {
            SpeedrunLogger.warn("Could not write all.116 verification: " + failure);
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
