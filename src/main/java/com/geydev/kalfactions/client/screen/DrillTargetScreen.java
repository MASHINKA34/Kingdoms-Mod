package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.ClientDrillTargets;
import com.geydev.kalfactions.outpost.cluster.DrillPayloads;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterType;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DrillTargetScreen extends Screen {
    private static final int GOLD = 0xFFD3A73D;
    private static final int CARD_HEIGHT = 34;
    private static final int CARD_GAP = 4;
    private final DrillScreen parent;
    private DrillPayloads.S2CTargets state;
    private double scroll;
    private Long pendingChunk;

    DrillTargetScreen(DrillScreen parent, DrillPayloads.S2CTargets state) {
        super(Component.translatable("screen.kingdoms.drill.selector_title"));
        this.parent = parent;
        this.state = state;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xFF101824, 0xFF04080D);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Layout layout = layout();
        List<DrillPayloads.TargetInfo> targets = targets();
        scroll = Mth.clamp(scroll, 0.0D, maxScroll(layout, targets.size()));

        graphics.fill(layout.panelLeft(), layout.panelTop(), layout.panelRight(), layout.panelBottom(), 0xFF080D14);
        graphics.fill(
                layout.panelLeft() + 2,
                layout.panelTop() + 2,
                layout.panelRight() - 2,
                layout.panelBottom() - 2,
                0xFF142231
        );
        graphics.fill(layout.panelLeft() + 2, layout.panelTop() + 2, layout.panelRight() - 2, layout.panelTop() + 4, GOLD);
        graphics.drawCenteredString(font, title, width / 2, layout.panelTop() + 12, 0xFFF4DEAA);

        if (targets.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.kingdoms.drill.no_targets"),
                    width / 2,
                    layout.panelTop() + layout.listHeight() / 2 + 24,
                    0xFF9AA6B2
            );
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.enableScissor(layout.panelLeft() + 4, layout.listTop(), layout.panelRight() - 4, layout.listBottom());
        DrillPayloads.TargetInfo hovered = targetAt(layout, mouseX, mouseY);
        for (int index = 0; index < targets.size(); index++) {
            Rect card = layout.card(index, scroll);
            if (card.bottom() < layout.listTop() || card.top() > layout.listBottom()) {
                continue;
            }
            renderTarget(graphics, card, targets.get(index), targets.get(index) == hovered);
        }
        graphics.disableScissor();

        renderScrollbar(graphics, layout, targets.size());
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.kingdoms.drill.selector_hint"),
                width / 2,
                layout.panelBottom() - 15,
                0xFF8894A0
        );
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) {
            renderTargetTooltip(graphics, hovered, mouseX, mouseY);
        }
    }

    private void renderTarget(
            GuiGraphics graphics,
            Rect card,
            DrillPayloads.TargetInfo target,
            boolean hovered
    ) {
        int border = target.selected() ? 0xFF42B86B : target.available() ? GOLD : 0xFF59616B;
        int background = hovered && target.available() ? 0xFF23364A : 0xFF172431;
        graphics.fill(card.left(), card.top(), card.right(), card.bottom(), 0xFF05090E);
        graphics.fill(card.left() + 1, card.top() + 1, card.right() - 1, card.bottom() - 1, border);
        graphics.fill(card.left() + 2, card.top() + 2, card.right() - 2, card.bottom() - 2, background);

        ResourceClusterType type = ResourceClusterType.parse(target.type()).orElse(null);
        if (type != null) {
            graphics.renderItem(new ItemStack(type.displayItem()), card.left() + 8, card.top() + 9);
        }
        Component name = type == null
                ? Component.literal(target.type())
                : Component.literal(type.displayName());
        graphics.drawString(font, name, card.left() + 32, card.top() + 6, 0xFFF0D99D, false);
        ChunkPos chunk = new ChunkPos(target.chunk());
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.kingdoms.drill.selector_details",
                        chunk.x,
                        chunk.z,
                        target.richness()
                ),
                card.left() + 32,
                card.top() + 19,
                target.available() ? 0xFFB9C8D5 : 0xFF7E8790,
                false
        );
        if (target.selected()) {
            graphics.drawString(font, "✔", card.right() - 16, card.top() + 12, 0xFF69D68C, false);
        } else if (!target.available()) {
            graphics.drawString(font, "✖", card.right() - 16, card.top() + 12, 0xFFB65B5B, false);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, Layout layout, int count) {
        double max = maxScroll(layout, count);
        if (max <= 0.0D) {
            return;
        }
        int trackLeft = layout.panelRight() - 9;
        int trackRight = layout.panelRight() - 5;
        graphics.fill(trackLeft, layout.listTop(), trackRight, layout.listBottom(), 0xFF0A1119);
        int trackHeight = layout.listBottom() - layout.listTop();
        int contentHeight = count * (CARD_HEIGHT + CARD_GAP);
        int thumbHeight = Math.max(16, trackHeight * trackHeight / contentHeight);
        int thumbTop = layout.listTop() + (int) ((trackHeight - thumbHeight) * (scroll / max));
        graphics.fill(trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, GOLD);
    }

    private void renderTargetTooltip(
            GuiGraphics graphics,
            DrillPayloads.TargetInfo target,
            int mouseX,
            int mouseY
    ) {
        String key = target.selected()
                ? "screen.kingdoms.drill.target_selected"
                : target.available()
                        ? "screen.kingdoms.drill.target_available"
                        : "screen.kingdoms.drill.target_busy";
        ChunkPos chunk = new ChunkPos(target.chunk());
        Component name = ResourceClusterType.parse(target.type())
                .map(type -> Component.literal(type.displayName()))
                .orElse(Component.literal(target.type()));
        graphics.renderComponentTooltip(
                font,
                List.of(
                        name,
                        Component.translatable(
                                "screen.kingdoms.drill.selector_details",
                                chunk.x,
                                chunk.z,
                                target.richness()
                        ),
                        Component.translatable(key)
                ),
                mouseX,
                mouseY
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = layout();
        double max = maxScroll(layout, targets().size());
        if (max > 0.0D) {
            scroll = Mth.clamp(scroll - scrollY * (CARD_HEIGHT + CARD_GAP), 0.0D, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        DrillPayloads.TargetInfo target = targetAt(layout(), mouseX, mouseY);
        if (target == null || !target.available() || pendingChunk != null) {
            return target != null;
        }
        if (target.selected()) {
            parent.returnFromSelector();
            return true;
        }
        pendingChunk = target.chunk();
        PacketDistributor.sendToServer(new DrillPayloads.C2SSelectTarget(parent.getMenu().containerId, target.chunk()));
        return true;
    }

    public void acceptState(DrillPayloads.S2CTargets updated) {
        if (updated.containerId() != parent.getMenu().containerId) {
            return;
        }
        state = updated;
        if (pendingChunk != null && updated.targets().stream()
                .anyMatch(target -> target.selected() && target.chunk() == pendingChunk)) {
            pendingChunk = null;
            parent.returnFromSelector();
            return;
        }
        pendingChunk = null;
    }

    @Override
    public void onClose() {
        if (state != null && state.hasTarget()) {
            parent.returnFromSelector();
            return;
        }
        ClientDrillTargets.clear(parent.getMenu().containerId);
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private DrillPayloads.TargetInfo targetAt(Layout layout, double mouseX, double mouseY) {
        if (mouseY < layout.listTop() || mouseY >= layout.listBottom()) {
            return null;
        }
        List<DrillPayloads.TargetInfo> targets = targets();
        for (int index = 0; index < targets.size(); index++) {
            if (layout.card(index, scroll).contains(mouseX, mouseY)) {
                return targets.get(index);
            }
        }
        return null;
    }

    private List<DrillPayloads.TargetInfo> targets() {
        return state == null ? List.of() : state.targets();
    }

    private Layout layout() {
        return Layout.create(width, height);
    }

    private static double maxScroll(Layout layout, int count) {
        return Math.max(0.0D, (double) count * (CARD_HEIGHT + CARD_GAP) - CARD_GAP - layout.listHeight());
    }

    record Layout(int panelLeft, int panelTop, int panelWidth, int panelHeight) {
        static Layout create(int screenWidth, int screenHeight) {
            int panelWidth = Math.max(220, Math.min(300, screenWidth - 40));
            int panelHeight = Math.max(160, Math.min(240, screenHeight - 40));
            return new Layout(
                    (screenWidth - panelWidth) / 2,
                    (screenHeight - panelHeight) / 2,
                    panelWidth,
                    panelHeight
            );
        }

        int panelRight() {
            return panelLeft + panelWidth;
        }

        int panelBottom() {
            return panelTop + panelHeight;
        }

        int listTop() {
            return panelTop + 26;
        }

        int listBottom() {
            return panelBottom() - 20;
        }

        int listHeight() {
            return listBottom() - listTop();
        }

        int cardWidth() {
            return panelWidth - 22;
        }

        Rect card(int index, double scroll) {
            int left = panelLeft + 8;
            int top = listTop() + index * (CARD_HEIGHT + CARD_GAP) - (int) Math.round(scroll);
            return new Rect(left, top, left + cardWidth(), top + CARD_HEIGHT);
        }
    }

    record Rect(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
