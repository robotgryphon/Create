package com.simibubi.create.content.contraptions.bearing;

import org.joml.Quaternionf;

import com.jozufozu.flywheel.api.event.RenderStage;
import com.jozufozu.flywheel.api.visualization.VisualizationContext;
import com.jozufozu.flywheel.lib.instance.InstanceTypes;
import com.jozufozu.flywheel.lib.instance.OrientedInstance;
import com.jozufozu.flywheel.lib.model.Models;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class StabilizedBearingVisual extends ActorVisual {

	final OrientedInstance topInstance;
	final RotatingInstance shaft;

	final Direction facing;
	final Axis rotationAxis;
	final Quaternionf blockOrientation;

	public StabilizedBearingVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld, MovementContext movementContext) {
		super(visualizationContext, simulationWorld, movementContext);

		BlockState blockState = movementContext.state;

		facing = blockState.getValue(BlockStateProperties.FACING);
		rotationAxis = Axis.of(Direction.get(Direction.AxisDirection.POSITIVE, facing.getAxis()).step());

		blockOrientation = BearingVisual.getBlockStateOrientation(facing);

        topInstance = instancerProvider.instancer(InstanceTypes.ORIENTED, Models.partial(AllPartialModels.BEARING_TOP), RenderStage.AFTER_BLOCK_ENTITIES)
				.createInstance();

		int blockLight = localBlockLight();
		topInstance.setPosition(movementContext.localPos)
				.setRotation(blockOrientation)
				.setBlockLight(blockLight);

		shaft = instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF, blockState.getValue(BlockStateProperties.FACING).getOpposite()), RenderStage.AFTER_BLOCK_ENTITIES)
				.createInstance();

		// not rotating so no need to set speed, axis, etc.
		shaft.setPosition(movementContext.localPos)
				.setBlockLight(blockLight);
	}

	@Override
	public void beginFrame() {
		float counterRotationAngle = StabilizedBearingMovementBehaviour.getCounterRotationAngle(context, facing, AnimationTickHolder.getPartialTicks());

		Quaternionf rotation = rotationAxis.rotationDegrees(counterRotationAngle);

		rotation.mul(blockOrientation);

		topInstance.setRotation(rotation);
	}
}