package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionClaimMapScreen extends FactionScreen {
    private static final int CELL_SIZE = 12;
    private int gridSize;
    private int gridLeft;
    private int gridTop;

    public FactionClaimMapScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.claim_map.title"), snapshot, successful, message);
    }

    @Override
    protected void initFactionWidgets() {
        gridSize = snapshot.mapRadius() * 2 + 1;
        gridLeft = left + 18;
        gridTop = top + 39;

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.back"),
                button -> FactionScreens.openRoot(snapshot, true, "")
        ).bounds(left + 238, top + 43, 74, 20).build());
        addRenderableWidget(Button.builder(
                text("screen.kingdoms.refresh"),
                button -> requestRefresh()
        ).bounds(left + 238, top + 69, 74, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int row = 0; row < gridSize; row++) {
            for (int column = 0; column < gridSize; column++) {
                int chunkX = snapshot.centerChunkX() + column - snapshot.mapRadius();
                int chunkZ = snapshot.centerChunkZ() + row - snapshot.mapRadius();
                int x = gridLeft + column * CELL_SIZE;
                int y = gridTop + row * CELL_SIZE;
                int parchment = ((chunkX * 31 + chunkZ * 17) & 1) == 0 ? 0xFFBFA66F : 0xFFC9B47E;
                graphics.fill(x, y, x + CELL_SIZE - 1, y + CELL_SIZE - 1, parchment);

                FactionSnapshot.Claim claim = snapshot.claimAt(chunkX, chunkZ);
                if (claim != null) {
                    graphics.fill(x + 1, y + 1, x + CELL_SIZE - 2, y + CELL_SIZE - 2, 0xD0000000 | claim.color());
                }
                if (chunkX == snapshot.centerChunkX() && chunkZ == snapshot.centerChunkZ()) {
                    graphics.fill(x + 4, y + 4, x + CELL_SIZE - 5, y + CELL_SIZE - 5, 0xFFFFFFFF);
                }
            }
        }

        int hoveredX = hoveredChunkX(mouseX);
        int hoveredZ = hoveredChunkZ(mouseY);
        if (insideGrid(mouseX, mouseY)) {
            FactionSnapshot.Claim claim = snapshot.claimAt(hoveredX, hoveredZ);
            Component label = claim == null
                    ? text("screen.kingdoms.claim_map.chunk", hoveredX, hoveredZ)
                    : text("screen.kingdoms.claim_map.claimed_chunk", claim.label(), hoveredX, hoveredZ);
            graphics.drawString(font, label, left + 238, top + 105, 0x3F2A19, false);
        }
        graphics.drawWordWrap(
                font,
                text("screen.kingdoms.claim_map.help"),
                left + 238,
                top + 121,
                80,
                0x5B452E
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && snapshot.canClaim() && insideGrid(mouseX, mouseY)) {
            int chunkX = hoveredChunkX(mouseX);
            int chunkZ = hoveredChunkZ(mouseY);
            FactionSnapshot.Claim claim = snapshot.claimAt(chunkX, chunkZ);
            if (claim == null) {
                PacketDistributor.sendToServer(new FactionPayloads.C2SSetClaim(
                        snapshot.tablePos(),
                        chunkX,
                        chunkZ,
                        true
                ));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        return mouseX >= gridLeft
                && mouseY >= gridTop
                && mouseX < gridLeft + gridSize * CELL_SIZE
                && mouseY < gridTop + gridSize * CELL_SIZE;
    }

    private int hoveredChunkX(double mouseX) {
        return snapshot.centerChunkX() + (int)((mouseX - gridLeft) / CELL_SIZE) - snapshot.mapRadius();
    }

    private int hoveredChunkZ(double mouseY) {
        return snapshot.centerChunkZ() + (int)((mouseY - gridTop) / CELL_SIZE) - snapshot.mapRadius();
    }
}
