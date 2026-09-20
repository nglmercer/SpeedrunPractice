package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TEMPORARY surgical probe: dumps real generated blocks around the
 * Stage-B lava sites of the 26.3 false positives (21, 43) plus the true
 * positive control (63) to distinguish surface-cover from aquifer water
 * from carvers. Delete after passing.
 */
public final class TempSurfProbe263 {
    private static final long[] SEEDS = {39L};
    private static final BlockPos[] SITES = {
        new BlockPos(47, 66, -24),
    };
    private static boolean armed;

    private TempSurfProbe263() {
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
            }, "probe-surf-263");
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void probe(MinecraftServer server) {
        LiveAdapter263 live = Runtime263.live();
        for (int i = 0; i < 600 && (live == null || live.server() == null); i++) {
            sleep(500);
            live = Runtime263.live();
        }
        if (live == null || live.server() == null) {
            SpeedrunLogger.warn("PROBESURF263 ABORT no-live-adapter");
            return;
        }
        final LiveAdapter263 adapter = live;
        for (int s = 0; s < SEEDS.length; s++) {
            dump(server, adapter, SEEDS[s], SITES[s]);
        }
        SpeedrunLogger.warn("PROBESURF263 DONE");
    }

    private static void dump(MinecraftServer server, LiveAdapter263 adapter, long seed,
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
                    SpeedrunLogger.warn("PROBESURF263 SEED=" + seed + " create-fail " + failure);
                } finally {
                    created.countDown();
                }
            }
        });
        if (!await(created, 120) || handle.get() == null
                || !(handle.get() instanceof LiveWorld263)) {
            return;
        }
        ServerLevel level = ((LiveWorld263) handle.get()).level();
        try {
            final ChunkPos chunk = ChunkPos.containing(site);
            CountDownLatch tickets = new CountDownLatch(1);
            server.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                level.getChunkSource().addTicketWithRadius(TicketType.FORCED,
                                        new ChunkPos(chunk.x() + dx, chunk.z() + dz), 2);
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
                            ready.set(level.getChunk(chunk.x(), chunk.z(),
                                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                            false) != null ? Boolean.TRUE : Boolean.FALSE);
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
                        out.append("PROBESURF263 SEED=").append(forSeed).append(" site=").append(at);
                        try {
                            int surf = level.getHeight(
                                    net.minecraft.world.level.levelgen.Heightmap.Types
                                            .MOTION_BLOCKING_NO_LEAVES,
                                    at.getX(), at.getZ());
                            out.append(" realSurf=").append(surf);
                        } catch (RuntimeException unreadable) {
                            out.append(" realSurf=?");
                        }
                        try {
                            out.append(" biome=").append(level.getBiome(at).unwrapKey()
                                    .map(Object::toString).orElse("?"));
                        } catch (RuntimeException unreadable) {
                            out.append(" biome=?");
                        }
                        // Spring-read neighborhood: origin/up/down/4-neighbors.
                        int[][] offsets = {{0, 0, 0}, {0, 1, 0}, {0, -1, 0}, {-1, 0, 0}, {1, 0, 0},
                            {0, 0, -1}, {0, 0, 1}};
                        for (int[] o : offsets) {
                            BlockPos pos = at.offset(o[0], o[1], o[2]);
                            try {
                                BlockState state = level.getBlockState(pos);
                                out.append(" [").append(o[0]).append(',').append(o[1]).append(',')
                                        .append(o[2]).append('=')
                                        .append(state.getBlock().getDescriptionId());
                                if (!state.getFluidState().isEmpty()) {
                                    out.append('+').append(net.minecraft.core.registries
                                            .BuiltInRegistries.FLUID.getKey(
                                                    state.getFluidState().getType()));
                                }
                                out.append(']');
                            } catch (RuntimeException unreadable) {
                                out.append(" [").append(o[0]).append(',').append(o[1]).append(',')
                                        .append(o[2]).append("=?]");
                            }
                        }
                        // Liquid scan in a box around the site (aquifer water?).
                        int water = 0;
                        int lava = 0;
                        int air = 0;
                        for (int x = at.getX() - 8; x <= at.getX() + 8; x++) {
                            for (int z = at.getZ() - 8; z <= at.getZ() + 8; z++) {
                                for (int y = at.getY() - 4; y <= at.getY() + 8; y++) {
                                    BlockState state;
                                    try {
                                        state = level.getBlockState(new BlockPos(x, y, z));
                                    } catch (RuntimeException unreadable) {
                                        continue;
                                    }
                                    if (!state.getFluidState().isEmpty()) {
                                        if (state.getFluidState().getType() == Fluids.WATER
                                                || state.getFluidState().getType()
                                                == Fluids.FLOWING_WATER) {
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
                        // Vertical column through the site (tree trunk?).
                        out.append(" col=");
                        for (int y = at.getY() - 8; y <= at.getY() + 24; y++) {
                            try {
                                BlockState state = level.getBlockState(
                                        new BlockPos(at.getX(), y, at.getZ()));
                                String key = state.getBlock().getDescriptionId();
                                int dot = key.lastIndexOf('.');
                                out.append(dot >= 0 ? key.substring(dot + 1) : key).append(',');
                            } catch (RuntimeException unreadable) {
                                out.append("?,");
                            }
                        }
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
                        SpeedrunLogger.warn("PROBESURF263 cleanup failed: " + failure);
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
}
