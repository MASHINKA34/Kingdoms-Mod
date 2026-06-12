package com.geydev.kalfactions.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class EmblemEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 312;
    private static final int PANEL_HEIGHT = 252;
    private static final int GRID = 16;
    private static final int PIXEL = 8;
    private static final int CANVAS = GRID * PIXEL;
    private static final int UPLOAD_SIZE = 32;
    private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;
    private static final int[] PALETTE = {
            0xFF1A1A1A, 0xFF4C4C4C, 0xFF9A9A9A, 0xFFFFFFFF,
            0xFFB02E26, 0xFFF9801D, 0xFFFED83D, 0xFF80C71F,
            0xFF5E7C16, 0xFF169C9C, 0xFF3AB3DA, 0xFF3C44AA,
            0xFF8932B8, 0xFFC74EBD, 0xFF835432, 0xFFF38BAA
    };

    private enum Tool {
        BRUSH,
        ERASER,
        FILL
    }

    private final Screen parent;
    private final BiConsumer<int[], String> onAccept;
    private final int[] pixels = new int[GRID * GRID];
    private int[] uploadedPixels;
    private String urlValue;

    private Tool tool = Tool.BRUSH;
    private int selectedColor = PALETTE[4];
    private boolean painting;
    private String noticeText = "";
    private long noticeShownAt;

    private int panelLeft;
    private int panelTop;
    private int canvasLeft;
    private int canvasTop;
    private int paletteLeft;
    private int paletteTop;
    private EditBox urlBox;
    private KingdomsButton brushButton;
    private KingdomsButton eraserButton;
    private KingdomsButton fillButton;

    public EmblemEditorScreen(Screen parent, int[] initialPixels, String initialUrl, BiConsumer<int[], String> onAccept) {
        super(Component.translatable("screen.kingdoms.emblem_editor"));
        this.parent = parent;
        this.onAccept = onAccept;
        this.urlValue = initialUrl == null ? "" : initialUrl;
        if (initialPixels != null && initialPixels.length == pixels.length) {
            System.arraycopy(initialPixels, 0, pixels, 0, pixels.length);
        } else if (initialPixels != null && initialPixels.length == UPLOAD_SIZE * UPLOAD_SIZE) {
            uploadedPixels = initialPixels.clone();
        }
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        canvasLeft = panelLeft + 12;
        canvasTop = panelTop + 26;
        paletteLeft = panelLeft + 152;
        paletteTop = panelTop + 26;

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.emblem.color"),
                button -> minecraft.setScreen(new ColorPickerScreen(
                        this,
                        selectedColor & 0xFFFFFF,
                        picked -> selectedColor = 0xFF000000 | picked
                )),
                paletteLeft, paletteTop + 100, 70, 20
        ));

        int toolsLeft = panelLeft + 230;
        int toolsWidth = panelLeft + PANEL_WIDTH - 12 - toolsLeft;
        brushButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.emblem.brush"),
                button -> selectTool(Tool.BRUSH),
                toolsLeft, paletteTop, toolsWidth, 20
        ));
        eraserButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.emblem.eraser"),
                button -> selectTool(Tool.ERASER),
                toolsLeft, paletteTop + 24, toolsWidth, 20
        ));
        fillButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.emblem.fill"),
                button -> selectTool(Tool.FILL),
                toolsLeft, paletteTop + 48, toolsWidth, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.emblem.clear"),
                button -> {
                    java.util.Arrays.fill(pixels, 0);
                    uploadedPixels = null;
                },
                toolsLeft, paletteTop + 72, toolsWidth, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.emblem.upload"),
                button -> openFileDialog(),
                toolsLeft, paletteTop + 96, toolsWidth, 20
        ));
        updateToolButtons();

        urlBox = new EditBox(
                font,
                panelLeft + 12,
                panelTop + 173,
                PANEL_WIDTH - 24,
                16,
                Component.translatable("screen.kingdoms.emblem.url_hint")
        );
        urlBox.setMaxLength(256);
        urlBox.setValue(urlValue);
        urlBox.setResponder(value -> urlValue = value);
        addRenderableWidget(urlBox);

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.apply"),
                button -> {
                    onAccept.accept(resultPixels(), urlValue.strip());
                    minecraft.setScreen(parent);
                },
                panelLeft + PANEL_WIDTH - 12 - 180, panelTop + PANEL_HEIGHT - 28, 88, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.cancel"),
                button -> onClose(),
                panelLeft + PANEL_WIDTH - 12 - 88, panelTop + PANEL_HEIGHT - 28, 88, 20
        ));
    }

    private int[] resultPixels() {
        if (uploadedPixels != null) {
            return uploadedPixels.clone();
        }
        return hasAnyPixel() ? pixels.clone() : null;
    }

    private void selectTool(Tool newTool) {
        tool = newTool;
        updateToolButtons();
    }

    private void updateToolButtons() {
        brushButton.active = tool != Tool.BRUSH;
        eraserButton.active = tool != Tool.ERASER;
        fillButton.active = tool != Tool.FILL;
    }

    private boolean hasAnyPixel() {
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }

    private void showNotice(String translationKey) {
        noticeText = Component.translatable(translationKey).getString();
        noticeShownAt = System.currentTimeMillis();
    }

    private void openFileDialog() {
        CompletableFuture.runAsync(() -> {
            String path;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                path = TinyFileDialogs.tinyfd_openFileDialog("PNG", "", filters, "PNG", false);
            }
            if (path == null) {
                return;
            }
            try {
                Path file = Path.of(path);
                if (Files.size(file) > MAX_FILE_BYTES) {
                    throw new IOException("File too large");
                }
                byte[] data = Files.readAllBytes(file);
                int[] sampled;
                try (NativeImage image = NativeImage.read(new ByteArrayInputStream(data))) {
                    sampled = sample(image);
                }
                minecraft.execute(() -> {
                    uploadedPixels = sampled;
                    noticeText = "";
                });
            } catch (Exception exception) {
                minecraft.execute(() -> showNotice("screen.kingdoms.emblem.upload_failed"));
            }
        }, Util.ioPool());
    }

    private static int[] sample(NativeImage image) {
        int[] out = new int[UPLOAD_SIZE * UPLOAD_SIZE];
        for (int y = 0; y < UPLOAD_SIZE; y++) {
            for (int x = 0; x < UPLOAD_SIZE; x++) {
                int sourceX = Math.min(image.getWidth() - 1, x * image.getWidth() / UPLOAD_SIZE);
                int sourceY = Math.min(image.getHeight() - 1, y * image.getHeight() / UPLOAD_SIZE);
                int abgr = image.getPixelRGBA(sourceX, sourceY);
                int alpha = abgr >>> 24;
                int blue = (abgr >> 16) & 0xFF;
                int green = (abgr >> 8) & 0xFF;
                int red = abgr & 0xFF;
                out[y * UPLOAD_SIZE + x] = alpha << 24 | red << 16 | green << 8 | blue;
            }
        }
        return out;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 10, 0xFFF3D58B, true);

        graphics.fill(canvasLeft - 1, canvasTop - 1, canvasLeft + CANVAS + 1, canvasTop + CANVAS + 1, 0xFF1A140C);
        if (uploadedPixels != null) {
            int cell = CANVAS / UPLOAD_SIZE;
            for (int y = 0; y < UPLOAD_SIZE; y++) {
                for (int x = 0; x < UPLOAD_SIZE; x++) {
                    int px = canvasLeft + x * cell;
                    int py = canvasTop + y * cell;
                    int checker = ((x + y) & 1) == 0 ? 0xFF3A3D47 : 0xFF31343D;
                    graphics.fill(px, py, px + cell, py + cell, checker);
                    int color = uploadedPixels[y * UPLOAD_SIZE + x];
                    if ((color >>> 24) > 0) {
                        graphics.fill(px, py, px + cell, py + cell, color);
                    }
                }
            }
        } else {
            for (int y = 0; y < GRID; y++) {
                for (int x = 0; x < GRID; x++) {
                    int px = canvasLeft + x * PIXEL;
                    int py = canvasTop + y * PIXEL;
                    int checker = ((x + y) & 1) == 0 ? 0xFF3A3D47 : 0xFF31343D;
                    graphics.fill(px, py, px + PIXEL, py + PIXEL, checker);
                    int color = pixels[y * GRID + x];
                    if (color != 0) {
                        graphics.fill(px, py, px + PIXEL, py + PIXEL, color);
                    }
                }
            }
            if (insideCanvas(mouseX, mouseY)) {
                int hx = canvasLeft + ((mouseX - canvasLeft) / PIXEL) * PIXEL;
                int hy = canvasTop + ((mouseY - canvasTop) / PIXEL) * PIXEL;
                graphics.fill(hx, hy, hx + PIXEL, hy + PIXEL, 0x60FFFFFF);
            }
        }

        for (int index = 0; index < PALETTE.length; index++) {
            int sx = paletteLeft + (index % 4) * 18;
            int sy = paletteTop + (index / 4) * 18;
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF1A140C);
            graphics.fill(sx + 1, sy + 1, sx + 15, sy + 15, PALETTE[index]);
            if (PALETTE[index] == selectedColor) {
                graphics.fill(sx, sy, sx + 16, sy + 1, 0xFFF3D58B);
                graphics.fill(sx, sy + 15, sx + 16, sy + 16, 0xFFF3D58B);
                graphics.fill(sx, sy, sx + 1, sy + 16, 0xFFF3D58B);
                graphics.fill(sx + 15, sy, sx + 16, sy + 16, 0xFFF3D58B);
            }
        }

        int previewTop = paletteTop + 78;
        graphics.fill(paletteLeft, previewTop, paletteLeft + 70, previewTop + 18, 0xFF1A140C);
        graphics.fill(paletteLeft + 1, previewTop + 1, paletteLeft + 69, previewTop + 17, selectedColor);

        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.emblem.url_hint"),
                panelLeft + 12,
                panelTop + 162,
                0xFF9A8F7A,
                true
        );

        if (!noticeText.isEmpty() && System.currentTimeMillis() - noticeShownAt < 3000L) {
            String clipped = font.plainSubstrByWidth(noticeText, PANEL_WIDTH - 24);
            graphics.drawString(font, clipped, panelLeft + (PANEL_WIDTH - font.width(clipped)) / 2,
                    panelTop + PANEL_HEIGHT - 44, 0xFFE89090, true);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, 0xFFC9A24C);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF2B2E38);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (insideCanvas(mouseX, mouseY)) {
                if (uploadedPixels != null) {
                    uploadedPixels = null;
                }
                if (tool == Tool.FILL) {
                    floodFill((int) (mouseX - canvasLeft) / PIXEL, (int) (mouseY - canvasTop) / PIXEL);
                } else {
                    painting = true;
                    paintAt(mouseX, mouseY);
                }
                return true;
            }
            int paletteIndex = paletteIndexAt(mouseX, mouseY);
            if (paletteIndex >= 0) {
                selectedColor = PALETTE[paletteIndex];
                if (tool == Tool.ERASER) {
                    selectTool(Tool.BRUSH);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (painting && insideCanvas(mouseX, mouseY)) {
            paintAt(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        painting = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void paintAt(double mouseX, double mouseY) {
        int x = (int) (mouseX - canvasLeft) / PIXEL;
        int y = (int) (mouseY - canvasTop) / PIXEL;
        if (x < 0 || x >= GRID || y < 0 || y >= GRID) {
            return;
        }
        pixels[y * GRID + x] = tool == Tool.ERASER ? 0 : selectedColor;
    }

    private void floodFill(int startX, int startY) {
        if (startX < 0 || startX >= GRID || startY < 0 || startY >= GRID) {
            return;
        }
        int target = pixels[startY * GRID + startX];
        if (target == selectedColor) {
            return;
        }
        Deque<int[]> pending = new ArrayDeque<>();
        pending.add(new int[] {startX, startY});
        while (!pending.isEmpty()) {
            int[] cell = pending.removeFirst();
            int x = cell[0];
            int y = cell[1];
            if (x < 0 || x >= GRID || y < 0 || y >= GRID || pixels[y * GRID + x] != target) {
                continue;
            }
            pixels[y * GRID + x] = selectedColor;
            pending.add(new int[] {x + 1, y});
            pending.add(new int[] {x - 1, y});
            pending.add(new int[] {x, y + 1});
            pending.add(new int[] {x, y - 1});
        }
    }

    private boolean insideCanvas(double mouseX, double mouseY) {
        return mouseX >= canvasLeft && mouseX < canvasLeft + CANVAS
                && mouseY >= canvasTop && mouseY < canvasTop + CANVAS;
    }

    private int paletteIndexAt(double mouseX, double mouseY) {
        for (int index = 0; index < PALETTE.length; index++) {
            int sx = paletteLeft + (index % 4) * 18;
            int sy = paletteTop + (index / 4) * 18;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
