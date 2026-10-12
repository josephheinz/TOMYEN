package com.jheinz.mymod.soul;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.foundry.FoundryRecipe;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.metal.HintItem;
import com.jheinz.mymod.metal.MetalSet;

/**
 * Soul steel: steel quenched with two Soul Charges in the Foundry. Its tools are near diamond (diamond's mining
 * level, a little less durable, more enchantable). A kill with its sword or axe draws the victim's soul into the
 * wielder, healing {@link #LIFESTEAL} health (two hearts).
 *
 * <p>The full set of its armor fills weaker monsters with dread: a hostile mob of at most {@link #DREAD_MAX_HEALTH}
 * max health that isn't a boss or an elite won't take the wearer as a target unless the wearer hurt it first, and
 * sometimes runs off for a moment instead (Mob.setTarget, see MobSoulSteelMixin). Mobs already after the wearer give
 * up when they notice the set. Only mobs that pick targets with goals are affected; brain-driven ones (piglins,
 * hoglins) aren't.
 */
public final class SoulSteel {
	public static final String NAME = "soul_steel";
	public static final int FOUNDRY_TEMPERATURE = 1000;
	public static final int FOUNDRY_TICKS = 300;
	public static final float LIFESTEAL = 4.0f;
	public static final float DREAD_MAX_HEALTH = 20.0f;
	private static final double DREAD_RANGE = 24.0;
	private static final float FLEE_CHANCE = 0.3f;
	private static final int FLEE_COOLDOWN = 100;

	public static final TagKey<Item> REPAIRS = MetalSet.repairTag(NAME);
	public static final ToolMaterial TOOL_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1400, 8.0f, 3.0f, 14, REPAIRS);
	public static final ArmorMaterial ARMOR_MATERIAL = new ArmorMaterial(31,
			Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 11),
			14, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0f, 0.05f, REPAIRS, ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id(NAME)));

	public static final MetalSet SET = new MetalSet(NAME, TOOL_MATERIAL, ARMOR_MATERIAL, 5.0f, -3.0f, -3.0f, 0.0f,
			(properties, hints) -> new HintItem(properties,
					Stream.concat(Stream.of("item.mymod.soul_steel.lifesteal"), hints.stream()).toList()),
			List.of(), List.of("item.mymod.soul_steel.armor_set"), MapColor.COLOR_CYAN, 5, false);

	/** Which players each mob has been hurt by: those it may fight back against despite the dread. */
	private static final Map<Mob, Set<UUID>> PROVOKED = new WeakHashMap<>();
	private static final Map<Mob, Integer> NEXT_FLEE = new WeakHashMap<>();

	private SoulSteel() {
	}

	public static List<FoundryRecipe> foundry() {
		return List.of(new FoundryRecipe(SET.ingot, 1, FOUNDRY_TICKS,
				List.of(new FoundryRecipe.Cost(Industry.STEEL_INGOT, 1), new FoundryRecipe.Cost(Souls.SOUL_CHARGE, 2)))
				.withMinTemperature(FOUNDRY_TEMPERATURE));
	}

	/** Whether this mob is too daunted by the target's soul steel to go after it. */
	public static boolean dreads(Mob mob, LivingEntity target) {
		if (!(target instanceof Player player) || !(mob instanceof Enemy) || mob.getMaxHealth() > DREAD_MAX_HEALTH
				|| mob.is(Souls.VACUUM_IMMUNE) || mob.is(Souls.VACUUM_RESISTANT) || !SET.wearsFullSet(player)) {
			return false;
		}
		Set<UUID> provokedBy = PROVOKED.get(mob);
		return provokedBy == null || !provokedBy.contains(player.getUUID());
	}

	/** Called when a mob balks at a target: now and then it backs off, with a shiver of soul fire. */
	public static void balk(Mob mob, LivingEntity target) {
		if (!(mob.level() instanceof ServerLevel level) || !(mob instanceof PathfinderMob pathfinder)) {
			return;
		}
		int now = level.getServer().getTickCount();
		if (NEXT_FLEE.getOrDefault(mob, 0) > now) {
			return;
		}
		NEXT_FLEE.put(mob, now + FLEE_COOLDOWN);
		level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
		if (level.getRandom().nextFloat() < FLEE_CHANCE) {
			Vec3 away = DefaultRandomPos.getPosAway(pathfinder, 10, 5, target.position());
			if (away != null) {
				pathfinder.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		// Mobs already hunting someone who's just put the set on think better of it.
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!SET.wearsFullSet(player)) {
				continue;
			}
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DREAD_RANGE),
					mob -> mob.getTarget() == player && dreads(mob, player))) {
				mob.setTarget(null);
				balk(mob, player);
			}
		}
	}

	private static void lifesteal(ServerLevel level, LivingEntity killer, LivingEntity victim) {
		killer.heal(LIFESTEAL);
		Vec3 from = victim.getBoundingBox().getCenter();
		Vec3 to = killer.getBoundingBox().getCenter().subtract(from);
		for (int i = 0; i < 6; i++) {
			Vec3 at = from.add(to.scale(i / 6.0));
			level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.01);
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 8, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 1.2f);
	}

	public static void initialize() {
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, victim, source) -> {
			// Only a blow struck in hand: not an arrow or a thrown weapon.
			if (killer instanceof LivingEntity wielder && source.getDirectEntity() == wielder && SET.isWeapon(wielder.getMainHandItem())) {
				lifesteal(level, wielder, victim);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof Mob mob && source.getEntity() instanceof Player player) {
				PROVOKED.computeIfAbsent(mob, m -> new HashSet<>()).add(player.getUUID());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SoulSteel::tick);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.NETHERITE_INGOT, SET.ingot, SET.nugget));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.insertAfter(Items.NETHERITE_BLOCK, SET.block.asItem()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.DIAMOND_HOE, SET.shovel, SET.pickaxe, SET.axe, SET.hoe));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.DIAMOND_SWORD, SET.sword);
			output.insertAfter(Items.DIAMOND_AXE, SET.axe);
			output.insertAfter(Items.DIAMOND_BOOTS, SET.helmet, SET.chestplate, SET.leggings, SET.boots);
		});
	}
}
