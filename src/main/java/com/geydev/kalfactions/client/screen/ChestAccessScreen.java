package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.chest.ChestAccessMode;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ChestAccessScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 3;
    private static final long NOTICE_MILLIS = 2600L;

    private final BlockPos pos;
    private ChestAccessMode mode;
    private List<FactionPayloads.ChestWhitelistEntry> whitelist;
    private List<String> candidates;
    private String noticeText = "";
    private boolean noticeSuccess = true;
    private long noticeShownAt;

    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int listTop;
    private int scrollOffset;

    public ChestAccessScreen(FactionPayloads.S2CChestAccessState state) {
        super(Component.translatable("screen.kingdoms.chest_access.title"));
        this.pos = state.pos();
        applyState(state);
    }

    public BlockPos pos() {
        return pos;
    }

    public void acceptState(FactionPayloads.S2CChestAccessState state) {
        applyState(state);
        if (!state.notice().getString().isBlank()) {
            noticeText = state.notice().getString();
            noticeSuccess = state.successful();
            noticeShownAt = System.currentTimeMillis();
        }
        rebuildWidgets();
    }

    private void applyState(FactionPayloads.S2CChestAccessState state) {
        ChestAccessMode parsed;
        try {
            parsed = ChestAccessMode.valueOf(state.mode().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            parsed = ChestAccessMode.FACTION;
        }
        this.mode = parsed;
        this.whitelist = state.whitelist();
        this.candidates = state.candidates();
    }

    @Override
    protected void init() {
        boolean showList = mode == ChestAccessMode.WHITELIST;
        int listRows = showList ? Math.max(1, Math.min(VISIBLE_ROWS, Math.max(1, whitelist.size()))) : 0;
        int listBlock = showList ? 12 + listRows * ROW_HEIGHT + 26 : 0;
        panelHeight = 26 + 12 + 48 + listBlock + 50;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());

        int buttonWidth = (PANEL_WIDTH - 16 - 8) / 2;
        int modeTop = panelTop + 38;
        addModeButton(ChestAccessMode.PERSONAL, panelLeft + 8, modeTop, buttonWidth);
        addModeButton(ChestAccessMode.FACTION, panelLeft + 8 + buttonWidth + 8, modeTop, buttonWidth);
        addModeButton(ChestAccessMode.PUBLIC, panelLeft + 8, modeTop + 24, buttonWidth);
        addModeButton(ChestAccessMode.WHITELIST, panelLeft + 8 + buttonWidth + 8, modeTop + 24, buttonWidth);

        listTop = modeTop + 48 + 12;
        if (showList) {
            int addTop = listTop + listRows * ROW_HEIGHT + 4;
            KingdomsButton add = KingdomsButton.create(
                    Component.translatable("screen.kingdoms.chest_access.add"),
                    button -> openPicker(),
                    panelLeft + 8, addTop, PANEL_WIDTH - 16, 20
            );
            add.active = !candidates.isEmpty();
            addRenderableWidget(add);
        }

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                panelLeft + (PANEL_WIDTH - 90) / 2, panelTop + panelHeight - 26, 90, 20
        ));
    }

    private void addModeButton(ChestAccessMode value, int x, int y, int width) {
        Component label = Component.translatable("kingdoms.access." + value.name().toLowerCase(Locale.ROOT));
        if (value == mode) {
            label = Component.literal("✔ ").append(label);
        }
        KingdomsButton button = KingdomsButton.create(
                label,
                pressed -> PacketDistributor.sendToServer(new FactionPayloads.C2SSetChestMode(pos, value.name())),
                x, y, width, 20
        );
        button.active = value != mode;
        addRenderableWidget(button);
    }

    private void openPicker() {
        List<SelectEntryScreen.Entry> entries = candidates.stream()
                .map(name -> SelectEntryScreen.Entry.player(name, null, true))
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                Component.translatable("screen.kingdoms.chest_access.pick"),
                entries,
                null,
                entry -> PacketDistributor.sendToServer(new FactionPayloads.C2SEditChestWhitelist(
                        pos, true, Util.NIL_UUID, entry.value()
                ))
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 9, 0xFFF3D58B, true);
        graphics.drawString(font, Component.translatable("screen.kingdoms.chest_access.mode"),
                panelLeft + 8, panelTop + 27, 0xFF9A8F7A, true);

        if (mode == ChestAccessMode.WHITELIST) {
            graphics.drawString(font, Component.translatable("screen.kingdoms.chest_access.whitelist"),
                    panelLeft + 8, listTop - 11, 0xFF9A8F7A, true);
            int shown = Math.min(VISIBLE_ROWS, whitelist.size() - scrollOffset);
            for (int index = 0; index < shown; index++) {
                renderRow(graphics, whitelist.get(scrollOffset + index), rowTop(index), mouseX, mouseY);
            }
            if (whitelist.isEmpty()) {
                Component empty = Component.translatable("screen.kingdoms.chest_access.empty");
                graphics.drawString(font, empty, panelLeft + (PANEL_WIDTH - font.width(empty)) / 2,
                        listTop + 7, 0xFF8E8B83, true);
            }
            if (whitelist.size() > VISIBLE_ROWS) {
                String pager = (scrollOffset + 1) + "–" + (scrollOffset + shown) + " / " + whitelist.size();
                graphics.drawString(font, pager, panelLeft + PANEL_WIDTH - 12 - font.width(pager),
                        listTop - 11, 0xFF9A8F7A, true);
            }
        }

        if (!noticeText.isEmpty() && System.currentTimeMillis() - noticeShownAt < NOTICE_MILLIS) {
            String clipped = font.plainSubstrByWidth(noticeText, PANEL_WIDTH - 16);
            int textWidth = font.width(clipped);
            int color = noticeSuccess ? 0xFFC9F0B8 : 0xFFE89090;
            graphics.drawString(font, clipped, panelLeft + (PANEL_WIDTH - textWidth) / 2,
                    panelTop + panelHeight - 40, color, true);
        }
    }

    private void renderRow(
            GuiGraphics graphics,
            FactionPayloads.ChestWhitelistEntry entry,
            int rowTop,
            int mouseX,
            int mouseY
    ) {
        int rowLeft = panelLeft + 8;
        int rowRight = panelLeft + PANEL_WIDTH - 8;
        graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_HEIGHT - 2, 0x28000000);

        PlayerSkin skin = resolveSkin(entry.name());
        PlayerFaceRenderer.draw(graphics, skin, rowLeft + 3, rowTop + 2, 16);
        graphics.drawString(font, entry.name(), rowLeft + 24, rowTop + 6, 0xFFFFFFFF, true);

        boolean removeHovered = isRemoveHovered(rowTop, mouseX, mouseY);
        int removeColor = removeHovered ? 0xFFFF6B6B : 0xFFB05050;
        graphics.drawString(font, "✕", rowRight - 12, rowTop + 6, removeColor, true);
    }

    private boolean isRemoveHovered(int rowTop, int mouseX, int mouseY) {
        int rowRight = panelLeft + PANEL_WIDTH - 8;
        return mouseX >= rowRight - 16 && mouseX < rowRight
                && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT - 2;
    }

    private PlayerSkin resolveSkin(String playerName) {
        if (minecraft != null && minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerName);
            if (info != null) {
                return info.getSkin();
            }
        }
        return DefaultPlayerSkin.get(Util.NIL_UUID);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mode == ChestAccessMode.WHITELIST) {
            int shown = Math.min(VISIBLE_ROWS, whitelist.size() - scrollOffset);
            for (int index = 0; index < shown; index++) {
                int rowTop = rowTop(index);
                if (isRemoveHovered(rowTop, (int) mouseX, (int) mouseY)) {
                    FactionPayloads.ChestWhitelistEntry entry = whitelist.get(scrollOffset + index);
                    PacketDistributor.sendToServer(new FactionPayloads.C2SEditChestWhitelist(
                            pos, false, entry.id(), entry.name()
                    ));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mode == ChestAccessMode.WHITELIST) {
            int updated = Math.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll());
            if (updated != scrollOffset) {
                scrollOffset = updated;
                return true;
            }
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
    public boolean isPauseScreen() {
        return false;
    }

    private int rowTop(int visibleIndex) {
        return listTop + visibleIndex * ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, whitelist.size() - VISIBLE_ROWS);
    }
}
