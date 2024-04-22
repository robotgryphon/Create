package dev.compactmods.gander.ponder.ui;

import com.mojang.blaze3d.platform.InputConstants;

import dev.compactmods.gander.client.gui.TickableGuiEventListener;
import dev.compactmods.gander.network.SceneDataRequest;
import dev.compactmods.gander.ponder.Scene;
import dev.compactmods.gander.ponder.SceneRaytracer;
import dev.compactmods.gander.ponder.widget.SceneRenderer;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.ClipboardManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.compactmods.gander.utility.Color;
import dev.compactmods.gander.utility.Pair;
import dev.compactmods.gander.utility.animation.LerpedFloat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import org.joml.Vector2f;

public class PonderUI extends Screen {

	private Scene scene;

	private boolean identifyMode;

	private final ClipboardManager clipboardHelper;

	protected int windowWidth, windowHeight;
	protected int windowXOffset, windowYOffset;
	protected int guiLeft, guiTop;

	protected boolean autoRotate = false;
	private SceneRenderer sceneRenderer;
	private Vector2f mainCameraRotation;

	private static final Vector2f DEFAULT_ROTATION = new Vector2f((float) Math.toRadians(-25), (float) Math.toRadians(-135));

	public PonderUI(ResourceLocation sceneID) {
		super(Component.empty());

		clipboardHelper = new ClipboardManager();

		PacketDistributor.SERVER.noArg().send(new SceneDataRequest(sceneID));
	}

	protected PonderUI(Scene scene) {
		super(Component.empty());

		this.scene = scene;
		clipboardHelper = new ClipboardManager();
	}

	public void setScene(Scene scene) {
		this.scene = scene;
		this.sceneRenderer.setScene(scene);
	}

	@Override
	protected void init() {
		super.init();

		guiLeft = (width - windowWidth) / 2;
		guiTop = (height - windowHeight) / 2;
		guiLeft += windowXOffset;
		guiTop += windowYOffset;

		Options bindings = minecraft.options;
		int bX = (width / 2) - 10;
		int bY = height - 20 - 31;

		this.sceneRenderer = this.addRenderableOnly(new SceneRenderer(width, height));
		this.sceneRenderer.setScene(scene);
		// this.sceneRenderer.shouldRenderCompass(true);
		this.mainCameraRotation = new Vector2f(DEFAULT_ROTATION);
	}

	@Override
	public void tick() {
		super.tick();

		for (GuiEventListener listener : children()) {
			if (listener instanceof TickableGuiEventListener tickable) {
				tickable.tick();
			}
		}

		if (!identifyMode && scene != null)
			scene.tick();

		if (autoRotate) {
			this.mainCameraRotation.y += Math.toRadians(2.5);
		}

		updateIdentifiedItem(scene);
	}

	public Scene getActiveScene() {
		return scene;
	}

	public void updateIdentifiedItem(Scene activeScene) {
		if (!identifyMode)
			return;

		Window w = minecraft.getWindow();
		double mouseX = minecraft.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth();
		double mouseY = minecraft.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight();

		Pair<ItemStack, BlockPos> pair = SceneRaytracer.rayTraceScene(activeScene, w.getWidth(), w.getHeight(), sceneRenderer.camera, mouseX, mouseY);
		var hoveredTooltipItem = pair.getFirst();
		var hoveredBlockPos = pair.getSecond();
	}

	@Override
	public void renderTransparentBackground(GuiGraphics pGuiGraphics) {

	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		this.sceneRenderer.prepareCamera(this.mainCameraRotation);
		super.render(graphics, mouseX, mouseY, partialTicks);

		// Chapter title
		RenderSystem.enableBlend();
		RenderSystem.disableDepthTest();

		boolean noWidgetsHovered = true;
		for (GuiEventListener child : children())
			noWidgetsHovered &= !child.isMouseOver(mouseX, mouseY);

		int tooltipColor = Color.WHITE.getRGB();

		RenderSystem.enableDepthTest();
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

		if (code == InputConstants.KEY_I) {
			this.identifyMode = !identifyMode;
			return true;
		}

		return super.keyPressed(code, scanCode, modifiers);
	}

	public Font getFontRenderer() {
		return font;
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	public void drawRightAlignedString(GuiGraphics graphics, PoseStack ms, String string, int x, int y, int color) {
		graphics.drawString(font, string, (float) (x - font.width(string)), (float) y, color, false);
	}
}
