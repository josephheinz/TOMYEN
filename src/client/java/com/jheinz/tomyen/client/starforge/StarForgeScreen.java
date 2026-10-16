package com.jheinz.tomyen.client.starforge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.starforge.Infusing;
import com.jheinz.tomyen.starforge.Infusion;
import com.jheinz.tomyen.starforge.StarForgeBlockEntity;
import com.jheinz.tomyen.starforge.StarForgeBlockEntity.Sky;
import com.jheinz.tomyen.starforge.StarForgeMenu;

/**
 * The Star Forge's screen: gear, catalyst and shards down the left beside a starlight gauge, a night-sky panel in
 * the middle where the infusion's own constellation is drawn star by star as it works, and the infused gear on the
 * right. Under the sky, a preview of what the infusion will do (or why it can't, or the infusion it will replace).
 * The moon in the panel's corner shows whether the forge can see the night sky; the "?" lists every catalyst.
 */
public class StarForgeScreen extends AbstractContainerScreen<StarForgeMenu> {
	private static final Identifier BACKGROUND = MyMod.id("textures/gui/container/star_forge.png");
	// Matches tools/gen_star_forge.py.
	static final int WIDTH = 176;
	static final int HEIGHT = 198;
	static final int PANEL_X0 = 48;
	static final int PANEL_Y0 = 17;
	static final int PANEL_X1 = 128;
	static final int PANEL_Y1 = 61;
	static final int GAUGE_X0 = 37;
	static final int GAUGE_Y0 = 18;
	static final int GAUGE_X1 = 41;
	static final int GAUGE_Y1 = 78;
	static final int SKY_X = 50;
	static final int SKY_Y = 19;
	static final int SKY_SIZE = 9;
	/** The sky icons sit in a row under the panel in the texture, in Sky order. */
	static final int ICON_U = 0;
	static final int ICON_V = 200;
	/** Ghost icons for the empty gear, catalyst and shard slots, 16 pixels each, under the sky icons. */
	static final int GHOST_V = 212;
	static final int HELP_X = 162;
	static final int HELP_Y = 5;
	static final int HELP_W = 7;
	static final int HELP_H = 9;
	static final int TEXT_X = 48;
	static final int TEXT_Y = 64;
	static final int DETAIL_LINES = 3;
	static final int TEXT_WIDTH = 124;

	/** Each infusion's constellation, in panel pixels: the stars in the order they light, then the lines between them. */
	record Constellation(int[][] stars, int[][] lines) {
	}

	static final Map<Infusion, Constellation> CONSTELLATIONS = new EnumMap<>(Infusion.class);

	static {
		// The Dipper: a handle and a bowl.
		CONSTELLATIONS.put(Infusion.STARLIT, new Constellation(new int[][] {{14, 31}, {25, 26}, {36, 27}, {46, 21}, {56, 30}, {67, 33}, {70, 19}},
				new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 3}}));
		// A feather: a quill and its barbs.
		CONSTELLATIONS.put(Infusion.FEATHERWEIGHT, new Constellation(new int[][] {{16, 36}, {28, 29}, {40, 22}, {52, 15}, {64, 9}, {32, 17}, {46, 33},
				{58, 26}}, new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {2, 5}, {2, 6}, {3, 7}}));
		// A spiral into the dark.
		CONSTELLATIONS.put(Infusion.VOIDTOUCHED, new Constellation(new int[][] {{40, 22}, {46, 18}, {50, 26}, {42, 32}, {31, 27}, {30, 14}, {44, 7},
				{60, 13}, {64, 32}}, new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 8}}));
		// A lantern flame.
		CONSTELLATIONS.put(Infusion.SOULBOUND, new Constellation(new int[][] {{40, 38}, {29, 28}, {33, 16}, {40, 6}, {47, 16}, {51, 28}, {40, 24}},
				new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 0}, {6, 0}}));
		// A butterfly.
		CONSTELLATIONS.put(Infusion.FAE_KISSED, new Constellation(new int[][] {{40, 22}, {26, 9}, {17, 21}, {28, 34}, {54, 9}, {63, 21}, {52, 34}},
				new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {0, 4}, {4, 5}, {5, 6}, {6, 0}}));
		// A snowflake.
		CONSTELLATIONS.put(Infusion.RIMED, new Constellation(new int[][] {{40, 22}, {54, 22}, {47, 10}, {33, 10}, {26, 22}, {33, 34}, {47, 34}},
				new int[][] {{0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}}));
		// A volcano and its plume.
		CONSTELLATIONS.put(Infusion.MOLTEN, new Constellation(new int[][] {{20, 39}, {31, 27}, {37, 31}, {41, 11}, {46, 29}, {52, 24}, {62, 39}},
				new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}}));
		// A cross.
		CONSTELLATIONS.put(Infusion.CONSECRATED, new Constellation(new int[][] {{40, 5}, {40, 15}, {28, 15}, {52, 15}, {40, 27}, {40, 39}},
				new int[][] {{0, 1}, {1, 2}, {1, 3}, {1, 4}, {4, 5}}));
		// A coiling dragon.
		CONSTELLATIONS.put(Infusion.DRAKEHIDE, new Constellation(new int[][] {{13, 31}, {21, 22}, {31, 27}, {41, 18}, {51, 24}, {60, 13}, {69, 17},
				{65, 27}}, new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 5}}));
		// A trefoil.
		CONSTELLATIONS.put(Infusion.IRRADIATED, new Constellation(new int[][] {{40, 23}, {40, 7}, {54, 32}, {26, 32}, {50, 15}, {44, 36}, {30, 15}},
				new int[][] {{0, 1}, {0, 2}, {0, 3}, {1, 4}, {4, 2}, {2, 5}, {5, 3}, {3, 6}, {6, 1}}));
	}

	public StarForgeScreen(StarForgeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		titleLabelX = (imageWidth - font.width(title)) / 2;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

		Sky sky = menu.sky();
		extractTwinkles(graphics, sky);
		Infusing job = menu.job();
		if (job.ready()) {
			extractConstellation(graphics, CONSTELLATIONS.get(job.infusion()), job.infusion().color(), menu.progress());
		}
		if (!sky.works) {
			// Dormant: the sky panel clouds over.
			graphics.fill(leftPos + PANEL_X0 + 1, topPos + PANEL_Y0 + 1, leftPos + PANEL_X1 - 1, topPos + PANEL_Y1 - 1, 0x99101018);
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos + SKY_X, topPos + SKY_Y, ICON_U + sky.ordinal() * SKY_SIZE, ICON_V,
				SKY_SIZE, SKY_SIZE, 256, 256);

		int[][] ghosts = {{StarForgeMenu.INPUT_X, StarForgeMenu.INPUT_Y}, {StarForgeMenu.CATALYST_X, StarForgeMenu.CATALYST_Y},
				{StarForgeMenu.FUEL_X, StarForgeMenu.FUEL_Y}};
		for (int i = 0; i < ghosts.length; i++) {
			if (!menu.getSlot(i).hasItem()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos + ghosts[i][0], topPos + ghosts[i][1], i * 16, GHOST_V, 16, 16,
						256, 256);
			}
		}

		int level = Mth.ceil(menu.starlight() * (GAUGE_Y1 - GAUGE_Y0));
		if (level > 0) {
			graphics.fillGradient(leftPos + GAUGE_X0, topPos + GAUGE_Y1 - level, leftPos + GAUGE_X1, topPos + GAUGE_Y1, 0xFFF4FBFF, 0xFF5E7FE0);
		}
	}

	/** A handful of the panel's faint stars flare and fade, slowly, while the sky is open. */
	private void extractTwinkles(GuiGraphicsExtractor graphics, Sky sky) {
		if (!sky.works) {
			return;
		}
		long now = Util.getMillis();
		for (int i = 0; i < 7; i++) {
			long seed = i * 7919L + 13;
			int x = PANEL_X0 + 3 + (int) (seed * 31 % (PANEL_X1 - PANEL_X0 - 6));
			int y = PANEL_Y0 + 3 + (int) (seed * 17 % (PANEL_Y1 - PANEL_Y0 - 6));
			float phase = (float) ((now / 1000.0 + i * 1.7) % 6.0) / 6.0f;
			float glow = Mth.clamp(1.0f - Math.abs(phase - 0.5f) * 6.0f, 0.0f, 1.0f);
			if (glow > 0.0f) {
				graphics.fill(leftPos + x, topPos + y, leftPos + x + 1, topPos + y + 1, ARGB.color(Math.round(glow * 255), 0xDCE8FF));
			}
		}
	}

	/** The infusion's constellation: faint dots to come, then each star and line lit as the work goes on. */
	private void extractConstellation(GuiGraphicsExtractor graphics, Constellation constellation, int color, float progress) {
		int ox = leftPos + PANEL_X0, oy = topPos + PANEL_Y0;
		int[][] stars = constellation.stars();
		float lit = progress * stars.length;
		// Lines first, under the stars: each one draws out once both its stars are lit.
		for (int[] line : constellation.lines()) {
			int[] a = stars[line[0]], b = stars[line[1]];
			float from = Math.max(line[0], line[1]);
			float drawn = Mth.clamp(lit - from, 0.0f, 1.0f);
			if (drawn > 0.0f) {
				line(graphics, ox + a[0], oy + a[1], ox + b[0], oy + b[1], drawn, ARGB.color(170, color));
			}
		}
		for (int i = 0; i < stars.length; i++) {
			int x = ox + stars[i][0], y = oy + stars[i][1];
			if (lit > i) {
				graphics.fill(x - 1, y, x + 2, y + 1, ARGB.opaque(color));
				graphics.fill(x, y - 1, x + 1, y + 2, ARGB.opaque(color));
				graphics.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
			} else {
				graphics.fill(x, y, x + 1, y + 1, ARGB.color(110, color));
			}
		}
	}

	/** A one-pixel line from a to b, only the first {@code part} of it. */
	private static void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float part, int color) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		int last = Math.round(steps * part);
		for (int i = 1; i < last; i++) {
			int x = x0 + Math.round((x1 - x0) * (float) i / steps);
			int y = y0 + Math.round((y1 - y0) * (float) i / steps);
			graphics.fill(x, y, x + 1, y + 1, color);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		graphics.text(font, "?", HELP_X + 1, HELP_Y + 1, 0xFF404040, false);
		Infusing job = menu.job();
		// Why a ready job isn't moving, written across the sky.
		Component hold = null;
		if (job.ready() && !menu.sky().works) {
			hold = Component.translatable("container.tomyen.star_forge.dormant");
		} else if (job.ready() && !menu.hasFuel()) {
			hold = Component.translatable("container.tomyen.star_forge.needs_starlight");
		} else if (job.ready() && menu.outputBlocked()) {
			hold = Component.translatable("container.tomyen.star_forge.output_full");
		}
		if (hold != null) {
			int x = (PANEL_X0 + PANEL_X1 - font.width(hold)) / 2;
			graphics.text(font, hold, x, (PANEL_Y0 + PANEL_Y1) / 2 - 4, 0xFFB8C6F0, true);
		}
		Component summary = job.ready()
				? Component.translatable("infusion.tomyen.title", job.infusion().displayName()).withColor(job.infusion().screenColor())
				: job.summary();
		clippedText(graphics, summary, TEXT_X, TEXT_Y);
		// What it will do, wrapped under the summary; the panel's tooltip has all of it.
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component detail : job.details()) {
			wrapped.addAll(font.split(detail, TEXT_WIDTH));
		}
		for (int i = 0; i < Math.min(DETAIL_LINES, wrapped.size()); i++) {
			graphics.text(font, wrapped.get(i), TEXT_X, TEXT_Y + 10 + i * 10, 0xFF404040, false);
		}
	}

	private void clippedText(GuiGraphicsExtractor graphics, Component text, int x, int y) {
		if (font.width(text) <= TEXT_WIDTH) {
			graphics.text(font, text, x, y, 0xFF404040, false);
			return;
		}
		var clipped = font.substrByWidth(text, TEXT_WIDTH - font.width("..."));
		graphics.text(font, Language.getInstance().getVisualOrder(clipped), x, y, 0xFF404040, false);
		graphics.text(font, "...", x + font.width(clipped), y, 0xFF404040, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		List<Component> lines = null;
		if (isHovering(SKY_X, SKY_Y, SKY_SIZE, SKY_SIZE, mouseX, mouseY)) {
			lines = skyLines(menu.sky());
		} else if (isHovering(HELP_X, HELP_Y, HELP_W, HELP_H, mouseX, mouseY)) {
			lines = StarForgeClient.guide();
		} else if (isHovering(GAUGE_X0 - 1, GAUGE_Y0 - 1, GAUGE_X1 - GAUGE_X0 + 2, GAUGE_Y1 - GAUGE_Y0 + 2, mouseX, mouseY)) {
			lines = List.of(Component.translatable("container.tomyen.star_forge.starlight", Mth.ceil(menu.starlight() * 100.0f)),
					Component.translatable("container.tomyen.star_forge.starlight.hint").withStyle(ChatFormatting.GRAY));
		} else if (isHovering(PANEL_X0, PANEL_Y0, TEXT_WIDTH, TEXT_Y + 10 * (DETAIL_LINES + 1) - PANEL_Y0, mouseX, mouseY)) {
			lines = jobLines(menu.job());
		}
		if (lines != null && !lines.isEmpty()) {
			graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
		}
	}

	private static List<Component> skyLines(Sky sky) {
		String key = "container.tomyen.star_forge.sky." + sky.name().toLowerCase(java.util.Locale.ROOT);
		return List.of(Component.translatable(key).withStyle(sky.works ? ChatFormatting.AQUA : ChatFormatting.GRAY),
				Component.translatable("container.tomyen.star_forge.sky.rule").withStyle(ChatFormatting.DARK_GRAY));
	}

	private List<Component> jobLines(Infusing job) {
		List<Component> lines = new ArrayList<>();
		if (job.ready()) {
			lines.addAll(job.infusion().tooltip(job.kinds()));
			if (job.status() == Infusing.Status.REPLACES) {
				lines.add(Component.translatable("container.tomyen.star_forge.replaces_warning", job.previous().displayName())
						.withStyle(ChatFormatting.GOLD));
			}
			lines.add(Component.translatable("container.tomyen.star_forge.cost",
					StarForgeBlockEntity.INFUSE_TICKS / StarForgeBlockEntity.STARLIGHT_PER_SHARD).withStyle(ChatFormatting.DARK_GRAY));
		} else {
			lines.add(job.summary());
			if (job.infusion() != null && job.status() == Infusing.Status.WONT_TAKE) {
				lines.add(Component.translatable("container.tomyen.star_forge.takes", StarForgeClient.takes(job.infusion()))
						.withStyle(ChatFormatting.GRAY));
			}
		}
		return lines;
	}
}
