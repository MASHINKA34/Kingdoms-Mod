package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.ClientInviteState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class InviteBadgeButton extends KingdomsButton {
    private InviteBadgeButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    public static InviteBadgeButton create(Component message, OnPress onPress, int x, int y, int width, int height) {
        return new InviteBadgeButton(x, y, width, height, message, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (ClientInviteState.pendingInvites() > 0) {
            int right = getX() + getWidth();
            graphics.fill(right - 8, getY() + 1, right - 1, getY() + 8, 0xFF1A140C);
            graphics.fill(right - 7, getY() + 2, right - 2, getY() + 7, 0xFFD8342A);
            graphics.fill(right - 6, getY() + 3, right - 5, getY() + 4, 0xFFFF8A7A);
        }
    }
}
