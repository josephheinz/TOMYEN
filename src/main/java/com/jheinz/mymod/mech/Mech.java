package com.jheinz.mymod.mech;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.napalm.Napalm;
import com.jheinz.mymod.powerarmor.FusionCoreItem;
import com.jheinz.mymod.powerarmor.PowerArmor;
import com.jheinz.mymod.powerarmor.PowerArmors;
import com.jheinz.mymod.tank.SmokeScreens;
import com.jheinz.mymod.tank.Tank;
import com.jheinz.mymod.tank.TankShell;

/**
 * The Bulwark Walker: a two-legged armoured walker about 4.5 blocks tall and 2.7 across, piloted from a cockpit in
 * its chest. Built (and docked again) in a {@link MechGantryBlock} from the parts in its {@link MechLoadout}.
 * <p>
 * <b>Size.</b> Its collision box is 2.5 wide and 4 tall: vanilla only looks for an entity up to 4 blocks below
 * (and 2 to the side of) the area it searches, so anything taller would sometimes be missed by hits on its upper
 * half. The head and pauldrons rise half a block above the box; walking, it feels ahead for that with a "head
 * bumper" and stops short of ceilings it would otherwise walk its head into.
 * <p>
 * <b>Walking.</b> Heavy and deliberate: it speeds up and slows down with some weight, steps up
 * {@link #STEP_UP} blocks, wades along the bottom of rivers (it doesn't swim or float), crushes weak blocks it walks
 * into (leaves, fences, glass panes, plants: the same as a tank's, with the pilot's build rights or mobGriefing),
 * and shoulders mobs aside. Every stride stomps, shaking the cockpit. The torso turns with the pilot's view, at
 * {@link #TORSO_SLEW} degrees a tick and at most {@link #TWIST_LIMIT} degrees off the legs (looking further round
 * pivots the legs); the legs walk the way W/A/S/D point relative to the torso, turning to it first, and walk
 * backwards rather than turn right round. Jump fires the jump jets: a burst of lift (and push, if walking) that
 * costs {@link #JET_FUEL} charge and takes {@link #JET_RECHARGE_TICKS} ticks to recharge. Landing from
 * {@link #SHOCKWAVE_FALL} blocks or more sends out a shockwave instead of taking fall damage.
 * <p>
 * <b>Pilot.</b> Use a leg or the chest to climb in (see {@link MechCrew}); hold sneak for
 * {@link MechCrew#EXIT_HOLD_TICKS} ticks to climb out. Nobody can climb into one already piloted, nor wearing
 * power armor (it won't go through the hatch). Seated, the pilot can't be hurt (the walker takes the hits), never
 * runs out of air, and fights with the arms (see {@link MechArm}): attack fires the left, use the right.
 * <p>
 * <b>Power.</b> The Fusion Core in its power plant drains {@link #IDLE_DRAIN} a tick crewed and
 * {@link #WALK_DRAIN} more walking, plus what jets and weapons cost, all worse with a battered torso. Empty (or
 * with no core) it lumbers at a third of its speed with no jets or weapons. Use it with a Fusion Core to swap the
 * core; the old one comes back.
 * <p>
 * <b>Damage.</b> {@link #MAX_HEALTH} of armour that shrugs off small arms (a fifth of their damage, half for
 * armour piercing) but takes the full weight of explosions, rockets and tank rounds. Heavy hits also wear the
 * {@link MechPart} they land on. Iron or steel (ingots or blocks) patch up the hull and the parts. Destroyed, it
 * blows up (power {@value #DEATH_BLAST}, terrain damage with mobGriefing), throws the pilot clear and drops its
 * cargo, its core, some of its weapons and scrap.
 * <p>
 * <b>Holding things.</b> A Power Fist's grab puts a mob, player or dropped item in that fist (as a passenger,
 * held where the hand is) until it's thrown, or let go when the pilot climbs out or the walker dies. Something
 * thrown hard into a wall or into something else gets hurt by it.
 */
public class Mech extends PathfinderMob {
	public static final float MAX_HEALTH = 400.0f;
	public static final float STEP_UP = 2.0f;
	/** Top speed (as a ridden speed, about a walking player) and the share of it walking backwards. */
	public static final float MAX_SPEED = 0.105f;
	private static final float REVERSE = 0.55f;
	private static final float ACCELERATION = 1.0f / 30.0f;
	private static final float BRAKING = 1.0f / 12.0f;
	/** Degrees a tick the legs turn (when sound). */
	private static final float LEG_TURN = 4.0f;
	/** Degrees a tick the torso turns and the arms pitch after the pilot's view, and their limits. */
	public static final float TORSO_SLEW = 7.0f;
	private static final float PITCH_SLEW = 5.0f;
	public static final float TWIST_LIMIT = 100.0f;
	public static final float MIN_PITCH = -35.0f;
	public static final float MAX_PITCH = 30.0f;
	/** Blocks a stride covers; each one stomps. */
	private static final double STRIDE = 1.7;
	private static final float JET_LIFT = 0.95f;
	private static final float JET_PUSH = 0.6f;
	public static final int JET_RECHARGE_TICKS = 60;
	static final float JET_FUEL = 900.0f;
	/** A landing from this many blocks or more sends out a shockwave. */
	private static final double SHOCKWAVE_FALL = 3.0;
	// Fusion core drain, in charge units a tick (a full core is 72000).
	static final float IDLE_DRAIN = 1.0f;
	static final float WALK_DRAIN = 5.0f;
	private static final float LOW_FUEL = 0.15f;
	public static final int MAX_SMOKE = 3;
	private static final int SMOKE_GUNPOWDER = 2;
	private static final int SMOKE_REFILL_TICKS = 100;
	private static final int SMOKE_GRENADES = 4;
	private static final float SMOKE_FUEL = 150.0f;
	static final double THROW_SPEED = 2.3;
	private static final float THROW_FUEL = 60.0f;
	private static final float THROWN_IMPACT = 8.0f;
	private static final int THROWN_TICKS = 40;
	/** Hits that leave at least this much (after the armour) wear a part. */
	private static final float PART_HIT_THRESHOLD = 5.0f;
	static final float DEATH_BLAST = 3.0f;
	private static final double AIM_RANGE = 160.0;
	/** Running into something at more than this (blocks a tick) knocks it about. */
	private static final double SHOVE_SPEED = 0.04;

	public static final int SEQ_NONE = 0;
	public static final int SEQ_ENTERING = 1;
	public static final int SEQ_EXITING = 2;

	// Entity events.
	static final byte STOMP_EVENT = 70;
	static final byte FIRE_LEFT_EVENT = 71;
	static final byte FIRE_RIGHT_EVENT = 72;
	static final byte JETS_EVENT = 73;
	static final byte LAND_EVENT = 74;
	public static final int RECOIL_TICKS = 6;
	public static final int JET_FLAME_TICKS = 12;

	static final EntityDataAccessor<Float> TORSO_YAW = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.FLOAT);
	static final EntityDataAccessor<Float> ARM_PITCH = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.FLOAT);
	/** What it's built from, as {@link MechLoadout#look}. */
	static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> PARTS = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.INT);
	/** The core's charge in thousandths, or -1 with no core. */
	static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.INT);
	/** The jump jets' charge in thousandths. */
	static final EntityDataAccessor<Integer> JETS = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> LEFT_ARM = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> RIGHT_ARM = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.INT);
	/** Ammunition in the stores for the left and right weapons (12 bits each), and smoke salvos (bits 24-26). */
	static final EntityDataAccessor<Integer> STORES = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.INT);
	/** What a fist holds: its entity id shifted up a bit, with the arm in bit 0; -1 for nothing. */
	static final EntityDataAccessor<Integer> HELD = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Byte> SEQUENCE = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.BYTE);
	static final EntityDataAccessor<Long> SEQUENCE_START = SynchedEntityData.defineId(Mech.class, EntityDataSerializers.LONG);

	private final SimpleContainer loadout = new SimpleContainer(MechLoadout.SIZE);
	private final SimpleContainer cargo = new SimpleContainer(27);
	private final MechArm[] arms = {new MechArm(this, MechArm.LEFT), new MechArm(this, MechArm.RIGHT)};
	private float torsoYaw;
	private float torsoYawO;
	private float armPitch;
	private float armPitchO;
	// Server: the core's exact charge, the jets, the smoke, the pilot's sneak, strides and what's been thrown.
	private float fuel;
	private boolean warnedLowFuel;
	private float jetCharge = 1.0f;
	private int smoke = MAX_SMOKE;
	private int smokeRefill;
	private int exitHold;
	private double strideTravel;
	private final List<Thrown> thrown = new ArrayList<>();
	private long aimTick = -1;
	private AimPoint aim = new AimPoint(Vec3.ZERO, null);
	// The authoritative side (the pilot's client): the throttle, -REVERSE to 1.
	private float throttle;
	// Client side: arm recoil, the rotary barrels' turn, the jets' flame, and stomps and landings for the cockpit shake.
	private final int[] recoil = new int[2];
	private final float[] barrelSpin = new float[2];
	private final float[] barrelSpinO = new float[2];
	private int jetFlame;
	public int clientStomps;
	public int clientLandings;

	private record Thrown(Entity entity, int[] ticks) {
	}

	/** Where the arms converge: what's under the torso's sight line, out to {@value #AIM_RANGE} blocks. */
	public record AimPoint(Vec3 point, @Nullable Entity entity) {
	}

	public Mech(EntityType<? extends Mech> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		// A walker that didn't come out of a gantry (summoned, say) comes as the factory sends it.
		List<ItemStack> standard = new ArrayList<>(List.of(new ItemStack(Mechs.TORSO), new ItemStack(Mechs.LEGS), new ItemStack(Mechs.ARM),
				new ItemStack(Mechs.ARM), new ItemStack(Mechs.POWER_PLANT), new ItemStack(PowerArmors.FUSION_CORE), new ItemStack(Mechs.AUTOCANNON),
				new ItemStack(Mechs.POWER_FIST), new ItemStack(Mechs.CARGO_POD)));
		install(standard);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, MAX_SPEED)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, STEP_UP)
				.add(Attributes.CAMERA_DISTANCE, 10.0)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(TORSO_YAW, 0.0f);
		builder.define(ARM_PITCH, 0.0f);
		builder.define(LOOK, 0);
		builder.define(PARTS, MechPart.ALL_SOUND);
		builder.define(FUEL, -1);
		builder.define(JETS, 1000);
		builder.define(LEFT_ARM, 0);
		builder.define(RIGHT_ARM, 0);
		builder.define(STORES, MAX_SMOKE << 24);
		builder.define(HELD, -1);
		builder.define(SEQUENCE, (byte) SEQ_NONE);
		builder.define(SEQUENCE_START, 0L);
	}

	void syncArm(int side, int packed) {
		entityData.set(side == MechArm.LEFT ? LEFT_ARM : RIGHT_ARM, packed);
	}

	/** The legs only ever face the way they walk; they don't swing round after the torso. */
	@Override
	protected BodyRotationControl createBodyControl() {
		return new BodyRotationControl(this) {
			@Override
			public void clientTick() {
				yBodyRot = getYRot();
			}
		};
	}

	@Override
	public int getMaxHeadYRot() {
		return 180;
	}

	// --- The loadout ---------------------------------------------------------------------------------------------

	/** The look the loadout makes, as synced. */
	public int look() {
		return entityData.get(LOOK);
	}

	public MechWeapon weapon(int side) {
		return MechLoadout.weapon(look(), side);
	}

	public MechShoulder shoulder() {
		return MechLoadout.shoulder(look());
	}

	/**
	 * Fits a gantry's parts: part conditions from their wear, the hull from the torso, the hold from the Cargo
	 * Pod and the charge from the core. Empty slots stay empty.
	 */
	void install(List<ItemStack> parts) {
		for (int slot = 0; slot < MechLoadout.SIZE; slot++) {
			ItemStack part = parts.get(slot);
			loadout.setItem(slot, MechLoadout.fits(slot, part) ? part.copy() : ItemStack.EMPTY);
		}
		int conditions = MechPart.ALL_SOUND;
		for (MechPart part : MechPart.values()) {
			conditions = part.with(conditions, condition(loadout.getItem(part.slot())));
		}
		entityData.set(PARTS, conditions);
		CustomData torso = loadout.getItem(MechLoadout.TORSO).get(DataComponents.CUSTOM_DATA);
		float hull = torso == null ? 1.0f : torso.copyTag().getFloatOr("Hull", 1.0f);
		setHealth(Math.max(1.0f, hull * MAX_HEALTH));
		cargo.clearContent();
		ItemContainerContents hold = loadout.getItem(MechLoadout.SHOULDER).get(DataComponents.CONTAINER);
		if (hold != null) {
			hold.copyInto(cargo.getItems());
		}
		ItemStack core = loadout.getItem(MechLoadout.CORE);
		fuel = core.getItem() instanceof FusionCoreItem ? FusionCoreItem.charge(core) : 0.0f;
		for (MechArm arm : arms) {
			arm.reset();
		}
		loadoutChanged();
	}

	/** Takes the parts back off, for a gantry: wear, hull, hold and charge all written back onto them. */
	List<ItemStack> uninstall() {
		flushFuel();
		int conditions = entityData.get(PARTS);
		List<ItemStack> parts = new ArrayList<>();
		for (int slot = 0; slot < MechLoadout.SIZE; slot++) {
			parts.add(loadout.getItem(slot).copy());
		}
		for (MechPart part : MechPart.values()) {
			ItemStack stack = parts.get(part.slot());
			if (stack.isDamageableItem()) {
				stack.setDamageValue(Math.round((1.0f - part.condition(conditions)) * stack.getMaxDamage()));
			}
		}
		ItemStack torso = parts.get(MechLoadout.TORSO);
		if (!torso.isEmpty()) {
			if (getHealth() >= getMaxHealth()) {
				torso.remove(DataComponents.CUSTOM_DATA);
			} else {
				CompoundTag tag = new CompoundTag();
				tag.putFloat("Hull", getHealth() / getMaxHealth());
				torso.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			}
		}
		ItemStack pod = parts.get(MechLoadout.SHOULDER);
		if (pod.is(Mechs.CARGO_POD)) {
			pod.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(cargo.getItems()));
		}
		cargo.clearContent();
		loadout.clearContent();
		loadoutChanged();
		return parts;
	}

	/** A part item's condition from its wear. */
	private static float condition(ItemStack stack) {
		return !stack.isDamageableItem() ? 1.0f : 1.0f - stack.getDamageValue() / (float) stack.getMaxDamage();
	}

	private void loadoutChanged() {
		entityData.set(LOOK, MechLoadout.look(loadout.getItems()));
		syncFuel();
	}

	public boolean hasCargoPod() {
		return shoulder() == MechShoulder.CARGO_POD;
	}

	/** Only for the server's menus: the hold. */
	SimpleContainer cargo() {
		return cargo;
	}

	// --- Ticking -------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		torsoYawO = torsoYaw;
		armPitchO = armPitch;
		barrelSpinO[0] = barrelSpin[0];
		barrelSpinO[1] = barrelSpin[1];
		super.tick();
		tickTorso();
		if (level() instanceof ServerLevel level) {
			tickCrew(level);
			tickPower();
			for (MechArm arm : arms) {
				arm.tick(level);
			}
			tickSmoke();
			tickStride(level);
			tickTerrain(level);
			tickThrown(level);
			tickStores();
		} else {
			for (int side = 0; side < 2; side++) {
				if (recoil[side] > 0) {
					recoil[side]--;
				}
				int packed = armState(side);
				float speed = weapon(side) == MechWeapon.ROTARY_CANNON ? MechArm.progress(packed) * (MechArm.active(packed) ? 1.0f : 0.6f) : 0.0f;
				barrelSpin[side] += speed * 1.1f;
			}
			if (jetFlame > 0) {
				jetFlame--;
			}
			tickJetFlames();
		}
	}

	/** Turns the torso and pitches the arms after the pilot's view, and keeps the torso within its twist of the legs. */
	private void tickTorso() {
		Player pilot = seatedPilot();
		if (level().isClientSide()) {
			if (pilot != null && pilot.isLocalPlayer()) {
				slew(pilot.getYRot(), pilot.getXRot());
			} else {
				torsoYaw = entityData.get(TORSO_YAW);
				armPitch = entityData.get(ARM_PITCH);
			}
			return;
		}
		if (pilot != null) {
			slew(pilot.getYRot(), pilot.getXRot());
		} else if (sequence() != SEQ_NONE) {
			// Square to the legs for the climb.
			slew(getYRot(), 0.0f);
		}
		entityData.set(TORSO_YAW, torsoYaw);
		entityData.set(ARM_PITCH, armPitch);
		yHeadRot = torsoYaw;
	}

	private void slew(float wantYaw, float wantPitch) {
		float yaw = torsoYaw + Mth.clamp(Mth.wrapDegrees(wantYaw - torsoYaw), -TORSO_SLEW, TORSO_SLEW);
		float twist = Mth.clamp(Mth.wrapDegrees(yaw - getYRot()), -TWIST_LIMIT, TWIST_LIMIT);
		torsoYaw = Mth.wrapDegrees(getYRot() + twist);
		armPitch = Mth.clamp(armPitch + Mth.clamp(Mth.clamp(wantPitch, MIN_PITCH, MAX_PITCH) - armPitch, -PITCH_SLEW, PITCH_SLEW), MIN_PITCH,
				MAX_PITCH);
	}

	/** Square the torso to the legs, for a walker just put down. */
	void alignTorso() {
		torsoYaw = getYRot();
		torsoYawO = torsoYaw;
		armPitch = 0.0f;
		armPitchO = 0.0f;
		entityData.set(TORSO_YAW, torsoYaw);
		entityData.set(ARM_PITCH, 0.0f);
	}

	public float torsoYaw(float partialTicks) {
		return Mth.rotLerp(partialTicks, torsoYawO, torsoYaw);
	}

	public float armPitch(float partialTicks) {
		return Mth.lerp(partialTicks, armPitchO, armPitch);
	}

	/** How far the torso is turned off the legs, in degrees (positive to the right). */
	public float twist(float partialTicks) {
		return Mth.wrapDegrees(torsoYaw(partialTicks) - getViewYRot(partialTicks));
	}

	// --- The pilot --------------------------------------------------------------------------------------------

	/** What a fist is holding, or null. */
	public @Nullable Entity held() {
		int packed = entityData.get(HELD);
		return packed < 0 ? null : level().getEntity(packed >> 1);
	}

	/** Which arm holds something ({@link MechArm#LEFT} or {@link MechArm#RIGHT}), or -1. */
	public int heldSide() {
		int packed = entityData.get(HELD);
		return packed < 0 ? -1 : packed & 1;
	}

	public boolean holding(int side) {
		return heldSide() == side;
	}

	private boolean isHeld(Entity entity) {
		int packed = entityData.get(HELD);
		return packed >= 0 && entity.getId() == packed >> 1;
	}

	/** The player in the walker (climbing or seated), or null. Something held in a fist isn't the pilot. */
	public @Nullable Player pilot() {
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof Player player && !isHeld(player)) {
				return player;
			}
		}
		return null;
	}

	/** The pilot, if they're in the seat and not climbing in or out. */
	public @Nullable Player seatedPilot() {
		Player pilot = pilot();
		return pilot != null && sequence() == SEQ_NONE ? pilot : null;
	}

	/** The seated pilot, on the server. */
	@Nullable ServerPlayer pilotServer() {
		return seatedPilot() instanceof ServerPlayer player ? player : null;
	}

	public boolean isSeated(Player player) {
		return seatedPilot() == player;
	}

	/** A sound only the pilot hears. */
	void pilotSound(SoundEvent sound, float volume, float pitch) {
		if (pilot() instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.NEUTRAL,
					player.getX(), player.getEyeY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
		}
	}

	void pilotMessage(Component message) {
		if (pilot() instanceof ServerPlayer player) {
			player.sendOverlayMessage(message);
		}
	}

	public int sequence() {
		return entityData.get(SEQUENCE);
	}

	/** How far through the pilot's climb in or out, 0 to 1 (1 with nobody climbing). */
	public float sequenceProgress(float partialTicks) {
		int sequence = sequence();
		if (sequence == SEQ_NONE) {
			return 1.0f;
		}
		float ticks = level().getGameTime() - entityData.get(SEQUENCE_START) + partialTicks;
		return Mth.clamp(ticks / (sequence == SEQ_ENTERING ? MechCrew.ENTER_TICKS : MechCrew.EXIT_TICKS), 0.0f, 1.0f);
	}

	private void startSequence(int sequence) {
		entityData.set(SEQUENCE, (byte) sequence);
		entityData.set(SEQUENCE_START, level().getGameTime());
	}

	public float hatchOpen(float partialTicks) {
		return MechCrew.hatchOpen(sequence(), sequenceProgress(partialTicks));
	}

	/** Climbs a player in: locked to the climb until they're in the seat. */
	void board(ServerPlayer player) {
		player.stopUsingItem();
		if (!player.startRiding(this, true, true)) {
			return;
		}
		throttle = 0.0f;
		exitHold = 0;
		startSequence(SEQ_ENTERING);
		player.setYRot(getYRot());
		playSound(SoundEvents.IRON_GOLEM_STEP, 0.8f, 0.6f);
	}

	/** Steps the climb along with its clanks, keeps the seated pilot breathing, and climbs them out when they've held sneak. */
	private void tickCrew(ServerLevel level) {
		Player pilot = pilot();
		int sequence = sequence();
		if (pilot == null) {
			if (sequence != SEQ_NONE) {
				entityData.set(SEQUENCE, (byte) SEQ_NONE);
			}
			exitHold = 0;
			if (held() != null) {
				dropHeld();
			}
			return;
		}
		int ticks = (int) (level.getGameTime() - entityData.get(SEQUENCE_START));
		Vec3 hatch = MechCrew.torsoPoint(this, 1.0f, 0.0, 14.0 / 16.0, 0.8);
		pilot.resetFallDistance();
		if (sequence == SEQ_ENTERING) {
			if (ticks == 8 || ticks == 13 || ticks == 18 || ticks == 23) {
				level.playSound(null, pilot.getX(), pilot.getY(), pilot.getZ(), SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 0.8f, 0.8f);
			}
			if (ticks == 25) {
				level.playSound(null, hatch.x, hatch.y, hatch.z, Mechs.HATCH_OPEN_SOUND, SoundSource.NEUTRAL, 1.0f, 0.8f);
			}
			if (ticks == 36) {
				level.playSound(null, hatch.x, hatch.y, hatch.z, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.0f, 0.7f);
			}
			if (ticks == 43) {
				level.playSound(null, hatch.x, hatch.y, hatch.z, Mechs.HATCH_CLOSE_SOUND, SoundSource.NEUTRAL, 1.0f, 0.8f);
			}
			if (ticks >= MechCrew.ENTER_TICKS) {
				entityData.set(SEQUENCE, (byte) SEQ_NONE);
				if (pilot instanceof ServerPlayer player) {
					player.sendOverlayMessage(Component.translatable("hud.mymod.mech.online").withStyle(ChatFormatting.GREEN));
				}
				level.playSound(null, getX(), getY() + 2.0, getZ(), Mechs.POWER_UP_SOUND, SoundSource.NEUTRAL, 1.0f, 1.0f);
			}
			return;
		}
		if (sequence == SEQ_EXITING) {
			if (ticks == 1) {
				level.playSound(null, hatch.x, hatch.y, hatch.z, Mechs.HATCH_OPEN_SOUND, SoundSource.NEUTRAL, 1.0f, 0.8f);
			}
			if (ticks == 16 || ticks == 20 || ticks == 24 || ticks == 28) {
				level.playSound(null, pilot.getX(), pilot.getY(), pilot.getZ(), SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 0.8f, 0.8f);
			}
			if (ticks == 18) {
				level.playSound(null, hatch.x, hatch.y, hatch.z, Mechs.HATCH_CLOSE_SOUND, SoundSource.NEUTRAL, 0.8f, 0.9f);
			}
			if (ticks >= MechCrew.EXIT_TICKS) {
				pilot.stopRiding();
				entityData.set(SEQUENCE, (byte) SEQ_NONE);
			}
			return;
		}
		// Seated: sealed in with its own air, and holding sneak climbs out (letting go of anything in a fist first).
		pilot.setAirSupply(pilot.getMaxAirSupply());
		boolean sneaking = pilot instanceof ServerPlayer player && player.getLastClientInput().shift();
		exitHold = sneaking ? exitHold + 1 : 0;
		if (exitHold >= MechCrew.EXIT_HOLD_TICKS) {
			exitHold = 0;
			for (MechArm arm : arms) {
				arm.release();
			}
			if (held() != null) {
				dropHeld();
			}
			throttle = 0.0f;
			startSequence(SEQ_EXITING);
		}
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		Vec3 at;
		if (isHeld(passenger)) {
			at = MechCrew.inFist(this, Math.max(0, heldSide()), passenger, 1.0f);
		} else {
			at = MechCrew.pilotPosition(this, passenger, sequence(), sequenceProgress(0.0f), 1.0f);
		}
		// Vanilla lowers a rider by its own vehicle attachment (a player sits 0.6 down): undo that, so it's exactly here.
		return at.subtract(position()).add(passenger.getVehicleAttachmentPoint(this));
	}

	/** Only the walker's own climb-in and its fists put anything aboard. */
	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	protected void removePassenger(Entity passenger) {
		boolean wasHeld = isHeld(passenger);
		super.removePassenger(passenger);
		if (wasHeld && !level().isClientSide()) {
			entityData.set(HELD, -1);
		}
	}

	/** Climbing out ends on the ground in front of the legs if there's room, else beside or behind them. */
	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		if (isHeld(passenger)) {
			return MechCrew.inFist(this, Math.max(0, heldSide()), passenger, 1.0f);
		}
		List<Vec3> spots = List.of(MechCrew.legsPoint(this, 1.0f, -0.5, 0.0, 2.1), MechCrew.legsPoint(this, 1.0f, -2.1, 0.0, 0.0),
				MechCrew.legsPoint(this, 1.0f, 2.1, 0.0, 0.0), MechCrew.legsPoint(this, 1.0f, 0.0, 0.0, -2.1));
		for (Vec3 spot : spots) {
			for (double dy : new double[] {0.0, 1.0, -1.0}) {
				Vec3 at = spot.add(0.0, dy, 0.0);
				if (DismountHelper.canDismountTo(level(), passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(at))) {
					return at;
				}
			}
		}
		return super.getDismountLocationForPassenger(passenger);
	}

	// --- Walking -------------------------------------------------------------------------------------------

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return pilot();
	}

	@Override
	protected void tickRidden(Player driver, Vec3 input) {
		super.tickRidden(driver, input);
		if (!isLocalInstanceAuthoritative()) {
			return;
		}
		boolean seated = isSeated(driver);
		float forward = seated ? driver.zza : 0.0f;
		float strafe = seated ? driver.xxa : 0.0f;
		float legs = getYRot();
		float turnRate = LEG_TURN * (0.5f + 0.5f * partCondition(MechPart.LEGS));
		float turn = 0.0f;
		float target = 0.0f;
		if (forward != 0.0f || strafe != 0.0f) {
			// W/A/S/D are relative to the torso; the legs turn to the way that points, or back onto it to walk backwards.
			float desired = torsoYaw - (float) Math.toDegrees(Math.atan2(strafe, forward));
			boolean backwards = Math.abs(Mth.wrapDegrees(desired - legs)) > 115.0f;
			float off = Mth.wrapDegrees((backwards ? desired + 180.0f : desired) - legs);
			turn = Mth.clamp(off, -turnRate, turnRate);
			float aligned = Mth.cos((off - turn) * Mth.DEG_TO_RAD);
			target = (backwards ? -REVERSE : 1.0f) * Mth.clamp((aligned - 0.75f) / 0.25f, 0.0f, 1.0f);
		} else if (seated) {
			// Looking further round than the torso turns: the legs pivot after the view.
			float twist = Mth.wrapDegrees(driver.getYRot() - legs);
			if (Math.abs(twist) > TWIST_LIMIT) {
				turn = Math.signum(twist) * Math.min(turnRate, Math.abs(twist) - TWIST_LIMIT + turnRate);
			}
		}
		if (turn != 0.0f) {
			setYRot(legs + turn);
			yBodyRot = getYRot();
		}
		boolean braking = target == 0.0f || throttle != 0.0f && Math.signum(target) != Math.signum(throttle);
		float rate = braking ? BRAKING : ACCELERATION;
		throttle = throttle < target ? Math.min(target, throttle + rate) : Math.max(target, throttle - rate);
	}

	@Override
	protected Vec3 getRiddenInput(Player driver, Vec3 selfInput) {
		return Math.abs(throttle) < 0.005f ? Vec3.ZERO : new Vec3(0.0, 0.0, Math.signum(throttle));
	}

	@Override
	protected float getRiddenSpeed(Player driver) {
		return Math.abs(throttle) * MAX_SPEED * speedFactor();
	}

	/** How much of its top speed it has: battered legs, no power and wading all slow it. */
	public float speedFactor() {
		return (0.4f + 0.6f * partCondition(MechPart.LEGS)) * (hasPower() ? 1.0f : 0.35f) * (isInWater() ? 0.6f : 1.0f);
	}

	/** On the pilot's client: the throttle, -{@value #REVERSE} (full reverse) to 1. */
	public float throttle() {
		return throttle;
	}

	/** Stops short of a ceiling the head and pauldrons (above the collision box) would walk into. */
	@Override
	public void travel(Vec3 input) {
		if (input.z != 0.0 && headBlocked(input.z > 0.0)) {
			input = Vec3.ZERO;
			Vec3 motion = getDeltaMovement();
			setDeltaMovement(0.0, motion.y, 0.0);
			throttle = 0.0f;
		}
		super.travel(input);
	}

	private boolean headBlocked(boolean forwards) {
		double sign = forwards ? 1.0 : -1.0;
		for (double reach : new double[] {0.6, 1.3}) {
			for (double right : new double[] {-1.2, 0.0, 1.2}) {
				Vec3 p = MechCrew.legsPoint(this, 1.0f, right, 4.25, sign * reach);
				BlockPos pos = BlockPos.containing(p);
				BlockState state = level().getBlockState(pos);
				if (!state.getCollisionShape(level(), pos).isEmpty() && !Tank.crushable(state)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Wading: it walks along the bottom, neither swimming nor floating, and the current doesn't carry it. */
	@Override
	public boolean isAffectedByFluids() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	/** On the pilot's client: whether the jets can fire now (charged, powered, and standing or wading). */
	public boolean jetsReady() {
		return jetCharge() >= 0.999f && hasPower() && (onGround() || isInWater());
	}

	/** On the pilot's client: the jets' kick, up and (walking) along the legs. The server burns the fuel. */
	public void jetBurst() {
		float legs = 0.6f + 0.4f * partCondition(MechPart.LEGS);
		Vec3 along = Vec3.directionFromRotation(0.0f, getYRot()).scale(JET_PUSH * Math.signum(throttle) * legs);
		Vec3 motion = getDeltaMovement();
		setDeltaMovement(motion.x + along.x, JET_LIFT * legs, motion.z + along.z);
		jetFlame = JET_FLAME_TICKS;
	}

	/** The server's side of the jets: they fire if charged and powered. */
	void jetsFired(ServerPlayer pilot) {
		if (jetCharge < 0.999f || !hasPower()) {
			return;
		}
		jetCharge = 0.0f;
		drain(JET_FUEL);
		ServerLevel level = (ServerLevel) level();
		level.playSound(null, getX(), getY() + 1.0, getZ(), Mechs.JETS_SOUND, SoundSource.NEUTRAL, 2.0f, 0.8f);
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 30, 1.2, 0.1, 1.2, 0.08);
		level.broadcastEntityEvent(this, JETS_EVENT);
	}

	public float jetCharge() {
		return level().isClientSide() ? entityData.get(JETS) / 1000.0f : jetCharge;
	}

	/** Client side: the jets' flames from the nozzles low on the back, for a moment after a burst. */
	private void tickJetFlames() {
		if (jetFlame <= 0) {
			return;
		}
		for (int side = -1; side <= 1; side += 2) {
			Vec3 nozzle = MechCrew.torsoPoint(this, 1.0f, side * 0.31, 0.15, -0.85);
			level().addParticle(ParticleTypes.FLAME, nozzle.x, nozzle.y, nozzle.z, 0.0, -0.25, 0.0);
			level().addParticle(ParticleTypes.LARGE_SMOKE, nozzle.x, nozzle.y - 0.3, nozzle.z, 0.0, -0.05, 0.0);
		}
	}

	/** Client side: how long the jet flames still burn, 0 to 1. */
	public float jetFlame(float partialTicks) {
		return jetFlame <= 0 ? 0.0f : Mth.clamp((jetFlame - partialTicks) / JET_FLAME_TICKS, 0.0f, 1.0f);
	}

	/** Server: a stomp each stride while walking on something. */
	private void tickStride(ServerLevel level) {
		if (!onGround() && !isInWater()) {
			return;
		}
		strideTravel += position().subtract(xo, yo, zo).horizontalDistance();
		if (strideTravel >= STRIDE) {
			strideTravel -= STRIDE;
			level.broadcastEntityEvent(this, STOMP_EVENT);
			playSound(Mechs.STEP_SOUND, 1.2f, 0.9f + random.nextFloat() * 0.15f);
		}
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// Its stride stomps instead (see tickStride).
	}

	/** No fall damage: past {@link #SHOCKWAVE_FALL} blocks a landing knocks back and hurts everything round it. */
	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		if (level() instanceof ServerLevel level && fallDistance >= 1.5) {
			level.broadcastEntityEvent(this, LAND_EVENT);
			if (fallDistance >= SHOCKWAVE_FALL) {
				shockwave(level, fallDistance);
			} else {
				playSound(Mechs.STEP_SOUND, 1.5f, 0.7f);
			}
		}
		return false;
	}

	private void shockwave(ServerLevel level, double fallDistance) {
		double strength = Math.min(fallDistance, 30.0);
		double radius = Math.min(3.0 + strength * 0.2, 8.0);
		float damage = (float) Math.min(strength * 0.8, 24.0);
		Player pilot = pilot();
		DamageSource source = damage(Mechs.SHOCKWAVE_DAMAGE, pilot);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 2.0, radius),
				target -> target != this && !hasPassenger(target) && target.isAlive() && !target.isSpectator())) {
			double distance = target.distanceTo(this);
			if (distance > radius) {
				continue;
			}
			float falloff = (float) (1.0 - distance / radius * 0.6);
			target.hurtServer(level, source, damage * falloff);
			Vec3 away = target.position().subtract(position()).horizontal();
			away = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
			double push = (0.6 + strength * 0.03) * falloff;
			target.push(away.x * push, 0.35 + strength * 0.01 * falloff, away.z * push);
			target.needsSync = true;
		}
		BlockState ground = level.getBlockState(blockPosition().below());
		if (!ground.isAir()) {
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			for (int i = 0; i < 32; i++) {
				float angle = Mth.TWO_PI * i / 32.0f;
				double r = radius * 0.6;
				level.sendParticles(dust, getX() + Mth.cos(angle) * r, getY() + 0.1, getZ() + Mth.sin(angle) * r, 4, 0.3, 0.05, 0.3, 0.1);
			}
		}
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 40, radius * 0.4, 0.1, radius * 0.4, 0.08);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 3, 1.0, 0.1, 1.0, 0.0);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.NEUTRAL, 2.0f, 0.6f);
		level.playSound(null, getX(), getY(), getZ(), Mechs.LAND_SOUND, SoundSource.NEUTRAL, 2.0f, 0.7f);
		// The legs take the strain.
		wearPart(MechPart.LEGS, (float) (strength / 150.0));
	}

	// --- Crushing and shoving --------------------------------------------------------------------------------

	/** Breaks weak blocks it walks into, and shoulders mobs out of the way. */
	private void tickTerrain(ServerLevel level) {
		Vec3 moved = position().subtract(xo, yo, zo);
		double speed = moved.horizontalDistance();
		if (speed < 0.01) {
			return;
		}
		if (tickCount % 2 == 0) {
			crushBlocks(level, moved);
		}
		shove(level, moved, speed);
	}

	private void crushBlocks(ServerLevel level, Vec3 moved) {
		Player pilot = pilot();
		boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
		if (pilot == null && !griefing || pilot != null && !pilot.mayBuild()) {
			return;
		}
		Vec3 ahead = moved.horizontal().normalize().scale(0.8);
		AABB box = getBoundingBox().expandTowards(ahead.x, 0.0, ahead.z).deflate(0.05, 0.0, 0.05);
		Set<BlockPos> checked = new HashSet<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY + 0.2, box.minZ),
				BlockPos.containing(box.maxX, box.maxY - 0.2, box.maxZ))) {
			BlockPos at = pos.immutable();
			if (!checked.add(at)) {
				continue;
			}
			BlockState state = level.getBlockState(at);
			if (Tank.crushable(state) && (pilot == null || level.mayInteract(pilot, at))) {
				level.destroyBlock(at, true, this);
			}
		}
	}

	private void shove(ServerLevel level, Vec3 moved, double speed) {
		Vec3 ahead = moved.horizontal().normalize();
		for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6, 0.0, 0.6),
				e -> e.isAlive() && e != this && !hasPassenger(e) && !(e instanceof Mech) && !(e instanceof Tank) && !e.isSpectator())) {
			Vec3 away = mob.position().subtract(position()).horizontal();
			away = away.lengthSqr() < 1.0e-4 ? ahead : away.normalize();
			Vec3 push = away.scale(0.35).add(ahead.scale(0.3));
			mob.push(push.x, 0.1, push.z);
			if (speed > SHOVE_SPEED && mob instanceof ServerPlayer) {
				mob.needsSync = true;
			}
		}
	}

	// --- Power ------------------------------------------------------------------------------------------------

	/** Whether there's a charged core: walking at full speed, jets and weapons all need it. */
	public boolean hasPower() {
		return level().isClientSide() ? entityData.get(FUEL) > 0 : !loadout.getItem(MechLoadout.CORE).isEmpty() && fuel > 0.0f;
	}

	/** The core's charge, 0 to 1, or -1 with no core. */
	public float fuel() {
		int fuel = entityData.get(FUEL);
		return fuel < 0 ? -1.0f : fuel / 1000.0f;
	}

	/** Burns charge from the core; a battered torso wastes more of it. */
	void drain(float amount) {
		ItemStack core = loadout.getItem(MechLoadout.CORE);
		if (core.isEmpty() || fuel <= 0.0f) {
			return;
		}
		fuel = Math.max(0.0f, fuel - amount * (1.0f + 0.6f * (1.0f - partCondition(MechPart.TORSO))));
		if (fuel <= 0.0f) {
			pilotMessage(Component.translatable("hud.mymod.mech.core_empty").withStyle(ChatFormatting.RED));
			playSound(Mechs.POWER_DOWN_SOUND, 1.0f, 1.0f);
		} else if (!warnedLowFuel && fuel < FusionCoreItem.MAX_CHARGE * LOW_FUEL) {
			warnedLowFuel = true;
			pilotMessage(Component.translatable("hud.mymod.mech.core_low").withStyle(ChatFormatting.GOLD));
			pilotSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.8f, 0.6f);
		}
	}

	private void tickPower() {
		if (seatedPilot() != null) {
			drain(IDLE_DRAIN);
			if (position().subtract(xo, yo, zo).horizontalDistanceSqr() > 1.0e-4) {
				drain(WALK_DRAIN);
			}
		}
		if (jetCharge < 1.0f && hasPower()) {
			jetCharge = Math.min(1.0f, jetCharge + 1.0f / JET_RECHARGE_TICKS);
		}
		entityData.set(JETS, Math.round(jetCharge * 1000.0f));
		if (tickCount % 20 == 0) {
			flushFuel();
		}
		syncFuel();
	}

	private void syncFuel() {
		ItemStack core = loadout.getItem(MechLoadout.CORE);
		if (level().isClientSide()) {
			return;
		}
		entityData.set(FUEL, core.isEmpty() ? -1 : fuel <= 0.0f ? 0 : Math.max(1, (int) (fuel * 1000.0f / FusionCoreItem.MAX_CHARGE)));
		int look = entityData.get(LOOK);
		boolean lit = !core.isEmpty() && fuel > 0.0f;
		if (MechLoadout.coreLit(look) != lit) {
			entityData.set(LOOK, lit ? look | 1 << 13 : look & ~(1 << 13));
		}
	}

	/** Writes the charge back onto the core item. */
	private void flushFuel() {
		ItemStack core = loadout.getItem(MechLoadout.CORE);
		if (core.getItem() instanceof FusionCoreItem) {
			FusionCoreItem.setCharge(core, Mth.ceil(fuel));
		}
	}

	/** Puts a new core in the power plant; returns the old one (or empty). */
	private ItemStack swapCore(ItemStack core) {
		flushFuel();
		ItemStack old = loadout.getItem(MechLoadout.CORE);
		loadout.setItem(MechLoadout.CORE, core);
		fuel = FusionCoreItem.charge(core);
		warnedLowFuel = false;
		loadoutChanged();
		return old;
	}

	// --- Stores -----------------------------------------------------------------------------------------------

	/** Takes one matching item from the Cargo Pod, or failing that the pilot's inventory. A creative pilot's isn't used up. */
	ItemStack takeFromStores(Predicate<ItemStack> filter) {
		ServerPlayer pilot = pilotServer();
		if (hasCargoPod()) {
			for (int i = 0; i < cargo.getContainerSize(); i++) {
				ItemStack stack = cargo.getItem(i);
				if (!stack.isEmpty() && filter.test(stack)) {
					return cargo.removeItem(i, 1);
				}
			}
		}
		if (pilot != null) {
			Inventory inventory = pilot.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.isEmpty() && filter.test(stack)) {
					return pilot.hasInfiniteMaterials() ? stack.copyWithCount(1) : inventory.removeItem(i, 1);
				}
			}
		}
		return ItemStack.EMPTY;
	}

	int countInStores(Predicate<ItemStack> filter) {
		int count = 0;
		if (hasCargoPod()) {
			for (ItemStack stack : cargo.getItems()) {
				if (!stack.isEmpty() && filter.test(stack)) {
					count += stack.getCount();
				}
			}
		}
		if (pilotServer() instanceof ServerPlayer pilot) {
			for (ItemStack stack : pilot.getInventory()) {
				if (!stack.isEmpty() && filter.test(stack)) {
					count += stack.getCount();
				}
			}
		}
		return count;
	}

	/** Puts something in the hold, or the pilot's pockets, or drops it if neither has room. */
	void giveToStores(ItemStack stack) {
		ItemStack left = hasCargoPod() ? cargo.addItem(stack) : stack;
		if (!left.isEmpty() && pilotServer() instanceof ServerPlayer pilot) {
			pilot.getInventory().placeItemBackInInventory(left, Prediction.SERVER_ONLY);
			return;
		}
		if (!left.isEmpty() && level() instanceof ServerLevel level) {
			spawnAtLocation(level, left, 1.0f);
		}
	}

	private void tickStores() {
		int packed = Math.min(4095, arms[0].reserve()) | Math.min(4095, arms[1].reserve()) << 12 | smoke << 24;
		if (entityData.get(STORES) != packed) {
			entityData.set(STORES, packed);
		}
	}

	public int reserve(int side) {
		return entityData.get(STORES) >> (side == MechArm.LEFT ? 0 : 12) & 4095;
	}

	public int smokeSalvos() {
		return entityData.get(STORES) >> 24 & 7;
	}

	void openCargo(ServerPlayer player) {
		if (!hasCargoPod()) {
			player.sendOverlayMessage(Component.translatable("hud.mymod.mech.no_cargo").withStyle(ChatFormatting.RED));
			return;
		}
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MechCargoMenu(id, inventory, cargo, this),
				Component.translatable("container.mymod.mech_cargo")));
	}

	// --- Smoke --------------------------------------------------------------------------------------------------

	private void tickSmoke() {
		if (shoulder() != MechShoulder.SMOKE_LAUNCHER || smoke >= MAX_SMOKE || ++smokeRefill < SMOKE_REFILL_TICKS) {
			return;
		}
		smokeRefill = 0;
		if (countInStores(stack -> stack.is(Items.GUNPOWDER)) >= SMOKE_GUNPOWDER) {
			for (int i = 0; i < SMOKE_GUNPOWDER; i++) {
				takeFromStores(stack -> stack.is(Items.GUNPOWDER));
			}
			smoke++;
		}
	}

	void fireSmoke(ServerPlayer pilot) {
		if (shoulder() != MechShoulder.SMOKE_LAUNCHER) {
			pilot.sendOverlayMessage(Component.translatable("hud.mymod.mech.no_smoke_launcher").withStyle(ChatFormatting.RED));
			return;
		}
		if (smoke <= 0 || !hasPower()) {
			pilot.sendOverlayMessage(Component.translatable(hasPower() ? "hud.mymod.mech.no_smoke" : "hud.mymod.mech.no_power")
					.withStyle(ChatFormatting.RED));
			pilotSound(SoundEvents.DISPENSER_FAIL, 0.6f, 1.0f);
			return;
		}
		ServerLevel level = (ServerLevel) level();
		smoke--;
		smokeRefill = 0;
		drain(SMOKE_FUEL);
		float yaw = torsoYaw;
		for (int i = 0; i < SMOKE_GRENADES; i++) {
			float spreadDegrees = -45.0f + 90.0f * i / (SMOKE_GRENADES - 1);
			Vec3 from = MechCrew.torsoPoint(this, 1.0f, 0.0, 33.0 / 16.0, -0.6);
			Vec3 to = MechCrew.framePoint(position(), yaw + spreadDegrees, 0.0, 0.6, 14.0 + random.nextFloat() * 3.0f);
			SmokeScreens.launch(level, from, to);
		}
		level.playSound(null, getX(), getY() + 4.0, getZ(), Mechs.SMOKE_SOUND, SoundSource.NEUTRAL, 2.0f, 0.9f);
	}

	// --- Aiming and hitting ------------------------------------------------------------------------------------------

	/** Where the arms converge this tick: what the torso's sight line from the viewport runs into. */
	AimPoint aimPoint(ServerLevel level) {
		if (aimTick == level.getGameTime()) {
			return aim;
		}
		aimTick = level.getGameTime();
		Vec3 from = MechCrew.viewport(this, 1.0f);
		Vec3 to = from.add(MechCrew.aim(this, 1.0f).scale(AIM_RANGE));
		HitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (block.getType() != HitResult.Type.MISS) {
			to = block.getLocation();
		}
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(this, from, to, new AABB(from, to).inflate(1.0), this::canStrike,
				from.distanceToSqr(to));
		aim = entity != null ? new AimPoint(entity.getLocation(), entity.getEntity()) : new AimPoint(to, null);
		return aim;
	}

	/** Whether the arms' shots and punches can land on something: not the walker, its pilot, or what it holds. */
	boolean canStrike(Entity entity) {
		return entity != this && !hasPassenger(entity) && entity.isAlive() && entity.isPickable() && !entity.isSpectator();
	}

	/** Whether a fist can pick something up: a dropped item, or a mob or player no bigger than an iron golem. Never a boss. */
	boolean canGrab(Entity entity) {
		if (entity == this || entity.isPassenger() || entity.isVehicle() || !entity.isAlive() || entity.isSpectator()) {
			return false;
		}
		if (entity instanceof ItemEntity) {
			return true;
		}
		return entity instanceof LivingEntity && !(entity instanceof Mech) && !(entity instanceof Tank)
				&& !entity.is(ConventionalEntityTypeTags.BOSSES) && entity.getBbWidth() <= 1.5f && entity.getBbHeight() <= 3.0f;
	}

	DamageSource damage(ResourceKey<DamageType> type, @Nullable Entity attacker) {
		return new DamageSource(level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), this,
				attacker != null ? attacker : this);
	}

	/** An arm fired: everyone's client kicks it back. */
	void armFired(int side) {
		level().broadcastEntityEvent(this, side == MechArm.LEFT ? FIRE_LEFT_EVENT : FIRE_RIGHT_EVENT);
	}

	// --- Holding and throwing -------------------------------------------------------------------------------------

	void grab(Entity entity, int side) {
		if (!entity.startRiding(this, true, false)) {
			return;
		}
		entityData.set(HELD, entity.getId() << 1 | side);
		drain(MechArm.GRAB_FUEL);
		playSound(Mechs.GRAB_SOUND, 1.2f, 0.8f);
		if (entity instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("hud.mymod.mech.grabbed_you").withStyle(ChatFormatting.RED));
		}
		pilotMessage(Component.translatable("hud.mymod.mech.holding", entity.getDisplayName()).withStyle(ChatFormatting.GRAY));
	}

	/** Hurls whatever's held along the arms' aim. */
	void throwHeld() {
		Entity held = held();
		int side = heldSide();
		if (held == null || side < 0) {
			return;
		}
		Vec3 aim = MechCrew.aim(this, 1.0f);
		held.stopRiding();
		Vec3 at = MechCrew.inFist(this, side, held, 1.0f).add(aim.scale(0.8));
		held.setPos(at.x, at.y, at.z);
		Vec3 velocity = aim.scale(THROW_SPEED).add(0.0, 0.3, 0.0);
		held.setDeltaMovement(velocity);
		held.needsSync = true;
		thrown.add(new Thrown(held, new int[] {THROWN_TICKS}));
		drain(THROW_FUEL);
		playSound(Mechs.THROW_SOUND, 1.5f, 0.7f);
		armFired(side);
	}

	/** Lets go of whatever's held, gently. */
	void dropHeld() {
		Entity held = held();
		if (held != null) {
			held.stopRiding();
			held.setDeltaMovement(Vec3.ZERO);
		}
		entityData.set(HELD, -1);
	}

	/** Something thrown hurts itself and whatever it hits, on a wall or on another mob. */
	private void tickThrown(ServerLevel level) {
		for (Iterator<Thrown> it = thrown.iterator(); it.hasNext();) {
			Thrown throw_ = it.next();
			Entity entity = throw_.entity();
			if (--throw_.ticks()[0] <= 0 || !entity.isAlive() || entity.isPassenger()) {
				it.remove();
				continue;
			}
			double speed = entity.getDeltaMovement().length();
			DamageSource source = damage(Mechs.FIST_DAMAGE, pilot());
			boolean struck = false;
			if (entity.horizontalCollision && speed > 0.2) {
				entity.hurtServer(level, source, THROWN_IMPACT);
				struck = true;
			}
			for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(0.2),
					e -> e != entity && e != this && !hasPassenger(e) && e.isAlive() && !e.isSpectator())) {
				other.hurtServer(level, source, THROWN_IMPACT);
				Vec3 push = entity.getDeltaMovement().horizontal().normalize().scale(0.8);
				other.push(push.x, 0.3, push.z);
				other.needsSync = true;
				entity.hurtServer(level, source, THROWN_IMPACT * 0.5f);
				struck = true;
			}
			if (struck) {
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 1.0f, 0.6f);
				it.remove();
			} else if (entity.onGround() && throw_.ticks()[0] < THROWN_TICKS - 3) {
				it.remove();
			}
		}
	}

	// --- Readouts -----------------------------------------------------------------------------------------------

	public float partCondition(MechPart part) {
		return part.condition(entityData.get(PARTS));
	}

	/** An arm's packed state (see {@link MechArm}). */
	public int armState(int side) {
		return entityData.get(side == MechArm.LEFT ? LEFT_ARM : RIGHT_ARM);
	}

	/** How far an arm has kicked back from its last shot, 0 to 1. */
	public float recoil(int side, float partialTicks) {
		return recoil[side] <= 0 ? 0.0f : Mth.clamp((recoil[side] - partialTicks) / RECOIL_TICKS, 0.0f, 1.0f);
	}

	/** How far a rotary cannon's barrels have turned, in radians. */
	public float barrelSpin(int side, float partialTicks) {
		return Mth.lerp(partialTicks, barrelSpinO[side], barrelSpin[side]);
	}

	@Override
	public void handleEntityEvent(byte id) {
		switch (id) {
			case STOMP_EVENT -> {
				clientStomps++;
				BlockState ground = level().getBlockState(blockPosition().below());
				if (!ground.isAir()) {
					for (int side = -1; side <= 1; side += 2) {
						Vec3 foot = MechCrew.legsPoint(this, 1.0f, side * 0.5, 0.05, 0.2);
						for (int i = 0; i < 6; i++) {
							level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), foot.x, foot.y, foot.z,
									(random.nextDouble() - 0.5) * 0.3, 0.1, (random.nextDouble() - 0.5) * 0.3);
						}
					}
				}
			}
			case FIRE_LEFT_EVENT -> recoil[MechArm.LEFT] = RECOIL_TICKS;
			case FIRE_RIGHT_EVENT -> recoil[MechArm.RIGHT] = RECOIL_TICKS;
			case JETS_EVENT -> jetFlame = JET_FLAME_TICKS;
			case LAND_EVENT -> clientLandings++;
			default -> super.handleEntityEvent(id);
		}
	}

	// --- Using it -------------------------------------------------------------------------------------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hasPassenger(player)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		float repair = held.is(Items.IRON_INGOT) ? 1.0f : held.is(Items.IRON_BLOCK) ? 9.0f : held.is(Industry.STEEL_INGOT) ? 2.0f
				: held.is(Industry.STEEL_BLOCK.asItem()) ? 18.0f : 0.0f;
		if (repair > 0.0f && needsRepair()) {
			if (!level().isClientSide()) {
				repair(repair);
				held.consume(1, player);
				playSound(SoundEvents.ANVIL_USE, 0.6f, 0.9f);
			}
			return InteractionResult.SUCCESS;
		}
		if (held.getItem() instanceof FusionCoreItem) {
			if (!level().isClientSide()) {
				ItemStack old = swapCore(held.copyWithCount(1));
				held.consume(1, player);
				if (!old.isEmpty()) {
					player.getInventory().placeItemBackInInventory(old, Prediction.SERVER_ONLY);
				}
				playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.8f);
				player.sendOverlayMessage(Component.translatable("hud.mymod.mech.core_swapped").withStyle(ChatFormatting.GREEN));
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (player instanceof ServerPlayer serverPlayer) {
				openCargo(serverPlayer);
			}
			return InteractionResult.SUCCESS;
		}
		if (pilot() != null) {
			if (!level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable("entity.mymod.mech.piloted").withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		if (player.isPassenger() || PowerArmor.isWearing(player)) {
			if (!level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable(PowerArmor.isWearing(player) ? "entity.mymod.mech.no_power_armor"
						: "entity.mymod.mech.cant_board").withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			board(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	private boolean needsRepair() {
		return getHealth() < getMaxHealth() || !MechPart.allSound(entityData.get(PARTS));
	}

	/** Patches the hull and every part: {@code ingots} iron ingots' worth (steel counts double). */
	private void repair(float ingots) {
		heal(6.0f * ingots);
		int parts = entityData.get(PARTS);
		for (MechPart part : MechPart.values()) {
			parts = part.with(parts, part.condition(parts) + 0.03f * ingots);
		}
		entityData.set(PARTS, parts);
	}

	private void wearPart(MechPart part, float amount) {
		int parts = entityData.get(PARTS);
		entityData.set(PARTS, part.with(parts, part.condition(parts) - amount));
	}

	// --- Damage ---------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity attacker = source.getEntity();
		// Its pilot (and what it holds) can't hurt it.
		if (attacker != null && hasPassenger(attacker)) {
			return false;
		}
		float armoured = damage * armourFactor(source);
		if (armoured <= 0.0f) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, armoured);
		if (hurt && armoured >= PART_HIT_THRESHOLD) {
			damagePart(source, armoured);
		}
		return hurt;
	}

	/** How much of a hit gets through: all of an explosion or tank round, a fifth of small arms, half if armour piercing. */
	private static float armourFactor(DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 1.0f;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)
				|| source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.CACTUS)) {
			return 0.0f;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return 1.25f;
		}
		if (source.getDirectEntity() instanceof TankShell) {
			return 1.0f;
		}
		if (source.is(Napalm.NAPALM) || source.is(Napalm.NAPALM_BURN)) {
			return 0.15f;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return 0.1f;
		}
		if (source.is(Mechs.FIST_DAMAGE) || source.is(Mechs.SHOCKWAVE_DAMAGE)) {
			return 0.6f;
		}
		if (source.is(ModDamageTypes.ARMOR_PIERCING_BULLET) || source.is(ModDamageTypes.PENETRATOR_BULLET)
				|| source.is(ModDamageTypes.TUNGSTEN_CORE_BULLET) || source.is(ModDamageTypes.DEPLETED_URANIUM_BULLET)) {
			return 0.5f;
		}
		return 0.2f;
	}

	/** Wears whichever part a heavy hit landed on, and tells the pilot. */
	private void damagePart(DamageSource source, float damage) {
		Vec3 at = source.getSourcePosition();
		MechPart part;
		if (at == null) {
			part = MechPart.values()[random.nextInt(MechPart.values().length)];
		} else {
			Vec3 offset = at.subtract(position());
			float yaw = torsoYaw * Mth.DEG_TO_RAD;
			double right = -Mth.cos(yaw) * offset.x - Mth.sin(yaw) * offset.z;
			if (offset.y < 2.2) {
				part = MechPart.LEGS;
			} else if (Math.abs(right) > 0.8 && offset.y < 3.9) {
				part = right < 0.0 ? MechPart.LEFT_ARM : MechPart.RIGHT_ARM;
			} else {
				part = MechPart.TORSO;
			}
		}
		float before = partCondition(part);
		wearPart(part, damage / 140.0f);
		float after = partCondition(part);
		if (before - after > 0.01f) {
			pilotMessage(Component.translatable("hud.mymod.mech.part_hit", Component.translatable(part.nameKey()))
					.withStyle(after < 0.35f ? ChatFormatting.RED : ChatFormatting.GOLD));
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level && !isRemoved() && !dead) {
			// The pilot is thrown clear (and singed), what's held is dropped, then it goes up.
			Player pilot = pilot();
			Entity held = held();
			for (Entity passenger : List.copyOf(getPassengers())) {
				passenger.stopRiding();
			}
			entityData.set(HELD, -1);
			if (pilot != null) {
				Vec3 away = Vec3.directionFromRotation(-50.0f, torsoYaw + 180.0f).scale(1.2);
				pilot.setDeltaMovement(away);
				pilot.needsSync = true;
				pilot.igniteForSeconds(3.0f);
				pilot.hurtServer(level, damageSources().explosion(this, source.getEntity()), 6.0f);
			}
			flushFuel();
			ExplosionDamageCalculator spare = new ExplosionDamageCalculator() {
				@Override
				public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
					return entity != pilot && entity != held && super.shouldDamageEntity(explosion, entity);
				}
			};
			level.explode(this, damageSources().explosion(this, source.getEntity()), spare, getX(), getY() + 2.0, getZ(), DEATH_BLAST, false,
					Level.ExplosionInteraction.MOB);
		}
		super.die(source);
	}

	/** The hold's contents, the core, and each weapon and the shoulder part half the time. Scrap comes from the loot table. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (ItemStack stack : cargo.removeAllItems()) {
			spawnAtLocation(level, stack, 2.0f);
		}
		ItemStack core = loadout.getItem(MechLoadout.CORE);
		if (!core.isEmpty()) {
			spawnAtLocation(level, core.copy(), 2.0f);
		}
		for (int slot : new int[] {MechLoadout.LEFT_WEAPON, MechLoadout.RIGHT_WEAPON, MechLoadout.SHOULDER}) {
			ItemStack part = loadout.getItem(slot);
			if (!part.isEmpty() && random.nextBoolean()) {
				ItemStack salvaged = part.copy();
				salvaged.remove(DataComponents.CONTAINER);
				spawnAtLocation(level, salvaged, 2.0f);
			}
		}
		loadout.clearContent();
	}

	@Override
	protected void tickDeath() {
		super.tickDeath();
		if (level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 2.5, getZ(), 4, 0.8, 0.8, 0.8, 0.02);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 3.5, getZ(), 2, 0.6, 0.3, 0.6, 0.02);
		}
	}

	// --- Bits and pieces -------------------------------------------------------------------------------------

	/** Other things bump into it (and can stand on it); its pilot doesn't. */
	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return isAlive() && (other == null || !hasPassenger(other) && other.getVehicle() != this);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		flushFuel();
		output.putFloat("TorsoYaw", torsoYaw);
		output.putFloat("ArmPitch", armPitch);
		output.putInt("Parts", entityData.get(PARTS));
		output.putFloat("Fuel", fuel);
		output.putFloat("Jets", jetCharge);
		output.putInt("Smoke", smoke);
		ContainerHelper.saveAllItems(output.child("Loadout"), loadout.getItems());
		ContainerHelper.saveAllItems(output.child("Cargo"), cargo.getItems());
		arms[0].save(output.child("LeftArm"));
		arms[1].save(output.child("RightArm"));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		torsoYaw = input.getFloatOr("TorsoYaw", getYRot());
		armPitch = input.getFloatOr("ArmPitch", 0.0f);
		torsoYawO = torsoYaw;
		armPitchO = armPitch;
		if (input.child("Loadout").isPresent()) {
			loadout.clearContent();
			ContainerHelper.loadAllItems(input.childOrEmpty("Loadout"), loadout.getItems());
			cargo.clearContent();
			ContainerHelper.loadAllItems(input.childOrEmpty("Cargo"), cargo.getItems());
		}
		entityData.set(PARTS, input.getIntOr("Parts", MechPart.ALL_SOUND));
		ItemStack core = loadout.getItem(MechLoadout.CORE);
		fuel = input.getFloatOr("Fuel", core.getItem() instanceof FusionCoreItem ? FusionCoreItem.charge(core) : 0.0f);
		jetCharge = input.getFloatOr("Jets", 1.0f);
		smoke = Mth.clamp(input.getIntOr("Smoke", MAX_SMOKE), 0, MAX_SMOKE);
		arms[0].load(input.childOrEmpty("LeftArm"));
		arms[1].load(input.childOrEmpty("RightArm"));
		loadoutChanged();
	}

	/** A walker put out of a gantry, facing the way it does, with the given parts. Null if there's no room for it. */
	static @Nullable Mech assemble(ServerLevel level, Vec3 at, float yaw, List<ItemStack> parts) {
		Mech mech = Mechs.MECH.create(level, EntitySpawnReason.TRIGGERED);
		if (mech == null) {
			return null;
		}
		mech.snapTo(at.x, at.y, at.z, yaw, 0.0f);
		mech.setYHeadRot(yaw);
		mech.setYBodyRot(yaw);
		mech.alignTorso();
		if (!level.noCollision(mech, mech.getBoundingBox())) {
			return null;
		}
		mech.install(parts);
		level.addFreshEntity(mech);
		return mech;
	}

	/** Server: a control from the pilot's keys. */
	void control(ServerPlayer pilot, int action) {
		switch (action) {
			case Mechs.Control.LEFT_DOWN -> arms[MechArm.LEFT].triggerDown();
			case Mechs.Control.LEFT_UP -> arms[MechArm.LEFT].triggerUp();
			case Mechs.Control.RIGHT_DOWN -> arms[MechArm.RIGHT].triggerDown();
			case Mechs.Control.RIGHT_UP -> arms[MechArm.RIGHT].triggerUp();
			case Mechs.Control.JETS -> jetsFired(pilot);
			case Mechs.Control.SMOKE -> fireSmoke(pilot);
			case Mechs.Control.CARGO -> openCargo(pilot);
			default -> {
			}
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	public float getVoicePitch() {
		return 0.6f;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ANVIL_LAND;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GENERIC_EXPLODE.value();
	}
}
