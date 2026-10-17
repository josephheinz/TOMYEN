package com.jheinz.tomyen.schnozzle;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.ModNetworking;
import com.jheinz.tomyen.MyMod;

/**
 * A whip braided from Schnozzle nose hair, with a Giant Booger for a grip.
 *
 * <p>Attacking lashes out to {@value #ATTACK_REACH} blocks (vanilla's attack handles the hit, through the
 * {@code attack_range} component, so Sharpness, Knockback, Fire Aspect and crits all apply) with a loud crack at the
 * tip; a miss cracks too, if the swing was charged. Using it snags whatever is in sight within {@value #SNAG_RANGE}
 * blocks: a creature is yanked over to you and slowed, a dropped item flies to you, and a block yanks you toward it,
 * with no fall damage for that flight. Every hit has a 15% chance ({@link #SLIME_CHANCE}) of leaving the target
 * {@link Schnozzles#SLIMED Slimed}.
 */
public class NoseHairWhipItem extends Item {
	public static final float ATTACK_REACH = 6.0f;
	public static final double SNAG_RANGE = 10.0;
	public static final int DURABILITY = 400;
	/** Iron sword damage (6 with the player's base 1) at a slightly quicker 1.8 swings a second. */
	private static final float ATTACK_DAMAGE = 5.0f;
	private static final float ATTACK_SPEED = -2.2f;
	private static final float KNOCKBACK = 0.8f;
	/**
	 * The attack swing: a little slower than the default, and different from the use swing, so the client can tell a lash
	 * (drawn flicking out and back over the swing, see client/schnozzle/WhipLashes) from a snag.
	 */
	public static final SwingAnimation ATTACK_SWING = new SwingAnimation(SwingAnimationType.WHACK, 8);
	private static final int SNAG_COOLDOWN = 30;
	/** A missed swing only cracks if it was at least this charged, so spam-clicking stays quiet. */
	private static final float MISS_CRACK_CHARGE = 0.6f;

	/** Yanked creatures: speed toward you per block away (before the cap) and the lift that gets them off the ground. */
	private static final double PULL_PER_BLOCK = 0.14;
	private static final double PULL_MAX = 1.4;
	private static final double PULL_LIFT = 0.42;
	private static final int PULL_SLOWNESS_TICKS = 30;
	/** Yanking yourself to a block: base speed, more per block away, capped. */
	private static final double YANK_BASE = 0.6;
	private static final double YANK_PER_BLOCK = 0.12;
	private static final double YANK_MAX = 1.6;
	private static final double YANK_LIFT = 0.25;

	public static final float SLIME_CHANCE = 0.15f;
	private static final Identifier LASH_TEXTURE = MyMod.id("textures/entity/nose_hair_whip_lash.png");
	private static final int SLIMED_TICKS = 60;

	public NoseHairWhipItem(Properties properties) {
		super(properties);
	}

	/** Durability, nose hair repairs, reach, damage, speed and knockback, and a slower swing for the lash to play out. */
	public static Properties properties() {
		return new Properties()
				.durability(DURABILITY)
				.repairable(Schnozzles.NOSE_HAIR)
				.enchantable(15)
				.component(DataComponents.ATTACK_RANGE, new AttackRange(0.0f, ATTACK_REACH, 0.0f, ATTACK_REACH + 0.5f, 0.2f, 1.0f))
				.component(DataComponents.ATTACK_ANIMATION, ATTACK_SWING)
				.component(DataComponents.WEAPON, new Weapon(1))
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(MyMod.id("nose_hair_whip_knockback"), KNOCKBACK,
								AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
						.build());
	}

	/** How far an attack lashes out (the {@code attack_range} component should match); a longer whip overrides it. */
	public float attackReach() {
		return ATTACK_REACH;
	}

	/** How far a snag reaches. */
	public double snagRange() {
		return SNAG_RANGE;
	}

	/** The lash's ribbon texture (client/schnozzle/WhipLashes). */
	public Identifier lashTexture() {
		return LASH_TEXTURE;
	}

	public static boolean isHeldBy(Player player) {
		return player.getMainHandItem().getItem() instanceof NoseHairWhipItem;
	}

	/** Called on the server for every swing (see mixin/ServerPunchWhipMixin), before the swing resets the charge. */
	public void swung(ServerLevel level, Player player) {
		// A hit has already reset the charge, and cracked in hurtEnemy.
		if (player.getAttackStrengthScale(0.5f) < MISS_CRACK_CHARGE) {
			return;
		}
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getViewVector(1.0f).scale(attackReach()));
		crack(level, level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).getLocation());
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
		if (mob.level() instanceof ServerLevel level) {
			crack(level, mob.getBoundingBox().getCenter());
			maybeSlime(level, mob, attacker);
		}
	}

	/** The crack of the tip breaking the sound barrier: loud, with a flash of a shock ring. */
	private static void crack(ServerLevel level, Vec3 at) {
		level.playSound(null, at.x, at.y, at.z, Schnozzles.WHIP_CRACK, SoundSource.PLAYERS, 1.6f, 0.9f + level.getRandom().nextFloat() * 0.25f);
		level.sendParticles(Schnozzles.WHIP_CRACK_PARTICLE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.3);
	}

	/** Now and then the booger grip leaves its mark. */
	private static void maybeSlime(ServerLevel level, LivingEntity target, @Nullable Entity attacker) {
		if (level.getRandom().nextFloat() >= SLIME_CHANCE) {
			return;
		}
		target.addEffect(new MobEffectInstance(Schnozzles.SLIMED, SLIMED_TICKS, 0), attacker);
		level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY(0.6), target.getZ(), 14, target.getBbWidth() * 0.4,
				target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.05);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), Schnozzles.BOOGER_SPLAT, SoundSource.PLAYERS, 0.8f, 1.1f);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		player.getCooldowns().addCooldown(stack, SNAG_COOLDOWN);
		if (level instanceof ServerLevel serverLevel) {
			snag(serverLevel, player, stack);
		}
		return InteractionResult.SUCCESS;
	}

	/** Lashes at whatever the player is looking at and pulls it (or the player) in. */
	private void snag(ServerLevel level, Player player, ItemStack stack) {
		HitResult hit = snagTarget(player, snagRange());
		Vec3 to = hit.getLocation();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Schnozzles.WHIP_SNAG, SoundSource.PLAYERS, 1.0f,
				0.9f + level.getRandom().nextFloat() * 0.2f);
		ModNetworking.WhipLash lash = new ModNetworking.WhipLash(player.getId(), to);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(watcher, lash);
		}
		if (player instanceof ServerPlayer self) {
			ServerPlayNetworking.send(self, lash);
		}
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}

		Vec3 toPlayer = player.position().subtract(to);
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity mob) {
			mob.hurtServer(level, level.damageSources().playerAttack(player), 1.0f);
			// After the hurt, which knocks it the other way.
			double resist = 1.0 - mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 flat = new Vec3(toPlayer.x, 0.0, toPlayer.z);
			double speed = Math.min(Math.max(flat.length() - 1.5, 0.0) * PULL_PER_BLOCK, PULL_MAX);
			mob.setDeltaMovement(flat.normalize().scale(speed * resist).add(0.0, PULL_LIFT * resist, 0.0));
			mob.syncVelocity = true;
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PULL_SLOWNESS_TICKS, 1), player);
			maybeSlime(level, mob, player);
			level.playSound(null, to.x, to.y, to.z, Schnozzles.WHIP_CRACK, SoundSource.PLAYERS, 0.8f, 1.3f);
		} else if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ItemEntity item) {
			// Over about ten ticks: items lose 2% a tick to drag and fall at 0.04.
			Vec3 flat = new Vec3(toPlayer.x, 0.0, toPlayer.z);
			item.setDeltaMovement(flat.scale(0.11).add(0.0, 0.2 + toPlayer.y * 0.1, 0.0));
			item.setNoPickUpDelay();
			item.syncVelocity = true;
		} else if (hit.getType() == HitResult.Type.BLOCK) {
			Vec3 pull = to.subtract(player.getEyePosition());
			double speed = Math.min(YANK_BASE + pull.length() * YANK_PER_BLOCK, YANK_MAX);
			Vec3 velocity = pull.normalize().scale(speed).add(0.0, YANK_LIFT, 0.0);
			if (velocity.length() > YANK_MAX) {
				velocity = velocity.normalize().scale(YANK_MAX);
			}
			player.setDeltaMovement(velocity);
			player.syncVelocity = true;
			// Falls are only counted from below the lowest point of the pull, and only until the player lands.
			player.resetFallDistance();
			player.setIgnoreFallDamageFromCurrentImpulse(true, new Vec3(player.getX(), Math.min(player.getY(), to.y), player.getZ()));
			level.playSound(null, to.x, to.y, to.z, Schnozzles.WHIP_CRACK, SoundSource.PLAYERS, 0.8f, 1.3f);
		}
		stack.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
	}

	/** The first creature or dropped item along the player's line of sight within range, else the block there, else a miss. */
	private static HitResult snagTarget(Player player, double range) {
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getViewVector(1.0f).scale(range));
		HitResult block = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		end = block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player.level(), player, start, end,
				new AABB(start, end).inflate(1.0), target -> snaggable(target) && target.getRootVehicle() != player.getRootVehicle(), 0.3f);
		return entity != null ? entity : block;
	}

	private static boolean snaggable(Entity entity) {
		return !entity.isSpectator() && entity.isAlive() && (entity instanceof ItemEntity || entity instanceof LivingEntity && entity.isPickable());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.nose_hair_whip.lash").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.nose_hair_whip.snag").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.nose_hair_whip.slime").withStyle(ChatFormatting.DARK_GREEN));
	}
}
