package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.ResearchNode;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ResearchScreen extends FactionScreen {
    private static final ResourceLocation TAB_SCIENCE = tex("research/tab_science");
    private static final ResourceLocation TAB_ECONOMIC = tex("research/tab_economic");
    private static final ResourceLocation TAB_MILITARY = tex("research/tab_military");
    private static final ResourceLocation NODE_LOCKED = tex("research/node_locked");
    private static final ResourceLocation NODE_AVAILABLE = tex("research/node_available");
    private static final ResourceLocation NODE_ACTIVE = tex("research/node_active");
    private static final ResourceLocation NODE_DONE = tex("research/node_done");
    private static final ResourceLocation BAR_EMPTY = tex("war/bar_empty");
    private static final ResourceLocation BAR_FULL = tex("war/bar_full");

    private static final int TAB_WIDTH = 32;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_STEP = 40;
    private static final int NODE_SIZE = 24;
    private static final int ROW_STEP = 25;

    private InfluenceType selectedType = InfluenceType.SCIENCE;

    public ResearchScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.research"), snapshot, successful, message);
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/" + path + ".png");
    }

    @Override
    protected void initFactionWidgets() {
        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.back"),
                button -> FactionScreens.openInfluence(snapshot, true, ""),
                left + 16, top + PANEL_HEIGHT - 25, 70, 20
        ));

        List<ResearchNode> nodes = ResearchNode.branch(selectedType);
        boolean hasActive = !snapshot.activeResearchNode().isEmpty();
        for (int i = 0; i < nodes.size(); i++) {
            ResearchNode node = nodes.get(i);
            if (state(node) != NodeState.AVAILABLE) {
                continue;
            }
            boolean affordable = snapshot.canManage() || snapshot.isOfficer();
            KingdomsButton start = KingdomsButton.create(
                    text("screen.kingdoms.research_start"),
                    button -> PacketDistributor.sendToServer(
                            new FactionPayloads.C2SStartResearch(snapshot.tablePos(), node.name())
                    ),
                    left + 238, top + 92 + i * ROW_STEP, 60, 20
            );
            start.active = affordable && !hasActive && influenceOf(node.type()) >= node.cost();
            addRenderableWidget(start);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < InfluenceType.VALUES.length; i++) {
                int tabX = left + CONTENT_LEFT + i * TAB_STEP;
                int tabY = top + 62;
                if (mouseX >= tabX && mouseX < tabX + TAB_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                    if (selectedType != InfluenceType.VALUES[i]) {
                        selectedType = InfluenceType.VALUES[i];
                        rebuildWidgets();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTabs(graphics);
        List<ResearchNode> nodes = ResearchNode.branch(selectedType);
        for (int i = 0; i < nodes.size(); i++) {
            renderNode(graphics, nodes.get(i), top + 92 + i * ROW_STEP);
        }
    }

    private void renderTabs(GuiGraphics graphics) {
        ResourceLocation[] tabs = {TAB_SCIENCE, TAB_ECONOMIC, TAB_MILITARY};
        for (int i = 0; i < tabs.length; i++) {
            int tabX = left + CONTENT_LEFT + i * TAB_STEP;
            int tabY = top + 62;
            float alpha = selectedType == InfluenceType.VALUES[i] ? 1.0F : 0.55F;
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(tabs[i], tabX, tabY, 0.0F, 0.0F, TAB_WIDTH, TAB_HEIGHT, TAB_WIDTH, TAB_HEIGHT);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderNode(GuiGraphics graphics, ResearchNode node, int rowY) {
        NodeState nodeState = state(node);
        ResourceLocation icon = switch (nodeState) {
            case DONE -> NODE_DONE;
            case ACTIVE -> NODE_ACTIVE;
            case AVAILABLE -> NODE_AVAILABLE;
            case LOCKED -> NODE_LOCKED;
        };
        int iconX = left + CONTENT_LEFT;
        graphics.blit(icon, iconX, rowY, NODE_SIZE, NODE_SIZE, 0.0F, 0.0F, 32, 32, 32, 32);

        int textX = iconX + NODE_SIZE + 6;
        graphics.drawString(font, text(node.translationKey()), textX, rowY + 1, TEXT_DARK, false);

        switch (nodeState) {
            case DONE -> graphics.drawString(
                    font, text("screen.kingdoms.research_done"), textX, rowY + 12, TEXT_MUTED, false);
            case ACTIVE -> renderActive(graphics, node, textX, rowY);
            case AVAILABLE -> graphics.drawString(
                    font,
                    text(
                            "screen.kingdoms.research_cost",
                            node.cost(),
                            text(node.type().translationKey()),
                            node.durationHours()
                    ),
                    textX, rowY + 12, TEXT_MUTED, false);
            case LOCKED -> graphics.drawString(
                    font, text("screen.kingdoms.research_locked"), textX, rowY + 12, TEXT_HINT, false);
        }
    }

    private void renderActive(GuiGraphics graphics, ResearchNode node, int textX, int rowY) {
        long remaining = Math.max(0L, snapshot.activeResearchEndMillis() - System.currentTimeMillis());
        long duration = Math.max(1L, node.durationMillis());
        float fraction = Math.clamp((duration - remaining) / (float) duration, 0.0F, 1.0F);
        graphics.drawString(
                font,
                text("screen.kingdoms.research_remaining", formatDuration(remaining)),
                textX, rowY + 11, TEXT_MUTED, false);
        int barY = rowY + 21;
        graphics.blit(BAR_EMPTY, textX, barY, 0.0F, 0.0F, 182, 5, 182, 5);
        int fullWidth = (int) (182 * fraction);
        if (fullWidth > 0) {
            graphics.blit(BAR_FULL, textX, barY, 0.0F, 0.0F, fullWidth, 5, 182, 5);
        }
    }

    private NodeState state(ResearchNode node) {
        if (snapshot.completedResearch().contains(node.name())) {
            return NodeState.DONE;
        }
        if (node.name().equals(snapshot.activeResearchNode())) {
            return NodeState.ACTIVE;
        }
        boolean prereqDone = node.prerequisite()
                .map(prereq -> snapshot.completedResearch().contains(prereq.name()))
                .orElse(true);
        return prereqDone ? NodeState.AVAILABLE : NodeState.LOCKED;
    }

    private long influenceOf(InfluenceType type) {
        return switch (type) {
            case SCIENCE -> snapshot.influenceScience();
            case ECONOMIC -> snapshot.influenceEconomic();
            case MILITARY -> snapshot.influenceMilitary();
        };
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private enum NodeState {
        LOCKED,
        AVAILABLE,
        ACTIVE,
        DONE
    }
}
