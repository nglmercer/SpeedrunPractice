package com.gregor0410.speedrunpractice.adapter116.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * Shared base for the practice screens: no-pause (single-player keeps
 * running behind the menu), a centered title, and small button helpers.
 */
abstract class PracticeScreenBase extends Screen {
    protected final String heading;

    protected PracticeScreenBase(String heading) {
        super(new LiteralText(heading));
        this.heading = heading;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected ButtonWidget centeredButton(int y, String label, ButtonWidget.PressAction action) {
        return addButton(new ButtonWidget(width / 2 - 100, y, 200, 20, new LiteralText(label), action));
    }

    protected ButtonWidget smallButton(int x, int y, int w, String label, ButtonWidget.PressAction action) {
        return addButton(new ButtonWidget(x, y, w, 20, new LiteralText(label), action));
    }

    protected void line(MatrixStack matrices, String text, int y, int color) {
        drawCenteredString(matrices, textRenderer, text, width / 2, y, color);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredString(matrices, textRenderer, heading, width / 2, 15, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
