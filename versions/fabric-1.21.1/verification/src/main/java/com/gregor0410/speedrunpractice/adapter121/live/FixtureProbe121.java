package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.util.SimpleJson;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.gregor0410.speedrunpractice.verification.VerificationSession;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verification-only collector for the canonical 1.21.1 fixture rows.
 *
 * <p>Every value is read from a freshly created, real practice world. The
 * resulting candidate is written outside the source tree and must be copied
 * into {@code verification/fixtures/1.21.1.json} only after inspection.</p>
 */
public final class FixtureProbe121 {
    private static final long[] SEEDS = {
        12345L, 20001L, 20002L, 20003L, 20004L,
    };
    private static final int RADIUS = 64;
    private static final int TICKET_RADIUS = 6;
    private static final String[] STRUCTURES = {
        "village", "stronghold", "fortress", "bastion_remnant",
    };

    private FixtureProbe121() {
    }

    /** Starts collection only when explicitly requested by verification. */
    public static void runNow(final MinecraftServer server) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                collect(server);
            }
        }, "verify-fixtures-121");
        worker.setDaemon(true);
        worker.start();
    }

    private static void collect(MinecraftServer server) {
        LiveAdapter121 adapter = Runtime121.live();
        if (adapter == null || adapter.server() == null) {
            fail("no live 1.21.1 adapter");
            return;
        }
        List<Object> rows = new ArrayList<Object>();
        try {
            for (long seed : SEEDS) {
                rows.add(collectSeed(server, adapter, seed));
            }
            writeCandidate(rows);
            VerificationSession.complete("fixtures.121", rows.size(), SEEDS.length,
                    "real practice worlds and structure metadata");
        } catch (Exception failure) {
            SpeedrunLogger.warn("Fixture collection failed: " + failure);
            fail(failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
        }
    }

    private static Map<String, Object> collectSeed(MinecraftServer server,
            LiveAdapter121 adapter, long seed) throws Exception {
        final AtomicReference<PracticeWorld> created = new AtomicReference<PracticeWorld>();
        callServer(server, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                created.set(adapter.worlds().createPracticeWorld(seed,
                        WorldAdapter.PracticeWorldOptions.builder(PracticeDimension.OVERWORLD)
                                .build()));
                return null;
            }
        });
        if (!(created.get() instanceof LiveWorld121)) {
            throw new IOException("seed " + seed + " did not create a live overworld");
        }
        LiveWorld121 handle = (LiveWorld121) created.get();
        ServerWorld world = handle.world();
        final AtomicReference<BlockPos> spawnReference = new AtomicReference<BlockPos>();
        try {
            Map<String, Object> row = callServer(server, new Callable<Map<String, Object>>() {
                @Override
                public Map<String, Object> call() throws Exception {
                    PracticePosition spawn = adapter.worlds().spawnPosition(handle);
                    BlockPos spawnBlock = new BlockPos(spawn.blockX(), spawn.blockY(), spawn.blockZ());
                    spawnReference.set(spawnBlock);
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("seed", Long.valueOf(seed));
                    row.put("verified", Boolean.TRUE);
                    row.put("spawn", position(spawnBlock));
                    row.put("spawnBiome", biomeId(world, spawnBlock));
                    Map<String, Object> structures = new LinkedHashMap<String, Object>();
                    for (String id : STRUCTURES) {
                        structures.put(id, structure(adapter, handle, id));
                    }
                    row.put("structures", structures);
                    return row;
                }
            });
            BlockPos spawn = spawnReference.get();
            if (spawn == null) {
                throw new IOException("seed " + seed + " did not expose a spawn position");
            }
            ChunkPos center = new ChunkPos(spawn);
            ensureLoaded(server, world, center);
            final boolean lava = callServer(server, new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return Boolean.valueOf(hasSurfaceLava(world, spawn));
                }
            }).booleanValue();
            row.put("lavaAvailable", Boolean.valueOf(lava));
            return row;
        } finally {
            final AtomicReference<String> cleanupFailure = new AtomicReference<String>();
            callServer(server, new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        BlockPos spawn = spawnReference.get();
                        if (spawn != null) {
                            removeTickets(world, new ChunkPos(spawn));
                        }
                        adapter.worlds().deletePracticeWorld(handle);
                    } catch (PracticeException | RuntimeException failure) {
                        cleanupFailure.set(String.valueOf(failure));
                    }
                    return null;
                }
            });
            if (cleanupFailure.get() != null) {
                throw new IOException("seed " + seed + " cleanup failed: " + cleanupFailure.get());
            }
        }
    }

    private static Map<String, Object> structure(LiveAdapter121 adapter, LiveWorld121 world,
            String id) throws PracticeException {
        Optional<StructureAdapter.StructureLocation> located = adapter.structures().locateNearest(
                world, StructureAdapter.StructureQuery.builder(id).radius(10000).build());
        if (!located.isPresent()) {
            throw new PracticeException("fixture structure missing: " + id,
                    "The dedicated-server fixture collector could not locate " + id + ".");
        }
        StructureAdapter.StructureLocation value = located.get();
        PracticePosition at = value.position();
        Map<String, Object> out = position(at.blockX(), at.blockY(), at.blockZ());
        String bastionType = value.metadata().get(StructureAdapter.StructureLocation.BASTION_TYPE_KEY);
        if (bastionType != null) {
            out.put("bastionType", bastionType);
        }
        String portalRoom = value.metadata().get(StructureAdapter.StructureLocation.PORTAL_ROOM_KEY);
        if (portalRoom != null) {
            out.put("portalRoom", portalRoom);
        }
        return out;
    }

    private static String biomeId(ServerWorld world, BlockPos pos) throws IOException {
        RegistryEntry<?> biome = world.getBiome(pos);
        if (biome == null || !biome.getKey().isPresent()) {
            throw new IOException("spawn biome has no registry key at " + pos);
        }
        return biome.getKey().get().getValue().toString();
    }

    private static boolean hasSurfaceLava(ServerWorld world, BlockPos spawn) {
        int bottom = world.getBottomY();
        int top = world.getTopY() - 1;
        for (int x = spawn.getX() - RADIUS; x <= spawn.getX() + RADIUS; x++) {
            for (int z = spawn.getZ() - RADIUS; z <= spawn.getZ() + RADIUS; z++) {
                long dx = (long) x - spawn.getX();
                long dz = (long) z - spawn.getZ();
                if (dx * dx + dz * dz > (long) RADIUS * RADIUS) {
                    continue;
                }
                int surface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                int floor = Math.max(bottom, surface - 8);
                for (int y = floor; y <= top; y++) {
                    if (isLava(world.getBlockState(new BlockPos(x, y, z)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isLava(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.isOf(Blocks.LAVA)) {
            return true;
        }
        FluidState fluid = state.getFluidState();
        return fluid != null && !fluid.isEmpty()
                && (fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA);
    }

    private static void ensureLoaded(MinecraftServer server, ServerWorld world, ChunkPos center)
            throws IOException {
        callServer(server, new Callable<Void>() {
            @Override
            public Void call() {
                for (int dx = -TICKET_RADIUS; dx <= TICKET_RADIUS; dx++) {
                    for (int dz = -TICKET_RADIUS; dz <= TICKET_RADIUS; dz++) {
                        ChunkPos at = new ChunkPos(center.x + dx, center.z + dz);
                        world.getChunkManager().addTicket(ChunkTicketType.FORCED, at, 2, at);
                    }
                }
                return null;
            }
        });
        for (int i = 0; i < 240; i++) {
            Boolean loaded = callServer(server, new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    for (int dx = -TICKET_RADIUS; dx <= TICKET_RADIUS; dx++) {
                        for (int dz = -TICKET_RADIUS; dz <= TICKET_RADIUS; dz++) {
                            ChunkPos at = new ChunkPos(center.x + dx, center.z + dz);
                            if (world.getChunkManager().getChunk(at.x, at.z,
                                    net.minecraft.world.chunk.ChunkStatus.FULL, false) == null) {
                                return Boolean.FALSE;
                            }
                        }
                    }
                    return Boolean.TRUE;
                }
            });
            if (Boolean.TRUE.equals(loaded)) {
                return;
            }
            sleep(500);
        }
        throw new IOException("timed out loading fixture chunks around " + center);
    }

    private static void removeTickets(ServerWorld world, ChunkPos center) {
        for (int dx = -TICKET_RADIUS; dx <= TICKET_RADIUS; dx++) {
            for (int dz = -TICKET_RADIUS; dz <= TICKET_RADIUS; dz++) {
                ChunkPos at = new ChunkPos(center.x + dx, center.z + dz);
                world.getChunkManager().removeTicket(ChunkTicketType.FORCED, at, 2, at);
            }
        }
    }

    private static Map<String, Object> position(BlockPos pos) {
        return position(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Map<String, Object> position(int x, int y, int z) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("x", Long.valueOf(x));
        out.put("y", Long.valueOf(y));
        out.put("z", Long.valueOf(z));
        return out;
    }

    private static void writeCandidate(List<Object> rows) throws IOException {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("version", "1.21.1");
        root.put("verifiedOn", LocalDate.now().toString());
        root.put("method", "Fresh 1.21.1 dedicated-server run: generated practice worlds, live structure lookup, and surface lava scan.");
        root.put("seeds", rows);
        Path output = outputDirectory().resolve("fixture-candidate.json");
        Files.createDirectories(output.getParent());
        Files.write(output, SimpleJson.toJson(root, true).getBytes(StandardCharsets.UTF_8));
        SpeedrunLogger.info("Wrote 1.21.1 fixture candidate to " + output);
    }

    private static Path outputDirectory() {
        String configured = System.getProperty("speedrun.practice.verification.output");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("SPEEDRUN_PRACTICE_VERIFICATION_OUTPUT");
        }
        if (configured == null || configured.trim().isEmpty()) {
            configured = "build/verification";
        }
        return Paths.get(configured).resolve("1.21.1");
    }

    private static <T> T callServer(MinecraftServer server, Callable<T> task) throws IOException {
        final AtomicReference<T> result = new AtomicReference<T>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        CountDownLatch done = new CountDownLatch(1);
        server.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(task.call());
                } catch (Throwable caught) {
                    failure.set(caught);
                } finally {
                    done.countDown();
                }
            }
        });
        try {
            if (!done.await(300, TimeUnit.SECONDS)) {
                throw new IOException("server task timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("server task interrupted", interrupted);
        }
        if (failure.get() != null) {
            Throwable caught = failure.get();
            if (caught instanceof IOException) {
                throw (IOException) caught;
            }
            if (caught instanceof PracticeException) {
                throw new IOException(caught.getMessage(), caught);
            }
            if (caught instanceof RuntimeException) {
                throw (RuntimeException) caught;
            }
            throw new IOException(caught.getMessage(), caught);
        }
        return result.get();
    }

    private static void fail(String detail) {
        try {
            VerificationSession.fail("fixtures.121", detail);
        } catch (IOException reportFailure) {
            SpeedrunLogger.warn("Fixture report write failed: " + reportFailure.getMessage());
        }
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
