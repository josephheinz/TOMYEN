package com.jheinz.tomyen.client.scrapbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.station.StationRecipe;
import com.jheinz.tomyen.station.StationRecipes;

/**
 * The Weapon Station's blueprint panel, beside its window: every blueprint recipe the station knows of, greyed out with a
 * lock until learned, with its costs in the tooltip. Also greys out the result when the inputs make a locked recipe.
 */
public final class BlueprintPanel {
	private static final int COLUMNS = 3;
	private static final int GAP = 2;
	private static final int HEADER = 16;
	private static final int PADDING = 6;

	private BlueprintPanel() {
	}

	private static List<StationRecipe> recipes() {
		return StationRecipes.ALL.stream().filter(recipe -> recipe.blueprint() != null).toList();
	}

	private static int x0(int left, int imageWidth) {
		return left + imageWidth + GAP;
	}

	private static int width() {
		return PADDING * 2 + COLUMNS * 18;
	}

	private static int height() {
		return HEADER + (recipes().size() + COLUMNS - 1) / COLUMNS * 18 + PADDING;
	}

	private static int cellX(int left, int imageWidth, int i) {
		return x0(left, imageWidth) + PADDING + 1 + i % COLUMNS * 18;
	}

	private static int cellY(int top, int i) {
		return top + HEADER + 1 + i / COLUMNS * 18;
	}

	public static boolean contains(double mouseX, double mouseY, int left, int top, int imageWidth) {
		int x = x0(left, imageWidth);
		return mouseX >= x && mouseX < x + width() && mouseY >= top && mouseY < top + height();
	}

	public static void extract(GuiGraphicsExtractor graphics, Font font, Player player, int left, int top, int imageWidth) {
		List<StationRecipe> recipes = recipes();
		if (recipes.isEmpty()) {
			return;
		}
		int x = x0(left, imageWidth);
		BenchGui.panel(graphics, x, top, x + width(), top + height());
		graphics.text(font, Component.translatable("container.tomyen.weapon_station.blueprints"), x + PADDING, top + 6, 0xFF404040, false);
		for (int i = 0; i < recipes.size(); i++) {
			StationRecipe recipe = recipes.get(i);
			int cx = cellX(left, imageWidth, i), cy = cellY(top, i);
			BenchGui.slotFrame(graphics, cx, cy);
			BenchGui.item(graphics, recipe.result(), cx, cy, !recipe.unlockedFor(player), true);
		}
	}

	/** A greyed, locked result when the inputs (the menu's first {@code inputCount} slots) make a recipe the player hasn't learned. */
	public static void extractLockedResult(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, Player player, int inputCount,
			int x, int y) {
		StationRecipe match = lockedMatch(menu, player, inputCount);
		if (match != null) {
			BenchGui.item(graphics, match.result(), x, y, true, true);
		}
	}

	private static StationRecipe lockedMatch(AbstractContainerMenu menu, Player player, int inputCount) {
		if (menu.getSlot(inputCount).hasItem()) {
			return null;
		}
		SimpleContainer inputs = new SimpleContainer(inputCount);
		for (int i = 0; i < inputCount; i++) {
			inputs.setItem(i, menu.getSlot(i).getItem());
		}
		StationRecipe match = StationRecipes.find(inputs);
		return match != null && !match.unlockedFor(player) ? match : null;
	}

	public static void extractTooltip(GuiGraphicsExtractor graphics, Font font, AbstractContainerMenu menu, Player player, int left,
			int top, int imageWidth, int inputCount, int resultX, int resultY, int mouseX, int mouseY) {
		List<StationRecipe> recipes = recipes();
		for (int i = 0; i < recipes.size(); i++) {
			int cx = cellX(left, imageWidth, i), cy = cellY(top, i);
			if (mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
				graphics.setTooltipForNextFrame(font, tooltip(recipes.get(i), player), Optional.empty(), mouseX, mouseY);
				return;
			}
		}
		if (mouseX >= left + resultX && mouseX < left + resultX + 16 && mouseY >= top + resultY && mouseY < top + resultY + 16) {
			StationRecipe match = lockedMatch(menu, player, inputCount);
			if (match != null) {
				graphics.setTooltipForNextFrame(font, tooltip(match, player), Optional.empty(), mouseX, mouseY);
			}
		}
	}

	private static List<Component> tooltip(StationRecipe recipe, Player player) {
		List<Component> lines = new ArrayList<>();
		lines.add(recipe.result().getHoverName());
		lines.addAll(BenchGui.blueprintLines(player, recipe.blueprint()));
		for (StationRecipe.Cost cost : recipe.costs()) {
			lines.add(BenchGui.costLine(cost.count(), new ItemStack(cost.item()).getHoverName(), BenchGui.carried(player, cost.item())));
		}
		if (recipe.unlockedFor(player)) {
			lines.add(Component.translatable("container.tomyen.weapon_station.blueprint_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}
}
