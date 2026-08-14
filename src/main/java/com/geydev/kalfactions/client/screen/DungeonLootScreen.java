package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.client.ClientChestTemplates;
import com.geydev.kalfactions.dungeon.ChestTemplate;
import com.geydev.kalfactions.dungeon.DungeonPayloads;
import com.geydev.kalfactions.menu.DungeonLootMenu;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private static final int ROW_SELECTED = 0x60C9A24C;
    private static final int ROW_HOVER = 0x30000000;

    static final int PLAN_WIDTH = 200;
    static final int TEMPLATE_WIDTH = 340;
    static final int TEMPLATE_HEIGHT = 265;
    static final int LIST_LEFT = 12;
    static final int LIST_TOP = 28;
    static final int LIST_WIDTH = 148;
    static final int LIST_ROW_HEIGHT = 24;
    static final int LIST_ROWS = 6;
    static final int NAME_BOX_TOP = 202;
    static final int PREVIEW_LEFT = 166;
    static final int PREVIEW_TOP = 44;
    static final int PREVIEW_WIDTH = 162;
    static final int PREVIEW_HEIGHT = 54;
    static final int LAST_ROW_BOTTOM = 242;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());

    private EditBox chanceBox;
    private EditBox minBox;
    private EditBox maxBox;
    private EditBox cooldownBox;
    private EditBox templateNameBox;
    private KingdomsButton saveTemplateButton;
    private KingdomsButton applyButton;
    private KingdomsButton applyAllButton;
    private KingdomsButton renameButton;
    private KingdomsButton deleteButton;
    private KingdomsButton cooldownToggle;
    private List<FormattedCharSequence> hintLines = List.of();
    private int selected = -1;
    private Tab tab = Tab.PLAN;
    private UUID selectedTemplate;
    private int templateScroll;
    private boolean applyTemplateCooldown = true;
    private boolean confirmSave;
    private boolean confirmApplyAll;
    private boolean confirmDelete;

    public DungeonLootScreen(DungeonLootMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PLAN_WIDTH;
        imageHeight = TEMPLATE_HEIGHT;
        inventoryLabelY = DungeonLootMenu.INVENTORY_TOP - 10;
        titleLabelX = MARGIN;
        titleLabelY = 7;
    }

    @Override
    protected void init() {
        imageWidth = tab == Tab.PLAN ? PLAN_WIDTH : TEMPLATE_WIDTH;
        super.init();
        menu.setSlotsVisible(tab == Tab.PLAN);
        int tabTop = Math.max(0, topPos - 21);
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.tab_plan"),
                button -> switchTab(Tab.PLAN),
                width / 2 - 86,
                tabTop,
                84,
                20
        )).active = tab != Tab.PLAN;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.tab_templates"),
                button -> switchTab(Tab.TEMPLATES),
                width / 2 + 2,
                tabTop,
                84,
                20
        )).active = tab != Tab.TEMPLATES;
        if (tab == Tab.PLAN) {
            initPlan();
        } else {
            initTemplates();
        }
    }

    private void initPlan() {
        templateNameBox = null;
        saveTemplateButton = null;
        applyButton = null;
        applyAllButton = null;
        renameButton = null;
        deleteButton = null;
        cooldownToggle = null;
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

    private void initTemplates() {
        chanceBox = null;
        minBox = null;
        maxBox = null;
        cooldownBox = null;
        templateNameBox = new EditBox(font, leftPos + LIST_LEFT + 2, topPos + NAME_BOX_TOP, LIST_WIDTH - 4, 16,
                Component.translatable("screen.kingdoms.dungeon_chest.template_name"));
        templateNameBox.setMaxLength(ChestTemplate.MAX_NAME_LENGTH);
        templateNameBox.setHint(Component.translatable("screen.kingdoms.dungeon_chest.template_name"));
        templateNameBox.setValue(selectedName());
        addRenderableWidget(templateNameBox);

        saveTemplateButton = addRenderableWidget(KingdomsButton.create(
                Component.empty(),
                button -> saveTemplate(),
                leftPos + LIST_LEFT,
                topPos + LAST_ROW_BOTTOM - 20,
                LIST_WIDTH,
                20
        ));
        cooldownToggle = addRenderableWidget(KingdomsButton.create(
                Component.empty(),
                button -> applyTemplateCooldown = !applyTemplateCooldown,
                leftPos + PREVIEW_LEFT,
                topPos + 128,
                162,
                18
        ));
        applyButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.template_apply"),
                button -> sendTemplate(DungeonPayloads.C2SChestTemplateAction.APPLY, false),
                leftPos + PREVIEW_LEFT,
                topPos + 150,
                162,
                20
        ));
        applyAllButton = addRenderableWidget(KingdomsButton.create(
                Component.empty(),
                button -> applyAll(),
                leftPos + PREVIEW_LEFT,
                topPos + 172,
                162,
                20
        ));
        renameButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.template_rename"),
                button -> sendTemplate(DungeonPayloads.C2SChestTemplateAction.RENAME, false),
                leftPos + PREVIEW_LEFT,
                topPos + 194,
                79,
                20
        ));
        deleteButton = addRenderableWidget(KingdomsButton.create(
                Component.empty(),
                button -> deleteTemplate(),
                leftPos + PREVIEW_LEFT + 83,
                topPos + 194,
                79,
                20
        ));
        refreshTemplateButtons();
    }

    private void switchTab(Tab target) {
        if (tab == target) {
            return;
        }
        if (tab == Tab.PLAN) {
            autoSave();
        }
        tab = target;
        confirmSave = false;
        confirmApplyAll = false;
        confirmDelete = false;
        rebuildWidgets();
        if (tab == Tab.TEMPLATES) {
            send(DungeonPayloads.C2SChestTemplateAction.SYNC, null, "", false, false);
        }
    }

    public void acceptTemplates() {
        confirmSave = false;
        confirmApplyAll = false;
        confirmDelete = false;
        if (selectedTemplate != null && template(selectedTemplate) == null) {
            selectedTemplate = null;
        }
        templateScroll = Math.clamp(templateScroll, 0, maxTemplateScroll());
    }

    private List<DungeonPayloads.ChestTemplateView> templates() {
        return ClientChestTemplates.templates(menu.pos());
    }

    private DungeonPayloads.ChestTemplateView template(UUID id) {
        return templates().stream()
                .filter(view -> view.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private DungeonPayloads.ChestTemplateView selectedView() {
        return selectedTemplate == null ? null : template(selectedTemplate);
    }

    private String selectedName() {
        DungeonPayloads.ChestTemplateView view = selectedView();
        return view == null ? "" : view.name();
    }

    private void saveTemplate() {
        String name = templateNameBox == null ? "" : templateNameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        boolean taken = templates().stream().anyMatch(view -> view.name().equalsIgnoreCase(name));
        if (taken && !confirmSave) {
            confirmSave = true;
            return;
        }
        confirmSave = false;
        send(DungeonPayloads.C2SChestTemplateAction.SAVE, null, name, false, taken);
    }

    private void applyAll() {
        if (selectedTemplate == null) {
            return;
        }
        if (!confirmApplyAll) {
            confirmApplyAll = true;
            return;
        }
        confirmApplyAll = false;
        sendTemplate(DungeonPayloads.C2SChestTemplateAction.APPLY_ALL, false);
    }

    private void deleteTemplate() {
        if (selectedTemplate == null) {
            return;
        }
        if (!confirmDelete) {
            confirmDelete = true;
            return;
        }
        confirmDelete = false;
        sendTemplate(DungeonPayloads.C2SChestTemplateAction.DELETE, false);
    }

    private void sendTemplate(int action, boolean overwrite) {
        if (selectedTemplate == null) {
            return;
        }
        String name = templateNameBox == null ? "" : templateNameBox.getValue().trim();
        send(action, selectedTemplate, name, applyTemplateCooldown, overwrite);
    }

    private void send(int action, UUID templateId, String name, boolean applyCooldown, boolean overwrite) {
        PacketDistributor.sendToServer(new DungeonPayloads.C2SChestTemplateAction(
                menu.pos(),
                UUID.randomUUID(),
                action,
                templateId,
                name,
                applyCooldown,
                overwrite
        ));
    }

    private void refreshTemplateButtons() {
        boolean picked = selectedTemplate != null;
        if (saveTemplateButton != null) {
            saveTemplateButton.setMessage(Component.translatable(confirmSave
                    ? "screen.kingdoms.dungeon_chest.template_overwrite"
                    : "screen.kingdoms.dungeon_chest.template_save"));
            saveTemplateButton.active = templateNameBox != null && !templateNameBox.getValue().isBlank();
        }
        if (cooldownToggle != null) {
            cooldownToggle.setMessage(Component.translatable(applyTemplateCooldown
                    ? "screen.kingdoms.dungeon_chest.template_cooldown_on"
                    : "screen.kingdoms.dungeon_chest.template_cooldown_off"));
        }
        if (applyButton != null) {
            applyButton.active = picked;
        }
        if (applyAllButton != null) {
            applyAllButton.setMessage(Component.translatable(confirmApplyAll
                    ? "screen.kingdoms.dungeon_chest.template_apply_all_confirm"
                    : "screen.kingdoms.dungeon_chest.template_apply_all"));
            applyAllButton.active = picked;
        }
        if (renameButton != null) {
            renameButton.active = picked && templateNameBox != null && !templateNameBox.getValue().isBlank();
        }
        if (deleteButton != null) {
            deleteButton.setMessage(Component.translatable(confirmDelete
                    ? "screen.kingdoms.dungeon_chest.template_delete_confirm"
                    : "screen.kingdoms.dungeon_chest.template_delete"));
            deleteButton.active = picked;
        }
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
        if (chest == null || chanceBox == null || minBox == null || maxBox == null || cooldownBox == null) {
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
        if (chest == null || selected < 0 || chanceBox == null) {
            return;
        }
        chanceBox.setValue(String.valueOf(chest.chanceAt(selected)));
        minBox.setValue(String.valueOf(chest.minAt(selected)));
        maxBox.setValue(String.valueOf(chest.maxAt(selected)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.TEMPLATES) {
            int row = hoveredTemplateRow(mouseX, mouseY);
            if (row >= 0) {
                DungeonPayloads.ChestTemplateView view = templates().get(row);
                selectedTemplate = view.id();
                confirmSave = false;
                confirmApplyAll = false;
                confirmDelete = false;
                if (templateNameBox != null) {
                    templateNameBox.setValue(view.name());
                }
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.TEMPLATES && overList(mouseX, mouseY)) {
            int updated = Math.clamp(templateScroll - (int) Math.signum(scrollY), 0, maxTemplateScroll());
            if (updated != templateScroll) {
                templateScroll = updated;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        autoSave();
        ClientChestTemplates.clear();
        super.removed();
    }

    private int maxTemplateScroll() {
        return Math.max(0, templates().size() - LIST_ROWS);
    }

    private boolean overList(double mouseX, double mouseY) {
        return insideList(leftPos + LIST_LEFT, topPos + LIST_TOP, mouseX, mouseY);
    }

    private int hoveredTemplateRow(double mouseX, double mouseY) {
        return templateRowAt(
                leftPos + LIST_LEFT,
                topPos + LIST_TOP,
                mouseX,
                mouseY,
                templateScroll,
                templates().size()
        );
    }

    private int hoveredPreviewSlot(double mouseX, double mouseY) {
        return previewSlotAt(leftPos + PREVIEW_LEFT, topPos + PREVIEW_TOP, mouseX, mouseY);
    }

    static boolean insideList(int left, int top, double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + LIST_WIDTH
                && mouseY >= top && mouseY < top + LIST_ROWS * LIST_ROW_HEIGHT;
    }

    static int templateRowAt(int left, int top, double mouseX, double mouseY, int scroll, int size) {
        if (!insideList(left, top, mouseX, mouseY)) {
            return -1;
        }
        int row = (int) ((mouseY - top) / LIST_ROW_HEIGHT) + scroll;
        return row >= 0 && row < size ? row : -1;
    }

    static int previewSlotAt(int left, int top, double mouseX, double mouseY) {
        if (mouseX < left || mouseY < top
                || mouseX >= left + PREVIEW_WIDTH || mouseY >= top + PREVIEW_HEIGHT) {
            return -1;
        }
        return (int) ((mouseY - top) / 18) * 9 + (int) ((mouseX - left) / 18);
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
        if (tab == Tab.PLAN) {
            drawSlots(graphics, leftPos + DungeonLootMenu.GRID_LEFT, topPos + DungeonLootMenu.GRID_TOP, 3);
            drawSlots(graphics, leftPos + DungeonLootMenu.INVENTORY_LEFT, topPos + DungeonLootMenu.INVENTORY_TOP, 3);
            drawSlots(graphics, leftPos + DungeonLootMenu.INVENTORY_LEFT, topPos + DungeonLootMenu.INVENTORY_TOP + 58, 1);
            return;
        }
        graphics.fill(
                leftPos + LIST_LEFT,
                topPos + LIST_TOP,
                leftPos + LIST_LEFT + LIST_WIDTH,
                topPos + LIST_TOP + LIST_ROWS * LIST_ROW_HEIGHT,
                SLOT_BACKGROUND
        );
        drawSlots(graphics, leftPos + PREVIEW_LEFT, topPos + PREVIEW_TOP, 3);
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tab != Tab.TEMPLATES) {
            return;
        }
        refreshTemplateButtons();
        renderTemplateList(graphics, mouseX, mouseY);
        renderPreview(graphics, mouseX, mouseY);
    }

    private void renderTemplateList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<DungeonPayloads.ChestTemplateView> templates = templates();
        int left = leftPos + LIST_LEFT;
        if (templates.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("screen.kingdoms.dungeon_chest.template_empty_list"),
                    left + 4, topPos + LIST_TOP + 8, MUTED, false);
            return;
        }
        int shown = Math.min(LIST_ROWS, templates.size() - templateScroll);
        for (int index = 0; index < shown; index++) {
            DungeonPayloads.ChestTemplateView view = templates.get(templateScroll + index);
            int rowTop = topPos + LIST_TOP + index * LIST_ROW_HEIGHT;
            boolean hovered = mouseX >= left && mouseX < left + LIST_WIDTH
                    && mouseY >= rowTop && mouseY < rowTop + LIST_ROW_HEIGHT;
            if (view.id().equals(selectedTemplate)) {
                graphics.fill(left, rowTop, left + LIST_WIDTH, rowTop + LIST_ROW_HEIGHT, ROW_SELECTED);
            } else if (hovered) {
                graphics.fill(left, rowTop, left + LIST_WIDTH, rowTop + LIST_ROW_HEIGHT, ROW_HOVER);
            }
            graphics.drawString(font, trim(view.name(), LIST_WIDTH - 8), left + 4, rowTop + 3, TEXT, false);
            graphics.drawString(font,
                    trim(Component.translatable(
                            "screen.kingdoms.dungeon_chest.template_row",
                            view.slots().size(),
                            view.author(),
                            DATE_FORMAT.format(Instant.ofEpochMilli(view.createdAt()))
                    ).getString(), LIST_WIDTH - 8),
                    left + 4, rowTop + 13, MUTED, false);
        }
        if (templates.size() > LIST_ROWS) {
            Component page = Component.translatable(
                    "screen.kingdoms.dungeon_chest.template_page",
                    templateScroll + shown,
                    templates.size()
            );
            graphics.drawString(font, page,
                    left + LIST_WIDTH - font.width(page),
                    topPos + LIST_TOP + LIST_ROWS * LIST_ROW_HEIGHT + 2, MUTED, false);
        }
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        DungeonPayloads.ChestTemplateView view = selectedView();
        int left = leftPos + PREVIEW_LEFT;
        if (view == null) {
            graphics.drawString(font,
                    Component.translatable("screen.kingdoms.dungeon_chest.template_pick"),
                    left, topPos + 30, MUTED, false);
            return;
        }
        graphics.drawString(font, trim(view.name(), 162), left, topPos + 30, GOLD, false);
        for (DungeonPayloads.ChestTemplateSlot slot : view.slots()) {
            int x = left + (slot.slot() % 9) * 18 + 1;
            int y = topPos + PREVIEW_TOP + (slot.slot() / 9) * 18 + 1;
            graphics.renderItem(slot.stack(), x, y);
            graphics.renderItemDecorations(font, slot.stack(), x, y,
                    slot.max() > 1 ? String.valueOf(slot.max()) : "");
        }
        graphics.drawString(font,
                Component.translatable("screen.kingdoms.dungeon_chest.template_author", view.author()),
                left, topPos + 102, MUTED, false);
        graphics.drawString(font,
                Component.translatable(
                        "screen.kingdoms.dungeon_chest.template_created",
                        DATE_FORMAT.format(Instant.ofEpochMilli(view.createdAt()))
                ),
                left, topPos + 112, MUTED, false);
        graphics.drawString(font,
                view.cooldownHours() < 0
                        ? Component.translatable("screen.kingdoms.dungeon_chest.template_cooldown_global")
                        : Component.translatable(
                                "screen.kingdoms.dungeon_chest.template_cooldown", view.cooldownHours()),
                left, topPos + 122, MUTED, false);

        int hovered = hoveredPreviewSlot(mouseX, mouseY);
        if (hovered < 0) {
            return;
        }
        for (DungeonPayloads.ChestTemplateSlot slot : view.slots()) {
            if (slot.slot() != hovered) {
                continue;
            }
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(slot.stack()));
            lines.add(Component.translatable(
                    "screen.kingdoms.dungeon_chest.tooltip",
                    slot.chance(),
                    slot.min(),
                    slot.max()
            ));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }

    private String trim(String value, int width) {
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, width - font.width("...")) + "...";
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        if (tab == Tab.TEMPLATES) {
            graphics.drawString(font,
                    Component.translatable("screen.kingdoms.dungeon_chest.template_hint"),
                    MARGIN, 18, MUTED, false);
            return;
        }
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
        if (tab == Tab.PLAN
                && chest != null
                && hoveredSlot != null
                && hoveredSlot.index < DungeonLootMenu.PLAN_SIZE) {
            lines.add(Component.translatable(
                    "screen.kingdoms.dungeon_chest.tooltip",
                    chest.chanceAt(hoveredSlot.index),
                    chest.minAt(hoveredSlot.index),
                    chest.maxAt(hoveredSlot.index)
            ));
        }
        return lines;
    }

    private enum Tab {
        PLAN,
        TEMPLATES
    }
}
