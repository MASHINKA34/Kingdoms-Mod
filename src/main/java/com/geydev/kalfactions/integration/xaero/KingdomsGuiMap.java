package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

final class KingdomsGuiMap extends GuiMap {
    private static Field selectionField;
    private static Field rightClickXField;
    private static Field rightClickZField;
    private static Field rightClickDimField;
    private static boolean failureLogged;

    KingdomsGuiMap(Screen parent, Screen escape, MapProcessor processor, Entity player) {
        super(parent, escape, processor, player);
    }

    @Override
    public ArrayList<RightClickOption> getRightClickOptions() {
        ArrayList<RightClickOption> options = super.getRightClickOptions();
        try {
            ResourceKey<Level> clickedDimension = clickedDimension();
            Minecraft minecraft = Minecraft.getInstance();
            boolean sameDimension = minecraft.level != null
                    && clickedDimension != null
                    && minecraft.level.dimension().equals(clickedDimension);
            List<Long> chunks = selectedChunks();
            if (chunks.isEmpty()) {
                return options;
            }
            boolean usable = sameDimension && chunks.size() <= FactionPayloads.C2SMapSetClaims.MAX_CHUNKS;
            options.add(option("kingdoms.xaero_map.claim", options.size(), chunks, true, usable));
            options.add(option("kingdoms.xaero_map.unclaim", options.size() + 1, chunks, false, usable));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!failureLogged) {
                failureLogged = true;
                KalFactions.LOGGER.warn("Could not add Kingdoms claim options to the Xaero world map", exception);
            }
        }
        return options;
    }

    private RightClickOption option(String key, int index, List<Long> chunks, boolean claimed, boolean usable) {
        RightClickOption option = new RightClickOption(key, index, this) {
            @Override
            public void onAction(Screen screen) {
                PacketDistributor.sendToServer(new FactionPayloads.C2SMapSetClaims(claimed, chunks));
            }
        };
        option.setActive(usable);
        option.setNameFormatArgs(chunks.size());
        return option;
    }

    private List<Long> selectedChunks() throws ReflectiveOperationException {
        resolveFields();
        List<Long> chunks = new ArrayList<>();
        MapTileSelection selection = (MapTileSelection) selectionField.get(this);
        if (selection != null) {
            for (int x = selection.getLeft(); x <= selection.getRight(); x++) {
                for (int z = selection.getTop(); z <= selection.getBottom(); z++) {
                    chunks.add(ChunkPos.asLong(x, z));
                    if (chunks.size() > FactionPayloads.C2SMapSetClaims.MAX_CHUNKS) {
                        return chunks;
                    }
                }
            }
            return chunks;
        }
        int blockX = rightClickXField.getInt(this);
        int blockZ = rightClickZField.getInt(this);
        chunks.add(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
        return chunks;
    }

    @SuppressWarnings("unchecked")
    private ResourceKey<Level> clickedDimension() throws ReflectiveOperationException {
        resolveFields();
        return (ResourceKey<Level>) rightClickDimField.get(this);
    }

    private static void resolveFields() throws ReflectiveOperationException {
        if (selectionField != null) {
            return;
        }
        Field selection = GuiMap.class.getDeclaredField("mapTileSelection");
        selection.setAccessible(true);
        Field clickX = GuiMap.class.getDeclaredField("rightClickX");
        clickX.setAccessible(true);
        Field clickZ = GuiMap.class.getDeclaredField("rightClickZ");
        clickZ.setAccessible(true);
        Field clickDim = GuiMap.class.getDeclaredField("rightClickDim");
        clickDim.setAccessible(true);
        rightClickXField = clickX;
        rightClickZField = clickZ;
        rightClickDimField = clickDim;
        selectionField = selection;
    }
}
