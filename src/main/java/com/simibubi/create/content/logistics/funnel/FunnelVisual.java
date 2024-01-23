package com.simibubi.create.content.logistics.funnel;

import java.util.ArrayList;
import java.util.function.Consumer;

import com.jozufozu.flywheel.api.event.RenderStage;
import com.jozufozu.flywheel.api.instance.Instance;
import com.jozufozu.flywheel.api.instance.Instancer;
import com.jozufozu.flywheel.api.visual.DynamicVisual;
import com.jozufozu.flywheel.api.visual.VisualFrameContext;
import com.jozufozu.flywheel.api.visualization.VisualizationContext;
import com.jozufozu.flywheel.lib.instance.AbstractInstance;
import com.jozufozu.flywheel.lib.model.Models;
import com.jozufozu.flywheel.lib.model.baked.PartialModel;
import com.jozufozu.flywheel.lib.visual.AbstractBlockEntityVisual;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.flwdata.FlapInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import com.simibubi.create.foundation.utility.AnimationTickHolder;

import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;

public class FunnelVisual extends AbstractBlockEntityVisual<FunnelBlockEntity> implements DynamicVisual {

	private final ArrayList<FlapInstance> flaps;

    public FunnelVisual(VisualizationContext context, FunnelBlockEntity blockEntity) {
        super(context, blockEntity);

        flaps = new ArrayList<>(4);

        if (!blockEntity.hasFlap()) return;

		PartialModel flapPartial = (blockState.getBlock() instanceof FunnelBlock ? AllPartialModels.FUNNEL_FLAP
				: AllPartialModels.BELT_FUNNEL_FLAP);
        Instancer<FlapInstance> model = instancerProvider.instancer(AllInstanceTypes.FLAPS, Models.partial(flapPartial), RenderStage.AFTER_BLOCK_ENTITIES);

        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(LightLayer.SKY, pos);

        Direction direction = FunnelBlock.getFunnelFacing(blockState);

        float flapness = blockEntity.flap.getValue(AnimationTickHolder.getPartialTicks());
        float horizontalAngle = direction.getOpposite().toYRot();

        for (int segment = 0; segment <= 3; segment++) {
            float intensity = segment == 3 ? 1.5f : segment + 1;
            float segmentOffset = -3.05f / 16f * segment + 0.075f / 16f;

            FlapInstance key = model.createInstance();

            key.setPosition(getVisualPosition())
               .setSegmentOffset(segmentOffset, 0, -blockEntity.getFlapOffset())
               .setBlockLight(blockLight)
               .setSkyLight(skyLight)
               .setHorizontalAngle(horizontalAngle)
               .setFlapness(flapness)
               .setFlapScale(-1)
               .setPivotVoxelSpace(0, 10, 9.5f)
               .setIntensity(intensity);

            flaps.add(key);
        }
    }

    @Override
    public void beginFrame(VisualFrameContext ctx) {
        if (flaps == null) return;

        float flapness = blockEntity.flap.getValue(AnimationTickHolder.getPartialTicks());

        for (FlapInstance flap : flaps) {
            flap.setFlapness(flapness);
        }
    }

    @Override
    public void updateLight() {
        if (flaps != null)
            relight(pos, flaps.stream());
    }

    @Override
    protected void _delete() {
        if (flaps == null) return;

        flaps.forEach(AbstractInstance::delete);
    }

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		if (flaps == null) return;

		flaps.forEach(consumer);
	}
}