package com.jheinz.mymod.client.trombone;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.trombone.TromboneItem;
import com.jheinz.mymod.trombone.TromboneMusic;
import com.jheinz.mymod.trombone.TromboneNotes;
import com.jheinz.mymod.trombone.TromboneSong;
import com.jheinz.mymod.trombone.TrombonePayloads;

/**
 * The Trombone's little staff, beside the hotbar while it's in hand: a dark plate with a brass strip, "Trombone" (or the
 * song just played, in its colour) and the note sounding; five staff lines (with ledger lines past them) carrying the
 * phrase so far, the newest note last, long notes hollow, and while blowing a pale note where the slide points; and
 * along the bottom a bar for each song, lit when it plays and draining while it cools down. Notes that made a song turn
 * its colour. Laid out to match tools/preview_trombone.py.
 */
public final class TromboneHud {
	private static final int BACKGROUND = 0xD0101418;
	private static final int ACCENT = 0xFFE2B85A;
	private static final int TEXT = 0xFFE6E9EC;
	private static final int DIM = 0xFF8F99A3;
	private static final int LINE = 0x60E6E9EC;
	private static final int GHOST = 0xA0FFE29A;
	private static final int WIDTH = 118;
	private static final int HEIGHT = 56;
	private static final int STAFF_X = 22;
	private static final int NOTE_STEP = 13;
	private static final int MAX_SHOWN = 7;
	/** How long the song's name shows after it's matched, in ticks. */
	private static final int SONG_SHOWN = 60;

	private static byte[] degrees = new byte[0];
	private static byte[] ticks = new byte[0];
	private static int song = -1;
	private static boolean played;
	private static int[] cooldowns = new int[TromboneSong.values().length];
	private static long received;

	private TromboneHud() {
	}

	public static void receive(TrombonePayloads.Staff staff) {
		Minecraft minecraft = Minecraft.getInstance();
		degrees = staff.degrees();
		ticks = staff.ticks();
		song = staff.song() >= 0 && staff.song() < TromboneSong.values().length ? staff.song() : -1;
		played = staff.played();
		cooldowns = staff.cooldowns();
		received = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
	}

	public static void clear() {
		degrees = new byte[0];
		ticks = new byte[0];
		song = -1;
		cooldowns = new int[TromboneSong.values().length];
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof TromboneItem)) {
			return;
		}
		long since = minecraft.level.getGameTime() - received;
		boolean blowing = TromboneMusic.playing(player);
		int x = graphics.guiWidth() / 2 + 91 + 6;
		int y = graphics.guiHeight() - HEIGHT - 3;
		Font font = minecraft.font;

		graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
		graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 2, ACCENT);
		TromboneSong shown = song >= 0 && since < SONG_SHOWN ? TromboneSong.values()[song] : null;
		if (shown != null) {
			Component name = played ? Component.translatable("hud.mymod.trombone.played", Component.translatable(shown.translationKey()))
					: Component.translatable("hud.mymod.trombone.cooling", Component.translatable(shown.translationKey()),
							Mth.ceil(Math.max(0, cooldowns[shown.ordinal()] - since) / 20.0f));
			graphics.text(font, name, x + 4, y + 5, played ? shown.colour() : DIM, true);
		} else {
			graphics.text(font, held.getHoverName(), x + 4, y + 5, ACCENT, true);
		}
		int live = TromboneNotes.degree(player.getXRot());
		if (blowing && shown == null) {
			String note = TromboneNotes.name(live);
			graphics.text(font, note, x + WIDTH - 4 - font.width(note), y + 5, TEXT, true);
		}
		graphics.item(held, x + 3, y + 24);

		for (int d = 3; d <= 11; d += 2) {
			graphics.fill(x + STAFF_X - 2, y + noteY(d), x + WIDTH - 4, y + noteY(d) + 1, LINE);
		}
		// The phrase (the newest that fit), then the ghost of the note being blown.
		int shownNotes = Math.min(degrees.length, blowing ? MAX_SHOWN - 1 : MAX_SHOWN);
		int first = degrees.length - shownNotes;
		int colour = shown != null && played ? shown.colour() : TEXT;
		for (int i = 0; i < shownNotes; i++) {
			int d = degrees[first + i];
			note(graphics, x + STAFF_X + i * NOTE_STEP, y, d, colour, ticks[first + i] >= TromboneSong.LONG_TICKS);
		}
		if (blowing) {
			note(graphics, x + STAFF_X + shownNotes * NOTE_STEP, y, live, GHOST, false);
		}

		TromboneSong[] songs = TromboneSong.values();
		for (int i = 0; i < songs.length; i++) {
			int x0 = x + 4 + i * 28;
			int rgb = songs[i].colour() & 0xFFFFFF;
			boolean lit = shown == songs[i] && played;
			graphics.fill(x0, y + HEIGHT - 6, x0 + 26, y + HEIGHT - 3, (lit ? 0xFF000000 : 0x50000000) | rgb);
			long left = cooldowns[i] - since;
			if (left > 0) {
				int w = Mth.ceil(26.0f * left / songs[i].cooldown());
				graphics.fill(x0, y + HEIGHT - 6, x0 + Math.min(26, w), y + HEIGHT - 3, 0xB0101418);
			}
		}
	}

	/** The y of a scale degree's note on the staff, from the panel's top: two pixels a step, Bb3 on the middle line. */
	private static int noteY(int degree) {
		return 43 - 2 * degree;
	}

	private static void note(GuiGraphicsExtractor graphics, int x, int panelY, int degree, int colour, boolean hollow) {
		int y = panelY + noteY(degree);
		if (degree <= 1) {
			graphics.fill(x - 2, panelY + noteY(1), x + 7, panelY + noteY(1) + 1, LINE);
		}
		if (degree >= 13) {
			graphics.fill(x - 2, panelY + noteY(13), x + 7, panelY + noteY(13) + 1, LINE);
		}
		graphics.fill(x + 1, y - 2, x + 4, y - 1, colour);
		graphics.fill(x, y - 1, x + 5, y + 1, colour);
		graphics.fill(x + 1, y + 1, x + 4, y + 2, colour);
		if (hollow) {
			graphics.fill(x + 1, y - 1, x + 4, y + 1, 0xFF101418);
		}
		// Stems up below the middle line, down from it and above.
		if (degree < TromboneNotes.BASE) {
			graphics.fill(x + 4, y - 9, x + 5, y, colour);
		} else {
			graphics.fill(x, y + 1, x + 1, y + 10, colour);
		}
	}
}
