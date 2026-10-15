"""The preview sheet for gen_detectors.py: every detector sprite scaled up (on a slot-grey tile, beside the vanilla
items they borrow from), and a mockup of each gadget's HUD panel laid out as DetectorHud lays it out (same padding,
line height, colours and graphics), beside a vanilla hotbar, in vanilla's own font."""
import math, os

from gunkit import png

SCALE = 6          # sprite sheet texels
GUI = 2            # HUD mockup: screen pixels per GUI pixel


class Image:
    def __init__(self, w, h, colour=(0, 0, 0)):
        self.w, self.h = w, h
        self.px = [list(colour) for _ in range(w * h)]

    def blend(self, x, y, rgb, a=255):
        if 0 <= x < self.w and 0 <= y < self.h and a:
            p = self.px[y * self.w + x]
            for i in range(3):
                p[i] = (p[i] * (255 - a) + rgb[i] * a) // 255

    def fill(self, x0, y0, x1, y1, rgb, a=255):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.blend(x, y, rgb, a)

    def save(self, path):
        png(os.path.abspath(path), self.w, self.h, [tuple(p) + (255,) for p in self.px])


def argb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255), (c >> 24) & 255


# ---------------------------------------------------------------------------------------------- vanilla font
class Font:
    ARROWS = {
        "↑": ["..X..", ".XXX.", "X.X.X", "..X..", "..X..", "..X..", "....."],
        "↗": ["..XXX", "...XX", "..X.X", ".X...", "X....", ".....", "....."],
        "→": [".....", "...X.", "....X", "XXXXX", "....X", "...X.", "....."],
        "↘": ["X....", ".X...", "..X.X", "...XX", "..XXX", ".....", "....."],
        "↓": ["..X..", "..X..", "..X..", "X.X.X", ".XXX.", "..X..", "....."],
        "↙": ["....X", "...X.", "X.X..", "XX...", "XXX..", ".....", "....."],
        "←": [".....", ".X...", "X....", "XXXXX", "X....", ".X...", "....."],
        "↖": ["XXX..", "XX...", "X.X..", "...X.", "....X", ".....", "....."],
    }

    def __init__(self, vanilla):
        sheet = vanilla("font/ascii")
        self.cell = sheet.w // 16
        self.glyphs = {}
        for code in range(32, 127):
            gx, gy = code % 16 * self.cell, code // 16 * self.cell
            pixels = [(x, y) for y in range(self.cell) for x in range(self.cell) if sheet.get(gx + x, gy + y)[3]]
            width = max((x for x, _ in pixels), default=-1) + 1
            self.glyphs[chr(code)] = (pixels, width + 1 if pixels else 4)
        for ch, rows in self.ARROWS.items():
            self.glyphs[ch] = ([(x, y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == "X"], 6)
        self.glyphs["·"] = ([(0, 3)], 2)

    def width(self, text):
        return sum(self.glyphs.get(ch, self.glyphs["?"])[1] for ch in text) - (1 if text else 0)

    def draw(self, img, text, x, y, colour, scale, shadow=True, italic=False):
        rgb, _ = argb(colour)
        if shadow:
            self.draw(img, text, x + 1, y + 1, 0xFF000000 | (rgb[0] // 4) << 16 | (rgb[1] // 4) << 8 | rgb[2] // 4, scale, False, italic)
        cx = x
        for ch in text:
            pixels, adv = self.glyphs.get(ch, self.glyphs["?"])
            for px, py in pixels:
                slant = (7 - py) // 4 if italic else 0
                img.fill((cx + px) * scale + slant * scale // 2, (y + py) * scale, (cx + px + 1) * scale + slant * scale // 2,
                         (y + py + 1) * scale, rgb)
            cx += adv


# ---------------------------------------------------------------------------------------------- sprites
def blit(img, canvas, x, y, scale, frame=0):
    for ty in range(16):
        for tx in range(16):
            p = canvas.get(tx, frame * 16 + ty)
            if p[3]:
                img.fill(x + tx * scale, y + ty * scale, x + (tx + 1) * scale, y + (ty + 1) * scale, p[:3], p[3])


ROWS = [
    ("Prospector's Pendulum (bob -1 .. 1)", [f"prospectors_pendulum_{i}" for i in range(7)], ["item/string", "item/iron_nugget", "item/copper_nugget"]),
    ("Metal Detector (LED by signal)", [f"metal_detector_{i}" for i in range(4)], ["item/iron_ingot", "item/copper_ingot"]),
    ("Meteorite Scanner (none, then ahead .. clockwise)", ["meteorite_scanner"] + [f"meteorite_scanner_{i}" for i in range(8)], ["item/spyglass"]),
    ("Dowsing Rod (idle, ahead/dip, right, left)", ["dowsing_rod", "dowsing_rod_ahead", "dowsing_rod_right", "dowsing_rod_left"],
     ["item/stick", "item/amethyst_shard"]),
    ("Fae Dowsing Rod", ["fae_dowsing_rod", "fae_dowsing_rod_ahead", "fae_dowsing_rod_right", "fae_dowsing_rod_left"], []),
    ("UFO Radar (sweep frames: quiet / contact / alert)", [("ufo_radar", f) for f in range(0, 8, 2)] + [("ufo_radar_contact", f) for f in (1, 4)]
     + [("ufo_radar_alert", f) for f in (1, 4)], ["item/compass_16"]),
    ("Lunar Almanac (moon phase 0 full .. 7)", [f"lunar_almanac_{i}" for i in range(8)], ["item/book"]),
    ("Ledger", ["ledger"], ["item/book", "item/emerald"]),
]


# ---------------------------------------------------------------------------------------------- HUD mockups
BACKGROUND, BORDER, DIVIDER = 0xD0101418, 0xFF2C333B, 0x40FFFFFF
TEXT, DIM, GOOD, WARN, BAD, OMEN = 0xFFE6E9EC, 0xFF8F99A3, 0xFF6EE08A, 0xFFFFC94D, 0xFFFF5E57, 0xFFB98AFF
PAD, HEADER, LINE = 4, 18, 10


def gfill(img, x0, y0, x1, y1, colour, ox, oy):
    rgb, a = argb(colour)
    img.fill(ox + x0 * GUI, oy + y0 * GUI, ox + x1 * GUI, oy + y1 * GUI, rgb, a)


def pips(img, x, y, fraction, colour, ox, oy):
    lit = math.ceil(fraction * 5 - 0.01)
    for i in range(5):
        gfill(img, x + i * 10, y, x + i * 10 + 9, y + 4, colour if i < lit else 0xFF2A3036, ox, oy)


def bar(img, x, y, signal, ox, oy):
    lit = round(signal * 12)
    for i in range(12):
        colour = 0xFF4CD964 if i < 6 else 0xFFFFD23F if i < 9 else 0xFFFF5A4C
        gfill(img, x + i * 5, y, x + i * 5 + 4, y + 5, colour if i < lit else 0xFF2A3036, ox, oy)


def scope(img, cx, cy, contacts, sweep, ox, oy):
    r = 22
    for dy in range(-r, r + 1):
        half = int(math.floor(math.sqrt(r * r - dy * dy + 0.5)))
        gfill(img, cx - half, cy + dy, cx + half + 1, cy + dy + 1, 0xFF0A1A10, ox, oy)
        gfill(img, cx - half, cy + dy, cx - half + 1, cy + dy + 1, 0xFF2F7A45, ox, oy)
        gfill(img, cx + half, cy + dy, cx + half + 1, cy + dy + 1, 0xFF2F7A45, ox, oy)
    gfill(img, cx - r, cy, cx + r + 1, cy + 1, 0x302F7A45, ox, oy)
    gfill(img, cx, cy - r, cx + 1, cy + r + 1, 0x302F7A45, ox, oy)
    inner = r // 2
    for i in range(24):
        a = i * math.pi / 12
        px, py = cx + round(math.sin(a) * inner), cy - round(math.cos(a) * inner)
        gfill(img, px, py, px + 1, py + 1, 0x602F7A45, ox, oy)
    for k in range(4, -1, -1):
        a = math.radians(sweep - k * 5)
        alpha = 0xFF if k == 0 else 0x70 - k * 0x14
        for t in range(1, r):
            px, py = cx + round(math.sin(a) * t), cy - round(math.cos(a) * t)
            gfill(img, px, py, px + 1, py + 1, alpha << 24 | 0x5CFF7A, ox, oy)
    for bearing, dist, rgb, glow in contacts:
        a = math.radians(bearing)
        d = min(1.0, dist / 128) * (r - 2)
        px, py = cx + round(math.sin(a) * d), cy - round(math.cos(a) * d)
        gfill(img, px - 1, py - 1, px + 2, py + 2, int((0.3 + 0.7 * glow) * 255) << 24 | rgb, ox, oy)
    gfill(img, cx, cy - 1, cx + 1, cy + 2, 0xFFFFFFFF, ox, oy)
    gfill(img, cx - 1, cy + 1, cx + 2, cy + 2, 0xFFFFFFFF, ox, oy)


def panel(img, font, ox, oy, gui_w, gui_h, icon, sprite_frame, title, accent, lines, graphic=None):
    """Draws a panel the way DetectorHud.draw does, in a gui_w x gui_h GUI-pixel scene at (ox, oy)."""
    width = 20 + font.width(title)
    for text, _, _ in lines:
        width = max(width, font.width(text))
    gw, gh = (graphic[0], graphic[1]) if graphic else (0, 0)
    width = max(width, gw) + PAD * 2
    height = PAD + HEADER + len(lines) * LINE + (gh + 3 if graphic else 0) + PAD - 1
    x = min(gui_w // 2 + 91 + 8, gui_w - width - 2)
    y = gui_h - height - 2
    gfill(img, x, y, x + width, y + height, BACKGROUND, ox, oy)
    for (a, b, c, d) in ((x, y, x + width, y + 1), (x, y + height - 1, x + width, y + height), (x, y + 1, x + 1, y + height - 1),
                         (x + width - 1, y + 1, x + width, y + height - 1)):
        gfill(img, a, b, c, d, BORDER, ox, oy)
    gfill(img, x + 1, y + 1, x + width - 1, y + 2, accent, ox, oy)
    blit(img, icon, ox + (x + PAD - 1) * GUI, oy + (y + PAD) * GUI, GUI, sprite_frame)
    font_draw(img, font, title, x + PAD + 18, y + PAD + 5, accent, ox, oy)
    gfill(img, x + PAD, y + PAD + HEADER, x + width - PAD, y + PAD + HEADER + 1, DIVIDER, ox, oy)
    ly = y + PAD + HEADER + 3
    for text, colour, italic in lines:
        font_draw(img, font, text, x + PAD, ly, colour, ox, oy, italic)
        ly += LINE
    if graphic:
        graphic[2](img, x + PAD, ly, ox, oy)


def font_draw(img, font, text, x, y, colour, ox, oy, italic=False):
    rgb, _ = argb(colour)
    for shadow in (True, False):
        col = (rgb[0] // 4, rgb[1] // 4, rgb[2] // 4) if shadow else rgb
        cx = x + (1 if shadow else 0)
        cy = y + (1 if shadow else 0)
        for ch in text:
            pixels, adv = font.glyphs.get(ch, font.glyphs["?"])
            for px, py in pixels:
                slant = (7 - py) // 4 if italic else 0
                img.fill(ox + (cx + px + slant) * GUI, oy + (cy + py) * GUI, ox + (cx + px + slant + 1) * GUI, oy + (cy + py + 1) * GUI, col)
            cx += adv


def scene(img, ox, oy, w, h, vanilla, slot_sprite, frame):
    """A dim world backdrop and the vanilla hotbar, the gadget in the selected first slot."""
    dirt = vanilla("block/coarse_dirt")
    grass = vanilla("block/moss_block")
    for y in range(h):
        for x in range(w):
            tex = grass if y < h - 40 else dirt
            p = tex.get((x // 2) % 16, (y // 2) % 16)
            shade = 0.45
            img.fill(ox + x * GUI, oy + y * GUI, ox + (x + 1) * GUI, oy + (y + 1) * GUI, tuple(int(c * shade) for c in p[:3]))
    hotbar = vanilla("gui/sprites/hud/hotbar")
    hx, hy = w // 2 - 91, h - 22
    for y in range(hotbar.h):
        for x in range(hotbar.w):
            p = hotbar.get(x, y)
            if p[3]:
                img.fill(ox + (hx + x) * GUI, oy + (hy + y) * GUI, ox + (hx + x + 1) * GUI, oy + (hy + y + 1) * GUI, p[:3], p[3])
    sel = vanilla("gui/sprites/hud/hotbar_selection")
    for y in range(sel.h):
        for x in range(sel.w):
            p = sel.get(x, y)
            if p[3]:
                img.fill(ox + (hx - 1 + x) * GUI, oy + (hy - 1 + y) * GUI, ox + (hx + x) * GUI, oy + (hy + y) * GUI, p[:3], p[3])
    blit(img, slot_sprite, ox + (hx + 3) * GUI, oy + (hy + 3) * GUI, GUI, frame)


def render(path, sprites, lang, vanilla, Canvas):
    font = Font(vanilla)

    def fmt(key, *args):
        s = lang["detector.mymod." + key]
        return (s % args) if args else s.replace("%%", "%")

    # Sprite sheet.
    label_h, tile = 12, 16 * SCALE + 8
    sheet_w = 1500
    sheet_h = len(ROWS) * (tile + label_h * SCALE // 3 + 14) + 40
    mock_w, mock_h = 480, 100
    mocks = 8
    cols = 1
    total_h = sheet_h + (mocks + cols - 1) // cols * (mock_h * GUI + 10) + 40
    img = Image(sheet_w, total_h, (24, 26, 30))
    y = 16
    for title, names, refs in ROWS:
        font.draw(img, title, 10, y // 2, 0xFFE6E9EC, 2)
        y += 26
        x = 20
        for name in names:
            frame = 0
            if isinstance(name, tuple):
                name, frame = name
            img.fill(x - 4, y - 4, x + 16 * SCALE + 4, y + 16 * SCALE + 4, (139, 139, 139))
            blit(img, sprites[name], x, y, SCALE, frame)
            x += tile
        x += 30
        for ref in refs:
            img.fill(x - 4, y - 4, x + 16 * SCALE + 4, y + 16 * SCALE + 4, (90, 90, 96))
            blit(img, vanilla(ref), x, y, SCALE)
            x += tile
        y += tile + 18

    # HUD mockups.
    y0 = sheet_h + 10
    scenes = []

    def add(sprite, frame, title, accent, lines, graphic=None):
        scenes.append((sprite, frame, title, accent, lines, graphic))

    add("prospectors_pendulum_5", 0, "Prospector's Pendulum", 0xFFE0A066,
        [(fmt("pendulum.tuned", "Silver"), 0xFFD8E4EC, False), (fmt("pendulum.pull_below", "↗", "Strong pull"), TEXT, False)],
        (49, 4, lambda img, x, y, ox, oy: pips(img, x, y, 0.8, 0xFFD8E4EC, ox, oy)))
    add("metal_detector_2", 0, "Metal Detector", 0xFF7FD06A,
        [(fmt("metal.signal", 62).replace("%%", "%"), WARN, False), (fmt("metal.find", "A cache", fmt("metal.below", 3)), TEXT, False)],
        (59, 5, lambda img, x, y, ox, oy: bar(img, x, y, 0.62, ox, oy)))
    add("meteorite_scanner_1", 0, "Meteorite Scanner", 0xFF5CE1E6,
        [(fmt("scanner.site", "↗", 312), TEXT, False), (fmt("scanner.meteor", 140, "NE"), BAD, False)])
    add("dowsing_rod_right", 0, "Dowsing Rod", 0xFFB38EF3,
        [(fmt("rod.tug", "→", "Steady tug"), TEXT, False), (fmt("rod.asleep"), DIM, False)])
    add("fae_dowsing_rod_ahead", 0, "Fae Dowsing Rod", 0xFFF08CD8,
        [(fmt("rod.ring", "↑", 214), TEXT, False), (fmt("rod.awake"), 0xFF7FE7FF, False)])
    add("ufo_radar_alert", 2, "UFO Radar", 0xFF5CFF7A, [(fmt("radar.abducting", 64, "NE"), BAD, False)],
        (45, 45, lambda img, x, y, ox, oy: scope(img, x + 22, y + 22, [(40, 64, 0xFF4A4A, 0.9), (250, 110, 0x9CFFB0, 0.3)], 52, ox, oy)))
    add("lunar_almanac_7", 0, "Lunar Almanac", 0xFFBFD4FF,
        [(fmt("moon.waxing_gibbous"), 0xFFBFD4FF, False), (fmt("moon.full_in_one"), WARN, False), (fmt("almanac.omen"), OMEN, True)])
    add("ledger", 0, "Ledger", 0xFF3DDC84,
        [(fmt("ledger.vault", 128), GOOD, False), (fmt("ledger.cards", 340, 3500), DIM, False), (fmt("ledger.owed", 340), WARN, False),
         (fmt("ledger.due", "2d 5h"), TEXT, False)])
    for i, (sprite, frame, title, accent, lines, graphic) in enumerate(scenes):
        ox = 10 + (i % cols) * (sheet_w // cols)
        oy = y0 + (i // cols) * (mock_h * GUI + 10)
        w = min(mock_w, (sheet_w // cols - 20) // GUI)
        scene(img, ox, oy, w, mock_h, vanilla, sprites[sprite], frame)
        panel(img, font, ox, oy, w, mock_h, sprites[sprite], frame, title, accent, lines, graphic)
    img.save(path)
