package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faith.FaithGod;
import com.geydev.kalfactions.faith.FaithPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FaithOfferingScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int CONTENT_RIGHT = 302;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int TEXT_DONE = 0xFF2F6B34;

    private FaithPayloads.S2CFaithState state;
    private int left;
    private int top;

    private FaithOfferingScreen(FaithPayloads.S2CFaithState state) {
        super(Component.translatable("screen.kingdoms.faith.offering.title"));
        this.state = state;
    }

    public static void handle(FaithPayloads.S2CFaithState payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof FaithOfferingScreen screen) {
                screen.accept(payload);
            } else {
                minecraft.setScreen(new FaithOfferingScreen(payload));
            }
            payload.notice().ifPresent(notice ->
                    com.geydev.kalfactions.client.KingdomsNoticeToast.show(notice, payload.noticeSuccessful()));
        });
    }

    private void accept(FaithPayloads.S2CFaithState payload) {
        state = payload;
        rebuildWidgets();
    }

    private FaithGod god() {
        return FaithGod.byIndex(state.god()).orElse(FaithGod.SCIENCE);
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        KingdomsButton offer = KingdomsButton.create(
                Component.translatable("screen.kingdoms.faith.offering.activate"),
                button -> PacketDistributor.sendToServer(new FaithPayloads.C2SFaithAction(
                        state.statuePos(),
                        FaithPayloads.ACTION_ACTIVATE_BUFF
                )),
                left + CONTENT_LEFT,
                top + PANEL_HEIGHT - 27,
                168,
                20
        );
        offer.active = state.buffRemainingMillis() <= 0L;
        addRenderableWidget(offer);
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74,
                top + PANEL_HEIGHT - 27,
                66,
                20
        ));
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

        Component header = Component.translatable("screen.kingdoms.faith.offering.effects");
        graphics.drawString(font, header, left + CONTENT_LEFT, top + 76, TEXT_DARK, false);
        List<Component> effects = effectLines();
        for (int index = 0; index < effects.size(); index++) {
            graphics.drawString(font, effects.get(index), left + CONTENT_LEFT + 6, top + 92 + index * 12,
                    TEXT_MUTED, false);
        }

        int y = top + 106 + effects.size() * 12;
        y = drawWrapped(graphics, Component.translatable(
                "screen.kingdoms.faith.offering.cost",
                state.buffCrystalCost(),
                god().crystal().getDescription()
        ), y, TEXT_DARK);
        y = drawWrapped(graphics, Component.translatable(
                "screen.kingdoms.faith.offering.duration", state.buffMinutes()), y, TEXT_MUTED);

        y += 6;
        boolean burning = state.buffRemainingMillis() > 0L;
        y = drawWrapped(graphics, burning
                ? Component.translatable(
                        "screen.kingdoms.faith.offering.remaining",
                        formatDuration(state.buffRemainingMillis()))
                : Component.translatable("screen.kingdoms.faith.offering.idle"),
                y, burning ? TEXT_DONE : TEXT_MUTED);
        if (burning && !state.buffOwnedByViewer()) {
            drawWrapped(graphics, Component.translatable("screen.kingdoms.faith.offering.forfeited"), y, TEXT_MUTED);
        }
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int y, int color) {
        int width = CONTENT_RIGHT - CONTENT_LEFT;
        for (var line : font.split(text, width)) {
            graphics.drawString(font, line, left + CONTENT_LEFT, y, color, false);
            y += 12;
        }
        return y;
    }

    private List<Component> effectLines() {
        List<Component> lines = new ArrayList<>(3);
        FaithGod god = god();
        switch (god) {
            case SCIENCE -> {
                lines.add(Component.translatable(
                        "screen.kingdoms.faith.effect.science.craft", percent(state.effectPrimary())));
                if (state.effectSecondary() > 0.0D) {
                    lines.add(Component.translatable(
                            "screen.kingdoms.faith.effect.science.experience", percent(state.effectSecondary())));
                }
            }
            case WAR -> {
                lines.add(Component.translatable(
                        "screen.kingdoms.faith.effect.war.damage", hearts(state.effectPrimary())));
                if (state.effectSecondary() > 0.0D) {
                    lines.add(Component.translatable(
                            "screen.kingdoms.faith.effect.war.health", hearts(state.effectSecondary())));
                }
            }
            case ECONOMY -> {
                lines.add(Component.translatable(
                        "screen.kingdoms.faith.effect.economy.sell", percent(state.effectPrimary())));
                if (state.effectSecondary() > 0.0D) {
                    lines.add(Component.translatable(
                            "screen.kingdoms.faith.effect.economy.drops", percent(state.effectSecondary())));
                }
                if (state.effectExtra() > 0) {
                    lines.add(Component.translatable(
                            "screen.kingdoms.faith.effect.economy.highlight", state.effectExtra()));
                }
            }
        }
        return lines;
    }

    private static String percent(double fraction) {
        return trim(fraction * 100.0D);
    }

    private static String hearts(double halfHearts) {
        return trim(halfHearts / 2.0D);
    }

    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis + 999L) / 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
