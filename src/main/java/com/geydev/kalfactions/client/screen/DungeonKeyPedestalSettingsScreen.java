package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.block.DungeonKeyPedestalActivation;
import com.geydev.kalfactions.block.DungeonKeyPedestalBlockEntity;
import com.geydev.kalfactions.pedestal.DungeonKeyPedestalPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonKeyPedestalSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 170;

    private final DungeonKeyPedestalPayloads.S2COpenSettings data;
    private DungeonKeyPedestalActivation requiredKey;
    private int panelLeft;
    private int panelTop;
    private KingdomsButton keyButton;
    private KingdomsButton unitButton;
    private EditBox secondsBox;
    private SignalTimeUnit timeUnit;
    private Component validationMessage = Component.empty();

    private DungeonKeyPedestalSettingsScreen(DungeonKeyPedestalPayloads.S2COpenSettings data) {
        super(Component.translatable("screen.kingdoms.dungeon_key_pedestal.title"));
        this.data = data;
        this.requiredKey = DungeonKeyPedestalActivation.fromSerializedName(data.requiredKey());
        this.timeUnit = SignalTimeUnit.bestFor(data.signalTicks());
    }

    public static void handleOpen(DungeonKeyPedestalPayloads.S2COpenSettings payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new DungeonKeyPedestalSettingsScreen(payload)));
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        keyButton = addRenderableWidget(KingdomsButton.create(
                keyButtonMessage(),
                button -> cycleRequiredKey(),
                panelLeft + 20,
                panelTop + 45,
                PANEL_WIDTH - 40,
                20
        ));

        secondsBox = addRenderableWidget(new EditBox(
                font,
                panelLeft + 20,
                panelTop + 91,
                130,
                20,
                Component.translatable("screen.kingdoms.dungeon_key_pedestal.signal_duration")
        ));
        secondsBox.setMaxLength(5);
        secondsBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        secondsBox.setValue(Integer.toString(timeUnit.fromTicks(data.signalTicks())));

        unitButton = addRenderableWidget(KingdomsButton.create(
                unitButtonMessage(),
                button -> cycleTimeUnit(),
                panelLeft + 160,
                panelTop + 91,
                80,
                20
        ));

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_key_pedestal.save"),
                button -> save(),
                panelLeft + 20,
                panelTop + 132,
                105,
                20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.cancel"),
                button -> onClose(),
                panelLeft + 135,
                panelTop + 132,
                105,
                20
        ));
    }

    private void cycleRequiredKey() {
        int index = DungeonKeyPedestalActivation.CONFIGURATION_VALUES.indexOf(requiredKey);
        requiredKey = DungeonKeyPedestalActivation.CONFIGURATION_VALUES.get(
                (index + 1) % DungeonKeyPedestalActivation.CONFIGURATION_VALUES.size()
        );
        keyButton.setMessage(keyButtonMessage());
    }

    private Component keyButtonMessage() {
        return Component.translatable(
                "screen.kingdoms.dungeon_key_pedestal.required_key",
                Component.translatable(requiredKey.displayNameKey())
        );
    }

    private void cycleTimeUnit() {
        int currentTicks = enteredTicksOr(timeUnit.ticksPerUnit);
        timeUnit = timeUnit.next();
        secondsBox.setValue(Integer.toString(timeUnit.fromTicksRoundedUp(currentTicks)));
        unitButton.setMessage(unitButtonMessage());
        validationMessage = Component.empty();
    }

    private Component unitButtonMessage() {
        return Component.translatable(timeUnit.translationKey);
    }

    private void save() {
        int amount;
        try {
            amount = Integer.parseInt(secondsBox.getValue());
        } catch (NumberFormatException exception) {
            showInvalidTime();
            return;
        }
        int maxAmount = timeUnit.maxAmount();
        if (amount < 1 || amount > maxAmount) {
            showInvalidTime();
            return;
        }
        PacketDistributor.sendToServer(new DungeonKeyPedestalPayloads.C2SUpdateSettings(
                data.pos(),
                requiredKey.getSerializedName(),
                amount * timeUnit.ticksPerUnit
        ));
        onClose();
    }

    private void showInvalidTime() {
        validationMessage = Component.translatable(
                "screen.kingdoms.dungeon_key_pedestal.invalid_time",
                timeUnit.maxAmount(),
                Component.translatable(timeUnit.translationKey)
        );
    }

    private int enteredTicksOr(int fallback) {
        try {
            int amount = Integer.parseInt(secondsBox.getValue());
            if (amount > 0 && amount <= DungeonKeyPedestalBlockEntity.MAX_SIGNAL_TICKS) {
                long ticks = (long) amount * timeUnit.ticksPerUnit;
                return (int) Math.min(ticks, DungeonKeyPedestalBlockEntity.MAX_SIGNAL_TICKS);
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 11, 0xFFF3D58B);
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.dungeon_key_pedestal.operator_hint"),
                panelLeft + 20,
                panelTop + 28,
                0xFFB8B1A2,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.dungeon_key_pedestal.signal_duration"),
                panelLeft + 20,
                panelTop + 78,
                0xFFE8DCC0,
                false
        );
        if (!validationMessage.getString().isEmpty()) {
            graphics.drawString(font, validationMessage, panelLeft + 20, panelTop + 115, 0xFFE07A6B, false);
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

    private enum SignalTimeUnit {
        SECONDS(20, "screen.kingdoms.dungeon_key_pedestal.unit.seconds"),
        MINUTES(20 * 60, "screen.kingdoms.dungeon_key_pedestal.unit.minutes"),
        HOURS(20 * 60 * 60, "screen.kingdoms.dungeon_key_pedestal.unit.hours");

        private final int ticksPerUnit;
        private final String translationKey;

        SignalTimeUnit(int ticksPerUnit, String translationKey) {
            this.ticksPerUnit = ticksPerUnit;
            this.translationKey = translationKey;
        }

        private static SignalTimeUnit bestFor(int ticks) {
            if (ticks >= HOURS.ticksPerUnit && ticks % HOURS.ticksPerUnit == 0) {
                return HOURS;
            }
            if (ticks >= MINUTES.ticksPerUnit && ticks % MINUTES.ticksPerUnit == 0) {
                return MINUTES;
            }
            return SECONDS;
        }

        private SignalTimeUnit next() {
            SignalTimeUnit[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private int fromTicks(int ticks) {
            return Math.max(1, ticks / ticksPerUnit);
        }

        private int fromTicksRoundedUp(int ticks) {
            return Math.max(1, (ticks + ticksPerUnit - 1) / ticksPerUnit);
        }

        private int maxAmount() {
            return DungeonKeyPedestalBlockEntity.MAX_SIGNAL_TICKS / ticksPerUnit;
        }
    }
}
