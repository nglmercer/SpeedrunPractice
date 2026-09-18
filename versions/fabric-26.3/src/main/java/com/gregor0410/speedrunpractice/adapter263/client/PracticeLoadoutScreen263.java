package com.gregor0410.speedrunpractice.adapter263.client;

import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutCompatibility;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Loadout screen: list, select, save current, apply, delete. */
final class PracticeLoadoutScreen263 extends PracticeScreenBase263 {
    /** Server-thread snapshot backing the screen. */
    static final class Data {
        final List<String> ids;
        final int selectedIdx;

        Data(List<String> ids, int selectedIdx) {
            this.ids = ids;
            this.selectedIdx = selectedIdx;
        }

        static Data snapshot(PracticeRuntime runtime, String keepSelected) {
            List<String> ids = ClientData263.loadoutIds(runtime);
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
    private EditBox nameField;

    PracticeLoadoutScreen263(Data data) {
        super("Loadouts");
        this.data = data;
    }

    private String selected() {
        return data.ids.isEmpty() ? null : data.ids.get(data.selectedIdx);
    }

    @Override
    protected void init() {
        nameField = new EditBox(font, width / 2 - 100, 150, 200, 20,
                Component.literal("Loadout name"));
        nameField.setMaxLength(40);
        addRenderableWidget(nameField);
        smallButton(width / 2 - 155, 92, 150, "Select: " + (selected() == null ? "(none)" : selected()),
                button -> reopen(selected() == null ? 0 : (data.selectedIdx + 1) % data.ids.size(), null));
        smallButton(width / 2 + 5, 92, 150, "Apply", button ->
                ClientScreens263.onServer((server, entity, runtime) -> {
                    String id = selected();
                    if (id == null) {
                        entity.sendSystemMessage(Component.literal("No loadouts saved yet."));
                        return;
                    }
                    Loadout loadout = runtime.loadouts().get(id);
                    if (loadout == null) {
                        entity.sendSystemMessage(Component.literal("Loadout \"" + id + "\" is gone."));
                        return;
                    }
                    LoadoutCompatibility.Result filtered =
                            LoadoutCompatibility.filter(runtime.adapter().registries(), loadout);
                    for (String warning : filtered.warnings()) {
                        entity.sendSystemMessage(Component.literal(warning));
                    }
                    runtime.adapter().players().applyLoadout(ClientScreens263.handle(entity),
                            filtered.loadout());
                    entity.sendSystemMessage(Component.literal("Applied loadout \"" + id + "\"."));
                }));
        smallButton(width / 2 - 155, 176, 150, "Save current", button -> {
            final String name = nameField.getValue().trim();
            ClientScreens263.onServer((server, entity, runtime) -> {
                if (name.isEmpty()) {
                    entity.sendSystemMessage(Component.literal("Enter a name first."));
                    return;
                }
                Loadout captured = runtime.adapter().inventories()
                        .captureLoadout(ClientScreens263.handle(entity), name);
                runtime.loadouts().save(captured);
                runtime.persistLoadouts();
                entity.sendSystemMessage(Component.literal("Saved loadout \"" + name + "\"."));
                reopenOnClient(name, runtime);
            });
        });
        smallButton(width / 2 + 5, 176, 150, "Delete", button ->
                ClientScreens263.onServer((server, entity, runtime) -> {
                    String id = selected();
                    if (id == null) {
                        entity.sendSystemMessage(Component.literal("No loadouts saved yet."));
                        return;
                    }
                    runtime.loadouts().delete(id);
                    runtime.persistLoadouts();
                    entity.sendSystemMessage(Component.literal("Deleted loadout \"" + id + "\"."));
                    reopenOnClient(null, runtime);
                }));
        centeredButton(200, "Back", button -> ClientScreens263.openMain());
    }

    private void reopen(int nextIdx, String keep) {
        final String keepSelected = keep != null ? keep
                : (data.ids.isEmpty() ? null : data.ids.get(nextIdx % data.ids.size()));
        ClientScreens263.onServer((server, entity, runtime) -> reopenOnClient(keepSelected, runtime));
    }

    private static void reopenOnClient(String keepSelected, PracticeRuntime runtime) {
        Data fresh = Data.snapshot(runtime, keepSelected);
        net.minecraft.client.Minecraft.getInstance()
                .execute(() -> ClientScreens263.open(new PracticeLoadoutScreen263(fresh)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        line(graphics, "Name for Save:", 138, 0xA0A0A0);
        int y = 34;
        if (data.ids.isEmpty()) {
            line(graphics, "No loadouts saved yet.", y, 0xA0A0A0);
        } else {
            int shown = Math.min(4, data.ids.size());
            for (int i = 0; i < shown; i++) {
                String marker = (i == data.selectedIdx) ? "> " : "  ";
                line(graphics, marker + data.ids.get(i), y, i == data.selectedIdx ? 0x55FF55 : 0xFFFFFF);
                y += 11;
            }
            if (data.ids.size() > shown) {
                line(graphics, "+" + (data.ids.size() - shown) + " more", y, 0x808080);
            }
        }
    }

}
