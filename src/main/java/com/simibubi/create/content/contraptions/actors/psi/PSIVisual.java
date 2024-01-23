package com.simibubi.create.content.contraptions.actors.psi;

import java.util.function.Consumer;

import com.jozufozu.flywheel.api.instance.Instance;
import com.jozufozu.flywheel.api.visual.DynamicVisual;
import com.jozufozu.flywheel.api.visual.TickableVisual;
import com.jozufozu.flywheel.api.visual.VisualFrameContext;
import com.jozufozu.flywheel.api.visual.VisualTickContext;
import com.jozufozu.flywheel.api.visualization.VisualizationContext;
import com.jozufozu.flywheel.lib.visual.AbstractBlockEntityVisual;
import com.simibubi.create.foundation.utility.AnimationTickHolder;

public class PSIVisual extends AbstractBlockEntityVisual<PortableStorageInterfaceBlockEntity> implements DynamicVisual, TickableVisual {

	private final PIInstance instance;

	public PSIVisual(VisualizationContext visualizationContext, PortableStorageInterfaceBlockEntity blockEntity) {
		super(visualizationContext, blockEntity);

		instance = new PIInstance(visualizationContext.instancerProvider(), blockState, getVisualPosition());
	}

	@Override
	public void init(float pt) {
		instance.init(isLit());
		super.init(pt);
	}

	@Override
	public void tick(VisualTickContext ctx) {
		instance.tick(isLit());
	}

	@Override
	public void beginFrame(VisualFrameContext ctx) {
		instance.beginFrame(blockEntity.getExtensionDistance(AnimationTickHolder.getPartialTicks()));
	}

	@Override
	public void updateLight() {
		relight(pos, instance.middle, instance.top);
	}

	@Override
	protected void _delete() {
		instance.remove();
	}

	private boolean isLit() {
		return blockEntity.isConnected();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		instance.collectCrumblingInstances(consumer);
	}
}