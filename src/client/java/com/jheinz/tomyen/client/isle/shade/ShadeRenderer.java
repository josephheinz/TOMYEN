package com.jheinz.tomyen.client.isle.shade;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.isle.shade.Shade;

/**
 * Draws a shade as the mob it was, with that mob's own renderer: a client-only "puppet" of the mob's type (never added
 * to the world) stands in for it, posed every frame from the shade (position, turn, head, walk cycle, hurt flash,
 * dying fall, gear and aggression), and is drawn through a {@link GhostSubmitCollector} that turns it see-through,
 * tinted and faintly glowing. So any mob, the mod's and other mods' included, comes back looking like itself.
 *
 * <p>The tint says what it is: tide shades a pale sea-green and faint; echoes the Revenant's cold blue, colder and
 * darker the deeper, more solid the fresher their record; a boss's shade violet and the most solid; a pet's a warm,
 * pale gold. Each bobs a little, flickers, and rises out of the ground as it forms.
 *
 * <p>A type whose renderer can't draw a puppet (it throws) is remembered and drawn as a wisp of soul fire instead, the
 * size of its hitbox, its name over it.
 */
public class ShadeRenderer extends EntityRenderer<Shade, ShadeRenderState> {
	private static final float[][] ECHO_TINT = {{0.62f, 0.8f, 0.96f}, {0.52f, 0.74f, 0.88f}, {0.46f, 0.52f, 0.92f}};
	private static final float[] TIDE_TINT = {0.55f, 0.9f, 0.84f};
	private static final float[] BOSS_TINT = {0.8f, 0.6f, 1.0f};
	private static final float[] PET_TINT = {1.0f, 0.93f, 0.74f};
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private static final Set<EntityType<?>> BROKEN = new HashSet<>();

	private static final class Puppet {
		final Entity entity;
		int lastTick = -1;

		Puppet(Entity entity) {
			this.entity = entity;
		}
	}

	private final Map<Shade, Puppet> puppets = new WeakHashMap<>();

	public ShadeRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.0f;
	}

	@Override
	public ShadeRenderState createRenderState() {
		return new ShadeRenderState();
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void extractRenderState(Shade shade, ShadeRenderState state, float partialTicks) {
		super.extractRenderState(shade, state, partialTicks);
		state.forming = shade.forming(partialTicks);
		state.scale = shade.mimicScale();
		state.bob = 0.12f + 0.06f * Mth.sin((shade.tickCount + partialTicks) * 0.08f + shade.getId());
		state.wispSize = Math.max(0.5f, Math.min(shade.getBbWidth(), shade.getBbHeight()));
		state.tint = tint(shade, state.ageInTicks);
		state.inner = null;
		state.innerRenderer = null;
		EntityType<?> type = shade.mimicType();
		if (type == null || BROKEN.contains(type)) {
			return;
		}
		try {
			Puppet puppet = puppets.get(shade);
			if (puppet == null || puppet.entity.getType() != type) {
				Entity entity = type.create(shade.level(), EntitySpawnReason.LOAD);
				if (entity == null) {
					BROKEN.add(type);
					return;
				}
				puppet = new Puppet(entity);
				puppets.put(shade, puppet);
			}
			pose(shade, puppet);
			EntityRenderer renderer = entityRenderDispatcher.getRenderer(puppet.entity);
			if (renderer == null || renderer instanceof ShadeRenderer) {
				BROKEN.add(type);
				return;
			}
			state.inner = renderer.createRenderState(puppet.entity, partialTicks);
			state.innerRenderer = renderer;
		} catch (Throwable t) {
			BROKEN.add(type);
			MyMod.LOGGER.warn("Isle of the Damned: can't draw a shade of {} as itself, so it'll be a wisp: {}", type, t.toString());
		}
	}

	/** Poses the puppet as the shade stands now. */
	private static void pose(Shade shade, Puppet puppet) {
		Entity entity = puppet.entity;
		entity.setPos(shade.getX(), shade.getY(), shade.getZ());
		entity.xo = shade.xo;
		entity.yo = shade.yo;
		entity.zo = shade.zo;
		entity.xOld = shade.xOld;
		entity.yOld = shade.yOld;
		entity.zOld = shade.zOld;
		entity.setYRot(shade.getYRot());
		entity.yRotO = shade.yRotO;
		entity.setXRot(shade.getXRot());
		entity.xRotO = shade.xRotO;
		entity.tickCount = shade.tickCount;
		entity.setOnGround(shade.onGround());
		if (entity instanceof LivingEntity living) {
			living.yBodyRot = shade.yBodyRot;
			living.yBodyRotO = shade.yBodyRotO;
			living.yHeadRot = shade.yHeadRot;
			living.yHeadRotO = shade.yHeadRotO;
			living.hurtTime = shade.hurtTime;
			living.deathTime = shade.deathTime;
			// The walk cycle: stepped once for each tick the shade has lived since last time, at the shade's own speed.
			if (puppet.lastTick < 0 || shade.tickCount - puppet.lastTick > 20) {
				puppet.lastTick = shade.tickCount - 1;
			}
			while (puppet.lastTick < shade.tickCount) {
				living.walkAnimation.update(shade.walkAnimation.speed(), 1.0f, 1.0f);
				puppet.lastTick++;
			}
			for (EquipmentSlot slot : SLOTS) {
				if (living.getItemBySlot(slot) != shade.getItemBySlot(slot)) {
					living.setItemSlot(slot, shade.getItemBySlot(slot));
				}
			}
		}
		if (entity instanceof Mob mob) {
			mob.setAggressive(shade.isAggressive());
		}
	}

	private static int tint(Shade shade, float age) {
		float[] rgb;
		float alpha;
		switch (shade.kind()) {
			case TIDE -> {
				rgb = TIDE_TINT;
				alpha = 0.4f;
			}
			case BOSS -> {
				rgb = BOSS_TINT;
				alpha = 0.7f;
			}
			case PET -> {
				rgb = PET_TINT;
				alpha = 0.5f;
			}
			default -> {
				rgb = ECHO_TINT[Mth.clamp(shade.depth().index, 0, 2)];
				alpha = 0.3f + 0.3f * shade.vividness();
			}
		}
		float flicker = 0.92f + 0.08f * Mth.sin(age * 0.13f + shade.getId() * 1.7f);
		alpha *= flicker * (1.0f - shade.forming(0.0f) * 0.9f);
		return ARGB.colorFromFloat(Mth.clamp(alpha, 0.0f, 1.0f), rgb[0], rgb[1], rgb[2]);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void submit(ShadeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		float height = state.boundingBoxHeight;
		poseStack.translate(0.0f, state.bob - height * 1.1f * state.forming * state.forming, 0.0f);
		EntityRenderState inner = state.inner;
		EntityRenderer renderer = state.innerRenderer;
		boolean drawn = false;
		if (inner != null && renderer != null) {
			try {
				poseStack.pushPose();
				poseStack.scale(state.scale, state.scale, state.scale);
				Vec3 offset = renderer.getRenderOffset(inner);
				poseStack.translate(offset.x, offset.y, offset.z);
				renderer.submit(inner, poseStack, new GhostSubmitCollector(collector, state.tint), camera);
				poseStack.popPose();
				drawn = true;
			} catch (Throwable t) {
				BROKEN.add(inner.entityType);
				MyMod.LOGGER.warn("Isle of the Damned: can't draw a shade of {} as itself, so it'll be a wisp: {}", inner.entityType, t.toString());
			}
		}
		if (!drawn) {
			poseStack.pushPose();
			poseStack.translate(0.0f, height * 0.5f, 0.0f);
			SoulWispRenderer.submitWisp(poseStack, collector, camera, state.ageInTicks, state.wispSize * 1.6f, state.tint);
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
