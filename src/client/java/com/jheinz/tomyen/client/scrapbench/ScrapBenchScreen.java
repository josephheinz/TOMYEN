package com.jheinz.tomyen.client.scrapbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.fishing.fish.Fishes;
import com.jheinz.tomyen.scrapbench.BenchRecipe;
import com.jheinz.tomyen.scrapbench.BenchRecipes;
import com.jheinz.tomyen.scrapbench.SalvageRecipe;
import com.jheinz.tomyen.scrapbench.SalvageRecipes;
import com.jheinz.tomyen.scrapbench.ScrapBenchMenu;
import com.jheinz.tomyen.shoddy.ShoddyGuns;

/**
 * The Scrap Bench: two tabs along the top (Assemble, Salvage), a scrolling list on the left and the current mode's slots
 * on the right.
 * <ul>
 * <li>Assemble lists everything the bench makes. Click one to pull its ingredients into the four input slots (shift: as
 * many crafts as fit a stack). Recipes you can't pay for are washed out; blueprint recipes you haven't learned are
 * greyed out with a lock, and say which blueprint to look for.</li>
 * <li>Salvage lists what the bench breaks down. Click one to put it in; the hammer breaks one down (shift: the whole
 * stack). An empty output grid shows faintly what the input will give.</li>
 * </ul>
 */
public class ScrapBenchScreen extends AbstractContainerScreen<ScrapBenchMenu> {
	// Layout (matches tools/gen_scrap_bench.py and ScrapBenchMenu).
	private static final int LIST_X = 8;
	private static final int LIST_Y = 16;
	private static final int COLUMNS = 4;
	private static final int ROWS = 3;
	private static final int CELL_W = 16;
	private static final int CELL_H = 18;
	private static final int SCROLL_X = 76;
	private static final int SCROLL_Y = 16;
	private static final int SCROLL_H = ROWS * CELL_H;
	private static final int SCROLLER_H = 15;
	private static final int OVERLAY_X = 92;
	private static final int OVERLAY_Y = 16;
	private static final int OVERLAY_W = 80;
	private static final int OVERLAY_H = 60;
	private static final int ASSEMBLE_OVERLAY_V = 0;
	private static final int SALVAGE_OVERLAY_V = 64;
	private static final int BUTTON_U = 176;
	private static final int BUTTON_V = 128;
	private static final int TAB_W = 26;
	private static final int TAB_H = 32;
	private static final int TAB_SPACING = 27;

	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/stonecutter/scroller");
	private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
	private static final Identifier CELL = Identifier.withDefaultNamespace("container/stonecutter/recipe");
	private static final Identifier CELL_HIGHLIGHTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
	private static final Identifier[] TABS_UNSELECTED = {
			Identifier.withDefaultNamespace("container/creative_inventory/tab_top_unselected_1"),
			Identifier.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2")};
	private static final Identifier[] TABS_SELECTED = {
			Identifier.withDefaultNamespace("container/creative_inventory/tab_top_selected_1"),
			Identifier.withDefaultNamespace("container/creative_inventory/tab_top_selected_2")};

	private float scrollOffs;
	private boolean scrolling;
	private int startIndex;
	private int shownMode = -1;

	public ScrapBenchScreen(ScrapBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, ScrapBenchMenu.WIDTH, ScrapBenchMenu.HEIGHT);
		inventoryLabelY = ScrapBenchMenu.INVENTORY_Y - 11;
	}

	private boolean assembling() {
		return menu.mode() == ScrapBenchMenu.MODE_ASSEMBLE;
	}

	private int entryCount() {
		return assembling() ? BenchRecipes.ALL.size() : SalvageRecipes.ALL.size();
	}

	private int offscreenRows() {
		return Math.max(0, (entryCount() + COLUMNS - 1) / COLUMNS - ROWS);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (shownMode != menu.mode()) {
			shownMode = menu.mode();
			scrollOffs = 0.0f;
			startIndex = 0;
		}
	}

	// ----------------------------------------------------------------------------------------------- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int mode = menu.mode();
		// The unselected tab sits behind the panel, the selected one in front of its top edge.
		tab(graphics, 1 - mode, false);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BenchGui.TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BenchGui.TEXTURE, leftPos + OVERLAY_X, topPos + OVERLAY_Y, 176,
				mode == ScrapBenchMenu.MODE_ASSEMBLE ? ASSEMBLE_OVERLAY_V : SALVAGE_OVERLAY_V, OVERLAY_W, OVERLAY_H, 256, 256);
		tab(graphics, mode, true);

		int sx = leftPos + SCROLL_X, sy = topPos + SCROLL_Y;
		boolean scrollable = offscreenRows() > 0;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, scrollable ? SCROLLER : SCROLLER_DISABLED, sx,
				sy + (int) ((SCROLL_H - SCROLLER_H) * scrollOffs), 12, SCROLLER_H);
		if (scrollable && mouseX >= sx && mouseX < sx + 12 && mouseY >= sy && mouseY < sy + SCROLL_H) {
			graphics.requestCursor(scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
		}

		extractList(graphics, mouseX, mouseY);
		if (assembling()) {
			extractLockedResult(graphics);
		} else {
			extractBreakButton(graphics, mouseX, mouseY);
			extractSalvagePreview(graphics);
		}
	}

	private void tab(GuiGraphicsExtractor graphics, int index, boolean selected) {
		int x = leftPos + index * TAB_SPACING, y = topPos - 28;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, (selected ? TABS_SELECTED : TABS_UNSELECTED)[index], x, y, TAB_W, TAB_H);
		Item icon = index == ScrapBenchMenu.MODE_ASSEMBLE ? ShoddyGuns.PIPE_PISTOL : Fishes.RUSTY_SCRAP;
		graphics.item(new ItemStack(icon), x + 5, y + 9);
	}

	private void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int end = Math.min(entryCount(), startIndex + COLUMNS * ROWS);
		for (int index = startIndex; index < end; index++) {
			int x = cellX(index), y = cellY(index);
			boolean hovered = mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? CELL_HIGHLIGHTED : CELL, x, y, CELL_W, CELL_H);
			if (assembling()) {
				BenchRecipe recipe = BenchRecipes.ALL.get(index);
				boolean locked = !recipe.unlockedFor(minecraft.player);
				BenchGui.item(graphics, recipe.result(), x, y + 1, locked, affordable(recipe));
				if (hovered && !locked) {
					graphics.requestCursor(CursorTypes.POINTING_HAND);
				}
			} else {
				Item input = SalvageRecipes.ALL.get(index).input();
				BenchGui.item(graphics, new ItemStack(input), x, y + 1, false, BenchGui.carried(minecraft.player, input) > 0);
				if (hovered) {
					graphics.requestCursor(CursorTypes.POINTING_HAND);
				}
			}
		}
	}

	/** Inputs that make a recipe this player hasn't learned: a greyed result with a lock. */
	private void extractLockedResult(GuiGraphicsExtractor graphics) {
		if (menu.getSlot(ScrapBenchMenu.RESULT_SLOT).hasItem()) {
			return;
		}
		BenchRecipe match = BenchRecipes.match(menu.inputs());
		if (match != null && !match.unlockedFor(minecraft.player)) {
			BenchGui.item(graphics, match.result(), leftPos + ScrapBenchMenu.RESULT_X, topPos + ScrapBenchMenu.RESULT_Y, true, true);
		}
	}

	private boolean canBreak() {
		return SalvageRecipes.find(menu.salvageInput()) != null;
	}

	private void extractBreakButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = leftPos + ScrapBenchMenu.BREAK_X, y = topPos + ScrapBenchMenu.BREAK_Y;
		boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
		int u = !canBreak() ? BUTTON_U + 36 : hovered ? BUTTON_U + 18 : BUTTON_U;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BenchGui.TEXTURE, x, y, u, BUTTON_V, 18, 18, 256, 256);
		if (hovered) {
			graphics.requestCursor(canBreak() ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
		}
	}

	/** Faint copies of what the input will break down into, while the output grid is empty. */
	private void extractSalvagePreview(GuiGraphicsExtractor graphics) {
		ItemStack input = menu.salvageInput();
		SalvageRecipe recipe = SalvageRecipes.find(input);
		if (recipe == null || !menu.outputsEmpty()) {
			return;
		}
		List<ItemStack> yield = recipe.yield(input);
		for (int i = 0; i < yield.size() && i < ScrapBenchMenu.OUTPUT_COUNT; i++) {
			int x = leftPos + ScrapBenchMenu.OUTPUT_X + i % 2 * 18, y = topPos + ScrapBenchMenu.OUTPUT_Y + i / 2 * 18;
			graphics.fakeItem(yield.get(i), x, y);
			graphics.itemDecorations(font, yield.get(i), x, y);
			graphics.fill(x, y, x + 16, y + 16, 0x808B8B8B);
		}
	}

	// ----------------------------------------------------------------------------------------------- tooltips

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		int index = entryAt(mouseX, mouseY);
		if (index >= 0) {
			graphics.setTooltipForNextFrame(font, assembling() ? recipeTooltip(BenchRecipes.ALL.get(index))
					: salvageTooltip(SalvageRecipes.ALL.get(index).input()), Optional.empty(), mouseX, mouseY);
			return;
		}
		for (int tab = 0; tab < 2; tab++) {
			if (onTab(tab, mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(font, Component.translatable(tab == ScrapBenchMenu.MODE_ASSEMBLE
						? "container.tomyen.scrap_bench.assemble" : "container.tomyen.scrap_bench.salvage"), mouseX, mouseY);
				return;
			}
		}
		if (!assembling() && isHovering(ScrapBenchMenu.BREAK_X, ScrapBenchMenu.BREAK_Y, 18, 18, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, List.of(
					Component.translatable("container.tomyen.scrap_bench.break").getVisualOrderText(),
					Component.translatable("container.tomyen.scrap_bench.break_all").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()),
					mouseX, mouseY);
			return;
		}
		if (assembling() && !menu.getSlot(ScrapBenchMenu.RESULT_SLOT).hasItem()
				&& isHovering(ScrapBenchMenu.RESULT_X, ScrapBenchMenu.RESULT_Y, 16, 16, mouseX, mouseY)) {
			BenchRecipe match = BenchRecipes.match(menu.inputs());
			if (match != null && !match.unlockedFor(minecraft.player)) {
				graphics.setTooltipForNextFrame(font, recipeTooltip(match), Optional.empty(), mouseX, mouseY);
			}
		}
	}

	private List<Component> recipeTooltip(BenchRecipe recipe) {
		List<Component> lines = new ArrayList<>();
		ItemStack result = recipe.result();
		lines.add(result.getCount() > 1 ? Component.translatable("container.tomyen.scrap_bench.result_count", result.getHoverName(),
				result.getCount()) : result.getHoverName());
		if (recipe.blueprint() != null) {
			lines.addAll(BenchGui.blueprintLines(minecraft.player, recipe.blueprint()));
		}
		for (BenchRecipe.Cost cost : recipe.costs()) {
			Component name = cost.item() != null ? new ItemStack(cost.item()).getHoverName() : BenchGui.tagName(cost.tag().location());
			lines.add(BenchGui.costLine(cost.count(), name, have(cost)));
		}
		if (recipe.unlockedFor(minecraft.player)) {
			lines.add(Component.translatable("container.tomyen.scrap_bench.fill_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	private List<Component> salvageTooltip(Item input) {
		List<Component> lines = new ArrayList<>();
		lines.add(new ItemStack(input).getHoverName());
		lines.add(Component.translatable("container.tomyen.scrap_bench.breaks_into").withStyle(ChatFormatting.GRAY));
		for (ItemStack out : SalvageRecipes.find(new ItemStack(input)).yield(new ItemStack(input))) {
			lines.add(Component.translatable("container.tomyen.scrap_bench.cost", out.getCount(), out.getHoverName())
					.withStyle(ChatFormatting.GRAY));
		}
		if (new ItemStack(input).isDamageableItem()) {
			lines.add(Component.translatable("container.tomyen.scrap_bench.worn").withStyle(ChatFormatting.DARK_GRAY));
		}
		lines.add(Component.translatable("container.tomyen.scrap_bench.salvage_hint").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	// ----------------------------------------------------------------------------------------- affordability

	/** How much of a cost the player has, counting the input slots (the best single kind, for a tag cost). */
	private int have(BenchRecipe.Cost cost) {
		int best = 0;
		for (Item option : cost.options()) {
			int count = BenchGui.carried(minecraft.player, option);
			for (int i = 0; i < ScrapBenchMenu.INPUT_COUNT; i++) {
				if (menu.inputs().getItem(i).is(option)) {
					count += menu.inputs().getItem(i).getCount();
				}
			}
			best = Math.max(best, count);
		}
		return best;
	}

	private boolean affordable(BenchRecipe recipe) {
		return recipe.costs().stream().allMatch(cost -> have(cost) >= cost.count());
	}

	// ------------------------------------------------------------------------------------------------- input

	private int cellX(int index) {
		return leftPos + LIST_X + (index - startIndex) % COLUMNS * CELL_W;
	}

	private int cellY(int index) {
		return topPos + LIST_Y + (index - startIndex) / COLUMNS * CELL_H;
	}

	private int entryAt(double mouseX, double mouseY) {
		int end = Math.min(entryCount(), startIndex + COLUMNS * ROWS);
		for (int index = startIndex; index < end; index++) {
			int x = cellX(index), y = cellY(index);
			if (mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H) {
				return index;
			}
		}
		return -1;
	}

	private boolean onTab(int tab, double mouseX, double mouseY) {
		int x = leftPos + tab * TAB_SPACING, y = topPos - 28;
		return mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < topPos;
	}

	private void press(int button) {
		minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (int tab = 0; tab < 2; tab++) {
			if (onTab(tab, event.x(), event.y())) {
				if (tab != menu.mode()) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
					press(tab == ScrapBenchMenu.MODE_ASSEMBLE ? ScrapBenchMenu.BUTTON_ASSEMBLE : ScrapBenchMenu.BUTTON_SALVAGE);
				}
				return true;
			}
		}
		int index = entryAt(event.x(), event.y());
		if (index >= 0) {
			if (assembling() && !BenchRecipes.ALL.get(index).unlockedFor(minecraft.player)) {
				return true;
			}
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0f));
			press(ScrapBenchMenu.BUTTON_LIST + index * 2 + (event.hasShiftDown() ? 1 : 0));
			return true;
		}
		if (!assembling() && canBreak() && isHovering(ScrapBenchMenu.BREAK_X, ScrapBenchMenu.BREAK_Y, 18, 18, event.x(), event.y())) {
			press(event.hasShiftDown() ? ScrapBenchMenu.BUTTON_BREAK_ALL : ScrapBenchMenu.BUTTON_BREAK_ONE);
			return true;
		}
		int sx = leftPos + SCROLL_X, sy = topPos + SCROLL_Y;
		if (event.x() >= sx && event.x() < sx + 12 && event.y() >= sy && event.y() < sy + SCROLL_H) {
			scrolling = true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (scrolling && offscreenRows() > 0) {
			int top = topPos + SCROLL_Y;
			scrollOffs = Mth.clamp(((float) event.y() - top - SCROLLER_H / 2.0f) / (SCROLL_H - SCROLLER_H), 0.0f, 1.0f);
			startIndex = (int) (scrollOffs * offscreenRows() + 0.5) * COLUMNS;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		scrolling = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (super.mouseScrolled(x, y, scrollX, scrollY)) {
			return true;
		}
		int rows = offscreenRows();
		if (rows > 0) {
			scrollOffs = Mth.clamp(scrollOffs - (float) scrollY / rows, 0.0f, 1.0f);
			startIndex = (int) (scrollOffs * rows + 0.5) * COLUMNS;
		}
		return true;
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
		return super.hasClickedOutside(mx, my, xo, yo) && !onTab(0, mx, my) && !onTab(1, mx, my);
	}
}
