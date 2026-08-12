package com.geydev.kalfactions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class FaithRenderTypes {
    private static final RenderStateShard.DepthTestStateShard ALWAYS_VISIBLE = new AlwaysVisibleDepthShard();

    public static final RenderType SEE_THROUGH_LINES = RenderType.create(
            "kingdoms_faith_see_through_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0D)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(ALWAYS_VISIBLE)
                    .createCompositeState(false)
    );

    private static final class AlwaysVisibleDepthShard extends RenderStateShard.DepthTestStateShard {
        private AlwaysVisibleDepthShard() {
            super("kingdoms_always_visible", 519);
        }

        @Override
        public void setupRenderState() {
            RenderSystem.disableDepthTest();
            RenderSystem.depthFunc(519);
        }

        @Override
        public void clearRenderState() {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
        }
    }

    private FaithRenderTypes() {
    }
}
