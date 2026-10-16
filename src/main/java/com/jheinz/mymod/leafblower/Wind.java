package com.jheinz.mymod.leafblower;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.banana.Bananas;
import com.jheinz.mymod.fishing.fish.ModFish;
import com.jheinz.mymod.scarecrow.Scarecrow;

/**
 * What a leaf blower's air does, a tick at a time, on the server.
 *
 * <p><b>Blowing</b> pushes everything in a cone {@link #BLOW_RANGE} blocks long ({@link #BLOW_HALF_ANGLE} degrees each
 * side of the aim) away along the aim, harder close in: mobs and players (less their knockback resistance), dropped items,
 * XP orbs, boats and the like, and projectiles (a fireball is turned straight round). It puts out anything burning in
 * it. Along a fan of rays it also snuffs fires, candles and campfires, strips a flurry of leaves off any leaf block it
 * hits (particles only; nothing breaks), slides a banana peel along the ground a block at a time, and sets a scarecrow
 * flailing.
 *
 * <p><b>Sucking</b> (a Blower-Vac) pulls dropped items and XP orbs in from {@link #SUCK_RANGE} blocks in a wider cone
 * (and anything within reach of the mouth whatever the angle); at the mouth an item goes into the bag (then the
 * inventory) and an orb into the player. Fish are hoovered out of the water whole, into the bag as their catch; other
 * small things (chickens, rabbits, bats, babies) are dragged up to the mouth and held there, flapping.
 */
public final class Wind {
	public static final double BLOW_RANGE = 8.0;
	public static final double SUCK_RANGE = 10.0;
	public static final double BLOW_HALF_ANGLE = 24.0;
	public static final double SUCK_HALF_ANGLE = 34.0;
	private static final double BLOW_COS = Math.cos(Math.toRadians(BLOW_HALF_ANGLE));
	private static final double SUCK_COS = Math.cos(Math.toRadians(SUCK_HALF_ANGLE));
	/** Anything this close to the mouth is caught whatever the angle. */
	private static final double MOUTH_REACH = 2.5;
	/** And collected this close. */
	private static final double COLLECT_REACH = 1.4;
	private static final double MAX_PUSHED_SPEED = 0.9;

	private Wind() {
	}

	public static void initialize() {
	}

	// ------------------------------------------------------------------------------------------------------------ blow

	public static void blow(ServerLevel level, Player player, Vec3 nozzle, Vec3 dir) {
		long now = level.getGameTime();
		AABB box = new AABB(nozzle, nozzle.add(dir.scale(BLOW_RANGE))).inflate(BLOW_RANGE * 0.45);
		for (Entity e : level.getEntities(player, box, e -> !e.isSpectator() && !e.isRemoved())) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(nozzle);
			double d = to.length();
			if (d > BLOW_RANGE || d < 1.0e-3 || to.scale(1.0 / d).dot(dir) < BLOW_COS && d > 1.2) {
				continue;
			}
			float f = (float) Math.max(0.2, 1.0 - d / BLOW_RANGE);
			push(level, e, dir, f, now);
		}
		if (now % 2 == 0) {
			rays(level, player, nozzle, dir, now);
		}
	}

	private static void push(ServerLevel level, Entity e, Vec3 dir, float f, long now) {
		if (e.isOnFire()) {
			e.clearFire();
			level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.NEUTRAL, 0.5f, 1.6f);
		}
		if (e instanceof Scarecrow scarecrow) {
			// Rooted to its post: it can only flail.
			if (now % 5 == 0) {
				level.broadcastEntityEvent(scarecrow, EntityEvent.ARMORSTAND_WOBBLE);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.WHEAT), scarecrow.getX(),
						scarecrow.getY(1.4), scarecrow.getZ(), 4, 0.25, 0.3, 0.25, 0.08);
			}
			return;
		}
		if (e instanceof AbstractHurtingProjectile fireball) {
			// A fireball (or wind charge, or skull) turned right round on whoever threw it.
			double speed = Math.max(0.6, fireball.getDeltaMovement().length());
			fireball.setDeltaMovement(dir.scale(speed));
			fireball.accelerationPower = Math.abs(fireball.accelerationPower);
			fireball.needsSync = true;
			return;
		}
		double k;
		if (e instanceof Projectile) {
			k = 0.25;
		} else if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
			k = 0.11;
		} else if (e instanceof Player player) {
			if (player.getAbilities().flying) {
				return;
			}
			k = 0.065 * (1.0 - player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		} else if (e instanceof LivingEntity living) {
			k = 0.09 * (1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		} else {
			k = 0.05;
		}
		k *= f;
		Vec3 v = e.getDeltaMovement().add(dir.x * k, dir.y * k * 0.6 + (e.onGround() ? 0.02 * f : 0.0), dir.z * k);
		double flat = Math.sqrt(v.x * v.x + v.z * v.z);
		if (flat > MAX_PUSHED_SPEED && !(e instanceof Projectile)) {
			v = new Vec3(v.x / flat * MAX_PUSHED_SPEED, v.y, v.z / flat * MAX_PUSHED_SPEED);
		}
		e.setDeltaMovement(v);
		e.needsSync = true;
		if (e instanceof LivingEntity living) {
			living.resetFallDistance();
		}
	}

	/** A fan of seven rays out to the cone's end: fires, candles, campfires, leaves and banana peels along them. */
	private static void rays(ServerLevel level, Player player, Vec3 nozzle, Vec3 dir, long now) {
		Vec3 right = dir.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 up = right.cross(dir).normalize();
		double spread = Math.tan(Math.toRadians(BLOW_HALF_ANGLE * 0.7));
		for (int r = 0; r < 7; r++) {
			Vec3 ray = dir;
			if (r > 0) {
				double a = Math.PI * 2.0 * r / 6.0 + now * 0.37;
				ray = dir.add(right.scale(Math.cos(a) * spread)).add(up.scale(Math.sin(a) * spread)).normalize();
			}
			BlockPos last = null;
			for (double s = 0.5; s <= BLOW_RANGE; s += 0.5) {
				Vec3 p = nozzle.add(ray.scale(s));
				BlockPos pos = BlockPos.containing(p);
				if (pos.equals(last)) {
					continue;
				}
				last = pos;
				if (touch(level, player, pos, ray, now)) {
					break;
				}
			}
		}
	}

	/** The air reaching one block. Returns true if it stops the ray. */
	private static boolean touch(ServerLevel level, Player player, BlockPos pos, Vec3 ray, long now) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof BaseFireBlock) {
			level.removeBlock(pos, false);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.4f + level.getRandom().nextFloat() * 0.3f);
			level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.02);
			return false;
		}
		if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
			CampfireBlock.douse(player, level, pos, state);
			level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
			level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.8f, 1.2f);
			return true;
		}
		if (AbstractCandleBlock.isLit(state)) {
			AbstractCandleBlock.extinguish(player, state, level, pos);
			return false;
		}
		if (state.is(BlockTags.LEAVES)) {
			if (now % 4 == 0) {
				Vec3 c = Vec3.atCenterOf(pos);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), c.x, c.y, c.z, 6, 0.45, 0.45, 0.45, 0.15);
			}
			return true;
		}
		if (state.is(Bananas.BANANA_PEEL_BLOCK)) {
			if (now % 6 == 0) {
				slidePeel(level, pos, state, ray);
			}
			return true;
		}
		return !state.getCollisionShape(level, pos).isEmpty();
	}

	/** A peel skids a block downwind, if there's floor there to lie on. */
	private static void slidePeel(ServerLevel level, BlockPos pos, BlockState state, Vec3 ray) {
		Direction way = Direction.getApproximateNearest(ray.x, 0.0, ray.z);
		if (way.getAxis().isVertical()) {
			return;
		}
		BlockPos to = pos.relative(way);
		if (!level.getBlockState(to).canBeReplaced() || !state.canSurvive(level, to) || level.getBlockState(to).is(state.getBlock())) {
			return;
		}
		level.removeBlock(pos, false);
		level.setBlockAndUpdate(to, state);
		level.playSound(null, to, Bananas.PEEL_SLIP, SoundSource.BLOCKS, 0.35f, 1.5f + level.getRandom().nextFloat() * 0.2f);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Bananas.BANANA_PEEL), to.getX() + 0.5, to.getY() + 0.1,
				to.getZ() + 0.5, 3, 0.2, 0.02, 0.2, 0.04);
	}

	// ------------------------------------------------------------------------------------------------------------ suck

	public static void suck(ServerLevel level, ServerPlayer player, ItemStack blower, Vec3 nozzle, Vec3 dir) {
		AABB box = new AABB(nozzle, nozzle.add(dir.scale(SUCK_RANGE))).inflate(SUCK_RANGE * 0.6);
		for (Entity e : level.getEntities(player, box, e -> !e.isSpectator() && !e.isRemoved())) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(nozzle);
			double d = to.length();
			if (d > SUCK_RANGE || d > MOUTH_REACH && to.scale(1.0 / Math.max(d, 1.0e-3)).dot(dir) < SUCK_COS) {
				continue;
			}
			if (e instanceof ItemEntity item) {
				if (d < COLLECT_REACH) {
					collect(level, player, blower, item);
				} else {
					pull(e, nozzle, d, 0.16);
				}
			} else if (e instanceof ExperienceOrb orb) {
				if (d < COLLECT_REACH) {
					orb.playerTouch(player);
				} else {
					pull(e, nozzle, d, 0.16);
				}
			} else if (e instanceof AbstractFish fish && !fish.isPersistenceRequired()) {
				Item catchItem = catchOf(fish);
				if (d < COLLECT_REACH && catchItem != null) {
					hoover(level, player, blower, fish, catchItem);
				} else {
					pull(e, nozzle, d, 0.1);
				}
			} else if (e instanceof LivingEntity living && !(e instanceof Player) && small(living)) {
				if (d > 1.0) {
					pull(e, nozzle, d, 0.08);
				} else {
					// Stuck on the mouth, flapping.
					e.setDeltaMovement(e.getDeltaMovement().scale(0.2));
					e.needsSync = true;
				}
				living.resetFallDistance();
			}
		}
	}

	/** Small enough to suck up: a chicken, rabbit, bat, silverfish or baby. */
	private static boolean small(LivingEntity living) {
		return living.isBaby() || living.getBbWidth() * living.getBbWidth() * living.getBbHeight() < 0.3;
	}

	private static void pull(Entity e, Vec3 nozzle, double d, double strength) {
		Vec3 toward = nozzle.subtract(e.getBoundingBox().getCenter()).normalize();
		double speed = Mth.clamp(strength + 0.03 * (SUCK_RANGE - d), 0.0, 0.7);
		e.setDeltaMovement(e.getDeltaMovement().scale(0.35).add(toward.scale(speed)));
		e.needsSync = true;
	}

	/** An item reaches the mouth: into the bag, the rest into the inventory, anything left stays where it was. */
	private static void collect(ServerLevel level, ServerPlayer player, ItemStack blower, ItemEntity item) {
		ItemStack stack = item.getItem().copy();
		int before = stack.getCount();
		stack = LeafBlowerItem.bag(blower, stack);
		if (!stack.isEmpty()) {
			player.getInventory().add(stack);
		}
		int taken = before - stack.getCount();
		if (taken <= 0) {
			return;
		}
		player.take(item, taken);
		if (stack.isEmpty()) {
			item.discard();
		} else {
			item.setItem(stack);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f,
				1.6f + level.getRandom().nextFloat() * 0.4f);
	}

	/** A fish reaches the mouth: hoovered up whole into the bag as its catch (or the inventory, or dropped). */
	private static void hoover(ServerLevel level, ServerPlayer player, ItemStack blower, AbstractFish fish, Item catchItem) {
		ItemStack caught = LeafBlowerItem.bag(blower, new ItemStack(catchItem));
		if (!caught.isEmpty()) {
			player.getInventory().placeItemBackInInventory(caught, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY(0.5), fish.getZ(), 12, 0.2, 0.2, 0.2, 0.1);
		level.playSound(null, fish.getX(), fish.getY(), fish.getZ(), SoundEvents.COD_FLOP, SoundSource.NEUTRAL, 1.0f, 1.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.6f);
		fish.discard();
	}

	private static @Nullable Item catchOf(AbstractFish fish) {
		if (fish instanceof ModFish mod) {
			return mod.kind().catchItem();
		} else if (fish instanceof Cod) {
			return Items.COD;
		} else if (fish instanceof Salmon) {
			return Items.SALMON;
		} else if (fish instanceof Pufferfish) {
			return Items.PUFFERFISH;
		} else if (fish instanceof TropicalFish) {
			return Items.TROPICAL_FISH;
		}
		return null;
	}
}
