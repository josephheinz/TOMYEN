"""Renders a preview sheet of the Trombone from the generated model files: from the side and three-quarters (slide in and
out), its inventory icon, first person with the slide in and fully out (with the arms where TromboneHands puts them),
a player holding it in third person (head-anchored, arms aimed as HumanoidTrombonePoseMixin aims them, slide in and out), and a
mockup of the HUD staff as TromboneHud lays it out, in vanilla's font.

Usage: python3 tools/preview_trombone.py src/main/resources/assets/tomyen out.png"""
import json, math, os, sys, zipfile

sys.path.insert(0, os.path.dirname(__file__))
ASSETS, OUT = sys.argv[1], sys.argv[2]
sys.argv = [sys.argv[0]]
from gunkit import apply, display_matrix, mul, rx, ry, rz, set_rotation, IDENTITY
from gunsmith_kit import render, ortho, first_person, gui_icon, compose, SIDE, RIGHT_FRONT, TILT
from gen_longswords import client_jar, read_png

MODELS = os.path.join(ASSETS, "models/item")
TEX = os.path.join(ASSETS, "textures")
FRAMES = 15
EXTEND = 11.0
JAR = zipfile.ZipFile(client_jar())

ATLAS = {}


def load(name):
    m = json.load(open(os.path.join(MODELS, name + ".json")))
    atlases = {}
    for key, ref in m["textures"].items():
        path = os.path.join(TEX, ref.split(":")[1] + ".png")
        if path not in ATLAS:
            w, h, px = read_png(open(path, "rb").read())
            ATLAS[path] = (px, w)
        atlases["#" + key] = ATLAS[path]
    return m["elements"], atlases, m.get("display")


BODY, ATL, DISPLAYS = load("trombone")
SLIDES = [load(f"trombone_slide_{f}")[0] for f in range(FRAMES)]


def trombone(frame):
    return BODY + SLIDES[frame]


# ------------------------------------------------------------------------------------------------- flat-colour boxes
FLAT = {}


def flat(rgb):
    key = "#flat%06x" % rgb
    FLAT[key] = ([((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)], 1)
    return key


def box(x0, y0, z0, x1, y1, z1, rgb, m=None, origin=None):
    tex = flat(rgb)
    e = {"from": [x0, y0, z0], "to": [x1, y1, z1],
         "faces": {f: {"uv": [0, 0, 16, 16], "texture": tex} for f in ("north", "south", "east", "west", "up", "down")}}
    if m is not None:
        set_rotation(e, m, origin)
    return e


def atlases():
    a = dict(ATL)
    a.update(FLAT)
    return a


def basis_to(d):
    """A rotation taking +y to the unit vector d."""
    n = math.sqrt(sum(c * c for c in d))
    d = [v / n for v in d]
    helper = [1.0, 0.0, 0.0] if abs(d[0]) < 0.9 else [0.0, 0.0, 1.0]
    x = [helper[1] * d[2] - helper[2] * d[1], helper[2] * d[0] - helper[0] * d[2], helper[0] * d[1] - helper[1] * d[0]]
    n = math.sqrt(sum(c * c for c in x))
    x = [c / n for c in x]
    z = [x[1] * d[2] - x[2] * d[1], x[2] * d[0] - x[0] * d[2], x[0] * d[1] - x[1] * d[0]]
    return [[x[i], d[i], z[i]] for i in range(3)]


SKIN, SLEEVE, SLEEVE_DARK = 0xC8996F, 0x2FA7A0, 0x24807A


# ------------------------------------------------------------------------------------------------------ first person
# TromboneHands' anchors (item space, blocks) and GunHands' shoulders (view space).
FP_ARM = (0.56, -0.52, -0.72)
MAIN_SHOULDER = (0.42, -0.78, -0.12)
OFF_SHOULDER = (-0.40, -0.80, -0.30)


def fp_space(p):
    t = DISPLAYS["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [(t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16 + FP_ARM[i] for i in range(3)]


GRIP = (5.6, 6.8, 14.6)
SLIDE_HAND = (8.55, 6.8, 10.4)


def arm_elements(shoulder, hand):
    """An arm (in view-space pixels) from the shoulder to just past the hand: sleeve, then the bare hand."""
    s = [v * 16 for v in shoulder]
    h = [v * 16 for v in hand]
    d = [h[i] - s[i] for i in range(3)]
    length = math.sqrt(sum(c * c for c in d)) + 1.2
    m = basis_to(d)
    out = [box(s[0] - 1, s[1], s[2] - 1, s[0] + 1, s[1] + length - 2.4, s[2] + 1, SLEEVE, m, s),
           box(s[0] - 1, s[1] + length - 2.4, s[2] - 1, s[0] + 1, s[1] + length, s[2] + 1, SKIN, m, s)]
    return out


def first_person_view(frame, w=480, h=270):
    e = EXTEND * frame / (FRAMES - 1)
    project, turn = first_person(DISPLAYS, w, h)
    img = [None] * (w * h)
    depth = [-1e9] * (w * h)
    # The near plane: parts reaching back past the camera (the tuning slide, behind the head) are clipped in game.
    near = [e for e in trombone(frame) if max(project(c)[2] for c in (e["from"], e["to"])) < -0.05]
    render(near, atlases(), project, turn, w, h, img, depth)
    f = (h / 2) / math.tan(math.radians(35))

    def view_project(p):
        x, y, z = (v / 16 for v in p)
        return (w / 2 + x / -z * f, h / 2 - y / -z * f, z)
    grip = fp_space(GRIP)
    slide = fp_space((SLIDE_HAND[0], SLIDE_HAND[1], SLIDE_HAND[2] - e))
    arms = arm_elements(OFF_SHOULDER, grip) + arm_elements(MAIN_SHOULDER, slide)
    render(arms, atlases(), view_project, lambda n: n, w, h, img, depth)
    return [(110, 130, 160) if v is None else v for v in img], w, h, 1


# ------------------------------------------------------------------------------------------------------ third person
def rzyx(z, y, x):
    return mul(rz(math.degrees(z)), mul(ry(math.degrees(y)), rx(math.degrees(x))))


def tp_space(p):
    t = DISPLAYS["thirdperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    return [(t["translation"][i] + sum(m[i][k] * (p[k] - 8) for k in range(3)) * s) / 16 for i in range(3)]


def to_world(m):
    """Entity model space (blocks, y down) to the preview's world (x the player's right, y up, -z ahead)."""
    return [-m[0], 1.501 - m[1], m[2]]


def player_scene(frame, pitch_deg, cam, w=360, h=380):
    e = EXTEND * frame / (FRAMES - 1)
    head_rot = rzyx(0.0, 0.0, math.radians(pitch_deg))
    head_pivot = [0.0, 0.0, 0.0]

    def head_point(q):
        """C space (PlayerTromboneHeldMixin) to entity model space, blocks."""
        local = [-q[0], -0.25 - q[1], q[2]]
        r = apply(head_rot, local)
        return [head_pivot[i] / 16 + r[i] for i in range(3)]

    # Arms aimed at the anchors (HumanoidTrombonePoseMixin).
    def aim(shoulder, target_blocks):
        t = [v * 16 for v in target_blocks]
        d = [t[i] - shoulder[i] for i in range(3)]
        n = math.sqrt(sum(c * c for c in d))
        d = [c / n for c in d]
        sx = -math.sqrt(max(0.0, 1 - d[1] ** 2))
        xr = math.atan2(sx, d[1])
        yr = math.atan2(d[0] / sx, d[2] / sx) if abs(sx) > 1e-6 else 0.0
        return xr, yr, max(0.85, min(1.35, n / 9.5))

    r_shoulder, l_shoulder = [-5.0, 2.0, 0.0], [5.0, 2.0, 0.0]
    grip = head_point(tp_space(GRIP))
    slide = head_point(tp_space((SLIDE_HAND[0], SLIDE_HAND[1], SLIDE_HAND[2] - e)))
    rx_, ry_, rs = aim(r_shoulder, slide)
    lx_, ly_, ls = aim(l_shoulder, grip)

    view_k = 190.0
    centre = [0.0, 1.25, -0.25]

    def cam_project(wp):
        v = apply(cam, [wp[i] - centre[i] for i in range(3)])
        return (w / 2 + v[0] * view_k, h / 2 - v[1] * view_k, v[2])

    img = [None] * (w * h)
    depth = [-1e9] * (w * h)

    def part(elements, pivot, rot, scale=(1, 1, 1)):
        def project(p):
            q = [p[i] * scale[i] for i in range(3)]
            r = apply(rot, q)
            m = [(pivot[i] + r[i]) / 16 for i in range(3)]
            return cam_project(to_world(m))

        def turn(n):
            r = apply(rot, n)
            return apply(cam, [-r[0], -r[1], r[2]])
        render(elements, atlases(), project, turn, w, h, img, depth)

    # Body parts in model pixels, y down.
    part([box(-4, 0, -2, 4, 12, 2, SLEEVE)], [0, 0, 0], IDENTITY)
    part([box(-2, 0, -2, 2, 12, 2, 0x3B3F9C), box(-2.05, 10, -2.05, 2.05, 12.05, 2.05, 0x4A4A4A)], [-1.9, 12, 0], IDENTITY)
    part([box(-2, 0, -2, 2, 12, 2, 0x3B3F9C), box(-2.05, 10, -2.05, 2.05, 12.05, 2.05, 0x4A4A4A)], [1.9, 12, 0], IDENTITY)
    part([box(-4, -8, -4, 4, 0, 4, SKIN), box(-4.1, -8.1, -4.1, 4.1, -6.5, 4.1, 0x4B3222),
          box(-3, -4.5, -4.12, -1, -3.5, -4.0, 0xF5F5F5), box(1, -4.5, -4.12, 3, -3.5, -4.0, 0xF5F5F5),
          box(-2, -4.5, -4.14, -1, -3.5, -4.0, 0x4E5BC4), box(1, -4.5, -4.14, 2, -3.5, -4.0, 0x4E5BC4)],
         [0, 0, 0], head_rot)
    part([box(-3, -2, -2, 1, 7, 2, SLEEVE), box(-3.02, 7, -2.02, 1.02, 10, 2.02, SKIN)], r_shoulder, rzyx(0, ry_, rx_), (1, rs, 1))
    part([box(-1, -2, -2, 3, 7, 2, SLEEVE), box(-1.02, 7, -2.02, 3.02, 10, 2.02, SKIN)], l_shoulder, rzyx(0, ly_, lx_), (1, ls, 1))

    # The trombone: item pixels -> C space -> head -> model -> world.
    def tb_project(p):
        return cam_project(to_world(head_point(tp_space(p))))

    tdm = display_matrix(DISPLAYS["thirdperson_righthand"]["rotation"])

    def tb_turn(n):
        q = apply(tdm, n)
        r = apply(head_rot, [-q[0], -q[1], q[2]])
        return apply(cam, [-r[0], -r[1], r[2]])
    render(trombone(frame), atlases(), tb_project, tb_turn, w, h, img, depth)
    print(f"   third person frame {frame}: right arm x {math.degrees(rx_):.0f} y {math.degrees(ry_):.0f} s {rs:.2f}; "
          f"left arm x {math.degrees(lx_):.0f} y {math.degrees(ly_):.0f} s {ls:.2f}")
    return [(120, 150, 185) if v is None else v for v in img], w, h, 1


# --------------------------------------------------------------------------------------------------------------- HUD
GUI = 3


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


def argb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255), (c >> 24) & 255


def vanilla(name):
    return read_png(JAR.read(f"assets/minecraft/textures/{name}.png"))


class Font:
    def __init__(self):
        w, h, px = vanilla("font/ascii")
        self.cell = w // 16
        self.glyphs = {}
        for code in range(32, 127):
            gx, gy = code % 16 * self.cell, code // 16 * self.cell
            pixels = [(x, y) for y in range(self.cell) for x in range(self.cell) if px[(gy + y) * w + gx + x][3]]
            width = max((x for x, _ in pixels), default=-1) + 1
            self.glyphs[chr(code)] = (pixels, width + 1 if pixels else 4)

    def width(self, text):
        return sum(self.glyphs[ch][1] for ch in text) - (1 if text else 0)

    def draw(self, g, text, x, y, colour, shadow=True):
        rgb, _ = argb(colour)
        if shadow:
            self.draw(g, text, x + 1, y + 1, 0xFF000000 | (rgb[0] // 4) << 16 | (rgb[1] // 4) << 8 | rgb[2] // 4, False)
        cx = x
        for ch in text:
            pixels, adv = self.glyphs[ch]
            for px, py in pixels:
                g.fill(cx + px, y + py, cx + px + 1, y + py + 1, colour)
            cx += adv


class Gui:
    """Draws in GUI pixels onto an Image GUI times bigger, like GuiGraphics.fill."""

    def __init__(self, img, ox=0, oy=0):
        self.img, self.ox, self.oy = img, ox, oy

    def fill(self, x0, y0, x1, y1, colour):
        rgb, a = argb(colour)
        self.img.fill(self.ox + x0 * GUI, self.oy + y0 * GUI, self.ox + x1 * GUI, self.oy + y1 * GUI, rgb, a)

    def sprite(self, w, h, px, x, y, k=1):
        for ty in range(h):
            for tx in range(w):
                p = px[ty * w + tx]
                if p[3] > 0:
                    self.img.fill(self.ox + (x * GUI) + tx * k, self.oy + (y * GUI) + ty * k,
                                  self.ox + (x * GUI) + (tx + 1) * k, self.oy + (y * GUI) + (ty + 1) * k, p[:3], p[3])


# TromboneHud's layout and colours.
BACKGROUND, ACCENT, TEXT, DIM, LINE = 0xD0101418, 0xFFE2B85A, 0xFFE6E9EC, 0xFF8F99A3, 0x60E6E9EC
PANEL_W, PANEL_H = 118, 56
STAFF_X, NOTE_STEP = 22, 13
SONGS = [("Sad Trombone", 0xFF6FA8FF), ("War March", 0xFFFF6A4D), ("Lullaby", 0xFFC9A0FF), ("Disco", 0xFFFF7FE0)]
NAMES = ["Bb2", "C3", "D3", "Eb3", "F3", "G3", "A3", "Bb3", "C4", "D4", "Eb4", "F4", "G4", "A4", "Bb4"]


def degree_y(d):
    return 43 - 2 * d


def note_head(g, x, y, colour, hollow):
    g.fill(x + 1, y - 2, x + 4, y - 1, colour)
    g.fill(x, y - 1, x + 5, y + 1, colour)
    g.fill(x + 1, y + 1, x + 4, y + 2, colour)
    if hollow:
        g.fill(x + 1, y - 1, x + 4, y + 1, 0xFF101418)


def staff(g, font, notes, ghost, title, title_colour, cooldowns, flash, icon):
    """notes: (degree, long, colour); ghost: degree or None; cooldowns: 0..1 left per song; flash: song index lit."""
    g.fill(0, 0, PANEL_W, PANEL_H, BACKGROUND)
    g.fill(1, 1, PANEL_W - 1, 2, ACCENT)
    font.draw(g, title, 4, 5, title_colour)
    if ghost is not None:
        name = NAMES[ghost]
        font.draw(g, name, PANEL_W - 4 - font.width(name), 5, TEXT)
    g.sprite(*icon, 3, 24, GUI)
    for d in (3, 5, 7, 9, 11):
        g.fill(STAFF_X - 2, degree_y(d), PANEL_W - 4, degree_y(d) + 1, LINE)
    slots = list(notes) + ([(ghost, False, None)] if ghost is not None else [])
    for i, (d, long, colour) in enumerate(slots):
        x = STAFF_X + i * NOTE_STEP
        y = degree_y(d)
        c = colour if colour is not None else 0xA0FFE29A
        for ledger in (1, 13):
            if (d <= 1 and ledger == 1) or (d >= 13 and ledger == 13):
                g.fill(x - 2, degree_y(ledger), x + 7, degree_y(ledger) + 1, LINE)
        note_head(g, x, y, c, long)
        if d < 7:
            g.fill(x + 4, y - 9, x + 5, y, c)
        else:
            g.fill(x, y + 1, x + 1, y + 10, c)
    for i, (_, colour) in enumerate(SONGS):
        x0 = 4 + i * 28
        lit = flash == i
        rgb = colour & 0xFFFFFF
        g.fill(x0, PANEL_H - 6, x0 + 26, PANEL_H - 3, (0xFF << 24 | rgb) if lit else (0x50 << 24 | rgb))
        if cooldowns[i] > 0:
            g.fill(x0, PANEL_H - 6, x0 + int(26 * cooldowns[i]), PANEL_H - 3, 0xB0101418)


def hud_mock(font):
    """Two states over a vanilla hotbar: mid-phrase while playing, and just after the Sad Trombone matched."""
    w, h = 330, 76
    hotbar = vanilla("gui/sprites/hud/hotbar")
    sel = vanilla("gui/sprites/hud/hotbar_selection")
    icon_frame = 0
    icon = gui_icon(DISPLAYS, 16)
    proj, turn = icon
    pic = render(trombone(4), atlases(), proj, turn, 16, 16)
    icon_px = (16, 16, [(0, 0, 0, 0) if p is None else tuple(p) + (255,) for p in pic])
    cells = []
    for state in range(2):
        img = Image(w * GUI, h * GUI, (54, 74, 44))
        g = Gui(img)
        g.fill(0, 0, w, h - 26, 0xFF6A8CB8)
        hx = 6
        g.sprite(hotbar[0], hotbar[1], hotbar[2], hx, h - 23, GUI)
        g.sprite(sel[0], sel[1], sel[2], hx - 1, h - 24, GUI)
        g.sprite(icon_px[0], icon_px[1], icon_px[2], hx + 3, h - 20, GUI)
        p = Gui(img, (hx + 182 + 6) * GUI, (h - PANEL_H - 3) * GUI)
        white = 0xFFE6E9EC
        if state == 0:
            staff(p, font, [(9, False, white), (11, False, white), (7, True, white)], 4, "Trombone", ACCENT,
                  [0.0, 0.55, 0.0, 0.0], None, icon_px)
        else:
            blue = SONGS[0][1]
            staff(p, font, [(10, False, blue), (9, False, blue), (8, False, blue), (7, True, blue)], None,
                  "Sad Trombone!", blue, [1.0, 0.55, 0.0, 0.0], 0, icon_px)
        cells.append(([tuple(px) for px in img.px], w * GUI, h * GUI, 1))
    return cells


# -------------------------------------------------------------------------------------------------------------- sheet
def views():
    rows = []
    for frames_, view, k in (((0, 14), SIDE, 13.0), ((7, 14), RIGHT_FRONT, 12.0)):
        row = []
        for f in frames_:
            w, h = 560, 260
            centre = [5.0, 8.5, 7.0]
            row.append(([(58, 60, 66) if v is None else v for v in render(trombone(f), atlases(), ortho(view, k, w, h, centre),
                                                                         lambda n: n, w, h)], w, h, 1))
        rows.append(row)
    proj, turn = gui_icon(DISPLAYS, 64)
    icon = render(trombone(4), atlases(), proj, turn, 64, 64)
    rows[0].append(([(139, 139, 139) if v is None else v for v in icon], 64, 64, 2))
    rows.append([first_person_view(0), first_person_view(7), first_person_view(14)])
    from gunsmith_kit import LEFT_REAR
    w, h = 420, 300
    rows.append([([(58, 60, 66) if v is None else v for v in render(trombone(3), atlases(), ortho(view, k, w, h, c), lambda n: n, w, h)],
                  w, h, 1) for view, k, c in ((RIGHT_FRONT, 34.0, [3.0, 8.8, -1.0]), (LEFT_REAR, 30.0, [5.5, 7.5, 18.0]),
                                              (mul(TILT, ry(-160)), 30.0, [8.0, 7.0, -4.0]))])
    front34 = mul(TILT, ry(180 + 40))
    side = mul(TILT, ry(90))
    rows.append([player_scene(0, -8, front34), player_scene(14, 10, front34), player_scene(7, 0, side),
                 player_scene(7, -25, mul(TILT, ry(-30)))])
    rows.append(hud_mock(Font()))
    return rows


compose(views(), OUT)
print("preview:", OUT)
