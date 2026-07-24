package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.ClientQuarryState;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.menu.QuarryMenu;
import com.geydev.kalfactions.quarry.QuarryManager;
import com.geydev.kalfactions.quarry.QuarryPayloads;
import com.geydev.kalfactions.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    static final int PANEL_WIDTH = 348;
    static final int PANEL_HEIGHT = 248;
    private static final int GOLD = 0xFFC9A24C;
    private static final int GOLD_LIGHT = 0xFFF1D58A;
    private static final int BLUE_DARK = 0xFF08131E;
    private static final int BLUE = 0xFF10283A;
    private static final int BLUE_LIGHT = 0xFF1A4058;
    private static final int TEXT = 0xFFE8DDBD;
    private static final int MUTED = 0xFFAAB3B8;
    private QuarryPayloads.S2CState state;
    private KingdomsButton actionButton;
    private boolean pending;

    public QuarryScreen(QuarryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        titleLabelX = 0;
        titleLabelY = 0;
        inventoryLabelX = 0;
        inventoryLabelY = 0;
    }

    @Override
    protected void init() {
        super.init();
        Layout layout = layout(leftPos, topPos);
        state = ClientQuarryState.get(menu.containerId);
        actionButton = addRenderableWidget(KingdomsButton.create(
                actionLabel(state),
                button -> sendAction(),
                layout.action().x(),
                layout.action().y(),
                layout.action().width(),
                layout.action().height()
        ));
        refreshButton();
        PacketDistributor.sendToServer(new QuarryPayloads.C2SRequestState(menu.containerId, menu.core()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (actionButton != null && actionButton.isHovered() && !actionButton.active) {
            graphics.renderTooltip(font, disabledReason(state), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Layout layout = layout(leftPos, topPos);
        panel(graphics, layout.panel());
        panel(graphics, layout.summary());
        panel(graphics, layout.details());
        panel(graphics, layout.production());
        graphics.fill(
                layout.header().x(),
                layout.header().y(),
                layout.header().right(),
                layout.header().bottom(),
                BLUE_LIGHT
        );
        graphics.fill(
                layout.header().x(),
                layout.header().bottom() - 2,
                layout.header().right(),
                layout.header().bottom(),
                GOLD
        );
        graphics.drawCenteredString(font, title, leftPos + PANEL_WIDTH / 2, topPos + 14, GOLD_LIGHT);
        graphics.renderItem(
                new ItemStack(ModBlocks.QUARRY_CORE.get()),
                layout.summary().x() + 12,
                layout.summary().y() + 15
        );
        if (state == null) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.kingdoms.quarry.loading"),
                    leftPos + PANEL_WIDTH / 2,
                    topPos + 112,
                    MUTED
            );
            return;
        }
        renderSummary(graphics, layout);
        renderDetails(graphics, layout);
        renderCapture(graphics, layout);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.kingdoms.quarry.production_later"),
                layout.production().x() + layout.production().width() / 2,
                layout.production().y() + 9,
                MUTED
        );
    }

    private void renderSummary(GuiGraphics graphics, Layout layout) {
        int x = layout.summary().x() + 38;
        int y = layout.summary().y() + 8;
        graphics.drawString(font, statusLabel(state.status()), x, y, statusColor(state.status()), false);
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.kingdoms.quarry.coordinates",
                        state.core().getX(),
                        state.core().getY(),
                        state.core().getZ()
                ),
                x,
                y + 13,
                TEXT,
                false
        );
        Component owner = state.ownerName().isEmpty()
                ? Component.translatable("screen.kingdoms.quarry.neutral")
                : Component.literal(state.ownerName()).withColor(state.ownerColor());
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.quarry.owner", owner),
                x,
                y + 26,
                TEXT,
                false
        );
    }

    private void renderDetails(GuiGraphics graphics, Layout layout) {
        int x = layout.details().x() + 9;
        int y = layout.details().y() + 8;
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.kingdoms.quarry.level",
                        state.level(),
                        QuarryManager.MAX_LEVEL
                ),
                x,
                y,
                TEXT,
                false
        );
        Component next = state.nextLevel() == 0
                ? Component.translatable("screen.kingdoms.quarry.maximum")
                : Component.literal(Integer.toString(state.nextLevel()));
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.quarry.next_level", next),
                x,
                y + 13,
                TEXT,
                false
        );
        Component cost = state.nextLevel() == 0
                ? Component.literal("—")
                : NumismaticsEconomy.format(state.nextCost());
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.quarry.next_cost", cost),
                x,
                y + 26,
                TEXT,
                false
        );
        Component viewer = state.viewerFactionName().isEmpty()
                ? Component.translatable("screen.kingdoms.quarry.no_faction")
                : Component.literal(state.viewerFactionName()).withColor(state.viewerFactionColor());
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.quarry.your_faction", viewer),
                x + 162,
                y,
                TEXT,
                false
        );
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.kingdoms.quarry.treasury",
                        NumismaticsEconomy.format(state.treasury())
                ),
                x + 162,
                y + 13,
                TEXT,
                false
        );
        if (!state.actionEnabled() && state.reason() != QuarryPayloads.REASON_NONE) {
            graphics.drawString(font, disabledReason(state), x + 162, y + 26, 0xFFE0A078, false);
        }
    }

    private void renderCapture(GuiGraphics graphics, Layout layout) {
        if (state.status() != QuarryPayloads.STATUS_UNDER_ATTACK) {
            return;
        }
        int x = layout.capture().x();
        int y = layout.capture().y();
        int width = layout.capture().width();
        graphics.fill(x, y, x + width, y + 22, 0xFF0A1823);
        Component attacker = Component.literal(state.attackerName()).withColor(state.attackerColor());
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.quarry.attacker", attacker),
                x + 5,
                y + 4,
                TEXT,
                false
        );
        Component timer = Component.translatable(
                state.capturePaused()
                        ? "screen.kingdoms.quarry.capture_paused"
                        : "screen.kingdoms.quarry.capture_remaining",
                formatTicks(state.captureTicksRemaining())
        );
        graphics.drawString(font, timer, x + 5, y + 13, state.capturePaused() ? 0xFFFFC44F : MUTED, false);
        int barX = x + 178;
        int barY = y + 7;
        int barWidth = width - 184;
        graphics.fill(barX, barY, barX + barWidth, barY + 8, 0xFF03080C);
        float completed = 1.0F - state.captureTicksRemaining() / (float) QuarryManager.CAPTURE_TICKS;
        int fill = Math.round(Math.clamp(completed, 0.0F, 1.0F) * barWidth);
        graphics.fill(barX, barY, barX + fill, barY + 8, state.capturePaused() ? 0xFFE6A829 : 0xFFD75A38);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void removed() {
        ClientQuarryState.clear(menu.containerId);
        super.removed();
    }

    public void acceptState(QuarryPayloads.S2CState newState) {
        if (!newState.core().equals(menu.core())) {
            return;
        }
        state = newState;
        pending = false;
        refreshButton();
    }

    private void sendAction() {
        if (state == null
                || pending
                || !state.actionEnabled()
                || state.action() == QuarryPayloads.ACTION_NONE) {
            return;
        }
        pending = true;
        refreshButton();
        PacketDistributor.sendToServer(new QuarryPayloads.C2SAction(
                menu.containerId,
                menu.core(),
                state.stateVersion(),
                state.action()
        ));
    }

    private void refreshButton() {
        if (actionButton == null) {
            return;
        }
        actionButton.setMessage(actionLabel(state));
        actionButton.active = state != null
                && state.action() != QuarryPayloads.ACTION_NONE
                && state.actionEnabled()
                && !pending;
    }

    private static Component actionLabel(QuarryPayloads.S2CState state) {
        int action = state == null ? QuarryPayloads.ACTION_NONE : state.action();
        return Component.translatable(switch (action) {
            case QuarryPayloads.ACTION_ACTIVATE -> "screen.kingdoms.quarry.activate";
            case QuarryPayloads.ACTION_UPGRADE -> "screen.kingdoms.quarry.upgrade";
            case QuarryPayloads.ACTION_CAPTURE -> "screen.kingdoms.quarry.capture";
            default -> "screen.kingdoms.quarry.no_action";
        });
    }

    private static Component statusLabel(int status) {
        return Component.translatable(switch (status) {
            case QuarryPayloads.STATUS_OWNED -> "screen.kingdoms.quarry.status_owned";
            case QuarryPayloads.STATUS_UNDER_ATTACK -> "screen.kingdoms.quarry.status_attacked";
            default -> "screen.kingdoms.quarry.status_neutral";
        });
    }

    private static int statusColor(int status) {
        return switch (status) {
            case QuarryPayloads.STATUS_OWNED -> 0xFF79D18A;
            case QuarryPayloads.STATUS_UNDER_ATTACK -> 0xFFFF8268;
            default -> GOLD_LIGHT;
        };
    }

    private static Component disabledReason(QuarryPayloads.S2CState state) {
        int reason = state == null ? QuarryPayloads.REASON_NONE : state.reason();
        return Component.translatable(switch (reason) {
            case QuarryPayloads.REASON_NOT_IN_FACTION -> "screen.kingdoms.quarry.reason.not_in_faction";
            case QuarryPayloads.REASON_REQUIRES_ACTIVATOR -> "screen.kingdoms.quarry.reason.requires_activator";
            case QuarryPayloads.REASON_NO_PERMISSION -> "screen.kingdoms.quarry.reason.no_permission";
            case QuarryPayloads.REASON_INSUFFICIENT_FUNDS -> "screen.kingdoms.quarry.reason.insufficient_funds";
            case QuarryPayloads.REASON_MAX_LEVEL -> "screen.kingdoms.quarry.reason.max_level";
            case QuarryPayloads.REASON_CAPTURE_ACTIVE -> "screen.kingdoms.quarry.reason.capture_active";
            case QuarryPayloads.REASON_CAPTURE_BUSY -> "screen.kingdoms.quarry.reason.capture_busy";
            default -> "screen.kingdoms.quarry.loading";
        });
    }

    private static String formatTicks(int ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L);
    }

    static Layout layout(int left, int top) {
        return new Layout(
                new Rect(left, top, PANEL_WIDTH, PANEL_HEIGHT),
                new Rect(left + 6, top + 6, PANEL_WIDTH - 12, 28),
                new Rect(left + 12, top + 42, PANEL_WIDTH - 24, 54),
                new Rect(left + 12, top + 102, PANEL_WIDTH - 24, 50),
                new Rect(left + 12, top + 158, PANEL_WIDTH - 24, 22),
                new Rect(left + 12, top + 184, PANEL_WIDTH - 24, 26),
                new Rect(left + 84, top + 217, 180, 22)
        );
    }

    private static void panel(GuiGraphics graphics, Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GOLD);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, BLUE_DARK);
        graphics.fill(rect.x() + 3, rect.y() + 3, rect.right() - 3, rect.bottom() - 3, BLUE);
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    record Layout(
            Rect panel,
            Rect header,
            Rect summary,
            Rect details,
            Rect capture,
            Rect production,
            Rect action
    ) {
    }
}
