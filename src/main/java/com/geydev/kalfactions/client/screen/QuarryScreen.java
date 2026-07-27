package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientQuarryState;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.menu.QuarryMenu;
import com.geydev.kalfactions.quarry.QuarryManager;
import com.geydev.kalfactions.quarry.QuarryPayloads;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            KalFactions.MOD_ID,
            "textures/gui/quarry/quarry_gui.png"
    );
    static final int PANEL_WIDTH = 348;
    static final int PANEL_HEIGHT = 260;
    private static final int TEXTURE_WIDTH = 696;
    private static final int TEXTURE_HEIGHT = 520;
    private static final int GOLD = 0xFFC9A24C;
    private static final int GOLD_LIGHT = 0xFFF1D58A;
    private static final int BLUE_DARK = 0xFF08131E;
    private static final int BLUE = 0xFF10283A;
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
        graphics.blit(
                BACKGROUND,
                leftPos,
                topPos,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
        renderMachineryAnimation(graphics, partialTick);
        panel(graphics, layout.summary());
        panel(graphics, layout.details());
        panel(graphics, layout.production());
        drawCenteredFitted(graphics, title, leftPos + PANEL_WIDTH / 2, topPos + 14, 136, GOLD_LIGHT);
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
        drawCenteredFitted(
                graphics,
                Component.translatable("screen.kingdoms.quarry.production_later"),
                layout.production().x() + layout.production().width() / 2,
                layout.production().y() + 9,
                layout.production().width() - 16,
                MUTED
        );
    }

    private void renderSummary(GuiGraphics graphics, Layout layout) {
        int x = layout.summary().x() + 38;
        int y = layout.summary().y() + 8;
        int width = layout.summary().right() - x - 9;
        drawFitted(graphics, statusLabel(state.status()), x, y, width, statusColor(state.status()));
        drawFitted(
                graphics,
                Component.translatable(
                        "screen.kingdoms.quarry.coordinates",
                        state.core().getX(),
                        state.core().getY(),
                        state.core().getZ()
                ),
                x,
                y + 13,
                width,
                TEXT
        );
        Component owner = state.ownerName().isEmpty()
                ? Component.translatable("screen.kingdoms.quarry.neutral")
                : Component.literal(state.ownerName()).withColor(state.ownerColor());
        drawFitted(
                graphics,
                Component.translatable("screen.kingdoms.quarry.owner", owner),
                x,
                y + 26,
                width,
                TEXT
        );
    }

    private void renderDetails(GuiGraphics graphics, Layout layout) {
        int x = layout.details().x() + 9;
        int y = layout.details().y() + 8;
        int leftWidth = 153;
        int rightX = x + 162;
        int rightWidth = layout.details().right() - rightX - 9;
        drawFitted(
                graphics,
                Component.translatable(
                        "screen.kingdoms.quarry.level",
                        state.level(),
                        QuarryManager.MAX_LEVEL
                ),
                x,
                y,
                leftWidth,
                TEXT
        );
        Component next = state.nextLevel() == 0
                ? Component.translatable("screen.kingdoms.quarry.maximum")
                : Component.literal(Integer.toString(state.nextLevel()));
        drawFitted(
                graphics,
                Component.translatable("screen.kingdoms.quarry.next_level", next),
                x,
                y + 13,
                leftWidth,
                TEXT
        );
        Component cost = state.nextLevel() == 0
                ? Component.literal("—")
                : NumismaticsEconomy.format(state.nextCost());
        drawFitted(
                graphics,
                Component.translatable("screen.kingdoms.quarry.next_cost", cost),
                x,
                y + 26,
                leftWidth,
                TEXT
        );
        Component viewer = state.viewerFactionName().isEmpty()
                ? Component.translatable("screen.kingdoms.quarry.no_faction")
                : Component.literal(state.viewerFactionName()).withColor(state.viewerFactionColor());
        drawFitted(
                graphics,
                Component.translatable("screen.kingdoms.quarry.your_faction", viewer),
                rightX,
                y,
                rightWidth,
                TEXT
        );
        drawFitted(
                graphics,
                Component.translatable(
                        "screen.kingdoms.quarry.treasury",
                        NumismaticsEconomy.format(state.treasury())
                ),
                rightX,
                y + 13,
                rightWidth,
                TEXT
        );
        if (!state.actionEnabled() && state.reason() != QuarryPayloads.REASON_NONE) {
            int reasonLeft = x + 162;
            int reasonWidth = layout.details().x() + layout.details().width() - 9 - reasonLeft;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(disabledReason(state), reasonWidth);
            for (int index = 0; index < Math.min(2, lines.size()); index++) {
                graphics.drawString(font, lines.get(index), reasonLeft, y + 26 + index * 10, 0xFFE0A078, false);
            }
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
        drawFitted(
                graphics,
                Component.translatable("screen.kingdoms.quarry.attacker", attacker),
                x + 5,
                y + 4,
                168,
                TEXT
        );
        Component timer = Component.translatable(
                state.capturePaused()
                        ? "screen.kingdoms.quarry.capture_paused"
                        : "screen.kingdoms.quarry.capture_remaining",
                formatTicks(state.captureTicksRemaining())
        );
        drawFitted(graphics, timer, x + 5, y + 13, 168, state.capturePaused() ? 0xFFFFC44F : MUTED);
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

    private void renderMachineryAnimation(GuiGraphics graphics, float partialTick) {
        double phase = (System.currentTimeMillis() + partialTick * 50.0D) * 0.004D;
        int alpha = 52 + (int) Math.round((Math.sin(phase) + 1.0D) * 38.0D);
        int glow = alpha << 24 | 0x42E8F5;
        graphics.fill(leftPos + 43, topPos + 20, leftPos + 47, topPos + 26, glow);
        graphics.fill(leftPos + 301, topPos + 20, leftPos + 305, topPos + 26, glow);
    }

    private void drawFitted(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = maxWidth / (float) textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawCenteredFitted(
            GuiGraphics graphics,
            Component text,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawCenteredString(font, text, centerX, y, color);
            return;
        }
        float scale = maxWidth / (float) textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    static Layout layout(int left, int top) {
        return new Layout(
                new Rect(left + 12, top + 42, PANEL_WIDTH - 24, 54),
                new Rect(left + 12, top + 102, PANEL_WIDTH - 24, 62),
                new Rect(left + 12, top + 170, PANEL_WIDTH - 24, 22),
                new Rect(left + 12, top + 196, PANEL_WIDTH - 24, 26),
                new Rect(left + 84, top + 229, 180, 22)
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
            Rect summary,
            Rect details,
            Rect capture,
            Rect production,
            Rect action
    ) {
    }
}
