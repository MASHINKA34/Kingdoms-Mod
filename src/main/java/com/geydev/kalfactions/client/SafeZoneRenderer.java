package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.item.SafeZoneWandItem;
import com.geydev.kalfactions.market.PlotSelection;
import com.geydev.kalfactions.safezone.SafeZonePayloads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class SafeZoneRenderer {
    private static final double RENDER_DISTANCE = 128.0D;
    private static final double OUTLINE_INFLATE = 0.01D;
    private static final float ZONE_RED = 0.20F;
    private static final float ZONE_GREEN = 0.84F;
    private static final float ZONE_BLUE = 0.78F;
    private static final int PARTICLE_INTERVAL = 10;
    private static final int MAX_PARTICLES = 64;
    private static final double PARTICLE_STEP = 2.0D;
    private static final double PARTICLE_DISTANCE = 24.0D;
    private static final DustParticleOptions OUTLINE_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.0F);

    private static String lastActionBarZone;

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSafeZoneStore.clear();
        lastActionBarZone = null;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        PlotSelection selection = heldSelection(player);
        AABB selectionBox = selection != null && selection.matchesDimension(minecraft.level)
                ? selectionBounds(selection)
                : null;

        if (selectionBox != null && selection.isComplete() && Screen.hasControlDown()) {
            Direction face = targetedFace(player, selectionBox);
            if (face != null) {
                AABB slab = faceSlab(selectionBox, face);
                VertexConsumer fill = bufferSource.getBuffer(RenderType.debugFilledBox());
                LevelRenderer.addChainedFilledBoxVertices(
                        poseStack,
                        fill,
                        slab.minX - cameraPos.x, slab.minY - cameraPos.y, slab.minZ - cameraPos.z,
                        slab.maxX - cameraPos.x, slab.maxY - cameraPos.y, slab.maxZ - cameraPos.z,
                        0.85F, 1.00F, 0.60F, 0.42F
                );
                bufferSource.endBatch(RenderType.debugFilledBox());
            }
        }

        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        for (SafeZonePayloads.ZoneEntry zone
                : ClientSafeZoneStore.zonesIn(minecraft.level.dimension().location())) {
            renderBox(poseStack, lines, cameraPos,
                    zone.minX(), zone.minY(), zone.minZ(),
                    zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1,
                    ZONE_RED, ZONE_GREEN, ZONE_BLUE);
        }
        if (selectionBox != null) {
            renderBox(poseStack, lines, cameraPos,
                    selectionBox.minX, selectionBox.minY, selectionBox.minZ,
                    selectionBox.maxX, selectionBox.maxY, selectionBox.maxZ,
                    1.00F, 1.00F, 1.00F);
        }
        bufferSource.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null
                || !Screen.hasControlDown() || !player.hasPermissions(2)) {
            return;
        }
        PlotSelection selection = heldSelection(player);
        if (selection == null || !selection.isComplete() || !selection.matchesDimension(minecraft.level)) {
            return;
        }
        AABB box = selectionBounds(selection);
        Direction face = targetedFace(player, box);
        if (face == null) {
            return;
        }
        boolean towardPlayer = event.getScrollDeltaY() < 0.0D;
        boolean outside = isOutsideFacePlane(player, box, face);
        byte delta = (byte) (towardPlayer == outside ? 1 : -1);
        PacketDistributor.sendToServer(
                new SafeZonePayloads.C2SAdjustSelection((byte) face.ordinal(), delta));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        BlockPos pos = player.blockPosition();
        SafeZonePayloads.ZoneEntry inside = null;
        for (SafeZonePayloads.ZoneEntry zone
                : ClientSafeZoneStore.zonesIn(minecraft.level.dimension().location())) {
            if (zone.contains(pos.getX(), pos.getY(), pos.getZ())) {
                inside = zone;
                break;
            }
        }
        if (inside == null) {
            if (lastActionBarZone != null) {
                lastActionBarZone = null;
                player.displayClientMessage(Component.translatable("kingdoms.safezone.left"), true);
            }
            return;
        }
        if (!inside.id().equals(lastActionBarZone)) {
            lastActionBarZone = inside.id();
            player.displayClientMessage(Component.translatable("kingdoms.safezone.entered"), true);
        }
    }

    @SubscribeEvent
    public static void onOutlineParticles(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || level.getGameTime() % PARTICLE_INTERVAL != 0L) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        int budget = MAX_PARTICLES;
        for (SafeZonePayloads.ZoneEntry zone : ClientSafeZoneStore.zonesIn(level.dimension().location())) {
            double minX = zone.minX();
            double minY = zone.minY();
            double minZ = zone.minZ();
            double maxX = zone.maxX() + 1.0D;
            double maxY = zone.maxY() + 1.0D;
            double maxZ = zone.maxZ() + 1.0D;
            for (double y : new double[] {minY, maxY}) {
                for (double z : new double[] {minZ, maxZ}) {
                    budget = spawnAlongEdge(level, eye, budget, minX, y, z, maxX, y, z);
                }
                for (double x : new double[] {minX, maxX}) {
                    budget = spawnAlongEdge(level, eye, budget, x, y, minZ, x, y, maxZ);
                }
            }
            for (double x : new double[] {minX, maxX}) {
                for (double z : new double[] {minZ, maxZ}) {
                    budget = spawnAlongEdge(level, eye, budget, x, minY, z, x, maxY, z);
                }
            }
            if (budget <= 0) {
                return;
            }
        }
    }

    private static int spawnAlongEdge(
            ClientLevel level,
            Vec3 eye,
            int budget,
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ
    ) {
        double length = Math.sqrt((toX - fromX) * (toX - fromX)
                + (toY - fromY) * (toY - fromY)
                + (toZ - fromZ) * (toZ - fromZ));
        int steps = Math.max(1, (int) Math.ceil(length / PARTICLE_STEP));
        for (int index = 0; index <= steps && budget > 0; index++) {
            double progress = (double) index / steps;
            double x = fromX + (toX - fromX) * progress;
            double y = fromY + (toY - fromY) * progress;
            double z = fromZ + (toZ - fromZ) * progress;
            if (eye.distanceToSqr(x, y, z) > PARTICLE_DISTANCE * PARTICLE_DISTANCE) {
                continue;
            }
            level.addParticle(OUTLINE_DUST, x, y, z, 0.0D, 0.0D, 0.0D);
            budget--;
        }
        return budget;
    }

    private static PlotSelection heldSelection(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            PlotSelection selection = SafeZoneWandItem.selectionOf(stack);
            if (selection != null) {
                return selection;
            }
        }
        return null;
    }

    private static AABB selectionBounds(PlotSelection selection) {
        BlockPos first = selection.first();
        BlockPos second = selection.second().orElse(first);
        return new AABB(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1,
                Math.max(first.getY(), second.getY()) + 1,
                Math.max(first.getZ(), second.getZ()) + 1
        );
    }

    private static Direction targetedFace(LocalPlayer player, AABB box) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        if (box.contains(eye)) {
            return Direction.getNearest(look.x, look.y, look.z);
        }
        Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(96.0D)));
        if (hit.isEmpty()) {
            return null;
        }
        Vec3 point = hit.get();
        double epsilon = 1.0E-4D;
        if (Math.abs(point.x - box.minX) < epsilon) {
            return Direction.WEST;
        }
        if (Math.abs(point.x - box.maxX) < epsilon) {
            return Direction.EAST;
        }
        if (Math.abs(point.y - box.minY) < epsilon) {
            return Direction.DOWN;
        }
        if (Math.abs(point.y - box.maxY) < epsilon) {
            return Direction.UP;
        }
        if (Math.abs(point.z - box.minZ) < epsilon) {
            return Direction.NORTH;
        }
        return Direction.SOUTH;
    }

    private static boolean isOutsideFacePlane(LocalPlayer player, AABB box, Direction face) {
        Vec3 eye = player.getEyePosition();
        return switch (face) {
            case EAST -> eye.x >= box.maxX;
            case WEST -> eye.x <= box.minX;
            case UP -> eye.y >= box.maxY;
            case DOWN -> eye.y <= box.minY;
            case SOUTH -> eye.z >= box.maxZ;
            case NORTH -> eye.z <= box.minZ;
        };
    }

    private static AABB faceSlab(AABB box, Direction face) {
        double thickness = 0.03D;
        return switch (face) {
            case WEST -> new AABB(box.minX - thickness, box.minY, box.minZ,
                    box.minX + thickness, box.maxY, box.maxZ);
            case EAST -> new AABB(box.maxX - thickness, box.minY, box.minZ,
                    box.maxX + thickness, box.maxY, box.maxZ);
            case DOWN -> new AABB(box.minX, box.minY - thickness, box.minZ,
                    box.maxX, box.minY + thickness, box.maxZ);
            case UP -> new AABB(box.minX, box.maxY - thickness, box.minZ,
                    box.maxX, box.maxY + thickness, box.maxZ);
            case NORTH -> new AABB(box.minX, box.minY, box.minZ - thickness,
                    box.maxX, box.maxY, box.minZ + thickness);
            case SOUTH -> new AABB(box.minX, box.minY, box.maxZ - thickness,
                    box.maxX, box.maxY, box.maxZ + thickness);
        };
    }

    private static void renderBox(
            PoseStack poseStack,
            VertexConsumer lines,
            Vec3 camera,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float red, float green, float blue
    ) {
        double centerX = (minX + maxX) / 2.0D;
        double centerZ = (minZ + maxZ) / 2.0D;
        if (camera.distanceToSqr(centerX, camera.y, centerZ) > RENDER_DISTANCE * RENDER_DISTANCE) {
            return;
        }
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                minX - camera.x - OUTLINE_INFLATE,
                minY - camera.y - OUTLINE_INFLATE,
                minZ - camera.z - OUTLINE_INFLATE,
                maxX - camera.x + OUTLINE_INFLATE,
                maxY - camera.y + OUTLINE_INFLATE,
                maxZ - camera.z + OUTLINE_INFLATE,
                red, green, blue, 1.0F
        );
    }

    private SafeZoneRenderer() {
    }
}
