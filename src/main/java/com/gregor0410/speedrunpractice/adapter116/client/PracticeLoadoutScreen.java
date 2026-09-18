package com.gregor0410.speedrunpractice.adapter116.client;

import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutCompatibility;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.List;

/** Loadout screen: list, select, save current, apply, delete. */
final class PracticeLoadoutScreen extends PracticeScreenBase {
    /** Server-thread snapshot backing the screen. */
    static final class Data {
        final List<String> ids;
        final int selectedIdx;

        Data(List<String> ids, int selectedIdx) {
            this.ids = ids;
            this.selectedIdx = selectedIdx;
        }

        static Data snapshot(PracticeRuntime runtime, String keepSelected) {
            List<String> ids = ClientData.loadoutIds(runtime);
            int idx = 0;
            if (keepSelected != null) {
                for (int i = 0; i < ids.size(); i++) {
                    if (keepSelected.equals(ids.get(i))) {
                        idx = i;
                    }
                }
            }
            if (!ids.isEmpty()) {
                idx = Math.max(0, Math.min(idx, ids.size() - 1));
            }
            return new Data(ids, idx);
        }
    }

    private final Data data;
    private TextFieldWidget nameField;

    PracticeLoadoutScreen(Data data) {
        super("Loadouts");
        this.data = data;
    }

    private String selected() {
        return data.ids.isEmpty() ? null : data.ids.get(data.selectedIdx);
    }

    @Override
    protected void init() {
        nameField = new TextFieldWidget(textRenderer, width / 2 - 100, 150, 200, 20,
                new LiteralText("Loadout name"));
        nameField.setMaxLength(40);
        children.add(nameField);
        smallButton(width / 2 - 155, 92, 150, "Select: " + (selected() == null ? "(none)" : selected()),
                button -> reopen(selected() == null ? 0 : (data.selectedIdx + 1) % data.ids.size(), null));
        smallButton(width / 2 + 5, 92, 150, "Apply", button -> ClientScreens.onServer((server, entity, runtime) -> {
            String id = selected();
            if (id == null) {
                entity.sendMessage(new LiteralText("No loadouts saved yet."), false);
                return;
            }
            Loadout loadout = runtime.loadouts().get(id);
            if (loadout == null) {
                entity.sendMessage(new LiteralText("Loadout \"" + id + "\" is gone."), false);
                return;
            }
            LoadoutCompatibility.Result filtered =
                    LoadoutCompatibility.filter(runtime.adapter().registries(), loadout);
            for (String warning : filtered.warnings()) {
                entity.sendMessage(new LiteralText(warning), false);
            }
            runtime.adapter().players().applyLoadout(ClientScreens.handle(entity), filtered.loadout());
            entity.sendMessage(new LiteralText("Applied loadout \"" + id + "\"."), false);
        }));
        smallButton(width / 2 - 155, 176, 150, "Save current", button -> {
            final String name = nameField.getText().trim();
            ClientScreens.onServer((server, entity, runtime) -> {
                if (name.isEmpty()) {
                    entity.sendMessage(new LiteralText("Enter a name first."), false);
                    return;
                }
                Loadout captured = runtime.adapter().inventories()
                        .captureLoadout(ClientScreens.handle(entity), name);
                runtime.loadouts().save(captured);
                runtime.persistLoadouts();
                entity.sendMessage(new LiteralText("Saved loadout \"" + name + "\"."), false);
                reopenOnClient(name, runtime);
            });
        });
        smallButton(width / 2 + 5, 176, 150, "Delete", button -> ClientScreens.onServer((server, entity, runtime) -> {
            String id = selected();
            if (id == null) {
                entity.sendMessage(new LiteralText("No loadouts saved yet."), false);
                return;
            }
            runtime.loadouts().delete(id);
            runtime.persistLoadouts();
            entity.sendMessage(new LiteralText("Deleted loadout \"" + id + "\"."), false);
            reopenOnClient(null, runtime);
        }));
        centeredButton(200, "Back", button -> ClientScreens.openMain());
    }

    private void reopen(int nextIdx, String keep) {
        final String keepSelected = keep != null ? keep
                : (data.ids.isEmpty() ? null : data.ids.get(nextIdx % data.ids.size()));
        ClientScreens.onServer((server, entity, runtime) -> reopenOnClient(keepSelected, runtime));
    }

    private static void reopenOnClient(String keepSelected, PracticeRuntime runtime) {
        Data fresh = Data.snapshot(runtime, keepSelected);
        net.minecraft.client.MinecraftClient.getInstance()
                .execute(() -> ClientScreens.open(new PracticeLoadoutScreen(fresh)));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        line(matrices, "Name for Save:", 138, 0xA0A0A0);
        nameField.render(matrices, mouseX, mouseY, delta);
        int y = 34;
        if (data.ids.isEmpty()) {
            line(matrices, "No loadouts saved yet.", y, 0xA0A0A0);
        } else {
            int shown = Math.min(4, data.ids.size());
            for (int i = 0; i < shown; i++) {
                String marker = (i == data.selectedIdx) ? "> " : "  ";
                line(matrices, marker + data.ids.get(i), y, i == data.selectedIdx ? 0x55FF55 : 0xFFFFFF);
                y += 11;
            }
            if (data.ids.size() > shown) {
                line(matrices, "+" + (data.ids.size() - shown) + " more", y, 0x808080);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (nameField != null) {
            nameField.tick();
        }
    }
}
