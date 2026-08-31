package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.invisibility.InvisibilityPayloads;
import com.geydev.kalfactions.invisibility.TrueInvisibility;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InvisibilityChaliceSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 176;

    private final InvisibilityPayloads.S2COpenChaliceSettings data;
    private EditBox durationBox;
    private int panelLeft;
    private int panelTop;
    private Component validationMessage = Component.empty();

    private InvisibilityChaliceSettingsScreen(InvisibilityPayloads.S2COpenChaliceSettings data) {
        super(Component.translatable("screen.kingdoms.invisibility_chalice.title"));
        this.data = data;
    }

    public static void handleOpen(InvisibilityPayloads.S2COpenChaliceSettings payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new InvisibilityChaliceSettingsScreen(payload)));
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        durationBox = addRenderableWidget(new EditBox(
                font,
                panelLeft + 20,
                panelTop + 92,
                PANEL_WIDTH - 40,
                20,
                Component.translatable("screen.kingdoms.invisibility_chalice.duration")
        ));
        durationBox.setMaxLength(4);
        durationBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        durationBox.setValue(Integer.toString(TrueInvisibility.clampSeconds(data.durationSeconds())));
        durationBox.setResponder(value -> validationMessage = Component.empty());
        setInitialFocus(durationBox);

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.invisibility_chalice.save"),
                button -> save(),
                panelLeft + 20,
                panelTop + 144,
                100,
                20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.cancel"),
                button -> onClose(),
                panelLeft + 130,
                panelTop + 144,
                100,
                20
        ));
    }

    private void save() {
        int seconds;
        try {
            seconds = Integer.parseInt(durationBox.getValue());
        } catch (NumberFormatException exception) {
            showInvalidDuration();
            return;
        }
        if (!TrueInvisibility.isValidSeconds(seconds)) {
            showInvalidDuration();
            return;
        }
        PacketDistributor.sendToServer(
                new InvisibilityPayloads.C2SUpdateChaliceSettings(data.pos(), seconds)
        );
        onClose();
    }

    private void showInvalidDuration() {
        validationMessage = Component.translatable(
                "screen.kingdoms.invisibility_chalice.invalid_duration",
                TrueInvisibility.MIN_SECONDS,
                TrueInvisibility.MAX_SECONDS
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 11, 0xFFF3D58B);
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.invisibility_chalice.operator_hint"),
                panelLeft + 20,
                panelTop + 28,
                0xFFB8B1A2,
                false
        );
        List<FormattedCharSequence> description = font.split(
                Component.translatable("effect.kingdoms.true_invisibility.description"),
                PANEL_WIDTH - 40
        );
        for (int line = 0; line < description.size(); line++) {
            graphics.drawString(
                    font,
                    description.get(line),
                    panelLeft + 20,
                    panelTop + 44 + line * 11,
                    0xFF9FB7C9,
                    false
            );
        }
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.invisibility_chalice.duration"),
                panelLeft + 20,
                panelTop + 79,
                0xFFE8DCC0,
                false
        );
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.kingdoms.invisibility_chalice.range",
                        TrueInvisibility.MIN_SECONDS,
                        TrueInvisibility.MAX_SECONDS
                ),
                panelLeft + 20,
                panelTop + 117,
                0xFFB8B1A2,
                false
        );
        if (!validationMessage.getString().isEmpty()) {
            graphics.drawString(font, validationMessage, panelLeft + 20, panelTop + 130, 0xFFE07A6B, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(
                panelLeft - 1,
                panelTop - 1,
                panelLeft + PANEL_WIDTH + 1,
                panelTop + PANEL_HEIGHT + 1,
                0xFFC9A24C
        );
        graphics.fill(
                panelLeft,
                panelTop,
                panelLeft + PANEL_WIDTH,
                panelTop + PANEL_HEIGHT,
                0xFF2B2E38
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
