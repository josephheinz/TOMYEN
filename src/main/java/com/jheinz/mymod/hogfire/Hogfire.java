package com.jheinz.mymod.hogfire;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.industry.Industry;

/**
 * A Hogfire: a pig in a flight harness, with wings and a rocket engine strapped to its back once they're fitted.
 * <p>
 * Fitting: a {@link HogfireGearItem flight harness} turns an adult pig into a Hogfire (it's a new entity, so its gear
 * and fuel save with it and nothing touches vanilla pigs); then the wings, then the rocket engine. Shears take the
 * last piece off and drop it; shearing off the harness makes it an ordinary pig again. Its pig variant and voice
 * carry over both ways.
 * <p>
 * Fuel: a Rocket Fuel Canister gives {@link HogfireFlight#FUEL_PER_CANISTER} units (the canister comes back empty),
 * up to two canisters' worth. Fully geared and fuelled, use it to climb on; sneak to climb off. The pilot's client
 * sends its controls every tick ({@link Hogfires.Controls}); the server flies it ({@link HogfireFlight}), so it has
 * no controlling passenger in vanilla's sense. On the ground it walks like a pig, steered by the pilot's look.
 * <p>
 * Without a pilot it's just a pig, except that with wings on it flutters down from a height instead of falling.
 */
public class Hogfire extends Pig {
	public static final int HARNESS = 1;
	public static final int WINGS = 2;
	public static final int ENGINE = 3;

	private static final EntityDataAccessor<Integer> DATA_GEAR = SynchedEntityData.defineId(Hogfire.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(Hogfire.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_THROTTLE = SynchedEntityData.defineId(Hogfire.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_AIRSPEED = SynchedEntityData.defineId(Hogfire.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_BANK = SynchedEntityData.defineId(Hogfire.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_AFTERBURNER = SynchedEntityData.defineId(Hogfire.class, EntityDataSerializers.BOOLEAN);
	/** How fast an unpiloted pig with wings sinks, in blocks a tick. */
	private static final double FLUTTER_SINK = 0.12;

	/** Server: the pilot's latest controls, and how many ticks ago they came. */
	private Hogfires.Controls controls = Hogfires.Controls.NEUTRAL;
	private int controlsAge;
	/** Server: fuel burnt this tick that doesn't add up to a whole unit yet. */
	private float burnRemainder;
	/** Client: the bank angle drawn this tick and last, eased towards the synced one. */
	private float bankNow;
	private float bankO;

	public Hogfire(EntityType<? extends Hogfire> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Pig.createAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_GEAR, HARNESS);
		entityData.define(DATA_FUEL, 0);
		entityData.define(DATA_THROTTLE, 0.0f);
		entityData.define(DATA_AIRSPEED, 0.0f);
		entityData.define(DATA_BANK, 0.0f);
		entityData.define(DATA_AFTERBURNER, false);
	}

	// --- Gear -----------------------------------------------------------------------------------------------

	/** Straps a flight harness on a pig: the pig becomes a Hogfire. Returns it, or null if it couldn't be done. */
	public static @Nullable Hogfire harness(Pig pig) {
		Hogfire hogfire = pig.convertTo(Hogfires.HOGFIRE, ConversionParams.single(pig, true, true), converted -> {
			converted.setComponent(DataComponents.PIG_VARIANT, pig.getVariant());
			converted.setComponent(DataComponents.PIG_SOUND_VARIANT, pig.get(DataComponents.PIG_SOUND_VARIANT));
		});
		if (hogfire != null) {
			hogfire.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 1.0f);
		}
		return hogfire;
	}

	/** How much gear it has on: HARNESS, WINGS or ENGINE (each includes the ones before). */
	public int gear() {
		return entityData.get(DATA_GEAR);
	}

	public boolean hasWings() {
		return gear() >= WINGS;
	}

	public boolean hasEngine() {
		return gear() >= ENGINE;
	}

	private void setGear(int gear) {
		entityData.set(DATA_GEAR, gear);
	}

	private static Item gearItem(int stage) {
		return switch (stage) {
			case ENGINE -> Hogfires.ROCKET_ENGINE;
			case WINGS -> Hogfires.PIG_WINGS;
			default -> Hogfires.PIG_FLIGHT_HARNESS;
		};
	}

	/** Fits the next piece of gear, if this is it. */
	private InteractionResult fit(Player player, ItemStack stack, HogfireGearItem item) {
		if (item.stage() != gear() + 1) {
			if (!level().isClientSide()) {
				String key = item.stage() <= gear() ? "entity.mymod.hogfire.already_fitted" : "entity.mymod.hogfire.wings_first";
				player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		if (!level().isClientSide()) {
			setGear(item.stage());
			stack.consume(1, player);
			playSound(item.stage() == ENGINE ? SoundEvents.ANVIL_PLACE : SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 0.8f, 1.2f);
			playSound(getAmbientSound(), 1.0f, 1.5f);
		}
		return InteractionResult.SUCCESS;
	}

	/** Shears take off the last piece fitted; without the harness it's a plain pig again. */
	private InteractionResult unfit(Player player, ItemStack shears, InteractionHand hand) {
		if (level() instanceof ServerLevel level) {
			int gear = gear();
			spawnAtLocation(level, new ItemStack(gearItem(gear)), 0.5f);
			shears.hurtAndBreak(1, player, hand.asEquipmentSlot());
			playSound(SoundEvents.SHEEP_SHEAR, 1.0f, 1.0f);
			if (gear == HARNESS) {
				Pig pig = convertTo(EntityTypes.PIG, ConversionParams.single(this, true, true), converted -> {
					converted.setComponent(DataComponents.PIG_VARIANT, getVariant());
					converted.setComponent(DataComponents.PIG_SOUND_VARIANT, get(DataComponents.PIG_SOUND_VARIANT));
				});
				if (pig == null) {
					setGear(HARNESS);
				}
			} else {
				setGear(gear - 1);
				if (gear == ENGINE) {
					// The fuel was in the engine's tank.
					setFuel(0);
					setThrottle(0.0f);
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	// --- Fuel and controls ------------------------------------------------------------------------------------

	public int fuel() {
		return entityData.get(DATA_FUEL);
	}

	void setFuel(int fuel) {
		entityData.set(DATA_FUEL, Mth.clamp(fuel, 0, HogfireFlight.MAX_FUEL));
	}

	/** Burns fuel at a rate in units a tick, keeping the fractions for next time. */
	void burn(float rate) {
		burnRemainder += rate;
		int whole = (int) burnRemainder;
		if (whole > 0) {
			burnRemainder -= whole;
			setFuel(fuel() - whole);
		}
	}

	public float throttle() {
		return entityData.get(DATA_THROTTLE);
	}

	void setThrottle(float throttle) {
		entityData.set(DATA_THROTTLE, Mth.clamp(throttle, 0.0f, 1.0f));
	}

	/** Blocks a tick, along the way it's flying. */
	public float airspeed() {
		return entityData.get(DATA_AIRSPEED);
	}

	void setAirspeed(float airspeed) {
		entityData.set(DATA_AIRSPEED, airspeed);
	}

	public float bank() {
		return entityData.get(DATA_BANK);
	}

	void setBank(float bank) {
		entityData.set(DATA_BANK, bank);
	}

	/** Client: degrees of roll, smoothed between ticks; positive banks to the right. */
	public float bank(float partialTicks) {
		return Mth.lerp(partialTicks, bankO, bankNow);
	}

	public boolean afterburner() {
		return entityData.get(DATA_AFTERBURNER);
	}

	void setAfterburner(boolean afterburner) {
		entityData.set(DATA_AFTERBURNER, afterburner);
	}

	/** Whether the rocket is lit. */
	public boolean engineRunning() {
		return hasEngine() && fuel() > 0 && throttle() > 0.0f;
	}

	public void setControls(Hogfires.Controls controls) {
		this.controls = controls;
		controlsAge = 0;
	}

	/** The pilot's controls, or (if they've stopped arriving) hands off, holding the last heading. */
	Hogfires.Controls controls() {
		if (controlsAge > HogfireFlight.CONTROLS_TIMEOUT) {
			return new Hogfires.Controls(controls.yaw(), controls.pitch(), false, false, false);
		}
		return controls;
	}

	/** The player flying it: a rider, once it's fully geared. */
	public @Nullable Player pilot() {
		return hasEngine() && getFirstPassenger() instanceof Player player ? player : null;
	}

	/** Its voice's oink, for HogfireFlight's squeals. */
	SoundEvent squealSound() {
		return getAmbientSound();
	}

	// --- Interaction ------------------------------------------------------------------------------------------

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (isVehicle()) {
			return InteractionResult.PASS;
		}
		if (held.is(Items.SHEARS)) {
			return unfit(player, held, hand);
		}
		if (held.getItem() instanceof HogfireGearItem item) {
			return fit(player, held, item);
		}
		if (held.is(Industry.ROCKET_FUEL_CANISTER)) {
			return refuel(player, held, hand);
		}
		if (isFood(held)) {
			return super.mobInteract(player, hand);
		}
		if (hasEngine() && !player.isSecondaryUseActive()) {
			if (fuel() <= 0) {
				if (!level().isClientSide()) {
					player.sendOverlayMessage(Component.translatable("entity.mymod.hogfire.no_fuel").withStyle(ChatFormatting.RED));
				}
				return InteractionResult.FAIL;
			}
			if (!level().isClientSide()) {
				// Hold the heading it's on until the pilot's controls start arriving.
				setControls(new Hogfires.Controls(getYRot(), 0.0f, false, false, false));
				player.startRiding(this);
				playSound(getAmbientSound(), 1.0f, 1.3f);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	private InteractionResult refuel(Player player, ItemStack canister, InteractionHand hand) {
		if (!hasEngine() || fuel() + HogfireFlight.FUEL_PER_CANISTER > HogfireFlight.MAX_FUEL) {
			if (!level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable(hasEngine() ? "entity.mymod.hogfire.tank_full" : "entity.mymod.hogfire.engine_first")
						.withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		if (!level().isClientSide()) {
			setFuel(fuel() + HogfireFlight.FUEL_PER_CANISTER);
			player.setItemInHand(hand, ItemUtils.createFilledResult(canister, player, new ItemStack(Industry.EMPTY_CANISTER)));
			playSound(SoundEvents.BUCKET_EMPTY_LAVA, 1.0f, 1.3f);
		}
		return InteractionResult.SUCCESS;
	}

	// --- Movement ---------------------------------------------------------------------------------------------

	/** Nobody steers it with a carrot: the pilot flies it through {@link HogfireFlight}, on the server. */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return null;
	}

	/** Its own legs (and AI) stay still while it's being flown. */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || pilot() != null;
	}

	@Override
	public void travel(Vec3 input) {
		Player pilot = pilot();
		if (pilot != null && isAlive()) {
			HogfireFlight.fly(this, pilot);
			return;
		}
		if (airspeed() > 0.0f) {
			setAirspeed(0.0f);
			setThrottle(0.0f);
			setAfterburner(false);
		}
		super.travel(input);
		if (hasWings() && !onGround() && !isInWater() && getDeltaMovement().y < -FLUTTER_SINK) {
			setDeltaMovement(getDeltaMovement().multiply(1.0, 0.0, 1.0).add(0.0, -FLUTTER_SINK, 0.0));
			resetFallDistance();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (pilot() != null) {
			yBodyRot = getYRot();
			yHeadRot = getYRot();
		}
		if (level().isClientSide()) {
			bankO = bankNow;
			bankNow = Mth.approach(bankNow, bank(), 8.0f);
			HogfireFlight.effects(this);
		} else {
			controlsAge++;
			if (bank() != 0.0f && pilot() == null) {
				setBank(Mth.approach(bank(), 0.0f, 4.0f));
			}
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		// A winged pig lands softly; a hard landing in flight is a crash, which HogfireFlight handles.
		return !hasWings() && super.causeFallDamage(fallDistance, damageModifier, damageSource);
	}

	/** The pilot doesn't get hurt by their own pig's crash twice, and can't hit their mount. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() != null && hasPassenger(source.getEntity())) {
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (int stage = HARNESS; stage <= gear(); stage++) {
			spawnAtLocation(level, gearItem(stage));
		}
	}

	/** Only a pilot rides it, and only once it's fully geared. */
	@Override
	public boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
		return !isVehicle() && hasEngine();
	}

	// --- Saving -----------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("gear", gear());
		output.putInt("fuel", fuel());
		output.putFloat("throttle", throttle());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setGear(Mth.clamp(input.getIntOr("gear", HARNESS), HARNESS, ENGINE));
		setFuel(input.getIntOr("fuel", 0));
		setThrottle(input.getFloatOr("throttle", 0.0f));
	}
}
