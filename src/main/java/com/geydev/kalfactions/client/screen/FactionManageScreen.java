package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.EmblemTextures;
import com.geydev.kalfactions.integration.xaero.XaeroMaps;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionManageScreen extends FactionScreen {
    private EditBox nameBox;
    private String nameValue;
    private int selectedColor;

    public FactionManageScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.faction_manage"), snapshot, successful, message);
        selectedColor = snapshot.color();
        nameValue = snapshot.name();
    }

    @Override
    public void acceptServerState(FactionSnapshot newSnapshot, boolean actionSuccessful, String message) {
        selectedColor = newSnapshot.color();
        nameValue = newSnapshot.name();
        super.acceptServerState(newSnapshot, actionSuccessful, message);
    }

    @Override
    protected void initFactionWidgets() {
        int leftColumn = left + CONTENT_LEFT;
        int rightColumn = left + 172;
        int columnWidth = 126;
        int row0 = top + 92;
        int rowStep = 21;

        nameBox = new EditBox(
                font,
                leftColumn,
                row0,
                columnWidth,
                20,
                text("screen.kingdoms.faction_name")
        );
        nameBox.setMaxLength(32);
        nameBox.setValue(nameValue);
        nameBox.setResponder(value -> nameValue = value);
        nameBox.setEditable(snapshot.canManage());
        addRenderableWidget(nameBox);

        KingdomsButton color = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.color"),
                button -> minecraft.setScreen(new ColorPickerScreen(this, selectedColor, picked -> selectedColor = picked)),
                leftColumn, row0 + rowStep, 61, 20
        ));
        color.active = snapshot.canManage();

        KingdomsButton emblem = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.emblem"),
                button -> minecraft.setScreen(new EmblemEditorScreen(
                        this,
                        unboxedEmblem(),
                        snapshot.emblemUrl(),
                        (pixels, url) -> PacketDistributor.sendToServer(new FactionPayloads.C2SSetEmblem(
                                snapshot.tablePos(),
                                boxedEmblem(pixels),
                                url
                        ))
                )),
                leftColumn + 65, row0 + rowStep, 61, 20
        ));
        emblem.active = snapshot.canManage();

        KingdomsButton save = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.save"),
                button -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SUpdateFaction(snapshot.tablePos(), nameBox.getValue(), selectedColor)
                ),
                leftColumn, row0 + rowStep * 2, columnWidth, 20
        ));
        save.active = snapshot.canManage();

        KingdomsButton pvp = addRenderableWidget(KingdomsButton.create(
                pvpLabel(),
                button -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SSetPvp(snapshot.tablePos(), !snapshot.internalPvp())
                ),
                leftColumn, row0 + rowStep * 3, columnWidth, 20
        ));
        pvp.active = snapshot.isOfficer();

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.members_count", snapshot.members().size()),
                button -> FactionScreens.openMembers(snapshot, true, ""),
                rightColumn, row0, columnWidth, 20
        ));

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.treasury"),
                button -> FactionScreens.openTreasury(snapshot, true, ""),
                rightColumn, row0 + rowStep, columnWidth, 20
        ));

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.claim_map.open"),
                button -> {
                    if (!XaeroMaps.openWorldMap()) {
                        FactionScreens.openClaims(snapshot, true, "");
                    }
                },
                rightColumn, row0 + rowStep * 2, columnWidth, 20
        ));

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.actions_open"),
                button -> FactionScreens.openActions(snapshot, true, ""),
                rightColumn, row0 + rowStep * 3, columnWidth, 20
        ));
    }

    private int[] unboxedEmblem() {
        List<Integer> emblem = snapshot.emblem();
        if (!FactionSnapshot.isValidEmblemSize(emblem.size())) {
            return null;
        }
        int[] pixels = new int[emblem.size()];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = emblem.get(index);
        }
        return pixels;
    }

    private static List<Integer> boxedEmblem(int[] pixels) {
        if (pixels == null || !FactionSnapshot.isValidEmblemSize(pixels.length)) {
            return List.of();
        }
        List<Integer> boxed = new ArrayList<>(pixels.length);
        for (int pixel : pixels) {
            boxed.add(pixel);
        }
        return boxed;
    }

    private Component pvpLabel() {
        Component state = snapshot.internalPvp()
                ? text("screen.kingdoms.on")
                : text("screen.kingdoms.off");
        return text("screen.kingdoms.pvp", state);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int leftColumn = left + CONTENT_LEFT;
        int rightColumn = left + 172;

        EmblemTextures.Emblem emblem = EmblemTextures.resolve(
                snapshot.factionId(),
                snapshot.emblem(),
                snapshot.emblemUrl()
        );
        graphics.fill(leftColumn - 1, top + 56, leftColumn + 13, top + 70, 0xFF1A140C);
        if (emblem != null) {
            graphics.blit(
                    emblem.texture(),
                    leftColumn, top + 57, 12, 12,
                    0.0F, 0.0F, emblem.width(), emblem.height(),
                    emblem.width(), emblem.height()
            );
        } else {
            graphics.fill(leftColumn, top + 57, leftColumn + 12, top + 69, 0xFF000000 | selectedColor);
        }
        graphics.drawString(font, snapshot.name(), leftColumn + 17, top + 59, TEXT_DARK, false);
        graphics.drawString(
                font,
                text("screen.kingdoms.owner", snapshot.ownerName()),
                rightColumn,
                top + 59,
                TEXT_MUTED,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.treasury_balance", currency(snapshot.treasury())),
                leftColumn,
                top + 71,
                TEXT_MUTED,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.total_influence", snapshot.influence()),
                rightColumn,
                top + 71,
                TEXT_MUTED,
                false
        );
        graphics.drawString(
                font,
                text(
                        "screen.kingdoms.manage_chunks",
                        snapshot.claimCount(),
                        snapshot.forceLoadUsed(),
                        chunkSlotLimit()
                ),
                leftColumn,
                top + 82,
                TEXT_MUTED,
                false
        );
    }

    private int chunkSlotLimit() {
        int chunkNodes = 0;
        for (String name : snapshot.completedResearch()) {
            com.geydev.kalfactions.faction.ResearchNode node =
                    com.geydev.kalfactions.faction.ResearchNode.parse(name).orElse(null);
            if (node != null && node.bonusTag().contains("CHUNK_SLOT")) {
                chunkNodes++;
            }
        }
        return com.geydev.kalfactions.config.ModConfigSpec.FORCE_LOAD_SLOTS.getAsInt() + 5 * chunkNodes;
    }

    static Component currency(long amount) {
        long lastTwo = Math.floorMod(amount, 100L);
        long last = Math.floorMod(amount, 10L);
        String key;
        if (last == 1L && lastTwo != 11L) {
            key = "kingdoms.currency.spurs.one";
        } else if (last >= 2L && last <= 4L && (lastTwo < 12L || lastTwo > 14L)) {
            key = "kingdoms.currency.spurs.few";
        } else {
            key = "kingdoms.currency.spurs.many";
        }
        return text(key, amount);
    }
}
