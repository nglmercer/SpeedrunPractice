package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TEMPORARY surgical probe: dumps real generated blocks around the
 * 1.21.1 new-FP site (seed 70) and the persistent-FN lava pool (seed
 * 987654321) to distinguish ores from rooted dirt from structure
 * adaptation from out-of-margin flow sources. Delete after passing.
 */
public final class TempSurfProbe121 {
    private static final long[] SEEDS = {70L, 987654321L};
    private static final BlockPos[] SITES = {
        new BlockPos(63, 59, -14),
        new BlockPos(69, 64, 1),
    };
    private static boolean armed;

    private TempSurfProbe121() {
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
            }, "probe-surf-121");
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void probe(MinecraftServer server) {
        LiveAdapter121 live = Runtime121.live();
        for (int i = 0; i < 600 && (live == null || live.server() == null); i++) {
            sleep(500);
            live = Runtime121.live();
        }
        if (live == null || live.server() == null) {
            SpeedrunLogger.warn("PROBESURF121 ABORT no-live-adapter");
            return;
        }
        final LiveAdapter121 adapter = live;
        for (int s = 0; s < SEEDS.length; s++) {
            dump(server, adapter, SEEDS[s], SITES[s]);
        }
        SpeedrunLogger.warn("PROBESURF121 DONE");
    }

    private static void dump(MinecraftServer server, LiveAdapter121 adapter, long seed,
            BlockPos site) {
        final AtomicReference<PracticeWorld> handle = new AtomicReference<PracticeWorld>();
        CountDownLatch created = new CountDownLatch(1);
        server.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    handle.set(adapter.worlds().createPracticeWorld(seed,
                            WorldAdapter.PracticeWorldOptions.builder(PracticeDimension.OVERWORLD)
                                    .build()));
                } catch (PracticeException | RuntimeException failure) {
                    SpeedrunLogger.warn("PROBESURF121 SEED=" + seed + " create-fail " + failure);
                } finally {
                    created.countDown();
                }
            }
        });
        if (!await(created, 120) || handle.get() == null
                || !(handle.get() instanceof LiveWorld121)) {
            return;
        }
        ServerWorld world = ((LiveWorld121) handle.get()).world();
        try {
            final ChunkPos chunk = new ChunkPos(site);
            CountDownLatch tickets = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                ChunkPos at = new ChunkPos(chunk.x + dx, chunk.z + dz);
                                world.getChunkManager().addTicket(
                                        ChunkTicketType.FORCED, at, 2, at);
                            }
                        }
                    } finally {
                        tickets.countDown();
                    }
                }
            });
            if (!await(tickets, 60)) {
                return;
            }
            for (int i = 0; i < 120; i++) {
                final AtomicReference<Boolean> ready = new AtomicReference<Boolean>(Boolean.FALSE);
                CountDownLatch polled = new CountDownLatch(1);
                server.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ready.set(world.getChunk(chunk.x, chunk.z,
                                            net.minecraft.world.chunk.ChunkStatus.FULL, false) != null
                                    ? Boolean.TRUE : Boolean.FALSE);
                        } finally {
                            polled.countDown();
                        }
                    }
                });
                if (!await(polled, 60) || Boolean.TRUE.equals(ready.get())) {
                    break;
                }
                sleep(500);
            }
            final BlockPos at = site;
            final long forSeed = seed;
            CountDownLatch dumped = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        StringBuilder out = new StringBuilder();
                        out.append("PROBESURF121 SEED=").append(forSeed).append(" site=").append(at);
                        try {
                            int surf = world.getTopY(
                                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                    at.getX(), at.getZ());
                            out.append(" realSurf=").append(surf);
                        } catch (RuntimeException unreadable) {
                            out.append(" realSurf=?");
                        }
                        try {
                            out.append(" biome=").append(world.getBiome(at).getKey()
                                    .map(Object::toString).orElse("?"));
                        } catch (RuntimeException unreadable) {
                            out.append(" biome=?");
                        }
                        // Spring-read neighborhood: origin/up/down/4-neighbors.
                        int[][] offsets = {{0, 0, 0}, {0, 1, 0}, {0, -1, 0}, {-1, 0, 0}, {1, 0, 0},
                            {0, 0, -1}, {0, 0, 1}};
                        for (int[] o : offsets) {
                            BlockPos pos = at.add(o[0], o[1], o[2]);
                            try {
                                BlockState state = world.getBlockState(pos);
                                out.append(" [").append(o[0]).append(',').append(o[1]).append(',')
                                        .append(o[2]).append('=')
                                        .append(state.getBlock().getTranslationKey());
                                if (!state.getFluidState().isEmpty()) {
                                    out.append('+').append(state.getFluidState().getFluid() == Fluids.WATER
                                            || state.getFluidState().getFluid() == Fluids.FLOWING_WATER
                                            ? "water" : "lava");
                                }
                                out.append(']');
                            } catch (RuntimeException unreadable) {
                                out.append(" [").append(o[0]).append(',').append(o[1]).append(',')
                                        .append(o[2]).append("=?]");
                            }
                        }
                        // Vertical column through the site (cover layers?).
                        out.append(" col=");
                        for (int y = at.getY() - 8; y <= at.getY() + 8; y++) {
                            try {
                                BlockState state = world.getBlockState(
                                        new BlockPos(at.getX(), y, at.getZ()));
                                out.append(shortName(state)).append(',');
                            } catch (RuntimeException unreadable) {
                                out.append("?,");
                            }
                        }
                        // Wide lava census (flow source hunting).
                        StringBuilder lavaAt = new StringBuilder();
                        int lavaCount = 0;
                        int listed = 0;
                        for (int x = at.getX() - 24; x <= at.getX() + 24; x++) {
                            for (int z = at.getZ() - 24; z <= at.getZ() + 24; z++) {
                                for (int y = at.getY() - 24; y <= at.getY() + 24; y++) {
                                    BlockState state;
                                    try {
                                        state = world.getBlockState(new BlockPos(x, y, z));
                                    } catch (RuntimeException unreadable) {
                                        continue;
                                    }
                                    if (isLava(state)) {
                                        lavaCount++;
                                        if (listed < 16) {
                                            listed++;
                                            lavaAt.append(" [").append(x).append(',').append(y)
                                                    .append(',').append(z).append(']');
                                        }
                                    }
                                }
                            }
                        }
                        out.append(" wideLava=").append(lavaCount).append(lavaAt);
                        // Structure proximity via production locate (adaptation?).
                        String[] ids = {"village", "pillager_outpost", "mansion", "trail_ruins"};
                        for (String id : ids) {
                            try {
                                java.util.Optional<com.gregor0410.speedrunpractice.common.adapter
                                        .StructureAdapter.StructureLocation> found = adapter
                                        .structures().locateNearest(handle.get(),
                                                com.gregor0410.speedrunpractice.common.adapter
                                                        .StructureAdapter.StructureQuery.builder(id)
                                                        .center(new com.gregor0410.speedrunpractice
                                                                .common.api.PracticePosition(
                                                                        at.getX(), at.getY(),
                                                                        at.getZ()))
                                                        .radius(500).build());
                                if (found == null || !found.isPresent()
                                        || found.get().position() == null) {
                                    out.append(' ').append(id).append("=none");
                                } else {
                                    double dx = found.get().position().x() - at.getX();
                                    double dz = found.get().position().z() - at.getZ();
                                    out.append(' ').append(id).append('@')
                                            .append((long) found.get().position().x()).append(',')
                                            .append((long) found.get().position().z()).append('d')
                                            .append((long) Math.sqrt(dx * dx + dz * dz));
                                }
                            } catch (PracticeException | RuntimeException unreadable) {
                                out.append(' ').append(id).append("=?")
                                        .append(unreadable.getMessage());
                            }
                        }
                        SpeedrunLogger.warn(out.toString());
                    } finally {
                        dumped.countDown();
                    }
                }
            });
            await(dumped, 180);
        } finally {
            CountDownLatch removed = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        adapter.worlds().deletePracticeWorld(handle.get());
                    } catch (PracticeException | RuntimeException failure) {
                        SpeedrunLogger.warn("PROBESURF121 cleanup failed: " + failure);
                    } finally {
                        removed.countDown();
                    }
                }
            });
            await(removed, 120);
        }
    }

    private static String shortName(BlockState state) {
        String key = state.getBlock().getTranslationKey();
        int dot = key.lastIndexOf('.');
        String name = dot >= 0 ? key.substring(dot + 1) : key;
        if (!state.getFluidState().isEmpty()) {
            name = name + "+" + (state.getFluidState().getFluid() == Fluids.WATER
                    || state.getFluidState().getFluid() == Fluids.FLOWING_WATER ? "W" : "L");
        }
        return name;
    }

    private static boolean isLava(BlockState state) {
        if (state == null) {
            return false;
        }
        if (state.getBlock() == net.minecraft.block.Blocks.LAVA) {
            return true;
        }
        return state.getFluidState() != null && !state.getFluidState().isEmpty()
                && (state.getFluidState().getFluid() == Fluids.LAVA
                        || state.getFluidState().getFluid() == Fluids.FLOWING_LAVA);
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
