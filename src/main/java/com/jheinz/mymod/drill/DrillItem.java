package com.jheinz.mymod.drill;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.uranium.Uranium;

/**
 * A drill: a pickaxe for stone and ores, and the only tool that harvests the rock tagged {@link #NEEDS_DRILL} (any
 * drill) and {@link #NEEDS_POWERED_DRILL} (a running motor drill). Every vanilla tool tier counts both tags incorrect,
 * so a pickaxe chips at that rock slowly and gets nothing from it.
 * <p>
 * The Hand Drill is cranked, so it always runs. A motor drill burns {@link Engine fuel} from its tank
 * ({@link Drills#DRILL_FUEL}), one unit a block dug; use it to pour in a canister from the inventory (or right-click
 * one onto it in a container). Running dry it still digs, but only as well as a stone pickaxe and at a wooden one's
 * pace. A drill that {@link #bores} can dig 3x3 ({@link DrillArea}): sneak-use switches that on and off, and sneaking
 * while digging takes a single block. Drills are held two-handed; their swing is the bit's spin, not an arm swing.
 */
public class DrillItem extends Item {
	/** Rock only drills harvest; the Hand Drill is enough. */
	public static final TagKey<Block> NEEDS_DRILL = TagKey.create(Registries.BLOCK, MyMod.id("needs_drill"));
	/** Rock only a running motor drill harvests. */
	public static final TagKey<Block> NEEDS_POWERED_DRILL = TagKey.create(Registries.BLOCK, MyMod.id("needs_powered_drill"));
	/** What the Hand Drill can't harvest: diamond-tier blocks and powered-drill rock. */
	public static final TagKey<Block> INCORRECT_FOR_HAND_DRILL = TagKey.create(Registries.BLOCK, MyMod.id("incorrect_for_hand_drill"));
	/** What a motor drill can't harvest (nothing, out of the box). */
	public static final TagKey<Block> INCORRECT_FOR_POWERED_DRILL = TagKey.create(Registries.BLOCK, MyMod.id("incorrect_for_powered_drill"));

	/** A motor drill with an empty tank digs what a pickaxe would at a wooden pickaxe's pace... */
	public static final float UNPOWERED_SPEED = 2.0f;
	/** ...and harvests only what a stone one could. */
	private static final TagKey<Block> UNPOWERED_INCORRECT = BlockTags.INCORRECT_FOR_STONE_TOOL;
	private static final SwingAnimation NO_SWING = new SwingAnimation(SwingAnimationType.NONE, 6);
	private static final int FUEL_COLOR = 0xF0A830;
	private static final int LOW_FUEL_COLOR = 0xE0402A;

	/** One fuel item and the units it pours in; a canister comes back empty. */
	public record Fuel(Supplier<Item> item, int units, boolean canister) {
	}

	/** What turns the bit, and the tank it burns from. Fuels are listed largest first. */
	public enum Engine {
		HAND_CRANK(0, List.of()),
		KEROSENE(1000, List.of(new Fuel(() -> Industry.KEROSENE_CANISTER, 500, true))),
		/** Kerosene, or a uranium fuel rod: eight canisters' worth in one. */
		HEAVY(4000, List.of(new Fuel(() -> Uranium.URANIUM_FUEL_ROD, 4000, false), new Fuel(() -> Industry.KEROSENE_CANISTER, 500, true)));

		private final int tank;
		private final List<Fuel> fuels;

		Engine(int tank, List<Fuel> fuels) {
			this.tank = tank;
			this.fuels = fuels;
		}

		public int tank() {
			return tank;
		}

		public @Nullable Fuel fuelFor(ItemStack stack) {
			for (Fuel fuel : fuels) {
				if (stack.is(fuel.item().get())) {
					return fuel;
				}
			}
			return null;
		}
	}

	private final Engine engine;
	private final boolean bores;
	private final boolean wideByDefault;

	/**
	 * @param bores         whether it can dig 3x3
	 * @param wideByDefault whether a new one starts out digging 3x3
	 */
	public DrillItem(Properties properties, Engine engine, boolean bores, boolean wideByDefault) {
		super(properties);
		this.engine = engine;
		this.bores = bores;
		this.wideByDefault = wideByDefault;
	}

	/** A drill's properties: a pickaxe of this material with no arm swing. */
	public static Item.Properties properties(ToolMaterial material, float attackSpeed) {
		return new Item.Properties().pickaxe(material, 1.0f, attackSpeed).component(DataComponents.ATTACK_ANIMATION, NO_SWING);
	}

	public Engine engine() {
		return engine;
	}

	public boolean hasTank() {
		return engine.tank > 0;
	}

	public boolean bores() {
		return bores;
	}

	public static int fuel(ItemStack stack) {
		return Mth.clamp(stack.getOrDefault(Drills.DRILL_FUEL, 0), 0, stack.getItem() instanceof DrillItem drill ? drill.engine.tank : 0);
	}

	public static void setFuel(ItemStack stack, int fuel) {
		if (fuel <= 0) {
			stack.remove(Drills.DRILL_FUEL);
		} else {
			stack.set(Drills.DRILL_FUEL, fuel);
		}
	}

	/** Whether the bit turns: always for the Hand Drill, while there's fuel for a motor drill. */
	public boolean powered(ItemStack stack) {
		return !hasTank() || fuel(stack) > 0;
	}

	/** Whether it runs in this player's hands: creative players need no fuel. */
	public boolean running(ItemStack stack, Player player) {
		return powered(stack) || player.hasInfiniteMaterials();
	}

	/** Whether 3x3 digging is switched on (whatever the fuel). */
	public boolean wide(ItemStack stack) {
		return bores && stack.getOrDefault(Drills.DRILL_WIDE, wideByDefault);
	}

	/** Whether the player digs 3x3 with this drill right now: switched on, running, and not sneaking. */
	public boolean digsWide(ItemStack stack, Player player) {
		return wide(stack) && running(stack, player) && !player.isShiftKeyDown();
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		if (powered(stack)) {
			return super.getDestroySpeed(stack, state);
		}
		return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? UNPOWERED_SPEED : 1.0f;
	}

	@Override
	public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
		if (powered(stack)) {
			return super.isCorrectToolForDrops(stack, state);
		}
		return state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !state.is(UNPOWERED_INCORRECT);
	}

	/** Wears the drill as any pickaxe and burns a unit of fuel for each block of stone or ore it bores. */
	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		boolean mined = super.mineBlock(stack, level, state, pos, owner);
		if (!level.isClientSide() && !stack.isEmpty() && hasTank() && state.getDestroySpeed(level, pos) != 0.0f
				&& state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !(owner instanceof Player player && player.hasInfiniteMaterials())) {
			setFuel(stack, fuel(stack) - 1);
		}
		return mined;
	}

	/** Use pours in fuel from the inventory; sneak-use switches 3x3 digging. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND || !hasTank() && !bores) {
			return InteractionResult.PASS;
		}
		if (player.isSecondaryUseActive() && bores) {
			if (player instanceof ServerPlayer serverPlayer) {
				boolean wide = !wide(stack);
				stack.set(Drills.DRILL_WIDE, wide);
				serverPlayer.sendOverlayMessage(Component.translatable(wide ? "item.mymod.drill.switched_wide" : "item.mymod.drill.switched_single"));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.5f,
						wide ? 1.2f : 0.9f);
			}
			return InteractionResult.CONSUME;
		}
		if (!hasTank()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			refuel(serverPlayer, stack);
		}
		return InteractionResult.CONSUME;
	}

	/** Pours the largest fuel from the inventory that fits in the tank whole. */
	private void refuel(ServerPlayer player, ItemStack drill) {
		int room = engine.tank - fuel(drill);
		boolean anyFuel = false;
		for (Fuel fuel : engine.fuels) {
			int slot = find(player, fuel.item().get());
			if (slot < 0) {
				continue;
			}
			anyFuel = true;
			if (fuel.units() > room) {
				continue;
			}
			player.getInventory().getItem(slot).shrink(1);
			setFuel(drill, fuel(drill) + fuel.units());
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.PLAYERS, 0.6f,
					1.3f);
			if (fuel.canister()) {
				player.getInventory().placeItemBackInInventory(new ItemStack(Industry.EMPTY_CANISTER), Prediction.SERVER_ONLY);
			}
			player.sendOverlayMessage(Component.translatable("item.mymod.drill.refuelled", Math.round(100.0f * fuel(drill) / engine.tank)));
			return;
		}
		Fuel smallest = engine.fuels.getLast();
		player.sendOverlayMessage(anyFuel
				? Component.translatable("item.mymod.drill.tank_full", Component.translatable(smallest.item().get().getDescriptionId()))
				: Component.translatable("item.mymod.drill.no_fuel").withStyle(ChatFormatting.RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	private static int find(Player player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	/** Right-clicking a fuel onto the drill in a container pours it in, if it fits whole; a canister comes back empty. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player,
			SlotAccess carriedItem) {
		Fuel fuel = clickAction == ClickAction.SECONDARY ? engine.fuelFor(other) : null;
		if (fuel == null || !slot.allowModification(player)) {
			return false;
		}
		if (fuel.units() > engine.tank - fuel(self)) {
			player.playSound(SoundEvents.DISPENSER_FAIL, 0.5f, 1.6f);
			return true;
		}
		// Runs on both sides, like a bundle's insert; the server's copy wins.
		other.shrink(1);
		setFuel(self, fuel(self) + fuel.units());
		player.playSound(SoundEvents.BUCKET_EMPTY_LAVA, 0.6f, 1.3f);
		if (fuel.canister()) {
			if (other.isEmpty()) {
				carriedItem.set(new ItemStack(Industry.EMPTY_CANISTER));
			} else if (!player.level().isClientSide()) {
				player.getInventory().placeItemBackInInventory(new ItemStack(Industry.EMPTY_CANISTER), Prediction.SERVER_ONLY);
			}
		}
		return true;
	}

	/** The fuel gauge, drawn just above the durability bar (GuiGraphicsDrillFuelBarMixin): how full, out of 13. */
	public int fuelBarWidth(ItemStack stack) {
		return Math.round(13.0f * fuel(stack) / engine.tank);
	}

	public int fuelBarColor(ItemStack stack) {
		return fuel(stack) * 100 < engine.tank * 15 ? LOW_FUEL_COLOR : FUEL_COLOR;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		if (!hasTank()) {
			tooltip.accept(Component.translatable("item.mymod.drill.hand_hint").withStyle(ChatFormatting.GRAY));
			return;
		}
		int fuel = fuel(stack);
		tooltip.accept(fuel > 0
				? Component.translatable("item.mymod.drill.fuel", Math.round(100.0f * fuel / engine.tank), fuel, engine.tank)
						.withStyle(ChatFormatting.GOLD)
				: Component.translatable("item.mymod.drill.empty").withStyle(ChatFormatting.RED));
		if (bores) {
			tooltip.accept(Component.translatable(wide(stack) ? "item.mymod.drill.mode_wide" : "item.mymod.drill.mode_single")
					.withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.mymod.drill.powered_hint").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.mymod.drill.refuel", fuelNames()).withStyle(ChatFormatting.DARK_GRAY));
		if (bores) {
			tooltip.accept(Component.translatable("item.mymod.drill.toggle_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private Component fuelNames() {
		Component names = null;
		for (Fuel fuel : engine.fuels.reversed()) {
			Component name = Component.translatable(fuel.item().get().getDescriptionId());
			names = names == null ? name : Component.translatable("item.mymod.drill.or", names, name);
		}
		return names;
	}
}
