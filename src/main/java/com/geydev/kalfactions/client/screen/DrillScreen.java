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
    private static final int READOUT_X = 268;
    private static final int READOUT_Y = 72;
    private static final int READOUT_WIDTH = 70;
    private static final int READOUT_HEIGHT = 46;
    private static final int TARGET_ICON_X = 295;
    private static final int TARGET_ICON_Y = 76;
    private static final int TARGET_ICON_SIZE = 16;
    private static final int CHANGE_BUTTON_X = 268;
    private static final int CHANGE_BUTTON_Y = 121;
    private static final int CHANGE_BUTTON_WIDTH = 70;
    private static final int CHANGE_BUTTON_HEIGHT = 16;
    private boolean openingSelector;

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
    protected void init() {
        super.init();
        Layout layout = layout();
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.drill.change_target"),
                button -> openSelector(),
                layout.changeButtonX(),
                layout.changeButtonY(),
                CHANGE_BUTTON_WIDTH,
                CHANGE_BUTTON_HEIGHT
        ));
        DrillPayloads.S2CTargets state = ClientDrillTargets.get(menu.containerId);
        if (state != null && !state.hasTarget() && minecraft != null) {
            minecraft.execute(this::openSelector);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderProgressTooltip(graphics, mouseX, mouseY);
        renderTargetTooltip(graphics, mouseX, mouseY);
    }

    private void renderTargetTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Layout layout = layout();
        if (mouseX < layout.targetIconX() - 1
                || mouseX >= layout.targetIconX() + TARGET_ICON_SIZE + 1
                || mouseY < layout.targetIconY() - 1
                || mouseY >= layout.targetIconY() + TARGET_ICON_SIZE + 1) {
            return;
        }
        DrillPayloads.TargetInfo selected = selectedTarget(ClientDrillTargets.get(menu.containerId));
        if (selected == null) {
            graphics.renderTooltip(
                    font,
                    Component.translatable("screen.kingdoms.drill.no_target"),
                    mouseX,
                    mouseY
            );
            return;
        }
        ChunkPos chunk = new ChunkPos(selected.chunk());
        graphics.renderComponentTooltip(
                font,
                List.of(
                        ResourceClusterType.parse(selected.type())
                                .map(type -> (Component) Component.literal(type.displayName()))
                                .orElse(Component.literal(selected.type())),
                        Component.translatable(
                                "screen.kingdoms.drill.selector_details",
                                chunk.x,
                                chunk.z,
                                selected.richness()
                        )
                ),
                mouseX,
                mouseY
        );
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        graphics.fill(
                leftPos + PROGRESS_X - 1,
                topPos + PROGRESS_Y - 1,
                leftPos + PROGRESS_X + FILL_WIDTH + 1,
                topPos + PROGRESS_Y + FILL_HEIGHT + 1,
                0xFF0B0D12
        );
        float fraction = menu.hasTarget() ? menu.progressFraction() : 0.0F;
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
        renderSelectedTarget(graphics);
        renderDrillSlots(graphics);
        renderPlayerInventoryPanel(graphics);
    }

    private void renderSelectedTarget(GuiGraphics graphics) {
        Layout layout = layout();
        DrillPayloads.TargetInfo selected = selectedTarget(ClientDrillTargets.get(menu.containerId));
        ResourceClusterType type = selected == null
                ? null
                : ResourceClusterType.parse(selected.type()).orElse(null);

        int plateLeft = leftPos + READOUT_X;
        int plateTop = topPos + READOUT_Y;
        int plateRight = plateLeft + READOUT_WIDTH;
        graphics.fill(plateLeft, plateTop, plateRight, plateTop + READOUT_HEIGHT, 0xFF080B10);
        graphics.fill(plateLeft + 1, plateTop + 1, plateRight - 1, plateTop + READOUT_HEIGHT - 1, 0xFF17222F);
        graphics.fill(plateLeft, plateTop, plateRight, plateTop + 2, 0xFFC6A24C);

        graphics.fill(
                layout.targetIconX() - 1,
                layout.targetIconY() - 1,
                layout.targetIconX() + TARGET_ICON_SIZE + 1,
                layout.targetIconY() + TARGET_ICON_SIZE + 1,
                selected == null ? 0xFF565B62 : 0xFFD3A73D
        );
        graphics.fill(
                layout.targetIconX(),
                layout.targetIconY(),
                layout.targetIconX() + TARGET_ICON_SIZE,
                layout.targetIconY() + TARGET_ICON_SIZE,
                0xFF17202A
        );
        if (type != null) {
            graphics.renderItem(new ItemStack(type.displayItem()), layout.targetIconX(), layout.targetIconY());
        }

        Component name = type == null
                ? Component.translatable("screen.kingdoms.drill.no_target")
                : Component.literal(type.displayName());
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(name, READOUT_WIDTH - 8);
        int color = type == null ? 0xFF9AA6B2 : 0xFFF0D99D;
        for (int index = 0; index < Math.min(2, lines.size()); index++) {
            net.minecraft.util.FormattedCharSequence line = lines.get(index);
            graphics.drawString(
                    font,
                    line,
                    plateLeft + (READOUT_WIDTH - font.width(line)) / 2,
                    plateTop + 26 + index * 10,
                    color,
                    false
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
        if (!menu.hasTarget() || !isMouseOverProgress(mouseX, mouseY)) {
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

    @Override
    public void removed() {
        if (!openingSelector) {
            ClientDrillTargets.clear(menu.containerId);
        }
        super.removed();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE8D6A0, true);
    }

    private static String formatTicks(int ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public void acceptState(DrillPayloads.S2CTargets state) {
        if (!state.hasTarget()) {
            openSelector();
        }
    }

    void returnFromSelector() {
        openingSelector = false;
        if (minecraft != null) {
            minecraft.setScreen(this);
        }
    }

    private void openSelector() {
        if (minecraft == null || minecraft.screen instanceof DrillTargetScreen) {
            return;
        }
        openingSelector = true;
        PacketDistributor.sendToServer(new DrillPayloads.C2SRequestTargets(menu.containerId));
        minecraft.setScreen(new DrillTargetScreen(this, ClientDrillTargets.get(menu.containerId)));
    }

    private Layout layout() {
        return new Layout(leftPos, topPos);
    }

    private static DrillPayloads.TargetInfo selectedTarget(DrillPayloads.S2CTargets state) {
        if (state == null || !state.hasTarget()) {
            return null;
        }
        return state.targets().stream().filter(DrillPayloads.TargetInfo::selected).findFirst().orElse(null);
    }

    record Layout(int left, int top) {
        int targetIconX() {
            return left + TARGET_ICON_X;
        }

        int targetIconY() {
            return top + TARGET_ICON_Y;
        }

        int changeButtonX() {
            return left + CHANGE_BUTTON_X;
        }

        int changeButtonY() {
            return top + CHANGE_BUTTON_Y;
        }
    }
}
