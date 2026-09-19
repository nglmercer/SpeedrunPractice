package com.gregor0410.speedrunpractice.config;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gregor0410.ptlib.PTLib;
import com.gregor0410.ptlib.config.PTConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModConfig{
    public static final List<String> DEFAULTENDINVENTORY;
    public static final List<String> DEFAULTNETHERINVENTORY;
    public static final List<String> DEFAULTPOSTBLINDINVENTORY;
    public Map<String,List<List<String>>> practiceInventories =new HashMap<>(ImmutableMap.of(
            "end",Lists.newArrayList(DEFAULTENDINVENTORY,new ArrayList<>(),new ArrayList<>()),
            "nether",Lists.newArrayList(DEFAULTNETHERINVENTORY,new ArrayList<>(),new ArrayList<>()),
            "overworld",Lists.newArrayList(new ArrayList<>(),new ArrayList<>(),new ArrayList<>()),
            "stronghold",Lists.newArrayList(DEFAULTPOSTBLINDINVENTORY,new ArrayList<>(),new ArrayList<>()),
            "postblind",Lists.newArrayList(DEFAULTPOSTBLINDINVENTORY,new ArrayList<>(),new ArrayList<>())));
    public Map<String,Integer> practiceSlots = new HashMap<>(ImmutableMap.of("end", 0, "nether", 0,"postblind",0,"overworld",0,"stronghold",0));
    public PTConfig ptConfig = new PTConfig();
    public int defaultMaxDist = 1000;
    public boolean calcMode = true;
    public boolean deletePracticeWorlds = true;
    public boolean postBlindSpawnChunks =false;
    public boolean caveSpawns=true;
    public boolean randomisePostBlindInventory=true;
    public boolean useSeedList = false;


    public static ModConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("speedrun-practice.json");
        BufferedReader reader = null;
        try {
            reader = Files.newBufferedReader(path);
            Gson gson = new Gson();
            ModConfig config = gson.fromJson(reader,ModConfig.class);
            reader.close();
            return config;
        } catch (IOException e) {
            return new ModConfig();
        }
    }

    public void save() throws IOException {
        PTLib.setConfig(ptConfig);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("speedrun-practice.json");
        Files.createDirectories(path.getParent());
        BufferedWriter writer = Files.newBufferedWriter(path);
        gson.toJson(this,writer);
        writer.close();
    }

    static{
        DEFAULTENDINVENTORY=Lists.newArrayList("{Slot:0b,id:\"minecraft:iron_axe\",Count:1b,tag:{Damage:0}}",
                "{Slot:1b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:2b,id:\"minecraft:iron_pickaxe\",Count:1b,tag:{Damage:0}}",
                "{Slot:3b,id:\"minecraft:water_bucket\",Count:1b}",
                "{Slot:4b,id:\"minecraft:ender_pearl\",Count:4b}",
                "{Slot:5b,id:\"minecraft:respawn_anchor\",Count:4b}",
                "{Slot:6b,id:\"minecraft:glowstone\",Count:4b}",
                "{Slot:7b,id:\"minecraft:crying_obsidian\",Count:64b}",
                "{Slot:8b,id:\"minecraft:cobblestone\",Count:64b}",
                "{Slot:9b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:10b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:11b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:12b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:13b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:14b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:15b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:16b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:17b,id:\"minecraft:white_bed\",Count:1b}",
                "{Slot:27b,id:\"minecraft:bow\",Count:1b,tag:{Damage:0}}",
                "{Slot:28b,id:\"minecraft:arrow\",Count:64b}");
        DEFAULTNETHERINVENTORY=Lists.newArrayList("{Slot:0b,id:\"minecraft:iron_axe\",Count:1b,tag:{Damage:0}}",
                "{Slot:1b,id:\"minecraft:iron_shovel\",Count:1b,tag:{Damage:0}}",
                "{Slot:2b,id:\"minecraft:iron_pickaxe\",Count:1b,tag:{Damage:0}}",
                "{Slot:3b,id:\"minecraft:flint_and_steel\",Count:1b,tag:{Damage:0}}",
                "{Slot:4b,id:\"minecraft:oak_boat\",Count:1b}",
                "{Slot:5b,id:\"minecraft:bread\",Count:5b}",
                "{Slot:6b,id:\"minecraft:crafting_table\",Count:1b}",
                "{Slot:7b,id:\"minecraft:lava_bucket\",Count:1b}",
                "{Slot:8b,id:\"minecraft:oak_planks\",Count:20b}",
                "{Slot:27b,id:\"minecraft:stick\",Count:2b}");
        DEFAULTPOSTBLINDINVENTORY=Lists.newArrayList("{Slot:0b,id:\"minecraft:iron_axe\",Count:1b,tag:{Damage:0}}",
                "{Slot:1b,id:\"minecraft:iron_shovel\",Count:1b,tag:{Damage:0}}",
                "{Slot:2b,id:\"minecraft:iron_pickaxe\",Count:1b,tag:{Damage:0}}",
                "{Slot:3b,id:\"minecraft:flint_and_steel\",Count:1b,tag:{Damage:0}}",
                "{Slot:4b,id:\"minecraft:ender_pearl\",Count:16b}",
                "{Slot:5b,id:\"minecraft:ender_eye\",Count:12b}",
                "{Slot:6b,id:\"minecraft:crafting_table\",Count:1b}",
                "{Slot:7b,id:\"minecraft:water_bucket\",Count:1b}",
                "{Slot:8b,id:\"minecraft:nether_bricks\",Count:32b}",
                "{Slot:9b,id:\"minecraft:string\",Count:64b}",
                "{Slot:10b,id:\"minecraft:glowstone\",Count:8b}",
                "{Slot:11b,id:\"minecraft:crying_obsidian\",Count:32b}",
                "{Slot:12b,id:\"minecraft:oak_planks\",Count:32b}",
                "{Slot:13b,id:\"minecraft:oak_boat\",Count:1b}",
                "{Slot:100b,id:\"minecraft:iron_boots\",Count:1b,tag:{Damage:0,Enchantments:[{lvl:3s,id:\"minecraft:soul_speed\"}]}}",
                "{Slot:103b,id:\"minecraft:golden_helmet\",Count:1b,tag:{Damage:0}}",
                "{Slot:-106b,id:\"minecraft:bread\",Count:5b}");
    }
}
