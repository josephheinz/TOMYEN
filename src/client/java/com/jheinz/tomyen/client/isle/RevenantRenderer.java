package com.jheinz.tomyen.client.isle;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.isle.Revenant;

/**
 * Draws a Revenant as its owner: the player model (slim or wide, as their skin is) in their own skin, tinted a cold
 * blue-grey and see-through, with the gear it died in worn and held as a player wears and holds it.
 *
 * <p>Over the body: soul-fire eyes (a glow layer on the head's own cubes, so nothing fights the skin) and a slow,
 * swirling soul-light shell a little larger than the body (its own inflated model, at a size no other layer uses, so
 * it never shares a plane with the skin, its outer layer or the armor). It rises out of the ground as it forms,
 * fading in; the deeper it lies the colder and fainter it is, and the brighter its eyes.
 */
public class RevenantRenderer extends LivingEntityRenderer<Revenant, AvatarRenderState, PlayerModel> {
	public static final ModelLayerLocation AURA = new ModelLayerLocation(MyMod.id("revenant"), "aura");
	public static final ModelLayerLocation AURA_SLIM = new ModelLayerLocation(MyMod.id("revenant"), "aura_slim");
	/** Between the jacket's 0.25 and the hat's 0.5 below it and the outer armor's 1.0 above. */
	private static final CubeDeformation AURA_INFLATE = new CubeDeformation(0.7f);
	private static final Identifier EYES_TEXTURE = MyMod.id("textures/entity/revenant/revenant_eyes.png");
	private static final Identifier AURA_TEXTURE = MyMod.id("textures/entity/revenant/revenant_aura.png");
	/**
	 * Translucent and full-bright rather than vanilla's additive eyes: drawn in the same translucent pass as the see-through
	 * body, just after it, so the body never ends up painted over them.
	 */
	private static final RenderType EYES = RenderTypes.entityTranslucentEmissive(EYES_TEXTURE);
	/** The ghostly tint at each depth: the Shore's grey-blue, the Hollows' colder teal, the Abyss's near-black indigo. */
	private static final float[][] TINT = {{0.55f, 0.72f, 0.92f}, {0.47f, 0.7f, 0.84f}, {0.42f, 0.5f, 0.84f}};
	private static final float[] ALPHA = {0.62f, 0.56f, 0.5f};

	private final PlayerModel wide;
	private final PlayerModel slim;
	private final PlayerModel wideAura;
	private final PlayerModel slimAura;
	private final PlayerSkinRenderCache skins;

	public RevenantRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
		wide = model;
		slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		wideAura = new AuraModel(context.bakeLayer(AURA), false);
		slimAura = new AuraModel(context.bakeLayer(AURA_SLIM), true);
		skins = context.getPlayerSkinRenderCache();
		addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), part -> new PlayerModel(part, false)),
				context.getEquipmentRenderer()));
		addLayer(new PlayerItemInHandLayer<>(this));
		addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
		addLayer(new EyesLayer(this));
		addLayer(new AuraLayer(this));
	}

	public static LayerDefinition createAura(boolean slimArms) {
		return LayerDefinition.create(PlayerModel.createMesh(AURA_INFLATE, slimArms), 64, 64);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new RevenantRenderState();
	}

	@Override
	public void extractRenderState(Revenant revenant, AvatarRenderState avatar, float partialTicks) {
		super.extractRenderState(revenant, avatar, partialTicks);
		RevenantRenderState state = (RevenantRenderState) avatar;
		HumanoidMobRenderer.extractHumanoidRenderState(revenant, state, partialTicks, itemModelResolver);
		state.skin = skins.getOrDefault(revenant.profile()).playerSkin();
		state.slim = state.skin.model() == PlayerModelType.SLIM;
		state.leftArmPose = armPose(revenant, HumanoidArm.LEFT);
		state.rightArmPose = armPose(revenant, HumanoidArm.RIGHT);
		state.id = revenant.getId();
		state.depth = revenant.depth().index;
		state.forming = revenant.forming(partialTicks);
		float flicker = 0.94f + 0.06f * Mth.sin(state.ageInTicks * 0.11f + revenant.getId());
		state.alpha = ALPHA[state.depth] * flicker * (1.0f - state.forming * 0.85f);
	}

	private static HumanoidModel.ArmPose armPose(Revenant revenant, HumanoidArm arm) {
		boolean main = revenant.getMainArm() == arm;
		ItemStack stack = main ? revenant.getMainHandItem() : revenant.getOffhandItem();
		ItemStack mainItem = revenant.getMainHandItem();
		if (!main && (mainItem.getItem() instanceof GunItem || mainItem.getItem() instanceof CrossbowItem)) {
			// Two-handed weapons take the off arm too.
			return stack.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		}
		if (stack.isEmpty()) {
			return HumanoidModel.ArmPose.EMPTY;
		}
		if (stack.getItem() instanceof GunItem) {
			return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		}
		if (main && revenant.isUsingItem()) {
			ItemUseAnimation use = stack.getUseAnimation();
			if (use == ItemUseAnimation.BOW) {
				return HumanoidModel.ArmPose.BOW_AND_ARROW;
			}
			if (use == ItemUseAnimation.CROSSBOW) {
				return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
			}
		}
		if (stack.getItem() instanceof CrossbowItem) {
			return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		}
		return HumanoidModel.ArmPose.ITEM;
	}

	@Override
	public void submit(AvatarRenderState avatar, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		RevenantRenderState state = (RevenantRenderState) avatar;
		model = state.slim ? slim : wide;
		poseStack.pushPose();
		// Forming: it rises out of the ground.
		poseStack.translate(0.0f, -1.6f * state.forming * state.forming, 0.0f);
		super.submit(avatar, poseStack, collector, camera);
		poseStack.popPose();
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return state.skin.body().texturePath();
	}

	@Override
	protected @Nullable RenderType getRenderType(AvatarRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
		Identifier texture = getTextureLocation(state);
		if (!bodyVisible && !forceTransparent) {
			return glowing ? RenderTypes.outline(texture) : null;
		}
		return RenderTypes.entityTranslucent(texture);
	}

	@Override
	protected int getModelTint(AvatarRenderState avatar) {
		RevenantRenderState state = (RevenantRenderState) avatar;
		float[] tint = TINT[Mth.clamp(state.depth, 0, 2)];
		return ARGB.colorFromFloat(state.alpha, tint[0], tint[1], tint[2]);
	}

	@Override
	protected float getShadowRadius(AvatarRenderState state) {
		return super.getShadowRadius(state) * 0.6f * (1.0f - ((RevenantRenderState) state).forming);
	}

	@Override
	protected void scale(AvatarRenderState state, PoseStack poseStack) {
		poseStack.scale(0.9375f, 0.9375f, 0.9375f);
	}

	@Override
	protected boolean shouldShowName(Revenant revenant, double distanceToCameraSq) {
		return false;
	}

	/** The eyes: soul fire in the sockets, brighter the deeper it lies. */
	private static class EyesLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		EyesLayer(RevenantRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState avatar, float yRot, float xRot) {
			RevenantRenderState state = (RevenantRenderState) avatar;
			if (state.isInvisible) {
				return;
			}
			float glow = (0.8f + 0.1f * state.depth) * (1.0f - state.forming);
			collector.order(1).submitModel(getParentModel(), state, poseStack, EYES, light, OverlayTexture.NO_OVERLAY,
					ARGB.colorFromFloat(glow, 1.0f, 1.0f, 1.0f), null, state.outlineColor);
		}
	}

	/** A slow swirl of soul light round the body. */
	private class AuraLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		AuraLayer(RevenantRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState avatar, float yRot, float xRot) {
			RevenantRenderState state = (RevenantRenderState) avatar;
			if (state.isInvisible) {
				return;
			}
			PlayerModel aura = state.slim ? slimAura : wideAura;
			float t = state.ageInTicks;
			float strength = (0.5f + 0.12f * state.depth) * (1.0f - state.forming);
			collector.order(2).submitModel(aura, state, poseStack, RenderTypes.energySwirl(AURA_TEXTURE, t * 0.004f % 1.0f, t * 0.009f % 1.0f),
					light, OverlayTexture.NO_OVERLAY, ARGB.colorFromFloat(1.0f, strength, strength, strength), null, state.outlineColor);
		}
	}

	/**
	 * The shell: the body's own cubes, inflated, without the outer skin layer's (whose inflated sizes would come within a
	 * hair of the armor's).
	 */
	private static final class AuraModel extends PlayerModel {
		AuraModel(net.minecraft.client.model.geom.ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			hat.visible = false;
			jacket.visible = false;
			leftSleeve.visible = false;
			rightSleeve.visible = false;
			leftPants.visible = false;
			rightPants.visible = false;
		}
	}
}
