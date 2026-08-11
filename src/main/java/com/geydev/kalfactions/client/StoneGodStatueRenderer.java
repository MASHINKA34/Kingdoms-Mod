package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.StoneGodStatueBlock;
import com.geydev.kalfactions.block.StoneGodStatueBlockEntity;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class StoneGodStatueRenderer implements BlockEntityRenderer<StoneGodStatueBlockEntity> {
    private static final Map<Variant, ModelData> MODELS = new EnumMap<>(Variant.class);

    public StoneGodStatueRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(StoneGodStatueBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(StoneGodStatueBlockEntity blockEntity) {
        var pos = blockEntity.getBlockPos();
        return new AABB(
                pos.getX() - 4.0D,
                pos.getY(),
                pos.getZ() - 4.0D,
                pos.getX() + 5.0D,
                pos.getY() + StoneGodStatueBlock.HEIGHT,
                pos.getZ() + 5.0D
        );
    }

    @Override
    public void render(
            StoneGodStatueBlockEntity blockEntity,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        Variant variant = variant(blockEntity.getBlockState());
        if (variant == null) {
            return;
        }
        ModelData model = model(variant);
        if (model.cubes().isEmpty()) {
            return;
        }
        Direction facing = blockEntity.getBlockState().getValue(StoneGodStatueBlock.FACING);
        float facingRotation = switch (facing) {
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation));
        pose.translate(-variant.worldCenterX(model), -model.minY(), -variant.worldCenterZ(model));
        renderGeometry(model, pose, buffer, packedLight, packedOverlay);
        pose.popPose();
    }

    static void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        Variant variant = variant(stack);
        if (variant == null) {
            return;
        }
        ModelData model = model(variant);
        if (model.cubes().isEmpty()) {
            return;
        }
        float scale = switch (displayContext) {
            case GUI -> 0.11F;
            case GROUND -> 0.065F;
            case FIXED -> 0.105F;
            default -> 0.08F;
        };

        pose.pushPose();
        pose.translate(0.5F, 0.05F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(displayContext == ItemDisplayContext.GUI ? 205.0F : 180.0F));
        pose.scale(scale, scale, scale);
        pose.translate(-model.centerX(), -model.minY(), -model.centerZ());
        renderGeometry(model, pose, buffer, packedLight, packedOverlay);
        pose.popPose();
    }

    private static void renderGeometry(
            ModelData model,
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(model.texture()));
        for (ModelCube cube : model.cubes()) {
            pose.pushPose();
            if (cube.rotationZ() != 0.0F) {
                pose.translate(cube.originX(), cube.originY(), cube.originZ());
                pose.mulPose(Axis.ZP.rotationDegrees(cube.rotationZ()));
                pose.translate(-cube.originX(), -cube.originY(), -cube.originZ());
            }
            for (Direction direction : Direction.values()) {
                Face face = cube.faces()[direction.get3DDataValue()];
                if (face != null) {
                    emitFace(consumer, pose, cube, direction, face, packedLight, packedOverlay);
                }
            }
            pose.popPose();
        }
    }

    private static void emitFace(
            VertexConsumer consumer,
            PoseStack pose,
            ModelCube cube,
            Direction direction,
            Face face,
            int light,
            int overlay
    ) {
        float x1 = cube.fromX();
        float y1 = cube.fromY();
        float z1 = cube.fromZ();
        float x2 = cube.toX();
        float y2 = cube.toY();
        float z2 = cube.toZ();

        switch (direction) {
            case DOWN -> quad(consumer, pose, direction, light, overlay, face,
                    x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2);
            case UP -> quad(consumer, pose, direction, light, overlay, face,
                    x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
            case NORTH -> quad(consumer, pose, direction, light, overlay, face,
                    x2, y2, z1, x2, y1, z1, x1, y1, z1, x1, y2, z1);
            case SOUTH -> quad(consumer, pose, direction, light, overlay, face,
                    x1, y2, z2, x1, y1, z2, x2, y1, z2, x2, y2, z2);
            case WEST -> quad(consumer, pose, direction, light, overlay, face,
                    x1, y2, z1, x1, y1, z1, x1, y1, z2, x1, y2, z2);
            case EAST -> quad(consumer, pose, direction, light, overlay, face,
                    x2, y2, z2, x2, y1, z2, x2, y1, z1, x2, y2, z1);
        }
    }

    private static void quad(
            VertexConsumer consumer,
            PoseStack pose,
            Direction direction,
            int light,
            int overlay,
            Face face,
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
        vertex(consumer, pose, direction, ax, ay, az, face.u1(), face.v1(), light, overlay);
        vertex(consumer, pose, direction, bx, by, bz, face.u1(), face.v2(), light, overlay);
        vertex(consumer, pose, direction, cx, cy, cz, face.u2(), face.v2(), light, overlay);
        vertex(consumer, pose, direction, dx, dy, dz, face.u2(), face.v1(), light, overlay);
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
            int overlay
    ) {
        var transform = pose.last();
        consumer.addVertex(transform, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(transform, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static ModelData model(Variant variant) {
        synchronized (MODELS) {
            return MODELS.computeIfAbsent(variant, StoneGodStatueRenderer::loadModel);
        }
    }

    private static ModelData loadModel(Variant variant) {
        ResourceLocation modelResource = ResourceLocation.fromNamespaceAndPath(
                KalFactions.MOD_ID,
                "models/block/" + variant.fileName + ".bbmodel"
        );
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(modelResource)
                    .orElseThrow(() -> new IllegalStateException("Missing model resource " + modelResource));
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject resolution = root.getAsJsonObject("resolution");
                float textureWidth = resolution.get("width").getAsFloat();
                float textureHeight = resolution.get("height").getAsFloat();
                JsonArray elements = root.getAsJsonArray("elements");
                List<ModelCube> cubes = new ArrayList<>(elements.size());
                float[] bounds = {
                        Float.POSITIVE_INFINITY,
                        Float.POSITIVE_INFINITY,
                        Float.POSITIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        Float.NEGATIVE_INFINITY
                };
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
                        if (!face.has("texture") || face.get("texture").isJsonNull()) {
                            continue;
                        }
                        float[] uv = vector4(face.getAsJsonArray("uv"));
                        faces[direction.get3DDataValue()] = new Face(
                                uv[0] / textureWidth,
                                uv[1] / textureHeight,
                                uv[2] / textureWidth,
                                uv[3] / textureHeight
                        );
                    }
                    ModelCube cube = new ModelCube(
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
                    );
                    includeBounds(bounds, cube);
                    cubes.add(cube);
                }
                ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                        KalFactions.MOD_ID,
                        "textures/block/" + variant.fileName + ".png"
                );
                ModelData model = new ModelData(
                        List.copyOf(cubes),
                        texture,
                        bounds[0],
                        bounds[1],
                        bounds[2],
                        bounds[3],
                        bounds[4],
                        bounds[5]
                );
                KalFactions.LOGGER.info("Loaded {} with {} cubes", modelResource, cubes.size());
                return model;
            }
        } catch (Exception exception) {
            KalFactions.LOGGER.error("Could not load stone god statue model {}", modelResource, exception);
            return ModelData.EMPTY;
        }
    }

    private static void includeBounds(float[] bounds, ModelCube cube) {
        double radians = Math.toRadians(cube.rotationZ());
        float cosine = (float)Math.cos(radians);
        float sine = (float)Math.sin(radians);
        for (float x : new float[]{cube.fromX(), cube.toX()}) {
            for (float y : new float[]{cube.fromY(), cube.toY()}) {
                float rotatedX = cube.originX()
                        + (x - cube.originX()) * cosine
                        - (y - cube.originY()) * sine;
                float rotatedY = cube.originY()
                        + (x - cube.originX()) * sine
                        + (y - cube.originY()) * cosine;
                bounds[0] = Math.min(bounds[0], rotatedX);
                bounds[1] = Math.min(bounds[1], rotatedY);
                bounds[3] = Math.max(bounds[3], rotatedX);
                bounds[4] = Math.max(bounds[4], rotatedY);
            }
        }
        bounds[2] = Math.min(bounds[2], cube.fromZ());
        bounds[5] = Math.max(bounds[5], cube.toZ());
    }

    private static Variant variant(BlockState state) {
        if (state.is(ModBlocks.RESEARCH_GOD_STONE_8BLOCKS.get())) {
            return Variant.RESEARCH;
        }
        if (state.is(ModBlocks.WAR_GOD_STONE_8BLOCKS.get())) {
            return Variant.WAR;
        }
        if (state.is(ModBlocks.ECONOMY_GOD_STONE_8BLOCKS.get())) {
            return Variant.ECONOMY;
        }
        return null;
    }

    private static Variant variant(ItemStack stack) {
        if (stack.is(ModItems.RESEARCH_GOD_STONE_8BLOCKS.get())) {
            return Variant.RESEARCH;
        }
        if (stack.is(ModItems.WAR_GOD_STONE_8BLOCKS.get())) {
            return Variant.WAR;
        }
        if (stack.is(ModItems.ECONOMY_GOD_STONE_8BLOCKS.get())) {
            return Variant.ECONOMY;
        }
        return null;
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

    private enum Variant {
        RESEARCH("research_god_stone_8blocks"),
        // The war statue is intentionally anchored to the center of its symmetric pedestal.
        // Centering its full bounds would let the asymmetric weapons shift the pedestal off-grid.
        WAR("war_god_stone_8blocks", 0.0F, 0.0F),
        ECONOMY("economy_god_stone_8blocks");

        private final String fileName;
        private final Float worldCenterX;
        private final Float worldCenterZ;

        Variant(String fileName) {
            this(fileName, null, null);
        }

        Variant(String fileName, Float worldCenterX, Float worldCenterZ) {
            this.fileName = fileName;
            this.worldCenterX = worldCenterX;
            this.worldCenterZ = worldCenterZ;
        }

        private float worldCenterX(ModelData model) {
            return worldCenterX != null ? worldCenterX : model.centerX();
        }

        private float worldCenterZ(ModelData model) {
            return worldCenterZ != null ? worldCenterZ : model.centerZ();
        }
    }

    private record Face(float u1, float v1, float u2, float v2) {
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

    private record ModelData(
            List<ModelCube> cubes,
            ResourceLocation texture,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ
    ) {
        private static final ModelData EMPTY = new ModelData(
                List.of(),
                ResourceLocation.withDefaultNamespace("missingno"),
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F
        );

        private float centerX() {
            return (minX + maxX) * 0.5F;
        }

        private float centerZ() {
            return (minZ + maxZ) * 0.5F;
        }
    }
}
