package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.ResearchNode;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ResearchScreen extends FactionScreen {
    private static final ResourceLocation PANEL = tex("research/panel");
    private static final ResourceLocation TAB_SCIENCE = tex("research/tab_science");
    private static final ResourceLocation TAB_ECONOMIC = tex("research/tab_economic");
    private static final ResourceLocation TAB_MILITARY = tex("research/tab_military");
    private static final ResourceLocation NODE_ROOT = tex("research/node_root");
    private static final ResourceLocation NODE_LOCKED = tex("research/node_locked");
    private static final ResourceLocation NODE_AVAILABLE = tex("research/node_available");
    private static final ResourceLocation NODE_ACTIVE = tex("research/node_active");
    private static final ResourceLocation NODE_DONE = tex("research/node_done");
    private static final ResourceLocation BAR_EMPTY = tex("war/bar_empty");
    private static final ResourceLocation BAR_FULL = tex("war/bar_full");
    private static final ResourceLocation ICON_SCIENCE = tex("influence/science");
    private static final ResourceLocation ICON_ECONOMIC = tex("influence/economic");
    private static final ResourceLocation ICON_MILITARY = tex("influence/military");

    private static final int WINDOW_WIDTH = 420;
    private static final int WINDOW_HEIGHT = 260;
    private static final int TREE_LEFT = 24;
    private static final int TREE_TOP = 58;
    private static final int TREE_WIDTH = 372;
    private static final int TREE_HEIGHT = 154;
    private static final int TAB_WIDTH = 42;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_STEP = 50;
    private static final int NODE_SIZE = 30;
    private static final int ROOT_SIZE = 46;

    private InfluenceType selectedType = InfluenceType.SCIENCE;
    private ResearchNode selectedNode = ResearchNode.SCI_ROOT;
    private Button startButton;
    private float panX;
    private float panY;
    private float zoom = 0.92F;
    private boolean draggingTree;
    private double lastDragX;
    private double lastDragY;

    public ResearchScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.research"), snapshot, successful, message);
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/" + path + ".png");
    }

    @Override
    protected void init() {
        left = (width - WINDOW_WIDTH) / 2;
        top = (height - WINDOW_HEIGHT) / 2;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + WINDOW_WIDTH - 74,
                top + WINDOW_HEIGHT - 25,
                66,
                20
        ));
        initFactionWidgets();
    }

    @Override
    protected void initFactionWidgets() {
        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.back"),
                button -> FactionScreens.openInfluence(snapshot, true, ""),
                left + 16, top + WINDOW_HEIGHT - 25, 70, 20
        ));
        startButton = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.research_start"),
                button -> startSelectedNode(),
                left + WINDOW_WIDTH - 150,
                top + WINDOW_HEIGHT - 25,
                66,
                20
        ));
        updateStartButton();
    }

    @Override
    public void acceptServerState(FactionSnapshot newSnapshot, boolean actionSuccessful, String message) {
        super.acceptServerState(newSnapshot, actionSuccessful, message);
        updateStartButton();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL, left, top, 0.0F, 0.0F, WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + (WINDOW_WIDTH - font.width(title)) / 2, top + 33, 0xFFFFE8AA, true);
        renderTabs(graphics);
        renderTree(graphics, mouseX, mouseY);
        renderSelectionLine(graphics);
        renderStatusNotice(graphics);
        ResearchNode hovered = hoveredNode(mouseX, mouseY);
        if (hovered != null) {
            renderNodeTooltip(graphics, hovered, mouseX, mouseY);
        }
    }

    private void renderTabs(GuiGraphics graphics) {
        ResourceLocation[] tabs = {TAB_SCIENCE, TAB_ECONOMIC, TAB_MILITARY};
        for (int i = 0; i < tabs.length; i++) {
            int tabX = left + TREE_LEFT + i * TAB_STEP;
            int tabY = top + 18;
            float alpha = selectedType == InfluenceType.VALUES[i] ? 1.0F : 0.58F;
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(tabs[i], tabX, tabY, TAB_WIDTH, TAB_HEIGHT, 0.0F, 0.0F, 128, 96, 128, 96);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderTree(GuiGraphics graphics, int mouseX, int mouseY) {
        int clipLeft = left + TREE_LEFT;
        int clipTop = top + TREE_TOP;
        int clipRight = clipLeft + TREE_WIDTH;
        int clipBottom = clipTop + TREE_HEIGHT;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        for (ResearchNode node : ResearchNode.branch(selectedType)) {
            node.prerequisite().ifPresent(parent -> renderConnection(graphics, parent, node));
        }
        for (ResearchNode node : ResearchNode.branch(selectedType)) {
            renderNode(graphics, node, node == hoveredNode(mouseX, mouseY));
        }
        graphics.disableScissor();
    }

    private void renderConnection(GuiGraphics graphics, ResearchNode parent, ResearchNode child) {
        int color = connectionComplete(parent, child) ? 0xFFC9A24C : 0xFF4A4D58;
        int x1 = screenX(parent);
        int y1 = screenY(parent);
        int x2 = screenX(child);
        int y2 = screenY(child);
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps <= 0) {
            return;
        }
        for (int i = 0; i <= steps; i += 3) {
            float t = i / (float) steps;
            int x = Math.round(x1 + (x2 - x1) * t);
            int y = Math.round(y1 + (y2 - y1) * t);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private void renderNode(GuiGraphics graphics, ResearchNode node, boolean hovered) {
        NodeState nodeState = state(node);
        ResourceLocation texture = node.root()
                ? NODE_ROOT
                : switch (nodeState) {
                    case DONE -> NODE_DONE;
                    case ACTIVE -> NODE_ACTIVE;
                    case AVAILABLE -> NODE_AVAILABLE;
                    case LOCKED -> NODE_LOCKED;
                };
        int size = node.root() ? ROOT_SIZE : NODE_SIZE;
        int x = screenX(node) - size / 2;
        int y = screenY(node) - size / 2;
        if (hovered || node == selectedNode) {
            graphics.fill(x - 3, y - 3, x + size + 3, y + size + 3, hovered ? 0x66C9A24C : 0x663FAE9E);
        }
        int sourceSize = node.root() ? 64 : 64;
        graphics.blit(texture, x, y, size, size, 0.0F, 0.0F, sourceSize, sourceSize, sourceSize, sourceSize);
        if (nodeState == NodeState.ACTIVE) {
            long remaining = Math.max(0L, snapshot.activeResearchEndMillis() - System.currentTimeMillis());
            long duration = Math.max(1L, node.durationMillis());
            float fraction = Math.clamp((duration - remaining) / (float) duration, 0.0F, 1.0F);
            int barWidth = 42;
            int barX = screenX(node) - barWidth / 2;
            int barY = y + size + 3;
            graphics.blit(BAR_EMPTY, barX, barY, 0.0F, 0.0F, barWidth, 5, 182, 5);
            int fullWidth = Math.max(0, (int) (barWidth * fraction));
            if (fullWidth > 0) {
                graphics.blit(BAR_FULL, barX, barY, 0.0F, 0.0F, fullWidth, 5, 182, 5);
            }
        }
    }

    private void renderSelectionLine(GuiGraphics graphics) {
        if (selectedNode == null) {
            return;
        }
        Component name = text(selectedNode.translationKey());
        Component cost = text(
                "screen.kingdoms.research_cost",
                selectedNode.cost(),
                text(selectedNode.type().translationKey()),
                selectedNode.durationHours()
        );
        graphics.drawString(font, name, left + TREE_LEFT, top + 218, 0xFFFFE8AA, true);
        graphics.drawString(font, cost, left + TREE_LEFT + 150, top + 218, 0xFFD7C57C, true);
    }

    private void renderStatusNotice(GuiGraphics graphics) {
        if (statusMessage == null || statusMessage.isBlank()) {
            return;
        }
        String clipped = font.plainSubstrByWidth(statusMessage, WINDOW_WIDTH - 38);
        graphics.drawString(font, clipped, left + 18, top + WINDOW_HEIGHT + 5, successful ? 0xFFB9F3A9 : 0xFFF2A7A7, true);
    }

    private void renderNodeTooltip(GuiGraphics graphics, ResearchNode node, int mouseX, int mouseY) {
        int boxWidth = 214;
        List<FormattedCharSequence> desc = font.split(text(node.descriptionKey()), boxWidth - 18);
        int boxHeight = 58 + desc.size() * 10;
        int x = Math.min(mouseX + 14, width - boxWidth - 6);
        int y = Math.min(mouseY + 12, height - boxHeight - 6);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF015171D);
        graphics.fill(x + 1, y + 1, x + boxWidth - 1, y + 2, 0xFFC9A24C);
        graphics.fill(x + 1, y + boxHeight - 2, x + boxWidth - 1, y + boxHeight - 1, 0xFF3A3D47);
        graphics.drawString(font, text(node.translationKey()), x + 8, y + 7, colorFor(node.type()), false);
        for (int i = 0; i < desc.size(); i++) {
            graphics.drawString(font, desc.get(i), x + 8, y + 20 + i * 10, 0xFFE8D6A0, false);
        }
        int lineY = y + 24 + desc.size() * 10;
        graphics.blit(iconFor(node.type()), x + 8, lineY - 2, 12, 12, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.drawString(
                font,
                text("screen.kingdoms.research_cost_short", node.cost(), node.durationHours()),
                x + 24,
                lineY,
                0xFFD7C57C,
                false
        );
        graphics.drawString(font, statusText(node), x + 8, lineY + 14, statusColor(node), false);
        graphics.drawString(font, text("screen.kingdoms.research_tag", node.bonusTag()), x + 8, lineY + 26, 0xFF9CA2B2, false);
    }

    private Component statusText(ResearchNode node) {
        NodeState nodeState = state(node);
        if (nodeState == NodeState.DONE) {
            return text("screen.kingdoms.research_done");
        }
        if (nodeState == NodeState.ACTIVE) {
            long remaining = Math.max(0L, snapshot.activeResearchEndMillis() - System.currentTimeMillis());
            return text("screen.kingdoms.research_active_status", formatDuration(remaining));
        }
        if (nodeState == NodeState.LOCKED) {
            return text("screen.kingdoms.research_need_previous");
        }
        if (influenceOf(node.type()) < node.cost()) {
            return text("screen.kingdoms.research_not_enough");
        }
        return text("screen.kingdoms.research_available");
    }

    private int statusColor(ResearchNode node) {
        NodeState nodeState = state(node);
        if (nodeState == NodeState.DONE) {
            return 0xFF6FE3D4;
        }
        if (nodeState == NodeState.ACTIVE) {
            return 0xFFFFCE4A;
        }
        if (nodeState == NodeState.AVAILABLE && influenceOf(node.type()) >= node.cost()) {
            return 0xFF5AFF8A;
        }
        return 0xFFFF9E9E;
    }

    private void startSelectedNode() {
        if (selectedNode == null || !canStart(selectedNode)) {
            return;
        }
        PacketDistributor.sendToServer(new FactionPayloads.C2SStartResearch(snapshot.tablePos(), selectedNode.name()));
    }

    private void updateStartButton() {
        if (startButton == null) {
            return;
        }
        startButton.active = selectedNode != null && canStart(selectedNode);
    }

    private boolean canStart(ResearchNode node) {
        return state(node) == NodeState.AVAILABLE
                && (snapshot.canManage() || snapshot.isOfficer())
                && snapshot.activeResearchNode().isEmpty()
                && influenceOf(node.type()) >= node.cost();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (int i = 0; i < InfluenceType.VALUES.length; i++) {
            int tabX = left + TREE_LEFT + i * TAB_STEP;
            int tabY = top + 18;
            if (mouseX >= tabX && mouseX < tabX + TAB_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                if (selectedType != InfluenceType.VALUES[i]) {
                    selectedType = InfluenceType.VALUES[i];
                    selectedNode = ResearchNode.branch(selectedType).getFirst();
                    panX = 0.0F;
                    panY = 0.0F;
                    rebuildWidgets();
                }
                return true;
            }
        }
        ResearchNode hovered = hoveredNode(mouseX, mouseY);
        if (hovered != null) {
            selectedNode = hovered;
            updateStartButton();
            return true;
        }
        if (insideTree(mouseX, mouseY)) {
            draggingTree = true;
            lastDragX = mouseX;
            lastDragY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingTree && button == 0) {
            panX += (float) ((mouseX - lastDragX) / zoom);
            panY += (float) ((mouseY - lastDragY) / zoom);
            lastDragX = mouseX;
            lastDragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingTree = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!insideTree(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        float previous = zoom;
        zoom = Math.clamp(zoom + (float) scrollY * 0.08F, 0.62F, 1.35F);
        float factor = zoom / previous;
        float centerX = (float) (mouseX - (left + TREE_LEFT + TREE_WIDTH / 2.0F));
        float centerY = (float) (mouseY - (top + TREE_TOP + TREE_HEIGHT / 2.0F));
        panX = panX - centerX / previous + centerX / (previous * factor);
        panY = panY - centerY / previous + centerY / (previous * factor);
        return true;
    }

    private ResearchNode hoveredNode(double mouseX, double mouseY) {
        if (!insideTree(mouseX, mouseY)) {
            return null;
        }
        for (ResearchNode node : ResearchNode.branch(selectedType)) {
            int size = node.root() ? ROOT_SIZE : NODE_SIZE;
            int x = screenX(node);
            int y = screenY(node);
            if (mouseX >= x - size / 2.0 && mouseX < x + size / 2.0
                    && mouseY >= y - size / 2.0 && mouseY < y + size / 2.0) {
                return node;
            }
        }
        return null;
    }

    private boolean insideTree(double mouseX, double mouseY) {
        return mouseX >= left + TREE_LEFT && mouseX < left + TREE_LEFT + TREE_WIDTH
                && mouseY >= top + TREE_TOP && mouseY < top + TREE_TOP + TREE_HEIGHT;
    }

    private int screenX(ResearchNode node) {
        return Math.round(left + TREE_LEFT + TREE_WIDTH / 2.0F + (node.treeX() + panX) * zoom);
    }

    private int screenY(ResearchNode node) {
        return Math.round(top + TREE_TOP + TREE_HEIGHT / 2.0F + (node.treeY() + panY) * zoom);
    }

    private boolean connectionComplete(ResearchNode parent, ResearchNode child) {
        return snapshot.completedResearch().contains(parent.name()) && snapshot.completedResearch().contains(child.name());
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

    private static ResourceLocation iconFor(InfluenceType type) {
        return switch (type) {
            case SCIENCE -> ICON_SCIENCE;
            case ECONOMIC -> ICON_ECONOMIC;
            case MILITARY -> ICON_MILITARY;
        };
    }

    private static int colorFor(InfluenceType type) {
        return switch (type) {
            case SCIENCE -> 0xFF9AD0FF;
            case ECONOMIC -> 0xFFFFE79A;
            case MILITARY -> 0xFFFFB0B0;
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum NodeState {
        LOCKED,
        AVAILABLE,
        ACTIVE,
        DONE
    }
}
