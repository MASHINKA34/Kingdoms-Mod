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

public final class AdminAnalyzerScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int CONTENT_RIGHT = 302;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int TEXT_RED = 0xFF9C2B2B;
    private static final int ROWS = 5;
    private static final int ROW_HEIGHT = 18;
    private static final int LIST_TOP = 92;
    private static final int INFO_ROW_TOP = 81;
    private static final int DETAIL_BUTTON_WIDTH = 18;

    private enum Tab {
        CHUNKS,
        FACTIONS
    }

    private LagTaxPayloads.S2CAnalyzerData data;
    private LagTaxPayloads.S2CChunkDetail detail;
    private boolean awaitingDetail;
    private Tab tab = Tab.CHUNKS;
    private int scroll;
    private int detailScroll;
    private int left;
    private int top;

    public AdminAnalyzerScreen(LagTaxPayloads.S2CAnalyzerData data) {
        super(Component.translatable("screen.kingdoms.analyzer.title"));
        this.data = data;
    }

    public static void handleData(LagTaxPayloads.S2CAnalyzerData payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof AdminAnalyzerScreen screen) {
                screen.acceptData(payload);
            } else {
                minecraft.setScreen(new AdminAnalyzerScreen(payload));
            }
        });
    }

    public static void handleDetail(LagTaxPayloads.S2CChunkDetail payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof AdminAnalyzerScreen screen) {
                screen.acceptDetail(payload);
            }
        });
    }

    private void acceptData(LagTaxPayloads.S2CAnalyzerData payload) {
        data = payload;
        scroll = Math.clamp(scroll, 0, maxScroll());
        rebuildWidgets();
    }

    private void acceptDetail(LagTaxPayloads.S2CChunkDetail payload) {
        detail = payload;
        detailScroll = 0;
        awaitingDetail = false;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        if (detail == null && !awaitingDetail) {
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.analyzer.tab_chunks"),
                    button -> {
                        tab = Tab.CHUNKS;
                        scroll = 0;
                        rebuildWidgets();
                    },
                    left + CONTENT_LEFT, top + 58, 88, 20
            ));
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.analyzer.tab_factions"),
                    button -> {
                        tab = Tab.FACTIONS;
                        scroll = 0;
                        rebuildWidgets();
                    },
                    left + CONTENT_LEFT + 92, top + 58, 88, 20
            ));
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.analyzer.refresh"),
                    button -> PacketDistributor.sendToServer(LagTaxPayloads.C2SOpenAnalyzer.INSTANCE),
                    left + CONTENT_RIGHT - 80, top + 58, 80, 20
            ));
        } else {
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.back"),
                    button -> {
                        detail = null;
                        detailScroll = 0;
                        awaitingDetail = false;
                        rebuildWidgets();
                    },
                    left + CONTENT_LEFT, top + PANEL_HEIGHT - 25, 66, 20
            ));
        }
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
        if (awaitingDetail) {
            Component waiting = Component.translatable("screen.kingdoms.analyzer.measuring");
            graphics.drawString(font, waiting, left + (PANEL_WIDTH - font.width(waiting)) / 2, top + 110, TEXT_MUTED, false);
            return;
        }
        if (detail != null) {
            renderDetail(graphics);
            return;
        }
        if (tab == Tab.CHUNKS) {
            renderChunks(graphics, mouseX, mouseY);
        } else {
            renderFactions(graphics);
        }
    }

    private void renderChunks(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.analyzer.hint"),
                left + CONTENT_LEFT,
                top + INFO_ROW_TOP,
                TEXT_MUTED,
                false
        );
        List<LagTaxPayloads.ChunkEntry> chunks = data.chunks();
        if (chunks.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.analyzer.empty");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2, top + 120, TEXT_MUTED, false);
            return;
        }
        int shown = Math.min(ROWS, chunks.size() - scroll);
        for (int i = 0; i < shown; i++) {
            LagTaxPayloads.ChunkEntry entry = chunks.get(scroll + i);
            int x = left + CONTENT_LEFT;
            int y = top + LIST_TOP + i * ROW_HEIGHT;
            int right = left + CONTENT_RIGHT;
            boolean hovered = mouseX >= x && mouseX < right - DETAIL_BUTTON_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            graphics.fill(x, y, right, y + ROW_HEIGHT - 2, hovered ? 0x55C9A24C : 0x24A8783D);
            String faction = entry.factionName().isBlank()
                    ? Component.translatable("screen.kingdoms.analyzer.wild").getString()
                    : entry.factionName();
            String label = String.format(
                    Locale.ROOT,
                    "%s мс [%d, %d] %s %s%s",
                    formatMs(entry.nanosPerTick()),
                    entry.chunkX() * 16 + 8,
                    entry.chunkZ() * 16 + 8,
                    shortDimension(entry.dimension()),
                    faction,
                    entry.frozen() ? " ❄" : ""
            );
            graphics.drawString(font, label, x + 4, y + 5, entry.frozen() ? TEXT_RED : TEXT_DARK, false);
            boolean detailHovered = mouseX >= right - DETAIL_BUTTON_WIDTH && mouseX < right
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            graphics.drawString(font, "≡", right - 12, y + 5, detailHovered ? 0xFF7A4A12 : TEXT_MUTED, false);
        }
        if (chunks.size() > ROWS) {
            String pager = (scroll + 1) + "-" + (scroll + shown) + " / " + chunks.size();
            graphics.drawString(font, pager, left + CONTENT_RIGHT - font.width(pager), top + INFO_ROW_TOP, TEXT_MUTED, false);
        }
    }

    private void renderFactions(GuiGraphics graphics) {
        Component quota = Component.translatable("screen.kingdoms.analyzer.quota", formatMs(data.quotaNanos()));
        graphics.drawString(font, quota, left + CONTENT_LEFT, top + INFO_ROW_TOP, TEXT_MUTED, false);
        List<LagTaxPayloads.FactionEntry> factions = data.factions();
        if (factions.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.analyzer.empty");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2, top + 120, TEXT_MUTED, false);
            return;
        }
        int shown = Math.min(ROWS, factions.size() - scroll);
        for (int i = 0; i < shown; i++) {
            LagTaxPayloads.FactionEntry entry = factions.get(scroll + i);
            int x = left + CONTENT_LEFT;
            int y = top + LIST_TOP + i * ROW_HEIGHT;
            graphics.fill(x, y, left + CONTENT_RIGHT, y + ROW_HEIGHT - 2, 0x24A8783D);
            String name = entry.name().isBlank()
                    ? Component.translatable("screen.kingdoms.analyzer.wild").getString()
                    : entry.name();
            String label = String.format(
                    Locale.ROOT,
                    "%s мс — %s%s",
                    formatMs(entry.nanosPerTick()),
                    name,
                    entry.frozen() ? " ❄" : ""
            );
            graphics.drawString(font, label, x + 4, y + 5, entry.frozen() ? TEXT_RED : TEXT_DARK, false);
            if (entry.unpaidBill() > 0L) {
                Component unpaid = Component.translatable("screen.kingdoms.analyzer.unpaid", entry.unpaidBill());
                graphics.drawString(font, unpaid, left + CONTENT_RIGHT - 4 - font.width(unpaid), y + 5, TEXT_RED, false);
            }
        }
        if (factions.size() > ROWS) {
            String pager = (scroll + 1) + "-" + (scroll + shown) + " / " + factions.size();
            graphics.drawString(font, pager, left + CONTENT_RIGHT - font.width(pager), top + INFO_ROW_TOP, TEXT_MUTED, false);
        }
    }

    private void renderDetail(GuiGraphics graphics) {
        String header = String.format(
                Locale.ROOT,
                "[%d, %d] %s",
                detail.chunkX() * 16 + 8,
                detail.chunkZ() * 16 + 8,
                shortDimension(detail.dimension())
        );
        graphics.drawString(font, header, left + CONTENT_LEFT, top + 62, TEXT_DARK, false);
        List<LagTaxPayloads.DetailEntry> entries = detail.entries();
        if (entries.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.analyzer.empty");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2, top + 120, TEXT_MUTED, false);
            return;
        }
        int shown = Math.min(ROWS, entries.size() - detailScroll);
        if (entries.size() > ROWS) {
            String pager = (detailScroll + 1) + "-" + (detailScroll + shown) + " / " + entries.size();
            graphics.drawString(font, pager, left + CONTENT_RIGHT - font.width(pager), top + INFO_ROW_TOP, TEXT_MUTED, false);
        }
        for (int i = 0; i < shown; i++) {
            LagTaxPayloads.DetailEntry entry = entries.get(detailScroll + i);
            int y = top + LIST_TOP + i * ROW_HEIGHT;
            graphics.fill(left + CONTENT_LEFT, y, left + CONTENT_RIGHT, y + ROW_HEIGHT - 2, 0x24A8783D);
            String label = String.format(
                    Locale.ROOT,
                    "%s мс %s (%d, %d, %d)",
                    formatMs(entry.nanosPerTick()),
                    blockName(entry.blockId()),
                    entry.pos().getX(),
                    entry.pos().getY(),
                    entry.pos().getZ()
            );
            graphics.drawString(font, label, left + CONTENT_LEFT + 4, y + 5, TEXT_DARK, false);
        }
    }

    private String blockName(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return blockId;
        }
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id)
                .map(block -> block.getName().getString())
                .orElse(blockId);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && detail == null && !awaitingDetail && tab == Tab.CHUNKS && !data.chunks().isEmpty()) {
            int shown = Math.min(ROWS, data.chunks().size() - scroll);
            for (int i = 0; i < shown; i++) {
                int x = left + CONTENT_LEFT;
                int y = top + LIST_TOP + i * ROW_HEIGHT;
                int right = left + CONTENT_RIGHT;
                if (mouseY < y || mouseY >= y + ROW_HEIGHT - 2 || mouseX < x || mouseX >= right) {
                    continue;
                }
                LagTaxPayloads.ChunkEntry entry = data.chunks().get(scroll + i);
                if (mouseX >= right - DETAIL_BUTTON_WIDTH) {
                    awaitingDetail = true;
                    detail = null;
                    PacketDistributor.sendToServer(new LagTaxPayloads.C2SAnalyzerDetail(
                            entry.dimension(), entry.chunkX(), entry.chunkZ()));
                    rebuildWidgets();
                } else {
                    PacketDistributor.sendToServer(new LagTaxPayloads.C2SAnalyzerTeleport(
                            entry.dimension(), entry.chunkX(), entry.chunkZ()));
                    onClose();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (detail != null) {
            int updated = Math.clamp(
                    detailScroll - (int) Math.signum(scrollY),
                    0,
                    Math.max(0, detail.entries().size() - ROWS)
            );
            if (updated != detailScroll) {
                detailScroll = updated;
                return true;
            }
        } else if (!awaitingDetail) {
            int updated = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            if (updated != scroll) {
                scroll = updated;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int maxScroll() {
        int size = tab == Tab.CHUNKS ? data.chunks().size() : data.factions().size();
        return Math.max(0, size - ROWS);
    }

    static String formatMs(long nanosPerTick) {
        return String.format(Locale.ROOT, "%.2f", nanosPerTick / 1_000_000.0D);
    }

    static String shortDimension(ResourceLocation dimension) {
        return "minecraft".equals(dimension.getNamespace()) ? dimension.getPath() : dimension.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
