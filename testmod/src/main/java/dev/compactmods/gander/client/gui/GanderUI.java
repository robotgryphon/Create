package dev.compactmods.gander.client.gui;

import com.mojang.blaze3d.platform.InputConstants;

import dev.compactmods.gander.network.SceneDataRequest;
import dev.compactmods.gander.client.gui.widget.SpatialRenderer;
import dev.compactmods.gander.level.BoundedBlockAndTintGetter;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.PacketDistributor;

import org.joml.Vector2f;

public class GanderUI extends Screen {

	protected boolean autoRotate = false;

	private SpatialRenderer sceneRenderer;
	private Vector2f mainCameraRotation;

	private static final Vector2f DEFAULT_ROTATION = new Vector2f((float) Math.toRadians(-25), (float) Math.toRadians(-135));

	public GanderUI(ResourceLocation sceneID) {
		super(Component.empty());
		PacketDistributor.SERVER.noArg().send(new SceneDataRequest(sceneID));
	}

	@Override
	protected void init() {
		super.init();

		this.sceneRenderer = this.addRenderableOnly(new SpatialRenderer(width, height));
		// this.sceneRenderer.shouldRenderCompass(true);
		this.mainCameraRotation = new Vector2f(DEFAULT_ROTATION);
	}

	@Override
	public void tick() {
		super.tick();
		// sceneRenderer.tick();

		if (autoRotate) {
			this.mainCameraRotation.y += Math.toRadians(2.5);
		}
	}

	@Override
	public void renderTransparentBackground(GuiGraphics pGuiGraphics) {

	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		this.sceneRenderer.prepareCamera(this.mainCameraRotation);
		super.render(graphics, mouseX, mouseY, partialTicks);
	}

	@Override
	public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
		this.sceneRenderer.scale(pScrollY);
		return true;
	}

	@Override
	public boolean keyPressed(int code, int scanCode, int modifiers) {
		Options settings = Minecraft.getInstance().options;

		final var rads = 1 / 12f;

		if (code == InputConstants.KEY_A) {
			this.autoRotate = !autoRotate;
			return true;
		}

		if (code == InputConstants.KEY_R) {
			this.mainCameraRotation.set(DEFAULT_ROTATION);
			return true;
		}

		if (code == InputConstants.KEY_UP) {
			if (this.mainCameraRotation.x < -rads)
				this.mainCameraRotation.x += rads;

			return true;
		}

		if (code == InputConstants.KEY_DOWN) {
			if (this.mainCameraRotation.x > -(Math.PI / 2) + (rads * 2))
				this.mainCameraRotation.x -= rads;
			return true;
		}

		if (code == InputConstants.KEY_LEFT) {
			this.mainCameraRotation.y += rads;
			return true;
		}

		if (code == InputConstants.KEY_RIGHT) {
			this.mainCameraRotation.y -= rads;
			return true;
		}

		return super.keyPressed(code, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public void setScene(BoundedBlockAndTintGetter scene) {
		this.sceneRenderer.setData(scene);
	}
}