package com.jheinz.mymod.client.fishing;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.fishing.RodCondition;
import com.jheinz.mymod.fishing.RodItem;
import com.jheinz.mymod.fishing.RodStats;
import com.jheinz.mymod.fishing.TackleBenchMenu;

/**
 * The Tackle Bench: the rod slot, its four part slots (line, hook, reel, lure), the bait slot under them and, on the
 * right, a readout of what the rod adds up to with its parts and bait, updating as they go in and out.
 */
public class TackleBenchScreen extends AbstractContainerScreen<TackleBenchMenu> {
	private static final Identifier BACKGROUND = MyMod.id("textures/gui/container/tackle_bench.png");
	/** The readout's inset, shared with the background texture. */
	private static final int PANEL_X = 96;
	private static final int PANEL_Y = 16;
	private static final int PANEL_W = 72;
	private static final int PANEL_H = 64;
	private static final int LABEL = 0xFFB9C6D2;
	private static final int BETTER = 0xFF7FD0FF;
	private static final int WORSE = 0xFFFF8A80;
	private static final int SAME = 0xFF6F7E8C;

	public TackleBenchScreen(TackleBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
		ItemStack rod = menu.rod();
		int x = leftPos + PANEL_X;
		int y = topPos + PANEL_Y;
		if (!(rod.getItem() instanceof RodItem)) {
			small(graphics, Component.translatable("container.mymod.tackle_bench.empty"), x + PANEL_W / 2, y + PANEL_H / 2 - 3, SAME, true);
			return;
		}
		RodStats stats = RodStats.of(rod);
		int line = 0;
		row(graphics, x, y, line++, "tier", Component.literal(String.valueOf(stats.tier())), LABEL);
		row(graphics, x, y, line++, "zone", percent(stats.zone() - 1.0f), tone(stats.zone() - 1.0f));
		row(graphics, x, y, line++, "speed", percent(stats.speed() - 1.0f), tone(1.0f - stats.speed()));
		row(graphics, x, y, line++, "hold", percent(stats.escape()), tone(stats.escape()));
		row(graphics, x, y, line++, "line", percent(stats.line()), tone(stats.line()));
		row(graphics, x, y, line++, "reel", percent(stats.reel() - 1.0f), tone(stats.reel() - 1.0f));
		int bite = stats.biteTicks(false, false);
		row(graphics, x, y, line++, "bite", Component.literal((bite > 0 ? "-" : bite < 0 ? "+" : "") + seconds(Math.abs(bite)) + "s"), tone(bite));
		row(graphics, x, y, line++, "rare", percent(stats.rarityBias(false, 0)), tone(stats.rarityBias(false, 0)));
		// The weight roll's lean from the rod's tier and bait (against an average species: no bait attraction).
		float size = stats.baseSizeBonus();
		row(graphics, x, y, line, "size", Component.literal("+" + Math.round(size * 100.0f) + "%"), tone(size));

		// What the numbers don't show, as tags under the readout.
		StringBuilder tags = new StringBuilder();
		if (stats.lava()) {
			tags.append(Component.translatable("container.mymod.tackle_bench.tag.lava").getString()).append("  ");
		}
		if (stats.mods().sonar()) {
			tags.append(Component.translatable("container.mymod.tackle_bench.tag.sonar").getString()).append("  ");
		}
		if (stats.assist() > 0) {
			tags.append(Component.translatable("container.mymod.tackle_bench.tag.servo").getString()).append("  ");
		}
		if (stats.mods().nightBiteTicks() > 0) {
			tags.append(Component.translatable("container.mymod.tackle_bench.tag.night").getString()).append("  ");
		}
		if (stats.mods().rainBiteTicks() > 0) {
			tags.append(Component.translatable("container.mymod.tackle_bench.tag.rain").getString()).append("  ");
		}
		if (stats.mods().predator() > 0) {
			tags.append(Component.translatable("container.mymod.tackle_bench.tag.predator").getString()).append("  ");
		}
		RodItem rodItem = (RodItem) rod.getItem();
		if (rodItem.condition() != RodCondition.NONE) {
			tags.append(Component.translatable(rodItem.condition().tagKey()).getString()).append("  ");
		}
		if (!rodItem.hasReel()) {
			tags.append(Component.translatable("container.mymod.tackle_bench.tag.no_reel").getString()).append("  ");
		}
		if (stats.mods().cursedBonus()) {
			tags.append(Component.translatable("container.mymod.tackle_bench.tag.cursed").getString()).append("  ");
		}
		if (stats.bait() != null && stats.bait().effect().radiation() > 0) {
			tags.append(Component.translatable("container.mymod.tackle_bench.tag.radiation").getString());
		}
		// Two tags to a line, under the rod (the bait slot sits under the parts).
		String[] words = tags.toString().trim().split("\\s+");
		for (int i = 0; i < words.length && !words[0].isEmpty(); i += 2) {
			String text = i + 1 < words.length ? words[i] + "  " + words[i + 1] : words[i];
			small(graphics, Component.literal(text), leftPos + 28, topPos + 61 + (i / 2) * 6, 0xFFFFC66B, true);
		}
	}

	private void row(GuiGraphicsExtractor graphics, int x, int y, int line, String key, Component value, int color) {
		float rowY = y + 2.5f + line * 6.5f;
		small(graphics, Component.translatable("container.mymod.tackle_bench.stat." + key), x + 4, rowY, LABEL, false);
		int width = Math.round(font.width(value) * 0.75f);
		small(graphics, value, x + PANEL_W - 4 - width, rowY, color, false);
	}

	private void small(GuiGraphicsExtractor graphics, Component text, float x, float y, int color, boolean centred) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(0.75f, 0.75f);
		if (centred) {
			graphics.centeredText(font, text, 0, 0, color);
		} else {
			graphics.text(font, text, 0, 0, color, false);
		}
		graphics.pose().popMatrix();
	}

	private static Component percent(float value) {
		int percent = Math.round(value * 100.0f);
		return Component.literal((percent > 0 ? "+" : "") + percent + "%");
	}

	private static String seconds(int ticks) {
		return String.format(Locale.ROOT, "%.1f", ticks / 20.0f);
	}

	private static int tone(float betterIfPositive) {
		return betterIfPositive > 0.0001f ? BETTER : betterIfPositive < -0.0001f ? WORSE : SAME;
	}
}
