package com.gregor0410.speedrunpractice.adapter121.mixin;

import com.gregor0410.speedrunpractice.adapter121.IPracticeServer121;
import com.gregor0410.speedrunpractice.adapter121.world.PracticeWorld121;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.mojang.serialization.Lifecycle;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.spawner.SpecialSpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Runtime practice worlds on 1.21.1 (mirrors the 1.16.1 server mixin on yarn
 * mappings, following the verified 26.3 shape). Each practice dimension is a
 * {@link PracticeWorld121} registered in the server's world map under a
 * {@code speedrun_practice} key, reusing the live dimension's type and
 * worldgen settings but seeded with the practice seed, so requested seed and
 * actual worldgen seed always match.
 *
 * <p>Level data is an isolated {@link LevelProperties} per practice world
 * (spawn included, structures always on), so practice play never writes into
 * the host save's level data. Only one linked set plus one end world exist at
 * a time; creating a new set prunes the previous one, and shutdown removes
 * every practice world.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin121 implements IPracticeServer121 {
    @Shadow
    @Final
    private Executor workerExecutor;

    @Shadow
    @Final
    protected LevelStorage.Session session;

    @Shadow
    @Final
    private WorldGenerationProgressListenerFactory worldGenerationProgressListenerFactory;

    @Shadow
    @Final
    private Map<RegistryKey<World>, ServerWorld> worlds;

    private final List<Map<PracticeDimension, PracticeWorld121>> linkedPracticeWorlds =
            new ArrayList<Map<PracticeDimension, PracticeWorld121>>();
    private final List<PracticeWorld121> endPracticeWorlds = new ArrayList<PracticeWorld121>();

    @Invoker("setupSpawn")
    private static void invokeSetupSpawn(ServerWorld world, ServerWorldProperties properties,
            boolean bonusChest, boolean debugWorld) {
        throw new AssertionError("mixin invoker");
    }

    @Override
    public Map<PracticeDimension, PracticeWorld121> createLinkedPracticeWorlds(long seed)
            throws IOException {
        String tag = seed + "_" + UUID.randomUUID();
        RegistryKey<World> overworldKey = practiceKey(tag + "_0");
        RegistryKey<World> netherKey = practiceKey(tag + "_1");
        RegistryKey<World> endKey = practiceKey(tag + "_2");

        PracticeWorld121 overworld = createPracticeWorld(seed, overworldKey,
                PracticeDimension.OVERWORLD);
        BlockPos overworldSpawn = spawnOf(overworld);
        PracticeWorld121 nether = createPracticeWorld(seed, netherKey, PracticeDimension.NETHER);
        setSpawn(nether, overworldSpawn);
        PracticeWorld121 end = createPracticeWorld(seed, endKey, PracticeDimension.END);
        setSpawn(end, overworldSpawn);
        PracticeWorld121.buildObsidianPlatform(end);

        Map<PracticeDimension, PracticeWorld121> triple =
                new EnumMap<PracticeDimension, PracticeWorld121>(PracticeDimension.class);
        triple.put(PracticeDimension.OVERWORLD, overworld);
        triple.put(PracticeDimension.NETHER, nether);
        triple.put(PracticeDimension.END, end);
        Map<RegistryKey<World>, ServerWorld> siblings =
                new HashMap<RegistryKey<World>, ServerWorld>();
        siblings.put(World.OVERWORLD, overworld);
        siblings.put(World.NETHER, nether);
        siblings.put(World.END, end);
        for (PracticeWorld121 member : triple.values()) {
            worlds.put(member.getRegistryKey(), member);
            member.link(siblings);
        }
        overworld.getChunkManager().addTicket(ChunkTicketType.START,
                new ChunkPos(overworldSpawn), 11, Unit.INSTANCE);

        linkedPracticeWorlds.add(triple);
        while (linkedPracticeWorlds.size() > 1) {
            removeLinkedPracticeWorlds(linkedPracticeWorlds.remove(0));
        }
        SpeedrunLogger.info("Created 1.21.1 practice worlds for seed " + seed);
        return Collections.unmodifiableMap(triple);
    }

    @Override
    public PracticeWorld121 createEndPracticeWorld(long seed) throws IOException {
        MinecraftServer server = (MinecraftServer) (Object) this;
        RegistryKey<World> endKey = practiceKey(seed + "_" + UUID.randomUUID() + "_3");
        PracticeWorld121 end = createPracticeWorld(seed, endKey, PracticeDimension.END);
        // Single end worlds stand alone: the stored spawn points at the main
        // overworld (vanilla end behavior), but the practice spawn stays on
        // the obsidian platform.
        ServerWorld mainOverworld = server.getOverworld();
        if (mainOverworld != null) {
            setSpawn(end, spawnOf(mainOverworld));
        }
        PracticeWorld121.buildObsidianPlatform(end);
        setInitialized(end);
        worlds.put(endKey, end);
        endPracticeWorlds.add(end);
        while (endPracticeWorlds.size() > 1) {
            removePracticeWorld(endPracticeWorlds.remove(0));
        }
        BlockPos platform = ServerWorld.END_SPAWN_POS;
        SpeedrunLogger.info("Created 1.21.1 end practice world for seed " + seed
                + " (platform at " + platform.getX() + "," + platform.getY() + ","
                + platform.getZ() + ")");
        return end;
    }

    @Override
    public void deletePracticeWorld(ServerWorld world) {
        if (!(world instanceof PracticeWorld121)) {
            return;
        }
        if (!worlds.containsKey(world.getRegistryKey())) {
            return;
        }
        try {
            removePracticeWorld((PracticeWorld121) world);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not delete practice world "
                    + world.getRegistryKey().getValue() + ": " + failure.getMessage(), failure);
        }
    }

    @Override
    public void pruneLeftoverPracticeWorlds() {
        // The dimensions root has no direct accessor; derive it from the
        // nether folder (<root>/dimensions/minecraft/the_nether) and refuse
        // to touch anything outside a directory literally named "dimensions".
        // Derive from the save root, not from the nether folder: on 1.21.1
        // getWorldDirectory(NETHER) answers the legacy DIM-1 path, which
        // makes the 26.3-style derivation point at the wrong directory.
        Path root = session.getDirectory(WorldSavePath.ROOT);
        Path dimensions = root == null ? null
                : root.resolve("dimensions").resolve("speedrun_practice");
        if (dimensions == null || dimensions.getParent() == null
                || !"dimensions".equals(dimensions.getParent().getFileName().toString())) {
            return;
        }
        if (!Files.isDirectory(dimensions)) {
            return;
        }
        Set<String> live = new HashSet<String>();
        for (RegistryKey<World> key : worlds.keySet()) {
            if (isPracticeKey(key)) {
                live.add(key.getValue().getPath());
            }
        }
        try (Stream<Path> entries = Files.list(dimensions)) {
            java.util.Iterator<Path> iterator = entries.iterator();
            while (iterator.hasNext()) {
                Path entry = iterator.next();
                if (live.contains(entry.getFileName().toString())) {
                    continue;
                }
                deleteDirectory(entry);
                SpeedrunLogger.info("Pruned leftover practice folder " + entry.getFileName());
            }
        } catch (IOException failure) {
            SpeedrunLogger.warn(
                    "Could not prune leftover practice folders: " + failure.getMessage());
        }
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void removePracticeWorldsOnShutdown(CallbackInfo info) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        ServerWorld mainOverworld = server.getOverworld();
        BlockPos lobby = mainOverworld == null ? new BlockPos(0, 64, 0)
                : spawnOf(mainOverworld);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Reset spawn points out of practice worlds so the stored
            // respawn dimension never dangles after the folders are gone.
            if (isPracticeKey(player.getSpawnPointDimension())) {
                player.setSpawnPoint(World.OVERWORLD, lobby, 0.0f, false, false);
            }
        }
        for (Map<PracticeDimension, PracticeWorld121> triple : linkedPracticeWorlds) {
            try {
                removeLinkedPracticeWorlds(triple);
            } catch (IOException | RuntimeException failure) {
                SpeedrunLogger.warn("Could not remove practice worlds on shutdown: "
                        + failure.getMessage());
            }
        }
        linkedPracticeWorlds.clear();
        for (PracticeWorld121 end : endPracticeWorlds) {
            try {
                removePracticeWorld(end);
            } catch (IOException | RuntimeException failure) {
                SpeedrunLogger.warn("Could not remove end practice world on shutdown: "
                        + failure.getMessage());
            }
        }
        endPracticeWorlds.clear();
    }

    private PracticeWorld121 createPracticeWorld(long seed, RegistryKey<World> key,
            PracticeDimension dimension) throws IOException {
        MinecraftServer server = (MinecraftServer) (Object) this;
        RegistryKey<World> vanillaKey = vanillaKey(dimension);
        ServerWorld live = server.getWorld(vanillaKey);
        if (live == null) {
            throw new IOException("Cannot build a practice " + dimension
                    + " world: the live " + vanillaKey.getValue() + " is not loaded.");
        }
        // The chunk generator carries no seed of its own (worldgen reads the
        // level seed), so a fresh copy of the live generator plus the
        // practice-seeded level reproduces the live world's terrain for the
        // practice seed exactly.
        ChunkGenerator liveGenerator = live.getChunkManager().getChunkGenerator();
        ChunkGenerator generator = liveGenerator;
        if (liveGenerator instanceof NoiseChunkGenerator) {
            NoiseChunkGenerator noise = (NoiseChunkGenerator) liveGenerator;
            generator = new NoiseChunkGenerator(liveGenerator.getBiomeSource(),
                    noise.getSettings());
        }
        DimensionOptions options = new DimensionOptions(live.getDimensionEntry(), generator);
        LevelProperties properties = new LevelProperties(
                server.getSaveProperties().getLevelInfo(),
                new GeneratorOptions(seed, true, false),
                LevelProperties.SpecialProperty.NONE, Lifecycle.stable());
        PracticeWorld121 world = PracticeWorld121.create(server, workerExecutor, session,
                properties, key, options, worldGenerationProgressListenerFactory.create(11),
                false, BiomeAccess.hashSeed(seed),
                Collections.<SpecialSpawner>emptyList(), true,
                new RandomSequencesState(seed), seed, dimension);
        if (dimension == PracticeDimension.OVERWORLD) {
            invokeSetupSpawn(world, properties, false, false);
        }
        // Practice worlds are disposable: never persist them (autosave
        // skips them, deletion drops the folder), so closing one releases
        // resources without flushing hundreds of chunks to disk first.
        world.savingDisabled = true;
        properties.setInitialized(true);
        return world;
    }

    private static RegistryKey<World> practiceKey(String path) {
        return RegistryKey.of(RegistryKeys.WORLD,
                Identifier.of("speedrun_practice", path));
    }

    private static RegistryKey<World> vanillaKey(PracticeDimension dimension) {
        switch (dimension) {
            case NETHER:
                return World.NETHER;
            case END:
                return World.END;
            case OVERWORLD:
            default:
                return World.OVERWORLD;
        }
    }

    private static boolean isPracticeKey(RegistryKey<World> key) {
        return key != null && "speedrun_practice".equals(key.getValue().getNamespace());
    }

    private static BlockPos spawnOf(ServerWorld world) {
        if (world.getLevelProperties() instanceof LevelProperties) {
            return ((LevelProperties) world.getLevelProperties()).getSpawnPos();
        }
        return new BlockPos(0, 64, 0);
    }

    private static void setSpawn(PracticeWorld121 world, BlockPos spawn) {
        ((LevelProperties) world.getLevelProperties()).setSpawnPos(spawn, 90.0f);
        setInitialized(world);
    }

    private static void setInitialized(PracticeWorld121 world) {
        ((LevelProperties) world.getLevelProperties()).setInitialized(true);
    }

    private void removeLinkedPracticeWorlds(
            Map<PracticeDimension, PracticeWorld121> triple) throws IOException {
        List<Map.Entry<PracticeDimension, PracticeWorld121>> members =
                new ArrayList<Map.Entry<PracticeDimension, PracticeWorld121>>(triple.entrySet());
        Collections.sort(members,
                new Comparator<Map.Entry<PracticeDimension, PracticeWorld121>>() {
                    @Override
                    public int compare(Map.Entry<PracticeDimension, PracticeWorld121> left,
                            Map.Entry<PracticeDimension, PracticeWorld121> right) {
                        return left.getKey().compareTo(right.getKey());
                    }
                });
        for (Map.Entry<PracticeDimension, PracticeWorld121> member : members) {
            removePracticeWorld(member.getValue());
        }
    }

    private void removePracticeWorld(PracticeWorld121 world) throws IOException {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (!worlds.containsKey(world.getRegistryKey())) {
            // Already removed (deleted mid-session, reaped again on
            // shutdown): closing a second time wedges the server thread
            // inside ServerWorld.close(), so there is nothing left to do.
            return;
        }
        ServerWorld mainOverworld = server.getOverworld();
        BlockPos lobby = mainOverworld == null ? new BlockPos(0, 64, 0)
                : spawnOf(mainOverworld);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getServerWorld() == world) {
                player.teleport(mainOverworld, lobby.getX() + 0.5, lobby.getY(),
                        lobby.getZ() + 0.5, 90.0f, 0.0f);
            }
        }
        EnderDragonFight fight = world.getEnderDragonFight();
        if (fight != null) {
            try {
                ((EnderDragonFightAccess121) (Object) fight).getBossBar().clearPlayers();
            } catch (RuntimeException missing) {
                // Fight bar cleanup is best-effort; the world still unloads.
            }
        }
        worlds.remove(world.getRegistryKey(), world);
        // No save and no close (legacy 1.16 parity): the folder is deleted
        // right below, savingDisabled kept autosave away so no region file
        // was ever opened, and close() would synchronously flush hundreds
        // of chunks (~50s) just to delete them. The chunk manager uses the
        // shared worker executor, so there is nothing per-world to shut down.
        deleteDirectory(session.getWorldDirectory(world.getRegistryKey()));
    }

    private static void deleteDirectory(Path root) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            if (root == null || !Files.exists(root)) {
                return;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                List<Path> entries = new ArrayList<Path>();
                java.util.Iterator<Path> iterator = walk.sorted(Comparator.reverseOrder()).iterator();
                while (iterator.hasNext()) {
                    entries.add(iterator.next());
                }
                for (Path path : entries) {
                    Files.deleteIfExists(path);
                }
                return;
            } catch (IOException failure) {
                last = failure;
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
        throw last;
    }
}
