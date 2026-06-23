package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.war.War;
import com.geydev.kalfactions.war.WarType;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Casus-belli modal shown after the attacker picks a target. Lets the leader choose a war type and
 * type a free-text reason; both are flavour, recorded with the war for the history archive.
 */
public final class DeclareWarScreen extends Screen {
    private static final int PANEL_WIDTH = 264;
    private static final int PANEL_HEIGHT = 226;

    private final Screen parent;
    private final BlockPos tablePos;
    private final String targetFactionName;
    private final WarType[] types = WarType.values();
    private final KingdomsButton[] typeButtons = new KingdomsButton[WarType.values().length];

    private WarType selectedType = WarType.DEFAULT;
    private EditBox reasonBox;
    private int panelLeft;
    private int panelTop;

    public DeclareWarScreen(Screen parent, BlockPos tablePos, String targetFactionName) {
        super(Component.translatable("screen.kingdoms.declare_war_title", targetFactionName));
        this.parent = parent;
        this.tablePos = tablePos;
        this.targetFactionName = targetFactionName;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        int colWidth = (PANEL_WIDTH - 24) / 2;
        int leftCol = panelLeft + 12;
        int rightCol = panelLeft + PANEL_WIDTH - 12 - colWidth;
        int typesTop = panelTop + 30;
        for (int index = 0; index < types.length; index++) {
            WarType type = types[index];
            int column = index % 2;
            int row = index / 2;
            int x = column == 0 ? leftCol : rightCol;
            int y = typesTop + row * 24;
            KingdomsButton button = addRenderableWidget(KingdomsButton.create(
                    Component.translatable(type.displayKey()),
                    ignored -> selectType(type),
                    x, y, colWidth, 20
            ));
            typeButtons[index] = button;
        }
        refreshTypeButtons();

        int reasonY = typesTop + ((types.length + 1) / 2) * 24 + 44;
        reasonBox = new EditBox(
                font,
                panelLeft + 12,
                reasonY,
                PANEL_WIDTH - 24,
                20,
                Component.translatable("screen.kingdoms.declare_war_reason")
        );
        reasonBox.setMaxLength(War.MAX_REASON_LENGTH);
        reasonBox.setHint(Component.translatable("screen.kingdoms.declare_war_reason_hint"));
        addRenderableWidget(reasonBox);

        int buttonsY = panelTop + PANEL_HEIGHT - 28;
        int half = (PANEL_WIDTH - 30) / 2;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.cancel"),
                button -> onClose(),
                panelLeft + 12, buttonsY, half, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.declare_war_confirm"),
                button -> confirm(),
                panelLeft + PANEL_WIDTH - 12 - half, buttonsY, half, 20
        ));
    }

    private void selectType(WarType type) {
        selectedType = type;
        refreshTypeButtons();
    }

    private void refreshTypeButtons() {
        for (int index = 0; index < typeButtons.length; index++) {
            // The selected type's button is greyed (inactive) to show the current choice.
            typeButtons[index].active = types[index] != selectedType;
        }
    }

    private void confirm() {
        PacketDistributor.sendToServer(new FactionPayloads.C2SDeclareWar(
                tablePos,
                targetFactionName,
                selectedType.id(),
                reasonBox.getValue()
        ));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 9, 0xFFF3D58B, true);

        Component description = Component.translatable(selectedType.descriptionKey());
        int reasonLabelY = reasonBox.getY() - 24;
        List<FormattedCharSequence> lines = font.split(description, PANEL_WIDTH - 24);
        for (int index = 0; index < Math.min(2, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), panelLeft + 12, reasonLabelY - 12 + index * 10, 0xFF9A8F7A, true);
        }
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.declare_war_reason"),
                panelLeft + 12,
                reasonBox.getY() - 11,
                0xFFC9A24C,
                true
        );
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, 0xFFC9A24C);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF2B2E38);
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
