package com.jheinz.tomyen.bathtub;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.fishing.fish.Fishes;
import com.jheinz.tomyen.industry.Industry;

/**
 * A Bathtub Boat, packed up with whatever was left in its tank ({@link Bathtubs#BOAT_FUEL}). Placed like a boat, on
 * water or land. Its fuel is the item bar; right-clicking a Kerosene Canister (the canister comes back empty) or a
 * bottle of Fish Oil (the bottle comes back) onto it in a container pours it in, the same fuels and the same ratio as
 * the leaf blower's.
 */
public class BathtubBoatItem extends Item {
	private static final int FUEL_COLOR = 0xF0A830;
	private static final int LOW_FUEL_COLOR = 0xE0402A;

	public BathtubBoatItem(Properties properties) {
		super(properties);
	}

	public static int fuel(ItemStack stack) {
		return Mth.clamp(stack.getOrDefault(Bathtubs.BOAT_FUEL, 0), 0, BathtubBoat.TANK);
	}

	public static void setFuel(ItemStack stack, int fuel) {
		if (fuel <= 0) {
			stack.remove(Bathtubs.BOAT_FUEL);
		} else {
			stack.set(Bathtubs.BOAT_FUEL, Math.min(fuel, BathtubBoat.TANK));
		}
	}

	/** Units of fuel one of this item pours in, or 0 if it isn't fuel. */
	public static int fuelUnits(ItemStack stack) {
		if (stack.is(Industry.KEROSENE_CANISTER)) {
			return BathtubBoat.KEROSENE_UNITS;
		} else if (stack.is(Fishes.FISH_OIL)) {
			return BathtubBoat.FISH_OIL_UNITS;
		}
		return 0;
	}

	/** What's left of a fuel item once it's poured. */
	public static ItemStack emptied(ItemStack fuel) {
		return fuel.is(Industry.KEROSENE_CANISTER) ? new ItemStack(Industry.EMPTY_CANISTER) : new ItemStack(Items.GLASS_BOTTLE);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		Vec3 view = player.getViewVector(1.0f);
		List<Entity> near = level.getEntities(player, player.getBoundingBox().expandTowards(view.scale(5.0)).inflate(1.0), EntitySelector.CAN_BE_PICKED);
		Vec3 eye = player.getEyePosition();
		for (Entity entity : near) {
			AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
			if (box.contains(eye)) {
				return InteractionResult.PASS;
			}
		}
		BathtubBoat boat = Bathtubs.BATHTUB_BOAT_ENTITY.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (boat == null) {
			return InteractionResult.FAIL;
		}
		Vec3 at = hit.getLocation();
		boat.setInitialPos(at.x, at.y, at.z);
		boat.setYRot(player.getYRot());
		if (!level.noCollision(boat, boat.getBoundingBox())) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			EntityType.<BathtubBoat>createDefaultStackConfig(serverLevel, stack, player).apply(boat);
			boat.setFuel(fuel(stack));
			level.addFreshEntity(boat);
			level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
			stack.consume(1, player);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}

	/** Pours one of {@code fuel} in if there's room for any of it; returns the empty it leaves, or null if none went in. */
	private static @Nullable ItemStack pour(ItemStack boat, ItemStack fuel) {
		int units = fuelUnits(fuel);
		int have = fuel(boat);
		if (units == 0 || have >= BathtubBoat.TANK) {
			return null;
		}
		setFuel(boat, have + units);
		return emptied(fuel);
	}

	/** Right-clicking fuel onto it in a container pours it in. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack boat, ItemStack carried, Slot slot, ClickAction action, Player player,
			SlotAccess carriedAccess) {
		if (action == ClickAction.SECONDARY && fuelUnits(carried) > 0 && slot.allowModification(player)) {
			ItemStack empty = pour(boat, carried);
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
		return super.overrideOtherStackedOnMe(boat, carried, slot, action, player, carriedAccess);
	}

	/** Its fuel gauge. */
	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0f * fuel(stack) / BathtubBoat.TANK);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return fuel(stack) * 4 < BathtubBoat.TANK ? LOW_FUEL_COLOR : FUEL_COLOR;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.bathtub_boat.fuel", Math.round(100.0f * fuel(stack) / BathtubBoat.TANK))
				.withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.bathtub_boat.hint_place").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.tomyen.bathtub_boat.hint_fuel").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.tomyen.bathtub_boat.hint_honk", Component.keybind("key.tomyen.honk"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
