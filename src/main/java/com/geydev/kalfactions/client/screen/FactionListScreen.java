package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.EmblemTextures;
import com.geydev.kalfactions.net.FactionPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionListScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int CONTENT_RIGHT = 298;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF4C3824;
    private static final int ROW_HEIGHT = 30;
    private static final int VISIBLE_ROWS = 3;
    private static final long FADE_IN_MILLIS = 250L;

    private enum Tab {
        FACTIONS,
        INVITES
    }

    private enum InviteSection {
        FACTION,
        ALLIANCE
    }

    private final long openedAt = System.currentTimeMillis();
    private List<FactionPayloads.FactionInfo> factions = List.of();
    private List<FactionPayloads.InviteInfo> invites = List.of();
    private List<FactionPayloads.AllianceInviteInfo> allianceInvites = List.of();
    private List<Object> inviteRows = List.of();
    private boolean loaded;
    private Tab tab = Tab.FACTIONS;
    private int scrollOffset;
    private String noticeText = "";
    private boolean noticeSuccess = true;
    private long noticeShownAt;

    private int left;
    private int top;
    private int listTop;

    public FactionListScreen() {
        super(Component.translatable("screen.kingdoms.factions_list"));
    }

    public void showNotice(Component message, boolean successful) {
        if (message == null || message.getString().isBlank()) {
            return;
        }
        noticeText = message.getString();
        noticeSuccess = successful;
        noticeShownAt = System.currentTimeMillis();
    }

    public void acceptData(FactionPayloads.S2CFactionList payload) {
        factions = payload.factions();
        invites = payload.invites();
        allianceInvites = payload.allianceInvites();
        rebuildInviteRows();
        loaded = true;
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        listTop = top + 82;

        KingdomsButton factionsTab = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.tab_factions"),
                button -> switchTab(Tab.FACTIONS),
                left + CONTENT_LEFT, top + 58, 131, 18
        ));
        factionsTab.active = tab != Tab.FACTIONS;

        int inviteCount = invites.size() + allianceInvites.size();
        Component invitesLabel = inviteCount == 0
                ? Component.translatable("screen.kingdoms.tab_invites")
                : Component.translatable("screen.kingdoms.tab_invites_count", inviteCount);
        KingdomsButton invitesTab = addRenderableWidget(KingdomsButton.create(
                invitesLabel,
                button -> switchTab(Tab.INVITES),
                left + 167, top + 58, 131, 18
        ));
        invitesTab.active = tab != Tab.INVITES;

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74, top + PANEL_HEIGHT - 25, 66, 20
        ));
    }

    private void switchTab(Tab newTab) {
        tab = newTab;
        scrollOffset = 0;
        rebuildWidgets();
    }

    private List<?> currentRows() {
        return tab == Tab.FACTIONS ? factions : inviteRows;
    }

    private void rebuildInviteRows() {
        List<Object> rows = new ArrayList<>();
        if (!invites.isEmpty()) {
            rows.add(InviteSection.FACTION);
            rows.addAll(invites);
        }
        if (!allianceInvites.isEmpty()) {
            rows.add(InviteSection.ALLIANCE);
            rows.addAll(allianceInvites);
        }
        inviteRows = List.copyOf(rows);
    }

    private int maxScroll() {
        return Math.max(0, currentRows().size() - VISIBLE_ROWS);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = font.width(title);
        graphics.drawString(font, title, left + (PANEL_WIDTH - titleWidth) / 2, top + 48, TEXT_DARK, false);

        float progress = Mth.clamp((System.currentTimeMillis() - openedAt) / (float) FADE_IN_MILLIS, 0.0F, 1.0F);
        int alpha = Math.max(16, (int) (progress * 255.0F));
        int slide = (int) ((1.0F - progress) * 8.0F);

        if (!loaded) {
            Component loading = Component.translatable("screen.kingdoms.flist.loading");
            graphics.drawString(font, loading, left + (PANEL_WIDTH - font.width(loading)) / 2,
                    listTop + 24, TEXT_MUTED, false);
            return;
        }

        List<?> rows = currentRows();
        if (rows.isEmpty()) {
            Component empty = Component.translatable(tab == Tab.FACTIONS
                    ? "screen.kingdoms.flist.empty"
                    : "screen.kingdoms.flist.no_invites");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2,
                    listTop + 24, TEXT_MUTED, false);
            return;
        }

        int shown = Math.min(VISIBLE_ROWS, rows.size() - scrollOffset);
        for (int index = 0; index < shown; index++) {
            int rowTop = listTop + index * ROW_HEIGHT + slide;
            if (tab == Tab.FACTIONS) {
                renderFactionRow(graphics, factions.get(scrollOffset + index), rowTop, mouseX, mouseY, alpha);
            } else {
                Object row = inviteRows.get(scrollOffset + index);
                if (row instanceof InviteSection section) {
                    renderInviteSection(graphics, section, rowTop, alpha);
                } else if (row instanceof FactionPayloads.InviteInfo invite) {
                    renderInviteRow(graphics, invite, rowTop, mouseX, mouseY, alpha);
                } else if (row instanceof FactionPayloads.AllianceInviteInfo allianceInvite) {
                    renderAllianceInviteRow(graphics, allianceInvite, rowTop, mouseX, mouseY, alpha);
                }
            }
        }
        if (rows.size() > VISIBLE_ROWS) {
            String pager = (scrollOffset + 1) + "–" + (scrollOffset + shown) + " / " + rows.size();
            graphics.drawString(font, pager, left + CONTENT_RIGHT - font.width(pager), top + 48, TEXT_MUTED, false);
        }
        if (!noticeText.isEmpty() && System.currentTimeMillis() - noticeShownAt < 3000L) {
            String clipped = font.plainSubstrByWidth(noticeText, PANEL_WIDTH - 90);
            int color = noticeSuccess ? 0xFF3F6B33 : 0xFF8C2B2B;
            graphics.drawString(font, clipped, left + CONTENT_LEFT, top + PANEL_HEIGHT - 19, color, false);
        }
    }

    private void renderFactionRow(
            GuiGraphics graphics,
            FactionPayloads.FactionInfo info,
            int rowTop,
            int mouseX,
            int mouseY,
            int alpha
    ) {
        int rowLeft = left + CONTENT_LEFT;
        int rowRight = left + CONTENT_RIGHT;
        boolean hovered = mouseX >= rowLeft && mouseX < rowRight
                && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT - 2;
        graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_HEIGHT - 2,
                (hovered ? 0x58 : 0x28) << 24 | 0xFFF3D5);

        renderEmblem(graphics, info.id(), info.emblem(), info.emblemUrl(), info.color(), rowLeft + 3, rowTop + 4);

        int textAlpha = alpha << 24;
        graphics.drawString(font, info.name(), rowLeft + 28, rowTop + 5, textAlpha | (TEXT_DARK & 0xFFFFFF), false);
        Component membersLabel = Component.translatable("screen.kingdoms.flist.members", info.memberCount());
        graphics.drawString(font, membersLabel, rowRight - 6 - font.width(membersLabel), rowTop + 5,
                textAlpha | (TEXT_MUTED & 0xFFFFFF), false);
        Component status = info.warWith().isEmpty()
                ? Component.translatable("screen.kingdoms.flist.peace")
                : Component.translatable("screen.kingdoms.flist.war", info.warWith());
        Component alliance = Component.translatable(
                "screen.kingdoms.flist.alliance",
                info.allies().isEmpty() ? "-" : String.join(", ", info.allies())
        );
        String statusLine = status.getString() + " | " + alliance.getString();
        String clipped = font.plainSubstrByWidth(statusLine, rowRight - rowLeft - 34);
        int statusColor = info.warWith().isEmpty() ? 0x3F6B33 : 0x8C2B2B;
        graphics.drawString(font, clipped, rowLeft + 28, rowTop + 17, textAlpha | statusColor, false);
    }

    private void renderInviteSection(GuiGraphics graphics, InviteSection section, int rowTop, int alpha) {
        Component label = Component.translatable(section == InviteSection.FACTION
                ? "screen.kingdoms.invites.factions"
                : "screen.kingdoms.invites.alliances");
        int color = alpha << 24 | (TEXT_DARK & 0xFFFFFF);
        graphics.drawString(font, label, left + CONTENT_LEFT + 4, rowTop + 10, color, false);
        graphics.fill(
                left + CONTENT_LEFT + 4 + font.width(label) + 5,
                rowTop + 14,
                left + CONTENT_RIGHT,
                rowTop + 15,
                color
        );
    }

    private void renderInviteRow(
            GuiGraphics graphics,
            FactionPayloads.InviteInfo info,
            int rowTop,
            int mouseX,
            int mouseY,
            int alpha
    ) {
        int rowLeft = left + CONTENT_LEFT;
        int rowRight = left + CONTENT_RIGHT;
        graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_HEIGHT - 2, 0x28 << 24 | 0xFFF3D5);

        renderEmblem(graphics, info.factionId(), info.emblem(), info.emblemUrl(), info.color(), rowLeft + 3, rowTop + 4);

        int textAlpha = alpha << 24;
        String name = info.factionName() + " (" + info.memberCount() + ")";
        graphics.drawString(font, name, rowLeft + 28, rowTop + 5, textAlpha | (TEXT_DARK & 0xFFFFFF), false);
        graphics.drawString(font, bonusLine(info.bonuses()), rowLeft + 28, rowTop + 17,
                textAlpha | (TEXT_MUTED & 0xFFFFFF), false);

        renderMiniButton(graphics, acceptZone(rowTop),
                Component.translatable("screen.kingdoms.accept"), 0xFF2F5B27, 0xFF3F7B33, mouseX, mouseY);
        renderMiniButton(graphics, declineZone(rowTop),
                Component.translatable("screen.kingdoms.decline"), 0xFF6B2424, 0xFF8C2B2B, mouseX, mouseY);
    }

    private void renderAllianceInviteRow(
            GuiGraphics graphics,
            FactionPayloads.AllianceInviteInfo info,
            int rowTop,
            int mouseX,
            int mouseY,
            int alpha
    ) {
        int rowLeft = left + CONTENT_LEFT;
        int rowRight = left + CONTENT_RIGHT;
        graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_HEIGHT - 2, 0x28 << 24 | 0xFFF3D5);

        renderEmblem(graphics, info.factionId(), info.emblem(), info.emblemUrl(), info.color(), rowLeft + 3, rowTop + 4);

        int textAlpha = alpha << 24;
        String name = info.factionName() + " (" + info.memberCount() + ")";
        graphics.drawString(font, name, rowLeft + 28, rowTop + 5, textAlpha | (TEXT_DARK & 0xFFFFFF), false);
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.alliance.requester", info.requesterName()),
                rowLeft + 28,
                rowTop + 17,
                textAlpha | (TEXT_MUTED & 0xFFFFFF),
                false
        );

        renderMiniButton(graphics, acceptZone(rowTop),
                Component.translatable("screen.kingdoms.accept"), 0xFF2F5B27, 0xFF3F7B33, mouseX, mouseY);
        renderMiniButton(graphics, declineZone(rowTop),
                Component.translatable("screen.kingdoms.decline"), 0xFF6B2424, 0xFF8C2B2B, mouseX, mouseY);
    }

    private Component bonusLine(List<String> bonuses) {
        if (bonuses.isEmpty()) {
            return Component.empty();
        }
        Component joined = null;
        for (String bonus : bonuses) {
            Component label = Component.translatable("kingdoms.bonus." + bonus.toLowerCase(Locale.ROOT));
            joined = joined == null
                    ? label
                    : Component.empty().append(joined).append(", ").append(label);
        }
        return joined;
    }

    private void renderEmblem(
            GuiGraphics graphics,
            java.util.UUID factionId,
            List<Integer> emblem,
            String emblemUrl,
            int color,
            int x,
            int y
    ) {
        graphics.fill(x - 1, y - 1, x + 21, y + 21, 0xFF1A140C);
        EmblemTextures.Emblem resolved = EmblemTextures.resolve(factionId, emblem, emblemUrl);
        if (resolved != null) {
            graphics.blit(resolved.texture(), x, y, 20, 20,
                    0.0F, 0.0F, resolved.width(), resolved.height(),
                    resolved.width(), resolved.height());
        } else {
            graphics.fill(x, y, x + 20, y + 20, 0xFF000000 | color);
        }
    }

    private void renderMiniButton(
            GuiGraphics graphics,
            int[] zone,
            Component label,
            int base,
            int hover,
            int mouseX,
            int mouseY
    ) {
        boolean hovered = mouseX >= zone[0] && mouseX < zone[2] && mouseY >= zone[1] && mouseY < zone[3];
        graphics.fill(zone[0], zone[1], zone[2], zone[3], hovered ? hover : base);
        String clipped = font.plainSubstrByWidth(label.getString(), zone[2] - zone[0] - 4);
        int textX = zone[0] + (zone[2] - zone[0] - font.width(clipped)) / 2;
        int textY = zone[1] + (zone[3] - zone[1] - 8) / 2;
        graphics.drawString(font, clipped, textX, textY, 0xFFF6E6C5, true);
    }

    private int[] acceptZone(int rowTop) {
        int rowRight = left + CONTENT_RIGHT;
        return new int[] {rowRight - 122, rowTop + 8, rowRight - 64, rowTop + 22};
    }

    private int[] declineZone(int rowTop) {
        int rowRight = left + CONTENT_RIGHT;
        return new int[] {rowRight - 60, rowTop + 8, rowRight - 4, rowTop + 22};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && loaded) {
            List<?> rows = currentRows();
            int shown = Math.min(VISIBLE_ROWS, rows.size() - scrollOffset);
            for (int index = 0; index < shown; index++) {
                int rowTop = listTop + index * ROW_HEIGHT;
                if (mouseY < rowTop || mouseY >= rowTop + ROW_HEIGHT - 2) {
                    continue;
                }
                if (tab == Tab.INVITES) {
                    Object row = inviteRows.get(scrollOffset + index);
                    if (row instanceof FactionPayloads.InviteInfo invite) {
                        if (inside(mouseX, mouseY, acceptZone(rowTop))) {
                            PacketDistributor.sendToServer(
                                    new FactionPayloads.C2SRespondInvite(invite.factionId(), true));
                            return true;
                        }
                        if (inside(mouseX, mouseY, declineZone(rowTop))) {
                            PacketDistributor.sendToServer(
                                    new FactionPayloads.C2SRespondInvite(invite.factionId(), false));
                            return true;
                        }
                    } else if (row instanceof FactionPayloads.AllianceInviteInfo allianceInvite) {
                        if (inside(mouseX, mouseY, acceptZone(rowTop))) {
                            PacketDistributor.sendToServer(
                                    new FactionPayloads.C2SRespondAlliance(allianceInvite.factionId(), true));
                            return true;
                        }
                        if (inside(mouseX, mouseY, declineZone(rowTop))) {
                            PacketDistributor.sendToServer(
                                    new FactionPayloads.C2SRespondAlliance(allianceInvite.factionId(), false));
                            return true;
                        }
                    }
                    return true;
                }
                if (mouseX >= left + CONTENT_LEFT && mouseX < left + CONTENT_RIGHT) {
                    minecraft.setScreen(new FactionDetailsScreen(this, factions.get(scrollOffset + index)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int[] zone) {
        return mouseX >= zone[0] && mouseX < zone[2] && mouseY >= zone[1] && mouseY < zone[3];
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
    public boolean isPauseScreen() {
        return false;
    }
}
