package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.EmblemTextures;
import com.geydev.kalfactions.net.FactionPayloads;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class FactionDetailsScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 4;
    private static final int LIST_TOP_OFFSET = 76;
    private static final long FADE_IN_MILLIS = 200L;

    private final Screen parent;
    private final FactionPayloads.FactionInfo info;
    private final long openedAt = System.currentTimeMillis();

    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int scrollOffset;

    public FactionDetailsScreen(Screen parent, FactionPayloads.FactionInfo info) {
        super(Component.literal(info.name()));
        this.parent = parent;
        this.info = info;
    }

    @Override
    protected void init() {
        int rows = Math.min(VISIBLE_ROWS, Math.max(1, info.members().size()));
        panelHeight = LIST_TOP_OFFSET + rows * ROW_HEIGHT + 36;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.back"),
                button -> onClose(),
                panelLeft + (PANEL_WIDTH - 90) / 2, panelTop + panelHeight - 28, 90, 20
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        float progress = Mth.clamp((System.currentTimeMillis() - openedAt) / (float) FADE_IN_MILLIS, 0.0F, 1.0F);
        int alpha = Math.max(16, (int) (progress * 255.0F)) << 24;

        graphics.fill(panelLeft + 9, panelTop + 7, panelLeft + 31, panelTop + 29, 0xFF1A140C);
        EmblemTextures.Emblem emblem = EmblemTextures.resolve(info.id(), info.emblem(), info.emblemUrl());
        if (emblem != null) {
            graphics.blit(emblem.texture(), panelLeft + 10, panelTop + 8, 20, 20,
                    0.0F, 0.0F, emblem.width(), emblem.height(), emblem.width(), emblem.height());
        } else {
            graphics.fill(panelLeft + 10, panelTop + 8, panelLeft + 30, panelTop + 28, 0xFF000000 | info.color());
        }
        graphics.drawString(font, title, panelLeft + 38, panelTop + 9, alpha | 0xF3D58B, true);

        Component stats = Component.translatable(
                "screen.kingdoms.flist.stats",
                info.memberCount(),
                info.influence()
        );
        graphics.drawString(font, stats, panelLeft + 38, panelTop + 20, alpha | 0x9A8F7A, true);

        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.flist.bonuses", bonusLine()),
                panelLeft + 10,
                panelTop + 36,
                alpha | 0xC9C0AC,
                true
        );
        Component status = info.warWith().isEmpty()
                ? Component.translatable("screen.kingdoms.flist.peace")
                : Component.translatable("screen.kingdoms.flist.war", info.warWith());
        graphics.drawString(font, status, panelLeft + 10, panelTop + 48,
                alpha | (info.warWith().isEmpty() ? 0x9FD08C : 0xE89090), true);
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.kingdoms.flist.alliance",
                        info.allies().isEmpty() ? "-" : String.join(", ", info.allies())
                ),
                panelLeft + 10,
                panelTop + 60,
                alpha | 0x9FD08C,
                true
        );

        int shown = Math.min(VISIBLE_ROWS, info.members().size() - scrollOffset);
        for (int index = 0; index < shown; index++) {
            renderMemberRow(graphics, info.members().get(scrollOffset + index), rowTop(index), mouseX, mouseY);
        }
        if (info.members().size() > VISIBLE_ROWS) {
            String pager = (scrollOffset + 1) + "–" + (scrollOffset + shown) + " / " + info.members().size();
            graphics.drawString(font, pager, panelLeft + PANEL_WIDTH - 12 - font.width(pager),
                    panelTop + 9, 0xFF9A8F7A, true);
        }
    }

    private String bonusLine() {
        StringBuilder joined = new StringBuilder();
        for (String bonus : info.bonuses()) {
            if (!joined.isEmpty()) {
                joined.append(", ");
            }
            joined.append(net.minecraft.client.resources.language.I18n.get(
                    "kingdoms.bonus." + bonus.toLowerCase(Locale.ROOT)));
        }
        return joined.toString();
    }

    private void renderMemberRow(
            GuiGraphics graphics,
            FactionPayloads.MemberInfo member,
            int rowTop,
            int mouseX,
            int mouseY
    ) {
        int rowLeft = panelLeft + 8;
        int rowRight = panelLeft + PANEL_WIDTH - 8;
        boolean hovered = mouseX >= rowLeft && mouseX < rowRight
                && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT - 2;
        graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_HEIGHT - 2, hovered ? 0x3CFFFFFF : 0x28000000);

        PlayerSkin skin = resolveSkin(member.name());
        PlayerFaceRenderer.draw(graphics, skin, rowLeft + 4, rowTop + 3, 16);
        graphics.drawString(font, member.name(), rowLeft + 26, rowTop + 3, 0xFFFFFFFF, true);
        graphics.drawString(font, Component.translatable(member.role()), rowLeft + 26, rowTop + 13, 0xFF9A8F7A, true);
    }

    private PlayerSkin resolveSkin(String playerName) {
        if (minecraft != null && minecraft.getConnection() != null) {
            PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(playerName);
            if (playerInfo != null) {
                return playerInfo.getSkin();
            }
        }
        return DefaultPlayerSkin.get(Util.NIL_UUID);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int updated = Math.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll());
        if (updated != scrollOffset) {
            scrollOffset = updated;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + panelHeight + 1, 0xFFC9A24C);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + panelHeight, 0xFF2B2E38);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int rowTop(int visibleIndex) {
        return panelTop + LIST_TOP_OFFSET + visibleIndex * ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, info.members().size() - VISIBLE_ROWS);
    }
}
