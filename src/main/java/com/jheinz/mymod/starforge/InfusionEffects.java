package com.jheinz.mymod.starforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.ModEffects;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.fishing.RodModifiers;
import com.jheinz.mymod.holy.HolyLight;
import com.jheinz.mymod.silver.EvilBane;
import com.jheinz.mymod.uranium.Radiation;

/**
 * What each {@link Infusion} does. The numbers are kept modest: an infusion is a sidegrade that ties a material into
 * your gear, not a new tier.
 * <ul>
 * <li><b>Starlit</b> (star crystal block): by night, weapons and guns hit {@link #STARLIT_DAMAGE} times as hard, tools
 * and drills dig {@link #STARLIT_MINING} times as fast, each armor piece takes {@link #STARLIT_ARMOR} off damage, and
 * rods get night bites sooner and rarer.</li>
 * <li><b>Featherweight</b> (alien alloy): held gear {@link #FEATHER_HELD} movement speed, each armor piece
 * {@link #FEATHER_ARMOR}; guns reload in {@link #FEATHER_RELOAD} of the time, rods reel faster.</li>
 * <li><b>Voidtouched</b> (void flux): {@link #VOID_PIERCE} of a weapon's or gun's hit ignores armor; tools, drills,
 * armor and rods have a {@link #VOID_DURABILITY} chance to keep each point of durability.</li>
 * <li><b>Soulbound</b> (soul charge): weapons heal the wielder {@link #SOUL_LIFESTEAL} of the damage they deal (guns
 * half that, once every {@link #GUN_LIFESTEAL_COOLDOWN} ticks); everything else stays with you through death.</li>
 * <li><b>Fae-Kissed</b> (fae dust): weapons and guns have a {@link #FAE_LOOT} chance of an extra drop from what they
 * kill, tools and drills {@link #FAE_ORE} of an extra ore drop, armor gives {@link #FAE_LUCK} luck a piece, rods lean
 * toward rare fish.</li>
 * <li><b>Rimed</b> (froststeel): hits slow the target; armor takes {@link #RIMED_ARMOR} off freezing a piece.</li>
 * <li><b>Molten</b> (magmasteel): hits set the target alight; tools and drills smelt what they mine (see
 * {@link com.jheinz.mymod.magmasteel.AutoSmelt}); armor has a {@link #MOLTEN_ARMOR} chance a piece to set a melee
 * attacker alight.</li>
 * <li><b>Consecrated</b> (holy silver): {@link #CONSECRATED_DAMAGE} times the damage to the evil, with holy fire
 * ({@link EvilBane}); armor takes {@link #CONSECRATED_ARMOR} a piece off the evil's blows.</li>
 * <li><b>Drakehide</b> (a dragon scale): armor takes {@link #DRAKEHIDE_ARMOR} a piece off fire and explosions.</li>
 * <li><b>Irradiated</b> (enriched uranium): hits weaken the target and give players a dose of radiation; holding it
 * gives the wielder a slight dose unless they're shielded (see {@link Radiation#isShielded}).</li>
 * </ul>
 * Hits are read off the damage source: the attacker's main-hand item, for a blow or a hitscan round
 * ({@link GunItem#hit}) whose direct and causing entity are the attacker. Thorns and explosions don't count.
 */
public final class InfusionEffects {
	public static final float STARLIT_DAMAGE = 1.15f;
	public static final float STARLIT_MINING = 1.25f;
	public static final float STARLIT_ARMOR = 0.04f;
	public static final int STARLIT_NIGHT_BITE_TICKS = 60;
	public static final float STARLIT_NIGHT_RARITY = 0.08f;

	public static final double FEATHER_HELD = 0.05;
	public static final double FEATHER_ARMOR = 0.025;
	public static final float FEATHER_RELOAD = 0.85f;
	public static final float FEATHER_REEL = 0.1f;

	public static final float VOID_PIERCE = 0.3f;
	public static final float VOID_DURABILITY = 0.3f;

	public static final float SOUL_LIFESTEAL = 0.1f;
	public static final float SOUL_LIFESTEAL_CAP = 2.0f;
	public static final int GUN_LIFESTEAL_COOLDOWN = 10;

	public static final float FAE_LOOT = 0.25f;
	public static final float FAE_ORE = 0.2f;
	public static final double FAE_LUCK = 0.5;
	public static final float FAE_RARITY = 0.08f;

	public static final int RIMED_SLOW_TICKS = 40;
	public static final float RIMED_ARMOR = 0.25f;

	public static final int MOLTEN_FIRE_TICKS = 60;
	public static final float MOLTEN_ARMOR = 0.15f;

	public static final float CONSECRATED_DAMAGE = 1.3f;
	public static final float CONSECRATED_ARMOR = 0.05f;
	private static final EvilBane CONSECRATED_BANE = new EvilBane(CONSECRATED_DAMAGE, true);

	public static final float DRAKEHIDE_ARMOR = 0.1f;

	public static final int IRRADIATED_WEAKNESS_TICKS = 60;
	public static final int IRRADIATED_DOSE = 100;
	/** Ticks of dose a second for holding it: a tenth of carrying a loose enriched uranium. */
	public static final int IRRADIATED_WIELDER_DOSE = 4;

	private static final String MODIFIER_PREFIX = "infusion.";
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** When each gun last drew a lifesteal heal, so a minigun can't drink a stream of them. */
	private static final Map<LivingEntity, Long> LAST_GUN_LIFESTEAL = new WeakHashMap<>();

	private InfusionEffects() {
	}

	/** A blow or round from an infused weapon: who struck, with what, and whether it was a gun. */
	public record Strike(LivingEntity attacker, ItemStack weapon, Infusion infusion, boolean gun) {
	}

	static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(InfusionEffects::afterDamage);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> returnSoulbound(newPlayer));
		ServerPlayerEvents.JOIN.register(InfusionEffects::returnSoulbound);
		LootTableEvents.MODIFY_DROPS.register(InfusionEffects::modifyDrops);
		ServerTickEvents.END_SERVER_TICK.register(InfusionEffects::tick);
	}

	// --- Infusing ------------------------------------------------------------------------------------------------

	/** Puts the infusion into the item (replacing any other), with the attribute bonuses it carries. */
	public static ItemStack infuse(ItemStack stack, Infusion infusion) {
		stack.set(StarForge.INFUSION, infusion);
		ItemAttributeModifiers base = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		List<ItemAttributeModifiers.Entry> kept = new ArrayList<>();
		for (ItemAttributeModifiers.Entry entry : base.modifiers()) {
			if (!(entry.modifier().id().getNamespace().equals(MyMod.MOD_ID) && entry.modifier().id().getPath().startsWith(MODIFIER_PREFIX))) {
				kept.add(entry);
			}
		}
		ItemAttributeModifiers modifiers = new ItemAttributeModifiers(List.copyOf(kept));
		Set<InfusionKind> kinds = InfusionKind.of(stack);
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		EquipmentSlotGroup group = kinds.contains(InfusionKind.ARMOR) && equippable != null
				? EquipmentSlotGroup.bySlot(equippable.slot()) : EquipmentSlotGroup.MAINHAND;
		AttributeModifier speed = null;
		AttributeModifier luck = null;
		if (infusion == Infusion.FEATHERWEIGHT) {
			speed = new AttributeModifier(modifierId(group), kinds.contains(InfusionKind.ARMOR) ? FEATHER_ARMOR : FEATHER_HELD,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else if (infusion == Infusion.FAE_KISSED && kinds.contains(InfusionKind.ARMOR)) {
			luck = new AttributeModifier(modifierId(group), FAE_LUCK, AttributeModifier.Operation.ADD_VALUE);
		}
		if (speed != null) {
			modifiers = modifiers.withModifierAdded(Attributes.MOVEMENT_SPEED, speed, group);
		}
		if (luck != null) {
			modifiers = modifiers.withModifierAdded(Attributes.LUCK, luck, group);
		}
		if (!modifiers.equals(base)) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
		}
		return stack;
	}

	private static net.minecraft.resources.Identifier modifierId(EquipmentSlotGroup group) {
		return MyMod.id(MODIFIER_PREFIX + group.getSerializedName());
	}

	// --- Hits ----------------------------------------------------------------------------------------------------

	/** The infused weapon behind this damage, if it's a blow or a gun's round from one. */
	public static @Nullable Strike strike(DamageSource source) {
		if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
				|| source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.THORNS)) {
			return null;
		}
		ItemStack weapon = attacker.getMainHandItem();
		Infusion infusion = Infusion.of(weapon);
		if (infusion == null) {
			return null;
		}
		Set<InfusionKind> kinds = InfusionKind.of(weapon);
		if (!InfusionKind.strikes(kinds) || !infusion.fits(kinds)) {
			return null;
		}
		return new Strike(attacker, weapon, infusion, kinds.contains(InfusionKind.GUN));
	}

	/** An infused weapon's damage before armor: Starlit by night, Consecrated against the evil. */
	public static float outgoing(LivingEntity target, DamageSource source, float damage) {
		Strike strike = strike(source);
		if (strike == null || damage <= 0.0f) {
			return damage;
		}
		return switch (strike.infusion()) {
			case STARLIT -> strike.attacker().level().isDarkOutside() ? damage * STARLIT_DAMAGE : damage;
			case CONSECRATED -> HolyLight.isEvil(target) ? damage * CONSECRATED_DAMAGE : damage;
			default -> damage;
		};
	}

	/** Voidtouched: part of the hit goes through the armor as if it weren't there. */
	public static float pierce(DamageSource source, float unarmored, float armored) {
		if (armored >= unarmored) {
			return armored;
		}
		Strike strike = strike(source);
		return strike != null && strike.infusion() == Infusion.VOIDTOUCHED ? armored + (unarmored - armored) * VOID_PIERCE : armored;
	}

	/** Damage after the target's infused armor: Starlit by night, Rimed, Consecrated and Drakehide. */
	public static float incoming(LivingEntity target, DamageSource source, float damage) {
		if (damage <= 0.0f || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return damage;
		}
		float reduction = 0.0f;
		boolean night = target.level().isDarkOutside();
		boolean fromEvil = HolyLight.isEvil(source.getEntity());
		boolean fire = source.is(DamageTypeTags.IS_FIRE);
		boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
		boolean freezing = source.is(DamageTypeTags.IS_FREEZING);
		for (EquipmentSlot slot : ARMOR) {
			Infusion infusion = armorInfusion(target, slot);
			if (infusion == null) {
				continue;
			}
			reduction += switch (infusion) {
				case STARLIT -> night ? STARLIT_ARMOR : 0.0f;
				case RIMED -> freezing ? RIMED_ARMOR : 0.0f;
				case CONSECRATED -> fromEvil ? CONSECRATED_ARMOR : 0.0f;
				case DRAKEHIDE -> fire || explosion ? DRAKEHIDE_ARMOR : 0.0f;
				default -> 0.0f;
			};
		}
		return reduction > 0.0f ? damage * Math.max(0.0f, 1.0f - reduction) : damage;
	}

	private static @Nullable Infusion armorInfusion(LivingEntity entity, EquipmentSlot slot) {
		ItemStack piece = entity.getItemBySlot(slot);
		Infusion infusion = Infusion.of(piece);
		return infusion != null && InfusionKind.of(piece).contains(InfusionKind.ARMOR) ? infusion : null;
	}

	private static int armorPieces(LivingEntity entity, Infusion infusion) {
		int pieces = 0;
		for (EquipmentSlot slot : ARMOR) {
			if (armorInfusion(entity, slot) == infusion) {
				pieces++;
			}
		}
		return pieces;
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (blocked || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		Strike strike = strike(source);
		if (strike != null && damageTaken > 0.0f) {
			onHit(level, strike, target, damageTaken);
		}
		// Molten armor: a melee attacker may catch fire.
		if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && attacker != target
				&& !source.is(DamageTypes.THORNS) && !(attacker.getMainHandItem().getItem() instanceof GunItem)) {
			int pieces = armorPieces(target, Infusion.MOLTEN);
			if (pieces > 0 && target.getRandom().nextFloat() < MOLTEN_ARMOR * pieces) {
				attacker.igniteForTicks(Math.max(attacker.getRemainingFireTicks(), MOLTEN_FIRE_TICKS));
				level.sendParticles(ParticleTypes.FLAME, attacker.getX(), attacker.getY(0.5), attacker.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
			}
		}
	}

	private static void onHit(ServerLevel level, Strike strike, LivingEntity target, float damage) {
		LivingEntity attacker = strike.attacker();
		boolean gun = strike.gun();
		double y = target.getY(0.5);
		switch (strike.infusion()) {
			case SOULBOUND -> {
				if (gun) {
					long now = level.getGameTime();
					Long last = LAST_GUN_LIFESTEAL.get(attacker);
					if (last != null && now - last < GUN_LIFESTEAL_COOLDOWN && now >= last) {
						return;
					}
					LAST_GUN_LIFESTEAL.put(attacker, now);
				}
				float heal = Math.min(damage * SOUL_LIFESTEAL * (gun ? 0.5f : 1.0f), SOUL_LIFESTEAL_CAP * (gun ? 0.5f : 1.0f));
				if (heal > 0.0f && attacker.getHealth() < attacker.getMaxHealth()) {
					attacker.heal(heal);
					level.sendParticles(ParticleTypes.SOUL, target.getX(), y, target.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
				}
			}
			case RIMED -> {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, gun ? RIMED_SLOW_TICKS * 3 / 4 : RIMED_SLOW_TICKS, 0), attacker);
				level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), y, target.getZ(), 4, 0.25, 0.3, 0.25, 0.01);
			}
			case MOLTEN -> target.igniteForTicks(Math.max(target.getRemainingFireTicks(), gun ? MOLTEN_FIRE_TICKS * 2 / 3 : MOLTEN_FIRE_TICKS));
			case CONSECRATED -> CONSECRATED_BANE.strike(level, target);
			case IRRADIATED -> {
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, IRRADIATED_WEAKNESS_TICKS, 0), attacker);
				if (target instanceof ServerPlayer player) {
					Radiation.irradiate(player, gun ? IRRADIATED_DOSE / 2 : IRRADIATED_DOSE);
				} else {
					target.addEffect(new MobEffectInstance(ModEffects.RADIATION_SICKNESS, IRRADIATED_DOSE, 0), attacker);
				}
			}
			default -> {
			}
		}
	}

	// --- Tools ---------------------------------------------------------------------------------------------------

	/** Starlit tools and drills dig faster by night. */
	public static float destroySpeed(Player player, BlockState state, float speed) {
		ItemStack tool = player.getMainHandItem();
		if (Infusion.of(tool) == Infusion.STARLIT && InfusionKind.digs(InfusionKind.of(tool)) && tool.getDestroySpeed(state) > 1.0f
				&& player.level().isDarkOutside()) {
			return speed * STARLIT_MINING;
		}
		return speed;
	}

	/** Voidtouched gear keeps some of the durability it would lose. */
	public static int durabilityChange(ItemStack stack, int amount, RandomSource random) {
		if (amount <= 0 || Infusion.of(stack) != Infusion.VOIDTOUCHED) {
			return amount;
		}
		Set<InfusionKind> kinds = InfusionKind.of(stack);
		if (!InfusionKind.digs(kinds) && !kinds.contains(InfusionKind.ARMOR) && !kinds.contains(InfusionKind.ROD)) {
			return amount;
		}
		int kept = 0;
		for (int i = 0; i < amount; i++) {
			if (random.nextFloat() < VOID_DURABILITY) {
				kept++;
			}
		}
		return amount - kept;
	}

	/** Whether a Molten tool or drill smelts what it mines. */
	public static boolean autoSmelts(@Nullable ItemInstance tool) {
		return tool instanceof ItemStack stack && Infusion.of(stack) == Infusion.MOLTEN && InfusionKind.digs(InfusionKind.of(stack));
	}

	/** How long a reload takes relative to normal: shorter with a Featherweight gun. */
	public static float reloadFactor(LivingEntity holder) {
		ItemStack gun = holder.getMainHandItem();
		return gun.getItem() instanceof GunItem && Infusion.of(gun) == Infusion.FEATHERWEIGHT ? FEATHER_RELOAD : 1.0f;
	}

	/** A rod's infusion as rod modifiers: Starlit night bites, Fae-Kissed rarity, Featherweight reeling. */
	public static RodModifiers rodModifiers(ItemStack rod) {
		Infusion infusion = Infusion.of(rod);
		if (infusion == null) {
			return RodModifiers.NONE;
		}
		return switch (infusion) {
			case STARLIT -> RodModifiers.builder().nightBiteTicks(STARLIT_NIGHT_BITE_TICKS).nightRarity(STARLIT_NIGHT_RARITY).build();
			case FAE_KISSED -> RodModifiers.builder().rarity(FAE_RARITY).build();
			case FEATHERWEIGHT -> RodModifiers.builder().reel(FEATHER_REEL).build();
			default -> RodModifiers.NONE;
		};
	}

	// --- Fae-Kissed drops ----------------------------------------------------------------------------------------

	private static void modifyDrops(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		if (drops.isEmpty()) {
			return;
		}
		RandomSource random = context.getRandom();
		BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
		if (state != null) {
			ItemInstance tool = context.getOptional(LootContextParams.TOOL);
			if (!(tool instanceof ItemStack stack) || Infusion.of(stack) != Infusion.FAE_KISSED || !InfusionKind.digs(InfusionKind.of(stack))
					|| !state.is(ConventionalBlockTags.ORES) || random.nextFloat() >= FAE_ORE) {
				return;
			}
			for (ItemStack drop : drops) {
				// The ore's yield, not the ore block itself (silk touch).
				if (!drop.is(state.getBlock().asItem()) && drop.getCount() < drop.getMaxStackSize()) {
					drop.grow(1);
				}
			}
			return;
		}
		DamageSource source = context.getOptional(LootContextParams.DAMAGE_SOURCE);
		Strike strike = source == null ? null : strike(source);
		if (strike == null || strike.infusion() != Infusion.FAE_KISSED || random.nextFloat() >= FAE_LOOT) {
			return;
		}
		List<ItemStack> stackable = drops.stream().filter(drop -> drop.getMaxStackSize() > 1 && !drop.isEmpty()).toList();
		if (!stackable.isEmpty()) {
			drops.add(stackable.get(random.nextInt(stackable.size())).copyWithCount(1));
		}
	}

	// --- Soulbound -----------------------------------------------------------------------------------------------

	/** Whether this item stays with its owner through death: Soulbound gear that isn't only a weapon. */
	public static boolean keptOnDeath(ItemStack stack) {
		if (Infusion.of(stack) != Infusion.SOULBOUND) {
			return false;
		}
		Set<InfusionKind> kinds = InfusionKind.of(stack);
		return InfusionKind.digs(kinds) || kinds.contains(InfusionKind.ARMOR) || kinds.contains(InfusionKind.ROD);
	}

	/** Just before a dying player's inventory drops: their Soulbound gear goes into the stash instead. */
	public static void stashSoulbound(Player player, ServerLevel level) {
		if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}
		Inventory inventory = player.getInventory();
		List<ItemStack> kept = new ArrayList<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && keptOnDeath(stack)) {
				kept.add(inventory.removeItemNoUpdate(slot));
			}
		}
		if (kept.isEmpty()) {
			return;
		}
		ItemContainerContents stash = player.getAttached(StarForge.SOULBOUND_STASH);
		if (stash != null) {
			stash.nonEmptyItemCopyStream().forEach(kept::add);
		}
		player.setAttached(StarForge.SOULBOUND_STASH, ItemContainerContents.fromItems(kept));
	}

	/** Gives a respawned player their Soulbound gear back: armor straight on if the slot is free. */
	private static void returnSoulbound(ServerPlayer player) {
		if (!player.isAlive()) {
			return;
		}
		ItemContainerContents stash = player.getAttached(StarForge.SOULBOUND_STASH);
		if (stash == null) {
			return;
		}
		player.removeAttached(StarForge.SOULBOUND_STASH);
		stash.nonEmptyItemCopyStream().forEach(stack -> {
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR
					&& player.getItemBySlot(equippable.slot()).isEmpty()) {
				player.setItemSlot(equippable.slot(), stack);
			} else {
				player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		});
	}

	// --- Wielding ------------------------------------------------------------------------------------------------

	/** Once a second: holding an Irradiated weapon gives its wielder a slight dose. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.tickCount % 20 != 0 || !player.isAlive()) {
				continue;
			}
			if (Infusion.of(player.getMainHandItem()) == Infusion.IRRADIATED || Infusion.of(player.getOffhandItem()) == Infusion.IRRADIATED) {
				Radiation.irradiate(player, IRRADIATED_WIELDER_DOSE);
			}
		}
	}
}
