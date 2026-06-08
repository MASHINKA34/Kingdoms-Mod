package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionMembersScreen extends FactionScreen {
    private static final int MEMBER_TIER = 0;
    private static final int OFFICER_TIER = 1;
    private static final int LEADER_TIER = 2;
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 6;

    private int scrollOffset;

    public FactionMembersScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.members", "Members"), snapshot, successful, message);
    }

    @Override
    protected void initFactionWidgets() {
        List<FactionSnapshot.Member> members = snapshot.members();
        scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, members.size() - VISIBLE_ROWS));

        int listTop = top + 40;
        int shown = Math.min(VISIBLE_ROWS, members.size() - scrollOffset);
        for (int index = 0; index < shown; index++) {
            FactionSnapshot.Member member = members.get(scrollOffset + index);
            int rowY = listTop + index * ROW_HEIGHT;
            int tier = tierOf(member.role());
            boolean self = snapshot.isSelf(member.playerId());

            Button kick = addRenderableWidget(Button.builder(
                    text("screen.kingdoms.kick", "Kick"),
                    button -> PacketDistributor.sendToServer(
                            new FactionPayloads.C2SKickMember(snapshot.tablePos(), member.playerId())
                    )
            ).bounds(left + 176, rowY, 58, 20).build());
            kick.active = !self && tier != LEADER_TIER
                    && (snapshot.canManage() || (snapshot.isOfficer() && tier == MEMBER_TIER));

            if (tier == LEADER_TIER) {
                continue;
            }
            String targetRole = tier == MEMBER_TIER ? "OFFICER" : "MEMBER";
            Component label = tier == MEMBER_TIER
                    ? text("screen.kingdoms.promote", "Promote")
                    : text("screen.kingdoms.demote", "Demote");
            Button role = addRenderableWidget(Button.builder(
                    label,
                    button -> PacketDistributor.sendToServer(
                            new FactionPayloads.C2SSetMemberRole(snapshot.tablePos(), member.playerId(), targetRole)
                    )
            ).bounds(left + 238, rowY, 78, 20).build());
            role.active = snapshot.canManage() && !self;
        }

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.back", "Back"),
                button -> FactionScreens.openRoot(snapshot, true, "")
        ).bounds(left + 16, top + PANEL_HEIGHT - 25, 70, 20).build());
        addRenderableWidget(Button.builder(
                text("screen.kingdoms.refresh", "Refresh"),
                button -> requestRefresh()
        ).bounds(left + 90, top + PANEL_HEIGHT - 25, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<FactionSnapshot.Member> members = snapshot.members();
        int listTop = top + 40;
        int shown = Math.min(VISIBLE_ROWS, members.size() - scrollOffset);
        for (int index = 0; index < shown; index++) {
            FactionSnapshot.Member member = members.get(scrollOffset + index);
            int rowY = listTop + index * ROW_HEIGHT + 6;
            graphics.drawString(font, member.name(), left + 16, rowY, 0x3F2A19, false);
            graphics.drawString(font, member.role(), left + 116, rowY, 0x715D43, false);
        }
        if (members.size() > VISIBLE_ROWS) {
            graphics.drawString(
                    font,
                    Component.literal((scrollOffset + 1) + "-" + (scrollOffset + shown) + " / " + members.size()),
                    left + 176,
                    top + PANEL_HEIGHT - 19,
                    0x715D43,
                    false
            );
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, snapshot.members().size() - VISIBLE_ROWS);
        if (max > 0) {
            int updated = Math.clamp(scrollOffset - (int) Math.signum(scrollY), 0, max);
            if (updated != scrollOffset) {
                scrollOffset = updated;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static int tierOf(String role) {
        String normalized = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("lead")) {
            return LEADER_TIER;
        }
        if (normalized.startsWith("off")) {
            return OFFICER_TIER;
        }
        return MEMBER_TIER;
    }
}
