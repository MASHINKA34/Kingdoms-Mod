package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.dungeon.DungeonPayloads;
import com.geydev.kalfactions.menu.DungeonLootMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonLootScreen extends AbstractContainerScreen<DungeonLootMenu> {
    private static final int SLOT_BACKGROUND = 0xFF171A22;
    private static final int SLOT_BORDER = 0xFF3C4152;
    private static final int SELECTED_BORDER = 0xFFF3D58B;
    private static final int GOLD = 0xFFF3D58B;
    private static final int TEXT = 0xFFE8DFCB;
    private static final int MUTED = 0xFF9A8F7A;
    private static final int CHANCE_BAR = 0xFF9B30FF;

    private EditBox chanceBox;
    private EditBox minBox;
    private EditBox maxBox;
    private EditBox cooldownBox;
    private int selected = -1;

    public DungeonLootScreen(DungeonLootMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 186;
        imageHeight = 244;
        inventoryLabelY = DungeonLootMenu.INVENTORY_TOP - 10;
        titleLabelX = 12;
        titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();
        chanceBox = numberBox(leftPos + 44, topPos + 98, 28, 3);
        minBox = numberBox(leftPos + 106, topPos + 98, 24, 2);
        maxBox = numberBox(leftPos + 150, topPos + 98, 24, 2);
        cooldownBox = numberBox(leftPos + 12, topPos + 117, 28, 4);
        cooldownBox.setValue(cooldownValue());

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.apply"),
                button -> save(),
                leftPos + 12,
                topPos + 136,
                84,
                20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.refresh"),
                button -> PacketDistributor.sendToServer(new DungeonPayloads.C2SDungeonChestAction(
                        menu.pos(),
                        DungeonPayloads.C2SDungeonChestAction.ACTION_REFRESH,
                        -1
                )),
                leftPos + 100,
                topPos + 136,
                74,
                20
        ));
        syncSelection();
    }

    private void save() {
        applyEntry();
        PacketDistributor.sendToServer(new DungeonPayloads.C2SDungeonChestAction(
                menu.pos(),
                DungeonPayloads.C2SDungeonChestAction.ACTION_COOLDOWN,
                parse(cooldownBox, -1, -1, DungeonChestBlockEntity.MAX_COOLDOWN_HOURS)
        ));
    }

    private EditBox numberBox(int x, int y, int width, int maxLength) {
        EditBox box = new EditBox(font, x, y, width, 14, Component.empty());
        box.setMaxLength(maxLength);
        box.setFilter(text -> text.matches("\\d{0," + maxLength + "}"));
        box.setBordered(true);
        addRenderableWidget(box);
        return box;
    }

    private String cooldownValue() {
        DungeonChestBlockEntity chest = chest();
        int hours = chest == null ? -1 : chest.configuredCooldownHours();
        return hours < 0 ? "" : String.valueOf(hours);
    }

    private DungeonChestBlockEntity chest() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof DungeonChestBlockEntity chest
                ? chest
                : null;
    }

    private void applyEntry() {
        if (selected < 0) {
            return;
        }
        PacketDistributor.sendToServer(new DungeonPayloads.C2SDungeonChestEntry(
                menu.pos(),
                selected,
                parse(chanceBox, DungeonChestBlockEntity.DEFAULT_CHANCE, 0, 100),
                parse(minBox, 1, 1, DungeonChestBlockEntity.MAX_COUNT),
                parse(maxBox, 1, 1, DungeonChestBlockEntity.MAX_COUNT)
        ));
    }

    private static int parse(EditBox box, int fallback, int low, int high) {
        if (box == null || box.getValue().isBlank()) {
            return fallback;
        }
        try {
            return Math.clamp(Integer.parseInt(box.getValue()), low, high);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void syncSelection() {
        DungeonChestBlockEntity chest = chest();
        if (chest == null || selected < 0) {
            return;
        }
        chanceBox.setValue(String.valueOf(chest.chanceAt(selected)));
        minBox.setValue(String.valueOf(chest.minAt(selected)));
        maxBox.setValue(String.valueOf(chest.maxAt(selected)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = hoveredPlanSlot(mouseX, mouseY);
        if (slot >= 0 && getMenu().getCarried().isEmpty() && button == 0) {
            selected = slot;
            syncSelection();
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (slot >= 0) {
            selected = slot;
            syncSelection();
        }
        return handled;
    }

    private int hoveredPlanSlot(double mouseX, double mouseY) {
        int gridLeft = leftPos + DungeonLootMenu.GRID_LEFT;
        int gridTop = topPos + DungeonLootMenu.GRID_TOP;
        if (mouseX < gridLeft || mouseY < gridTop || mouseX >= gridLeft + 162 || mouseY >= gridTop + 54) {
            return -1;
        }
        int column = (int) ((mouseX - gridLeft) / 18);
        int row = (int) ((mouseY - gridTop) / 18);
        return row * 9 + column;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        KingdomsPanel.draw(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawSlots(graphics, leftPos + DungeonLootMenu.GRID_LEFT, topPos + DungeonLootMenu.GRID_TOP, 3);
        drawSlots(graphics, leftPos + DungeonLootMenu.INVENTORY_LEFT, topPos + DungeonLootMenu.INVENTORY_TOP, 3);
        drawSlots(graphics, leftPos + DungeonLootMenu.INVENTORY_LEFT, topPos + DungeonLootMenu.INVENTORY_TOP + 58, 1);
    }

    private void drawSlots(GuiGraphics graphics, int left, int top, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                int x = left + column * 18;
                int y = top + row * 18;
                graphics.fill(x, y, x + 18, y + 18, SLOT_BORDER);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BACKGROUND);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        graphics.drawString(font, playerInventoryTitle, 12, inventoryLabelY, MUTED, false);
        graphics.drawString(font,
                Component.translatable("screen.kingdoms.dungeon_chest.hint"),
                12, 21, MUTED, false);

        DungeonChestBlockEntity chest = chest();
        for (int slot = 0; slot < DungeonLootMenu.PLAN_SIZE; slot++) {
            int x = DungeonLootMenu.GRID_LEFT + (slot % 9) * 18;
            int y = DungeonLootMenu.GRID_TOP + (slot / 9) * 18;
            if (slot == selected) {
                graphics.renderOutline(x, y, 18, 18, SELECTED_BORDER);
            }
            if (chest == null || menu.planItem(slot).isEmpty()) {
                continue;
            }
            int height = Math.max(1, chest.chanceAt(slot) * 16 / 100);
            graphics.fill(x + 1, y + 17 - height, x + 2, y + 17, CHANCE_BAR);
            String range = chest.minAt(slot) == chest.maxAt(slot)
                    ? String.valueOf(chest.minAt(slot))
                    : chest.minAt(slot) + "-" + chest.maxAt(slot);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 250.0F);
            graphics.drawString(font, range, x + 17 - font.width(range), y + 10, 0xFFFFFFFF, true);
            graphics.pose().popPose();
        }

        Component label = selected < 0 || chest == null || menu.planItem(selected).isEmpty()
                ? Component.translatable("screen.kingdoms.dungeon_chest.pick_slot")
                : menu.planItem(selected).getHoverName();
        graphics.drawString(font, label, 12, 88, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.dungeon_chest.chance"), 12, 101, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.dungeon_chest.from"), 88, 101, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.dungeon_chest.to"), 134, 101, MUTED, false);
        graphics.drawString(font,
                Component.translatable(
                        "screen.kingdoms.dungeon_chest.cooldown_label",
                        chest == null ? 0 : chest.effectiveCooldownHours()
                ),
                44, 120, MUTED, false);
    }

    @Override
    public List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        DungeonChestBlockEntity chest = chest();
        if (chest != null && hoveredSlot != null && hoveredSlot.index < DungeonLootMenu.PLAN_SIZE) {
            lines.add(Component.translatable(
                    "screen.kingdoms.dungeon_chest.tooltip",
                    chest.chanceAt(hoveredSlot.index),
                    chest.minAt(hoveredSlot.index),
                    chest.maxAt(hoveredSlot.index)
            ));
        }
        return lines;
    }
}
