package com.jheinz.mymod.skullcrane;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.mymod.powerarmor.PowerArmor;
import com.jheinz.mymod.tank.Tank;

/**
 * The hold a skullcrane pack has on its prey: an invisible entity that keeps track of the skulls clamped on and,
 * once there are enough of them, carries the prey up and drops it. The prey rides it while it's carried, so the
 * server moves them (they can look about and swing at the skulls, but not walk, and sneaking doesn't get them off),
 * and the skulls are held in a ring over their shoulders, the grip placing them every tick.
 *
 * <ul>
 * <li>The first skull to clamp on makes the grip. Until there are {@link #holdersNeeded} of them (two for a
 * player-sized victim, one for a chicken or a child, {@value #POWER_ARMOR_HOLDERS} for someone in power armor; a pack
 * leader counts as {@value SkullcraneLeader#LIFT_WEIGHT}, so it lifts a player alone) they just cling on, weighing the victim down and scratching; after {@value #CLING_TICKS} ticks they give up.
 * <li>With enough on, they lift off, rising {@value #LIFT_SPEED} blocks a tick with just enough of them and
 * proportionally faster with more, up to {@value #MAX_LIFT_SPEED}: 3 blocks a second with two on a player, 6 with four,
 * 8 at most. More skulls can clamp on on the way up (up to {@value #MAX_HOLDERS}).
 * <li>{@value #MIN_DROP}-{@value #MAX_DROP} blocks over the ground (picked per lift), or at a ceiling, the top of the
 * world, or after {@value #MAX_LIFT_TICKS} ticks, they let go and the fall does the rest. So do they if they fall
 * below the number needed, through being killed or hit hard enough to let go (see {@link Skullcrane#hurtServer}),
 * so taking them out on the way up drops you from lower down.
 * <li>The victim dying, a creative player, a grip with no skulls left (say, reloaded with a player who logged out
 * mid-air), a victim that somehow got off: it lets go at once.
 * </ul>
 * Only prey no bigger than {@value #MAX_WIDTH} by {@value #MAX_HEIGHT} blocks is lifted, which leaves out golems,
 * horses and every boss; nobody shut in a tank either. Anyone riding something else is pulled off it.
 */
public class SkullcraneGrip extends Entity {
	public static final int MAX_HOLDERS = 6;
	static final float MAX_WIDTH = 0.8f;
	static final float MAX_HEIGHT = 2.1f;
	static final int POWER_ARMOR_HOLDERS = 5;
	static final double LIFT_SPEED = 0.15;
	static final double MAX_LIFT_SPEED = 0.4;
	static final int MIN_DROP = 20;
	static final int MAX_DROP = 28;
	static final int CLING_TICKS = 100;
	static final int MAX_LIFT_TICKS = 400;
	/** How long skulls hang back after letting go before they try again. */
	static final int REGROUP_TICKS = 100;
	/** Room the skulls need over the victim's head. */
	private static final double HEADROOM = 0.8;
	private static final int GROUND_SEARCH = 64;

	private final List<Skullcrane> holders = new ArrayList<>();
	private @Nullable LivingEntity victim;
	private boolean lifting;
	private double dropHeight;
	private int clingTicks;
	private int liftTicks;
	private int blockedTicks;
	private int humTicks;

	public SkullcraneGrip(EntityType<? extends SkullcraneGrip> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	// ------------------------------------------------------------------------------------------------ rules

	/** Whether skullcranes could carry this off at all, given enough of them. */
	public static boolean canLift(LivingEntity entity) {
		if (!entity.isAlive() || entity.isSpectator() || entity instanceof Skullcrane) {
			return false;
		}
		if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		if (entity.getVehicle() instanceof Tank) {
			return false;
		}
		return entity.getBbWidth() <= MAX_WIDTH && entity.getBbHeight() <= MAX_HEIGHT;
	}

	/** How many skulls it takes to lift this. */
	public static int holdersNeeded(LivingEntity entity) {
		if (PowerArmor.isWearing(entity)) {
			return POWER_ARMOR_HOLDERS;
		}
		float width = entity.getBbWidth();
		return width * width * entity.getBbHeight() < 0.25f ? 1 : 2;
	}

	/**
	 * Where a holder sits, relative to its victim's feet: in a ring over their shoulders, starting at their right one,
	 * every other one a little higher when there are more than two. Worked out the same on both sides.
	 */
	public static Vec3 holdOffset(float bodyYaw, float width, float height, int slot, int count) {
		float yaw = bodyYaw * Mth.DEG_TO_RAD;
		double angle = slot * Mth.TWO_PI / Math.max(count, 1);
		double rightX = -Mth.cos(yaw);
		double rightZ = -Mth.sin(yaw);
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);
		double radius = width * 0.5 + 0.25 + 0.06 * count;
		double x = (rightX * Math.cos(angle) + forwardX * Math.sin(angle)) * radius;
		double z = (rightZ * Math.cos(angle) + forwardZ * Math.sin(angle)) * radius;
		double y = height + 0.05 + (count > 2 && slot % 2 == 1 ? 0.25 : 0.0);
		return new Vec3(x, y, z);
	}

	/** The grip already on someone, if any. */
	static @Nullable SkullcraneGrip on(LivingEntity victim) {
		if (victim.getVehicle() instanceof SkullcraneGrip grip) {
			return grip;
		}
		for (SkullcraneGrip grip : victim.level().getEntitiesOfClass(SkullcraneGrip.class, victim.getBoundingBox().inflate(2.0))) {
			if (grip.victim == victim && !grip.isRemoved()) {
				return grip;
			}
		}
		return null;
	}

	/** A skull clamps on: onto the grip already on its victim, or a new one. */
	static void latch(ServerLevel level, Skullcrane skull, LivingEntity victim) {
		SkullcraneGrip grip = on(victim);
		if (grip == null) {
			grip = new SkullcraneGrip(Skullcranes.GRIP, level);
			grip.victim = victim;
			grip.snapTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0.0f);
			level.addFreshEntity(grip);
		}
		if (!grip.hasRoom() || grip.holders.contains(skull)) {
			return;
		}
		grip.holders.add(skull);
		skull.clampTo(grip);
		grip.placeHolders();
		level.playSound(null, skull.getX(), skull.getY(), skull.getZ(), Skullcranes.CLAMP, SoundSource.HOSTILE, 1.0f,
				0.9f + skull.getRandom().nextFloat() * 0.2f);
		// Its claws dig in.
		victim.hurtServer(level, skull.damageSources().mobAttack(skull), (float) skull.getAttributeValue(Attributes.ATTACK_DAMAGE));
	}

	/** How much lift its holders give between them, in ordinary skulls. */
	private int liftPower() {
		int power = 0;
		for (Skullcrane holder : holders) {
			power += holder.liftWeight();
		}
		return power;
	}

	public boolean hasRoom() {
		return holders.size() < MAX_HOLDERS;
	}

	public boolean isLifting() {
		return lifting;
	}

	public @Nullable LivingEntity victim() {
		return victim;
	}

	/** A skull lets go (hit too hard, say): if that leaves too few of them to hold the weight, they all do. */
	void drop(Skullcrane skull, int cooldown) {
		if (holders.remove(skull)) {
			skull.detach(cooldown);
			if (level() instanceof ServerLevel level && lifting && victim != null && liftPower() < holdersNeeded(victim)) {
				release(level);
			} else {
				placeHolders();
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (victim == null && getFirstPassenger() instanceof LivingEntity rider) {
			victim = rider;
		}
		holders.removeIf(holder -> !holder.isAlive() || holder.isRemoved() || holder.grip() != this);
		if (victim == null || !victim.isAlive() || victim.isRemoved() || holders.isEmpty() || !canLift(victim)) {
			release(level);
			return;
		}
		if (lifting) {
			lift(level, victim);
		} else {
			cling(level, victim);
		}
		if (!isRemoved()) {
			placeHolders();
		}
	}

	/** Not enough of them yet: they hang on, weighing the victim down, until there are or they give up. */
	private void cling(ServerLevel level, LivingEntity victim) {
		setPos(victim.position());
		if (clingTicks++ % 10 == 0) {
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, Math.min(holders.size(), 3) - 1), holders.get(0));
		}
		if (liftPower() >= holdersNeeded(victim)) {
			liftOff(level, victim);
		} else if (clingTicks > CLING_TICKS) {
			release(level);
		}
	}

	private void liftOff(ServerLevel level, LivingEntity victim) {
		if (victim.isSleeping()) {
			victim.stopSleeping();
		}
		if (victim.isPassenger()) {
			victim.stopRiding();
		}
		victim.ejectPassengers();
		victim.stopFallFlying();
		setPos(victim.position());
		if (!victim.startRiding(this, true, false)) {
			release(level);
			return;
		}
		lifting = true;
		liftTicks = 0;
		humTicks = 20;
		dropHeight = MIN_DROP + random.nextDouble() * (MAX_DROP - MIN_DROP);
		level.playSound(null, getX(), getY() + 2.0, getZ(), Skullcranes.LIFT, SoundSource.HOSTILE, 1.5f, 0.9f);
		if (victim instanceof ServerPlayer player) {
			// Over vanilla's "press shift to dismount", which doesn't work here.
			player.sendOverlayMessage(Component.translatable("entity.mymod.skullcrane.lifted").withStyle(ChatFormatting.RED));
		}
	}

	/** Up, faster the more of them there are, until they're high enough or can't go any higher. */
	private void lift(ServerLevel level, LivingEntity victim) {
		int needed = holdersNeeded(victim);
		if (victim.getVehicle() != this || liftPower() < needed || ++liftTicks > MAX_LIFT_TICKS) {
			release(level);
			return;
		}
		double speed = Math.min(MAX_LIFT_SPEED, LIFT_SPEED * liftPower() / needed);
		AABB body = victim.getBoundingBox();
		AABB above = new AABB(body.minX, body.maxY, body.minZ, body.maxX, body.maxY + HEADROOM + speed, body.maxZ).inflate(0.2, 0.0, 0.2);
		boolean blocked = !level.noBlockCollision(null, above) || body.maxY + HEADROOM + speed > level.getMaxY();
		if (blocked) {
			// Hit a ceiling (or the top of the world): that's as high as it goes.
			if (++blockedTicks > 6) {
				release(level);
				return;
			}
			setDeltaMovement(Vec3.ZERO);
		} else {
			blockedTicks = 0;
			setPos(getX(), getY() + speed, getZ());
			setDeltaMovement(0.0, speed, 0.0);
		}
		if (--humTicks <= 0) {
			float progress = (float) Mth.clamp((getY() - groundBelow()) / dropHeight, 0.0, 1.0);
			level.playSound(null, getX(), getY() + 2.0, getZ(), Skullcranes.LIFT, SoundSource.HOSTILE, 0.8f, 0.9f + 0.5f * progress);
			humTicks = 25;
		}
		if (liftTicks % 2 == 0) {
			// The antigrav emitters straining.
			Skullcrane holder = holders.get(random.nextInt(holders.size()));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, holder.getX(), holder.getY(), holder.getZ(), 1, 0.15, 0.05, 0.15, 0.02);
		}
		if (getY() - groundBelow() >= dropHeight) {
			release(level);
		}
	}

	/** The top of the first solid block under it, searching {@link #GROUND_SEARCH} blocks down. */
	private double groundBelow() {
		BlockPos.MutableBlockPos pos = blockPosition().mutable();
		for (int i = 0; i <= GROUND_SEARCH; i++) {
			VoxelShape shape = level().getBlockState(pos).getCollisionShape(level(), pos);
			if (!shape.isEmpty()) {
				return pos.getY() + shape.max(Direction.Axis.Y);
			}
			pos.move(Direction.DOWN);
		}
		return getY() - GROUND_SEARCH;
	}

	/** Holds each skull in its place over the victim's shoulders, facing in. */
	private void placeHolders() {
		LivingEntity held = victim;
		if (held == null) {
			return;
		}
		Vec3 feet = lifting ? position() : held.position();
		int count = holders.size();
		for (int i = 0; i < count; i++) {
			Skullcrane holder = holders.get(i);
			Vec3 offset = holder.holdOffset(held.yBodyRot, held.getBbWidth(), held.getBbHeight(), i, count);
			float yaw = (float) (Mth.atan2(-offset.z, -offset.x) * Mth.RAD_TO_DEG) - 90.0f;
			holder.holdAt(feet.add(offset), yaw, i, count);
		}
	}

	/** Everyone lets go: the skulls fly off to regroup, and the victim drops from wherever it is. */
	void release(ServerLevel level) {
		if (isRemoved()) {
			return;
		}
		for (Skullcrane holder : List.copyOf(holders)) {
			holder.detach(REGROUP_TICKS);
		}
		holders.clear();
		if (lifting) {
			level.playSound(null, getX(), getY() + 2.0, getZ(), Skullcranes.RELEASE, SoundSource.HOSTILE, 1.5f, 0.9f + random.nextFloat() * 0.2f);
		}
		LivingEntity held = victim;
		ejectPassengers();
		if (held != null) {
			// The fall counts from here.
			held.resetFallDistance();
		}
		discard();
	}

	@Override
	public void remove(RemovalReason reason) {
		for (Skullcrane holder : List.copyOf(holders)) {
			holder.detach(REGROUP_TICKS);
		}
		holders.clear();
		super.remove(reason);
	}

	// ------------------------------------------------------------------------------------------------ riding

	/** The victim hangs right where the grip is. */
	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		moveFunction.accept(passenger, getX(), getY(), getZ());
	}

	/** Let go mid-air, the victim stays put (and falls). */
	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return position();
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty();
	}

	@Override
	public boolean dismountsUnderwater() {
		return false;
	}

	// ------------------------------------------------------------------------------------------------ the rest

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	// Nothing worth keeping: reloaded, it has no skulls and lets go straight away.
	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
