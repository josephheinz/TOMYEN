package com.jheinz.tomyen.fishing.josh;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.ImmutableSet;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.fish.Fishes;

/**
 * Josh: a villager who sells Fish Fish, only Fish Fish, at every level. Being Josh is his profession (its name is
 * "Josh"), its job site the {@link FishFishCrateBlock Fish Fish Crate}, and whoever takes the job is named Josh, the name
 * always showing (a name tag won't stick: he goes back to Josh). His trades are data-driven like the banker's:
 * data/tomyen/trade_set/josh/level_N.json, each listing trades in data/tomyen/villager_trade/josh/ (tools/gen_josh.py).
 *
 * <p>Where he comes from:
 * <ul>
 * <li>Now and then, Josh moves into a village ({@link JoshArrivals}): about one village in eight, once, bringing his crate.
 * <li>Anyone can make one: an unemployed villager next to a Fish Fish Crate (a barrel and two Fish Fish) takes the job.
 * </ul>
 *
 * <p>Josh holds a Fish Fish when he isn't showing a trade, says fishy things in chat when players are near
 * ({@link #LINES}), and some of his idle mumbling is a fish flopping (VillagerJoshMixin). His outfit (yellow
 * oilskins, a sou'wester with a Fish Fish on it) is the profession's texture layer.
 */
public final class Josh {
	/** His name, always. */
	public static final String NAME = "Josh";
	/** How many lines he has (chat.tomyen.josh.N). */
	private static final int LINES = 8;
	/** How often (ticks) Joshes near players are looked after: their fish, their name, and maybe a word. */
	private static final int TICK_EVERY = 100;
	/** The chance, each look, that a Josh near a player says something (about once a minute). */
	private static final float SPEAK_CHANCE = 1.0f / 12.0f;
	/** How near a player has to be for Josh to bother. */
	private static final double EARSHOT = 10.0;

	public static final Block FISH_FISH_CRATE = block("fish_fish_crate", FishFishCrateBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.PODZOL).strength(2.5f).sound(SoundType.WOOD).noOcclusion().ignitedByLava());

	public static final ResourceKey<PoiType> CRATE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MyMod.id("fish_fish_crate"));
	public static final PoiType CRATE_POI_TYPE = PoiHelper.register(CRATE_POI.identifier(), 1, 1, FISH_FISH_CRATE);

	public static final ResourceKey<VillagerProfession> PROFESSION_KEY = ResourceKey.create(Registries.VILLAGER_PROFESSION, MyMod.id("josh"));
	public static final VillagerProfession PROFESSION = Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, PROFESSION_KEY,
			new VillagerProfession(Component.translatable("entity.tomyen.villager.josh"),
					poi -> poi.is(CRATE_POI), poi -> poi.is(CRATE_POI), ImmutableSet.of(), ImmutableSet.of(),
					SoundEvents.VILLAGER_WORK_FISHERMAN,
					Int2ObjectMap.ofEntries(
							Int2ObjectMap.entry(1, tradeSet(1)),
							Int2ObjectMap.entry(2, tradeSet(2)),
							Int2ObjectMap.entry(3, tradeSet(3)),
							Int2ObjectMap.entry(4, tradeSet(4)),
							Int2ObjectMap.entry(5, tradeSet(5)))));

	private Josh() {
	}

	private static ResourceKey<TradeSet> tradeSet(int level) {
		return ResourceKey.create(Registries.TRADE_SET, MyMod.id("josh/level_" + level));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static boolean isJosh(Entity entity) {
		return entity instanceof Villager villager && villager.getVillagerData().profession().is(PROFESSION_KEY);
	}

	/** Whether the entity's name is already Josh. */
	static boolean namedJosh(Entity entity) {
		return entity.getCustomName() != null && NAME.equals(entity.getCustomName().getString());
	}

	/** Josh's name, showing. */
	public static void name(Villager villager) {
		if (!namedJosh(villager)) {
			villager.setCustomName(Component.literal(NAME));
		}
		villager.setCustomNameVisible(true);
	}

	/** A villager who stops being Josh (his crate gone before he ever traded) stops being called Josh. */
	public static void unname(Villager villager) {
		if (namedJosh(villager)) {
			villager.setCustomName(null);
			villager.setCustomNameVisible(false);
		}
	}

	/** Says one of his lines to everyone within earshot, as chat ("<Josh> Fish Fish."). */
	public static void speak(ServerLevel level, Villager josh) {
		Component line = Component.translatable("chat.tomyen.josh." + josh.getRandom().nextInt(LINES));
		Component message = Component.translatable("chat.type.text", josh.getDisplayName(), line);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(josh) <= EARSHOT * EARSHOT * 1.5) {
				player.sendSystemMessage(message);
			}
		}
	}

	/** Every Josh near a player: back to his name, a Fish Fish back in his hand, and now and then a word. */
	private static void tick(ServerLevel level) {
		if (level.getGameTime() % TICK_EVERY != 0) {
			return;
		}
		Set<Villager> joshes = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator()) {
				joshes.addAll(level.getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(EARSHOT), Josh::isJosh));
			}
		}
		for (Villager josh : joshes) {
			name(josh);
			if (josh.getTradingPlayer() == null && josh.getMainHandItem().isEmpty() && !josh.isSleeping()) {
				// Showing a trade puts that in his hand and takes it away after; between trades he holds a Fish Fish.
				josh.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Fishes.FISH_FISH));
				josh.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
			}
			if (josh.getTradingPlayer() == null && !josh.isSleeping() && josh.getRandom().nextFloat() < SPEAK_CHANCE) {
				speak(level, josh);
			}
		}
	}

	public static void initialize() {
		JoshArrivals.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.BARREL, FISH_FISH_CRATE));
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			tick(level);
			JoshArrivals.tick(level);
		});
		// A greeting when he's asked for his wares (the trades open as usual).
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel serverLevel && entity instanceof Villager josh && isJosh(josh)
					&& josh.getTradingPlayer() == null && josh.getRandom().nextInt(3) == 0) {
				speak(serverLevel, josh);
			}
			return InteractionResult.PASS;
		});
	}
}
