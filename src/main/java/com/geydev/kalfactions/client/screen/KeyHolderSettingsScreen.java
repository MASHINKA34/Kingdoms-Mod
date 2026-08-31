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
    private KingdomsButton unitButton;
    private KingdomsButton consumeKeyButton;
    private EditBox pulseAmountBox;
    private PulseTimeUnit timeUnit;
    private Component validationMessage = Component.empty();

    private KeyHolderSettingsScreen(KeyHolderPayloads.S2COpenSettings data) {
        super(Component.translatable("screen.kingdoms.key_holder.title"));
        this.data = data;
        this.mode = KeyHolderMode.fromSerializedName(data.mode());
        this.consumeKey = data.consumeKey();
        this.timeUnit = PulseTimeUnit.bestFor(KeyHolderBlockEntity.clampPulseTicks(data.pulseTicks()));
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

        pulseAmountBox = addRenderableWidget(new EditBox(
                font,
                panelLeft + 20,
                panelTop + 91,
                130,
                20,
                Component.translatable("screen.kingdoms.key_holder.pulse_duration")
        ));
        pulseAmountBox.setMaxLength(5);
        pulseAmountBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        pulseAmountBox.setValue(Integer.toString(
                timeUnit.fromTicks(KeyHolderBlockEntity.clampPulseTicks(data.pulseTicks()))
        ));
        pulseAmountBox.setResponder(value -> validationMessage = Component.empty());

        unitButton = addRenderableWidget(KingdomsButton.create(
                unitButtonMessage(),
                button -> cycleTimeUnit(),
                panelLeft + 160,
                panelTop + 91,
                80,
                20
        ));

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

    private void cycleTimeUnit() {
        int currentTicks = enteredTicksOr(timeUnit.ticksPerUnit);
        timeUnit = timeUnit.next();
        pulseAmountBox.setValue(Integer.toString(timeUnit.fromTicksRoundedUp(currentTicks)));
        unitButton.setMessage(unitButtonMessage());
        validationMessage = Component.empty();
    }

    private Component unitButtonMessage() {
        return Component.translatable(timeUnit.translationKey);
    }

    private int enteredTicksOr(int fallback) {
        try {
            int amount = Integer.parseInt(pulseAmountBox.getValue());
            if (amount > 0 && amount <= timeUnit.maxAmount()) {
                return amount * timeUnit.ticksPerUnit;
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
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
        int amount;
        try {
            amount = Integer.parseInt(pulseAmountBox.getValue());
        } catch (NumberFormatException exception) {
            showInvalidDuration();
            return;
        }
        if (amount < 1 || amount > timeUnit.maxAmount()) {
            showInvalidDuration();
            return;
        }
        int pulseTicks = amount * timeUnit.ticksPerUnit;
        if (!KeyHolderBlockEntity.isValidPulseTicks(pulseTicks)) {
            showInvalidDuration();
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

    private void showInvalidDuration() {
        validationMessage = Component.translatable(
                "screen.kingdoms.key_holder.invalid_duration",
                timeUnit.maxAmount(),
                Component.translatable(timeUnit.translationKey)
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
                Component.translatable("screen.kingdoms.key_holder.pulse_duration"),
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

    private enum PulseTimeUnit {
        SECONDS(20, "screen.kingdoms.key_holder.unit.seconds"),
        MINUTES(20 * 60, "screen.kingdoms.key_holder.unit.minutes"),
        HOURS(20 * 60 * 60, "screen.kingdoms.key_holder.unit.hours");

        private final int ticksPerUnit;
        private final String translationKey;

        PulseTimeUnit(int ticksPerUnit, String translationKey) {
            this.ticksPerUnit = ticksPerUnit;
            this.translationKey = translationKey;
        }

        private static PulseTimeUnit bestFor(int ticks) {
            if (ticks >= HOURS.ticksPerUnit && ticks % HOURS.ticksPerUnit == 0) {
                return HOURS;
            }
            if (ticks >= MINUTES.ticksPerUnit && ticks % MINUTES.ticksPerUnit == 0) {
                return MINUTES;
            }
            return SECONDS;
        }

        private PulseTimeUnit next() {
            PulseTimeUnit[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private int fromTicks(int ticks) {
            return Math.max(1, ticks / ticksPerUnit);
        }

        private int fromTicksRoundedUp(int ticks) {
            return Math.max(1, (ticks + ticksPerUnit - 1) / ticksPerUnit);
        }

        private int maxAmount() {
            return KeyHolderBlockEntity.MAX_PULSE_TICKS / ticksPerUnit;
        }
    }
}
