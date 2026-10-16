package com.jheinz.tomyen.client.detector;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.bank.Debt;
import com.jheinz.tomyen.bank.DebtCollection;
import com.jheinz.tomyen.bank.Vaults;
import com.jheinz.tomyen.detector.Detectors;
import com.jheinz.tomyen.detector.LedgerItem;
import com.jheinz.tomyen.detector.Moon;
import com.jheinz.tomyen.detector.OreTarget;
import com.jheinz.tomyen.fae.Feywild;
import com.jheinz.tomyen.meteorite.Meteor;

/**
 * The detectors' shared HUD panel, beside the hotbar where the ammo count goes: a dark plate with a strip of the
 * gadget's colour along the top, its icon and name, a hairline, then a few short lines and, for some, a graphic (the
 * pendulum's pull, the metal detector's signal bar, the radar scope). The main hand's gadget wins over the off
 * hand's; with a gun in the main hand the panel sits above its ammo count.
 */
public final class DetectorHud {
	private static final int BACKGROUND = 0xD0101418;
	private static final int BORDER = 0xFF2C333B;
	private static final int DIVIDER = 0x40FFFFFF;
	private static final int TEXT = 0xFFE6E9EC;
	private static final int DIM = 0xFF8F99A3;
	private static final int GOOD = 0xFF6EE08A;
	private static final int WARN = 0xFFFFC94D;
	private static final int BAD = 0xFFFF5E57;
	private static final int OMEN = 0xFFB98AFF;
	private static final int PAD = 4;
	private static final int HEADER = 18;
	private static final int LINE = 10;

	private interface Graphic {
		void draw(GuiGraphicsExtractor graphics, int x, int y, float partialTicks);
	}

	private static final class Panel {
		final ItemStack icon;
		final int accent;
		final List<Component> lines = new ArrayList<>();
		final List<Integer> colours = new ArrayList<>();
		int graphicWidth;
		int graphicHeight;
		@Nullable Graphic graphic;

		Panel(ItemStack icon, int accent) {
			this.icon = icon;
			this.accent = accent;
		}

		Panel line(Component text, int colour) {
			lines.add(text);
			colours.add(colour);
			return this;
		}

		Panel graphic(int width, int height, Graphic graphic) {
			graphicWidth = width;
			graphicHeight = height;
			this.graphic = graphic;
			return this;
		}
	}

	private DetectorHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		Panel panel = panel(minecraft, player, player.getMainHandItem());
		if (panel == null) {
			panel = panel(minecraft, player, player.getOffhandItem());
		}
		if (panel != null) {
			draw(graphics, minecraft.font, panel, player.getMainHandItem().getItem() instanceof GunItem,
					delta.getGameTimeDeltaPartialTick(false));
		}
	}

	private static @Nullable Panel panel(Minecraft minecraft, LocalPlayer player, ItemStack stack) {
		Item item = stack.getItem();
		if (item == Detectors.PROSPECTORS_PENDULUM) {
			return pendulum(stack);
		} else if (item == Detectors.METAL_DETECTOR) {
			return metalDetector(stack);
		} else if (item == Detectors.METEORITE_SCANNER) {
			return meteoriteScanner(player, stack);
		} else if (item == Detectors.DOWSING_ROD || item == Detectors.FAE_DOWSING_ROD) {
			return dowsingRod(player, stack);
		} else if (item == Detectors.UFO_RADAR) {
			return radar(player, stack);
		} else if (item == Detectors.LUNAR_ALMANAC) {
			return almanac(player, stack);
		} else if (item == Detectors.LEDGER) {
			return ledger(player, stack);
		}
		return null;
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, Panel panel, boolean aboveAmmo, float partialTicks) {
		int width = 20 + font.width(panel.icon.getHoverName());
		for (Component line : panel.lines) {
			width = Math.max(width, font.width(line));
		}
		width = Math.max(width, panel.graphicWidth) + PAD * 2;
		int height = PAD + HEADER + panel.lines.size() * LINE + (panel.graphic != null ? panel.graphicHeight + 3 : 0) + PAD - 1;
		int x = Math.min(graphics.guiWidth() / 2 + 91 + 8, graphics.guiWidth() - width - 2);
		int y = graphics.guiHeight() - height - 2 - (aboveAmmo ? 24 : 0);

		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.outline(x, y, width, height, BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + 2, panel.accent);
		graphics.item(panel.icon, x + PAD - 1, y + PAD);
		graphics.text(font, panel.icon.getHoverName(), x + PAD + 18, y + PAD + 5, panel.accent, true);
		graphics.fill(x + PAD, y + PAD + HEADER, x + width - PAD, y + PAD + HEADER + 1, DIVIDER);
		int lineY = y + PAD + HEADER + 3;
		for (int i = 0; i < panel.lines.size(); i++) {
			graphics.text(font, panel.lines.get(i), x + PAD, lineY, panel.colours.get(i), true);
			lineY += LINE;
		}
		if (panel.graphic != null) {
			panel.graphic.draw(graphics, x + PAD, lineY, partialTicks);
		}
	}

	private static MutableComponent tr(String key, Object... args) {
		return Component.translatable("detector.tomyen." + key, args);
	}

	/** A strength word for 0 (faint) to 1 (right there). */
	private static String strength(float closeness) {
		return closeness > 0.66f ? "strong" : closeness > 0.33f ? "steady" : "faint";
	}

	// --- Prospector's Pendulum -----------------------------------------------------------------------------------

	private static Panel pendulum(ItemStack stack) {
		Panel panel = new Panel(stack, 0xFFE0A066);
		OreTarget target = PendulumClient.target();
		if (target == null) {
			return panel.line(tr("pendulum.untuned"), DIM).line(tr("pendulum.tune_hint"), DIM);
		}
		panel.line(tr("pendulum.tuned", target.name()), 0xFF000000 | target.colour());
		BlockPos nearest = PendulumClient.nearest();
		LocalPlayer player = Minecraft.getInstance().player;
		if (nearest == null || player == null) {
			return panel.line(tr("pendulum.still", Detectors.PENDULUM_RANGE), DIM);
		}
		float closeness = PendulumClient.closeness();
		int dy = nearest.getY() - Mth.floor(player.getEyeY());
		Component pull = tr("pendulum.pull." + strength(closeness));
		String arrow = DetectorClient.arrow(DetectorClient.relativeYaw(player, nearest.getX() + 0.5, nearest.getZ() + 0.5));
		Component line = dy < -2 ? tr("pendulum.pull_below", arrow, pull) : dy > 2 ? tr("pendulum.pull_above", arrow, pull) : tr("pendulum.pull_level", arrow, pull);
		panel.line(line, TEXT);
		return panel.graphic(49, 4, (graphics, x, y, partialTicks) -> pips(graphics, x, y, closeness, 0xFF000000 | target.colour()));
	}

	/** Five pips, lit as far as the reading goes. */
	private static void pips(GuiGraphicsExtractor graphics, int x, int y, float fraction, int colour) {
		int lit = Mth.ceil(fraction * 5.0f - 0.01f);
		for (int i = 0; i < 5; i++) {
			graphics.fill(x + i * 10, y, x + i * 10 + 9, y + 4, i < lit ? colour : 0xFF2A3036);
		}
	}

	// --- Metal Detector ------------------------------------------------------------------------------------------

	private static Panel metalDetector(ItemStack stack) {
		Panel panel = new Panel(stack, 0xFF7FD06A);
		float signal = MetalDetectorClient.signal();
		int percent = Math.round(signal * 100.0f);
		if (percent < 2) {
			panel.line(tr("metal.none"), DIM);
		} else {
			panel.line(tr("metal.signal", percent), signal > 0.75f ? BAD : signal > 0.45f ? WARN : GOOD);
		}
		MetalDetectorClient.Find find = MetalDetectorClient.strongest();
		if (find != null && signal > 0.45f) {
			int depth = MetalDetectorClient.depth();
			Component where = depth < -1 ? tr("metal.below", -depth) : depth > 1 ? tr("metal.above", depth) : tr("metal.level");
			panel.line(tr("metal.find", tr("metal.kind." + find.name().toLowerCase()), where), TEXT);
		} else {
			panel.line(tr("metal.sweep"), DIM);
		}
		return panel.graphic(59, 5, (graphics, x, y, partialTicks) -> {
			int lit = Math.round(signal * 12.0f);
			for (int i = 0; i < 12; i++) {
				int colour = i < 6 ? 0xFF4CD964 : i < 9 ? 0xFFFFD23F : 0xFFFF5A4C;
				graphics.fill(x + i * 5, y, x + i * 5 + 4, y + 5, i < lit ? colour : 0xFF2A3036);
			}
		});
	}

	// --- Meteorite Scanner ---------------------------------------------------------------------------------------

	private static Panel meteoriteScanner(LocalPlayer player, ItemStack stack) {
		Panel panel = new Panel(stack, 0xFF5CE1E6);
		if (player.level().dimension() != Level.OVERWORLD) {
			panel.line(tr("scanner.wrong_world"), DIM);
		} else {
			BlockPos site = TrackerClient.target(player, Detectors.METEORITE_SCANNER);
			if (site == null) {
				panel.line(tr("scanner.none"), DIM).line(tr("scanner.none_range", Detectors.METEORITE_SCANNER_RANGE), DIM);
			} else {
				int distance = (int) Math.round(DetectorClient.flatDistance(player, site.getX() + 0.5, site.getZ() + 0.5));
				if (distance < TrackerClient.ARRIVED) {
					panel.line(tr("scanner.here"), GOOD).line(tr("scanner.dig"), DIM);
				} else {
					String arrow = DetectorClient.arrow(DetectorClient.relativeYaw(player, site.getX() + 0.5, site.getZ() + 0.5));
					panel.line(tr("scanner.site", arrow, distance), TEXT);
				}
			}
		}
		Meteor meteor = TrackerClient.meteor();
		if (meteor != null) {
			int distance = (int) Math.round(DetectorClient.flatDistance(player, meteor.getX(), meteor.getZ()));
			boolean flash = player.tickCount / 5 % 2 == 0;
			panel.line(tr("scanner.meteor", distance, DetectorClient.compass(meteor.getX() - player.getX(), meteor.getZ() - player.getZ())),
					flash ? BAD : WARN);
		}
		return panel;
	}

	// --- Dowsing Rods --------------------------------------------------------------------------------------------

	private static Panel dowsingRod(LocalPlayer player, ItemStack stack) {
		boolean fae = stack.is(Detectors.FAE_DOWSING_ROD);
		Panel panel = new Panel(stack, fae ? 0xFFF08CD8 : 0xFFB38EF3);
		Level level = player.level();
		if (Feywild.is(level)) {
			return panel.line(tr("rod.feywild"), OMEN);
		}
		if (level.dimension() != Level.OVERWORLD) {
			return panel.line(tr("rod.wrong_world"), DIM);
		}
		Item rod = stack.getItem();
		BlockPos ring = TrackerClient.target(player, rod);
		int range = TrackerClient.range(rod);
		if (ring == null) {
			return panel.line(tr("rod.none"), DIM).line(tr("rod.none_range", range), DIM);
		}
		double distance = DetectorClient.flatDistance(player, ring.getX() + 0.5, ring.getZ() + 0.5);
		if (distance < TrackerClient.ARRIVED) {
			panel.line(tr("rod.here"), fae ? 0xFFF08CD8 : GOOD);
		} else {
			String arrow = DetectorClient.arrow(DetectorClient.relativeYaw(player, ring.getX() + 0.5, ring.getZ() + 0.5));
			if (fae) {
				panel.line(tr("rod.ring", arrow, (int) Math.round(distance)), TEXT);
			} else {
				panel.line(tr("rod.tug", arrow, tr("rod.tug." + strength(1.0f - (float) (distance / range)))), TEXT);
			}
		}
		return panel.line(Moon.isNight(level) ? tr("rod.awake") : tr("rod.asleep"), Moon.isNight(level) ? 0xFF7FE7FF : DIM);
	}

	// --- UFO Radar -----------------------------------------------------------------------------------------------

	private static final int SCOPE = 22;

	private static Panel radar(LocalPlayer player, ItemStack stack) {
		Panel panel = new Panel(stack, 0xFF5CFF7A);
		List<UfoRadarClient.Contact> contacts = UfoRadarClient.contacts();
		UfoRadarClient.Contact alert = UfoRadarClient.alertContact();
		if (alert != null) {
			panel.line(UfoRadarClient.alertText(player, UfoRadarClient.alert(), alert),
					UfoRadarClient.alert() == UfoRadarClient.ABDUCTING ? (player.tickCount / 5 % 2 == 0 ? BAD : WARN) : WARN);
		} else if (contacts.isEmpty()) {
			panel.line(tr("radar.clear", Detectors.UFO_RADAR_RANGE), DIM);
		} else {
			panel.line(contacts.size() == 1 ? tr("radar.contact") : tr("radar.contacts", contacts.size()), GOOD);
		}
		return panel.graphic(SCOPE * 2 + 1, SCOPE * 2 + 1, (graphics, x, y, partialTicks) -> scope(graphics, player, x + SCOPE, y + SCOPE,
				partialTicks));
	}

	private static void scope(GuiGraphicsExtractor graphics, LocalPlayer player, int cx, int cy, float partialTicks) {
		int r = SCOPE;
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
			graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, 0xFF0A1A10);
			graphics.fill(cx - half, cy + dy, cx - half + 1, cy + dy + 1, 0xFF2F7A45);
			graphics.fill(cx + half, cy + dy, cx + half + 1, cy + dy + 1, 0xFF2F7A45);
		}
		graphics.fill(cx - r, cy, cx + r + 1, cy + 1, 0x302F7A45);
		graphics.fill(cx, cy - r, cx + 1, cy + r + 1, 0x302F7A45);
		int inner = r / 2;
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12.0;
			graphics.fill(cx + (int) Math.round(Math.sin(a) * inner), cy - (int) Math.round(Math.cos(a) * inner),
					cx + (int) Math.round(Math.sin(a) * inner) + 1, cy - (int) Math.round(Math.cos(a) * inner) + 1, 0x602F7A45);
		}
		// The sweep and its fading trail.
		float sweep = UfoRadarClient.sweep(partialTicks);
		for (int k = 4; k >= 0; k--) {
			double a = Math.toRadians(sweep - k * 5.0f);
			int alpha = k == 0 ? 0xFF : 0x70 - k * 0x14;
			for (int t = 1; t < r; t++) {
				int px = cx + (int) Math.round(Math.sin(a) * t), py = cy - (int) Math.round(Math.cos(a) * t);
				graphics.fill(px, py, px + 1, py + 1, alpha << 24 | 0x5CFF7A);
			}
		}
		for (UfoRadarClient.Contact contact : UfoRadarClient.contacts()) {
			double a = Math.toRadians(UfoRadarClient.bearing(player, contact));
			double d = Math.min(1.0, DetectorClient.flatDistance(player, contact.x(), contact.z()) / Detectors.UFO_RADAR_RANGE) * (r - 2);
			int px = cx + (int) Math.round(Math.sin(a) * d), py = cy - (int) Math.round(Math.cos(a) * d);
			float glow = 0.3f + 0.7f * UfoRadarClient.glow(contact, partialTicks);
			int rgb = contact.beaming() ? 0xFF4A4A : contact.landing() ? 0xFFC94D : 0x9CFFB0;
			graphics.fill(px - 1, py - 1, px + 2, py + 2, (int) (glow * 255) << 24 | rgb);
		}
		graphics.fill(cx, cy - 1, cx + 1, cy + 2, 0xFFFFFFFF);
		graphics.fill(cx - 1, cy + 1, cx + 2, cy + 2, 0xFFFFFFFF);
	}

	// --- Lunar Almanac -------------------------------------------------------------------------------------------

	private static Panel almanac(LocalPlayer player, ItemStack stack) {
		Panel panel = new Panel(stack, 0xFFBFD4FF);
		Level level = player.level();
		if (!Moon.visible(level)) {
			panel.line(tr("moon.none"), DIM);
		} else {
			MoonPhase phase = Moon.phase(level, player.position());
			panel.line(Moon.phaseName(phase), 0xFFBFD4FF);
			panel.line(Moon.countdown(level, phase), Moon.nightsToFull(phase) == 0 ? BAD : Moon.nightsToFull(phase) == 1 ? WARN : TEXT);
		}
		if (AlmanacClient.omen()) {
			panel.line(tr("almanac.omen").withStyle(ChatFormatting.ITALIC), OMEN);
		}
		if (AlmanacClient.hunted()) {
			panel.line(tr("almanac.hunted"), BAD);
		}
		return panel;
	}

	// --- Ledger --------------------------------------------------------------------------------------------------

	private static Panel ledger(LocalPlayer player, ItemStack stack) {
		Panel panel = new Panel(stack, 0xFF3DDC84);
		int vault = Vaults.clientBalance(player.getUUID());
		panel.line(vault < 0 ? tr("ledger.vault_unknown") : tr("ledger.vault", vault), GOOD);
		int[] cards = LedgerItem.cards(player);
		if (cards[1] > 0) {
			panel.line(tr("ledger.cards", cards[0], cards[1]), cards[0] >= cards[1] ? BAD : DIM);
		}
		Debt debt = Debt.of(player);
		if (debt == null) {
			panel.line(tr("ledger.debt_free"), GOOD);
		} else if (debt.hunting()) {
			panel.line(tr("ledger.owed", debt.owed()), WARN);
			panel.line(tr("ledger.hunting"), player.tickCount / 5 % 2 == 0 ? BAD : WARN);
		} else {
			panel.line(tr("ledger.owed", debt.owed()), WARN);
			panel.line(tr("ledger.due", DebtCollection.formatTime(debt.ticksLeft())), debt.ticksLeft() < 24000 ? BAD : TEXT);
		}
		return panel;
	}
}
