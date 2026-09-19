package com.gregor0410.speedrunpractice.config;

import com.google.common.collect.Lists;
import com.gregor0410.ptlib.PTLib;
import com.gregor0410.ptlib.config.PTConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.world.gen.chunk.StructuresConfig;
import net.minecraft.world.gen.feature.StructureFeature;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static java.lang.Math.ceil;
import static java.lang.Math.round;

/**
 * Client-only Cloth Config screen for {@link ModConfig}.
 *
 * <p>Kept in its own class because the config <em>data</em> ({@link ModConfig})
 * must load on a dedicated server, where neither {@code net.minecraft.client}
 * nor Cloth Config entry classes exist.
 */
@Environment(EnvType.CLIENT)
public final class ModConfigScreen {
    private ModConfigScreen() {
    }

    public static Screen create(ModConfig config, Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setDoesConfirmSave(false)
                .setTransparentBackground(true)
                .setTitle(new TranslatableText("speedrun-practice.options"))
                .setSavingRunnable(() -> {
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        ConfigCategory general = builder.getOrCreateCategory(new TranslatableText("speedrun-practice.options.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        general.addEntry(entryBuilder.startIntSlider(new TranslatableText("speedrun-practice.options.nether_region_size"), getNetherRegionSize(config), 1, 200)
                .setDefaultValue(100)
                .setSaveConsumer(value -> setNetherRegionSize(config, value))
                .setTextGetter(a -> new LiteralText(String.format("%d %%", a)))
                .build());
        general.addEntry(entryBuilder.startIntSlider(new TranslatableText("speedrun-practice.options.bastion_rarity"), config.ptConfig.getBastionRarity(), 0, 100)
                .setDefaultValue(60)
                .setTextGetter(a -> new LiteralText(String.format("%d %%", a)))
                .setSaveConsumer(a -> config.ptConfig.setBastionRarity(a))
                .setTooltip(new TranslatableText("speedrun-practice.options.bastion_rarity_tooltip"))
                .build());
        general.addEntry(entryBuilder.startIntField(new TranslatableText("speedrun-practice.options.max_dist"), config.defaultMaxDist)
                .setDefaultValue(1000)
                .setMin(0)
                .setSaveConsumer(a -> config.defaultMaxDist = a)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.randomisePostBlindInventory"), config.randomisePostBlindInventory)
                .setDefaultValue(true)
                .setSaveConsumer(a -> config.randomisePostBlindInventory = a)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.deletePracticeWorlds"), config.deletePracticeWorlds)
                .setDefaultValue(true)
                .setSaveConsumer(a -> config.deletePracticeWorlds = a)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.postBlindSpawnChunks"), config.postBlindSpawnChunks)
                .setDefaultValue(false)
                .setSaveConsumer(a -> config.postBlindSpawnChunks = a)
                .setTooltip(new TranslatableText("speedrun-practice.options.postBlindSpawnChunks.tooltip1"), new TranslatableText("speedrun-practice.options.postBlindSpawnChunks.tooltip2"))
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.postBlindCaveSpawns"), config.caveSpawns)
                .setDefaultValue(true)
                .setSaveConsumer(a -> config.caveSpawns = a)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.calc_mode"), config.calcMode)
                .setDefaultValue(true)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.useSeedList"), config.useSeedList)
                .setDefaultValue(false)
                .setSaveConsumer(a -> config.useSeedList = a)
                .build());
        general.addEntry(entryBuilder.startSubCategory(new TranslatableText("speedrun-practice.options.bastions"), Lists.newArrayList(
                entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.bastions.housing"), config.ptConfig.isHousing())
                        .setDefaultValue(true)
                        .setYesNoTextSupplier(a -> a ? ScreenTexts.ON : ScreenTexts.OFF)
                        .setSaveConsumer(a -> config.ptConfig.setHousing(a))
                        .build(),
                entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.bastions.stables"), config.ptConfig.isStables())
                        .setDefaultValue(true)
                        .setYesNoTextSupplier(a -> a ? ScreenTexts.ON : ScreenTexts.OFF)
                        .setSaveConsumer(a -> config.ptConfig.setStables(a))
                        .build(),
                entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.bastions.treasure"), config.ptConfig.isTreasure())
                        .setDefaultValue(true)
                        .setYesNoTextSupplier(a -> a ? ScreenTexts.ON : ScreenTexts.OFF)
                        .setSaveConsumer(a -> config.ptConfig.setTreasure(a))
                        .build(),
                entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.bastions.bridge"), config.ptConfig.isBridge())
                        .setDefaultValue(true)
                        .setYesNoTextSupplier(a -> a ? ScreenTexts.ON : ScreenTexts.OFF)
                        .setSaveConsumer(a -> config.ptConfig.setBridge(a))
                        .build())).build());
        general.addEntry(entryBuilder.startSubCategory(new TranslatableText("speedrun-practice.options.end"), Lists.newArrayList(
                entryBuilder.startEnumSelector(new TranslatableText("speedrun-practice.options.dragon_type"), PTLib.DragonType.class, config.ptConfig.getDragonType())
                        .setDefaultValue(PTLib.DragonType.BOTH)
                        .setSaveConsumer(a -> config.ptConfig.setDragonType(a))
                        .build(),
                entryBuilder.startBooleanToggle(new TranslatableText("speedrun-practice.options.eliminate_cage_spawns"), config.ptConfig.isEliminateCageSpawns())
                        .setDefaultValue(true)
                        .setSaveConsumer(a -> config.ptConfig.setEliminateCageSpawns(a))
                        .build(),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.small_boy", 0),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.small_cage", 1),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.tall_cage", 2),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.m85", 3),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.m88", 4),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.m91", 5),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.t94", 6),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.t97", 7),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.t100", 8),
                getEndTower(entryBuilder, config, "speedrun-practice.options.towers.t103", 9)
        )).build());

        return builder.build();
    }

    private static void setNetherRegionSize(ModConfig config, Integer integer) {
        float scale = (float) integer / 100;
        int defaultNetherSpacing = StructuresConfig.DEFAULT_STRUCTURES.get(StructureFeature.FORTRESS).getSpacing();
        int defaultNetherSeparation = StructuresConfig.DEFAULT_STRUCTURES.get(StructureFeature.FORTRESS).getSeparation();
        int netherSalt = 30084232;
        PTConfig.StructureRegion netherConfig = new PTConfig.StructureRegion((int) ceil(defaultNetherSpacing * scale), round(defaultNetherSeparation * scale), netherSalt);
        config.ptConfig.getStructureRegions().put("fortress", netherConfig);
        config.ptConfig.getStructureRegions().put("bastion_remnant", netherConfig);
    }

    private static int getNetherRegionSize(ModConfig config) {
        int defaultNetherSpacing = StructuresConfig.DEFAULT_STRUCTURES.get(StructureFeature.FORTRESS).getSpacing();
        PTConfig.StructureRegion fortress = config.ptConfig.getStructureRegions().get("fortress");
        if (fortress == null) return 100;
        return (int) ((float) fortress.spacing / (float) defaultNetherSpacing * 100);
    }

    @NotNull
    private static BooleanListEntry getEndTower(ConfigEntryBuilder entryBuilder, ModConfig config, String text, int tower) {
        return entryBuilder.startBooleanToggle(new TranslatableText(text), config.ptConfig.getEndTowers().get(tower))
                .setDefaultValue(true)
                .setYesNoTextSupplier(a -> a ? ScreenTexts.ON : ScreenTexts.OFF)
                .setSaveConsumer(a -> config.ptConfig.getEndTowers().set(tower, a))
                .build();
    }
}
