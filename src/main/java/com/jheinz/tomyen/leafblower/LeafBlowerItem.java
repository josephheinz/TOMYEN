package com.jheinz.tomyen.leafblower;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.GunRecoil;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.fishing.fish.Fishes;
import com.jheinz.tomyen.industry.Industry;
import com.jheinz.tomyen.survival.SatchelMenu;
import com.jheinz.tomyen.survival.Survival;

/**
 * A two-stroke leaf blower, and (with a Vacuum Kit) the Blower-Vac. Holding use runs it: every tick it blows a cone of
 * wind ({@link Wind#blow}), or a Blower-Vac switched to suck pulls drops, orbs and small things in ({@link Wind#suck}),
 * burning a unit of fuel. The tank holds {@link #TANK} units (half a minute flat out) and is kept as the stack's loaded
 * magazine so the ammo HUD and full-auto handling read it like a flamethrower's; its gauge is the item bar.
 *
 * <p>Fuel: punching tops it up from the inventory, a Kerosene Canister filling it (the canister comes back empty) or a
 * bottle of Fish Oil a quarter of it (the bottle comes back), whichever fits; right-clicking either onto it in a container
 * does the same. The fire-mode key switches a Blower-Vac between blow and suck, and sneak-punching opens its
 * {@link #BAG_SIZE}-slot bag (a Scavenger's Satchel's menu), where what it sucks up goes first.
 */
public class LeafBlowerItem extends GunItem {
	public static final int TANK = 600;
	public static final int KEROSENE_UNITS = 600;
	public static final int FISH_OIL_UNITS = 150;
	/** Ticks after running that it still counts as running, bridging the gap between use packets. */
	public static final int RUNNING_GRACE = 3;
	public static final int BAG_SIZE = SatchelMenu.SIZE;
	private static final int FUEL_COLOR = 0xF0A830;
	private static final int LOW_FUEL_COLOR = 0xE0402A;

	private final boolean vacuum;

	public LeafBlowerItem(Properties properties, GunStats stats, boolean vacuum) {
		super(properties, stats, List.of(), List.of());
		this.vacuum = vacuum;
	}

	public boolean vacuum() {
		return vacuum;
	}

	@Override
	public int capacity(ItemStack gun) {
		return TANK;
	}

	public static int fuel(ItemStack blower) {
		MagazineContents tank = blower.get(ModComponents.LOADED_MAGAZINE);
		return tank == null ? 0 : tank.rounds();
	}

	/** Whether this blower ran within the last {@link #RUNNING_GRACE} ticks of {@code gameTime}. */
	public static boolean running(ItemStack blower, long gameTime) {
		Long at = blower.get(LeafBlowers.RUNNING_AT);
		return at != null && gameTime - at <= RUNNING_GRACE && gameTime >= at;
	}

	public static boolean sucking(ItemStack blower) {
		return blower.getItem() instanceof LeafBlowerItem item && item.vacuum && blower.has(LeafBlowers.SUCK);
	}

	/** Where the air leaves (or goes in): the nozzle's end, ahead of the eye and below and right of it, as the model is held. */
	public static Vec3 nozzle(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 up = right.cross(look);
		double side = player.getMainArm() == HumanoidArm.LEFT ? -1.0 : 1.0;
		return eye.add(look.scale(1.25)).add(right.scale(0.3 * side)).add(up.scale(-0.25));
	}

	/** The way the wind goes: from the nozzle to where the player looks, a few blocks out. */
	public static Vec3 aim(Player player, Vec3 nozzle, float partialTick) {
		Vec3 target = player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(6.0));
		return target.subtract(nozzle).normalize();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack blower = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		boolean free = player.hasInfiniteMaterials();
		long now = level.getGameTime();
		if (!free && fuel(blower) <= 0) {
			if (level instanceof ServerLevel && now % 8 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 0.8f);
			}
			return InteractionResult.FAIL;
		}
		Vec3 nozzle = nozzle(player, 1.0f);
		if (level.getFluidState(BlockPos.containing(nozzle)).is(FluidTags.WATER)) {
			// Underwater it only gurgles.
			if (level instanceof ServerLevel serverLevel && now % 5 == 0) {
				serverLevel.sendParticles(ParticleTypes.BUBBLE, nozzle.x, nozzle.y, nozzle.z, 8, 0.15, 0.15, 0.15, 0.1);
			}
		}
		blower.set(LeafBlowers.RUNNING_AT, now);
		if (level.isClientSide()) {
			GunRecoil.queue(player, 1, stats(blower, player));
			return InteractionResult.CONSUME;
		}
		ServerLevel serverLevel = (ServerLevel) level;
		Vec3 direction = aim(player, nozzle, 1.0f);
		if (sucking(blower)) {
			Wind.suck(serverLevel, (ServerPlayer) player, blower, nozzle, direction);
		} else {
			Wind.blow(serverLevel, player, nozzle, direction);
		}
		if (now % 20 == 0) {
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		if (!free) {
			blower.set(ModComponents.LOADED_MAGAZINE, blower.get(ModComponents.LOADED_MAGAZINE).withRounds(fuel(blower) - 1));
		}
		return InteractionResult.CONSUME;
	}

	// ----------------------------------------------------------------------------------------------------------- fuel

	/** How many units of fuel one of this item pours in, or 0 if it isn't fuel. */
	public static int fuelUnits(ItemStack stack) {
		if (stack.is(Industry.KEROSENE_CANISTER)) {
			return KEROSENE_UNITS;
		} else if (stack.is(Fishes.FISH_OIL)) {
			return FISH_OIL_UNITS;
		}
		return 0;
	}

	/** Pours one of {@code fuel} in, if there's room for some of it. Returns the empty container it leaves, or null if nothing went in. */
	private static @Nullable ItemStack pour(ItemStack blower, ItemStack fuel) {
		int units = fuelUnits(fuel);
		int have = fuel(blower);
		if (units == 0 || have >= TANK) {
			return null;
		}
		blower.set(ModComponents.LOADED_MAGAZINE, new MagazineContents(fuel.typeHolder(), Math.min(TANK, have + units)));
		return fuel.is(Industry.KEROSENE_CANISTER) ? new ItemStack(Industry.EMPTY_CANISTER) : new ItemStack(Items.GLASS_BOTTLE);
	}

	/**
	 * Punching tops up the tank: with the most fuel that fits without waste (a canister into a dry tank, a bottle of fish oil
	 * into a part-full one), else the smallest fuel there is. Sneak-punching a Blower-Vac opens its bag instead.
	 */
	@Override
	public void reload(ServerPlayer player, ItemStack blower) {
		if (vacuum && player.isShiftKeyDown()) {
			openBag(player, blower);
			return;
		}
		int room = TANK - fuel(blower);
		if (room <= 0) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.leaf_blower.tank_full"));
			return;
		}
		int best = -1;
		int bestUnits = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			int units = fuelUnits(player.getInventory().getItem(i));
			if (units == 0) {
				continue;
			}
			boolean fits = units <= room;
			boolean bestFits = bestUnits > 0 && bestUnits <= room;
			if (best < 0 || (fits && (!bestFits || units > bestUnits)) || (!fits && !bestFits && units < bestUnits)) {
				best = i;
				bestUnits = units;
			}
		}
		if (best < 0) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.leaf_blower.no_fuel").withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			return;
		}
		ItemStack fuel = player.getInventory().getItem(best).split(1);
		ItemStack empty = pour(blower, fuel);
		if (empty != null) {
			player.getInventory().placeItemBackInInventory(empty, Prediction.SERVER_ONLY);
		}
		player.getCooldowns().addCooldown(blower, Math.round(stats().reloadTicks() * reloadFactor(player)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.7f, 1.3f);
	}

	/** Right-clicking fuel onto it in a container pours it in. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack blower, ItemStack carried, Slot slot, ClickAction action, Player player,
			SlotAccess carriedAccess) {
		if (action == ClickAction.SECONDARY && fuelUnits(carried) > 0 && slot.allowModification(player)) {
			ItemStack empty = pour(blower, carried);
			if (empty == null) {
				player.playSound(SoundEvents.DISPENSER_FAIL, 0.5f, 1.6f);
				return true;
			}
			carried.shrink(1);
			if (carried.isEmpty()) {
				carriedAccess.set(empty);
			} else {
				player.getInventory().placeItemBackInInventory(empty, Prediction.PREDICTED);
			}
			player.playSound(SoundEvents.BUCKET_FILL, 0.7f, 1.3f);
			return true;
		}
		return super.overrideOtherStackedOnMe(blower, carried, slot, action, player, carriedAccess);
	}

	/** Its fuel gauge. */
	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0f * fuel(stack) / TANK);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return fuel(stack) * 4 < TANK ? LOW_FUEL_COLOR : FUEL_COLOR;
	}

	// -------------------------------------------------------------------------------------------------- modes and bag

	/** The fire-mode key: a Blower-Vac switches between blow and suck. */
	@Override
	public void cycleFireMode(ServerPlayer player, ItemStack blower) {
		if (!vacuum) {
			return;
		}
		if (blower.has(LeafBlowers.SUCK)) {
			blower.remove(LeafBlowers.SUCK);
		} else {
			blower.set(LeafBlowers.SUCK, Unit.INSTANCE);
		}
		player.sendOverlayMessage(Component.translatable("item.tomyen.leaf_vacuum.mode", modeName(blower)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6f,
				blower.has(LeafBlowers.SUCK) ? 0.9f : 1.2f);
	}

	private static Component modeName(ItemStack blower) {
		return Component.translatable(blower.has(LeafBlowers.SUCK) ? "item.tomyen.leaf_vacuum.mode.suck" : "item.tomyen.leaf_vacuum.mode.blow");
	}

	private static void openBag(ServerPlayer player, ItemStack blower) {
		player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new SatchelMenu(id, inventory, blower, InteractionHand.MAIN_HAND),
				blower.getHoverName()));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), Survival.SATCHEL_OPEN, SoundSource.PLAYERS, 0.8f, 1.0f);
	}

	/** How many of the bag's slots hold something. */
	public static int bagUsed(ItemStack blower) {
		return (int) blower.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count();
	}

	/**
	 * Puts as much of {@code stack} as fits into the bag (topping up matching stacks, then empty slots) and returns what
	 * didn't fit. Things that can't go in a container item go nowhere.
	 */
	public static ItemStack bag(ItemStack blower, ItemStack stack) {
		if (!SatchelMenu.fits(stack) || stack.getItem() instanceof LeafBlowerItem) {
			return stack;
		}
		NonNullList<ItemStack> slots = NonNullList.withSize(BAG_SIZE, ItemStack.EMPTY);
		blower.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(slots);
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int i = 0; i < BAG_SIZE && !stack.isEmpty(); i++) {
				ItemStack slot = slots.get(i);
				if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
					int move = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
					slot.grow(move);
					stack.shrink(move);
				} else if (pass == 1 && slot.isEmpty()) {
					slots.set(i, stack.copy());
					stack.setCount(0);
				}
			}
		}
		blower.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
		return stack;
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	/** Burnt or blown up, a Blower-Vac spills its bag. */
	@Override
	public void onDestroyed(ItemEntity itemEntity) {
		ItemContainerContents contents = itemEntity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		if (contents != null) {
			ItemUtils.onContainerDestroyed(itemEntity, contents.nonEmptyItemCopyStream());
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.leaf_blower.fuel", Math.round(100.0f * fuel(stack) / TANK))
				.withStyle(ChatFormatting.GRAY));
		if (vacuum) {
			tooltip.accept(Component.translatable("item.tomyen.leaf_vacuum.mode", modeName(stack)).withStyle(ChatFormatting.GRAY));
			tooltip.accept(Component.translatable("item.tomyen.leaf_vacuum.bag", bagUsed(stack), BAG_SIZE).withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.tomyen.leaf_blower.hint").withStyle(ChatFormatting.DARK_GRAY));
		if (vacuum) {
			tooltip.accept(Component.translatable("item.tomyen.leaf_vacuum.hint", Component.keybind("key.tomyen.fire_mode"))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** For the HUD: a 0-1 fill. */
	public static float fill(ItemStack blower) {
		return Mth.clamp(fuel(blower) / (float) TANK, 0.0f, 1.0f);
	}
}
