package com.gregor0410.speedrunpractice;

import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import com.google.gson.Gson;
import com.gregor0410.ptlib.PTLib;
import com.gregor0410.ptlib.rng.AccessibleRandom;
import com.gregor0410.speedrunpractice.command.Command;
import com.gregor0410.speedrunpractice.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.io.IOException;

public class SpeedrunPractice implements ModInitializer {
    public static ModConfig config;
    public static AccessibleRandom random = new AccessibleRandom();
    public static boolean welcomeShown = false;
    static final Gson gson = new Gson();
    private static final ModContainer modContainer = FabricLoader.getInstance().getModContainer("speedrun-practice").get();
    private static final String donationLink = "https://ko-fi.com/gregor0410";
    static final Version version = modContainer.getMetadata().getVersion();
    public static AutoSaveStater autoSaveStater = new AutoSaveStater();
    public static SpeedrunIGTInterface speedrunIGTInterface=null;
    private static final UpdateChecker updateChecker = new UpdateChecker();
    public static final SeedManager seedManager = new SeedManager();

    @Override
    public void onInitialize() {
        if(FabricLoader.getInstance().isModLoaded("speedrunigt")){
            try {
                speedrunIGTInterface = new SpeedrunIGTInterface();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
        config = ModConfig.load();
        seedManager.reload();
        PTLib.setConfig(config.ptConfig);
        Command.registerCommands();
        updateChecker.checkUpdate();
    }

    public static void sendWelcomeMessage(ServerPlayerEntity player) throws IOException, VersionParsingException {
        player.sendMessage(Text.literal(String.format("[SpeedrunPractice v%s by Gregor0410]",version)).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x00ff00))),false);
        player.sendMessage(Text.literal("[Donation Link]")
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,donationLink))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Text.literal("Click"))))
                .formatted(Formatting.DARK_GREEN),false);

        if (updateChecker.isOutdatedVersion()) {
            player.sendMessage(Text.literal(String.format("There is a new version available: v%s", updateChecker.getVersionName())).formatted(Formatting.RED),false);
            player.sendMessage(Text.literal(String.format("Patch notes:\n%s ", updateChecker.getChangelogs().replace('\r',' ').replace('-','•'))),false);
            player.sendMessage(
                    Text.literal("Click to download latest version")
                            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x00ff00))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,"https://github.com/Gregor0410/SpeedrunPractice/releases/latest"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Text.literal("Click")))),false);
        } else if (updateChecker.isCheckedUpdate()) {
            player.sendMessage(Text.literal("You are on the latest version."),false);
        }
    }

    public enum DragonType{ FRONT, BACK, BOTH }
}
