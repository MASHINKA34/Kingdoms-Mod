package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.market.MarketPayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlotTrustScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 6;

    private final int plotId;
    private List<MarketPayloads.TrustEntry> trustedPlayers;
    private List<MarketPayloads.TrustEntry> trustedFactions;
    private List<MarketPayloads.PlayerCandidate> playerCandidates;
    private List<MarketPayloads.TrustEntry> factionCandidates;
    private List<Row> rows = List.of();

    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int listTop;
    private int scrollOffset;

    private PlotTrustScreen(MarketPayloads.S2CPlotTrustState state) {
        super(Component.translatable("screen.kingdoms.plot_trust_title", state.plotId()));
        this.plotId = state.plotId();
        applyState(state);
    }

    public static void handle(MarketPayloads.S2CPlotTrustState payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof PlotTrustScreen screen && screen.plotId == payload.plotId()) {
                screen.acceptState(payload);
            } else {
                minecraft.setScreen(new PlotTrustScreen(payload));
            }
        });
    }

    private void acceptState(MarketPayloads.S2CPlotTrustState state) {
        applyState(state);
        rebuildWidgets();
    }

    private void applyState(MarketPayloads.S2CPlotTrustState state) {
        this.trustedPlayers = state.trustedPlayers();
        this.trustedFactions = state.trustedFactions();
        this.playerCandidates = state.playerCandidates();
        this.factionCandidates = state.factionCandidates();
        List<Row> built = new ArrayList<>();
        built.add(Row.header(Component.translatable("screen.kingdoms.plot_trust_factions")));
        if (trustedFactions.isEmpty()) {
            built.add(Row.empty());
        } else {
            for (MarketPayloads.TrustEntry entry : trustedFactions) {
                built.add(Row.faction(entry));
            }
        }
        built.add(Row.header(Component.translatable("screen.kingdoms.plot_trust_players")));
        if (trustedPlayers.isEmpty()) {
            built.add(Row.empty());
        } else {
            for (MarketPayloads.TrustEntry entry : trustedPlayers) {
                built.add(Row.player(entry));
            }
        }
        this.rows = List.copyOf(built);
    }

    @Override
    protected void init() {
        int listRows = Math.min(VISIBLE_ROWS, rows.size());
        panelHeight = 26 + listRows * ROW_HEIGHT + 8 + 24 + 34;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;
        listTop = panelTop + 26;
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());

        int buttonWidth = (PANEL_WIDTH - 16 - 8) / 2;
        int buttonsTop = listTop + listRows * ROW_HEIGHT + 8;
        KingdomsButton addFaction = KingdomsButton.create(
                Component.translatable("screen.kingdoms.plot_trust_add_faction"),
                button -> openFactionPicker(),
                panelLeft + 8, buttonsTop, buttonWidth, 20
        );
        addFaction.active = !factionCandidates.isEmpty();
        addRenderableWidget(addFaction);

        KingdomsButton addPlayer = KingdomsButton.create(
                Component.translatable("screen.kingdoms.plot_trust_add_player"),
                button -> openPlayerPicker(),
                panelLeft + 8 + buttonWidth + 8, buttonsTop, buttonWidth, 20
        );
        addPlayer.active = !playerCandidates.isEmpty();
        addRenderableWidget(addPlayer);

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                panelLeft + (PANEL_WIDTH - 90) / 2, panelTop + panelHeight - 26, 90, 20
        ));
    }

    private void openFactionPicker() {
        List<SelectEntryScreen.Entry> entries = factionCandidates.stream()
                .map(candidate -> SelectEntryScreen.Entry.swatch(candidate.name(), candidate.color()))
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                Component.translatable("screen.kingdoms.plot_trust_pick_faction"),
                entries,
                null,
                entry -> factionCandidates.stream()
                        .filter(candidate -> candidate.name().equals(entry.value()))
                        .findFirst()
                        .ifPresent(candidate -> PacketDistributor.sendToServer(
                                new MarketPayloads.C2SEditPlotTrust(plotId, true, true, candidate.id())))
        ));
    }

    private void openPlayerPicker() {
        List<SelectEntryScreen.Entry> entries = playerCandidates.stream()
                .map(candidate -> SelectEntryScreen.Entry.player(
                        candidate.name(),
                        candidate.factionName().isEmpty()
                                ? null
                                : Component.literal(candidate.factionName()),
                        true))
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                Component.translatable("screen.kingdoms.plot_trust_pick_player"),
                entries,
                null,
                entry -> playerCandidates.stream()
                        .filter(candidate -> candidate.name().equals(entry.value()))
                        .findFirst()
                        .ifPresent(candidate -> PacketDistributor.sendToServer(
                                new MarketPayloads.C2SEditPlotTrust(plotId, true, false, candidate.id())))
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 9, 0xFFF3D58B, true);

        int shown = Math.min(VISIBLE_ROWS, rows.size() - scrollOffset);
        for (int index = 0; index < shown; index++) {
            renderRow(graphics, rows.get(scrollOffset + index), rowTop(index), mouseX, mouseY);
        }
        if (rows.size() > VISIBLE_ROWS) {
            String pager = (scrollOffset + 1) + "–" + (scrollOffset + shown) + " / " + rows.size();
            graphics.drawString(font, pager, panelLeft + PANEL_WIDTH - 8 - font.width(pager),
                    panelTop + 10, 0xFF9A8F7A, true);
        }
    }

    private void renderRow(GuiGraphics graphics, Row row, int rowTop, int mouseX, int mouseY) {
        int rowLeft = panelLeft + 8;
        int rowRight = panelLeft + PANEL_WIDTH - 8;
        switch (row.kind) {
            case HEADER -> graphics.drawString(font, row.label, rowLeft, rowTop + 8, 0xFF9A8F7A, true);
            case EMPTY -> graphics.drawString(font,
                    Component.translatable("screen.kingdoms.plot_trust_empty"),
                    rowLeft + 8, rowTop + 7, 0xFF8E8B83, true);
            case FACTION, PLAYER -> {
                graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_HEIGHT - 2, 0x28000000);
                if (row.kind == Kind.PLAYER) {
                    PlayerSkin skin = resolveSkin(row.entry.name());
                    PlayerFaceRenderer.draw(graphics, skin, rowLeft + 3, rowTop + 2, 16);
                } else {
                    graphics.fill(rowLeft + 3, rowTop + 2, rowLeft + 19, rowTop + 18, 0xFF1A140C);
                    graphics.fill(rowLeft + 4, rowTop + 3, rowLeft + 18, rowTop + 17,
                            0xFF000000 | row.entry.color());
                }
                graphics.drawString(font, row.entry.name(), rowLeft + 24, rowTop + 6, 0xFFFFFFFF, true);
                boolean removeHovered = isRemoveHovered(rowTop, mouseX, mouseY);
                int removeColor = removeHovered ? 0xFFFF6B6B : 0xFFB05050;
                graphics.drawString(font, "✕", rowRight - 12, rowTop + 6, removeColor, true);
            }
        }
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
        if (button == 0) {
            int shown = Math.min(VISIBLE_ROWS, rows.size() - scrollOffset);
            for (int index = 0; index < shown; index++) {
                Row row = rows.get(scrollOffset + index);
                if ((row.kind == Kind.FACTION || row.kind == Kind.PLAYER)
                        && isRemoveHovered(rowTop(index), (int) mouseX, (int) mouseY)) {
                    PacketDistributor.sendToServer(new MarketPayloads.C2SEditPlotTrust(
                            plotId, false, row.kind == Kind.FACTION, row.entry.id()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
    public boolean isPauseScreen() {
        return false;
    }

    private int rowTop(int visibleIndex) {
        return listTop + visibleIndex * ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - VISIBLE_ROWS);
    }

    private enum Kind {
        HEADER,
        EMPTY,
        FACTION,
        PLAYER
    }

    private record Row(Kind kind, Component label, MarketPayloads.TrustEntry entry) {
        static Row header(Component label) {
            return new Row(Kind.HEADER, label, null);
        }

        static Row empty() {
            return new Row(Kind.EMPTY, null, null);
        }

        static Row faction(MarketPayloads.TrustEntry entry) {
            return new Row(Kind.FACTION, null, entry);
        }

        static Row player(MarketPayloads.TrustEntry entry) {
            return new Row(Kind.PLAYER, null, entry);
        }
    }
}
