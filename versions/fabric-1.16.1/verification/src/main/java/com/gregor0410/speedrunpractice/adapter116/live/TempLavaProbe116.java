package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedFilters;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.verification.VerificationSession;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TEMPORARY headless probe for the 1.16.1 Stage-B lava port: compares
 * the production {@link SeedAnalyzer} lava verdict against real generated
 * chunks in a practice world per seed. Delete after passing.
 */
public final class TempLavaProbe116 {
    private static final long[] SEEDS = {
        12345L, 20001L, 20002L, 20003L, 20004L, 987654321L,
        // Seed 17 documents the known cross-chunk order-inversion FN
        // (tall-grass plant shifting the lava-lake box up by one).
        17L,
    };
    private static final int RADIUS = 64;
    private static final int TICKET_RADIUS = 6;
    /** FORCED tickets (unmapped yarn name: field_14031). */
    private static final ChunkTicketType<ChunkPos> FORCED = ChunkTicketType.field_14031;
    private static boolean armed;

    private TempLavaProbe116() {
    }

    /** TEMP: registers the one-shot probe. Call sites must be reverted. */
    public static synchronized void arm() {
        if (armed) {
            return;
        }
        armed = true;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    probe(server);
                }
            }, "probe-lava-116");
            worker.setDaemon(true);
            worker.start();
        });
    }

    /** Starts this probe only when the verification command explicitly asks for it. */
    public static void runNow(final MinecraftServer server) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                probe(server);
            }
        }, "verify-lava-116");
        worker.setDaemon(true);
        worker.start();
    }

    private static void probe(MinecraftServer server) {
        LiveAdapter116 live = Runtime116.live();
        for (int i = 0; i < 600 && (live == null || live.server() == null); i++) {
            sleep(500);
            live = Runtime116.live();
        }
        if (live == null || live.server() == null) {
            SpeedrunLogger.warn("PROBELAVA116 ABORT no-live-adapter");
            return;
        }
        final LiveAdapter116 adapter = live;
        SeedAnalyzer seeds = adapter.seeds();
        SpeedrunLogger.warn("PROBELAVA116 START supportsLava=" + seeds.supportsLava());
        StringBuilder dist = new StringBuilder();
        int distTrue = 0;
        int distTotal = 0;
        java.util.List<Long> distTrues = new java.util.ArrayList<Long>();
        for (long scan = 1; scan <= 20; scan++) {
            try {
                boolean verdict = seeds.analyze(scan, SeedQuery.builder()
                        .version(GameVersion.MC_1_16_1).requireLava().build()).matches();
                distTotal++;
                if (verdict) {
                    distTrue++;
                    distTrues.add(scan);
                }
                dist.append(verdict ? 'T' : 'f');
            } catch (RuntimeException failure) {
                dist.append('E');
            }
        }
        SpeedrunLogger.warn("PROBELAVA116 DIST true=" + distTrue + "/" + distTotal + " " + dist
                + " trues=" + distTrues);
        java.util.List<Long> targets = new java.util.ArrayList<Long>();
        for (long seed : SEEDS) {
            targets.add(seed);
        }
        for (Long found : distTrues) {
            if (!targets.contains(found)) {
                targets.add(found);
            }
        }
        int match = 0;
        int total = 0;
        for (long seed : targets) {
            total++;
            boolean stageB;
            Object finding;
            try {
                SeedAnalyzer.SeedAnalysis analysis = seeds.analyze(seed, SeedQuery.builder()
                        .version(GameVersion.MC_1_16_1).requireLava().build());
                stageB = analysis.matches();
                finding = analysis.findings().get(SeedFilters.FIND_LAVA);
            } catch (RuntimeException failure) {
                SpeedrunLogger.warn("PROBELAVA116 SEED=" + seed + " ANALYZE-FAIL " + failure);
                continue;
            }
            String truth = groundTruth(server, adapter, seed);
            boolean surface = truth.matches(".* surf=[1-9][0-9]*.*");
            boolean ok = stageB == surface;
            if (ok) {
                match++;
            }
            SpeedrunLogger.warn("PROBELAVA116 SEED=" + seed + " STAGEB=" + stageB
                    + " FIND_LAVA=" + finding + " TRUTH=" + truth + " SURFACE=" + surface
                    + " MATCH=" + ok);
        }
        SpeedrunLogger.warn("PROBELAVA116 DONE match=" + match + "/" + total);
        try {
            VerificationSession.complete("lava.stage-b", match, total, "real generated chunks");
        } catch (java.io.IOException failure) {
            SpeedrunLogger.warn("Verification report write failed: " + failure.getMessage());
        }
    }

    /** Real generated-chunk lava scan in a practice world for the seed. */
    private static String groundTruth(MinecraftServer server, LiveAdapter116 adapter, long seed) {
        final AtomicReference<PracticeWorld> handle = new AtomicReference<PracticeWorld>();
        final AtomicReference<String> error = new AtomicReference<String>();
        CountDownLatch created = new CountDownLatch(1);
        server.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    handle.set(adapter.worlds().createPracticeWorld(seed,
                            WorldAdapter.PracticeWorldOptions.builder(PracticeDimension.OVERWORLD)
                                    .build()));
                } catch (PracticeException | RuntimeException failure) {
                    error.set(String.valueOf(failure));
                } finally {
                    created.countDown();
                }
            }
        });
        if (!await(created, 120)) {
            return "create-timeout";
        }
        if (handle.get() == null || !(handle.get() instanceof LiveWorld)) {
            return "create-fail:" + error.get();
        }
        ServerWorld world = ((LiveWorld) handle.get()).world();
        BlockPos spawn = SeedAnalyzer116.predictSpawn(
                new VanillaLayeredBiomeSource(seed, false, false), world.getSeaLevel(), seed);
        final BlockPos center = spawn;
        try {
            ChunkPos middle = new ChunkPos(center);
            CountDownLatch tickets = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int dx = -TICKET_RADIUS; dx <= TICKET_RADIUS; dx++) {
                            for (int dz = -TICKET_RADIUS; dz <= TICKET_RADIUS; dz++) {
                                ChunkPos at = new ChunkPos(middle.x + dx, middle.z + dz);
                                world.getChunkManager().addTicket(FORCED, at, 2, at);
                            }
                        }
                    } finally {
                        tickets.countDown();
                    }
                }
            });
            if (!await(tickets, 60)) {
                return "ticket-timeout";
            }
            if (!waitLoaded(server, world, middle)) {
                return "load-timeout";
            }
            final AtomicReference<String> found = new AtomicReference<String>("none");
            CountDownLatch scanned = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        found.set(scan(world, center));
                        // TEMP round-7: dump the real spring neighborhood.
                        if (seed == 17L) {
                            dumpSpring(world);
                        }
                    } catch (RuntimeException failure) {
                        found.set("scan-fail:" + failure);
                    } finally {
                        scanned.countDown();
                    }
                }
            });
            if (!await(scanned, 300)) {
                return "scan-timeout";
            }
            return found.get();
        } finally {
            CountDownLatch removed = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        try {
                            ChunkPos middle = new ChunkPos(center);
                            for (int dx = -TICKET_RADIUS; dx <= TICKET_RADIUS; dx++) {
                                for (int dz = -TICKET_RADIUS; dz <= TICKET_RADIUS; dz++) {
                                    ChunkPos at = new ChunkPos(middle.x + dx, middle.z + dz);
                                    world.getChunkManager().removeTicket(FORCED, at, 2, at);
                                }
                            }
                        } catch (RuntimeException ignored) {
                            // Best effort; the world is deleted below anyway.
                        }
                        adapter.worlds().deletePracticeWorld(handle.get());
                    } catch (PracticeException | RuntimeException failure) {
                        SpeedrunLogger.warn("PROBELAVA116 cleanup failed: " + failure);
                    } finally {
                        removed.countDown();
                    }
                }
            });
            await(removed, 120);
        }
    }

    private static boolean waitLoaded(MinecraftServer server, ServerWorld world, ChunkPos middle) {
        for (int i = 0; i < 240; i++) {
            final AtomicReference<Boolean> ready = new AtomicReference<Boolean>(Boolean.FALSE);
            CountDownLatch polled = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean all = true;
                        for (int dx = -TICKET_RADIUS; dx <= TICKET_RADIUS && all; dx++) {
                            for (int dz = -TICKET_RADIUS; dz <= TICKET_RADIUS && all; dz++) {
                                all = world.getChunk(middle.x + dx, middle.z + dz,
                                        net.minecraft.world.chunk.ChunkStatus.FULL, false) != null;
                            }
                        }
                        ready.set(all ? Boolean.TRUE : Boolean.FALSE);
                    } finally {
                        polled.countDown();
                    }
                }
            });
            if (!await(polled, 60)) {
                return false;
            }
            if (Boolean.TRUE.equals(ready.get())) {
                return true;
            }
            sleep(500);
        }
        return false;
    }

    // TEMP round-8 diagnostics: revert after use. Runs on the server thread.
    // Smart dump: all lava + cave air (lake extent) plus the top solid per
    // column (hill surface the lake gates read).
    private static void dumpSpring(ServerWorld world) {
        for (int x = 34; x <= 54; x++) {
            for (int z = -32; z <= -14; z++) {
                BlockPos topPos = null;
                BlockState topState = null;
                for (int y = 60; y <= 85; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state;
                    try {
                        state = world.getBlockState(pos);
                    } catch (RuntimeException unreadable) {
                        continue;
                    }
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    topPos = pos;
                    topState = state;
                    if (state.getBlock() == Blocks.LAVA
                            || state.getBlock() == Blocks.CAVE_AIR) {
                        SpeedrunLogger.warn("PROBELAVA116-DUMP seed=17 at=" + x + "," + y + ","
                                + z + " state=" + state);
                    }
                }
                if (topPos != null) {
                    SpeedrunLogger.warn("PROBELAVA116-DTOP seed=17 at=" + x + ","
                            + topPos.getY() + "," + z + " state=" + topState);
                }
            }
        }
    }

    /**
     * Lava census within the radius: first (bottom-up) hit plus the
     * highest lava Y and a surface count (lava no deeper than 8 below
     * the real local surface, mirroring the Stage-B gate). Runs on the
     * server thread.
     */
    private static String scan(ServerWorld world, BlockPos spawn) {
        String first = null;
        int highest = Integer.MIN_VALUE;
        int surface = 0;
        StringBuilder detail = new StringBuilder();
        int detailed = 0;
        for (int x = spawn.getX() - RADIUS; x <= spawn.getX() + RADIUS; x++) {
            for (int z = spawn.getZ() - RADIUS; z <= spawn.getZ() + RADIUS; z++) {
                long dx = (long) x - spawn.getX();
                long dz = (long) z - spawn.getZ();
                if (dx * dx + dz * dz > (long) RADIUS * RADIUS) {
                    continue;
                }
                int realSurface;
                try {
                    realSurface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                } catch (RuntimeException unreadable) {
                    continue;
                }
                int floor = realSurface - 8;
                for (int y = 0; y < 256; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state;
                    try {
                        state = world.getBlockState(pos);
                    } catch (RuntimeException unreadable) {
                        continue;
                    }
                    if (isLava(state)) {
                        if (first == null) {
                            first = "lava@" + x + "," + y + "," + z;
                        }
                        if (y > highest) {
                            highest = y;
                        }
                        if (y >= floor) {
                            surface++;
                        }
                        // TEMP diagnostics: high-lava positions + real surfaces.
                        if (y >= 40 && detailed < 8) {
                            detailed++;
                            detail.append(" [").append(x).append(',').append(y).append(',')
                                    .append(z).append("/s").append(realSurface).append(']');
                        }
                    }
                }
            }
        }
        if (first == null) {
            return "none";
        }
        return first + " top=" + highest + " surf=" + surface + detail;
    }

    private static boolean isLava(BlockState state) {
        if (state == null) {
            return false;
        }
        if (state.getBlock() == net.minecraft.block.Blocks.LAVA) {
            return true;
        }
        FluidState fluid = state.getFluidState();
        return fluid != null && !fluid.isEmpty()
                && (fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA);
    }

    private static boolean await(CountDownLatch latch, int seconds) {
        try {
            return latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
