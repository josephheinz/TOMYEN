package com.jheinz.tomyen.starforge;

import java.util.Set;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.tomyen.ModBlocks;
import com.jheinz.tomyen.MyMod;

/**
 * The Star Forge (Exotic tier): a meteorite-stone and star-steel forge that draws starlight to infuse gear. It works
 * only under an open night sky (see {@link StarForgeBlockEntity}), burning star crystal shards as starlight, and puts
 * one {@link Infusion} into a weapon, tool, armor piece, gun, drill or rod from a catalyst out of one of the mod's other
 * threads: star crystal, alien alloy, void flux, soul charges, fae dust, froststeel, magmasteel, holy silver, dragon
 * scales and enriched uranium.
 */
public final class StarForge {
	/** On a piece of gear: its infusion. Absent means none. */
	public static final DataComponentType<Infusion> INFUSION = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("infusion"), DataComponentType.<Infusion>builder().persistent(Infusion.CODEC).networkSynchronized(Infusion.STREAM_CODEC)
					.build());

	/** On a dead player: their Soulbound gear, held over from death to respawn. */
	public static final AttachmentType<ItemContainerContents> SOULBOUND_STASH = AttachmentRegistry.<ItemContainerContents>builder()
			.persistent(ItemContainerContents.CODEC)
			.copyOnDeath()
			.buildAndRegister(MyMod.id("soulbound_stash"));

	public static final Block STAR_FORGE = block("star_forge", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(5.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK)
			.noOcclusion().lightLevel(StarForgeBlock::lightLevel));
	public static final BlockEntityType<StarForgeBlockEntity> STAR_FORGE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("star_forge"), new BlockEntityType<>(StarForgeBlockEntity::new, Set.of(STAR_FORGE)));
	public static final MenuType<StarForgeMenu> STAR_FORGE_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("star_forge"),
			new MenuType<>(StarForgeMenu::new, FeatureFlags.VANILLA_SET));

	private StarForge() {
	}

	private static Block block(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new StarForgeBlock(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().rarity(Rarity.RARE)));
		return block;
	}

	public static void initialize() {
		InfusionEffects.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(ModBlocks.FOUNDRY, STAR_FORGE));
	}
}
