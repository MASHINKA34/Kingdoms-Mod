package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.ClientDungeonSelection;
import com.geydev.kalfactions.dungeon.DungeonManager;
import com.geydev.kalfactions.dungeon.DungeonPayloads;
import com.geydev.kalfactions.integration.xaero.XaeroMaps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 196;
    private static final int GOLD = 0xFFF3D58B;
    private static final int TEXT = 0xFFE8DFCB;
    private static final int MUTED = 0xFF9A8F7A;
    private static final long STATUS_DURATION_MILLIS = 3600L;

    private final int dungeonId;
    private DungeonPayloads.S2COpenDungeon state;
    private EditBox nameBox;
    private KingdomsButton saveButton;
    private String statusMessage = "";
    private boolean statusSuccessful;
    private long statusShownAt;
    private int panelLeft;
    private int panelTop;

    public DungeonScreen(DungeonPayloads.S2COpenDungeon payload) {
        super(Component.translatable("screen.kingdoms.dungeon.title"));
        this.dungeonId = payload.dungeonId();
        this.state = payload;
    }

    public int dungeonId() {
        return dungeonId;
    }

    public void acceptState(DungeonPayloads.S2COpenDungeon payload) {
        state = payload;
        if (nameBox != null && !nameBox.isFocused()) {
            nameBox.setValue(payload.name());
        }
        String message = payload.message().getString();
        if (!message.isBlank()) {
            statusMessage = message;
            statusSuccessful = payload.successful();
            statusShownAt = System.currentTimeMillis();
        }
        updateSaveButton();
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        String previous = nameBox == null ? state.name() : nameBox.getValue();
        nameBox = new EditBox(
                font,
                panelLeft + 12,
                panelTop + 34,
                PANEL_WIDTH - 24,
                18,
                Component.translatable("screen.kingdoms.dungeon.name_hint")
        );
        nameBox.setMaxLength(DungeonManager.MAX_NAME_LENGTH);
        nameBox.setHint(Component.translatable("screen.kingdoms.dungeon.name_hint"));
        nameBox.setValue(previous);
        nameBox.setResponder(text -> updateSaveButton());
        addRenderableWidget(nameBox);

        saveButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon.save_name"),
                button -> submitName(),
                panelLeft + 12,
                panelTop + 56,
                PANEL_WIDTH - 24,
                20
        ));

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon.select_area"),
                button -> openMap(),
                panelLeft + 12,
                panelTop + 122,
                PANEL_WIDTH - 24,
                20
        ));

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon.delete"),
                button -> minecraft.setScreen(new KingdomsConfirmScreen(
                        this,
                        Component.translatable("screen.kingdoms.dungeon.delete"),
                        Component.translatable("screen.kingdoms.dungeon.delete_confirm", state.name()),
                        () -> {
                            PacketDistributor.sendToServer(
                                    new DungeonPayloads.C2SRemoveDungeon(dungeonId)
                            );
                            onClose();
                        }
                )),
                panelLeft + 12,
                panelTop + 146,
                PANEL_WIDTH - 24,
                20
        ));

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                panelLeft + PANEL_WIDTH - 78,
                panelTop + PANEL_HEIGHT - 26,
                66,
                20
        ));

        updateSaveButton();
        setInitialFocus(nameBox);
    }

    private void submitName() {
        String name = DungeonManager.normalizeName(nameBox.getValue());
        if (name.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new DungeonPayloads.C2SRenameDungeon(dungeonId, name));
    }

    private void openMap() {
        ClientDungeonSelection.begin(dungeonId, state.name());
        if (!XaeroMaps.openWorldMap()) {
            minecraft.setScreen(new DungeonMapScreen(state));
        }
    }

    private void updateSaveButton() {
        if (saveButton != null) {
            saveButton.active = !DungeonManager.normalizeName(nameBox.getValue()).isEmpty();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 12, GOLD, true);

        BlockPos core = state.corePos();
        graphics.drawString(font,
                Component.translatable("screen.kingdoms.dungeon.chunks", state.chunkCount()),
                panelLeft + 12, panelTop + 84, TEXT, true);
        graphics.drawString(font,
                Component.translatable("screen.kingdoms.dungeon.dimension", state.dimension().toString()),
                panelLeft + 12, panelTop + 96, MUTED, true);
        graphics.drawString(font,
                Component.translatable(
                        "screen.kingdoms.dungeon.core",
                        core.getX(),
                        core.getY(),
                        core.getZ()
                ),
                panelLeft + 12, panelTop + 108, MUTED, true);

        graphics.drawString(font,
                Component.translatable("screen.kingdoms.dungeon.containers", state.containerCount()),
                panelLeft + 12, panelTop + PANEL_HEIGHT - 40, MUTED, true);
        renderStatus(graphics);
    }

    private void renderStatus(GuiGraphics graphics) {
        if (statusMessage.isBlank() || System.currentTimeMillis() - statusShownAt > STATUS_DURATION_MILLIS) {
            return;
        }
        graphics.drawString(
                font,
                font.plainSubstrByWidth(statusMessage, PANEL_WIDTH - 24),
                panelLeft + 12,
                panelTop + PANEL_HEIGHT - 52,
                statusSuccessful ? 0xFF91D69B : 0xFFE29388,
                true
        );
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        KingdomsPanel.draw(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
