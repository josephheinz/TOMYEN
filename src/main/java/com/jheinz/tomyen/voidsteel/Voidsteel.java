package com.jheinz.tomyen.voidsteel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.foundry.FoundryRecipe;
import com.jheinz.tomyen.industry.Industry;
import com.jheinz.tomyen.metal.HintItem;
import com.jheinz.tomyen.metal.MetalSet;
import com.jheinz.tomyen.soul.SoulSteel;

/**
 * Voidsteel: steel fused with Void Flux (an ender pearl ground into end stone) in a white-hot Foundry. Its tools are
 * between diamond and netherite (netherite's mining level), and whatever they mine goes through the void into the
 * miner's bank vault ({@link VaultDrops}). Its sword blinks: sneak and use it to flash {@link VoidsteelSwordItem}'s
 * few blocks ahead.
 *
 * <p>Its armor is only half there. Each piece gives a projectile a {@link #PHASE_CHANCE_PER_PIECE} chance to pass
 * straight through its wearer (ProjectileVoidsteelMixin), decided once per projectile. The full set won't let the
 * void have its wearer: the blow from the void that would kill them takes them back instead to the last solid ground
 * they stood on, at most once every {@link #RESCUE_COOLDOWN} ticks (five minutes).
 */
public final class Voidsteel {
	public static final String NAME = "voidsteel";
	public static final int FOUNDRY_TEMPERATURE = 1400;
	public static final int FOUNDRY_TICKS = 300;
	public static final float PHASE_CHANCE_PER_PIECE = 0.05f;
	public static final int RESCUE_COOLDOWN = 6000;

	public static final TagKey<Item> REPAIRS = MetalSet.repairTag(NAME);
	public static final ToolMaterial TOOL_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1800, 8.5f,
			3.5f, 15, REPAIRS);
	public static final ArmorMaterial ARMOR_MATERIAL = new ArmorMaterial(35,
			Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 11),
			15, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.5f, 0.05f, REPAIRS, ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id(NAME)));

	public static final MetalSet SET = new MetalSet(NAME, TOOL_MATERIAL, ARMOR_MATERIAL, 5.0f, -3.0f, -3.5f, 0.0f,
			VoidsteelSwordItem::new, HintItem::new, List.of("item.tomyen.voidsteel.vault"),
			List.of("item.tomyen.voidsteel.armor_piece", "item.tomyen.voidsteel.armor_set"), MapColor.COLOR_PURPLE, 0, false);

	/** An ender pearl ground into end stone: what the Foundry fuses with steel to make voidsteel. */
	public static final Item VOID_FLUX = item("void_flux", new Item.Properties().rarity(Rarity.UNCOMMON));

	/** Where each player last stood on solid ground, and in which dimension. */
	private record Ground(ResourceKey<Level> dimension, Vec3 position) {
	}

	private static final Map<ServerPlayer, Ground> LAST_GROUND = new WeakHashMap<>();
	private static final Map<UUID, Integer> NEXT_RESCUE = new HashMap<>();
	/** For each projectile, the entities it has been close enough to hit, and whether it passes through them. */
	private static final Map<Projectile, Map<Entity, Boolean>> PHASED = new WeakHashMap<>();

	private Voidsteel() {
	}

	private static Item item(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	public static List<FoundryRecipe> foundry() {
		return List.of(new FoundryRecipe(SET.ingot, 1, FOUNDRY_TICKS,
				List.of(new FoundryRecipe.Cost(Industry.STEEL_INGOT, 1), new FoundryRecipe.Cost(VOID_FLUX, 1)))
				.withMinTemperature(FOUNDRY_TEMPERATURE));
	}

	/**
	 * Whether a projectile passes through this entity rather than hitting it (see ProjectileVoidsteelMixin). The roll is
	 * made once, when the projectile is about to reach the entity, so a projectile that phases keeps phasing.
	 */
	public static boolean phases(Projectile projectile, Entity target) {
		if (!(target instanceof LivingEntity living) || !(projectile.level() instanceof ServerLevel level)
				|| projectile.getOwner() == target) {
			return false;
		}
		int pieces = SET.piecesWorn(living);
		if (pieces == 0) {
			return false;
		}
		Map<Entity, Boolean> rolls = PHASED.computeIfAbsent(projectile, p -> new WeakHashMap<>());
		Boolean phased = rolls.get(target);
		if (phased == null) {
			// Only once the projectile is actually on course for it, not just passing near.
			Vec3 from = projectile.position();
			AABB box = target.getBoundingBox().inflate(0.3);
			if (!box.contains(from) && box.clip(from, from.add(projectile.getDeltaMovement())).isEmpty()) {
				return false;
			}
			phased = level.getRandom().nextFloat() < PHASE_CHANCE_PER_PIECE * pieces;
			rolls.put(target, phased);
			if (phased) {
				level.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY(0.5), target.getZ(), 20,
						target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.3);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDERMAN_TELEPORT,
						target.getSoundSource(), 0.4f, 1.4f);
			}
		}
		return phased;
	}

	/** Called as the void is about to deal a full set's wearer a lethal blow: takes them back to solid ground if it can. */
	private static boolean rescue(ServerPlayer player, DamageSource source, float amount) {
		if (!source.is(DamageTypes.FELL_OUT_OF_WORLD) || amount < player.getHealth() + player.getAbsorptionAmount()
				|| !SET.wearsFullSet(player)) {
			return false;
		}
		Ground ground = LAST_GROUND.get(player);
		MinecraftServer server = player.level().getServer();
		ServerLevel level = ground == null ? null : server.getLevel(ground.dimension());
		int now = server.getTickCount();
		if (level == null || NEXT_RESCUE.getOrDefault(player.getUUID(), 0) > now) {
			return false;
		}
		NEXT_RESCUE.put(player.getUUID(), now + RESCUE_COOLDOWN);
		Vec3 to = ground.position();
		player.teleportTo(level, to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1.0, to.z, 40, 0.4, 0.8, 0.4, 0.05);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.8f);
		player.sendOverlayMessage(Component.translatable("message.tomyen.voidsteel.rescued").withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// Solid ground: standing on something with a collision shape, above the void.
			if (player.onGround() && !player.isSpectator() && player.getY() > player.level().getMinY()
					&& !player.level().getBlockState(player.getOnPos()).getCollisionShape(player.level(), player.getOnPos()).isEmpty()) {
				LAST_GROUND.put(player, new Ground(player.level().dimension(), player.position()));
			}
		}
	}

	public static void initialize() {
		LootTableEvents.MODIFY_DROPS.register(VaultDrops::modifyDrops);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer player && rescue(player, source, amount)));
		ServerTickEvents.END_SERVER_TICK.register(Voidsteel::tick);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(SoulSteel.SET.nugget, VOID_FLUX, SET.ingot, SET.nugget));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.insertAfter(SoulSteel.SET.block.asItem(), SET.block.asItem()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(SoulSteel.SET.hoe, SET.shovel, SET.pickaxe, SET.axe, SET.hoe));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(SoulSteel.SET.sword, SET.sword);
			output.insertAfter(SoulSteel.SET.axe, SET.axe);
			output.insertAfter(SoulSteel.SET.boots, SET.helmet, SET.chestplate, SET.leggings, SET.boots);
		});
	}
}
