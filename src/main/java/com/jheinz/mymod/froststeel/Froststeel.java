package com.jheinz.mymod.froststeel;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.foundry.FoundryRecipe;
import com.jheinz.mymod.magmasteel.Magmasteel;
import com.jheinz.mymod.metal.MetalSet;

/**
 * Froststeel: iron quenched with blue ice in a cold Foundry, magmasteel's opposite. The quench has a ceiling rather
 * than a floor: the Foundry holds at {@link #FOUNDRY_TEMPERATURE} while it works, and a hotter one has to cool down
 * to it first. Its tools sit between iron and diamond, like magmasteel's, and its sword and axe chill what they hit:
 * Slowness and powder snow's frost, and a blow on a fully frozen target shatters the ice for extra damage
 * ({@link FroststeelWeaponItem}).
 *
 * <p>Its armor keeps the cold out: each piece takes {@link #FREEZE_REDUCTION_PER_PIECE} off freeze damage, and the
 * full set can't freeze at all (LivingEntityFroststeelMixin), walks on powder snow without sinking
 * (PowderSnowBlockFroststeelMixin) and freezes still water underfoot as Frost Walker I does, without the enchantment.
 */
public final class Froststeel {
	public static final String NAME = "froststeel";
	/** The hottest the Foundry may be for the quench. */
	public static final int FOUNDRY_TEMPERATURE = 400;
	public static final int FOUNDRY_TICKS = 200;
	public static final float FREEZE_REDUCTION_PER_PIECE = 0.2f;
	/** Frost Walker I's reach: water within this many blocks of the wearer's feet freezes. */
	private static final int ICE_WALK_RADIUS = 3;

	public static final TagKey<Item> REPAIRS = MetalSet.repairTag(NAME);
	public static final ToolMaterial TOOL_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 750, 7.0f,
			2.5f, 16, REPAIRS);
	public static final ArmorMaterial ARMOR_MATERIAL = new ArmorMaterial(24,
			Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 2, ArmorType.BODY, 7),
			16, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f, REPAIRS, ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id(NAME)));

	public static final MetalSet SET = new MetalSet(NAME, TOOL_MATERIAL, ARMOR_MATERIAL, 5.5f, -3.05f, -2.5f, -0.5f,
			FroststeelWeaponItem::new, List.of(),
			List.of("item.mymod.froststeel.armor_piece", "item.mymod.froststeel.armor_set"), MapColor.ICE, 0, false);

	/** Where each wearer of the full set last froze the water around them: it freezes again when they move on. */
	private static final Map<ServerPlayer, BlockPos> LAST_STEP = new WeakHashMap<>();

	private Froststeel() {
	}

	public static List<FoundryRecipe> foundry() {
		return List.of(new FoundryRecipe(SET.ingot, 1, FOUNDRY_TICKS,
				List.of(new FoundryRecipe.Cost(Items.IRON_INGOT, 1), new FoundryRecipe.Cost(Items.BLUE_ICE, 1)))
				.withMaxTemperature(FOUNDRY_TEMPERATURE));
	}

	/** What fraction of freeze damage gets through the froststeel the entity wears. */
	public static float freezeDamageScale(LivingEntity entity) {
		return Math.max(0.0f, 1.0f - FREEZE_REDUCTION_PER_PIECE * SET.piecesWorn(entity));
	}

	/** Whether the cold can't touch this entity: a full set keeps it from freezing at all. */
	public static boolean coldProof(LivingEntity entity) {
		return SET.wearsFullSet(entity);
	}

	/**
	 * Frost Walker I without the enchantment: still water in a disc around the wearer's feet turns to frosted ice
	 * (which melts away behind them), when they're on the ground and not riding.
	 */
	private static void freezeWater(ServerPlayer player) {
		BlockPos at = player.blockPosition();
		if (!player.onGround() || player.isPassenger() || at.equals(LAST_STEP.get(player))) {
			return;
		}
		LAST_STEP.put(player, at);
		ServerLevel level = player.level();
		Vec3 feet = player.position();
		BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
		BlockPos centre = at.below();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-ICE_WALK_RADIUS, 0, -ICE_WALK_RADIUS),
				centre.offset(ICE_WALK_RADIUS, 0, ICE_WALK_RADIUS))) {
			if (pos.distToCenterSqr(feet.x, pos.getY() + 0.5, feet.z) < Mth.square(ICE_WALK_RADIUS)
					&& level.getBlockState(pos.above()).isAir() && level.getBlockState(pos).is(Blocks.WATER)
					&& level.getFluidState(pos).is(Fluids.WATER) && level.isUnobstructed(ice, pos, CollisionContext.empty())
					&& level.setBlockAndUpdate(pos, ice)) {
				level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (coldProof(player)) {
				freezeWater(player);
			} else {
				LAST_STEP.remove(player);
			}
		}
	}

	public static void initialize() {
		// The full set: no freeze damage at all.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(source.is(DamageTypeTags.IS_FREEZING) && coldProof(entity)));
		ServerTickEvents.END_SERVER_TICK.register(Froststeel::tick);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Magmasteel.SET.nugget, SET.ingot, SET.nugget));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.insertAfter(Magmasteel.SET.block.asItem(), SET.block.asItem()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Magmasteel.SET.hoe, SET.shovel, SET.pickaxe, SET.axe, SET.hoe));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Magmasteel.SET.sword, SET.sword);
			output.insertAfter(Magmasteel.SET.axe, SET.axe);
			output.insertAfter(Magmasteel.SET.boots, SET.helmet, SET.chestplate, SET.leggings, SET.boots);
		});
	}
}
