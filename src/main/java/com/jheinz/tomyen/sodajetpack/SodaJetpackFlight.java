package com.jheinz.tomyen.sodajetpack;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The jetpack's server side. As with all player movement, the wearer's client does the flying (SodaJetpackClient
 * pushes them up while they hold jump in the air); the server works out from the same inputs whether they're
 * thrusting, burns the fuel and pressure, loads more fuel from their inventory, takes the edge off their fall, and
 * plays the fizzing, sputtering and spinning for everyone to hear. Sputters and spin-outs come from
 * {@link JetpackChaos}, so both sides agree on them.
 *
 * <ul>
 * <li>Thrusting uses {@link JetpackMode#drain} of the pressure a tick. On the ground it builds back by
 * {@value #REGEN} a tick; run it right out in the air and it goes flat.
 * <li>A flat jetpack does nothing until it's shaken ({@link #shake}): each tick of shaking builds {@value #SHAKE_RATE}
 * of pressure, and once it's full again it pops back into life.
 * <li>Thrust cushions a fall (the fall so far is cut by {@link JetpackMode#cushion} each tick), so a gentle landing
 * on a sputtering pack is soft; run out high up and it's an ordinary, painful fall (Big Red Boots halve it).
 * <li>Hitting a wall at speed under thrust or in a spin-out is a crash: wall damage like an elytra's, the bottles
 * crumple (it goes flat) and the pack takes some wear.
 * </ul>
 */
public final class SodaJetpackFlight {
	public static final float REGEN = 1.0f / 100.0f;
	public static final float SHAKE_RATE = 1.0f / 30.0f;
	/** Horizontal speed (blocks a tick) at which hitting a wall under thrust is a crash. */
	private static final double CRASH_SPEED = 0.36;
	/** A crash counts for this long after the last thrust. */
	private static final int CRASH_TICKS = 10;
	private static final int CRASH_WEAR = 4;
	/** The fizz sound is replayed this often while thrusting. */
	private static final int THRUST_SOUND_TICKS = 4;

	private SodaJetpackFlight() {
	}

	/** The server's bookkeeping for one wearer. */
	public static final class Run {
		long lastShake = Long.MIN_VALUE / 2;
		long lastThrust = Long.MIN_VALUE / 2;
		long nextEmptyMessage;
		Vec3 lastPos = Vec3.ZERO;
		double lastSpeed;
	}

	/** Whether a player is somewhere the jetpack can push them: off the ground, out of water, not flying otherwise. */
	public static boolean airborne(Player player) {
		return !player.onGround() && !player.isInWater() && !player.isInLava() && !player.getAbilities().flying && !player.isFallFlying()
				&& !player.isPassenger() && !player.isSpectator() && !player.isSleeping();
	}

	/** Whether both hands are empty, as shaking needs. */
	public static boolean handsFree(Player player) {
		return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
	}

	/**
	 * How the jetpack would fly now, as far as the wearer's client can tell: what's in the tank, and what it would load
	 * from the inventory when that runs dry. Creative players never run out.
	 */
	public static JetpackMode predictMode(Player player, JetpackTank tank) {
		boolean sugar = tank.sugar() > 0;
		boolean slime = tank.slime() > 0;
		if (player.getAbilities().instabuild) {
			return sugar || slime ? JetpackMode.of(sugar, slime) : JetpackMode.MIX;
		}
		Inventory inventory = player.getInventory();
		boolean charge = inventory.contains(stack -> stack.is(SodaJetpacks.FIZZ_CHARGE));
		if (!sugar) {
			sugar = charge || inventory.contains(stack -> stack.is(Items.SUGAR));
			slime |= charge;
		}
		if (!slime) {
			slime = inventory.contains(stack -> stack.is(Items.SLIME_BALL) || stack.is(Items.SLIME_BLOCK));
		}
		return JetpackMode.of(sugar, slime);
	}

	static void tick(ServerPlayer player) {
		Run run = player.getAttachedOrCreate(SodaJetpacks.RUN);
		ServerLevel level = player.level();
		Vec3 pos = player.position();
		double speed = pos.subtract(run.lastPos).horizontalDistance();
		double lastSpeed = run.lastSpeed;
		run.lastPos = pos;
		run.lastSpeed = speed;
		int fx = SodaJetpacks.fx(player);
		if (!SodaJetpacks.isWearing(player)) {
			if (fx != 0) {
				player.removeAttached(SodaJetpacks.FX);
			}
			return;
		}
		JetpackTank tank = SodaJetpacks.tank(player);
		JetpackTank next = tank;
		long time = level.getGameTime();
		int id = player.getId();
		Input input = player.getLastClientInput();
		boolean shaking = time - run.lastShake <= 2;
		int flags = 0;
		JetpackMode mode = JetpackMode.NONE;

		if (input.jump() && airborne(player) && !shaking && tank.pressurised()) {
			next = refuel(level, player, next);
			mode = next.mode();
			if (mode == JetpackMode.NONE) {
				if (time >= run.nextEmptyMessage) {
					run.nextEmptyMessage = time + 60;
					player.sendOverlayMessage(Component.translatable("soda_jetpack.tomyen.empty").withStyle(ChatFormatting.RED));
					play(level, player, SodaJetpacks.SPUTTER, 0.5f, 0.6f);
				}
			} else {
				flags |= SodaJetpacks.FX_THRUST;
				run.lastThrust = time;
				if (JetpackChaos.sputters(time, id, mode)) {
					flags |= SodaJetpacks.FX_SPUTTER;
					if (JetpackChaos.sputterStarts(time, id, mode)) {
						play(level, player, SodaJetpacks.SPUTTER, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.3f);
					}
				} else {
					if (!player.getAbilities().instabuild) {
						next = next.burn();
					}
					next = next.withPressure(next.pressure() - mode.drain);
					player.fallDistance *= mode.cushion;
					if (time % THRUST_SOUND_TICKS == 0) {
						play(level, player, SodaJetpacks.THRUST, 0.55f, 0.85f + level.getRandom().nextFloat() * 0.35f);
					}
				}
				if (JetpackChaos.spins(time, id, mode)) {
					flags |= SodaJetpacks.FX_SPIN;
					if (JetpackChaos.spinStarts(time, id, mode)) {
						play(level, player, SodaJetpacks.SPIN, 0.8f, 1.0f + level.getRandom().nextFloat() * 0.3f);
					}
				}
				if (next.pressure() <= 0.0f) {
					next = next.withFlat(true);
					play(level, player, SodaJetpacks.FLAT, 0.9f, 1.0f);
					player.sendOverlayMessage(Component.translatable("soda_jetpack.tomyen.flat").withStyle(ChatFormatting.GOLD));
				}
			}
		} else if (player.onGround() && !tank.flat() && tank.pressure() < 1.0f) {
			next = next.withPressure(tank.pressure() + REGEN);
		}

		// A crash: into a wall at speed while thrusting or spinning out.
		if (player.horizontalCollision && lastSpeed > CRASH_SPEED && time - run.lastThrust <= CRASH_TICKS) {
			next = crash(level, player, next, lastSpeed);
		}
		if (shaking) {
			flags |= SodaJetpacks.FX_SHAKE;
		}
		if (flags != 0) {
			flags |= (mode == JetpackMode.NONE ? next.mode() : mode).ordinal() << SodaJetpacks.FX_MODE_SHIFT;
		}
		if (!next.equals(tank)) {
			player.setAttached(SodaJetpacks.TANK, next);
		}
		if (flags != fx) {
			player.setAttached(SodaJetpacks.FX, flags);
		}
	}

	/** Tops the tank up from the inventory if a fuel's run dry: a Fizz Charge (both), else sugar, then slime. */
	private static JetpackTank refuel(ServerLevel level, ServerPlayer player, JetpackTank tank) {
		if (tank.sugar() > 0 && tank.slime() > 0) {
			return tank;
		}
		if (player.getAbilities().instabuild) {
			return tank.sugar() == 0 && tank.slime() == 0 ? tank.add(SodaJetpacks.CHARGE_TICKS, SodaJetpacks.CHARGE_TICKS) : tank;
		}
		Inventory inventory = player.getInventory();
		JetpackTank before = tank;
		if (tank.sugar() <= 0) {
			if (take(inventory, SodaJetpacks.FIZZ_CHARGE)) {
				tank = tank.add(SodaJetpacks.CHARGE_TICKS, SodaJetpacks.CHARGE_TICKS);
				ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
				if (!player.addItem(bottle)) {
					player.spawnAtLocation(level, bottle);
				}
			} else if (take(inventory, Items.SUGAR)) {
				tank = tank.add(SodaJetpacks.SUGAR_TICKS, 0);
			}
		}
		if (tank.slime() <= 0) {
			if (take(inventory, Items.SLIME_BALL)) {
				tank = tank.add(0, SodaJetpacks.SLIME_TICKS);
			} else if (take(inventory, Items.SLIME_BLOCK)) {
				tank = tank.add(0, SodaJetpacks.SLIME_BLOCK_TICKS);
			}
		}
		if (!tank.equals(before)) {
			play(level, player, SodaJetpacks.REFUEL, 0.35f, 1.0f + level.getRandom().nextFloat() * 0.2f);
		}
		return tank;
	}

	private static boolean take(Inventory inventory, Item item) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				inventory.removeItem(i, 1);
				return true;
			}
		}
		return false;
	}

	private static JetpackTank crash(ServerLevel level, ServerPlayer player, JetpackTank tank, double speed) {
		float damage = (float) ((speed - CRASH_SPEED * 0.7) * 12.0);
		player.hurtServer(level, player.damageSources().flyIntoWall(), damage);
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 16, 0.4, 0.4, 0.4, 0.08);
		level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 20, 0.5, 0.5, 0.5, 0.1);
		play(level, player, SodaJetpacks.CRASH, 1.0f, 1.0f);
		ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
		if (worn.is(SodaJetpacks.SODA_JETPACK)) {
			worn.hurtAndBreak(CRASH_WEAR, player, EquipmentSlot.CHEST);
		}
		return tank.withPressure(0.0f).withFlat(true);
	}

	/**
	 * A tick of shaking from the wearer's client: builds the pressure back up, rattling and fizzing, and brings a
	 * flat pack back to life once it's full. At most once a tick, and only while sneaking with both hands empty.
	 */
	static void shake(ServerPlayer player) {
		if (!SodaJetpacks.isWearing(player) || !player.isShiftKeyDown() || !handsFree(player)) {
			return;
		}
		Run run = player.getAttachedOrCreate(SodaJetpacks.RUN);
		ServerLevel level = player.level();
		long time = level.getGameTime();
		if (run.lastShake == time) {
			return;
		}
		run.lastShake = time;
		JetpackTank tank = SodaJetpacks.tank(player);
		if (time % 5 == 0) {
			play(level, player, SodaJetpacks.SHAKE, 0.6f, 0.9f + level.getRandom().nextFloat() * 0.3f);
		}
		if (tank.pressure() >= 1.0f && !tank.flat()) {
			return;
		}
		JetpackTank next = tank.withPressure(tank.pressure() + SHAKE_RATE);
		if (next.pressure() >= 1.0f) {
			next = next.withFlat(false);
			play(level, player, SodaJetpacks.CHARGED, 0.9f, 1.0f);
			if (tank.flat()) {
				player.sendOverlayMessage(Component.translatable("soda_jetpack.tomyen.charged").withStyle(ChatFormatting.GREEN));
			}
		}
		player.setAttached(SodaJetpacks.TANK, next);
	}

	private static void play(ServerLevel level, ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, player.getX(), player.getY() + 0.7, player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}
}
