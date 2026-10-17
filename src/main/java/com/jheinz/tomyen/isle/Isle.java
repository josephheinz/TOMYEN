package com.jheinz.tomyen.isle;

import java.util.Set;
import java.util.function.Function;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.isle.worldgen.IsleFeatures;

/**
 * The Isle of the Damned, pass 1: a Dark Souls corpse run.
 *
 * <p>A player who dies with anything on them leaves a {@link GravestoneBlock} where they fell; what they carried and
 * half their experience go into their {@link Hoard} on the Isle (see {@link DeathCapture}), and a {@link Revenant}, a
 * ghost of them in their death gear, guards it there. The owner pays respects at the stone (sneak and hold use for
 * five seconds) or offers it something holy or soul-touched to be pulled down to the Isle ({@link PayingRespects});
 * beating the Revenant spills the hoard into Soul Caches only they can open and opens a Return Rift home. Dying on the
 * Isle drags the hoard a depth deeper ({@link IsleDepth}). The Lantern of Return at the arrival shrine always leads
 * home, leaving the hoard where it is.
 *
 * <p>This class registers it all; {@link IsleDimension} is the dimension itself and {@link IsleEvents} the hooks later
 * passes (shades, the Soul Tide) build on.
 */
public final class Isle {
	// --- Sounds (vanilla ones, re-pitched: see sounds.json) -----------------------------------------------------------
	public static final SoundEvent RESPECTS = sound("isle_respects");
	public static final SoundEvent PULLED_DOWN = sound("isle_pulled_down");
	public static final SoundEvent OFFERING = sound("isle_offering");
	public static final SoundEvent ARRIVE = sound("isle_arrive");
	public static final SoundEvent RETURN = sound("isle_return");
	public static final SoundEvent HOARD_SINKS = sound("isle_hoard_sinks");
	public static final SoundEvent ISLE_LOOP = sound("isle_loop");
	public static final SoundEvent ISLE_ADDITIONS = sound("isle_additions");
	public static final SoundEvent ISLE_MUSIC = sound("music.isle");
	public static final SoundEvent REVENANT_AMBIENT = sound("revenant_ambient");
	public static final SoundEvent REVENANT_HURT = sound("revenant_hurt");
	public static final SoundEvent REVENANT_DEATH = sound("revenant_death");
	public static final SoundEvent REVENANT_FORM = sound("revenant_form");
	public static final SoundEvent SOUL_CACHE_OPEN = sound("soul_cache_open");
	public static final SoundEvent RIFT_OPENS = sound("return_rift_opens");

	// --- Tags ---------------------------------------------------------------------------------------------------------
	/** Offered at a Gravestone: an instant trip down, and Soul Ward on arrival. */
	public static final TagKey<Item> HOLY_OFFERINGS = TagKey.create(Registries.ITEM, MyMod.id("isle_holy_offerings"));
	public static final TagKey<Item> SOUL_OFFERINGS = TagKey.create(Registries.ITEM, MyMod.id("isle_soul_offerings"));
	/** The Isle's natural ground: what its shades will spawn on (pass 2). */
	public static final TagKey<Block> ISLE_GROUND = TagKey.create(Registries.BLOCK, MyMod.id("isle_ground"));

	// --- Soul Ward ----------------------------------------------------------------------------------------------------
	/** From arriving by offering: extra armor and toughness for the first minute on the Isle. */
	public static final Holder<MobEffect> SOUL_WARD = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("soul_ward"),
			new SoulWardEffect().addAttributeModifier(Attributes.ARMOR, MyMod.id("effect.soul_ward.armor"), 6.0,
					AttributeModifier.Operation.ADD_VALUE)
					.addAttributeModifier(Attributes.ARMOR_TOUGHNESS, MyMod.id("effect.soul_ward.toughness"), 4.0,
							AttributeModifier.Operation.ADD_VALUE)
					.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, MyMod.id("effect.soul_ward.knockback"), 0.3,
							AttributeModifier.Operation.ADD_VALUE));

	// --- The Isle's ground --------------------------------------------------------------------------------------------
	public static final Block ASHEN_STONE = block("ashen_stone", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5f, 6.0f).sound(SoundType.TUFF));
	public static final Block ASH_SOIL = block("ash_soil", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY)
			.strength(0.5f).sound(SoundType.SOUL_SOIL));
	/** Glows faintly, the only light in the Hollows besides the braziers. */
	public static final Block SOUL_MOSS = block("soul_moss", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
			.strength(0.1f).sound(SoundType.MOSS).lightLevel(state -> 3));
	public static final Block SOUL_MOSS_CARPET = block("soul_moss_carpet", CarpetBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_CYAN).strength(0.1f).sound(SoundType.MOSS_CARPET).lightLevel(state -> 2).pushReaction(PushReaction.POPPED));
	public static final Block GRAVE_STONE_BRICKS = block("grave_stone_bricks", Block::new, bricks());
	public static final Block CRACKED_GRAVE_STONE_BRICKS = block("cracked_grave_stone_bricks", Block::new, bricks());
	public static final Block MOSSY_GRAVE_STONE_BRICKS = block("mossy_grave_stone_bricks", Block::new, bricks());
	public static final Block GRAVE_STONE_BRICK_STAIRS = block("grave_stone_brick_stairs",
			properties -> new StairBlock(GRAVE_STONE_BRICKS.defaultBlockState(), properties), bricks());
	public static final Block GRAVE_STONE_BRICK_SLAB = block("grave_stone_brick_slab", SlabBlock::new, bricks());
	public static final Block GRAVE_STONE_BRICK_WALL = block("grave_stone_brick_wall", WallBlock::new, bricks().forceSolidOn());

	// --- Ruins and remains --------------------------------------------------------------------------------------------
	/** An old, nameless headstone, leaning where it's sunk. Decoration; the Isle is full of them. */
	public static final Block OLD_HEADSTONE = block("old_headstone", OldHeadstoneBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5f, 6.0f)
			.sound(SoundType.TUFF).noOcclusion());
	public static final Block BONE_PILE = block("bone_pile", BonePileBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
			.strength(0.8f).sound(SoundType.BONE_BLOCK).noOcclusion().pushReaction(PushReaction.POPPED));
	public static final Block SOUL_BRAZIER = block("soul_brazier", SoulBrazierBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5f).sound(SoundType.LANTERN).lightLevel(state -> 13)
			.noOcclusion());

	// --- What the corpse run is made of (no items: none can be carried off) ------------------------------------------
	public static final Block GRAVESTONE = blockOnly("gravestone", GravestoneBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).strength(-1.0f, 3600000.0f).sound(SoundType.STONE)
			.noOcclusion().lightLevel(state -> 6).pushReaction(PushReaction.IMMOVEABLE).noLootTable());
	public static final Block SOUL_CACHE = blockOnly("soul_cache", SoulCacheBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_CYAN).instrument(NoteBlockInstrument.BASEDRUM).strength(-1.0f, 3600000.0f).sound(SoundType.BONE_BLOCK)
			.noOcclusion().lightLevel(state -> 9).pushReaction(PushReaction.IMMOVEABLE).noLootTable());
	public static final Block LANTERN_OF_RETURN = blockOnly("lantern_of_return", LanternOfReturnBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(state -> 15)
			.pushReaction(PushReaction.IMMOVEABLE).noLootTable());
	public static final Block RETURN_RIFT = blockOnly("return_rift", ReturnRiftBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0f, 3600000.0f).sound(SoundType.GLASS).noCollision().noOcclusion()
			.lightLevel(state -> 12).pushReaction(PushReaction.IMMOVEABLE).noLootTable());

	public static final BlockEntityType<GravestoneBlockEntity> GRAVESTONE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("gravestone"), new BlockEntityType<>(GravestoneBlockEntity::new, Set.of(GRAVESTONE)));
	public static final BlockEntityType<SoulCacheBlockEntity> SOUL_CACHE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("soul_cache"), new BlockEntityType<>(SoulCacheBlockEntity::new, Set.of(SOUL_CACHE)));

	// --- The Revenant -------------------------------------------------------------------------------------------------
	/** Never saved with the world: {@link RevenantManager} raises it from its hoard when its owner comes near. */
	public static final EntityType<Revenant> REVENANT = entity("revenant", EntityType.Builder.of(Revenant::new, MobCategory.MONSTER)
			.sized(0.6f, 1.8f)
			.eyeHeight(1.62f)
			.clientTrackingRange(10)
			.noSave()
			.noLootTable()
			.noSummon());

	// --- On players ---------------------------------------------------------------------------------------------------
	/** Where a player was pulled down to the Isle from, so its ways out can send them back there. */
	public static final AttachmentType<GlobalPos> RETURN_POINT = AttachmentRegistry.<GlobalPos>builder()
			.persistent(GlobalPos.CODEC)
			.buildAndRegister(MyMod.id("isle_return_point"));
	/** Where the player's own Revenant waits, for their HUD (see IsleHint). */
	public static final AttachmentType<IsleHint> HINT = AttachmentRegistry.<IsleHint>builder()
			.syncWith(IsleHint.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(MyMod.id("isle_hint"));

	private Isle() {
	}

	private static BlockBehaviour.Properties bricks() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
				.strength(1.5f, 6.0f).sound(SoundType.TUFF_BRICKS);
	}

	private static Block blockOnly(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = blockOnly(name, factory, properties);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
		return block;
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(REVENANT, Revenant.createAttributes());
		Hoards.initialize();
		IsleFeatures.initialize();
		IsleDimension.initialize();
		DeathCapture.initialize();
		PayingRespects.initialize();
		RevenantManager.initialize();
		IsleCommands.initialize();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.insertAfter(Items.TUFF, ASHEN_STONE);
			output.insertAfter(Items.SOUL_SOIL, ASH_SOIL);
			output.insertAfter(Items.MOSS_CARPET, SOUL_MOSS, SOUL_MOSS_CARPET);
			output.insertAfter(Items.BONE_BLOCK, BONE_PILE);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> output.insertAfter(Items.TUFF_BRICK_WALL,
				ASHEN_STONE, GRAVE_STONE_BRICKS, CRACKED_GRAVE_STONE_BRICKS, MOSSY_GRAVE_STONE_BRICKS, GRAVE_STONE_BRICK_STAIRS,
				GRAVE_STONE_BRICK_SLAB, GRAVE_STONE_BRICK_WALL));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.SOUL_LANTERN, SOUL_BRAZIER, OLD_HEADSTONE));
	}

	/** Soul Ward: does nothing itself but carry its attribute bonuses. */
	private static final class SoulWardEffect extends MobEffect {
		SoulWardEffect() {
			super(MobEffectCategory.BENEFICIAL, 0x5FD8E8);
		}
	}
}
