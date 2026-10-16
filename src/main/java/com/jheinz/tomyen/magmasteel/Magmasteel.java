package com.jheinz.tomyen.magmasteel;

import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.foundry.FoundryRecipe;
import com.jheinz.tomyen.metal.MetalSet;

/**
 * Magmasteel: iron alloyed with a magma block in a white-hot Foundry. Its tools sit between iron and diamond (iron's
 * mining level, and enchant as readily as gold) and smelt whatever they mine (see {@link AutoSmelt}); its sword and
 * axe set what they hit alight, one Fire Aspect level's worth on top of any they carry
 * ({@link MagmasteelWeaponItem}).
 *
 * <p>Its armor shrugs off fire: each piece takes {@link #FIRE_REDUCTION_PER_PIECE} off fire and lava damage
 * (LivingEntityMagmasteelMixin), and the full set makes the wearer immune to it, puts them out, and lets them walk on
 * still lava as if it were a slow, solid floor (LiquidBlockMagmasteelMixin and {@link #LAVA_WADING_SLOWDOWN}).
 */
public final class Magmasteel {
	public static final String NAME = "magmasteel";
	public static final int FOUNDRY_TEMPERATURE = 1200;
	public static final int FOUNDRY_TICKS = 300;
	public static final float FIRE_REDUCTION_PER_PIECE = 0.15f;
	/** How much slower the full set walks across lava than on land. */
	private static final double LAVA_WADING_SLOWDOWN = -0.45;
	private static final Identifier LAVA_WADING_ID = MyMod.id("magmasteel_lava_wading");
	/** The floor lava makes for the full set: a hair above the surface, so the wearer's feet stay out of it. */
	public static final VoxelShape LAVA_FLOOR = Block.column(16.0, 0.0, 15.0);

	public static final TagKey<Item> REPAIRS = MetalSet.repairTag(NAME);
	public static final ToolMaterial TOOL_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 650, 7.0f,
			2.5f, 22, REPAIRS);
	public static final ArmorMaterial ARMOR_MATERIAL = new ArmorMaterial(22,
			Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 7, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2, ArmorType.BODY, 7),
			20, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f, REPAIRS, ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id(NAME)));

	public static final MetalSet SET = new MetalSet(NAME, TOOL_MATERIAL, ARMOR_MATERIAL, 5.5f, -3.05f, -2.5f, -0.5f,
			MagmasteelWeaponItem::new, List.of("item.tomyen.magmasteel.auto_smelt"),
			List.of("item.tomyen.magmasteel.armor_piece", "item.tomyen.magmasteel.armor_set"), MapColor.COLOR_ORANGE, 6, true);

	private Magmasteel() {
	}

	public static List<FoundryRecipe> foundry() {
		return List.of(new FoundryRecipe(SET.ingot, 1, FOUNDRY_TICKS,
				List.of(new FoundryRecipe.Cost(Items.IRON_INGOT, 1), new FoundryRecipe.Cost(Items.MAGMA_BLOCK, 1)))
				.withMinTemperature(FOUNDRY_TEMPERATURE));
	}

	/** What fraction of fire and lava damage gets through the magmasteel the entity wears. */
	public static float fireDamageScale(LivingEntity entity) {
		return 1.0f - FIRE_REDUCTION_PER_PIECE * SET.piecesWorn(entity);
	}

	/** Whether still lava at {@code pos} is solid underfoot for whatever is asking (see LiquidBlockMagmasteelMixin). */
	public static boolean isLavaFloor(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		// The entity first: block states work out their shapes with no entity while tags are still unbound.
		if (!(context instanceof EntityCollisionContext entityContext) || !(entityContext.getEntity() instanceof LivingEntity entity)
				|| !SET.wearsFullSet(entity)) {
			return false;
		}
		FluidState fluid = state.getFluidState();
		return fluid.is(FluidTags.LAVA) && fluid.isSource() && context.isAbove(LAVA_FLOOR, pos, true)
				&& !level.getFluidState(pos.above()).is(FluidTags.LAVA);
	}

	private static boolean onLava(LivingEntity entity) {
		return entity.onGround() && entity.level().getFluidState(entity.blockPosition()).is(FluidTags.LAVA);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean fullSet = SET.wearsFullSet(player);
			if (fullSet && player.getRemainingFireTicks() > 0) {
				player.clearFire();
			}
			// Lava holds the full set up, but wading across it is slow going.
			AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			boolean wading = fullSet && onLava(player);
			if (speed != null && wading != speed.hasModifier(LAVA_WADING_ID)) {
				if (wading) {
					speed.addTransientModifier(new AttributeModifier(LAVA_WADING_ID, LAVA_WADING_SLOWDOWN,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				} else {
					speed.removeModifier(LAVA_WADING_ID);
				}
			}
		}
	}

	public static void initialize() {
		LootTableEvents.MODIFY_DROPS.register(AutoSmelt::modifyDrops);
		// The full set: no fire or lava damage at all.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(source.is(DamageTypeTags.IS_FIRE) && SET.wearsFullSet(entity)));
		ServerTickEvents.END_SERVER_TICK.register(Magmasteel::tick);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.IRON_INGOT, SET.ingot, SET.nugget));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.insertAfter(Items.IRON_BLOCK, SET.block.asItem()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.IRON_HOE, SET.shovel, SET.pickaxe, SET.axe, SET.hoe));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.IRON_SWORD, SET.sword);
			output.insertAfter(Items.IRON_AXE, SET.axe);
			output.insertAfter(Items.IRON_BOOTS, SET.helmet, SET.chestplate, SET.leggings, SET.boots);
		});
	}
}
