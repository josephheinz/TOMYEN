package com.jheinz.tomyen.accountant;

import java.util.Optional;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.bank.Banking;

/**
 * The Accountant: a slime in a suit who works for the bank (see the bank package). He turns up by the ATMs bankers work
 * at, one to a village; lends emeralds against your account (see {@link Loans} and {@link LoanMenu}); audits players who
 * owe, and helps himself to the emeralds of the overdue; and splits into Junior Accountants when killed, which the bank
 * bills you for. His entity ({@link Accountant}), the Extension Form he carries, his spawn egg, sounds and loan screen.
 * <p>
 * His model, texture, item art, loot table, sounds and lang are made by tools/gen_accountant.py.
 */
public final class Accountants {
	/** How often each player's surroundings are checked for an ATM without an Accountant. */
	private static final int SPAWN_INTERVAL = 600;
	/** Chance, per check, that one turns up at an ATM that hasn't got one. */
	private static final float SPAWN_CHANCE = 0.2f;
	/** ATMs this close to a player are considered. */
	private static final int ATM_SEARCH = 64;
	/** One Accountant to an ATM this far round (about a village). */
	private static final double ONE_PER = 48.0;

	public static final EntityType<Accountant> ACCOUNTANT = entity("accountant",
			EntityType.Builder.of(Accountant::new, MobCategory.CREATURE)
					// Per size, like a slime: he's 2 (a block), his juniors 1.
					.sized(0.52f, 0.52f)
					.eyeHeight(0.325f)
					.clientTrackingRange(10));

	public static final Item EXTENSION_FORM = item("extension_form", ExtensionFormItem::new, new Item.Properties().stacksTo(16));
	public static final Item ACCOUNTANT_SPAWN_EGG = item("accountant_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ACCOUNTANT));

	public static final MenuType<LoanMenu> LOAN_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("loan_office"),
			new MenuType<>(LoanMenu::new, FeatureFlags.VANILLA_SET));

	/** Small, prim hops: vanilla's small slime, pitched up. */
	public static final SoundEvent HOP = sound("accountant_hop");
	public static final SoundEvent SQUISH = sound("accountant_squish");
	public static final SoundEvent HURT = sound("accountant_hurt");
	public static final SoundEvent DEATH = sound("accountant_death");
	/** Papers rustling: he opens his files (an audit, or the loan office). */
	public static final SoundEvent PAPERS = sound("accountant_papers");
	/** A pen scratching: he makes a note. */
	public static final SoundEvent SCRIBBLE = sound("accountant_scribble");

	private Accountants() {
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(ACCOUNTANT, Accountant.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.SLIME_SPAWN_EGG, ACCOUNTANT_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Banking.DEBIT_CARD, EXTENSION_FORM));
		ServerTickEvents.END_SERVER_TICK.register(Accountants::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SPAWN_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator() && player.getRandom().nextFloat() < SPAWN_CHANCE) {
				spawnNear(player);
			}
		}
	}

	/** Sends an Accountant to the nearest ATM round the player, if it hasn't got one. */
	private static void spawnNear(ServerPlayer player) {
		ServerLevel level = player.level();
		Optional<BlockPos> atm = level.getPoiManager().findClosest(type -> type.is(Banking.ATM_POI), player.blockPosition(), ATM_SEARCH,
				PoiManager.Occupancy.ANY);
		if (atm.isEmpty() || !level.getEntitiesOfClass(Accountant.class, new AABB(atm.get()).inflate(ONE_PER), a -> !a.isTiny()).isEmpty()) {
			return;
		}
		BlockPos spot = findSpot(level, atm.get(), level.getRandom());
		Accountant accountant = spot == null ? null : ACCOUNTANT.create(level, EntitySpawnReason.EVENT);
		if (accountant == null) {
			return;
		}
		accountant.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
		accountant.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
		accountant.setHome(atm.get());
		level.addFreshEntity(accountant);
	}

	/** Somewhere to stand a few blocks from the ATM: solid ground with room for him, or null. */
	private static @Nullable BlockPos findSpot(ServerLevel level, BlockPos atm, RandomSource random) {
		for (int attempt = 0; attempt < 16; attempt++) {
			BlockPos column = atm.offset(random.nextIntBetweenInclusive(-6, 6), 0, random.nextIntBetweenInclusive(-6, 6));
			for (int dy = 3; dy >= -3; dy--) {
				BlockPos pos = column.above(dy);
				BlockPos below = pos.below();
				if (level.hasChunkAt(pos) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
						&& level.getFluidState(pos).isEmpty()
						&& level.noCollision(ACCOUNTANT.getDimensions().scale(Accountant.SIZE).makeBoundingBox(pos.getX() + 0.5, pos.getY(),
								pos.getZ() + 0.5))) {
					return pos;
				}
			}
		}
		return null;
	}
}
