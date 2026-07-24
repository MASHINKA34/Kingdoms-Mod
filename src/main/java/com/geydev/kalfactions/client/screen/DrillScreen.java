package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientDrillTargets;
import com.geydev.kalfactions.menu.DrillMenu;
import com.geydev.kalfactions.outpost.cluster.DrillPayloads;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterType;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DrillScreen extends AbstractContainerScreen<DrillMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/drill/drill_gui.png");
    private static final ResourceLocation PROGRESS =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/drill/progress.png");
    private static final int BACKGROUND_WIDTH = 370;
    private static final int BACKGROUND_HEIGHT = 188;
    private static final int PROGRESS_WIDTH = 162;
    private static final int PROGRESS_HEIGHT = 20;
    private static final int SCREEN_HEIGHT = 244;
    private static final int PROGRESS_X = 116;
    private static final int PROGRESS_Y = 53;
    private static final int FILL_WIDTH = 136;
    private static final int FILL_HEIGHT = 8;
    private static final int INVENTORY_PANEL_TOP = 150;
    private static final int INVENTORY_LABEL_Y = 158;
    private static final int TARGETS_PER_PAGE = 10;
    private static final int TARGET_START_X = 55;
    private static final int TARGET_Y = 12;
    private static final int TARGET_STEP = 26;
    private static final int TARGET_SIZE = 20;
    private int targetPage;

    public DrillScreen(DrillMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BACKGROUND_WIDTH;
        imageHeight = SCREEN_HEIGHT;
        inventoryLabelX = DrillMenu.PLAYER_INVENTORY_X;
        inventoryLabelY = INVENTORY_LABEL_Y;
        titleLabelX = 0;
        titleLabelY = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderProgressTooltip(graphics, mouseX, mouseY);
        renderTargetTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(
                leftPos + PROGRESS_X - 1,
                topPos + PROGRESS_Y - 1,
                leftPos + PROGRESS_X + FILL_WIDTH + 1,
                topPos + PROGRESS_Y + FILL_HEIGHT + 1,
                0xFF0B0D12
        );
        float fraction = menu.progressFraction();
        int fillWidth = Math.round(FILL_WIDTH * fraction);
        if (fillWidth > 0) {
            int srcWidth = Math.max(1, Math.round(PROGRESS_WIDTH * fraction));
            graphics.blit(
                    PROGRESS,
                    leftPos + PROGRESS_X,
                    topPos + PROGRESS_Y,
                    fillWidth,
                    FILL_HEIGHT,
                    0.0F,
                    0.0F,
                    srcWidth,
                    PROGRESS_HEIGHT,
                    PROGRESS_WIDTH,
                    PROGRESS_HEIGHT
            );
        }
        graphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        renderTargets(graphics);
        renderDrillSlots(graphics);
        renderPlayerInventoryPanel(graphics);
    }

    private void renderTargets(GuiGraphics graphics) {
        DrillPayloads.S2CTargets state = ClientDrillTargets.get(menu.containerId);
        if (state == null || state.targets().isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.kingdoms.drill.no_targets"),
                    imageWidth / 2,
                    TARGET_Y + 6,
                    0xFF9A9A9A
            );
            return;
        }
        int pages = pageCount(state.targets());
        targetPage = Math.clamp(targetPage, 0, pages - 1);
        int from = targetPage * TARGETS_PER_PAGE;
        int to = Math.min(state.targets().size(), from + TARGETS_PER_PAGE);
        for (int index = from; index < to; index++) {
            DrillPayloads.TargetInfo target = state.targets().get(index);
            int x = TARGET_START_X + (index - from) * TARGET_STEP;
            int color = target.selected() ? 0xFF42B86B : target.available() ? 0xFFD3A73D : 0xFF565B62;
            graphics.fill(x - 2, TARGET_Y - 2, x + 20, TARGET_Y + 20, 0xFF080B10);
            graphics.fill(x - 1, TARGET_Y - 1, x + 19, TARGET_Y + 19, color);
            graphics.fill(x, TARGET_Y, x + 18, TARGET_Y + 18, 0xFF17202A);
            ResourceClusterType.parse(target.type()).ifPresent(type ->
                    graphics.renderItem(new ItemStack(type.displayItem()), x + 1, TARGET_Y + 1)
            );
        }
        if (pages > 1) {
            graphics.drawString(font, "<", TARGET_START_X - 20, TARGET_Y + 5, targetPage > 0 ? 0xFFFFFFFF : 0xFF555555);
            graphics.drawString(
                    font,
                    ">",
                    TARGET_START_X + TARGETS_PER_PAGE * TARGET_STEP + 1,
                    TARGET_Y + 5,
                    targetPage + 1 < pages ? 0xFFFFFFFF : 0xFF555555
            );
        }
    }

    private void renderDrillSlots(GuiGraphics graphics) {
        for (int slot = 0; slot < 18; slot++) {
            int row = slot / DrillMenu.DRILL_COLUMNS;
            int column = slot % DrillMenu.DRILL_COLUMNS;
            goldSlotFrame(
                    graphics,
                    leftPos + DrillMenu.DRILL_SLOT_X + column * DrillMenu.DRILL_SLOT_STEP_X,
                    topPos + DrillMenu.DRILL_SLOT_Y + row * DrillMenu.DRILL_SLOT_STEP_Y
            );
        }
    }

    private void renderPlayerInventoryPanel(GuiGraphics graphics) {
        int panelTop = topPos + INVENTORY_PANEL_TOP;
        graphics.fill(leftPos, panelTop, leftPos + imageWidth, topPos + imageHeight, 0xFF101820);
        graphics.fill(leftPos + 4, panelTop + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF172331);
        graphics.fill(leftPos + 8, panelTop + 8, leftPos + imageWidth - 8, topPos + imageHeight - 8, 0xFF0D141D);
        graphics.fill(leftPos, panelTop, leftPos + imageWidth, panelTop + 2, 0xFFB98E35);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, 0xFF4D3210);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                inventorySlotFrame(
                        graphics,
                        leftPos + DrillMenu.PLAYER_INVENTORY_X + column * 18,
                        topPos + DrillMenu.PLAYER_INVENTORY_Y + row * 18
                );
            }
        }
        for (int column = 0; column < 9; column++) {
            inventorySlotFrame(
                    graphics,
                    leftPos + DrillMenu.PLAYER_INVENTORY_X + column * 18,
                    topPos + DrillMenu.PLAYER_HOTBAR_Y
            );
        }
    }

    private static void goldSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 16, y + 16, 0xE40A1119);
        graphics.fill(x - 2, y - 2, x + 18, y - 1, 0xFFE0B857);
        graphics.fill(x - 2, y + 17, x + 18, y + 18, 0xFF5D3B13);
        graphics.fill(x - 2, y - 1, x - 1, y + 17, 0xFFB8872F);
        graphics.fill(x + 17, y - 1, x + 18, y + 17, 0xFF6D4919);
    }

    private static void inventorySlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF05080D);
        graphics.fill(x, y, x + 16, y + 16, 0xFF24303D);
        graphics.fill(x, y, x + 16, y + 1, 0xFF546373);
        graphics.fill(x, y, x + 1, y + 16, 0xFF3C4855);
        graphics.fill(x, y + 15, x + 16, y + 16, 0xFF121923);
        graphics.fill(x + 15, y, x + 16, y + 16, 0xFF121923);
    }

    private void renderProgressTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseOverProgress(mouseX, mouseY)) {
            return;
        }
        Component timer = Component.translatable("screen.kingdoms.drill.remaining", formatTicks(menu.remainingTicks()));
        graphics.renderTooltip(font, timer, mouseX, mouseY);
    }

    private boolean isMouseOverProgress(int mouseX, int mouseY) {
        return mouseX >= leftPos + PROGRESS_X
                && mouseX < leftPos + PROGRESS_X + FILL_WIDTH
                && mouseY >= topPos + PROGRESS_Y
                && mouseY < topPos + PROGRESS_Y + FILL_HEIGHT;
    }

    private void renderTargetTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        DrillPayloads.TargetInfo target = targetAt(mouseX, mouseY);
        if (target == null) {
            return;
        }
        ChunkPos chunk = new ChunkPos(target.chunk());
        Component resource = ResourceClusterType.parse(target.type())
                .map(type -> type.displayItem().getDescription())
                .orElse(Component.literal(target.type()));
        String statusKey = target.selected()
                ? "screen.kingdoms.drill.target_selected"
                : target.available()
                        ? "screen.kingdoms.drill.target_available"
                        : "screen.kingdoms.drill.target_busy";
        graphics.renderTooltip(
                font,
                List.of(
                        resource,
                        Component.translatable(
                                "screen.kingdoms.drill.target_details",
                                chunk.x,
                                chunk.z,
                                target.richness()
                        ),
                        Component.translatable(statusKey)
                ),
                java.util.Optional.empty(),
                mouseX,
                mouseY
        );
    }

    private DrillPayloads.TargetInfo targetAt(double mouseX, double mouseY) {
        DrillPayloads.S2CTargets state = ClientDrillTargets.get(menu.containerId);
        if (state == null || mouseY < topPos + TARGET_Y - 2 || mouseY >= topPos + TARGET_Y + TARGET_SIZE) {
            return null;
        }
        int local = (int) mouseX - leftPos - TARGET_START_X;
        if (local < -2) {
            return null;
        }
        int column = local / TARGET_STEP;
        if (column < 0 || column >= TARGETS_PER_PAGE || local % TARGET_STEP >= TARGET_SIZE) {
            return null;
        }
        int index = targetPage * TARGETS_PER_PAGE + column;
        return index < state.targets().size() ? state.targets().get(index) : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            DrillPayloads.S2CTargets state = ClientDrillTargets.get(menu.containerId);
            if (state != null && pageCount(state.targets()) > 1
                    && mouseY >= topPos + TARGET_Y - 2
                    && mouseY < topPos + TARGET_Y + TARGET_SIZE) {
                if (mouseX >= leftPos + TARGET_START_X - 22 && mouseX < leftPos + TARGET_START_X - 4 && targetPage > 0) {
                    targetPage--;
                    return true;
                }
                if (mouseX >= leftPos + TARGET_START_X + TARGETS_PER_PAGE * TARGET_STEP
                        && mouseX < leftPos + TARGET_START_X + TARGETS_PER_PAGE * TARGET_STEP + 18
                        && targetPage + 1 < pageCount(state.targets())) {
                    targetPage++;
                    return true;
                }
            }
            DrillPayloads.TargetInfo target = targetAt(mouseX, mouseY);
            if (target != null && target.available()) {
                PacketDistributor.sendToServer(new DrillPayloads.C2SSelectTarget(menu.containerId, target.chunk()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        ClientDrillTargets.clear(menu.containerId);
        super.removed();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE8D6A0, true);
        if (menu.hasFiniteDeposit()) {
            Component reserve = menu.depositRemaining() == 0
                    ? Component.translatable("screen.kingdoms.drill.depleted")
                    : Component.translatable(
                            "screen.kingdoms.drill.reserve",
                            menu.depositRemaining(),
                            menu.depositOriginal()
                    );
            graphics.drawCenteredString(font, reserve, imageWidth / 2, 34, 0xFFE8D6A0);
        }
    }

    private static String formatTicks(int ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static int pageCount(List<DrillPayloads.TargetInfo> targets) {
        return Math.max(1, (targets.size() + TARGETS_PER_PAGE - 1) / TARGETS_PER_PAGE);
    }
}
