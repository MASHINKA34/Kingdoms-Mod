package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientClaimStore;
import com.geydev.kalfactions.client.ClientClaimStore.ClaimInfo;
import com.geydev.kalfactions.client.ClientClaimStore.ViewerInfo;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.economy.PriceMath;
import com.geydev.kalfactions.net.FactionPayloads;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

final class KingdomsGuiMap extends GuiMap {
    private static final long NOTICE_DURATION_MILLIS = 4000L;

    private static Field selectionField;
    private static Field rightClickXField;
    private static Field rightClickZField;
    private static Field rightClickDimField;
    private static boolean failureLogged;

    private Component noticeMessage;
    private boolean noticeSuccessful;
    private long noticeUntilMillis;

    KingdomsGuiMap(Screen parent, Screen escape, MapProcessor processor, Entity player) {
        super(parent, escape, processor, player);
    }

    void showNotice(Component message, boolean successful) {
        noticeMessage = message;
        noticeSuccessful = successful;
        noticeUntilMillis = System.currentTimeMillis() + NOTICE_DURATION_MILLIS;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (noticeMessage == null || System.currentTimeMillis() >= noticeUntilMillis) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(noticeMessage, Math.min(360, width - 40));
        int textWidth = 0;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int boxWidth = textWidth + 16;
        int boxHeight = lines.size() * 10 + 10;
        int boxLeft = (width - boxWidth) / 2;
        int boxTop = height - 58 - boxHeight;
        graphics.fill(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight, 0xC0101018);
        int accent = noticeSuccessful ? 0xFF7FBF6F : 0xFFC05050;
        graphics.fill(boxLeft, boxTop, boxLeft + boxWidth, boxTop + 1, accent);
        int color = noticeSuccessful ? 0xFFC9F0B8 : 0xFFF3B8B8;
        for (int index = 0; index < lines.size(); index++) {
            net.minecraft.util.FormattedCharSequence line = lines.get(index);
            graphics.drawString(font, line, boxLeft + (boxWidth - font.width(line)) / 2,
                    boxTop + 6 + index * 10, color, true);
        }
    }

    @Override
    public ArrayList<RightClickOption> getRightClickOptions() {
        ArrayList<RightClickOption> options = super.getRightClickOptions();
        try {
            ViewerInfo viewer = ClientClaimStore.viewer();
            if (!viewer.hasFaction()) {
                return options;
            }
            ResourceKey<Level> clickedDimension = clickedDimension();
            Minecraft minecraft = Minecraft.getInstance();
            boolean sameDimension = minecraft.level != null
                    && clickedDimension != null
                    && minecraft.level.dimension().equals(clickedDimension);
            List<Long> selected = selectedChunks();
            if (selected.isEmpty()) {
                return options;
            }

            List<Long> claimable = new ArrayList<>();
            List<Long> ownClaims = new ArrayList<>();
            for (Long packed : selected) {
                ChunkPos pos = new ChunkPos(packed);
                ClaimInfo claim = clickedDimension == null
                        ? null
                        : ClientClaimStore.get(clickedDimension, pos.x, pos.z);
                if (claim == null) {
                    claimable.add(packed);
                } else if (claim.factionId().equals(viewer.factionId())) {
                    ownClaims.add(packed);
                }
            }

            boolean withinLimit = selected.size() <= FactionPayloads.C2SMapSetClaims.MAX_CHUNKS;
            options.add(claimOption(options.size(), viewer, claimable, sameDimension && withinLimit));
            options.add(unclaimOption(options.size(), ownClaims, sameDimension && withinLimit));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!failureLogged) {
                failureLogged = true;
                KalFactions.LOGGER.warn("Could not add Kingdoms claim options to the Xaero world map", exception);
            }
        }
        return options;
    }

    private RightClickOption claimOption(int index, ViewerInfo viewer, List<Long> chunks, boolean usable) {
        long totalPrice = estimateClaimPrice(viewer, chunks.size());
        String key = totalPrice < 0L ? "kingdoms.xaero_map.claim_noprice" : "kingdoms.xaero_map.claim";
        RightClickOption option = option(key, index, chunks, true);
        if (totalPrice < 0L) {
            option.setNameFormatArgs(chunks.size());
        } else {
            option.setNameFormatArgs(chunks.size(), NumismaticsEconomy.format(totalPrice).getString());
        }
        option.setActive(usable && !chunks.isEmpty());
        return option;
    }

    private RightClickOption unclaimOption(int index, List<Long> chunks, boolean usable) {
        RightClickOption option = option("kingdoms.xaero_map.unclaim", index, chunks, false);
        option.setNameFormatArgs(chunks.size());
        option.setActive(usable && !chunks.isEmpty());
        return option;
    }

    private RightClickOption option(String key, int index, List<Long> chunks, boolean claimed) {
        return new RightClickOption(key, index, this) {
            @Override
            public void onAction(Screen screen) {
                if (!chunks.isEmpty()) {
                    PacketDistributor.sendToServer(new FactionPayloads.C2SMapSetClaims(claimed, chunks));
                }
            }
        };
    }

    private static long estimateClaimPrice(ViewerInfo viewer, int chunkCount) {
        try {
            int freeClaims = ModConfigSpec.FREE_CLAIMS.get();
            long baseCost = ModConfigSpec.EXPANSION_BASE_COST.get();
            double growth = ModConfigSpec.EXPANSION_GROWTH.get();
            long total = 0L;
            for (int index = 0; index < chunkCount; index++) {
                total = PriceMath.saturatedAdd(total, PriceMath.claimPrice(
                        viewer.claimCount() + index,
                        freeClaims,
                        baseCost,
                        growth,
                        Mth.clamp(viewer.claimDiscount(), 0.0D, 1.0D)
                ));
            }
            return total;
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return -1L;
        }
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
