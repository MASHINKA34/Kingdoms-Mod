package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ResearchScreen extends FactionScreen {
    private static final ResourceLocation PANEL = tex("research/panel");
    private static final ResourceLocation TAB_SCIENCE = tex("research/tab_science");
    private static final ResourceLocation TAB_ECONOMIC = tex("research/tab_economic");
    private static final ResourceLocation TAB_MILITARY = tex("research/tab_military");
    private static final ResourceLocation TAB_LEGACY = tex("research/tab_legacy");
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
    private static final int TREE_TOP = 62;
    private static final int TREE_WIDTH = 372;
    private static final int TREE_HEIGHT = 148;
    private static final int TAB_WIDTH = 44;
    private static final int TAB_HEIGHT = 34;
    private static final int TAB_STEP = 48;
    private static final int LEGACY_TAB = InfluenceType.VALUES.length;
    private static final int TAB_COUNT = LEGACY_TAB + 1;
    private static final int NODE_SIZE = 30;
    private static final int ROOT_SIZE = 46;
    private static final int LEGACY_ICON_INSET = 6;
    private static final int LEGACY_ROW_SHIFT = 50;
    private static final int FORCE_LOAD_SLOTS_PER_LEVEL = 5;
    private static final int MINING_SPEED_PERCENT_PER_LEVEL = 5;
    private static final int DRILL_BASE_OUTPUT = 32;
    private static final int DRILL_OUTPUT_PER_LEVEL = 16;
    private static final int DRILL_INTERVAL_REDUCTION_SECONDS = 2 * 60 * 60;
    private static final int DRILL_INTERVAL_FLOOR_SECONDS = 4 * 60 * 60;

    private int selectedTab;
    private ResearchNode selectedNode = ResearchNode.SCI_SMELT;
    private Button startButton;
    private Button extraBonusButton;
    private float panX;
    private float panY;
    private float zoom = 0.72F;
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
        extraBonusButton = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.legacy_extra_bonus"),
                button -> openExtraBonusPicker(),
                left + 92,
                top + WINDOW_HEIGHT - 25,
                140,
                20
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
        graphics.blit(PANEL, left, top, 0.0F, 0.0F, WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        int titleAreaLeft = left + 224;
        int titleAreaWidth = WINDOW_WIDTH - 248;
        graphics.drawString(font, title, titleAreaLeft + (titleAreaWidth - font.width(title)) / 2, top + 34, 0xFFFFE8AA, true);
        renderTabs(graphics, mouseX, mouseY);
        renderTree(graphics, mouseX, mouseY);
        ResearchNode hovered = hoveredNode(mouseX, mouseY);
        if (hovered == null) {
            renderSelectionLine(graphics);
        }
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
        ResourceLocation[] tabs = {TAB_SCIENCE, TAB_ECONOMIC, TAB_MILITARY, TAB_LEGACY};
        for (int i = 0; i < tabs.length; i++) {
            int tabX = tabX(i);
            int tabY = tabY(i);
            boolean active = selectedTab == i;
            boolean hovered = mouseX >= tabX && mouseX < tabX + TAB_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
            int frame = active ? 0xFFFFCE4A : hovered ? 0xFFC9A24C : 0x663A3D47;
            graphics.fill(tabX - 2, tabY - 2, tabX + TAB_WIDTH + 2, tabY - 1, frame);
            graphics.fill(tabX - 2, tabY + TAB_HEIGHT + 1, tabX + TAB_WIDTH + 2, tabY + TAB_HEIGHT + 2, frame);
            graphics.fill(tabX - 2, tabY - 2, tabX - 1, tabY + TAB_HEIGHT + 2, frame);
            graphics.fill(tabX + TAB_WIDTH + 1, tabY - 2, tabX + TAB_WIDTH + 2, tabY + TAB_HEIGHT + 2, frame);
            float alpha = active ? 1.0F : hovered ? 0.82F : 0.58F;
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
        boolean bigRoot = node.root() && !node.legacy();
        ResourceLocation texture = bigRoot
                ? NODE_ROOT
                : switch (nodeState) {
                    case DONE -> NODE_DONE;
                    case ACTIVE -> NODE_ACTIVE;
                    case AVAILABLE -> NODE_AVAILABLE;
                    case LOCKED -> NODE_LOCKED;
                };
        int size = bigRoot ? ROOT_SIZE : NODE_SIZE;
        int x = screenX(node) - size / 2;
        int y = screenY(node) - size / 2;
        if (hovered || node == selectedNode) {
            drawSelectionFrame(graphics, x - 3, y - 3, size + 6, hovered ? 0xCCC9A24C : 0xCC6FE3D4);
        }
        int sourceSize = 64;
        graphics.blit(texture, x, y, size, size, 0.0F, 0.0F, sourceSize, sourceSize, sourceSize, sourceSize);
        FactionBonus legacyBonus = node.legacy() ? legacyBonus(node.legacySlot()) : null;
        if (legacyBonus != null) {
            int iconSize = size - LEGACY_ICON_INSET * 2;
            float alpha = nodeState == NodeState.LOCKED ? 0.45F : 1.0F;
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(
                    FactionCreateScreen.bonusIcon(legacyBonus),
                    x + LEGACY_ICON_INSET,
                    y + LEGACY_ICON_INSET,
                    iconSize,
                    iconSize,
                    0.0F,
                    0.0F,
                    64,
                    64,
                    64,
                    64
            );
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (nodeState == NodeState.ACTIVE) {
            long remaining = Math.max(0L, snapshot.activeResearchEndMillis() - System.currentTimeMillis());
            long duration = Math.max(1L, effectiveDurationMillis(node));
            float fraction = Math.clamp((duration - remaining) / (float) duration, 0.0F, 1.0F);
            int barWidth = 42;
            int barX = screenX(node) - barWidth / 2;
            int barY = y + size + 6;
            graphics.blit(BAR_EMPTY, barX, barY, barWidth, 5, 0.0F, 0.0F, 182, 5, 182, 5);
            int fullWidth = Math.max(0, (int) (barWidth * fraction));
            if (fullWidth > 0) {
                graphics.blit(BAR_FULL, barX, barY, fullWidth, 5, 0.0F, 0.0F, 182, 5, 182, 5);
            }
        }
    }

    private static void drawSelectionFrame(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + 2, color);
        graphics.fill(x, y + size - 2, x + size, y + size, color);
        graphics.fill(x, y, x + 2, y + size, color);
        graphics.fill(x + size - 2, y, x + size, y + size, color);
    }

    private void renderSelectionLine(GuiGraphics graphics) {
        if (selectedNode == null) {
            return;
        }
        List<InfluenceType> types = selectedNode.costTypes();
        Component influence = text(
                types.size() > 1
                        ? "screen.kingdoms.research_cost_each_short"
                        : "screen.kingdoms.research_cost_short",
                influenceCostPerType(selectedNode),
                effectiveDurationHours(selectedNode)
        );
        int cost = crystalCostPerType(selectedNode);
        Component crystals = cost <= 0
                ? null
                : types.size() > 1
                        ? text("screen.kingdoms.research_crystal_cost_each_line", cost, ownedCrystalsText(types))
                        : text("screen.kingdoms.research_crystal_cost_line", cost, crystalsOf(types.getFirst()));
        int costWidth = types.size() * 14 + 2 + font.width(influence);
        if (crystals != null) {
            costWidth += 8 + types.size() * 18 + 2 + font.width(crystals);
        }
        int lineLeft = left + TREE_LEFT;
        int lineRight = lineLeft + TREE_WIDTH;
        int cursor = Math.max(lineLeft, lineRight - costWidth);
        graphics.drawString(
                font,
                clipToWidth(selectionTitle(selectedNode), Math.max(0, cursor - 8 - lineLeft)),
                lineLeft,
                top + 218,
                0xFFFFE8AA,
                true
        );
        for (InfluenceType type : types) {
            graphics.blit(iconFor(type), cursor, top + 216, 12, 12, 0.0F, 0.0F, 16, 16, 16, 16);
            cursor += 14;
        }
        cursor += 2;
        graphics.drawString(font, influence, cursor, top + 218, 0xFFD7C57C, true);
        cursor += font.width(influence) + 8;
        if (crystals != null) {
            for (InfluenceType type : types) {
                graphics.renderItem(crystalStack(type), cursor, top + 214);
                cursor += 18;
            }
            graphics.drawString(font, crystals, cursor + 2, top + 218, 0xFFB9CFF6, true);
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
        String clipped = font.plainSubstrByWidth(statusMessage, WINDOW_WIDTH - 38);
        graphics.drawString(font, clipped, left + 18, top + WINDOW_HEIGHT + 5, successful ? 0xFFB9F3A9 : 0xFFF2A7A7, true);
    }

    private void renderNodeTooltip(GuiGraphics graphics, ResearchNode node, int mouseX, int mouseY) {
        int boxWidth = 232;
        List<FormattedCharSequence> desc = font.split(nodeDescription(node), boxWidth - 20);
        List<FormattedCharSequence> effect = new ArrayList<>();
        for (Component line : effectLines(node)) {
            effect.addAll(font.split(line, boxWidth - 20));
        }
        List<InfluenceType> types = node.costTypes();
        int descBlock = desc.size() * 11;
        int effectBlock = effect.size() * 11;
        boolean showCrystals = crystalCostPerType(node) > 0;
        int boxHeight = (showCrystals ? 90 : 71) + descBlock + effectBlock;
        int x = Math.min(mouseX + 14, width - boxWidth - 6);
        int y = mouseY + 12;
        int bottomLimit = top + TREE_TOP + TREE_HEIGHT;
        if (y + boxHeight > bottomLimit) {
            y = mouseY - boxHeight - 12;
        }
        y = Math.clamp(y, top + 44, Math.max(top + 44, bottomLimit - boxHeight));
        graphics.fill(x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, 0xFF0B0D12);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xFC15171D);
        graphics.fill(x + 1, y + 1, x + boxWidth - 1, y + 2, 0xFFC9A24C);
        graphics.fill(x + 1, y + boxHeight - 2, x + boxWidth - 1, y + boxHeight - 1, 0xFF3A3D47);
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
        graphics.drawString(
                font,
                text(
                        types.size() > 1
                                ? "screen.kingdoms.research_cost_each_short"
                                : "screen.kingdoms.research_cost_short",
                        influenceCostPerType(node),
                        effectiveDurationHours(node)
                ),
                cursor + 2,
                lineY,
                0xFFE6CE7E,
                true
        );
        int statusY = lineY + 12;
        if (showCrystals) {
            int crystalCursor = x + 8;
            for (InfluenceType type : types) {
                graphics.renderItem(crystalStack(type), crystalCursor, lineY + 10);
                crystalCursor += 18;
            }
            graphics.drawString(
                    font,
                    types.size() > 1
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
                            ),
                    crystalCursor + 2,
                    lineY + 14,
                    0xFFB9CFF6,
                    true
            );
            statusY = lineY + 31;
        }
        graphics.drawString(font, statusText(node), x + 10, statusY, statusColor(node), true);
        for (int i = 0; i < effect.size(); i++) {
            graphics.drawString(font, effect.get(i), x + 10, statusY + 15 + i * 11, 0xFFCBD6F0, true);
        }
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
    }

    private boolean canStart(ResearchNode node) {
        return state(node) == NodeState.AVAILABLE
                && (snapshot.canManage() || snapshot.isOfficer())
                && snapshot.activeResearchNode().isEmpty()
                && hasInfluenceFor(node)
                && hasCrystalsFor(node);
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
        return left + TREE_LEFT + 4 + index * TAB_STEP;
    }

    private int tabY(int index) {
        return top + (selectedTab == index ? 16 : 19);
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
        for (ResearchNode node : visibleNodes()) {
            int size = node.root() && !node.legacy() ? ROOT_SIZE : NODE_SIZE;
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
        return Math.round(top + TREE_TOP + TREE_HEIGHT / 2.0F + (treeY(node) + panY) * zoom);
    }

    private int treeY(ResearchNode node) {
        if (!node.legacy() || legacyBonus(1) != null) {
            return node.treeY();
        }
        return node.treeY() + LEGACY_ROW_SHIFT;
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
        boolean prereqDone = node.prerequisites().stream()
                .allMatch(prereq -> snapshot.completedResearch().contains(prereq.name()));
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
        AVAILABLE,
        ACTIVE,
        DONE
    }
}
