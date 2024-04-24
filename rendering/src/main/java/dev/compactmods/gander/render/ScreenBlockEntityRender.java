package dev.compactmods.gander.render;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.fml.loading.FMLEnvironment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.stream.Stream;

public class ScreenBlockEntityRender {

	private static final Logger LOGS = LogManager.getLogger();

	public static void render(BlockAndTintGetter world, Stream<BlockEntity> resolver, PoseStack ms, MultiBufferSource buffer, float pt) {
		render(world, resolver, ms, null, buffer, pt);
	}

	public static void render(BlockAndTintGetter world, Stream<BlockEntity> resolver, PoseStack ms, @Nullable Matrix4f lightTransform, MultiBufferSource buffer, float pt) {
		resolver.forEach(ent -> render(world, ent, ms, lightTransform, buffer, pt));
	}

	public static void render(BlockAndTintGetter world, BlockEntity blockEntity, PoseStack ms, MultiBufferSource buffer, float pt) {
		render(world, blockEntity, ms, null, buffer, pt);
	}

	public static void render(BlockAndTintGetter world, BlockEntity blockEntity, PoseStack ms, @Nullable Matrix4f lightTransform, MultiBufferSource buffer, float pt) {
		BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
		if (renderer == null)
			return;

		BlockPos pos = blockEntity.getBlockPos();
		ms.pushPose();
		ms.translate(pos.getX(), pos.getY(), pos.getZ());

		try {
			BlockPos worldPos = getLightPos(lightTransform, pos);
			int worldLight = LevelRenderer.getLightColor(world, worldPos);

			renderer.render(blockEntity, pt, ms, buffer, worldLight, OverlayTexture.NO_OVERLAY);

		} catch (Exception e) {
			String message = "BlockEntity " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()) + " could not be rendered virtually.";
			if (FMLEnvironment.production)
				LOGS.error(message, e);
			else
				LOGS.error(message);
		}

		ms.popPose();
	}

	private static BlockPos getLightPos(@Nullable Matrix4f lightTransform, BlockPos pos) {
		if (lightTransform != null) {
			Vector4f lightVec = new Vector4f(pos.getX() + .5f, pos.getY() + .5f, pos.getZ() + .5f, 1);
			lightVec.mul(lightTransform);
			return BlockPos.containing(lightVec.x(), lightVec.y(), lightVec.z());
		} else {
			return pos;
		}
	}

	private static int maxLight(int packedLight1, int packedLight2) {
		int blockLight1 = LightTexture.block(packedLight1);
		int skyLight1 = LightTexture.sky(packedLight1);
		int blockLight2 = LightTexture.block(packedLight2);
		int skyLight2 = LightTexture.sky(packedLight2);
		return LightTexture.pack(Math.max(blockLight1, blockLight2), Math.max(skyLight1, skyLight2));
	}

}