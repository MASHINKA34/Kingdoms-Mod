package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.EmblemTextures;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.LegacyEffect;
import com.geydev.kalfactions.faction.LegacyResearch;
import com.geydev.kalfactions.faction.ResearchCosts;
import com.geydev.kalfactions.faction.ResearchNode;
import com.geydev.kalfactions.client.LegacyAbilityKeys;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import com.geydev.kalfactions.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ResearchScreen extends FactionScreen {
    private static final ResourceLocation PANEL_FRAME = tex("research/panel_frame");
    private static final ResourceLocation TREE_FRAME = tex("research/tree_frame");
    private static final ResourceLocation INFO_FRAME = tex("research/info_frame");
    private static final ResourceLocation TREE_TILES = tex("research/tree_tiles");
    private static final ResourceLocation TABS = tex("research/tabs");
    private static final ResourceLocation SEAL_FRAME = tex("research/seal_frame");
    private static final ResourceLocation NODE_ROOT = tex("research/node_root");
    private static final ResourceLocation NODE_LOCKED = tex("research/node_locked");
    private static final ResourceLocation NODE_UNAVAILABLE = tex("research/node_unavailable");
    private static final ResourceLocation NODE_AVAILABLE = tex("research/node_available");
    private static final ResourceLocation NODE_ACTIVE = tex("research/node_active");
    private static final ResourceLocation NODE_DONE = tex("research/node_done");
    private static final ResourceLocation NODE_OVERLAY = tex("research/node_overlay");
    private static final ResourceLocation NODE_ROOT_OVERLAY = tex("research/node_root_overlay");
    private static final ResourceLocation NODE_ICONS = tex("research/node_icons");
    private static final ResourceLocation PROGRESS = tex("research/progress");
    private static final ResourceLocation ICON_SCIENCE = tex("influence/science");
    private static final ResourceLocation ICON_ECONOMIC = tex("influence/economic");
    private static final ResourceLocation ICON_MILITARY = tex("influence/military");

    private static final int MAX_WINDOW_WIDTH = 400;
    private static final int MAX_WINDOW_HEIGHT = 236;
    private static final int SAFE_MARGIN = 14;
    private static final int PANEL_BORDER = 6;
    private static final int TREE_FRAME_LEFT = 10;
    private static final int TREE_FRAME_TOP = 44;
    private static final int TREE_FRAME_BORDER = 4;
    private static final int TREE_FOOTER_GAP = 4;
    private static final int FOOTER_BOTTOM = 8;
    private static final int FOOTER_HEIGHT = 45;
    private static final int TAB_WIDTH = 36;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_GAP = 4;
    private static final int LEGACY_TAB = InfluenceType.VALUES.length;
    private static final int TAB_COUNT = LEGACY_TAB + 1;
    private static final int NODE_SIZE = 32;
    private static final int ROOT_SIZE = 40;
    private static final int NODE_OVERLAY_SIZE = 40;
    private static final int ROOT_OVERLAY_SIZE = 48;
    private static final int NODE_ICON_SIZE = 14;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 18;
    private static final int CAMERA_PADDING = 24;
    private static final float MIN_ZOOM = 0.44F;
    private static final float MAX_ZOOM = 1.50F;
    private static final float ZOOM_STEP = 0.14F;
    private static final int LEGACY_ROW_SHIFT = 50;
    private static final int FORCE_LOAD_SLOTS_PER_LEVEL = 5;
    private static final int MINING_SPEED_PERCENT_PER_LEVEL = 5;
    private static final int DRILL_BASE_OUTPUT = 32;
    private static final int DRILL_OUTPUT_PER_LEVEL = 16;
    private static final int DRILL_INTERVAL_REDUCTION_SECONDS = 2 * 60 * 60;
    private static final int DRILL_INTERVAL_FLOOR_SECONDS = 4 * 60 * 60;

    private int selectedTab;
    private ResearchNode selectedNode = ResearchNode.SCI_SMELT;
    private Button backButton;
    private Button startButton;
    private Button extraBonusButton;
    private Button doneButton;
    private int windowWidth;
    private int windowHeight;
    private float panX;
    private float panY;
    private float zoom = 0.72F;
    private boolean centerCamera = true;
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
        windowWidth = Math.max(1, Math.min(MAX_WINDOW_WIDTH, width - SAFE_MARGIN * 2));
        windowHeight = Math.max(1, Math.min(MAX_WINDOW_HEIGHT, height - SAFE_MARGIN * 2));
        left = (width - windowWidth) / 2;
        top = (height - windowHeight) / 2;
        doneButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                0,
                0,
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        ));
        initFactionWidgets();
        if (centerCamera) {
            centerTree();
            centerCamera = false;
        } else {
            clampPan();
        }
    }

    @Override
    protected void initFactionWidgets() {
        backButton = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.back"),
                button -> FactionScreens.openInfluence(snapshot, true, ""),
                0, 0, BUTTON_WIDTH, BUTTON_HEIGHT
        ));
        startButton = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.research_start"),
                button -> startSelectedNode(),
                0, 0, BUTTON_WIDTH, BUTTON_HEIGHT
        ));
        extraBonusButton = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.legacy_extra_bonus_short"),
                button -> openExtraBonusPicker(),
                0, 0, BUTTON_WIDTH, BUTTON_HEIGHT
        ));
        updateStartButton();
    }

    private boolean canPickExtraBonus() {
        return snapshot.extraBonus().isEmpty()
                && snapshot.canManage()
                && legacyLevelOf(FactionBonus.RESEARCHERS) >= LegacyResearch.MAX_LEVEL;
    }

    private void openExtraBonusPicker() {
        if (minecraft == null || !canPickExtraBonus()) {
            return;
        }
        List<FactionBonus> owned = parsedBonuses();
        List<FactionBonus> order = new ArrayList<>();
        List<SelectEntryScreen.Entry> entries = new ArrayList<>();
        for (FactionBonus bonus : FactionBonus.SELECTABLE) {
            if (owned.contains(bonus)) {
                continue;
            }
            order.add(bonus);
            entries.add(SelectEntryScreen.Entry.icon(
                    net.minecraft.client.resources.language.I18n.get(bonus.translationKey()),
                    Component.translatable(bonus.descriptionKey()),
                    FactionCreateScreen.bonusIcon(bonus),
                    true
            ));
        }
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.legacy_extra_bonus_pick"),
                entries,
                null,
                entry -> {
                    int index = entries.indexOf(entry);
                    if (index >= 0) {
                        PacketDistributor.sendToServer(new FactionPayloads.C2SChooseExtraBonus(
                                snapshot.tablePos(),
                                order.get(index).name()
                        ));
                    }
                }
        ));
    }

    @Override
    public void acceptServerState(FactionSnapshot newSnapshot, boolean actionSuccessful, String message) {
        super.acceptServerState(newSnapshot, actionSuccessful, message);
        updateStartButton();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawNineSlice(graphics, PANEL_FRAME, left, top, windowWidth, windowHeight, PANEL_BORDER, 24, 24);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderTabs(graphics, mouseX, mouseY);
        renderTree(graphics, mouseX, mouseY);
        renderInfoPanel(graphics);
        renderActionButtons(graphics, mouseX, mouseY, partialTick);
        Component categoryTitle = tabTitle(selectedTab);
        graphics.drawString(
                font,
                categoryTitle,
                left + (windowWidth - font.width(categoryTitle)) / 2,
                top + 32,
                0xFFF0CE72,
                true
        );
        ResearchNode hovered = hoveredNode(mouseX, mouseY);
        renderResearchStatusNotice(graphics);
        if (hovered != null) {
            renderNodeTooltip(graphics, hovered, mouseX, mouseY);
        } else {
            int tab = hoveredTab(mouseX, mouseY);
            if (tab >= 0) {
                graphics.renderTooltip(font, tabTitle(tab), mouseX, mouseY);
            }
        }
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int tabX = tabX(i);
            int tabY = tabY(i);
            boolean active = selectedTab == i;
            boolean hovered = mouseX >= tabX && mouseX < tabX + TAB_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            int stateY = active ? TAB_HEIGHT * 2 : hovered ? TAB_HEIGHT : 0;
            graphics.blit(
                    TABS,
                    tabX,
                    tabY,
                    TAB_WIDTH,
                    TAB_HEIGHT,
                    i * TAB_WIDTH,
                    stateY,
                    TAB_WIDTH,
                    TAB_HEIGHT,
                    TAB_WIDTH * TAB_COUNT,
                    TAB_HEIGHT * 3
            );
        }
    }

    private void renderActionButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        backButton.render(graphics, mouseX, mouseY, partialTick);
        startButton.render(graphics, mouseX, mouseY, partialTick);
        extraBonusButton.render(graphics, mouseX, mouseY, partialTick);
        doneButton.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTree(GuiGraphics graphics, int mouseX, int mouseY) {
        drawNineSlice(
                graphics,
                TREE_FRAME,
                treeFrameLeft(),
                treeFrameTop(),
                treeFrameWidth(),
                treeFrameHeight(),
                TREE_FRAME_BORDER,
                16,
                16
        );
        int clipLeft = treeLeft();
        int clipTop = treeTop();
        int clipRight = clipLeft + treeWidth();
        int clipBottom = clipTop + treeHeight();
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        renderTreeTiles(graphics);
        List<ResearchNode> nodes = visibleNodes();
        for (ResearchNode node : nodes) {
            for (ResearchNode parent : node.prerequisites()) {
                renderConnection(graphics, parent, node);
            }
        }
        for (ResearchNode node : nodes) {
            renderNode(graphics, node, node == hoveredNode(mouseX, mouseY));
        }
        graphics.disableScissor();
    }

    private List<ResearchNode> visibleNodes() {
        if (selectedTab != LEGACY_TAB) {
            return ResearchNode.branch(InfluenceType.VALUES[selectedTab]);
        }
        List<ResearchNode> nodes = new ArrayList<>();
        for (int slot = 0; slot < LegacyResearch.MAX_SLOTS; slot++) {
            if (legacyBonus(slot) != null) {
                nodes.addAll(ResearchNode.legacySlotNodes(slot));
            }
        }
        return List.copyOf(nodes);
    }

    private List<FactionBonus> parsedBonuses() {
        List<FactionBonus> parsed = new ArrayList<>();
        for (String name : snapshot.bonuses()) {
            try {
                parsed.add(FactionBonus.parse(name));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
        }
        return parsed;
    }

    private FactionBonus extraBonus() {
        if (snapshot.extraBonus().isEmpty()) {
            return null;
        }
        try {
            return FactionBonus.parse(snapshot.extraBonus());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private List<FactionBonus> legacySlots() {
        List<FactionBonus> founding = new ArrayList<>(parsedBonuses());
        founding.remove(extraBonus());
        return LegacyResearch.slots(founding);
    }

    private int legacyLevelOf(FactionBonus bonus) {
        int slot = legacySlots().indexOf(bonus);
        return slot < 0 ? 0 : legacyLevel(slot);
    }

    private double legacyValue(LegacyEffect effect) {
        return parsedBonuses().contains(effect.bonus()) ? effect.value(legacyLevelOf(effect.bonus())) : 0.0D;
    }

    private double researchDiscount() {
        return Math.min(0.90D, legacyValue(LegacyEffect.RESEARCH_DISCOUNT));
    }

    private long influenceCostPerType(ResearchNode node) {
        return ResearchCosts.discounted(node.influenceCostPerType(), researchDiscount());
    }

    private FactionBonus legacyBonus(int slot) {
        List<FactionBonus> slots = legacySlots();
        return slot < 0 || slot >= slots.size() ? null : slots.get(slot);
    }

    private int legacyLevel(int slot) {
        int level = 0;
        for (ResearchNode node : ResearchNode.legacySlotNodes(slot)) {
            if (snapshot.completedResearch().contains(node.name())) {
                level++;
            }
        }
        return level;
    }

    private void renderConnection(GuiGraphics graphics, ResearchNode parent, ResearchNode child) {
        int x1 = screenX(parent);
        int y1 = screenY(parent);
        int x2 = screenX(child);
        int y2 = screenY(child);
        int direction = Integer.compare(x2, x1);
        if (direction == 0) {
            direction = 1;
        }
        int startX = x1 + direction * (nodeSize(parent) / 2 + 1);
        int endX = x2 - direction * (nodeSize(child) / 2 + 4);
        if (direction > 0 && endX <= startX || direction < 0 && endX >= startX) {
            return;
        }
        int midX = (startX + endX) / 2;
        int color = connectionColor(parent, child);
        drawHorizontal(graphics, startX, midX, y1, 3, 0xFF080B11);
        drawVertical(graphics, midX, y1, y2, 3, 0xFF080B11);
        drawHorizontal(graphics, midX, endX, y2, 3, 0xFF080B11);
        drawHorizontal(graphics, startX, midX, y1, 1, color);
        drawVertical(graphics, midX, y1, y2, 1, color);
        drawHorizontal(graphics, midX, endX, y2, 1, color);
        drawArrow(graphics, endX, y2, direction, color);
    }

    private void renderNode(GuiGraphics graphics, ResearchNode node, boolean hovered) {
        NodeState nodeState = state(node);
        boolean bigRoot = node.root() && !node.legacy();
        ResourceLocation texture = switch (nodeState) {
            case DONE -> NODE_DONE;
            case ACTIVE -> NODE_ACTIVE;
            case AVAILABLE -> NODE_AVAILABLE;
            case UNAVAILABLE -> NODE_UNAVAILABLE;
            case LOCKED -> NODE_LOCKED;
        };
        int size = nodeSize(node);
        int x = screenX(node) - size / 2;
        int y = screenY(node) - size / 2;
        if (bigRoot) {
            graphics.blit(
                    NODE_ROOT,
                    x,
                    y,
                    ROOT_SIZE,
                    ROOT_SIZE,
                    0.0F,
                    nodeState.ordinal() * ROOT_SIZE,
                    ROOT_SIZE,
                    ROOT_SIZE,
                    ROOT_SIZE,
                    ROOT_SIZE * NodeState.values().length
            );
        } else {
            graphics.blit(texture, x, y, NODE_SIZE, NODE_SIZE, 0.0F, 0.0F, NODE_SIZE, NODE_SIZE, NODE_SIZE, NODE_SIZE);
        }
        FactionBonus legacyBonus = node.legacy() ? legacyBonus(node.legacySlot()) : null;
        float iconAlpha = nodeState == NodeState.LOCKED ? 0.35F : nodeState == NodeState.UNAVAILABLE ? 0.62F : 1.0F;
        graphics.setColor(1.0F, 1.0F, 1.0F, iconAlpha);
        if (legacyBonus != null) {
            graphics.blit(
                    FactionCreateScreen.bonusIcon(legacyBonus),
                    screenX(node) - 8,
                    screenY(node) - 8,
                    16,
                    16,
                    0.0F,
                    0.0F,
                    64,
                    64,
                    64,
                    64
            );
        } else if (!node.legacy()) {
            graphics.blit(
                    NODE_ICONS,
                    screenX(node) - NODE_ICON_SIZE / 2,
                    screenY(node) - NODE_ICON_SIZE / 2,
                    NODE_ICON_SIZE,
                    NODE_ICON_SIZE,
                    iconIndex(node) * NODE_ICON_SIZE,
                    0.0F,
                    NODE_ICON_SIZE,
                    NODE_ICON_SIZE,
                    NODE_ICON_SIZE * 20,
                    NODE_ICON_SIZE
            );
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        int overlayFrame = node == selectedNode
                ? 1 + (int) ((System.currentTimeMillis() / 180L) % 3L)
                : hovered ? 0 : -1;
        if (overlayFrame >= 0) {
            int overlaySize = bigRoot ? ROOT_OVERLAY_SIZE : NODE_OVERLAY_SIZE;
            ResourceLocation overlay = bigRoot ? NODE_ROOT_OVERLAY : NODE_OVERLAY;
            graphics.blit(
                    overlay,
                    screenX(node) - overlaySize / 2,
                    screenY(node) - overlaySize / 2,
                    overlaySize,
                    overlaySize,
                    overlayFrame * overlaySize,
                    0.0F,
                    overlaySize,
                    overlaySize,
                    overlaySize * 4,
                    overlaySize
            );
        }
        if (nodeState == NodeState.ACTIVE) {
            long remaining = Math.max(0L, snapshot.activeResearchEndMillis() - System.currentTimeMillis());
            long duration = Math.max(1L, effectiveDurationMillis(node));
            float fraction = Math.clamp((duration - remaining) / (float) duration, 0.0F, 1.0F);
            int barWidth = 48;
            int barX = screenX(node) - barWidth / 2;
            int barY = y + size + 4;
            graphics.blit(PROGRESS, barX, barY, barWidth, 6, 0.0F, 0.0F, barWidth, 6, barWidth, 12);
            int fullWidth = Math.max(0, (int) (barWidth * fraction));
            if (fullWidth > 0) {
                graphics.blit(PROGRESS, barX, barY, fullWidth, 6, 0.0F, 6.0F, fullWidth, 6, barWidth, 12);
            }
        }
    }

    private void renderInfoPanel(GuiGraphics graphics) {
        drawNineSlice(
                graphics,
                INFO_FRAME,
                footerLeft(),
                footerTop(),
                footerWidth(),
                FOOTER_HEIGHT,
                4,
                16,
                16
        );
        int sealX = footerLeft() + 5;
        int sealY = footerTop() + 2;
        graphics.blit(SEAL_FRAME, sealX, sealY, 20, 20, 0.0F, 0.0F, 20, 20, 20, 20);
        EmblemTextures.Emblem emblem = EmblemTextures.resolve(
                snapshot.factionId(),
                snapshot.emblem(),
                snapshot.emblemUrl(),
                snapshot.color()
        );
        graphics.blit(
                emblem.texture(),
                sealX + 2,
                sealY + 2,
                16,
                16,
                0.0F,
                0.0F,
                emblem.width(),
                emblem.height(),
                emblem.width(),
                emblem.height()
        );
        if (selectedNode == null) {
            return;
        }
        List<InfluenceType> types = selectedNode.costTypes();
        int rowY = footerTop() + 8;
        Component influence = text(
                "screen.kingdoms.research_cost_compact",
                compactValue(influenceCostPerType(selectedNode)),
                effectiveDurationHours(selectedNode)
        );
        int crystalsPerType = crystalCostPerType(selectedNode);
        Component crystals = Component.literal(compactValue(crystalsPerType));
        int costWidth = types.size() * 10 + font.width(influence);
        if (crystalsPerType > 0) {
            costWidth += 6 + types.size() * 18 + font.width(crystals);
        }
        int costCursor = footerLeft() + footerWidth() - 5 - costWidth;
        int walletCursor = sealX + 25;
        FactionBonus bonus = selectedTab == LEGACY_TAB ? extraBonus() : null;
        if (bonus != null && windowWidth >= 340) {
            graphics.blit(FactionCreateScreen.bonusIcon(bonus), walletCursor, footerTop() + 4, 16, 16,
                    0.0F, 0.0F, 64, 64, 64, 64);
            walletCursor += 20;
        }
        for (InfluenceType type : types) {
            graphics.blit(iconFor(type), walletCursor, rowY - 1, 8, 8, 0.0F, 0.0F, 16, 16, 16, 16);
            walletCursor += 10;
        }
        Component owned = ownedInfluenceText(types);
        int ownedWidth = Math.max(0, costCursor - 7 - walletCursor);
        if (ownedWidth > font.width("…")) {
            Component visibleOwned = clipToWidth(owned, ownedWidth);
            graphics.drawString(font, visibleOwned, walletCursor, rowY, 0xFFB8D4D1, true);
            walletCursor += font.width(visibleOwned);
        }
        int titleLeft = walletCursor + 5;
        int titleWidth = Math.max(0, costCursor - 7 - titleLeft);
        if (titleWidth > font.width("…")) {
            Component selection = clipToWidth(selectionTitle(selectedNode), titleWidth);
            graphics.drawString(font, selection, titleLeft + (titleWidth - font.width(selection)) / 2, rowY, 0xFFE7D18B, true);
        }
        for (InfluenceType type : types) {
            graphics.blit(iconFor(type), costCursor, rowY - 1, 8, 8, 0.0F, 0.0F, 16, 16, 16, 16);
            costCursor += 10;
        }
        graphics.drawString(font, influence, costCursor, rowY, 0xFFD1A84E, true);
        costCursor += font.width(influence);
        if (crystalsPerType > 0) {
            costCursor += 6;
            for (InfluenceType type : types) {
                graphics.renderItem(crystalStack(type), costCursor, footerTop() + 3);
                costCursor += 18;
            }
            graphics.drawString(font, crystals, costCursor, rowY, 0xFFB9CFF6, true);
        }
    }

    private Component selectionTitle(ResearchNode node) {
        if (!node.legacy()) {
            return nodeTitle(node);
        }
        FactionBonus bonus = legacyBonus(node.legacySlot());
        return bonus == null
                ? text("kingdoms.research.legacy.empty")
                : text("kingdoms.research.legacy.title_short", text(bonus.translationKey()), node.legacyLevel());
    }

    private Component clipToWidth(Component value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        int ellipsis = font.width("…");
        return Component.literal(
                font.plainSubstrByWidth(value.getString(), Math.max(0, maxWidth - ellipsis)) + "…"
        );
    }

    private void renderResearchStatusNotice(GuiGraphics graphics) {
        if (statusMessage == null || statusMessage.isBlank()) {
            return;
        }
        String clipped = font.plainSubstrByWidth(statusMessage, windowWidth - 48);
        int boxWidth = font.width(clipped) + 14;
        int boxLeft = left + (windowWidth - boxWidth) / 2;
        int boxTop = treeTop() + 6;
        drawNineSlice(graphics, INFO_FRAME, boxLeft, boxTop, boxWidth, 17, 4, 16, 16);
        graphics.drawString(font, clipped, boxLeft + 7, boxTop + 5,
                successful ? 0xFFB9F3A9 : 0xFFF2A7A7, true);
    }

    private void renderNodeTooltip(GuiGraphics graphics, ResearchNode node, int mouseX, int mouseY) {
        List<InfluenceType> types = node.costTypes();
        Component costLine = text(
                types.size() > 1
                        ? "screen.kingdoms.research_cost_each_short"
                        : "screen.kingdoms.research_cost_short",
                influenceCostPerType(node),
                effectiveDurationHours(node)
        );
        Component crystalLine = types.size() > 1
                ? text(
                        "screen.kingdoms.research_crystal_cost_each_short",
                        crystalCostPerType(node),
                        ownedCrystalsText(types)
                )
                : text(
                        "screen.kingdoms.research_crystal_cost_short",
                        crystalCostPerType(node),
                        crystalName(types.getFirst()),
                        crystalsOf(types.getFirst())
                );
        int availableWidth = Math.max(120, width - 12);
        int boxWidth = Math.min(Math.max(220, tooltipContentWidth(node, types, costLine, crystalLine)), availableWidth);
        List<FormattedCharSequence> desc = new ArrayList<>(font.split(nodeDescription(node), boxWidth - 20));
        List<FormattedCharSequence> effect = new ArrayList<>();
        for (Component line : effectLines(node)) {
            effect.addAll(font.split(line, boxWidth - 20));
        }
        boolean showCrystals = crystalCostPerType(node) > 0;
        int baseHeight = showCrystals ? 90 : 71;
        int maxHeight = Math.max(70, height - 12);
        while (baseHeight + desc.size() * 11 + effect.size() * 11 > maxHeight && !effect.isEmpty()) {
            effect.removeLast();
        }
        while (baseHeight + desc.size() * 11 + effect.size() * 11 > maxHeight && desc.size() > 1) {
            desc.removeLast();
        }
        int descBlock = desc.size() * 11;
        int effectBlock = effect.size() * 11;
        int boxHeight = Math.min(maxHeight, baseHeight + descBlock + effectBlock);
        int preferredX = mouseX < width / 2 ? mouseX + 14 : mouseX - boxWidth - 14;
        int x = Math.clamp(preferredX, 6, Math.max(6, width - boxWidth - 6));
        int preferredY = mouseY + 12;
        if (preferredY + boxHeight > height - 6) {
            preferredY = mouseY - boxHeight - 12;
        }
        int y = Math.clamp(preferredY, 6, Math.max(6, height - boxHeight - 6));
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        drawNineSlice(graphics, INFO_FRAME, x, y, boxWidth, boxHeight, 4, 16, 16);
        graphics.fill(x + 4, y + 3, x + boxWidth - 4, y + 4, 0xFFA97D31);
        graphics.drawString(font, nodeTitle(node), x + 10, y + 8, colorFor(node), true);
        for (int i = 0; i < desc.size(); i++) {
            graphics.drawString(font, desc.get(i), x + 10, y + 23 + i * 11, 0xFFEFE0B4, true);
        }
        int lineY = y + 28 + descBlock;
        int cursor = x + 10;
        for (InfluenceType type : types) {
            graphics.blit(iconFor(type), cursor, lineY - 2, 12, 12, 0.0F, 0.0F, 16, 16, 16, 16);
            cursor += 14;
        }
        graphics.drawString(font, costLine, cursor + 2, lineY, 0xFFE6CE7E, true);
        int statusY = lineY + 12;
        if (showCrystals) {
            int crystalCursor = x + 8;
            for (InfluenceType type : types) {
                graphics.renderItem(crystalStack(type), crystalCursor, lineY + 10);
                crystalCursor += 18;
            }
            graphics.drawString(font, crystalLine, crystalCursor + 2, lineY + 14, 0xFFB9CFF6, true);
            statusY = lineY + 31;
        }
        graphics.drawString(font, statusText(node), x + 10, statusY, statusColor(node), true);
        for (int i = 0; i < effect.size(); i++) {
            graphics.drawString(font, effect.get(i), x + 10, statusY + 15 + i * 11, 0xFFCBD6F0, true);
        }
        graphics.pose().popPose();
    }

    private int tooltipContentWidth(
            ResearchNode node,
            List<InfluenceType> types,
            Component costLine,
            Component crystalLine
    ) {
        int widest = font.width(nodeTitle(node));
        widest = Math.max(widest, font.width(statusText(node)));
        widest = Math.max(widest, types.size() * 14 + 2 + font.width(costLine));
        if (crystalCostPerType(node) > 0) {
            widest = Math.max(widest, types.size() * 18 + 4 + font.width(crystalLine));
        }
        return widest + 20;
    }

    private Component nodeTitle(ResearchNode node) {
        if (!node.legacy()) {
            return text(node.translationKey());
        }
        FactionBonus bonus = legacyBonus(node.legacySlot());
        return bonus == null
                ? text("kingdoms.research.legacy.empty")
                : text("kingdoms.research.legacy.title", text(bonus.translationKey()), node.legacyLevel());
    }

    private Component nodeDescription(ResearchNode node) {
        if (!node.legacy()) {
            return text(node.descriptionKey());
        }
        FactionBonus bonus = legacyBonus(node.legacySlot());
        return bonus == null
                ? text("kingdoms.research.legacy.empty.desc")
                : text("kingdoms.research.legacy.desc", text(bonus.translationKey()));
    }

    private List<Component> effectLines(ResearchNode node) {
        if (!node.legacy()) {
            return List.of(text("screen.kingdoms.research_effect", bonusText(node)));
        }
        FactionBonus bonus = legacyBonus(node.legacySlot());
        if (bonus == null) {
            return List.of(text("kingdoms.research.legacy.empty.desc"));
        }
        int level = node.legacyLevel();
        List<Component> lines = new ArrayList<>();
        for (LegacyEffect effect : LegacyEffect.values()) {
            if (effect.bonus() != bonus || (!effect.unlockedAt(level) && !effect.unlockedAt(level - 1))) {
                continue;
            }
            lines.add(legacyEffectLine(effect, level));
        }
        if (level >= LegacyResearch.MAX_LEVEL) {
            lines.add(text(
                    "kingdoms.research.legacy.mastery_line",
                    text("kingdoms.research.legacy.mastery." + bonus.name().toLowerCase(Locale.ROOT))
            ));
            if (bonus == FactionBonus.MINERS) {
                lines.add(text(
                        "kingdoms.research.legacy.mastery.key",
                        LegacyAbilityKeys.minerVisionKeyName()
                ));
            }
        }
        return List.copyOf(lines);
    }

    private static Component legacyEffectLine(LegacyEffect effect, int level) {
        return text(
                "kingdoms.research.legacy.effect_line",
                text(effect.translationKey()),
                legacyEffectValue(effect, level - 1),
                legacyEffectValue(effect, level)
        );
    }

    private static String legacyEffectValue(LegacyEffect effect, int level) {
        double value = effect.value(level);
        return switch (effect) {
            case OUTPOST_SIZE -> {
                int size = (int) Math.round(value);
                yield size + "x" + size;
            }
            case HOOKAH_ARMOR -> decimal(value);
            case MINING_SPEED, BACK_DAMAGE, CRIT_DAMAGE, HOOKAH_SPEED, HOOKAH_DAMAGE, SELL_PRICE, MOUNT_SPEED,
                    RESEARCH_SPEED -> "+" + decimalPercent(value);
            default -> decimalPercent(value);
        };
    }

    private static String decimal(double value) {
        double safe = Math.max(0.0D, value);
        return Math.abs(safe - Math.rint(safe)) < 0.05D
                ? String.valueOf((int) Math.rint(safe))
                : String.format(Locale.ROOT, "%.1f", safe);
    }

    private static String decimalPercent(double fraction) {
        return decimal(fraction * 100.0D) + "%";
    }

    private Component bonusText(ResearchNode node) {
        String[] parts = node.bonusTag().split("\\+");
        MutableComponent result = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(effectText(parts[i].trim(), node));
        }
        return result;
    }

    private Component effectText(String tag, ResearchNode node) {
        String normalized = canonicalTag(tag.toUpperCase(Locale.ROOT));
        int level = displayLevel(normalized, node);
        return switch (normalized) {
            case "MINING_SPEED" -> effectProgress(normalized, node,
                    percent(MINING_SPEED_PERCENT_PER_LEVEL),
                    signedPercent(MINING_SPEED_PERCENT_PER_LEVEL * level));
            case "CHUNK_SLOT", "SCIENCE_CHUNK_SLOT", "ECONOMIC_CHUNK_SLOT" -> effectProgress(normalized, node,
                    text("kingdoms.research.effect.chunk_slot.per_level", FORCE_LOAD_SLOTS_PER_LEVEL),
                    text("kingdoms.research.effect.chunk_slot.total", forceLoadLimit(level)));
            case "DRILL_OUTPUT" -> effectProgress(normalized, node,
                    text("kingdoms.research.effect.drill_output.per_level", DRILL_OUTPUT_PER_LEVEL),
                    text("kingdoms.research.effect.drill_output.total", DRILL_BASE_OUTPUT + DRILL_OUTPUT_PER_LEVEL * level));
            case "DRILL_INTERVAL" -> effectProgress(normalized, node,
                    text("kingdoms.research.effect.drill_interval.per_level", durationText(DRILL_INTERVAL_REDUCTION_SECONDS)),
                    text(
                            "kingdoms.research.effect.drill_interval.total",
                            durationText(drillIntervalSeconds(level)),
                            durationText(drillIntervalFloorSeconds())
                    ));
            case "SMELT_SPEED" -> effectSingle(normalized, text("kingdoms.research.effect.smelt_speed.detail"));
            case "ORE_DROP" -> effectSingle(normalized, text(
                    "kingdoms.research.effect.ore_drop.detail",
                    unsignedPercent(ModConfigSpec.ORE_BONUS_CHANCE.getAsDouble())
            ));
            case "CRAFT_EXTRA" -> effectProgress(normalized, node,
                    signedPercent(10),
                    signedPercent(Math.min(50, 10 * level)));
            case "ENCHANT_BOOST" -> effectSingle(normalized, text("kingdoms.research.effect.enchant_boost.detail"));
            case "BUY_RATE" -> effectProgress(normalized, node,
                    signedPercent(10),
                    signedPercent(10 * level));
            case "CLAIM_DISCOUNT", "OUTPOST_DISCOUNT", "VILLAGER_DISCOUNT" -> effectProgress(normalized, node,
                    signedPercent(-10),
                    signedPercent(-Math.min(90, 10 * level)));
            case "VILLAGER_EXTRA" -> effectProgress(normalized, node,
                    signedPercent(25),
                    signedPercent(Math.min(60, 25 * level)));
            case "RAID_STEAL_RESIST" -> effectProgress(normalized, node,
                    signedPercent(-10),
                    signedPercent(-Math.min(100, 10 * level)));
            case "RAID_WARNING" -> effectProgress(normalized, node,
                    text("kingdoms.research.effect.raid_warning.per_level", durationText(120)),
                    text("kingdoms.research.effect.raid_warning.total", durationText(raidWarningSeconds(level))));
            case "FEWER_RAIDERS" -> effectProgress(normalized, node,
                    text("kingdoms.research.effect.fewer_raiders.per_level", 1),
                    text("kingdoms.research.effect.fewer_raiders.total", level));
            case "WARRIOR_DAMAGE" -> effectProgress(normalized, node,
                    signedPercent(5),
                    signedPercent(5 * level));
            case "ARMOR_BOOST" -> effectProgress(normalized, node,
                    signedPercent(-5),
                    signedPercent(-Math.min(50, 5 * level)));
            case "TNT_RESIST", "CLAIM_TNT_RESIST" -> effectProgress(normalized, node,
                    signedPercent(30),
                    text("kingdoms.research.effect.tnt_resist.total", unsignedPercent(Math.min(0.30D, 0.30D * level))));
            case "RAID_REWARD" -> effectProgress(normalized, node,
                    signedPercent(10),
                    signedPercent(10 * level));
            default -> effectSingle(normalized, effectName(normalized));
        };
    }

    private Component effectProgress(String tag, ResearchNode node, Object perLevel, Object total) {
        return text(
                state(node) == NodeState.DONE
                        ? "screen.kingdoms.research_effect.current"
                        : "screen.kingdoms.research_effect.after",
                effectName(tag),
                text("screen.kingdoms.research_effect.per_level", perLevel),
                total
        );
    }

    private static Component effectSingle(String tag, Object detail) {
        return text("screen.kingdoms.research_effect.single", effectName(tag), detail);
    }

    private int displayLevel(String tag, ResearchNode node) {
        int completed = completedBonusLevel(tag);
        return state(node) == NodeState.DONE ? completed : completed + 1;
    }

    private int completedBonusLevel(String tag) {
        int count = 0;
        for (String nodeName : snapshot.completedResearch()) {
            ResearchNode completed = ResearchNode.parse(nodeName).orElse(null);
            if (completed != null && bonusTagContains(completed.bonusTag(), tag)) {
                count++;
            }
        }
        return count;
    }

    private static boolean bonusTagContains(String bonusTag, String tag) {
        for (String part : bonusTag.split("\\+")) {
            if (canonicalTag(part.trim().toUpperCase(Locale.ROOT)).equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalTag(String tag) {
        return switch (tag) {
            case "SCIENCE_CHUNK_SLOT", "ECONOMIC_CHUNK_SLOT" -> "CHUNK_SLOT";
            case "CLAIM_TNT_RESIST" -> "TNT_RESIST";
            default -> tag;
        };
    }

    private static Component effectName(String tag) {
        return text("kingdoms.research.effect." + tag.toLowerCase(Locale.ROOT));
    }

    private static Component durationText(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        int hours = safeSeconds / 3600;
        int minutes = (safeSeconds % 3600) / 60;
        int remainingSeconds = safeSeconds % 60;
        if (hours > 0 && minutes > 0) {
            return text("screen.kingdoms.research_duration.hours_minutes", hours, minutes);
        }
        if (hours > 0) {
            return text("screen.kingdoms.research_duration.hours", hours);
        }
        if (minutes > 0) {
            return text("screen.kingdoms.research_duration.minutes", minutes);
        }
        return text("screen.kingdoms.research_duration.seconds", remainingSeconds);
    }

    private long effectiveDurationMillis(ResearchNode node) {
        long duration = node.durationMillis();
        double speed = 1.0D + legacyValue(LegacyEffect.RESEARCH_SPEED);
        return Math.max(1L, (long) Math.ceil(duration / Math.max(0.0001D, speed)));
    }

    private int effectiveDurationHours(ResearchNode node) {
        return Math.max(1, (int) Math.ceil(effectiveDurationMillis(node) / 3_600_000.0D));
    }

    private static String signedPercent(int value) {
        return (value > 0 ? "+" : "") + value + "%";
    }

    private static String percent(int value) {
        return value + "%";
    }

    private static String unsignedPercent(double fraction) {
        return Math.round(Math.max(0.0D, fraction) * 100.0D) + "%";
    }

    private static int forceLoadLimit(int level) {
        return ModConfigSpec.FORCE_LOAD_SLOTS.getAsInt() + FORCE_LOAD_SLOTS_PER_LEVEL * level;
    }

    private static int drillIntervalSeconds(int level) {
        int base = Math.max(1, ModConfigSpec.OUTPOST_DRILL_INTERVAL_SECONDS.getAsInt());
        int floor = drillIntervalFloorSeconds();
        return Math.max(floor, base - DRILL_INTERVAL_REDUCTION_SECONDS * level);
    }

    private static int drillIntervalFloorSeconds() {
        return Math.min(Math.max(1, ModConfigSpec.OUTPOST_DRILL_INTERVAL_SECONDS.getAsInt()), DRILL_INTERVAL_FLOOR_SECONDS);
    }

    private static int raidWarningSeconds(int level) {
        return Math.max(0, ModConfigSpec.RAID_WARNING_SECONDS.getAsInt()) + 120 * level;
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
        if (!snapshot.activeResearchNode().isEmpty()) {
            return text("screen.kingdoms.research_other_active");
        }
        if (!snapshot.canManage() && !snapshot.isOfficer()) {
            return text("screen.kingdoms.research_officer_required");
        }
        if (!hasInfluenceFor(node)) {
            return text("screen.kingdoms.research_not_enough_influence");
        }
        if (!hasCrystalsFor(node)) {
            return text("screen.kingdoms.research_not_enough_crystals");
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
        if (nodeState == NodeState.AVAILABLE && hasInfluenceFor(node) && hasCrystalsFor(node)) {
            return 0xFF5AFF8A;
        }
        return 0xFFFF9E9E;
    }

    private boolean hasInfluenceFor(ResearchNode node) {
        long perType = influenceCostPerType(node);
        for (InfluenceType type : node.costTypes()) {
            if (influenceOf(type) < perType) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCrystalsFor(ResearchNode node) {
        int perType = crystalCostPerType(node);
        for (InfluenceType type : node.costTypes()) {
            if (crystalsOf(type) < perType) {
                return false;
            }
        }
        return true;
    }

    private void startSelectedNode() {
        if (selectedNode == null || !canStart(selectedNode)) {
            return;
        }
        PacketDistributor.sendToServer(new FactionPayloads.C2SStartResearch(snapshot.tablePos(), selectedNode.name()));
    }

    private void updateStartButton() {
        if (extraBonusButton != null) {
            extraBonusButton.visible = selectedTab == LEGACY_TAB && canPickExtraBonus();
            extraBonusButton.active = extraBonusButton.visible;
        }
        if (startButton == null) {
            return;
        }
        startButton.active = selectedNode != null && canStart(selectedNode);
        layoutButtons();
    }

    private boolean canStart(ResearchNode node) {
        return state(node) == NodeState.AVAILABLE;
    }

    private int crystalCostPerType(ResearchNode node) {
        int total = node.legacy()
                ? LegacyResearch.crystalCost(node.legacyLevel())
                : snapshot.researchCrystalCosts()
                        .get(Math.clamp(node.tier(), 1, snapshot.researchCrystalCosts().size()) - 1);
        return (int) ResearchCosts.discounted(node.crystalCostPerType(total), researchDiscount());
    }

    private Component ownedCrystalsText(List<InfluenceType> types) {
        StringBuilder owned = new StringBuilder();
        for (InfluenceType type : types) {
            if (!owned.isEmpty()) {
                owned.append('/');
            }
            owned.append(crystalsOf(type));
        }
        return Component.literal(owned.toString());
    }

    private Component ownedInfluenceText(List<InfluenceType> types) {
        StringBuilder owned = new StringBuilder();
        for (InfluenceType type : types) {
            if (!owned.isEmpty()) {
                owned.append('/');
            }
            owned.append(compactValue(influenceOf(type)));
        }
        return Component.literal(owned.toString());
    }

    private int crystalsOf(InfluenceType type) {
        return switch (type) {
            case SCIENCE -> snapshot.crystalScience();
            case ECONOMIC -> snapshot.crystalEconomic();
            case MILITARY -> snapshot.crystalMilitary();
        };
    }

    private Component crystalName(InfluenceType type) {
        return text("item.kingdoms.crystal_" + type.id());
    }

    private static ItemStack crystalStack(InfluenceType type) {
        return new ItemStack(ModItems.crystalFor(type));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (int i = 0; i < TAB_COUNT; i++) {
            int tabX = tabX(i);
            int tabY = tabY(i);
            if (mouseX >= tabX && mouseX < tabX + TAB_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                if (selectedTab != i) {
                    selectedTab = i;
                    List<ResearchNode> nodes = visibleNodes();
                    selectedNode = nodes.isEmpty() ? null : nodes.getFirst();
                    centerCamera = true;
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

    private int hoveredTab(double mouseX, double mouseY) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int x = tabX(i);
            int y = tabY(i);
            if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    private static Component tabTitle(int index) {
        return index == LEGACY_TAB
                ? text("kingdoms.research.legacy.branch")
                : text(InfluenceType.VALUES[index].translationKey());
    }

    private int tabX(int index) {
        int tabsWidth = TAB_COUNT * TAB_WIDTH + (TAB_COUNT - 1) * TAB_GAP;
        return left + (windowWidth - tabsWidth) / 2 + index * (TAB_WIDTH + TAB_GAP);
    }

    private int tabY(int index) {
        return top + 5;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingTree && button == 0) {
            panX += (float) ((mouseX - lastDragX) / zoom);
            panY += (float) ((mouseY - lastDragY) / zoom);
            lastDragX = mouseX;
            lastDragY = mouseY;
            clampPan();
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
        zoom = Math.clamp(zoom + (float) scrollY * ZOOM_STEP, MIN_ZOOM, MAX_ZOOM);
        float factor = zoom / previous;
        float centerX = (float) (mouseX - (treeLeft() + treeWidth() / 2.0F));
        float centerY = (float) (mouseY - (treeTop() + treeHeight() / 2.0F));
        panX = panX - centerX / previous + centerX / (previous * factor);
        panY = panY - centerY / previous + centerY / (previous * factor);
        clampPan();
        return true;
    }

    private ResearchNode hoveredNode(double mouseX, double mouseY) {
        if (!insideTree(mouseX, mouseY)) {
            return null;
        }
        for (ResearchNode node : visibleNodes()) {
            int size = nodeSize(node);
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
        return mouseX >= treeLeft() && mouseX < treeLeft() + treeWidth()
                && mouseY >= treeTop() && mouseY < treeTop() + treeHeight();
    }

    private int screenX(ResearchNode node) {
        return Math.round(treeLeft() + treeWidth() / 2.0F + (node.treeX() + panX) * zoom);
    }

    private int screenY(ResearchNode node) {
        return Math.round(treeTop() + treeHeight() / 2.0F + (treeY(node) + panY) * zoom);
    }

    private int treeY(ResearchNode node) {
        if (!node.legacy() || legacyBonus(1) != null) {
            return node.treeY();
        }
        return node.treeY() + LEGACY_ROW_SHIFT;
    }

    private void layoutButtons() {
        if (backButton == null || startButton == null || doneButton == null || extraBonusButton == null) {
            return;
        }
        int buttonY = footerTop() + 23;
        if (extraBonusButton.visible) {
            int firstX = footerLeft() + 5;
            int available = footerWidth() - 10;
            int gap = Math.max(2, (available - BUTTON_WIDTH * 4) / 3);
            backButton.setX(firstX);
            extraBonusButton.setX(firstX + BUTTON_WIDTH + gap);
            startButton.setX(firstX + (BUTTON_WIDTH + gap) * 2);
            doneButton.setX(firstX + (BUTTON_WIDTH + gap) * 3);
        } else {
            backButton.setX(footerLeft() + 5);
            startButton.setX(left + (windowWidth - BUTTON_WIDTH) / 2);
            doneButton.setX(footerLeft() + footerWidth() - BUTTON_WIDTH - 5);
        }
        backButton.setY(buttonY);
        extraBonusButton.setY(buttonY);
        startButton.setY(buttonY);
        doneButton.setY(buttonY);
    }

    private int footerLeft() {
        return left + TREE_FRAME_LEFT;
    }

    private int footerTop() {
        return top + windowHeight - FOOTER_BOTTOM - FOOTER_HEIGHT;
    }

    private int footerWidth() {
        return windowWidth - TREE_FRAME_LEFT * 2;
    }

    private int treeFrameLeft() {
        return left + TREE_FRAME_LEFT;
    }

    private int treeFrameTop() {
        return top + TREE_FRAME_TOP;
    }

    private int treeFrameWidth() {
        return windowWidth - TREE_FRAME_LEFT * 2;
    }

    private int treeFrameHeight() {
        return footerTop() - TREE_FOOTER_GAP - treeFrameTop();
    }

    private int treeLeft() {
        return treeFrameLeft() + TREE_FRAME_BORDER;
    }

    private int treeTop() {
        return treeFrameTop() + TREE_FRAME_BORDER;
    }

    private int treeWidth() {
        return treeFrameWidth() - TREE_FRAME_BORDER * 2;
    }

    private int treeHeight() {
        return treeFrameHeight() - TREE_FRAME_BORDER * 2;
    }

    private void renderTreeTiles(GuiGraphics graphics) {
        int offsetX = Math.floorMod(Math.round(panX * zoom * 0.15F), 16);
        int offsetY = Math.floorMod(Math.round(panY * zoom * 0.15F), 16);
        int startX = treeLeft() - 16 + offsetX;
        int startY = treeTop() - 16 + offsetY;
        int row = 0;
        for (int y = startY; y < treeTop() + treeHeight(); y += 16) {
            int column = 0;
            for (int x = startX; x < treeLeft() + treeWidth(); x += 16) {
                int variant = Math.floorMod(column * 31 + row * 17 + selectedTab * 7, 4);
                graphics.blit(TREE_TILES, x, y, 16, 16, variant * 16, 0.0F, 16, 16, 64, 16);
                column++;
            }
            row++;
        }
    }

    private void centerTree() {
        List<ResearchNode> nodes = visibleNodes();
        if (nodes.isEmpty()) {
            panX = 0.0F;
            panY = 0.0F;
            return;
        }
        int minX = nodes.stream().mapToInt(ResearchNode::treeX).min().orElse(0);
        int maxX = nodes.stream().mapToInt(ResearchNode::treeX).max().orElse(0);
        int minY = nodes.stream().mapToInt(this::treeY).min().orElse(0);
        int maxY = nodes.stream().mapToInt(this::treeY).max().orElse(0);
        panX = -(minX + maxX) / 2.0F;
        panY = -(minY + maxY) / 2.0F;
        clampPan();
    }

    private void clampPan() {
        List<ResearchNode> nodes = visibleNodes();
        if (nodes.isEmpty() || treeWidth() <= 0 || treeHeight() <= 0) {
            panX = 0.0F;
            panY = 0.0F;
            return;
        }
        float padding = CAMERA_PADDING / zoom;
        float minX = nodes.stream().mapToInt(ResearchNode::treeX).min().orElse(0) - padding;
        float maxX = nodes.stream().mapToInt(ResearchNode::treeX).max().orElse(0) + padding;
        float minY = nodes.stream().mapToInt(this::treeY).min().orElse(0) - padding;
        float maxY = nodes.stream().mapToInt(this::treeY).max().orElse(0) + padding;
        panX = clampAxis(panX, minX, maxX, treeWidth() / (2.0F * zoom));
        panY = clampAxis(panY, minY, maxY, treeHeight() / (2.0F * zoom));
    }

    private static float clampAxis(float pan, float contentMin, float contentMax, float viewHalf) {
        if (contentMax - contentMin <= viewHalf * 2.0F) {
            return -(contentMin + contentMax) / 2.0F;
        }
        float camera = -pan;
        float minCamera = contentMin + viewHalf;
        float maxCamera = contentMax - viewHalf;
        return -Math.clamp(camera, minCamera, maxCamera);
    }

    private static int nodeSize(ResearchNode node) {
        return node.root() && !node.legacy() ? ROOT_SIZE : NODE_SIZE;
    }

    private boolean startConditionsMet(ResearchNode node) {
        return (snapshot.canManage() || snapshot.isOfficer())
                && snapshot.activeResearchNode().isEmpty()
                && hasInfluenceFor(node)
                && hasCrystalsFor(node);
    }

    private int connectionColor(ResearchNode parent, ResearchNode child) {
        NodeState childState = state(child);
        if (childState == NodeState.DONE) {
            return 0xFFC8C35B;
        }
        if (childState == NodeState.ACTIVE) {
            return 0xFF7AEBDD;
        }
        if (snapshot.completedResearch().contains(parent.name())) {
            return 0xFF318D8B;
        }
        return 0xFF3D4654;
    }

    private static void drawHorizontal(GuiGraphics graphics, int x1, int x2, int y, int thickness, int color) {
        int half = thickness / 2;
        graphics.fill(Math.min(x1, x2), y - half, Math.max(x1, x2) + 1, y - half + thickness, color);
    }

    private static void drawVertical(GuiGraphics graphics, int x, int y1, int y2, int thickness, int color) {
        int half = thickness / 2;
        graphics.fill(x - half, Math.min(y1, y2), x - half + thickness, Math.max(y1, y2) + 1, color);
    }

    private static void drawArrow(GuiGraphics graphics, int x, int y, int direction, int color) {
        for (int offset = -2; offset <= 2; offset++) {
            int distance = 2 - Math.abs(offset);
            int pixelX = x - direction * distance;
            graphics.fill(pixelX, y + offset, pixelX + 1, y + offset + 1, color);
        }
    }

    private NodeState state(ResearchNode node) {
        if (snapshot.completedResearch().contains(node.name())) {
            return NodeState.DONE;
        }
        if (node.name().equals(snapshot.activeResearchNode())) {
            return NodeState.ACTIVE;
        }
        boolean prereqDone = node.prerequisites().stream()
                .allMatch(prereq -> snapshot.completedResearch().contains(prereq.name()));
        if (!prereqDone) {
            return NodeState.LOCKED;
        }
        return startConditionsMet(node) ? NodeState.AVAILABLE : NodeState.UNAVAILABLE;
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

    private static int iconIndex(ResearchNode node) {
        String tag = canonicalTag(node.bonusTag().split("\\+")[0].trim().toUpperCase(Locale.ROOT));
        return switch (tag) {
            case "SMELT_SPEED" -> 0;
            case "MINING_SPEED" -> 1;
            case "DRILL_INTERVAL" -> 2;
            case "DRILL_OUTPUT" -> 3;
            case "CHUNK_SLOT" -> 4;
            case "ORE_DROP" -> 5;
            case "ENCHANT_BOOST" -> 6;
            case "CRAFT_EXTRA" -> 7;
            case "BUY_RATE" -> 8;
            case "RAID_STEAL_RESIST" -> 9;
            case "OUTPOST_DISCOUNT" -> 10;
            case "CLAIM_DISCOUNT" -> 11;
            case "VILLAGER_DISCOUNT" -> 12;
            case "VILLAGER_EXTRA" -> 13;
            case "RAID_WARNING" -> 14;
            case "RAID_REWARD" -> 15;
            case "WARRIOR_DAMAGE" -> 16;
            case "FEWER_RAIDERS" -> 17;
            case "ARMOR_BOOST" -> 18;
            case "TNT_RESIST" -> 19;
            default -> 0;
        };
    }

    private static String compactValue(long value) {
        if (value < 10_000L) {
            return Long.toString(value);
        }
        if (value < 1_000_000L) {
            return compactUnit(value, 1_000.0D, "K");
        }
        return compactUnit(value, 1_000_000.0D, "M");
    }

    private static String compactUnit(long value, double divisor, String suffix) {
        double scaled = value / divisor;
        return Math.abs(scaled - Math.rint(scaled)) < 0.05D
                ? (long) Math.rint(scaled) + suffix
                : String.format(Locale.ROOT, "%.1f%s", scaled, suffix);
    }

    private static void drawNineSlice(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            int border,
            int textureWidth,
            int textureHeight
    ) {
        int innerWidth = Math.max(0, width - border * 2);
        int innerHeight = Math.max(0, height - border * 2);
        int textureInnerWidth = textureWidth - border * 2;
        int textureInnerHeight = textureHeight - border * 2;
        graphics.blit(texture, x, y, border, border,
                0, 0, border, border, textureWidth, textureHeight);
        graphics.blit(texture, x + width - border, y, border, border,
                textureWidth - border, 0, border, border, textureWidth, textureHeight);
        graphics.blit(texture, x, y + height - border, border, border,
                0, textureHeight - border, border, border, textureWidth, textureHeight);
        graphics.blit(texture, x + width - border, y + height - border, border, border,
                textureWidth - border, textureHeight - border, border, border, textureWidth, textureHeight);
        if (innerWidth > 0) {
            graphics.blit(texture, x + border, y, innerWidth, border,
                    border, 0, textureInnerWidth, border, textureWidth, textureHeight);
            graphics.blit(texture, x + border, y + height - border, innerWidth, border,
                    border, textureHeight - border, textureInnerWidth, border, textureWidth, textureHeight);
        }
        if (innerHeight > 0) {
            graphics.blit(texture, x, y + border, border, innerHeight,
                    0, border, border, textureInnerHeight, textureWidth, textureHeight);
            graphics.blit(texture, x + width - border, y + border, border, innerHeight,
                    textureWidth - border, border, border, textureInnerHeight, textureWidth, textureHeight);
        }
        if (innerWidth > 0 && innerHeight > 0) {
            graphics.blit(texture, x + border, y + border, innerWidth, innerHeight,
                    border, border, textureInnerWidth, textureInnerHeight, textureWidth, textureHeight);
        }
    }

    private static int colorFor(ResearchNode node) {
        if (node.legacy()) {
            return 0xFFFFCE4A;
        }
        return switch (node.type()) {
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
        UNAVAILABLE,
        AVAILABLE,
        ACTIVE,
        DONE
    }
}
