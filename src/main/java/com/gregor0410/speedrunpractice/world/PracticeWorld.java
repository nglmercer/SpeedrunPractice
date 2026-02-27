package com.gregor0410.speedrunpractice.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.spawner.SpecialSpawner;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executor;

public class PracticeWorld extends ServerWorld {
    private final long seed;
    public Map<RegistryKey<World>,RegistryKey<World>> associatedWorlds = new HashMap<>();

    public PracticeWorld(MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey<World> registryKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener generationProgressListener, boolean bl, long l, List<SpecialSpawner> list, boolean bl2, long seed) {
        super(server, workerExecutor, session, properties, registryKey, dimensionOptions, generationProgressListener, bl, l, list, bl2, null);
        this.seed = seed;
    }

    @Override
    public long getSeed() {
        return seed;
    }
}
