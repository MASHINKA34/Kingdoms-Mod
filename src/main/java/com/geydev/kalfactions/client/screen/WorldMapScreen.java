package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.ClientClaimStore;
import com.geydev.kalfactions.client.ClientWorldMapStore;
import com.geydev.kalfactions.client.ClientWorldMapTracks;
import com.geydev.kalfactions.net.FactionPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class WorldMapScreen extends Screen {
    private static final int TRACK_COLOR = 0xFFFF4FA8;
    private static final int STATION_COLOR = 0xFFFFD24A;
    private static final int STATION_BORDER = 0xFF1A1A1A;
    private static final int TRAIN_COLOR = 0xFFFFFFFF;
    private static final int TRAIN_BORDER = 0xFF101010;
    private static final int CLAIM_ALPHA = 0x55000000;
    private static final int GOLD = 0xFFF3D58B;
    private static final int TEXT = 0xFFEDE6D6;
    private static final double STATION_HOVER_RADIUS_SQ = 36.0;

    private int viewLeft;
    private int viewTop;
    private int viewRight;
    private int viewBottom;
    private double zoom = 1.0;
    private double centerImageX;
    private double centerImageZ;
    private boolean initialised;
    private boolean dragging;

    public WorldMapScreen() {
        super(Component.translatable("screen.kingdoms.worldmap.title"));
    }

    @Override
    protected void init() {
        int margin = 16;
        viewLeft = margin;
        viewTop = 28;
        viewRight = width - margin;
        viewBottom = height - 16;
        int resolution = Math.max(1, ClientWorldMapStore.resolution());
        if (!initialised) {
            centerImageX = resolution / 2.0;
            centerImageZ = resolution / 2.0;
            initialised = true;
        }
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                width - margin - 64,
                4,
                64,
                20
        ));
    }

    private double scale() {
        int resolution = Math.max(1, ClientWorldMapStore.resolution());
        double base = Math.min(
                (viewRight - viewLeft) / (double) resolution,
                (viewBottom - viewTop) / (double) resolution
        );
        return base * zoom;
    }

    private double midX() {
        return (viewLeft + viewRight) / 2.0;
    }

    private double midY() {
        return (viewTop + viewBottom) / 2.0;
    }

    private double imageToScreenX(double imageX) {
        return midX() + (imageX - centerImageX) * scale();
    }

    private double imageToScreenY(double imageZ) {
        return midY() + (imageZ - centerImageZ) * scale();
    }

    private double worldToImage(double world, double min, int regionBlocks, int resolution) {
        return (world - min) / regionBlocks * resolution;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, (width - titleWidth) / 2, 10, GOLD, true);

        ResourceLocation texture = ClientWorldMapStore.texture();
        int regionBlocks = ClientWorldMapStore.regionBlocks();
        if (texture == null || regionBlocks <= 0 || minecraft == null || minecraft.level == null) {
            Component message = Component.translatable("screen.kingdoms.worldmap.empty");
            graphics.drawCenteredString(font, message, width / 2, height / 2, TEXT);
            return;
        }

        int resolution = ClientWorldMapStore.resolution();
        double minX = ClientWorldMapStore.centerX() - regionBlocks / 2.0;
        double minZ = ClientWorldMapStore.centerZ() - regionBlocks / 2.0;
        ResourceKey<Level> dimension = minecraft.level.dimension();

        graphics.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        graphics.fill(viewLeft, viewTop, viewRight, viewBottom, 0xFF101014);

        int drawX = (int) Math.round(imageToScreenX(0));
        int drawY = (int) Math.round(imageToScreenY(0));
        int drawSize = (int) Math.round(resolution * scale());
        if (drawSize > 0) {
            graphics.blit(texture, drawX, drawY, drawSize, drawSize, 0.0F, 0.0F, resolution, resolution, resolution, resolution);
        }

        renderClaims(graphics, dimension, minX, minZ, regionBlocks, resolution);
        renderTracks(graphics, dimension, minX, minZ, regionBlocks, resolution);
        renderStations(graphics, dimension, minX, minZ, regionBlocks, resolution);
        renderTrains(graphics, dimension, minX, minZ, regionBlocks, resolution);
        graphics.disableScissor();

        renderTooltip(graphics, mouseX, mouseY, dimension, minX, minZ, regionBlocks, resolution);
    }

    private void renderClaims(GuiGraphics graphics, ResourceKey<Level> dimension,
                              double minX, double minZ, int regionBlocks, int resolution) {
        for (var entry : ClientClaimStore.claims(dimension).entrySet()) {
            ChunkPos pos = new ChunkPos(entry.getKey());
            int x0 = (int) Math.round(imageToScreenX(worldToImage(pos.x * 16, minX, regionBlocks, resolution)));
            int x1 = (int) Math.round(imageToScreenX(worldToImage(pos.x * 16 + 16, minX, regionBlocks, resolution)));
            int y0 = (int) Math.round(imageToScreenY(worldToImage(pos.z * 16, minZ, regionBlocks, resolution)));
            int y1 = (int) Math.round(imageToScreenY(worldToImage(pos.z * 16 + 16, minZ, regionBlocks, resolution)));
            if (x1 < viewLeft || x0 > viewRight || y1 < viewTop || y0 > viewBottom) {
                continue;
            }
            int color = (entry.getValue().color() & 0x00FFFFFF) | CLAIM_ALPHA;
            graphics.fill(x0, y0, Math.max(x0 + 1, x1), Math.max(y0 + 1, y1), color);
        }
    }

    private void renderTracks(GuiGraphics graphics, ResourceKey<Level> dimension,
                              double minX, double minZ, int regionBlocks, int resolution) {
        float[] segments = ClientWorldMapTracks.segments(dimension);
        for (int i = 0; i + 3 < segments.length; i += 4) {
            double ax = imageToScreenX(worldToImage(segments[i], minX, regionBlocks, resolution));
            double ay = imageToScreenY(worldToImage(segments[i + 1], minZ, regionBlocks, resolution));
            double bx = imageToScreenX(worldToImage(segments[i + 2], minX, regionBlocks, resolution));
            double by = imageToScreenY(worldToImage(segments[i + 3], minZ, regionBlocks, resolution));
            drawLine(graphics, ax, ay, bx, by);
        }
    }

    private void drawLine(GuiGraphics graphics, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double dist = Math.sqrt(dx * dx + dy * dy);
        int steps = (int) Math.min(4000.0, Math.max(1.0, dist));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(ax + dx * t);
            int y = (int) Math.round(ay + dy * t);
            graphics.fill(x, y, x + 1, y + 1, TRACK_COLOR);
        }
    }

    private void renderStations(GuiGraphics graphics, ResourceKey<Level> dimension,
                                double minX, double minZ, int regionBlocks, int resolution) {
        for (FactionPayloads.StationView station : ClientWorldMapTracks.stations(dimension)) {
            int sx = (int) Math.round(imageToScreenX(worldToImage(station.x(), minX, regionBlocks, resolution)));
            int sy = (int) Math.round(imageToScreenY(worldToImage(station.z(), minZ, regionBlocks, resolution)));
            if (sx < viewLeft - 4 || sx > viewRight + 4 || sy < viewTop - 4 || sy > viewBottom + 4) {
                continue;
            }
            graphics.fill(sx - 3, sy - 3, sx + 3, sy + 3, STATION_BORDER);
            graphics.fill(sx - 2, sy - 2, sx + 2, sy + 2, STATION_COLOR);
        }
    }

    private void renderTrains(GuiGraphics graphics, ResourceKey<Level> dimension,
                             double minX, double minZ, int regionBlocks, int resolution) {
        for (FactionPayloads.TrainView train : ClientWorldMapTracks.trains(dimension)) {
            int sx = (int) Math.round(imageToScreenX(worldToImage(train.x(), minX, regionBlocks, resolution)));
            int sy = (int) Math.round(imageToScreenY(worldToImage(train.z(), minZ, regionBlocks, resolution)));
            if (sx < viewLeft - 4 || sx > viewRight + 4 || sy < viewTop - 4 || sy > viewBottom + 4) {
                continue;
            }
            graphics.fill(sx - 3, sy - 3, sx + 3, sy + 3, TRAIN_BORDER);
            graphics.fill(sx - 2, sy - 2, sx + 2, sy + 2, TRAIN_COLOR);
        }
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, ResourceKey<Level> dimension,
                               double minX, double minZ, int regionBlocks, int resolution) {
        if (mouseX < viewLeft || mouseX > viewRight || mouseY < viewTop || mouseY > viewBottom) {
            return;
        }
        FactionPayloads.TrainView hoveredTrain = null;
        double bestTrainDistance = STATION_HOVER_RADIUS_SQ;
        for (FactionPayloads.TrainView train : ClientWorldMapTracks.trains(dimension)) {
            double sx = imageToScreenX(worldToImage(train.x(), minX, regionBlocks, resolution));
            double sy = imageToScreenY(worldToImage(train.z(), minZ, regionBlocks, resolution));
            double distance = (sx - mouseX) * (sx - mouseX) + (sy - mouseY) * (sy - mouseY);
            if (distance < bestTrainDistance) {
                bestTrainDistance = distance;
                hoveredTrain = train;
            }
        }
        if (hoveredTrain != null) {
            String name = hoveredTrain.name().isBlank() ? "Train" : hoveredTrain.name();
            graphics.renderTooltip(font, Component.literal(name), mouseX, mouseY);
            return;
        }
        FactionPayloads.StationView hoveredStation = null;
        double bestDistance = STATION_HOVER_RADIUS_SQ;
        for (FactionPayloads.StationView station : ClientWorldMapTracks.stations(dimension)) {
            double sx = imageToScreenX(worldToImage(station.x(), minX, regionBlocks, resolution));
            double sy = imageToScreenY(worldToImage(station.z(), minZ, regionBlocks, resolution));
            double distance = (sx - mouseX) * (sx - mouseX) + (sy - mouseY) * (sy - mouseY);
            if (distance < bestDistance) {
                bestDistance = distance;
                hoveredStation = station;
            }
        }
        if (hoveredStation != null) {
            String name = hoveredStation.name().isBlank() ? "Station" : hoveredStation.name();
            graphics.renderTooltip(font, Component.literal(name), mouseX, mouseY);
            return;
        }
        double imageX = centerImageX + (mouseX - midX()) / scale();
        double imageZ = centerImageZ + (mouseY - midY()) / scale();
        double worldX = minX + imageX / resolution * regionBlocks;
        double worldZ = minZ + imageZ / resolution * regionBlocks;
        int chunkX = (int) Math.floor(worldX / 16.0);
        int chunkZ = (int) Math.floor(worldZ / 16.0);
        ClientClaimStore.ClaimInfo claim = ClientClaimStore.get(dimension, chunkX, chunkZ);
        if (claim != null && !claim.name().isBlank()) {
            graphics.renderTooltip(font, Component.literal(claim.name()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inViewport(mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            double scale = scale();
            centerImageX -= dragX / scale;
            centerImageZ -= dragY / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inViewport(mouseX, mouseY)) {
            double factor = scrollY > 0 ? 1.15 : 1.0 / 1.15;
            double newZoom = Math.clamp(zoom * factor, 0.2, 10.0);
            if (newZoom != zoom) {
                double scaleBefore = scale();
                double imageX = centerImageX + (mouseX - midX()) / scaleBefore;
                double imageZ = centerImageZ + (mouseY - midY()) / scaleBefore;
                zoom = newZoom;
                double scaleAfter = scale();
                centerImageX = imageX - (mouseX - midX()) / scaleAfter;
                centerImageZ = imageZ - (mouseY - midY()) / scaleAfter;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean inViewport(double mouseX, double mouseY) {
        return mouseX >= viewLeft && mouseX <= viewRight && mouseY >= viewTop && mouseY <= viewBottom;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
