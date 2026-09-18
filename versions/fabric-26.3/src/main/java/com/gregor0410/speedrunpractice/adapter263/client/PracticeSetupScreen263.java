package com.gregor0410.speedrunpractice.adapter263.client;

import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.gui.PracticeMenuModel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scenario setup: seed source + fixed-seed field + loadout picker, the
 * preset's default options for reference, capability notes, and Start.
 */
final class PracticeSetupScreen263 extends PracticeScreenBase263 {
    private final PracticeMenuModel.ScenarioScreen model;
    private final String customId;
    private final List<String> loadouts;
    private int seedSourceIdx;
    private int loadoutIdx;
    private String pendingSeedValue = "";
    private EditBox seedField;

    PracticeSetupScreen263(PracticeMenuModel.ScenarioScreen model, String customId) {
        super(model.preset().displayName());
        this.model = model;
        this.customId = customId;
        this.loadouts = new ArrayList<String>();
        this.loadouts.add("(none)");
        this.loadouts.addAll(model.loadoutOptions());
        this.seedSourceIdx = 0;
        this.loadoutIdx = 0;
    }

    private PracticeSetupScreen263(PracticeMenuModel.ScenarioScreen model, String customId,
            int seedSourceIdx, int loadoutIdx, String seedValue) {
        this(model, customId);
        this.seedSourceIdx = seedSourceIdx;
        this.loadoutIdx = loadoutIdx;
        this.pendingSeedValue = seedValue;
    }

    @Override
    protected void init() {
        seedField = new EditBox(font, width / 2 - 100, 118, 200, 20,
                Component.literal("Seed value"));
        seedField.setMaxLength(24);
        seedField.setValue(pendingSeedValue);
        addRenderableWidget(seedField);
        smallButton(width / 2 - 155, 92, 150, "Seed: " + seedSource(), button -> reopen(
                (seedSourceIdx + 1) % model.seedSourceOptions().size(), loadoutIdx));
        smallButton(width / 2 + 5, 92, 150, "Loadout: " + loadouts.get(loadoutIdx), button -> reopen(
                seedSourceIdx, (loadoutIdx + 1) % loadouts.size()));
        smallButton(width / 2 - 155, 190, 150, "Start practice", button -> start());
        smallButton(width / 2 + 5, 190, 150, "Back", button -> ClientScreens263.openMain());
    }

    private String seedSource() {
        return model.seedSourceOptions().get(seedSourceIdx);
    }

    private void reopen(int nextSeed, int nextLoadout) {
        String value = seedField == null ? pendingSeedValue : seedField.getValue();
        ClientScreens263.open(new PracticeSetupScreen263(model, customId, nextSeed, nextLoadout, value));
    }

    private void start() {
        final String source = seedSource();
        final String value = seedField == null ? "" : seedField.getValue().trim();
        final String loadout = loadoutIdx == 0 ? null : loadouts.get(loadoutIdx);
        final PracticePreset preset = model.preset();
        ClientScreens263.onServer((server, entity, runtime) -> {
            PracticeSettings settings = new PracticeSettings(preset.defaults().asMap());
            settings.set("seed.source", source);
            if ("fixed".equals(source)) {
                if (value.isEmpty()) {
                    entity.sendSystemMessage(Component.literal(
                            "Enter a seed value or pick another source."));
                    return;
                }
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException bad) {
                    entity.sendSystemMessage(Component.literal("\"" + value + "\" is not a number."));
                    return;
                }
                settings.set("seed.value", value);
            }
            if (loadout != null) {
                settings.set("loadout", loadout);
            }
            PracticeSession session;
            if (customId != null) {
                session = runtime.startCustom(customId, settings, ClientScreens263.handle(entity));
            } else {
                session = runtime.startPractice(preset.type(), settings, ClientScreens263.handle(entity));
            }
            String name = preset.type() == PracticeType.CUSTOM ? customId : preset.displayName();
            entity.sendSystemMessage(Component.literal(
                    "Started " + name + " on seed " + session.seed() + "."));
            net.minecraft.client.Minecraft.getInstance().execute(() -> ClientScreens263.open(null));
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int y = 34;
        line(graphics, "Seed source, loadout, then Start.", y, 0xA0A0A0);
        y += 12;
        List<String> notes = model.unsupportedNotes();
        for (int i = 0; i < notes.size() && i < 2; i++) {
            line(graphics, notes.get(i), y, 0xFF5555);
            y += 10;
        }
        line(graphics, "Seed value (fixed source):", 106, 0xA0A0A0);
        y = 144;
        Map<String, String> options = model.options();
        int shown = 0;
        for (Map.Entry<String, String> option : options.entrySet()) {
            if (shown >= 3) {
                break;
            }
            line(graphics, option.getKey() + " = " + option.getValue(), y, 0x808080);
            y += 10;
            shown++;
        }
        if (options.size() > shown) {
            line(graphics, "+" + (options.size() - shown) + " more defaults (edit via config)", y, 0x808080);
        }
    }

}
