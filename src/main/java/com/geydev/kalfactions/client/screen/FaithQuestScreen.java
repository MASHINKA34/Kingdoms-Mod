package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faith.FaithGod;
import com.geydev.kalfactions.faith.FaithPayloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FaithQuestScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int CONTENT_RIGHT = 302;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int TEXT_DONE = 0xFF2F6B34;
    private static final int TEXT_MISSING = 0xFF8A3A2A;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 5;
    private static final int LIST_TOP = 92;

    private FaithPayloads.S2CFaithState state;
    private int left;
    private int top;
    private int scroll;

    private FaithQuestScreen(FaithPayloads.S2CFaithState state) {
        super(Component.translatable("screen.kingdoms.faith.quest.title"));
        this.state = state;
    }

    public static void handle(FaithPayloads.S2CFaithState payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof FaithQuestScreen screen) {
                screen.accept(payload);
            } else {
                minecraft.setScreen(new FaithQuestScreen(payload));
            }
            payload.notice().ifPresent(notice ->
                    com.geydev.kalfactions.client.KingdomsNoticeToast.show(notice, payload.noticeSuccessful()));
        });
    }

    private void accept(FaithPayloads.S2CFaithState payload) {
        state = payload;
        scroll = Math.clamp(scroll, 0, maxScroll());
        rebuildWidgets();
    }

    private FaithGod god() {
        return FaithGod.byIndex(state.god()).orElse(FaithGod.SCIENCE);
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        scroll = Math.clamp(scroll, 0, maxScroll());
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.faith.quest.offer"),
                button -> send(FaithPayloads.ACTION_OFFER_QUEST),
                left + CONTENT_LEFT,
                top + PANEL_HEIGHT - 27,
                88,
                20
        ));
        KingdomsButton levelUp = KingdomsButton.create(
                Component.translatable("screen.kingdoms.faith.quest.level_up"),
                button -> send(FaithPayloads.ACTION_LEVEL_UP),
                left + CONTENT_LEFT + 94,
                top + PANEL_HEIGHT - 27,
                106,
                20
        );
        levelUp.active = state.questComplete();
        addRenderableWidget(levelUp);
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74,
                top + PANEL_HEIGHT - 27,
                66,
                20
        ));
    }

    private void send(byte action) {
        PacketDistributor.sendToServer(new FaithPayloads.C2SFaithAction(state.statuePos(), action));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component godName = Component.translatable(god().translationKey());
        graphics.drawString(font, godName, left + (PANEL_WIDTH - font.width(godName)) / 2, top + 44, TEXT_DARK, false);
        Component level = Component.translatable("screen.kingdoms.faith.level", state.level(), FaithGod.MAX_LEVEL);
        graphics.drawString(font, level, left + (PANEL_WIDTH - font.width(level)) / 2, top + 56, TEXT_MUTED, false);

        if (state.level() >= FaithGod.MAX_LEVEL) {
            Component maxed = Component.translatable("screen.kingdoms.faith.quest.maxed");
            graphics.drawString(font, maxed, left + (PANEL_WIDTH - font.width(maxed)) / 2, top + 110, TEXT_DONE, false);
            return;
        }

        Component header = Component.translatable("screen.kingdoms.faith.quest.next", state.level() + 1);
        graphics.drawString(font, header, left + CONTENT_LEFT, top + 74, TEXT_DARK, false);
        if (state.killsOrTrophy() && state.killsRequired() > 0) {
            Component either = Component.translatable("screen.kingdoms.faith.quest.either");
            graphics.drawString(font, either, left + CONTENT_RIGHT - font.width(either), top + 74, TEXT_MUTED, false);
        }

        List<Row> rows = rows();
        int shown = Math.min(VISIBLE_ROWS, rows.size() - scroll);
        ItemStack hovered = null;
        for (int index = 0; index < shown; index++) {
            Row row = rows.get(scroll + index);
            int y = top + LIST_TOP + index * ROW_HEIGHT;
            graphics.fill(left + CONTENT_LEFT, y, left + CONTENT_RIGHT, y + ROW_HEIGHT - 2, 0x24A8783D);
            if (!row.icon.isEmpty()) {
                graphics.renderItem(row.icon, left + CONTENT_LEFT + 2, y + 1);
                if (mouseX >= left + CONTENT_LEFT + 2 && mouseX < left + CONTENT_LEFT + 20
                        && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
                    hovered = row.icon;
                }
            }
            graphics.drawString(font, row.label, left + CONTENT_LEFT + 24, y + 5, TEXT_DARK, false);
            Component progress = Component.literal(row.done + " / " + row.needed);
            graphics.drawString(
                    font,
                    progress,
                    left + CONTENT_RIGHT - 6 - font.width(progress),
                    y + 5,
                    row.done >= row.needed ? TEXT_DONE : TEXT_MISSING,
                    false
            );
        }
        if (rows.size() > VISIBLE_ROWS) {
            String pager = (scroll + 1) + "-" + (scroll + shown) + " / " + rows.size();
            graphics.drawString(font, pager, left + CONTENT_RIGHT - font.width(pager), top + 84, TEXT_MUTED, false);
        }
        if (hovered != null) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private List<Row> rows() {
        List<Row> rows = new java.util.ArrayList<>();
        for (FaithPayloads.QuestEntry entry : state.entries()) {
            if (entry.required() <= 0) {
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(entry.iconItemId());
            ItemStack icon = itemId == null
                    ? new ItemStack(Items.AIR)
                    : new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            Component label = entry.labelKey().isEmpty()
                    ? icon.getHoverName()
                    : Component.translatable(entry.labelKey());
            rows.add(new Row(icon, label, entry.delivered(), entry.required()));
        }
        if (state.killsRequired() > 0) {
            rows.add(new Row(
                    ItemStack.EMPTY,
                    Component.translatable("screen.kingdoms.faith.quest.kills"),
                    state.killsDone(),
                    state.killsRequired()
            ));
        }
        if (state.spursRequired() > 0L) {
            rows.add(new Row(
                    ItemStack.EMPTY,
                    Component.translatable("screen.kingdoms.faith.quest.spurs"),
                    state.spursDelivered(),
                    state.spursRequired()
            ));
        }
        return rows;
    }

    private int maxScroll() {
        return Math.max(0, rows().size() - VISIBLE_ROWS);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int updated = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        if (updated != scroll) {
            scroll = updated;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(ItemStack icon, Component label, long done, long needed) {
    }
}
