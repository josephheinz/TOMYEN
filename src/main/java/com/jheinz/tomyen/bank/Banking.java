package com.jheinz.tomyen.bank;

import java.util.function.Function;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;

import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.tomyen.MyMod;

/**
 * Everything for banking and credit: the banker villager and its ATM job site, bank vaults, credit and
 * debit cards, the debt each player carries, and the collectors sent after players who don't pay. Kept together so the rest
 * of the mod only has to call {@link #initialize()}.
 * <p>
 * The banker's trades are data-driven, like vanilla's: data/tomyen/trade_set/banker/level_N.json, each
 * listing trades in data/tomyen/villager_trade/banker/.
 */
public final class Banking {
	/** On a credit card: emeralds charged to it so far. Absent means nothing. */
	public static final DataComponentType<Integer> CARD_BALANCE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("card_balance"), DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/** On a debit card: whose vault it spends from. Absent until it's linked at an ATM. */
	public static final DataComponentType<CardHolder> CARD_HOLDER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("card_holder"), DataComponentType.<CardHolder>builder().persistent(CardHolder.CODEC)
					.networkSynchronized(CardHolder.STREAM_CODEC).build());

	/**
	 * On a player: their bank vault's contents (see {@link Vaults}). Kept through death and never dropped;
	 * synced to its own player so their debit card tooltips and trade previews know the balance.
	 */
	public static final AttachmentType<ItemContainerContents> VAULT = AttachmentRegistry.<ItemContainerContents>builder()
			.persistent(ItemContainerContents.CODEC)
			.copyOnDeath()
			.syncWith(ItemContainerContents.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(MyMod.id("vault"));

	/** On a player: their credit account. Survives death (the hunt decides what death does to it). */
	public static final AttachmentType<Debt> DEBT = AttachmentRegistry.<Debt>builder()
			.persistent(Debt.CODEC)
			.copyOnDeath()
			.syncWith(Debt.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(MyMod.id("debt"));

	// Credit limits, in emeralds, rising with the banker's level.
	public static final Item CREDIT_CARD = card("credit_card", 1000, Rarity.COMMON);
	public static final Item GOLD_CREDIT_CARD = card("gold_credit_card", 2500, Rarity.UNCOMMON);
	public static final Item PLATINUM_CREDIT_CARD = card("platinum_credit_card", 5000, Rarity.RARE);
	public static final Item DEBIT_CARD = item("debit_card", DebitCardItem::new, new Item.Properties().stacksTo(1));

	public static final Block ATM = block("atm", AtmBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
			.strength(3.5f, 9.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

	public static final MenuType<VaultMenu> VAULT_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("bank_vault"),
			new MenuType<>(VaultMenu::new, FeatureFlags.VANILLA_SET));

	public static final ResourceKey<PoiType> ATM_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MyMod.id("atm"));
	public static final PoiType ATM_POI_TYPE = PoiHelper.register(ATM_POI.identifier(), 1, 1, ATM);

	public static final ResourceKey<VillagerProfession> BANKER_KEY = ResourceKey.create(Registries.VILLAGER_PROFESSION, MyMod.id("banker"));
	public static final VillagerProfession BANKER = Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, BANKER_KEY,
			new VillagerProfession(Component.translatable("entity.tomyen.villager.banker"),
					poi -> poi.is(ATM_POI), poi -> poi.is(ATM_POI), ImmutableSet.of(), ImmutableSet.of(),
					SoundEvents.VILLAGER_WORK_CARTOGRAPHER,
					Int2ObjectMap.ofEntries(
							Int2ObjectMap.entry(1, tradeSet(1)),
							Int2ObjectMap.entry(2, tradeSet(2)),
							Int2ObjectMap.entry(3, tradeSet(3)),
							Int2ObjectMap.entry(4, tradeSet(4)),
							Int2ObjectMap.entry(5, tradeSet(5)))));

	public static final EntityType<DebtCollector> DEBT_COLLECTOR = entity("debt_collector",
			EntityType.Builder.of(DebtCollector::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10));
	public static final EntityType<CollectorGolem> COLLECTOR_GOLEM = entity("collector_golem",
			EntityType.Builder.of(CollectorGolem::new, MobCategory.MISC)
					.noLootTable()
					.sized(1.4f, 2.7f)
					.clientTrackingRange(10));

	private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));
	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));

	private Banking() {
	}

	private static ResourceKey<TradeSet> tradeSet(int level) {
		return ResourceKey.create(Registries.TRADE_SET, MyMod.id("banker/level_" + level));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item card(String name, int limit, Rarity rarity) {
		return item(name, properties -> new CreditCardItem(properties, limit), new Item.Properties().stacksTo(1).rarity(rarity));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static boolean isBanker(Entity entity) {
		return entity instanceof Villager villager && villager.getVillagerData().profession().is(BANKER_KEY);
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(DEBT_COLLECTOR, DebtCollector.createAttributes());
		FabricDefaultAttributeRegistry.register(COLLECTOR_GOLEM, CollectorGolem.createAttributes());

		CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB)
				.register(output -> output.insertAfter(Items.RECOVERY_COMPASS, CREDIT_CARD, GOLD_CREDIT_CARD, PLATINUM_CREDIT_CARD, DEBIT_CARD));
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_BLOCKS_TAB).register(output -> output.insertAfter(Items.LECTERN, ATM));

		ServerTickEvents.END_SERVER_TICK.register(DebtCollection::tick);
		ServerPlayerEvents.LEAVE.register(player -> {
			DebtCollection.onLeave(player);
			Vaults.onLeave(player);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(Vaults::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DebtCollection.onServerStopped();
			Vaults.onServerStopped();
		});

		// Sneak-use a banker with emeralds to pay down your debt, rather than opening its trades.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!player.isShiftKeyDown() || !isBanker(entity) || !(held.is(Items.EMERALD) || held.is(Items.EMERALD_BLOCK))) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				CreditCards.repay(serverPlayer, held);
				serverPlayer.sendSystemMessage(DebtCollection.statement(serverPlayer));
			}
			return InteractionResult.SUCCESS;
		});

		// Sneak-use an ATM with emeralds to pay down your debt from your hand. Vanilla never lets the block
		// see a sneaking click with an item in hand, so this catches it first.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!player.isShiftKeyDown() || player.isSpectator() || !level.getBlockState(hit.getBlockPos()).is(ATM)
					|| !(held.is(Items.EMERALD) || held.is(Items.EMERALD_BLOCK))) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				CreditCards.repay(serverPlayer, held);
			}
			return InteractionResult.SUCCESS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("debt")
				.executes(command -> {
					ServerPlayer player = command.getSource().getPlayerOrException();
					command.getSource().sendSystemMessage(DebtCollection.statement(player));
					return Debt.owed(player);
				})
				// Testing aids for operators.
				.then(Commands.literal("charge").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("emeralds", IntegerArgumentType.integer(1)).executes(command -> {
							ServerPlayer player = command.getSource().getPlayerOrException();
							DebtCollection.charge(player, IntegerArgumentType.getInteger(command, "emeralds"), new ItemStack(CREDIT_CARD));
							return 1;
						})))
				.then(Commands.literal("deadline").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(0)).executes(command -> {
							ServerPlayer player = command.getSource().getPlayerOrException();
							Debt debt = Debt.of(player);
							if (debt != null && !debt.hunting()) {
								player.setAttached(DEBT, debt.withTicksLeft(IntegerArgumentType.getInteger(command, "seconds") * 20));
							}
							command.getSource().sendSystemMessage(DebtCollection.statement(player));
							return 1;
						})))
				.then(Commands.literal("forgive").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(command -> {
					ServerPlayer player = command.getSource().getPlayerOrException();
					DebtCollection.settle(player);
					command.getSource().sendSystemMessage(DebtCollection.statement(player));
					return 1;
				}))));
	}
}
