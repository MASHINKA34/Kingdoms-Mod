package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.dungeon.DungeonLoot;
import com.geydev.kalfactions.dungeon.DungeonPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonChestScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 224;
    private static final int GOLD = 0xFFF3D58B;
    private static final int TEXT = 0xFFE8DFCB;
    private static final int MUTED = 0xFF9A8F7A;
    private static final long STATUS_DURATION_MILLIS = 3600L;

    private final BlockPos pos;
    private DungeonPayloads.S2CDungeonChestState state;
    private int mode;
    private EditBox lootTableBox;
    private EditBox cooldownBox;
    private KingdomsButton templateButton;
    private KingdomsButton tableButton;
    private String statusMessage = "";
    private boolean statusSuccessful;
    private long statusShownAt;
    private int panelLeft;
    private int panelTop;

    public DungeonChestScreen(DungeonPayloads.S2CDungeonChestState payload) {
        super(Component.translatable("screen.kingdoms.dungeon_chest.title"));
        this.pos = payload.pos();
        this.state = payload;
        this.mode = payload.mode();
    }

    public BlockPos pos() {
        return pos;
    }

    public void acceptState(DungeonPayloads.S2CDungeonChestState payload) {
        state = payload;
        mode = payload.mode();
        if (lootTableBox != null && !lootTableBox.isFocused()) {
            lootTableBox.setValue(payload.lootTable());
        }
        if (cooldownBox != null && !cooldownBox.isFocused()) {
            cooldownBox.setValue(payload.cooldownHours() < 0 ? "" : String.valueOf(payload.cooldownHours()));
        }
        String message = payload.message().getString();
        if (!message.isBlank()) {
            statusMessage = message;
            statusSuccessful = payload.successful();
            statusShownAt = System.currentTimeMillis();
        }
        updateModeButtons();
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        templateButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.mode_template"),
                button -> {
                    mode = DungeonChestBlockEntity.MODE_TEMPLATE;
                    updateModeButtons();
                },
                panelLeft + 12,
                panelTop + 30,
                (PANEL_WIDTH - 30) / 2,
                20
        ));
        tableButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.mode_table"),
                button -> {
                    mode = DungeonChestBlockEntity.MODE_LOOT_TABLE;
                    updateModeButtons();
                },
                panelLeft + 18 + (PANEL_WIDTH - 30) / 2,
                panelTop + 30,
                (PANEL_WIDTH - 30) / 2,
                20
        ));

        String previousTable = lootTableBox == null ? state.lootTable() : lootTableBox.getValue();
        lootTableBox = new EditBox(
                font,
                panelLeft + 12,
                panelTop + 68,
                PANEL_WIDTH - 24,
                18,
                Component.translatable("screen.kingdoms.dungeon_chest.table_hint")
        );
        lootTableBox.setMaxLength(DungeonPayloads.C2SDungeonChestAction.MAX_TABLE_LENGTH);
        lootTableBox.setHint(Component.translatable("screen.kingdoms.dungeon_chest.table_hint"));
        lootTableBox.setValue(previousTable);
        addRenderableWidget(lootTableBox);

        String previousCooldown = cooldownBox == null
                ? (state.cooldownHours() < 0 ? "" : String.valueOf(state.cooldownHours()))
                : cooldownBox.getValue();
        cooldownBox = new EditBox(
                font,
                panelLeft + 12,
                panelTop + 104,
                60,
                18,
                Component.translatable("screen.kingdoms.dungeon_chest.cooldown_hint")
        );
        cooldownBox.setFilter(text -> text.matches("\\d{0,4}"));
        cooldownBox.setMaxLength(4);
        cooldownBox.setValue(previousCooldown);
        addRenderableWidget(cooldownBox);

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.save"),
                button -> send(DungeonPayloads.C2SDungeonChestAction.ACTION_APPLY),
                panelLeft + 12,
                panelTop + 132,
                PANEL_WIDTH - 24,
                20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.save_template"),
                button -> send(DungeonPayloads.C2SDungeonChestAction.ACTION_SAVE_TEMPLATE),
                panelLeft + 12,
                panelTop + 156,
                PANEL_WIDTH - 24,
                20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.dungeon_chest.refresh"),
                button -> send(DungeonPayloads.C2SDungeonChestAction.ACTION_REFRESH),
                panelLeft + 12,
                panelTop + 180,
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
        updateModeButtons();
    }

    private void updateModeButtons() {
        if (templateButton == null || tableButton == null) {
            return;
        }
        templateButton.active = mode != DungeonChestBlockEntity.MODE_TEMPLATE;
        tableButton.active = mode != DungeonChestBlockEntity.MODE_LOOT_TABLE;
        if (lootTableBox != null) {
            lootTableBox.setEditable(mode == DungeonChestBlockEntity.MODE_LOOT_TABLE);
        }
    }

    private void send(int action) {
        int cooldown = cooldownBox.getValue().isBlank()
                ? -1
                : Math.clamp(Integer.parseInt(cooldownBox.getValue()), 0, DungeonChestBlockEntity.MAX_COOLDOWN_HOURS);
        PacketDistributor.sendToServer(new DungeonPayloads.C2SDungeonChestAction(
                pos,
                action,
                mode,
                lootTableBox.getValue(),
                cooldown
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 12, GOLD, true);
        graphics.drawString(font,
                Component.translatable("screen.kingdoms.dungeon_chest.table_label"),
                panelLeft + 12, panelTop + 56, MUTED, true);
        graphics.drawString(font,
                Component.translatable(
                        "screen.kingdoms.dungeon_chest.cooldown_label",
                        state.effectiveCooldownHours()
                ),
                panelLeft + 80, panelTop + 109, MUTED, true);
        graphics.drawString(font,
                Component.translatable("screen.kingdoms.dungeon_chest.template_size", state.templateCount()),
                panelLeft + 12, panelTop + 92, TEXT, true);
        graphics.drawString(font,
                state.configured()
                        ? Component.translatable(
                                "screen.kingdoms.dungeon_chest.next_refill",
                                DungeonLoot.formatRemaining(state.remainingMillis()))
                        : Component.translatable("screen.kingdoms.dungeon_chest.not_configured"),
                panelLeft + 12, panelTop + PANEL_HEIGHT - 38, state.configured() ? TEXT : 0xFFE29388, true);
        renderStatus(graphics);
    }

    private void renderStatus(GuiGraphics graphics) {
        if (statusMessage.isBlank() || System.currentTimeMillis() - statusShownAt > STATUS_DURATION_MILLIS) {
            return;
        }
        graphics.drawString(
                font,
                font.plainSubstrByWidth(statusMessage, PANEL_WIDTH - 100),
                panelLeft + 12,
                panelTop + PANEL_HEIGHT - 22,
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
