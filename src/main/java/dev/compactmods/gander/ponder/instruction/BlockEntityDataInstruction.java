package dev.compactmods.gander.ponder.instruction;

import java.util.function.UnaryOperator;

import dev.compactmods.gander.SyncedBlockEntity;
import dev.compactmods.gander.ponder.PonderScene;
import dev.compactmods.gander.ponder.PonderLevel;
import dev.compactmods.gander.ponder.Selection;

import dev.compactmods.gander.ponder.instruction.contract.WorldModifyInstruction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BlockEntityDataInstruction extends WorldModifyInstruction {

	private final boolean redraw;
	private final UnaryOperator<CompoundTag> data;
	private final Class<? extends BlockEntity> type;

	public BlockEntityDataInstruction(Selection selection, Class<? extends BlockEntity> type,
		UnaryOperator<CompoundTag> data, boolean redraw) {
		super(selection);
		this.type = type;
		this.data = data;
		this.redraw = redraw;
	}

	@Override
	protected void runModification(Selection selection, PonderScene scene) {
		PonderLevel world = scene.getWorld();
		selection.forEach(pos -> {
			if (!world.getBounds()
				.isInside(pos))
				return;
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (!type.isInstance(blockEntity))
				return;
			CompoundTag apply = data.apply(blockEntity.saveWithFullMetadata());
			if (blockEntity instanceof SyncedBlockEntity)
				((SyncedBlockEntity) blockEntity).readClient(apply);
			blockEntity.load(apply);
		});
	}

	@Override
	protected boolean needsRedraw() {
		return redraw;
	}

}
