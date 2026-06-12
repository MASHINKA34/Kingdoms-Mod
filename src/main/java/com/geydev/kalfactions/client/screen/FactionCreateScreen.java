package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionCreateScreen extends FactionScreen {
    private EditBox nameBox;
    private String nameValue = "";
    private int selectedColor;
    private FactionBonus firstBonus;
    private FactionBonus secondBonus;
    private int[] emblemPixels;
    private String emblemUrl = "";
    private KingdomsButton createButton;

    public FactionCreateScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.faction_create"), snapshot, successful, message);
        selectedColor = snapshot.color();
    }

    @Override
    protected void initFactionWidgets() {
        int controlsLeft = left + 78;
        int controlsWidth = 180;

        nameBox = new EditBox(
                font,
                controlsLeft,
                top + 56,
                controlsWidth,
                20,
                text("screen.kingdoms.faction_name")
        );
        nameBox.setMaxLength(32);
        nameBox.setValue(nameValue);
        addRenderableWidget(nameBox);

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.color"),
                button -> minecraft.setScreen(new ColorPickerScreen(this, selectedColor, picked -> selectedColor = picked)),
                controlsLeft, top + 80, controlsWidth, 20
        ));

        addRenderableWidget(KingdomsButton.create(
                bonusLabel(1, firstBonus),
                button -> openBonusPicker(secondBonus, picked -> {
                    firstBonus = picked;
                    rebuildWidgets();
                }),
                controlsLeft, top + 104, 87, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                bonusLabel(2, secondBonus),
                button -> openBonusPicker(firstBonus, picked -> {
                    secondBonus = picked;
                    rebuildWidgets();
                }),
                controlsLeft + 93, top + 104, 87, 20
        ));

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.emblem"),
                button -> minecraft.setScreen(new EmblemEditorScreen(this, emblemPixels, emblemUrl, (pixels, url) -> {
                    emblemPixels = pixels;
                    emblemUrl = url;
                })),
                controlsLeft, top + 128, controlsWidth, 20
        ));

        createButton = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.create"),
                button -> PacketDistributor.sendToServer(new FactionPayloads.C2SCreateFaction(
                        snapshot.tablePos(),
                        nameBox.getValue(),
                        selectedColor,
                        List.of(firstBonus.name(), secondBonus.name()),
                        boxedEmblem(),
                        emblemUrl
                )),
                controlsLeft, top + 152, controlsWidth, 20
        ));
        nameBox.setResponder(value -> {
            nameValue = value;
            updateCreateButton();
        });
        updateCreateButton();
    }

    private void updateCreateButton() {
        createButton.active = nameValue.trim().length() >= 3 && firstBonus != null && secondBonus != null;
    }

    private List<Integer> boxedEmblem() {
        if (emblemPixels == null || emblemPixels.length != FactionSnapshot.EMBLEM_PIXELS) {
            return List.of();
        }
        List<Integer> boxed = new ArrayList<>(emblemPixels.length);
        for (int pixel : emblemPixels) {
            boxed.add(pixel);
        }
        return boxed;
    }

    private Component bonusLabel(int slot, FactionBonus bonus) {
        Component value = bonus == null
                ? text("screen.kingdoms.bonus_none")
                : Component.translatable(bonus.translationKey());
        return Component.literal(slot + ": ").append(value);
    }

    private void openBonusPicker(FactionBonus excluded, Consumer<FactionBonus> onPick) {
        List<SelectEntryScreen.Entry> entries = new ArrayList<>();
        List<FactionBonus> order = new ArrayList<>();
        for (FactionBonus bonus : FactionBonus.SELECTABLE) {
            if (bonus == excluded) {
                continue;
            }
            order.add(bonus);
            entries.add(new SelectEntryScreen.Entry(
                    I18n.get(bonus.translationKey()),
                    null,
                    Component.translatable(bonus.descriptionKey()),
                    bonusColor(bonus),
                    true
            ));
        }
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.bonus_pick"),
                entries,
                null,
                entry -> {
                    int index = entries.indexOf(entry);
                    if (index >= 0) {
                        onPick.accept(order.get(index));
                    }
                }
        ));
    }

    static int bonusColor(FactionBonus bonus) {
        return switch (bonus) {
            case MINERS -> 0x8A8A8A;
            case FARMERS -> 0x6FBF4A;
            case BUILDERS -> 0xC9A24C;
            case WARRIORS -> 0xC05050;
            case HOOKAH -> 0x7A5AB5;
            case CRAFTERS -> 0x4A90BF;
            case TRADERS -> 0xD4B23C;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, text("screen.kingdoms.faction_name"), left + CONTENT_LEFT, top + 62, TEXT_DARK, false);
        graphics.fill(left + 56, top + 82, left + 74, top + 100, 0xFF1A140C);
        graphics.fill(left + 58, top + 84, left + 72, top + 98, 0xFF000000 | selectedColor);
        graphics.drawString(font, text("screen.kingdoms.bonuses"), left + CONTENT_LEFT, top + 110, TEXT_DARK, false);
    }
}
