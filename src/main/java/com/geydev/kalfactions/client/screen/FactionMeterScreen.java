package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.tax.LagTaxPayloads;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionMeterScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int CONTENT_RIGHT = 302;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int TEXT_RED = 0xFF9C2B2B;
    private static final int TEXT_GREEN = 0xFF2E6B2E;
    private static final int ROWS = 4;
    private static final int ROW_HEIGHT = 15;
    private static final int LIST_TOP = 124;

    private LagTaxPayloads.S2CMeterData data;
    private int scroll;
    private int left;
    private int top;

    public FactionMeterScreen(LagTaxPayloads.S2CMeterData data) {
        super(Component.translatable("screen.kingdoms.meter.title"));
        this.data = data;
    }

    public static void handle(LagTaxPayloads.S2CMeterData payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof FactionMeterScreen screen) {
                screen.acceptData(payload);
            } else {
                minecraft.setScreen(new FactionMeterScreen(payload));
            }
        });
    }

    private void acceptData(LagTaxPayloads.S2CMeterData payload) {
        data = payload;
        scroll = Math.clamp(scroll, 0, maxScroll());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable(data.autoRenew()
                        ? "screen.kingdoms.meter.auto_renew_on"
                        : "screen.kingdoms.meter.auto_renew_off"),
                button -> PacketDistributor.sendToServer(new LagTaxPayloads.C2SSetAutoRenew(!data.autoRenew())),
                left + CONTENT_LEFT, top + PANEL_HEIGHT - 25, 130, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.analyzer.refresh"),
                button -> PacketDistributor.sendToServer(LagTaxPayloads.C2SOpenMeter.INSTANCE),
                left + CONTENT_LEFT + 134, top + PANEL_HEIGHT - 25, 64, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74, top + PANEL_HEIGHT - 25, 66, 20
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
        graphics.drawString(font, title, left + (PANEL_WIDTH - font.width(title)) / 2, top + 48, TEXT_DARK, false);

        double loadMs = data.totalNanos() / 1_000_000.0D;
        double quotaMs = data.quotaNanos() / 1_000_000.0D;
        Component loadLine = Component.translatable(
                "screen.kingdoms.meter.load",
                formatMs(loadMs),
                formatMs(quotaMs)
        );
        int loadColor = quotaMs > 0.0D && loadMs >= quotaMs ? TEXT_RED
                : quotaMs > 0.0D && loadMs >= quotaMs * 0.75D ? 0xFF8A6D1A
                : TEXT_GREEN;
        graphics.drawString(font, loadLine, left + CONTENT_LEFT, top + 62, loadColor, false);

        int barLeft = left + CONTENT_LEFT;
        int barRight = left + CONTENT_RIGHT;
        int barTop = top + 74;
        graphics.fill(barLeft, barTop, barRight, barTop + 6, 0x40000000);
        if (quotaMs > 0.0D) {
            double fillFraction = Math.min(1.0D, loadMs / quotaMs);
            int fill = (int) ((barRight - barLeft) * fillFraction);
            int fillColor = loadMs >= quotaMs ? 0xFFC04040 : loadMs >= quotaMs * 0.75D ? 0xFFC9A24C : 0xFF6FBF6F;
            graphics.fill(barLeft, barTop, barLeft + fill, barTop + 6, fillColor);
        }

        Component forecast = data.forecastPerDay() > 0L
                ? Component.translatable("screen.kingdoms.meter.forecast", data.forecastPerDay())
                : Component.translatable("screen.kingdoms.meter.forecast_free");
        graphics.drawString(font, forecast, left + CONTENT_LEFT, top + 84, TEXT_MUTED, false);
        Component accrued = Component.translatable("screen.kingdoms.meter.accrued", data.accruedBill());
        graphics.drawString(font, accrued, left + CONTENT_LEFT, top + 94, TEXT_MUTED, false);

        if (data.frozen()) {
            Component frozen = Component.translatable("screen.kingdoms.meter.frozen", data.unpaidBill());
            graphics.drawString(font, frozen, left + CONTENT_LEFT, top + 104, TEXT_RED, false);
        } else if (data.unpaidBill() > 0L) {
            Component unpaid = Component.translatable("screen.kingdoms.meter.unpaid", data.unpaidBill());
            graphics.drawString(font, unpaid, left + CONTENT_LEFT, top + 104, TEXT_RED, false);
        }

        List<LagTaxPayloads.MeterChunk> chunks = data.chunks();
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.meter.chunks_header"),
                left + CONTENT_LEFT,
                top + LIST_TOP - 10,
                TEXT_DARK,
                false
        );
        if (chunks.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.meter.empty");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2, top + LIST_TOP + 16, TEXT_MUTED, false);
        } else {
            int shown = Math.min(ROWS, chunks.size() - scroll);
            for (int i = 0; i < shown; i++) {
                LagTaxPayloads.MeterChunk chunk = chunks.get(scroll + i);
                int y = top + LIST_TOP + i * ROW_HEIGHT;
                graphics.fill(left + CONTENT_LEFT, y, left + CONTENT_RIGHT, y + ROW_HEIGHT - 2, 0x24A8783D);
                String label = String.format(
                        Locale.ROOT,
                        "%s мс [%d, %d] %s",
                        AdminAnalyzerScreen.formatMs(chunk.nanosPerTick()),
                        chunk.chunkX() * 16 + 8,
                        chunk.chunkZ() * 16 + 8,
                        AdminAnalyzerScreen.shortDimension(chunk.dimension())
                );
                graphics.drawString(font, label, left + CONTENT_LEFT + 4, y + 4, TEXT_DARK, false);
                if (chunk.loaded()) {
                    long remaining = Math.max(0L, chunk.expiresAtMillis() - System.currentTimeMillis());
                    Component timer = Component.translatable("screen.kingdoms.meter.load_timer", formatDuration(remaining));
                    graphics.drawString(font, timer, left + CONTENT_RIGHT - 4 - font.width(timer), y + 4, TEXT_GREEN, false);
                }
            }
            if (chunks.size() > ROWS) {
                String pager = (scroll + 1) + "-" + (scroll + shown) + " / " + chunks.size();
                graphics.drawString(font, pager, left + CONTENT_RIGHT - font.width(pager), top + LIST_TOP - 10, TEXT_MUTED, false);
            }
        }
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

    private int maxScroll() {
        return Math.max(0, data.chunks().size() - ROWS);
    }

    private static String formatMs(double ms) {
        return String.format(Locale.ROOT, "%.2f", ms);
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = totalSeconds % 3_600L / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
