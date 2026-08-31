package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.block.KeyHolderBlockEntity;
import com.geydev.kalfactions.block.KeyHolderMode;
import com.geydev.kalfactions.keyholder.KeyHolderPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KeyHolderSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 190;

    private final KeyHolderPayloads.S2COpenSettings data;
    private KeyHolderMode mode;
    private boolean consumeKey;
    private int panelLeft;
    private int panelTop;
    private KingdomsButton modeButton;
    private KingdomsButton consumeKeyButton;
    private EditBox pulseTicksBox;
    private Component validationMessage = Component.empty();

    private KeyHolderSettingsScreen(KeyHolderPayloads.S2COpenSettings data) {
        super(Component.translatable("screen.kingdoms.key_holder.title"));
        this.data = data;
        this.mode = KeyHolderMode.fromSerializedName(data.mode());
        this.consumeKey = data.consumeKey();
    }

    public static void handleOpen(KeyHolderPayloads.S2COpenSettings payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new KeyHolderSettingsScreen(payload)));
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        modeButton = addRenderableWidget(KingdomsButton.create(
                modeButtonMessage(),
                button -> cycleMode(),
                panelLeft + 20,
                panelTop + 45,
                PANEL_WIDTH - 40,
                20
        ));

        pulseTicksBox = addRenderableWidget(new EditBox(
                font,
                panelLeft + 20,
                panelTop + 91,
                PANEL_WIDTH - 40,
                20,
                Component.translatable("screen.kingdoms.key_holder.pulse_ticks")
        ));
        pulseTicksBox.setMaxLength(4);
        pulseTicksBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        pulseTicksBox.setValue(Integer.toString(KeyHolderBlockEntity.clampPulseTicks(data.pulseTicks())));

        consumeKeyButton = addRenderableWidget(KingdomsButton.create(
                consumeKeyButtonMessage(),
                button -> toggleConsumeKey(),
                panelLeft + 20,
                panelTop + 121,
                PANEL_WIDTH - 40,
                20
        ));

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.key_holder.save"),
                button -> save(),
                panelLeft + 20,
                panelTop + 158,
                105,
                20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.cancel"),
                button -> onClose(),
                panelLeft + 135,
                panelTop + 158,
                105,
                20
        ));
    }

    private void cycleMode() {
        mode = mode.next();
        modeButton.setMessage(modeButtonMessage());
    }

    private Component modeButtonMessage() {
        return Component.translatable(
                "screen.kingdoms.key_holder.mode",
                Component.translatable(mode.displayNameKey())
        );
    }

    private void toggleConsumeKey() {
        consumeKey = !consumeKey;
        consumeKeyButton.setMessage(consumeKeyButtonMessage());
    }

    private Component consumeKeyButtonMessage() {
        return Component.translatable(
                "screen.kingdoms.key_holder.consume_key",
                Component.translatable(consumeKey
                        ? "screen.kingdoms.key_holder.consume_key.on"
                        : "screen.kingdoms.key_holder.consume_key.off")
        );
    }

    private void save() {
        int pulseTicks;
        try {
            pulseTicks = Integer.parseInt(pulseTicksBox.getValue());
        } catch (NumberFormatException exception) {
            showInvalidTicks();
            return;
        }
        if (!KeyHolderBlockEntity.isValidPulseTicks(pulseTicks)) {
            showInvalidTicks();
            return;
        }
        PacketDistributor.sendToServer(new KeyHolderPayloads.C2SUpdateSettings(
                data.pos(),
                mode.getSerializedName(),
                pulseTicks,
                consumeKey
        ));
        onClose();
    }

    private void showInvalidTicks() {
        validationMessage = Component.translatable(
                "screen.kingdoms.key_holder.invalid_ticks",
                KeyHolderBlockEntity.MIN_PULSE_TICKS,
                KeyHolderBlockEntity.MAX_PULSE_TICKS
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 11, 0xFFF3D58B);
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.key_holder.operator_hint"),
                panelLeft + 20,
                panelTop + 28,
                0xFFB8B1A2,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.key_holder.pulse_ticks"),
                panelLeft + 20,
                panelTop + 78,
                0xFFE8DCC0,
                false
        );
        if (!validationMessage.getString().isEmpty()) {
            graphics.drawString(font, validationMessage, panelLeft + 20, panelTop + 145, 0xFFE07A6B, false);
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
