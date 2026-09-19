package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TEMPORARY surgical probe: dumps real generated blocks around the
 * Stage-B lava sites of the 1.16.1 false positives (20004, 10, 19) to
 * distinguish carvers from water-lake adjacency from surface cover.
 * Delete after passing.
 */
public final class TempBasinProbe116 {
    private static final long[] SEEDS = {20004L, 10L, 19L};
    private static final BlockPos[] SITES = {
        new BlockPos(117, 63, -9),
        new BlockPos(-204, 59, 129),
        new BlockPos(184, 87, 206),
    };
    private static boolean armed;

    private TempBasinProbe116() {
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
            }, "probe-basin-116");
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void probe(MinecraftServer server) {
        LiveAdapter116 live = Runtime116.live();
        for (int i = 0; i < 600 && (live == null || live.server() == null); i++) {
            sleep(500);
            live = Runtime116.live();
        }
        if (live == null || live.server() == null) {
            SpeedrunLogger.warn("PROBEBASIN116 ABORT no-live-adapter");
            return;
        }
        final LiveAdapter116 adapter = live;
        for (int s = 0; s < SEEDS.length; s++) {
            dump(server, adapter, SEEDS[s], SITES[s]);
        }
        SpeedrunLogger.warn("PROBEBASIN116 DONE");
    }

    private static void dump(MinecraftServer server, LiveAdapter116 adapter, long seed,
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
                    SpeedrunLogger.warn("PROBEBASIN116 SEED=" + seed + " create-fail " + failure);
                } finally {
                    created.countDown();
                }
            }
        });
        if (!await(created, 120) || handle.get() == null
                || !(handle.get() instanceof LiveWorld)) {
            return;
        }
        ServerWorld world = ((LiveWorld) handle.get()).world();
        try {
            ChunkPos chunk = new ChunkPos(site);
            CountDownLatch tickets = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                ChunkPos at = new ChunkPos(chunk.x + dx, chunk.z + dz);
                                world.getChunkManager().addTicket(
                                        TempLavaProbe116Forced.get(), at, 2, at);
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
            CountDownLatch dumped = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        StringBuilder out = new StringBuilder();
                        out.append("PROBEBASIN116 SEED=").append(seed).append(" site=").append(site);
                        try {
                            int surf = world.getTopY(
                                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                    site.getX(), site.getZ());
                            out.append(" realSurf=").append(surf);
                        } catch (RuntimeException unreadable) {
                            out.append(" realSurf=?");
                        }
                        try {
                            out.append(" biome=").append(world.getBiome(site));
                        } catch (RuntimeException unreadable) {
                            out.append(" biome=?");
                        }
                        // Spring-read neighborhood: origin/up/down/4-neighbors.
                        int[][] offsets = {{0, 0, 0}, {0, 1, 0}, {0, -1, 0}, {-1, 0, 0}, {1, 0, 0},
                            {0, 0, -1}, {0, 0, 1}};
                        for (int[] o : offsets) {
                            BlockPos at = site.add(o[0], o[1], o[2]);
                            try {
                                BlockState state = world.getBlockState(at);
                                out.append(" [").append(o[0]).append(',').append(o[1]).append(',')
                                        .append(o[2]).append('=')
                                        .append(state.getBlock().getTranslationKey()).append(']');
                            } catch (RuntimeException unreadable) {
                                out.append(" [").append(o[0]).append(',').append(o[1]).append(',')
                                        .append(o[2]).append("=?]");
                            }
                        }
                        // Liquid scan in a box around the site (water neighbors?).
                        int water = 0;
                        int lava = 0;
                        int air = 0;
                        for (int x = site.getX() - 8; x <= site.getX() + 8; x++) {
                            for (int z = site.getZ() - 8; z <= site.getZ() + 8; z++) {
                                for (int y = site.getY() - 4; y <= site.getY() + 8; y++) {
                                    BlockState state;
                                    try {
                                        state = world.getBlockState(new BlockPos(x, y, z));
                                    } catch (RuntimeException unreadable) {
                                        continue;
                                    }
                                    if (!state.getFluidState().isEmpty()) {
                                        if (state.getFluidState().getFluid()
                                                == net.minecraft.fluid.Fluids.WATER
                                                || state.getFluidState().getFluid()
                                                == net.minecraft.fluid.Fluids.FLOWING_WATER) {
                                            water++;
                                        } else {
                                            lava++;
                                        }
                                    } else if (state.isAir()) {
                                        air++;
                                    }
                                }
                            }
                        }
                        out.append(" boxWater=").append(water).append(" boxLava=").append(lava)
                                .append(" boxAir=").append(air);
                        SpeedrunLogger.warn(out.toString());
                    } finally {
                        dumped.countDown();
                    }
                }
            });
            await(dumped, 120);
        } finally {
            CountDownLatch removed = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        adapter.worlds().deletePracticeWorld(handle.get());
                    } catch (PracticeException | RuntimeException failure) {
                        SpeedrunLogger.warn("PROBEBASIN116 cleanup failed: " + failure);
                    } finally {
                        removed.countDown();
                    }
                }
            });
            await(removed, 120);
        }
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

    /** TEMP accessor for the probe's FORCED ticket type. */
    private static final class TempLavaProbe116Forced {
        static net.minecraft.server.world.ChunkTicketType<ChunkPos> get() {
            return ChunkTicketTypeHolder.FORCED;
        }

        private static final class ChunkTicketTypeHolder {
            static final net.minecraft.server.world.ChunkTicketType<ChunkPos> FORCED =
                    net.minecraft.server.world.ChunkTicketType.field_14031;
        }
    }
}
