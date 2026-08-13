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
import net.minecraft.util.FormattedCharSequence;
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
    private static final int MARGIN = 12;

    private EditBox chanceBox;
    private EditBox minBox;
    private EditBox maxBox;
    private EditBox cooldownBox;
    private List<FormattedCharSequence> hintLines = List.of();
    private int selected = -1;

    public DungeonLootScreen(DungeonLootMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 200;
        imageHeight = 265;
        inventoryLabelY = DungeonLootMenu.INVENTORY_TOP - 10;
        titleLabelX = MARGIN;
        titleLabelY = 7;
    }

    @Override
    protected void init() {
        super.init();
        hintLines = font.split(
                Component.translatable("screen.kingdoms.dungeon_chest.hint"),
                imageWidth - MARGIN * 2
        );
        chanceBox = numberBox(leftPos + 46, topPos + 109, 28, 3);
        minBox = numberBox(leftPos + 98, topPos + 109, 24, 2);
        maxBox = numberBox(leftPos + 146, topPos + 109, 24, 2);
        cooldownBox = numberBox(leftPos + 84, topPos + 128, 28, 4);
        cooldownBox.setValue(cooldownValue());

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.apply"),
                button -> save(true),
                leftPos + MARGIN,
                topPos + 147,
                85,
                20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.refresh"),
                button -> PacketDistributor.sendToServer(
                        new DungeonPayloads.C2SDungeonChestRefill(menu.pos())
                ),
                leftPos + 103,
                topPos + 147,
                85,
                20
        ));
        syncSelection();
    }

    private void save(boolean announce) {
        PacketDistributor.sendToServer(new DungeonPayloads.C2SDungeonChestEntry(
                menu.pos(),
                selected,
                parse(chanceBox, DungeonChestBlockEntity.DEFAULT_CHANCE, 0, 100),
                parse(minBox, 1, 1, DungeonChestBlockEntity.MAX_COUNT),
                parse(maxBox, 1, 1, DungeonChestBlockEntity.MAX_COUNT),
                parse(cooldownBox, -1, -1, DungeonChestBlockEntity.MAX_COOLDOWN_HOURS),
                announce
        ));
    }

    private void autoSave() {
        DungeonChestBlockEntity chest = chest();
        if (chest == null) {
            return;
        }
        int chance = parse(chanceBox, DungeonChestBlockEntity.DEFAULT_CHANCE, 0, 100);
        int min = parse(minBox, 1, 1, DungeonChestBlockEntity.MAX_COUNT);
        int max = parse(maxBox, 1, 1, DungeonChestBlockEntity.MAX_COUNT);
        int cooldown = parse(cooldownBox, -1, -1, DungeonChestBlockEntity.MAX_COOLDOWN_HOURS);
        boolean entryChanged = selected >= 0
                && (chest.chanceAt(selected) != chance
                        || chest.minAt(selected) != Math.min(min, max)
                        || chest.maxAt(selected) != Math.max(min, max));
        if (!entryChanged && chest.configuredCooldownHours() == cooldown) {
            return;
        }
        save(false);
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
        if (slot >= 0 && slot != selected) {
            autoSave();
        }
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

    @Override
    public void removed() {
        autoSave();
        super.removed();
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
        graphics.drawString(font, playerInventoryTitle, MARGIN, inventoryLabelY, MUTED, false);
        for (int line = 0; line < Math.min(2, hintLines.size()); line++) {
            graphics.drawString(font, hintLines.get(line), MARGIN, 18 + line * 10, MUTED, false);
        }

        if (selected >= 0) {
            graphics.renderOutline(
                    DungeonLootMenu.GRID_LEFT + (selected % 9) * 18,
                    DungeonLootMenu.GRID_TOP + (selected / 9) * 18,
                    18,
                    18,
                    SELECTED_BORDER
            );
        }

        DungeonChestBlockEntity chest = chest();
        Component label = selected < 0 || menu.planItem(selected).isEmpty()
                ? Component.translatable("screen.kingdoms.dungeon_chest.pick_slot")
                : menu.planItem(selected).getHoverName();
        graphics.drawString(font, label, MARGIN, 98, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.dungeon_chest.chance"), MARGIN, 112, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.dungeon_chest.from"), 80, 112, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.dungeon_chest.to"), 128, 112, MUTED, false);
        graphics.drawString(font,
                Component.translatable("screen.kingdoms.dungeon_chest.cooldown_label"),
                MARGIN, 131, MUTED, false);
        graphics.drawString(font,
                Component.translatable(
                        "screen.kingdoms.dungeon_chest.cooldown_global",
                        chest == null ? 0 : chest.effectiveCooldownHours()
                ),
                116, 131, MUTED, false);
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
