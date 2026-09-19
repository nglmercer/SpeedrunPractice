package com.gregor0410.speedrunpractice.adapter121.client;

import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.loadout.LoadoutCompatibility;
import com.gregor0410.speedrunpractice.practices.PracticeRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Loadout screen: list, select, save current, apply, delete. */
final class PracticeLoadoutScreen121 extends PracticeScreenBase121 {
    /** Server-thread snapshot backing the screen. */
    static final class Data {
        final List<String> ids;
        final int selectedIdx;

        Data(List<String> ids, int selectedIdx) {
            this.ids = ids;
            this.selectedIdx = selectedIdx;
        }

        static Data snapshot(PracticeRuntime runtime, String keepSelected) {
            List<String> ids = ClientData121.loadoutIds(runtime);
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

    PracticeLoadoutScreen121(Data data) {
        super("Loadouts");
        this.data = data;
    }

    private String selected() {
        return data.ids.isEmpty() ? null : data.ids.get(data.selectedIdx);
    }

    @Override
    protected void init() {
        nameField = new TextFieldWidget(client.textRenderer, width / 2 - 100, 150, 200, 20,
                Text.literal("Loadout name"));
        nameField.setMaxLength(40);
        addDrawableChild(nameField);
        smallButton(width / 2 - 155, 92, 150, "Select: " + (selected() == null ? "(none)" : selected()),
                button -> reopen(selected() == null ? 0 : (data.selectedIdx + 1) % data.ids.size(), null));
        smallButton(width / 2 + 5, 92, 150, "Apply", button ->
                ClientScreens121.onServer((server, entity, runtime) -> {
                    String id = selected();
                    if (id == null) {
                        entity.sendMessage(Text.literal("No loadouts saved yet."), false);
                        return;
                    }
                    Loadout loadout = runtime.loadouts().get(id);
                    if (loadout == null) {
                        entity.sendMessage(Text.literal("Loadout \"" + id + "\" is gone."), false);
                        return;
                    }
                    LoadoutCompatibility.Result filtered =
                            LoadoutCompatibility.filter(runtime.adapter().registries(), loadout);
                    for (String warning : filtered.warnings()) {
                        entity.sendMessage(Text.literal(warning), false);
                    }
                    runtime.adapter().players().applyLoadout(ClientScreens121.handle(entity),
                            filtered.loadout());
                    entity.sendMessage(Text.literal("Applied loadout \"" + id + "\"."), false);
                }));
        smallButton(width / 2 - 155, 176, 150, "Save current", button -> {
            final String name = nameField.getText().trim();
            ClientScreens121.onServer((server, entity, runtime) -> {
                if (name.isEmpty()) {
                    entity.sendMessage(Text.literal("Enter a name first."), false);
                    return;
                }
                Loadout captured = runtime.adapter().inventories()
                        .captureLoadout(ClientScreens121.handle(entity), name);
                runtime.loadouts().save(captured);
                runtime.persistLoadouts();
                entity.sendMessage(Text.literal("Saved loadout \"" + name + "\"."), false);
                reopenOnClient(name, runtime);
            });
        });
        smallButton(width / 2 + 5, 176, 150, "Delete", button ->
                ClientScreens121.onServer((server, entity, runtime) -> {
                    String id = selected();
                    if (id == null) {
                        entity.sendMessage(Text.literal("No loadouts saved yet."), false);
                        return;
                    }
                    runtime.loadouts().delete(id);
                    runtime.persistLoadouts();
                    entity.sendMessage(Text.literal("Deleted loadout \"" + id + "\"."), false);
                    reopenOnClient(null, runtime);
                }));
        centeredButton(200, "Back", button -> ClientScreens121.openMain());
    }

    private void reopen(int nextIdx, String keep) {
        final String keepSelected = keep != null ? keep
                : (data.ids.isEmpty() ? null : data.ids.get(nextIdx % data.ids.size()));
        ClientScreens121.onServer((server, entity, runtime) -> reopenOnClient(keepSelected, runtime));
    }

    private static void reopenOnClient(String keepSelected, PracticeRuntime runtime) {
        Data fresh = Data.snapshot(runtime, keepSelected);
        MinecraftClient.getInstance()
                .execute(() -> ClientScreens121.open(new PracticeLoadoutScreen121(fresh)));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        line(context, "Name for Save:", 138, 0xA0A0A0);
        int y = 34;
        if (data.ids.isEmpty()) {
            line(context, "No loadouts saved yet.", y, 0xA0A0A0);
        } else {
            int shown = Math.min(4, data.ids.size());
            for (int i = 0; i < shown; i++) {
                String marker = (i == data.selectedIdx) ? "> " : "  ";
                line(context, marker + data.ids.get(i), y, i == data.selectedIdx ? 0x55FF55 : 0xFFFFFF);
                y += 11;
            }
            if (data.ids.size() > shown) {
                line(context, "+" + (data.ids.size() - shown) + " more", y, 0x808080);
            }
        }
    }

}
