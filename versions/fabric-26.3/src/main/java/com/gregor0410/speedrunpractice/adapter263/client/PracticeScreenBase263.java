package com.gregor0410.speedrunpractice.adapter263.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared base for the practice screens: no-pause (single-player keeps
 * running behind the menu), a centered title, and small button helpers.
 */
abstract class PracticeScreenBase263 extends Screen {
    protected final String heading;

    protected PracticeScreenBase263(String heading) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal(heading));
        this.heading = heading;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected Button centeredButton(int y, String label, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action)
                .bounds(width / 2 - 100, y, 200, 20).build());
    }

    protected Button smallButton(int x, int y, int w, String label, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action)
                .bounds(x, y, w, 20).build());
    }

    protected void line(GuiGraphicsExtractor graphics, String text, int y, int color) {
        graphics.centeredText(font, text, width / 2, y, color);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, heading, width / 2, 15, 0xFFFFFF);
    }
}
