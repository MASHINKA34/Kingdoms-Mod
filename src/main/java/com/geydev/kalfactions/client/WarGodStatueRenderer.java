package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.WarGodStatueBlock;
import com.geydev.kalfactions.block.WarGodStatueBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Renders the complete 9 x 12 x 6-block Blockbench statue from its project data. */
public final class WarGodStatueRenderer implements BlockEntityRenderer<WarGodStatueBlockEntity> {
    // The tallest rotated horn reaches 196.971 Blockbench units; this maps it to exactly 32 px (2 blocks).
    private static final float WORLD_SCALE = 0.16246F;
    private static final ResourceLocation MODEL_RESOURCE = ResourceLocation.fromNamespaceAndPath(
            KalFactions.MOD_ID,
            "models/block/war_god_statue.bbmodel"
    );
    private static final ResourceLocation[] TEXTURES = {
            texture("war_god_blackstone"),
            texture("war_god_polished_blackstone"),
            texture("war_god_basalt"),
            texture("war_god_dark_oak"),
            texture("war_god_dark_iron"),
            texture("war_god_dark_gold"),
            texture("war_god_crystal"),
            texture("war_god_runes")
    };
    private static final int CRYSTAL_TEXTURE = 6;
    private static volatile List<ModelCube> cachedModel;
    private static volatile boolean loadAttempted;

    public WarGodStatueRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(WarGodStatueBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public AABB getRenderBoundingBox(WarGodStatueBlockEntity blockEntity) {
        var pos = blockEntity.getBlockPos();
        return new AABB(
                pos.getX() - 1.0D,
                pos.getY(),
                pos.getZ() - 1.0D,
                pos.getX() + 2.0D,
                pos.getY() + 2.0D,
                pos.getZ() + 2.0D
        );
    }

    @Override
    public void render(
            WarGodStatueBlockEntity blockEntity,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        Direction facing = blockEntity.getBlockState().getValue(WarGodStatueBlock.FACING);
        float facingRotation = switch (facing) {
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
        Level level = blockEntity.getLevel();
        float time = level == null ? partialTick : level.getGameTime() + partialTick;

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation));
        pose.scale(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE);
        renderGeometry(pose, buffer, packedLight, packedOverlay, time);
        pose.popPose();
    }

    static void renderGeometry(
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay,
            float time
    ) {
        List<ModelCube> model = model();
        if (model.isEmpty()) {
            return;
        }
        float pulse = 0.5F + 0.5F * (float)Math.sin(time * 0.12F);
        int crystalBrightness = Math.round(170.0F + 85.0F * pulse);

        // A BufferSource invalidates the previous VertexConsumer when the render type changes.
        // Complete one texture pass before asking it for the next buffer.
        for (int texture = 0; texture < TEXTURES.length; texture++) {
            boolean emissive = texture == CRYSTAL_TEXTURE;
            RenderType renderType = emissive
                    ? RenderType.eyes(TEXTURES[texture])
                    : RenderType.entityCutoutNoCull(TEXTURES[texture]);
            VertexConsumer consumer = buffer.getBuffer(renderType);
            for (ModelCube cube : model) {
                if (!hasTexture(cube, texture)) {
                    continue;
                }
                pose.pushPose();
                if (cube.rotationZ != 0.0F) {
                    pose.translate(cube.originX, cube.originY, cube.originZ);
                    pose.mulPose(Axis.ZP.rotationDegrees(cube.rotationZ));
                    pose.translate(-cube.originX, -cube.originY, -cube.originZ);
                }

                for (Direction direction : Direction.values()) {
                    Face face = cube.faces[direction.get3DDataValue()];
                    if (face == null || face.texture != texture) {
                        continue;
                    }
                    emitFace(
                            consumer,
                            pose,
                            cube,
                            direction,
                            face,
                            emissive ? LightTexture.FULL_BRIGHT : packedLight,
                            packedOverlay,
                            emissive ? crystalBrightness : 255
                    );
                }
                pose.popPose();
            }
        }
        renderCoreGlow(pose, buffer, pulse);
    }

    private static void renderCoreGlow(PoseStack pose, MultiBufferSource buffer, float pulse) {
        VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
        float centerY = 126.5F / 16.0F;
        float frontZ = -28.35F / 16.0F;
        float breathing = 0.92F + pulse * 0.08F;

        glowDiamond(
                glow,
                pose,
                centerY,
                frontZ,
                18.0F / 16.0F * breathing,
                255,
                8,
                2,
                Math.round(24.0F + 46.0F * pulse)
        );
        glowDiamond(
                glow,
                pose,
                centerY,
                frontZ - 0.01F,
                12.5F / 16.0F * breathing,
                255,
                35,
                12,
                Math.round(65.0F + 90.0F * pulse)
        );
    }

    private static void glowDiamond(
            VertexConsumer consumer,
            PoseStack pose,
            float centerY,
            float z,
            float radius,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        var matrix = pose.last().pose();
        consumer.addVertex(matrix, 0.0F, centerY + radius, z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, radius, centerY, z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, 0.0F, centerY - radius, z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, -radius, centerY, z).setColor(red, green, blue, alpha);
    }

    private static boolean hasTexture(ModelCube cube, int texture) {
        for (Face face : cube.faces) {
            if (face != null && face.texture == texture) {
                return true;
            }
        }
        return false;
    }

    private static void emitFace(
            VertexConsumer consumer,
            PoseStack pose,
            ModelCube cube,
            Direction direction,
            Face face,
            int light,
            int overlay,
            int brightness
    ) {
        float x1 = cube.fromX;
        float y1 = cube.fromY;
        float z1 = cube.fromZ;
        float x2 = cube.toX;
        float y2 = cube.toY;
        float z2 = cube.toZ;
        float u1 = face.u1;
        float v1 = face.v1;
        float u2 = face.u2;
        float v2 = face.v2;

        switch (direction) {
            case DOWN -> quad(consumer, pose, direction, light, overlay, brightness, u1, v1, u2, v2,
                    x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2);
            case UP -> quad(consumer, pose, direction, light, overlay, brightness, u1, v1, u2, v2,
                    x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
            case NORTH -> quad(consumer, pose, direction, light, overlay, brightness, u1, v1, u2, v2,
                    x2, y2, z1, x2, y1, z1, x1, y1, z1, x1, y2, z1);
            case SOUTH -> quad(consumer, pose, direction, light, overlay, brightness, u1, v1, u2, v2,
                    x1, y2, z2, x1, y1, z2, x2, y1, z2, x2, y2, z2);
            case WEST -> quad(consumer, pose, direction, light, overlay, brightness, u1, v1, u2, v2,
                    x1, y2, z1, x1, y1, z1, x1, y1, z2, x1, y2, z2);
            case EAST -> quad(consumer, pose, direction, light, overlay, brightness, u1, v1, u2, v2,
                    x2, y2, z2, x2, y1, z2, x2, y1, z1, x2, y2, z1);
        }
    }

    private static void quad(
            VertexConsumer consumer,
            PoseStack pose,
            Direction direction,
            int light,
            int overlay,
            int brightness,
            float u1,
            float v1,
            float u2,
            float v2,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float cx,
            float cy,
            float cz,
            float dx,
            float dy,
            float dz
    ) {
        vertex(consumer, pose, direction, ax, ay, az, u1, v1, light, overlay, brightness);
        vertex(consumer, pose, direction, bx, by, bz, u1, v2, light, overlay, brightness);
        vertex(consumer, pose, direction, cx, cy, cz, u2, v2, light, overlay, brightness);
        vertex(consumer, pose, direction, dx, dy, dz, u2, v1, light, overlay, brightness);
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack pose,
            Direction direction,
            float x,
            float y,
            float z,
            float u,
            float v,
            int light,
            int overlay,
            int brightness
    ) {
        var transform = pose.last();
        consumer.addVertex(transform, x, y, z)
                .setColor(brightness, brightness, brightness, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(
                        transform,
                        direction.getStepX(),
                        direction.getStepY(),
                        direction.getStepZ()
                );
    }

    private static List<ModelCube> model() {
        if (!loadAttempted) {
            synchronized (WarGodStatueRenderer.class) {
                if (!loadAttempted) {
                    cachedModel = loadModel();
                    loadAttempted = true;
                }
            }
        }
        return cachedModel == null ? List.of() : cachedModel;
    }

    private static List<ModelCube> loadModel() {
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(MODEL_RESOURCE)
                    .orElseThrow(() -> new IllegalStateException("Missing model resource " + MODEL_RESOURCE));
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray elements = root.getAsJsonArray("elements");
                List<ModelCube> cubes = new ArrayList<>(elements.size());
                for (var element : elements) {
                    JsonObject object = element.getAsJsonObject();
                    float[] from = vector(object.getAsJsonArray("from"));
                    float[] to = vector(object.getAsJsonArray("to"));
                    float[] origin = object.has("origin")
                            ? vector(object.getAsJsonArray("origin"))
                            : new float[]{0.0F, 0.0F, 0.0F};
                    float rotationZ = object.has("rotation")
                            ? object.getAsJsonArray("rotation").get(2).getAsFloat()
                            : 0.0F;
                    Face[] faces = new Face[Direction.values().length];
                    JsonObject faceObjects = object.getAsJsonObject("faces");
                    for (Direction direction : Direction.values()) {
                        if (!faceObjects.has(direction.getName())) {
                            continue;
                        }
                        JsonObject face = faceObjects.getAsJsonObject(direction.getName());
                        int texture = face.get("texture").getAsInt();
                        if (texture < 0 || texture >= TEXTURES.length) {
                            continue;
                        }
                        float[] uv = vector4(face.getAsJsonArray("uv"));
                        faces[direction.get3DDataValue()] = new Face(
                                texture,
                                uv[0] / 64.0F,
                                uv[1] / 64.0F,
                                uv[2] / 64.0F,
                                uv[3] / 64.0F
                        );
                    }
                    cubes.add(new ModelCube(
                            from[0] / 16.0F,
                            from[1] / 16.0F,
                            from[2] / 16.0F,
                            to[0] / 16.0F,
                            to[1] / 16.0F,
                            to[2] / 16.0F,
                            origin[0] / 16.0F,
                            origin[1] / 16.0F,
                            origin[2] / 16.0F,
                            rotationZ,
                            faces
                    ));
                }
                KalFactions.LOGGER.info("Loaded war god statue model with {} cubes", cubes.size());
                return List.copyOf(cubes);
            }
        } catch (Exception exception) {
            KalFactions.LOGGER.error("Could not load war god statue model {}", MODEL_RESOURCE, exception);
            return List.of();
        }
    }

    private static float[] vector(JsonArray array) {
        return new float[]{
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
        };
    }

    private static float[] vector4(JsonArray array) {
        return new float[]{
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat(),
                array.get(3).getAsFloat()
        };
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/block/" + name + ".png");
    }

    private record Face(int texture, float u1, float v1, float u2, float v2) {
    }

    private record ModelCube(
            float fromX,
            float fromY,
            float fromZ,
            float toX,
            float toY,
            float toZ,
            float originX,
            float originY,
            float originZ,
            float rotationZ,
            Face[] faces
    ) {
    }
}
