package com.jheinz.mymod.scrapbench;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Scrap Bench's two modes over one inventory. Assemble: four input slots and a result, matched against
 * {@link BenchRecipes} like the Weapon Station does. Salvage: one input slot, a "break down" button and six output
 * slots. Only the current mode's slots are shown (and switching mode hands the hidden ones back).
 * <p>
 * Buttons (sent with {@code handleInventoryButtonClick}): {@link #BUTTON_ASSEMBLE} and {@link #BUTTON_SALVAGE} switch
 * mode, {@link #BUTTON_BREAK_ONE}/{@link #BUTTON_BREAK_ALL} salvage, and {@link #BUTTON_LIST} + 2 × index (+1 with
 * shift) picks an entry from the side list: it moves that recipe's ingredients (or that junk) out of the inventory into
 * the input slots.
 */
public class ScrapBenchMenu extends AbstractContainerMenu {
	public static final int MODE_ASSEMBLE = 0;
	public static final int MODE_SALVAGE = 1;

	public static final int BUTTON_ASSEMBLE = 0;
	public static final int BUTTON_SALVAGE = 1;
	public static final int BUTTON_BREAK_ONE = 2;
	public static final int BUTTON_BREAK_ALL = 3;
	public static final int BUTTON_LIST = 100;

	public static final int INPUT_COUNT = 4;
	public static final int OUTPUT_COUNT = 6;

	// Slot indices.
	public static final int RESULT_SLOT = INPUT_COUNT;
	public static final int SALVAGE_SLOT = RESULT_SLOT + 1;
	public static final int OUTPUT_START = SALVAGE_SLOT + 1;
	private static final int INVENTORY_START = OUTPUT_START + OUTPUT_COUNT;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int INVENTORY_END = HOTBAR_START + 9;

	// Layout, shared with ScrapBenchScreen and tools/gen_scrap_bench.py (positions are where items are drawn).
	public static final int WIDTH = 176;
	public static final int HEIGHT = 178;
	public static final int INPUT_X = 96;
	public static final int INPUT_Y = 22;
	public static final int RESULT_X = 152;
	public static final int RESULT_Y = 31;
	public static final int SALVAGE_X = 97;
	public static final int SALVAGE_Y = 22;
	public static final int BREAK_X = 96;
	public static final int BREAK_Y = 42;
	public static final int OUTPUT_X = 134;
	public static final int OUTPUT_Y = 22;
	public static final int INVENTORY_Y = 96;

	private final ContainerLevelAccess access;
	private final Player player;
	private final DataSlot mode = DataSlot.standalone();
	private final ResultContainer result = new ResultContainer();
	private final Container inputs = new SimpleContainer(INPUT_COUNT) {
		@Override
		public void setChanged() {
			super.setChanged();
			slotsChanged(this);
		}
	};
	private final Container salvageInput = new SimpleContainer(1);
	private final Container outputs = new SimpleContainer(OUTPUT_COUNT);

	public ScrapBenchMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public ScrapBenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(ScrapBench.MENU, containerId);
		this.access = access;
		this.player = inventory.player;
		for (int i = 0; i < INPUT_COUNT; i++) {
			addSlot(new ModeSlot(inputs, i, INPUT_X + i % 2 * 18, INPUT_Y + i / 2 * 18, MODE_ASSEMBLE));
		}
		addSlot(new ResultSlot(result, RESULT_X, RESULT_Y));
		addSlot(new ModeSlot(salvageInput, 0, SALVAGE_X, SALVAGE_Y, MODE_SALVAGE) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return SalvageRecipes.find(stack) != null;
			}
		});
		for (int i = 0; i < OUTPUT_COUNT; i++) {
			addSlot(new ModeSlot(outputs, i, OUTPUT_X + i % 2 * 18, OUTPUT_Y + i / 2 * 18, MODE_SALVAGE) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}
			});
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		addDataSlot(mode);
		mode.set(MODE_ASSEMBLE);
	}

	public int mode() {
		return mode.get();
	}

	/** The assembly inputs (the screen reads them to grey out a locked match). */
	public Container inputs() {
		return inputs;
	}

	public ItemStack salvageInput() {
		return salvageInput.getItem(0);
	}

	public boolean outputsEmpty() {
		return outputs.isEmpty();
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == inputs) {
			// Only the server knows a real level/pos; the client gets the result slot synced.
			access.execute((level, pos) -> {
				BenchRecipe recipe = BenchRecipes.find(inputs, player);
				result.setItem(0, recipe == null ? ItemStack.EMPTY : recipe.result());
			});
		}
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == BUTTON_ASSEMBLE || id == BUTTON_SALVAGE) {
			int wanted = id == BUTTON_ASSEMBLE ? MODE_ASSEMBLE : MODE_SALVAGE;
			if (mode.get() != wanted) {
				// Hand back whatever is in the slots that are about to be hidden.
				if (wanted == MODE_ASSEMBLE) {
					clearContainer(player, salvageInput);
					clearContainer(player, outputs);
				} else {
					clearContainer(player, inputs);
				}
				mode.set(wanted);
			}
			return true;
		}
		if (id == BUTTON_BREAK_ONE || id == BUTTON_BREAK_ALL) {
			return mode.get() == MODE_SALVAGE && breakDown(id == BUTTON_BREAK_ALL);
		}
		if (id >= BUTTON_LIST) {
			int index = (id - BUTTON_LIST) / 2;
			boolean all = (id - BUTTON_LIST) % 2 == 1;
			return mode.get() == MODE_ASSEMBLE ? fill(index, all) : fillSalvage(index, all);
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------- salvage

	/** Breaks down one of the input (or the whole stack), as long as the outputs have room. */
	private boolean breakDown(boolean all) {
		boolean any = false;
		do {
			ItemStack input = salvageInput.getItem(0);
			SalvageRecipe recipe = SalvageRecipes.find(input);
			if (recipe == null) {
				break;
			}
			List<ItemStack> yield = recipe.yield(input);
			if (!fits(yield)) {
				break;
			}
			for (ItemStack stack : yield) {
				insert(stack);
			}
			salvageInput.removeItem(0, 1);
			any = true;
		} while (all);
		if (any) {
			access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7f, 1.15f));
		}
		return any;
	}

	/** Whether all these stacks would fit in the output slots together. */
	private boolean fits(List<ItemStack> stacks) {
		SimpleContainer trial = new SimpleContainer(OUTPUT_COUNT);
		for (int i = 0; i < OUTPUT_COUNT; i++) {
			trial.setItem(i, outputs.getItem(i).copy());
		}
		for (ItemStack stack : stacks) {
			if (!trial.addItem(stack.copy()).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private void insert(ItemStack stack) {
		ItemStack left = ((SimpleContainer) outputs).addItem(stack);
		if (!left.isEmpty()) {
			player.getInventory().placeItemBackInInventory(left, Prediction.SERVER_ONLY);
		}
	}

	/** Moves a stack of the picked junk from the inventory into the salvage slot (with {@code all}, as much as fits). */
	private boolean fillSalvage(int index, boolean all) {
		if (index < 0 || index >= SalvageRecipes.ALL.size()) {
			return false;
		}
		Item wanted = SalvageRecipes.ALL.get(index).input();
		ItemStack current = salvageInput.getItem(0);
		if (!current.isEmpty() && !current.is(wanted)) {
			clearContainer(player, salvageInput);
		}
		boolean moved = false;
		for (int i = INVENTORY_START; i < INVENTORY_END; i++) {
			Slot slot = slots.get(i);
			ItemStack stack = slot.getItem();
			if (!stack.is(wanted)) {
				continue;
			}
			ItemStack in = salvageInput.getItem(0);
			if (in.isEmpty()) {
				salvageInput.setItem(0, slot.remove(stack.getCount()));
				moved = true;
			} else if (ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
				int take = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
				in.grow(take);
				slot.remove(take);
				moved = true;
			}
			if (!all && moved) {
				break;
			}
		}
		if (moved) {
			salvageInput.setChanged();
		}
		return moved;
	}

	// ------------------------------------------------------------------------------------------------ assembly

	/**
	 * Fills the input slots with one craft's worth of the picked recipe (or as many crafts as fit a stack), after
	 * handing back what was there. Does nothing if the recipe is locked or the inventory can't pay for one.
	 */
	private boolean fill(int index, boolean all) {
		if (index < 0 || index >= BenchRecipes.ALL.size()) {
			return false;
		}
		BenchRecipe recipe = BenchRecipes.ALL.get(index);
		if (!recipe.unlockedFor(player)) {
			return false;
		}
		clearContainer(player, inputs);
		// Pick, for each cost, the single item kind with the most on hand (a tag cost can be any of its members).
		Item[] picks = new Item[recipe.costs().size()];
		int times = Integer.MAX_VALUE;
		for (int c = 0; c < picks.length; c++) {
			BenchRecipe.Cost cost = recipe.costs().get(c);
			int best = 0;
			for (Item option : cost.options()) {
				int have = count(option);
				if (have > best) {
					best = have;
					picks[c] = option;
				}
			}
			if (picks[c] == null || best < cost.count()) {
				return true;
			}
			int max = all ? Math.min(best, new ItemStack(picks[c]).getMaxStackSize()) / cost.count() : 1;
			times = Math.min(times, max);
		}
		for (int c = 0; c < picks.length; c++) {
			int needed = recipe.costs().get(c).count() * times;
			ItemStack gathered = ItemStack.EMPTY;
			for (int i = INVENTORY_START; i < INVENTORY_END && needed > 0; i++) {
				Slot slot = slots.get(i);
				ItemStack stack = slot.getItem();
				if (!stack.is(picks[c]) || !gathered.isEmpty() && !ItemStack.isSameItemSameComponents(gathered, stack)) {
					continue;
				}
				ItemStack taken = slot.remove(Math.min(needed, stack.getCount()));
				needed -= taken.getCount();
				if (gathered.isEmpty()) {
					gathered = taken;
				} else {
					gathered.grow(taken.getCount());
				}
			}
			inputs.setItem(c, gathered);
		}
		return true;
	}

	/** How many of an item the player carries (stacks with extra components, like damaged tools, count too). */
	private int count(Item item) {
		int total = 0;
		for (int i = INVENTORY_START; i < INVENTORY_END; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// --------------------------------------------------------------------------------------------- housekeeping

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((level, pos) -> {
			clearContainer(player, inputs);
			clearContainer(player, salvageInput);
			clearContainer(player, outputs);
		});
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index == RESULT_SLOT) {
			if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, original);
		} else if (index < INVENTORY_START) {
			if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean moved = mode.get() == MODE_ASSEMBLE ? moveItemStackTo(stack, 0, INPUT_COUNT, false)
					: SalvageRecipes.find(stack) != null && moveItemStackTo(stack, SALVAGE_SLOT, SALVAGE_SLOT + 1, false);
			if (!moved) {
				boolean fromInventory = index < HOTBAR_START;
				if (!moveItemStackTo(stack, fromInventory ? HOTBAR_START : INVENTORY_START,
						fromInventory ? INVENTORY_END : HOTBAR_START, false)) {
					return ItemStack.EMPTY;
				}
			}
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != result && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, ScrapBench.SCRAP_BENCH);
	}

	/** A slot that's only there in one mode. */
	private class ModeSlot extends Slot {
		private final int shownIn;

		ModeSlot(Container container, int index, int x, int y, int shownIn) {
			super(container, index, x, y);
			this.shownIn = shownIn;
		}

		@Override
		public boolean isActive() {
			return mode.get() == shownIn;
		}
	}

	/** Output only; taking the result pays for it out of the input slots. */
	private class ResultSlot extends ModeSlot {
		ResultSlot(Container container, int x, int y) {
			super(container, 0, x, y, MODE_ASSEMBLE);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public void onTake(Player player, ItemStack stack) {
			@Nullable BenchRecipe recipe = BenchRecipes.find(inputs, player);
			if (recipe != null) {
				recipe.consume(inputs);
			}
			access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.2f));
			super.onTake(player, stack);
		}
	}
}
