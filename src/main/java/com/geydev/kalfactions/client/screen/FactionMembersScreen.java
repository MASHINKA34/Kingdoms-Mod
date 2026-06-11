package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionMembersScreen extends FactionScreen {
    private static final int MEMBER_TIER = 0;
    private static final int OFFICER_TIER = 1;
    private static final int LEADER_TIER = 2;
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 5;

    private int scrollOffset;

    public FactionMembersScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.members"), snapshot, successful, message);
    }

    @Override
    protected void initFactionWidgets() {
        List<FactionSnapshot.Member> members = snapshot.members();
        scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, members.size() - VISIBLE_ROWS));

        int listTop = top + 62;
        int shown = Math.min(VISIBLE_ROWS, members.size() - scrollOffset);
        for (int index = 0; index < shown; index++) {
            FactionSnapshot.Member member = members.get(scrollOffset + index);
            int rowY = listTop + index * ROW_HEIGHT;
            int tier = tierOf(member.role());
            boolean self = snapshot.isSelf(member.playerId());

            KingdomsButton kick = addRenderableWidget(KingdomsButton.create(
                    text("screen.kingdoms.kick"),
                    button -> PacketDistributor.sendToServer(
                            new FactionPayloads.C2SKickMember(snapshot.tablePos(), member.playerId())
                    ),
                    left + 170, rowY, 54, 20
            ));
            kick.active = !self && tier != LEADER_TIER
                    && (snapshot.canManage() || (snapshot.isOfficer() && tier == MEMBER_TIER));

            if (tier == LEADER_TIER) {
                continue;
            }
            String targetRole = tier == MEMBER_TIER ? "OFFICER" : "MEMBER";
            Component label = tier == MEMBER_TIER
                    ? text("screen.kingdoms.promote")
                    : text("screen.kingdoms.demote");
            KingdomsButton role = addRenderableWidget(KingdomsButton.create(
                    label,
                    button -> PacketDistributor.sendToServer(
                            new FactionPayloads.C2SSetMemberRole(snapshot.tablePos(), member.playerId(), targetRole)
                    ),
                    left + 228, rowY, 70, 20
            ));
            role.active = snapshot.canManage() && !self;
        }

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.back"),
                button -> FactionScreens.openRoot(snapshot, true, ""),
                left + 16, top + PANEL_HEIGHT - 25, 70, 20
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<FactionSnapshot.Member> members = snapshot.members();
        int listTop = top + 62;
        int shown = Math.min(VISIBLE_ROWS, members.size() - scrollOffset);
        for (int index = 0; index < shown; index++) {
            FactionSnapshot.Member member = members.get(scrollOffset + index);
            int rowY = listTop + index * ROW_HEIGHT + 6;
            graphics.drawString(font, member.name(), left + CONTENT_LEFT, rowY, TEXT_DARK, false);
            graphics.drawString(font, roleText(member.role()), left + 116, rowY, TEXT_HINT, false);
        }
        if (members.size() > VISIBLE_ROWS) {
            graphics.drawString(
                    font,
                    text(
                            "screen.kingdoms.members_page",
                            scrollOffset + 1,
                            scrollOffset + shown,
                            members.size()
                    ),
                    left + 200,
                    top + 181,
                    0xFFD9C49A,
                    true
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

    private static Component roleText(String role) {
        return switch (tierOf(role)) {
            case LEADER_TIER -> text("kingdoms.role.leader");
            case OFFICER_TIER -> text("kingdoms.role.officer");
            default -> text("kingdoms.role.member");
        };
    }
}
