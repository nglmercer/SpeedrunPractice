package com.gregor0410.speedrunpractice;

import net.minecraft.text.Text;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SeedManager {
    private List<Long> seedList = new ArrayList<>();
    private int currentIndex = 0;
    private static final Path SEED_LIST_PATH = FabricLoader.getInstance().getConfigDir().resolve("speedrun-practice-seeds.txt");

    public void reload() {
        if (Files.exists(SEED_LIST_PATH)) {
            try (Stream<String> lines = Files.lines(SEED_LIST_PATH)) {
                seedList = lines
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .map(line -> {
                            try {
                                return Long.parseLong(line);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                        .filter(seed -> seed != null)
                        .collect(Collectors.toList());
                currentIndex = 0;
            } catch (IOException e) {
                seedList = new ArrayList<>();
            }
        } else {
            seedList = new ArrayList<>();
            try {
                Files.createFile(SEED_LIST_PATH);
            } catch (IOException ignored) {}
        }
    }

    public long getNextSeed() {
        if (SpeedrunPractice.config.useSeedList && !seedList.isEmpty()) {
            long seed = seedList.get(currentIndex);
            currentIndex = (currentIndex + 1) % seedList.size();
            return seed;
        }
        return SpeedrunPractice.random.nextLong();
    }

    public int getSeedCount() {
        return seedList.size();
    }

    public void setIndex(int index) {
        if (index >= 0 && index < seedList.size()) {
            this.currentIndex = index;
        }
    }
}
