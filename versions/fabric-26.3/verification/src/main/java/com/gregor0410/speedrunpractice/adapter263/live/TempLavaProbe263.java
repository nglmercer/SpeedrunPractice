package com.gregor0410.speedrunpractice.adapter263.live;

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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TEMPORARY headless probe for the gated Stage-B lava verifier:
 * compares the production {@link SeedAnalyzer} lava verdict against real
 * generated chunks in a practice world per fixture seed. Delete after
 * passing.
 */
public final class TempLavaProbe263 {
    private static final long[] SEEDS = {
        12345L, 987654321L, 555555555L, -123456789L, 20260918L,
        // Positive Stage-B control retained alongside the five canonical
        // fixture rows.
        63L,
    };
    private static final int RADIUS = 64;
    private static final int TICKET_RADIUS = 6;
    private static boolean armed;

    private TempLavaProbe263() {
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
            }, "probe-lava-263");
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
        }, "verify-lava-263");
        worker.setDaemon(true);
        worker.start();
    }

    private static void probe(MinecraftServer server) {
        LiveAdapter263 live = Runtime263.live();
        for (int i = 0; i < 600 && (live == null || live.server() == null); i++) {
            sleep(500);
            live = Runtime263.live();
        }
        if (live == null || live.server() == null) {
            SpeedrunLogger.warn("PROBELAVA263 ABORT no-live-adapter");
            return;
        }
        final LiveAdapter263 adapter = live;
        SeedAnalyzer seeds = adapter.seeds();
        SpeedrunLogger.warn("PROBELAVA263 START supportsLava=" + seeds.supportsLava());
        java.util.List<Long> targets = new java.util.ArrayList<Long>();
        for (long seed : SEEDS) {
            targets.add(seed);
        }
        int match = 0;
        int total = 0;
        for (long seed : targets) {
            total++;
            boolean stageB;
            Object finding;
            try {
                SeedAnalyzer.SeedAnalysis analysis = seeds.analyze(seed, SeedQuery.builder()
                        .version(GameVersion.V_26_3).requireLava().build());
                stageB = analysis.matches();
                finding = analysis.findings().get(SeedFilters.FIND_LAVA);
            } catch (RuntimeException failure) {
                SpeedrunLogger.warn("PROBELAVA263 SEED=" + seed + " ANALYZE-FAIL " + failure);
                continue;
            }
            String truth = groundTruth(server, adapter, seed);
            boolean surface = truth.matches(".* surf=[1-9][0-9]*.*");
            boolean ok = stageB == surface;
            if (ok) {
                match++;
            }
            SpeedrunLogger.warn("PROBELAVA263 SEED=" + seed + " STAGEB=" + stageB
                    + " FIND_LAVA=" + finding + " TRUTH=" + truth + " SURFACE=" + surface
                    + " MATCH=" + ok);
        }
        SpeedrunLogger.warn("PROBELAVA263 DONE match=" + match + "/" + total);
        try {
            VerificationSession.complete("lava.stage-b", match, total, "real generated chunks");
        } catch (java.io.IOException failure) {
            SpeedrunLogger.warn("Verification report write failed: " + failure.getMessage());
        }
    }

    /** Real generated-chunk lava scan in a practice world for the seed. */
    private static String groundTruth(MinecraftServer server, LiveAdapter263 adapter, long seed) {
        BlockPos spawn;
        try {
            spawn = ((SeedAnalyzer263) adapter.seeds()).spawnCenter(seed);
        } catch (RuntimeException failure) {
            return "spawn-fail:" + failure;
        }
        if (spawn == null) {
            return "spawn-null";
        }
        final BlockPos center = spawn;
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
        if (handle.get() == null || !(handle.get() instanceof LiveWorld263)) {
            return "create-fail:" + error.get();
        }
        ServerLevel level = ((LiveWorld263) handle.get()).level();
        try {
            ChunkPos middle = ChunkPos.containing(center);
            CountDownLatch tickets = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int dx = -TICKET_RADIUS; dx <= TICKET_RADIUS; dx++) {
                            for (int dz = -TICKET_RADIUS; dz <= TICKET_RADIUS; dz++) {
                                level.getChunkSource().addTicketWithRadius(TicketType.FORCED,
                                        new ChunkPos(middle.x() + dx, middle.z() + dz), 2);
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
            if (!waitLoaded(server, level, middle)) {
                return "load-timeout";
            }
            final AtomicReference<String> found = new AtomicReference<String>("none");
            CountDownLatch scanned = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        found.set(scan(level, center, seed));
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
                            int middleX = ChunkPos.containing(center).x();
                            int middleZ = ChunkPos.containing(center).z();
                            for (int dx = -TICKET_RADIUS; dx <= TICKET_RADIUS; dx++) {
                                for (int dz = -TICKET_RADIUS; dz <= TICKET_RADIUS; dz++) {
                                    level.getChunkSource().removeTicketWithRadius(TicketType.FORCED,
                                            new ChunkPos(middleX + dx, middleZ + dz), 2);
                                }
                            }
                        } catch (RuntimeException ignored) {
                            // Best effort; the level is deleted below anyway.
                        }
                        adapter.worlds().deletePracticeWorld(handle.get());
                    } catch (PracticeException | RuntimeException failure) {
                        SpeedrunLogger.warn("PROBELAVA263 cleanup failed: " + failure);
                    } finally {
                        removed.countDown();
                    }
                }
            });
            await(removed, 120);
        }
    }

    private static boolean waitLoaded(MinecraftServer server, ServerLevel level, ChunkPos middle) {
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
                                all = level.getChunk(middle.x() + dx, middle.z() + dz,
                                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                        false) != null;
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

    /**
     * Lava census within the radius: first (bottom-up) hit plus the
     * highest lava Y and a surface count (lava no deeper than 8 below
     * the real local surface, mirroring the Stage-B gate). Runs on the
     * server thread.
     */
    private static String scan(ServerLevel level, BlockPos spawn, long seed) {
        int bottom = level.getMinY();
        int top = level.getMinY() + level.getHeight() - 1;
        // TEMP round-5: wide fluid-only dump around the seed-42 phantom.
        if (seed == 42L) {
            for (int x = -64; x <= -24; x++) {
                for (int y = 55; y <= 75; y++) {
                    for (int z = -64; z <= -24; z++) {
                        BlockPos at = new BlockPos(x, y, z);
                        BlockState state;
                        try {
                            state = level.getBlockState(at);
                        } catch (RuntimeException unreadable) {
                            continue;
                        }
                        if (state == null || (!state.is(Blocks.WATER)
                                && !state.is(Blocks.LAVA) && !state.is(Blocks.CAVE_AIR))) {
                            continue;
                        }
                        SpeedrunLogger.warn("PROBELAVA263-DUMP seed=42 at=" + x + "," + y + ","
                                + z + " state=" + state);
                    }
                }
            }
        }
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
                    realSurface = level.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types
                                    .MOTION_BLOCKING_NO_LEAVES,
                            x, z);
                } catch (RuntimeException unreadable) {
                    continue;
                }
                int floor = realSurface - 8;
                for (int y = bottom; y <= top; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state;
                    try {
                        state = level.getBlockState(pos);
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
        if (state.is(Blocks.LAVA)) {
            return true;
        }
        FluidState fluid = state.getFluidState();
        return fluid != null && !fluid.isEmpty()
                && (fluid.getType() == Fluids.LAVA || fluid.getType() == Fluids.FLOWING_LAVA);
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
