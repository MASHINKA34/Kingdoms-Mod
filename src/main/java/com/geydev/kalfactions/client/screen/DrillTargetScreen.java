package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.ClientDrillTargets;
import com.geydev.kalfactions.outpost.cluster.DrillPayloads;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterType;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DrillTargetScreen extends Screen {
    private static final int GOLD = 0xFFD3A73D;
    private static final int CARD_HEIGHT = 40;
    private static final int ROWS = 4;
    private final DrillScreen parent;
    private DrillPayloads.S2CTargets state;
    private int page;
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
        graphics.fill(
                layout.panelLeft(),
                layout.panelTop(),
                layout.panelRight(),
                layout.panelBottom(),
                0xFF080D14
        );
        graphics.fill(
                layout.panelLeft() + 2,
                layout.panelTop() + 2,
                layout.panelRight() - 2,
                layout.panelBottom() - 2,
                0xFF142231
        );
        graphics.fill(
                layout.panelLeft() + 2,
                layout.panelTop() + 2,
                layout.panelRight() - 2,
                layout.panelTop() + 4,
                GOLD
        );
        graphics.drawCenteredString(font, title, width / 2, layout.panelTop() + 12, 0xFFF4DEAA);

        List<DrillPayloads.TargetInfo> targets = targets();
        int pages = pageCount(targets, layout.perPage());
        page = Math.clamp(page, 0, pages - 1);
        if (targets.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.kingdoms.drill.no_targets"),
                    width / 2,
                    layout.panelTop() + 94,
                    0xFF9AA6B2
            );
        } else {
            int from = page * layout.perPage();
            int to = Math.min(targets.size(), from + layout.perPage());
            for (int index = from; index < to; index++) {
                renderTarget(graphics, layout, targets.get(index), index - from);
            }
        }
        if (pages > 1) {
            Rect previous = layout.previousPage();
            Rect next = layout.nextPage();
            drawPageButton(graphics, previous, "<", page > 0);
            drawPageButton(graphics, next, ">", page + 1 < pages);
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.kingdoms.drill.page", page + 1, pages),
                    width / 2,
                    layout.panelBottom() - 17,
                    0xFFC9D2DB
            );
        }
        renderTargetTooltip(graphics, layout, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTarget(
            GuiGraphics graphics,
            Layout layout,
            DrillPayloads.TargetInfo target,
            int localIndex
    ) {
        Rect card = layout.card(localIndex);
        int border = target.selected() ? 0xFF42B86B : target.available() ? GOLD : 0xFF59616B;
        graphics.fill(card.left(), card.top(), card.right(), card.bottom(), 0xFF05090E);
        graphics.fill(card.left() + 1, card.top() + 1, card.right() - 1, card.bottom() - 1, border);
        graphics.fill(card.left() + 2, card.top() + 2, card.right() - 2, card.bottom() - 2, 0xFF172431);
        ResourceClusterType.parse(target.type()).ifPresent(type ->
                graphics.renderItem(new ItemStack(type.displayItem()), card.left() + 7, card.top() + 11)
        );
        Component resource = ResourceClusterType.parse(target.type())
                .map(type -> type.displayItem().getDescription())
                .orElse(Component.literal(target.type()));
        graphics.drawString(font, resource, card.left() + 29, card.top() + 7, 0xFFF0D99D, false);
        ChunkPos chunk = new ChunkPos(target.chunk());
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.kingdoms.drill.selector_details",
                        chunk.x,
                        chunk.z,
                        target.richness()
                ),
                card.left() + 29,
                card.top() + 22,
                target.available() ? 0xFFB9C8D5 : 0xFF7E8790,
                false
        );
    }

    private static void drawPageButton(GuiGraphics graphics, Rect rect, String label, boolean active) {
        graphics.fill(rect.left(), rect.top(), rect.right(), rect.bottom(), active ? 0xFF76551D : 0xFF26303A);
        graphics.drawCenteredString(
                net.minecraft.client.Minecraft.getInstance().font,
                label,
                (rect.left() + rect.right()) / 2,
                rect.top() + 4,
                active ? 0xFFFFFFFF : 0xFF6F7780
        );
    }

    private void renderTargetTooltip(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        DrillPayloads.TargetInfo target = targetAt(layout, mouseX, mouseY);
        if (target == null) {
            return;
        }
        String key = target.selected()
                ? "screen.kingdoms.drill.target_selected"
                : target.available()
                        ? "screen.kingdoms.drill.target_available"
                        : "screen.kingdoms.drill.target_busy";
        graphics.renderTooltip(font, Component.translatable(key), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        Layout layout = layout();
        int pages = pageCount(targets(), layout.perPage());
        if (layout.previousPage().contains(mouseX, mouseY) && page > 0) {
            page--;
            return true;
        }
        if (layout.nextPage().contains(mouseX, mouseY) && page + 1 < pages) {
            page++;
            return true;
        }
        DrillPayloads.TargetInfo target = targetAt(layout, mouseX, mouseY);
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
        List<DrillPayloads.TargetInfo> targets = targets();
        int from = page * layout.perPage();
        int to = Math.min(targets.size(), from + layout.perPage());
        for (int index = from; index < to; index++) {
            if (layout.card(index - from).contains(mouseX, mouseY)) {
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

    private static int pageCount(List<DrillPayloads.TargetInfo> targets, int perPage) {
        return Math.max(1, (targets.size() + perPage - 1) / perPage);
    }

    record Layout(
            int panelLeft,
            int panelTop,
            int panelWidth,
            int panelHeight,
            int columns,
            int cardWidth
    ) {
        static Layout create(int screenWidth, int screenHeight) {
            int panelWidth = Math.max(250, Math.min(370, screenWidth - 20));
            int panelHeight = Math.max(210, Math.min(238, screenHeight - 20));
            int columns = panelWidth >= 330 ? 2 : 1;
            int gap = 8;
            int cardWidth = (panelWidth - 28 - gap * (columns - 1)) / columns;
            return new Layout(
                    (screenWidth - panelWidth) / 2,
                    (screenHeight - panelHeight) / 2,
                    panelWidth,
                    panelHeight,
                    columns,
                    cardWidth
            );
        }

        int panelRight() {
            return panelLeft + panelWidth;
        }

        int panelBottom() {
            return panelTop + panelHeight;
        }

        int perPage() {
            return columns * ROWS;
        }

        Rect card(int index) {
            int column = index % columns;
            int row = index / columns;
            int left = panelLeft + 14 + column * (cardWidth + 8);
            int top = panelTop + 35 + row * (CARD_HEIGHT + 5);
            return new Rect(left, top, left + cardWidth, top + CARD_HEIGHT);
        }

        Rect previousPage() {
            return new Rect(panelLeft + 14, panelBottom() - 25, panelLeft + 36, panelBottom() - 7);
        }

        Rect nextPage() {
            return new Rect(panelRight() - 36, panelBottom() - 25, panelRight() - 14, panelBottom() - 7);
        }
    }

    record Rect(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
