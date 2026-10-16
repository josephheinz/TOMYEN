package com.jheinz.tomyen.scarecrow;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.alien.AbductionDecoy;
import com.jheinz.tomyen.alien.Aliens;
import com.jheinz.tomyen.alien.TractorBeam;
import com.jheinz.tomyen.alien.Ufo;

/**
 * A scarecrow: a straw-stuffed figure in a plaid shirt and a straw hat, tied to a post with its arms out along a
 * crossbar. Placed from its item like an armor stand, it can wear armor and anything that goes on a head (a helmet, a
 * carved pumpkin, a skull) in place of its hat. Right-click with armor to dress it, with an empty hand to take a piece
 * back; punch it twice quickly to pick it up.
 * <ul>
 * <li><b>A UFO decoy</b> ({@link AbductionDecoy}): a roaming saucer goes for a scarecrow in range before any livestock.
 * It's heavy: the beam lifts it at {@value #BEAM_RISE} blocks a tick (a cow goes up more than twice as fast), shedding
 * straw, which gives everyone time to shoot the saucer (enough damage and it lets go). Once it's in the hull the aliens
 * inspect it for a few seconds, then drop it in disgust with a disappointed chirp: it flutters back down, Probed, with
 * a mocking name if it didn't have one. That saucer won't fall for it twice, and no saucer goes for it again for a
 * couple of minutes, but the visit has cost the saucer one of its abductions.
 * <li><b>A mob decoy</b>: hostile mobs nearby are drawn to attack it for a few seconds at a time
 * ({@link ScarecrowLureGoal}). It has {@value #MAX_HEALTH} health, straw burns (fire does double damage to all but the
 * scrap one), and when it's destroyed it falls apart into its materials. Hay bales mend it fully; wheat a little.
 * <li><b>A crop guard</b> ({@link CropGuard}): mobs don't trample farmland, and rabbits don't raid crops, near it.
 * </ul>
 * Its variants: the plain one (a burlap sack head), the Jack-o'-Scarecrow (a jack-o'-lantern head that glows at night)
 * and the Scrap Scarecrow (a bucket head with LED eyes, a tarp coat and wires for straw), which also decoys rogue
 * machines.
 */
public class Scarecrow extends LivingEntity implements AbductionDecoy {
	public static final float WIDTH = 0.75f;
	public static final float HEIGHT = 2.3f;
	public static final int MAX_HEALTH = 30;
	/** How fast a UFO's beam lifts it: it's heavy. */
	static final double BEAM_RISE = 0.025;
	/** How long the aliens look it over before they give up on it. */
	private static final int INSPECT_TICKS = 70;
	/** After a saucer's thrown it back, no saucer goes for it for this long. */
	private static final int UFO_COOLDOWN = 2400;
	/** It flutters down rather than drops: its coat catches the air. */
	private static final double MAX_FALL = 0.35;
	private static final int PROBED_NAMES = 4;

	private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(Scarecrow.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_INSPECTED = SynchedEntityData.defineId(Scarecrow.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_PROBED = SynchedEntityData.defineId(Scarecrow.class, EntityDataSerializers.BOOLEAN);

	/** When it was last punched (for the wobble, and picking it up with a second quick punch). */
	public long lastHit;
	// Server side.
	private int inspectTicks;
	private int inspectorId = -1;
	private @Nullable UUID rejectedBy;
	private int ufoCooldown;

	public Scarecrow(EntityType<? extends Scarecrow> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createLivingAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, (byte) Variant.STRAW.ordinal());
		builder.define(DATA_INSPECTED, false);
		builder.define(DATA_PROBED, false);
	}

	// ------------------------------------------------------------------------------------------------ state

	public Variant variant() {
		return Variant.byId(entityData.get(DATA_VARIANT));
	}

	public void setVariant(Variant variant) {
		entityData.set(DATA_VARIANT, (byte) variant.ordinal());
	}

	/** Inside a saucer being looked over: nowhere to be seen, and untouchable. */
	public boolean isInspected() {
		return entityData.get(DATA_INSPECTED);
	}

	/** It's been aboard a saucer and thrown back. */
	public boolean isProbed() {
		return entityData.get(DATA_PROBED);
	}

	/** Standing about where it can draw mobs and guard crops: not up a beam or inside a saucer. */
	public boolean isDecoyActive() {
		return isAlive() && !isRemoved() && !isInspected() && !isPassenger();
	}

	// ------------------------------------------------------------------------------------------------ UFOs

	@Override
	public boolean attractsUfo(Ufo ufo) {
		return isDecoyActive() && ufoCooldown <= 0 && !ufo.getUUID().equals(rejectedBy)
				&& level().canSeeSky(BlockPos.containing(getX(), getEyeY(), getZ()));
	}

	@Override
	public double beamRise() {
		return BEAM_RISE;
	}

	@Override
	public void takenAboard(Ufo ufo) {
		inspectorId = ufo.getId();
		inspectTicks = 0;
		entityData.set(DATA_INSPECTED, true);
		setPos(ufo.position());
		setDeltaMovement(Vec3.ZERO);
	}

	/** Aboard: held in the hull while they look it over, with a few puzzled chirps and a probe or two. */
	private void inspect(ServerLevel level) {
		Entity ship = level.getEntity(inspectorId);
		if (!(ship instanceof Ufo ufo) || !ufo.isAlive() || ufo.isCrashing()) {
			reject(level, null);
			return;
		}
		setPos(ufo.position());
		setDeltaMovement(Vec3.ZERO);
		resetFallDistance();
		inspectTicks++;
		if (inspectTicks == 12 || inspectTicks == 30) {
			level.playSound(null, ufo.getX(), ufo.getY(), ufo.getZ(), Aliens.ALIEN_AMBIENT, SoundSource.HOSTILE, 1.5f,
					inspectTicks == 12 ? 1.3f : 1.6f);
		}
		if (inspectTicks == 45) {
			// A probe goes in.
			level.playSound(null, ufo.getX(), ufo.getY(), ufo.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0f, 2.0f);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ufo.getX(), ufo.getY() - 0.2, ufo.getZ(), 12, 0.6, 0.1, 0.6, 0.05);
		}
		if (inspectTicks >= INSPECT_TICKS) {
			reject(level, ufo);
		}
	}

	/** Not what they were after: thrown back out, Probed (and named for it), to flutter down. */
	private void reject(ServerLevel level, @Nullable Ufo ufo) {
		entityData.set(DATA_INSPECTED, false);
		entityData.set(DATA_PROBED, true);
		inspectorId = -1;
		ufoCooldown = UFO_COOLDOWN;
		setYRot(random.nextFloat() * 360.0f);
		if (ufo != null) {
			rejectedBy = ufo.getUUID();
			setPos(ufo.getX(), ufo.getY() - HEIGHT - 0.4, ufo.getZ());
			double angle = random.nextDouble() * Mth.TWO_PI;
			setDeltaMovement(Math.cos(angle) * 0.25, 0.05, Math.sin(angle) * 0.25);
			level.playSound(null, ufo.getX(), ufo.getY(), ufo.getZ(), Scarecrows.UFO_DISAPPOINTED, SoundSource.HOSTILE, 3.0f, 1.0f);
			level.sendParticles(ParticleTypes.END_ROD, ufo.getX(), ufo.getY() - 0.3, ufo.getZ(), 10, 0.5, 0.1, 0.5, 0.03);
		}
		if (!hasCustomName()) {
			setCustomName(Component.translatable("entity.tomyen.scarecrow.probed." + random.nextInt(PROBED_NAMES), 100 + random.nextInt(900)));
		}
		Component message = Component.translatable("entity.tomyen.scarecrow.rejected").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(64.0))) {
			player.sendOverlayMessage(message);
		}
	}

	// ------------------------------------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (ufoCooldown > 0) {
			ufoCooldown--;
		}
		if (isInspected()) {
			inspect(level);
			return;
		}
		if (getVehicle() instanceof TractorBeam && tickCount % 6 == 0) {
			// Shedding straw as it goes up.
			level.sendParticles(debris(), getX(), getY() + HEIGHT * 0.5, getZ(), 3, 0.3, 0.6, 0.3, 0.02);
		}
		Vec3 motion = getDeltaMovement();
		if (!onGround() && !isPassenger() && motion.y < -MAX_FALL) {
			setDeltaMovement(motion.x, -MAX_FALL, motion.z);
		}
	}

	@Override
	protected void tickHeadTurn(float yBodyRotT) {
		yBodyRotO = yRotO;
		yBodyRot = getYRot();
	}

	@Override
	public void setYBodyRot(float yBodyRot) {
		yBodyRotO = yRotO = yBodyRot;
		yHeadRotO = yHeadRot = yBodyRot;
	}

	@Override
	public void setYHeadRot(float yHeadRot) {
		yBodyRotO = yRotO = yHeadRot;
		yHeadRotO = yHeadRot = yHeadRot;
	}

	// ------------------------------------------------------------------------------------------------ dressing and mending

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.NAME_TAG)) {
			return super.interact(player, hand, location);
		}
		if (player.isSpectator() || isInspected()) {
			return InteractionResult.PASS;
		}
		boolean mends = held.is(Items.HAY_BLOCK) || held.is(Items.WHEAT);
		if (mends && getHealth() >= getMaxHealth()) {
			return InteractionResult.PASS;
		}
		if (player.level().isClientSide()) {
			return mends || !held.isEmpty() && getEquipmentSlotForItem(held).getType() == EquipmentSlot.Type.HUMANOID_ARMOR
					|| held.isEmpty() && hasItemInSlot(clickedSlot(location)) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (mends) {
			heal(held.is(Items.HAY_BLOCK) ? getMaxHealth() : 4.0f);
			held.consume(1, player);
			playSound(SoundEvents.GRASS_PLACE, 1.0f, 0.9f);
			((ServerLevel) level()).sendParticles(debris(), getX(), getY() + 1.2, getZ(), 12, 0.3, 0.5, 0.3, 0.05);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (held.isEmpty()) {
			EquipmentSlot slot = clickedSlot(location);
			return hasItemInSlot(slot) && swapItem(player, slot, held, hand) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		}
		EquipmentSlot slot = getEquipmentSlotForItem(held);
		if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && swapItem(player, slot, held, hand)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	/** Which piece someone reaching for it with an empty hand means, by how high up they clicked. */
	private EquipmentSlot clickedSlot(Vec3 location) {
		double y = location.y;
		if (y >= 1.62) {
			return EquipmentSlot.HEAD;
		}
		if (y >= 0.95) {
			return EquipmentSlot.CHEST;
		}
		return y >= 0.5 ? EquipmentSlot.LEGS : EquipmentSlot.FEET;
	}

	private boolean swapItem(Player player, EquipmentSlot slot, ItemStack held, InteractionHand hand) {
		ItemStack worn = getItemBySlot(slot);
		if (player.hasInfiniteMaterials() && worn.isEmpty() && !held.isEmpty()) {
			setItemSlot(slot, held.copyWithCount(1));
			return true;
		}
		if (held.isEmpty() || held.getCount() <= 1) {
			setItemSlot(slot, held);
			player.setItemInHand(hand, worn);
			return true;
		}
		if (!worn.isEmpty()) {
			return false;
		}
		setItemSlot(slot, held.split(1));
		return true;
	}

	@Override
	public boolean canUseSlot(EquipmentSlot slot) {
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}

	// ------------------------------------------------------------------------------------------------ taking damage

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved() || isInspected()) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, damage);
		}
		if (isInvulnerableTo(level, source)) {
			return false;
		}
		if (source.getDirectEntity() instanceof Player player && source.getEntity() == player) {
			return punchedBy(level, player, source);
		}
		if (source.is(DamageTypeTags.IS_FIRE) && variant() != Variant.SCRAP) {
			damage *= 2.0f;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive()) {
			level.sendParticles(debris(), getX(), getY() + 1.2, getZ(), 6, 0.25, 0.4, 0.25, 0.05);
		}
		return hurt;
	}

	/** Punched by someone: it wobbles; a second punch straight after picks it up (as with an armor stand). */
	private boolean punchedBy(ServerLevel level, Player player, DamageSource source) {
		if (!player.getAbilities().mayBuild || !level.mayInteract(player, blockPosition())) {
			return false;
		}
		if (source.isCreativePlayer()) {
			fallApart(level);
			discard();
			return true;
		}
		long time = level.getGameTime();
		if (time - lastHit > 5) {
			level.broadcastEntityEvent(this, EntityEvent.ARMORSTAND_WOBBLE);
			gameEvent(GameEvent.ENTITY_DAMAGE, player);
			lastHit = time;
			return true;
		}
		ItemStack item = new ItemStack(variant().item());
		item.set(DataComponents.CUSTOM_NAME, getCustomName());
		Block.popResource(level, blockPosition(), item);
		dropEquipment(level);
		fallApart(level);
		gameEvent(GameEvent.ENTITY_DIE, player);
		discard();
		return true;
	}

	@Override
	public void handleEntityEvent(@EntityEvent.Value byte id) {
		if (id == EntityEvent.ARMORSTAND_WOBBLE) {
			if (level().isClientSide()) {
				level().playLocalSound(getX(), getY(), getZ(), getHurtSound(level().damageSources().generic()), getSoundSource(), 0.5f, 1.0f, false);
				lastHit = level().getGameTime();
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return isInspected() || super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
		return false;
	}

	// ------------------------------------------------------------------------------------------------ falling apart

	/** Destroyed: no lying-down death, it just comes apart in a burst of straw. */
	@Override
	protected void tickDeath() {
		if (level() instanceof ServerLevel level && !isRemoved()) {
			fallApart(level);
			remove(RemovalReason.KILLED);
		}
	}

	private void fallApart(ServerLevel level) {
		level.sendParticles(debris(), getX(), getY() + 1.1, getZ(), 30, 0.3, 0.7, 0.3, 0.08);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LOG.defaultBlockState()), getX(), getY() + 0.4, getZ(), 8,
				0.1, 0.4, 0.1, 0.05);
		level.playSound(null, getX(), getY(), getZ(), getDeathSound(), getSoundSource(), 1.0f, 0.9f);
	}

	/** What it's made of, more or less, when it's destroyed. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		spawnAtLocation(level, new ItemStack(Items.STICK, 1 + random.nextInt(2)));
		switch (variant()) {
			case STRAW -> {
				spawnAtLocation(level, new ItemStack(Items.WHEAT, 3 + random.nextInt(4)));
				spawnAtLocation(level, new ItemStack(Items.STRING, 1 + random.nextInt(2)));
				if (random.nextBoolean()) {
					spawnAtLocation(level, new ItemStack(Items.CARVED_PUMPKIN));
				}
			}
			case PUMPKIN -> {
				spawnAtLocation(level, new ItemStack(Items.WHEAT, 3 + random.nextInt(4)));
				spawnAtLocation(level, new ItemStack(Items.CARVED_PUMPKIN));
				spawnAtLocation(level, new ItemStack(Items.TORCH));
			}
			case SCRAP -> {
				spawnAtLocation(level, new ItemStack(Items.WHEAT, 1 + random.nextInt(3)));
				spawnAtLocation(level, new ItemStack(ModItems.TECH_SCRAP, 1 + random.nextInt(2)));
				spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, 2 + random.nextInt(3)));
			}
		}
	}

	/** Whatever it's wearing drops. */
	@Override
	protected void dropEquipment(ServerLevel level) {
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			ItemStack stack = equipment.set(slot, ItemStack.EMPTY);
			if (!stack.isEmpty() && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				Block.popResource(level, blockPosition().above(), stack);
			}
		}
	}

	private BlockParticleOption debris() {
		BlockState state = switch (variant()) {
			case SCRAP -> Blocks.IRON_BARS.defaultBlockState();
			default -> Blocks.HAY_BLOCK.defaultBlockState();
		};
		return new BlockParticleOption(ParticleTypes.BLOCK, state);
	}

	// ------------------------------------------------------------------------------------------------ the rest

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean isAffectedByPotions() {
		return false;
	}

	@Override
	public boolean canBeSeenByAnyone() {
		return !isInspected() && super.canBeSeenByAnyone();
	}

	@Override
	public boolean isPickable() {
		return !isInspected() && super.isPickable();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0 * 96.0;
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(variant().item());
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return variant() == Variant.SCRAP ? SoundEvents.METAL_HIT : SoundEvents.GRASS_HIT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return variant() == Variant.SCRAP ? SoundEvents.METAL_BREAK : SoundEvents.GRASS_BREAK;
	}

	@Override
	public LivingEntity.Fallsounds getFallSounds() {
		return new LivingEntity.Fallsounds(SoundEvents.ARMOR_STAND_FALL, SoundEvents.ARMOR_STAND_FALL);
	}

	/** Mobs nearby it can draw (see {@link ScarecrowLureGoal}). */
	AABB lureArea(double radius) {
		return getBoundingBox().inflate(radius, 6.0, radius);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Variant", variant().id());
		output.putBoolean("Probed", isProbed());
		output.storeNullable("RejectedBy", UUIDUtil.CODEC, rejectedBy);
		output.putInt("UfoCooldown", ufoCooldown);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setVariant(Variant.byName(input.getStringOr("Variant", Variant.STRAW.id())));
		entityData.set(DATA_PROBED, input.getBooleanOr("Probed", false));
		rejectedBy = input.read("RejectedBy", UUIDUtil.CODEC).orElse(null);
		ufoCooldown = input.getIntOr("UfoCooldown", 0);
	}

	/** Its three looks; each is its own item. */
	public enum Variant {
		STRAW("scarecrow"),
		/** Its jack-o'-lantern head glows at night. */
		PUMPKIN("pumpkin_scarecrow"),
		/** Tech junk: also decoys rogue machines. */
		SCRAP("scrap_scarecrow");

		private final String id;

		Variant(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public net.minecraft.world.item.Item item() {
			return Scarecrows.item(this);
		}

		static Variant byId(int ordinal) {
			Variant[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : STRAW;
		}

		static Variant byName(String id) {
			for (Variant variant : values()) {
				if (variant.id.equals(id)) {
					return variant;
				}
			}
			return STRAW;
		}
	}
}
