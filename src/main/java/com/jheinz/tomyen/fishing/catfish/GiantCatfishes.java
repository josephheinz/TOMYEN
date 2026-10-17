package com.jheinz.tomyen.fishing.catfish;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.BaitItem;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.FishingFights;
import com.jheinz.tomyen.fishing.PartSlot;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.fishing.RodPartItem;

/**
 * The Giant Catfish, the River Monster ({@link GiantCatfish}), and everything that comes with it: a rare river boss for
 * the Workshop and Foundry tiers, hooked on a rod at night or met in deep river water.
 *
 * <ul>
 * <li><b>Stink Bait</b> (rotten flesh, Raw Shrimp and garlic): what draws it, {@value #STINK_ATTRACTION} times likelier
 * to come to the hook, and bigger; everything else bites a little sooner at night.</li>
 * <li>Killed, it drops <b>Catfish Fillets</b> (food; they cook, and it drops them cooked if it burned), <b>Catfish
 * Whiskers</b>, bones and rusty scrap from the junk in its belly, and, now and then if a player killed it, the
 * <b>Ancient Hook</b> that was in its lip: a rod hook it's shrugged off for a century, that holds anything.</li>
 * <li>Two Catfish Whiskers and fish oil braid a <b>Whisker Line</b>, the touchiest rod line there is: bites come sooner,
 * the reel's green zone is wider and the line holds strong.</li>
 * <li>Killed by a player, it gives up a <b>Giant Catfish</b> trophy of its weight, too big for a Fish Mount: it goes on a
 * <b>Giant Fish Mount</b>, a carved board five blocks wide, made from three Fish Mounts, steel plate and gold.</li>
 * </ul>
 */
public final class GiantCatfishes {
	/** In a river's monster spawns (zombies are 95): about one in five hundred picks, and then only in deep water. */
	private static final int RIVER_WEIGHT = 1;
	static final float STINK_ATTRACTION = 30.0f;
	public static final String SPECIES_ID = "tomyen:giant_catfish";

	public static final EntityType<GiantCatfish> GIANT_CATFISH_ENTITY = entity("giant_catfish",
			EntityType.Builder.of(GiantCatfish::new, MobCategory.MONSTER)
					// Five blocks long as drawn; the box is its thick middle, head to vent.
					.sized(2.4f, 1.2f)
					.eyeHeight(0.6f)
					.clientTrackingRange(10)
					.notInPeaceful());

	/** River monsters, for Stink Bait. */
	public static final TagKey<Item> RIVER_MONSTERS = TagKey.create(Registries.ITEM, MyMod.id("river_monsters"));

	/** The trophy: only ever made as one, with its weight and catcher on it. */
	public static final Item GIANT_CATFISH = item("giant_catfish", properties -> new HintItem(properties, "tooltip.tomyen.giant_catfish"),
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item CATFISH_FILLET = item("catfish_fillet", Item::new,
			new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).build()));
	public static final Item COOKED_CATFISH_FILLET = item("cooked_catfish_fillet", Item::new,
			new Item.Properties().food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.8f).build()));
	public static final Item CATFISH_WHISKER = item("catfish_whisker", properties -> new HintItem(properties, "tooltip.tomyen.catfish_whisker"),
			new Item.Properties());
	/** A rod line braided from whiskers: it feels every nibble. */
	public static final Item WHISKER_LINE = item("whisker_line", properties -> new RodPartItem(properties, PartSlot.LINE,
			RodModifiers.builder().line(0.4f).zone(0.12f).biteTicks(20).build()), new Item.Properties().rarity(Rarity.UNCOMMON));
	/** The hook it carried in its lip for a century: whatever's on it stays on, and the big ones come to it. */
	public static final Item ANCIENT_HOOK = item("ancient_hook", properties -> new RodPartItem(properties, PartSlot.HOOK,
			RodModifiers.builder().escape(0.35f).rarity(0.08f).build()), new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item STINK_BAIT = item("stink_bait", properties -> new BaitItem(properties, new BaitItem.Effect(
			RodModifiers.builder().nightBiteTicks(60).build(), 0.15f, RIVER_MONSTERS, STINK_ATTRACTION, 0.3f, false, 0)),
			new Item.Properties());
	public static final Item GIANT_CATFISH_SPAWN_EGG = item("giant_catfish_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(GIANT_CATFISH_ENTITY));

	public static final Block GIANT_FISH_MOUNT = block("giant_fish_mount", GiantFishMountBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5f).sound(SoundType.WOOD).noOcclusion().noCollision()
					.ignitedByLava());
	public static final BlockEntityType<GiantFishMountBlockEntity> GIANT_FISH_MOUNT_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, MyMod.id("giant_fish_mount"),
			new BlockEntityType<>(GiantFishMountBlockEntity::new, Set.of(GIANT_FISH_MOUNT)));

	public static final SoundEvent AMBIENT = sound("giant_catfish_ambient");
	public static final SoundEvent HURT = sound("giant_catfish_hurt");
	public static final SoundEvent DEATH = sound("giant_catfish_death");
	/** A bellow from the deep: when it comes up on a line, and when it lines up a charge. */
	public static final SoundEvent ROAR = sound("giant_catfish_roar");
	public static final SoundEvent BITE = sound("giant_catfish_bite");
	public static final SoundEvent SLAP = sound("giant_catfish_slap");
	public static final SoundEvent SPLASH = sound("giant_catfish_splash");
	/** Flopping about on land, and shaking what it's dragging. */
	public static final SoundEvent FLOP = sound("giant_catfish_flop");
	public static final SoundEvent SWIM = sound("giant_catfish_swim");
	public static final SoundEvent HARPOONED = sound("giant_catfish_harpooned");

	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));

	private GiantCatfishes() {
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		item(name, itemProperties -> new HintBlockItem(block, itemProperties, "tooltip.tomyen.giant_fish_mount"),
				new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(GIANT_CATFISH_ENTITY, GiantCatfish.createAttributes());
		SpawnPlacements.register(GIANT_CATFISH_ENTITY, SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR,
				GiantCatfish::checkSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_RIVER), MobCategory.MONSTER, GIANT_CATFISH_ENTITY, RIVER_WEIGHT, 1, 1);
		FishingFights.surfacing(GiantCatfish::surface);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.ELDER_GUARDIAN_SPAWN_EGG, GIANT_CATFISH_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> output.insertAfter(Items.COOKED_SALMON, CATFISH_FILLET, COOKED_CATFISH_FILLET));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.PRISMARINE_CRYSTALS, CATFISH_WHISKER));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Fishing.PARTS.getLast(), WHISKER_LINE, ANCIENT_HOOK, STINK_BAIT));
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_BLOCKS_TAB)
				.register(output -> output.insertAfter(Fishing.FISH_MOUNTS.getLast().asItem(), GIANT_FISH_MOUNT));
	}

	/** An item with a line of grey advice under its name. */
	static class HintItem extends Item {
		private final String hint;

		HintItem(Properties properties, String hint) {
			super(properties);
			this.hint = hint;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
				TooltipFlag flag) {
			tooltip.accept(Component.translatable(hint).withStyle(ChatFormatting.GRAY));
		}
	}

	static class HintBlockItem extends BlockItem {
		private final String hint;

		HintBlockItem(Block block, Properties properties, String hint) {
			super(block, properties);
			this.hint = hint;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
				TooltipFlag flag) {
			tooltip.accept(Component.translatable(hint).withStyle(ChatFormatting.GRAY));
		}
	}
}
