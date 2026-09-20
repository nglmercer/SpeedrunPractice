package com.gregor0410.speedrunpractice.adapter263.mixin;

import com.gregor0410.speedrunpractice.adapter263.IPracticeServer263;
import com.gregor0410.speedrunpractice.adapter263.world.PracticeLevel263;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
 * Runtime practice worlds on 26.3 (mirrors the 1.16.1 server mixin on modern
 * mappings). Each practice dimension is a {@link PracticeLevel263} registered
 * in the server's level map under a {@code speedrun_practice} key, reusing
 * the live dimension's type and worldgen settings but seeded with the
 * practice seed, so requested seed and actual worldgen seed always match.
 *
 * <p>Level data is an isolated {@link PrimaryLevelData} per practice level
 * (spawn included), so practice play never writes into the host save's
 * level data. Only one linked set plus one end world exist at a time;
 * creating a new set prunes the previous one, and shutdown removes every
 * practice level.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin263 implements IPracticeServer263 {
    @Shadow
    @Final
    private Executor executor;

    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;

    @Shadow
    @Final
    protected WorldData worldData;

    @Shadow
    @Final
    private Map<ResourceKey<Level>, ServerLevel> levels;

    private final List<Map<PracticeDimension, PracticeLevel263>> linkedPracticeLevels =
            new ArrayList<Map<PracticeDimension, PracticeLevel263>>();
    private final List<PracticeLevel263> endPracticeLevels = new ArrayList<PracticeLevel263>();

    @Override
    public Map<PracticeDimension, PracticeLevel263> createLinkedPracticeWorlds(long seed)
            throws IOException {
        MinecraftServer server = (MinecraftServer) (Object) this;
        String tag = seed + "_" + UUID.randomUUID();
        ResourceKey<Level> overworldKey = practiceKey(tag + "_0");
        ResourceKey<Level> netherKey = practiceKey(tag + "_1");
        ResourceKey<Level> endKey = practiceKey(tag + "_2");

        PracticeLevel263 overworld = createPracticeLevel(seed, overworldKey, PracticeDimension.OVERWORLD);
        BlockPos overworldSpawn = computeSpawn(overworld);
        setSpawn(overworld, overworldKey, overworldSpawn);
        PracticeLevel263 nether = createPracticeLevel(seed, netherKey, PracticeDimension.NETHER);
        setSpawn(nether, overworldKey, overworldSpawn);
        PracticeLevel263 end = createPracticeLevel(seed, endKey, PracticeDimension.END);
        setSpawn(end, overworldKey, overworldSpawn);
        PracticeLevel263.buildObsidianPlatform(end);

        Map<PracticeDimension, PracticeLevel263> triple =
                new EnumMap<PracticeDimension, PracticeLevel263>(PracticeDimension.class);
        triple.put(PracticeDimension.OVERWORLD, overworld);
        triple.put(PracticeDimension.NETHER, nether);
        triple.put(PracticeDimension.END, end);
        Map<ResourceKey<Level>, ServerLevel> siblings =
                new HashMap<ResourceKey<Level>, ServerLevel>();
        siblings.put(Level.OVERWORLD, overworld);
        siblings.put(Level.NETHER, nether);
        siblings.put(Level.END, end);
        for (PracticeLevel263 member : triple.values()) {
            levels.put(member.dimension(), member);
            member.link(siblings);
        }
        overworld.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SPAWN,
                new ChunkPos(net.minecraft.core.SectionPos.blockToSectionCoord(overworldSpawn.getX()),
                        net.minecraft.core.SectionPos.blockToSectionCoord(overworldSpawn.getZ())),
                11);

        linkedPracticeLevels.add(triple);
        while (linkedPracticeLevels.size() > 1) {
            removeLinkedPracticeLevels(linkedPracticeLevels.remove(0));
        }
        SpeedrunLogger.info("Created 26.3 practice worlds for seed " + seed);
        return Collections.unmodifiableMap(triple);
    }

    @Override
    public PracticeLevel263 createEndPracticeWorld(long seed) throws IOException {
        MinecraftServer server = (MinecraftServer) (Object) this;
        ResourceKey<Level> endKey = practiceKey(seed + "_" + UUID.randomUUID() + "_3");
        PracticeLevel263 end = createPracticeLevel(seed, endKey, PracticeDimension.END);
        // Single end worlds stand alone: respawn data points at the main
        // overworld (vanilla end behavior), but the practice spawn stays on
        // the obsidian platform.
        ServerLevel mainOverworld = server.getLevel(Level.OVERWORLD);
        LevelData.RespawnData mainRespawn =
                mainOverworld == null ? null : mainOverworld.getRespawnData();
        BlockPos platform = ServerLevel.END_SPAWN_POINT;
        if (mainRespawn != null) {
            LevelData.RespawnData respawn = LevelData.RespawnData.of(
                    Level.OVERWORLD, mainRespawn.pos(), 90.0f, 0.0f);
            levelData(end).setSpawn(respawn);
            end.setRespawnData(respawn);
        }
        PracticeLevel263.buildObsidianPlatform(end);
        levelData(end).setInitialized(true);
        levels.put(endKey, end);
        endPracticeLevels.add(end);
        while (endPracticeLevels.size() > 1) {
            removePracticeLevel(endPracticeLevels.remove(0));
        }
        SpeedrunLogger.info("Created 26.3 end practice world for seed " + seed
                + " (platform at " + platform.getX() + "," + platform.getY() + "," + platform.getZ() + ")");
        return end;
    }

    @Override
    public void deletePracticeLevel(ServerLevel level) {
        if (!(level instanceof PracticeLevel263)) {
            return;
        }
        if (!levels.containsKey(level.dimension())) {
            return;
        }
        try {
            removePracticeLevel((PracticeLevel263) level);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not delete practice world "
                    + level.dimension().identifier() + ": " + failure.getMessage(), failure);
        }
    }

    @Override
    public void pruneLeftoverPracticeLevels() {
        // The dimensions root has no direct accessor; derive it from the
        // nether folder (<root>/dimensions/minecraft/the_nether) and refuse
        // to touch anything outside a directory literally named "dimensions".
        Path probe = storageSource.getDimensionPath(Level.NETHER);
        Path dimensions = probe == null || probe.getParent() == null ? null
                : probe.getParent().getParent().resolve("speedrun_practice");
        if (dimensions == null || dimensions.getParent() == null
                || !"dimensions".equals(dimensions.getParent().getFileName().toString())) {
            return;
        }
        if (!Files.isDirectory(dimensions)) {
            return;
        }
        Set<String> live = new HashSet<String>();
        for (ResourceKey<Level> key : levels.keySet()) {
            if (isPracticeKey(key)) {
                live.add(key.identifier().getPath());
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
            SpeedrunLogger.warn("Could not prune leftover practice folders: " + failure.getMessage());
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void removePracticeLevelsOnShutdown(CallbackInfo info) {
        for (Map<PracticeDimension, PracticeLevel263> triple : linkedPracticeLevels) {
            try {
                removeLinkedPracticeLevels(triple);
            } catch (IOException | RuntimeException failure) {
                SpeedrunLogger.warn("Could not remove practice worlds on shutdown: "
                        + failure.getMessage());
            }
        }
        linkedPracticeLevels.clear();
        for (PracticeLevel263 end : endPracticeLevels) {
            try {
                removePracticeLevel(end);
            } catch (IOException | RuntimeException failure) {
                SpeedrunLogger.warn("Could not remove end practice world on shutdown: "
                        + failure.getMessage());
            }
        }
        endPracticeLevels.clear();
    }

    private PracticeLevel263 createPracticeLevel(long seed, ResourceKey<Level> key,
            PracticeDimension dimension) throws IOException {
        MinecraftServer server = (MinecraftServer) (Object) this;
        ResourceKey<Level> vanillaKey = vanillaKey(dimension);
        ServerLevel live = server.getLevel(vanillaKey);
        if (live == null) {
            throw new IOException("Cannot build a practice " + dimension
                    + " world: the live " + vanillaKey.identifier() + " is not loaded.");
        }
        ChunkGenerator liveGenerator = live.getChunkSource().getGenerator();
        ChunkGenerator generator = liveGenerator;
        if (liveGenerator instanceof NoiseBasedChunkGenerator) {
            NoiseBasedChunkGenerator noise = (NoiseBasedChunkGenerator) liveGenerator;
            generator = new NoiseBasedChunkGenerator(
                    liveGenerator.getBiomeSource(), noise.generatorSettings());
        }
        LevelStem stem = new LevelStem(live.dimensionTypeRegistration(), generator);
        ServerLevelData data = new PrimaryLevelData(worldData.getLevelSettings().copy(),
                PrimaryLevelData.SpecialWorldProperty.NONE, Lifecycle.stable());
        PracticeLevel263 practice = PracticeLevel263.create(server, executor, storageSource, data, key, stem,
                worldData.isDebugWorld(), BiomeManager.obfuscateSeed(seed),
                Collections.<net.minecraft.world.level.CustomSpawner>emptyList(), true,
                seed, dimension);
        // Practice levels are disposable. Keep autosave from opening files
        // that would still be held when the level is removed below.
        practice.noSave = true;
        return practice;
    }

    private static ResourceKey<Level> practiceKey(String path) {
        return ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath("speedrun_practice", path));
    }

    private static ResourceKey<Level> vanillaKey(PracticeDimension dimension) {
        switch (dimension) {
            case NETHER:
                return Level.NETHER;
            case END:
                return Level.END;
            case OVERWORLD:
            default:
                return Level.OVERWORLD;
        }
    }

    private static boolean isPracticeKey(ResourceKey<Level> key) {
        return key != null && "speedrun_practice".equals(key.identifier().getNamespace());
    }

    private static BlockPos computeSpawn(PracticeLevel263 level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        net.minecraft.world.level.ChunkPos origin = generator.getOrigin(randomState);
        int y = generator.getSpawnHeight(level);
        return origin.getWorldPosition().offset(8, y, 8);
    }

    private static void setSpawn(PracticeLevel263 level, ResourceKey<Level> respawnLevel, BlockPos spawn) {
        LevelData.RespawnData respawn = LevelData.RespawnData.of(respawnLevel, spawn, 90.0f, 0.0f);
        levelData(level).setSpawn(respawn);
        level.setRespawnData(respawn);
        levelData(level).setInitialized(true);
    }

    private static ServerLevelData levelData(PracticeLevel263 level) {
        return (ServerLevelData) level.getLevelData();
    }

    private void removeLinkedPracticeLevels(Map<PracticeDimension, PracticeLevel263> triple)
            throws IOException {
        List<Map.Entry<PracticeDimension, PracticeLevel263>> members =
                new ArrayList<Map.Entry<PracticeDimension, PracticeLevel263>>(triple.entrySet());
        Collections.sort(members, new Comparator<Map.Entry<PracticeDimension, PracticeLevel263>>() {
            @Override
            public int compare(Map.Entry<PracticeDimension, PracticeLevel263> left,
                    Map.Entry<PracticeDimension, PracticeLevel263> right) {
                return left.getKey().compareTo(right.getKey());
            }
        });
        for (Map.Entry<PracticeDimension, PracticeLevel263> member : members) {
            removePracticeLevel(member.getValue());
        }
    }

    private void removePracticeLevel(PracticeLevel263 level) throws IOException {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (!levels.containsKey(level.dimension())) {
            // Already removed (deleted mid-session, reaped again on
            // shutdown): closing a second time wedges the server thread
            // inside ServerLevel.close(), so there is nothing left to do.
            return;
        }
        ServerLevel mainOverworld = server.overworld();
        BlockPos lobby = mainOverworld == null ? BlockPos.ZERO : mainOverworld.getRespawnData().pos();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() == level) {
                player.teleportTo(mainOverworld, lobby.getX() + 0.5, lobby.getY(),
                        lobby.getZ() + 0.5, Collections.<Relative>emptySet(), 90.0f, 0.0f, false);
            }
        }
        if (level.dimensionType().hasEnderDragonFight() && level.getDragonFight() != null) {
            try {
                ((EnderDragonFightAccess263) (Object) level.getDragonFight()).getDragonEvent()
                        .removeAllPlayers();
            } catch (RuntimeException missing) {
                // Fight bar cleanup is best-effort; the level still unloads.
            }
        }
        levels.remove(level.dimension(), level);
        // Disposable practice levels are marked noSave at construction time;
        // close releases their chunk/data resources without flushing them.
        level.close();
        deleteDirectory(storageSource.getDimensionPath(level.dimension()));
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
