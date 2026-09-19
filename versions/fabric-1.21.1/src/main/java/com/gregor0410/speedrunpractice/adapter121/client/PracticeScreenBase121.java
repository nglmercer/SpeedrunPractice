package com.gregor0410.speedrunpractice.adapter121.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Shared base for the practice screens: no-pause (single-player keeps
 * running behind the menu), a centered title, and small button helpers.
 */
abstract class PracticeScreenBase121 extends Screen {
    protected final String heading;

    protected PracticeScreenBase121(String heading) {
        super(Text.literal(heading));
        this.heading = heading;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected ButtonWidget centeredButton(int y, String label, ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(width / 2 - 100, y, 200, 20).build());
    }

    protected ButtonWidget smallButton(int x, int y, int w, String label,
            ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, w, 20).build());
    }

    protected void line(DrawContext context, String text, int y, int color) {
        TextRenderer renderer = client == null ? null : client.textRenderer;
        if (renderer != null) {
            context.drawCenteredTextWithShadow(renderer, Text.literal(text), width / 2, y, color);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        line(context, heading, 15, 0xFFFFFF);
    }
}
