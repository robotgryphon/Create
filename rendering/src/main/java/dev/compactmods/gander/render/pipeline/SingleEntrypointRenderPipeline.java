package dev.compactmods.gander.render.pipeline;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;

import org.joml.Matrix4f;

import java.util.Set;

public record SingleEntrypointRenderPipeline<TCtx>(Set<PipelineLifecyclePhase<TCtx>> setupPhases,
                                                   Set<PipelineLifecyclePhase<TCtx>> cleanupPhases,
                                                   Set<PipelineGeometryUploadPhase<TCtx>> geometryPhases)
    implements SinglePassRenderPipeline<TCtx> {

    @Override
    public PipelineState setup(TCtx ctx, Camera camera) {
        PipelineState state = new PipelineState();

        for(var phase : setupPhases)
            phase.run(state, ctx, camera);

        return state;
    }

    @Override
    public void render(PipelineState state, TCtx ctx, GuiGraphics graphics, Camera camera, PoseStack poseStack, Matrix4f projectionMatrix) {
        for(var phase : geometryPhases)
            phase.upload(state, ctx, graphics, camera, poseStack, projectionMatrix);

        for(var phase : cleanupPhases)
            phase.run(state, ctx, camera);
    }
}