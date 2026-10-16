package com.jheinz.mymod.client.scrapbench;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.blueprint.Blueprint;
import com.jheinz.mymod.blueprint.Blueprints;

/**
 * Drawing shared by the Scrap Bench screen and the Weapon Station's blueprint panel: the lock badge, the greyed-out and
 * "can't afford" washes over an item, and recipe tooltips that list costs against what the player carries.
 */
public final class BenchGui {
	public static final Identifier TEXTURE = MyMod.id("textures/gui/container/scrap_bench.png");
	// Sprite positions in TEXTURE (see tools/gen_scrap_bench.py).
	public static final int LOCK_U = 230;
	public static final int LOCK_V = 128;
	public static final int LOCK_W = 8;
	public static final int LOCK_H = 9;

	/** Over a locked recipe: mostly hides the item, the way a missing ingredient looks in vanilla's recipe book. */
	private static final int LOCKED_WASH = 0xB08B8B8B;
	/** Over a recipe the player can't pay for yet: a lighter wash. */
	private static final int SHORT_WASH = 0x708B8B8B;

	private BenchGui() {
	}

	/** An item with an optional wash over it and a lock in its corner. */
	public static void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, boolean locked, boolean affordable) {
		graphics.item(stack, x, y);
		if (stack.getCount() > 1 && !locked) {
			graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y);
		}
		if (locked) {
			graphics.fill(x, y, x + 16, y + 16, LOCKED_WASH);
			lock(graphics, x + 9, y + 8);
		} else if (!affordable) {
			graphics.fill(x, y, x + 16, y + 16, SHORT_WASH);
		}
	}

	public static void lock(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, LOCK_U, LOCK_V, LOCK_W, LOCK_H, 256, 256);
	}

	/** A recessed slot frame (vanilla's colours) around an item drawn at (x, y). */
	public static void slotFrame(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
		graphics.fill(x - 1, y - 1, x + 16, y, 0xFF373737);
		graphics.fill(x - 1, y - 1, x, y + 16, 0xFF373737);
		graphics.fill(x, y + 16, x + 17, y + 17, 0xFFFFFFFF);
		graphics.fill(x + 16, y, x + 17, y + 17, 0xFFFFFFFF);
	}

	/** A small raised panel in vanilla's container style. */
	public static void panel(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		graphics.fill(x0 + 1, y0, x1 - 1, y1, 0xFF000000);
		graphics.fill(x0, y0 + 1, x1, y1 - 1, 0xFF000000);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
		graphics.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 3, 0xFFFFFFFF);
		graphics.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 2, 0xFFFFFFFF);
		graphics.fill(x0 + 2, y1 - 3, x1 - 1, y1 - 1, 0xFF555555);
		graphics.fill(x1 - 3, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);
	}

	/** One cost line: "2 × Iron Ingot", red when the player is short. */
	public static Component costLine(int count, Component name, int have) {
		return Component.translatable("container.mymod.scrap_bench.cost", count, name)
				.withStyle(have >= count ? ChatFormatting.GRAY : ChatFormatting.RED);
	}

	/** The header lines for a blueprint recipe: learned, or what to look for. */
	public static List<Component> blueprintLines(Player player, Identifier blueprintId) {
		List<Component> lines = new ArrayList<>();
		Blueprint blueprint = Blueprints.get(blueprintId);
		if (blueprint == null) {
			return lines;
		}
		if (Blueprints.knows(player, blueprintId)) {
			lines.add(Component.translatable("container.mymod.blueprint.learned").withStyle(ChatFormatting.AQUA));
		} else {
			lines.add(Component.translatable("container.mymod.blueprint.locked", Blueprints.stack(blueprint).getHoverName())
					.withStyle(ChatFormatting.GOLD));
			lines.add(Component.translatable("container.mymod.blueprint.where").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	/** How many of an item the player carries. */
	public static int carried(Player player, Item item) {
		int total = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static Component tagName(Identifier tag) {
		return Component.translatableWithFallback("tag.item." + tag.getNamespace() + "." + tag.getPath().replace('/', '.'), tag.getPath());
	}
}
