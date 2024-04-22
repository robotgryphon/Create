package dev.compactmods.gander.outliner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.compactmods.gander.client.render.RenderBufferHelper;
import net.minecraft.client.renderer.MultiBufferSource;

import net.minecraft.world.item.DyeColor;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.compactmods.gander.client.render.RenderTypes;
import dev.compactmods.gander.utility.Iterate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.Vec3;

public class BlockClusterOutline extends Outline {

	private final Cluster cluster;

	protected final Vector3f originTemp = new Vector3f();

	public BlockClusterOutline(Iterable<BlockPos> positions) {
		cluster = new Cluster();
		positions.forEach(cluster::include);
	}

	@Override
	public void render(PoseStack ms, MultiBufferSource buffer, Vec3 camera, float pt) {
		int color = DyeColor.WHITE.getTextColor();

		int lightmap = params.lightmap;
		boolean disableLineNormals = params.disableLineNormals;

		renderEdges(ms, buffer, camera, pt, color, lightmap, disableLineNormals);
	}

	protected void renderEdges(PoseStack ms, MultiBufferSource buffer, Vec3 camera, float pt, int color, int lightmap, boolean disableNormals) {
		float lineWidth = params.getLineWidth();
		if (lineWidth == 0)
			return;
		if (cluster.isEmpty())
			return;

		ms.pushPose();
		ms.translate(cluster.anchor.getX() - camera.x, cluster.anchor.getY() - camera.y,
			cluster.anchor.getZ() - camera.z);

		PoseStack.Pose pose = ms.last();
		VertexConsumer consumer = buffer.getBuffer(RenderTypes.getOutlineSolid());

		cluster.visibleEdges.forEach(edge -> {
			BlockPos pos = edge.pos;
			Vector3f origin = originTemp;
			origin.set(pos.getX(), pos.getY(), pos.getZ());
			Direction direction = Direction.get(AxisDirection.POSITIVE, edge.axis);
			RenderBufferHelper.bufferCuboidLine(pose, consumer, origin, direction, 1, lineWidth, color, lightmap, disableNormals);
		});

		ms.popPose();
	}

	private static class Cluster {

		private BlockPos anchor;
		private final Map<MergeEntry, AxisDirection> visibleFaces;
		private final Set<MergeEntry> visibleEdges;

		public Cluster() {
			visibleEdges = new HashSet<>();
			visibleFaces = new HashMap<>();
		}

		public boolean isEmpty() {
			return anchor == null;
		}

		public void include(BlockPos pos) {
			if (anchor == null)
				anchor = pos;

			pos = pos.subtract(anchor);

			// 6 FACES
			for (Axis axis : Iterate.axes) {
				Direction direction = Direction.get(AxisDirection.POSITIVE, axis);
				for (int offset : Iterate.zeroAndOne) {
					MergeEntry entry = new MergeEntry(axis, pos.relative(direction, offset));
					if (visibleFaces.remove(entry) == null)
						visibleFaces.put(entry, offset == 0 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE);
				}
			}

			// 12 EDGES
			for (Axis axis : Iterate.axes) {
				for (Axis axis2 : Iterate.axes) {
					if (axis == axis2)
						continue;
					for (Axis axis3 : Iterate.axes) {
						if (axis == axis3)
							continue;
						if (axis2 == axis3)
							continue;

						Direction direction = Direction.get(AxisDirection.POSITIVE, axis2);
						Direction direction2 = Direction.get(AxisDirection.POSITIVE, axis3);

						for (int offset : Iterate.zeroAndOne) {
							BlockPos entryPos = pos.relative(direction, offset);
							for (int offset2 : Iterate.zeroAndOne) {
								entryPos = entryPos.relative(direction2, offset2);
								MergeEntry entry = new MergeEntry(axis, entryPos);
								if (!visibleEdges.remove(entry))
									visibleEdges.add(entry);
							}
						}
					}

					break;
				}
			}

		}

	}

	private static class MergeEntry {

		private final Axis axis;
		private final BlockPos pos;

		public MergeEntry(Axis axis, BlockPos pos) {
			this.axis = axis;
			this.pos = pos;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof MergeEntry other))
				return false;

            return this.axis == other.axis && this.pos.equals(other.pos);
		}

		@Override
		public int hashCode() {
			return this.pos.hashCode() * 31 + axis.ordinal();
		}
	}

}
