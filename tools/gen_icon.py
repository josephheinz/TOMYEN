"""The mod icon: TOM stacked on YEN in big block letters, with a flaming meteor and a UFO behind them over a grass block.

Drawn on a 64x64 pixel grid and scaled up with nearest-neighbour, so it reads like Minecraft art. Writes the 128x128 icon
the game and Mod Menu use, plus a 512x512 copy in dist/ for the CurseForge project avatar.

Run from the repo root: python3 tools/gen_icon.py
"""
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..")
N = 64
CLEAR = (0, 0, 0, 0)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


# Sky in flat bands, darkest at the top.
SKY = [hexc(c) for c in ("#140c2a", "#1b1036", "#221443", "#2a1850", "#33205e", "#3d276b")]
STAR, STAR_DIM = hexc("#fff6d8"), hexc("#9b8cc8")
FIRE_CORE, FIRE_MID, FIRE_OUT, FIRE_EDGE = hexc("#fff4a8"), hexc("#ffb83a"), hexc("#f2672a"), hexc("#b8302a")
ROCK_DARK, ROCK, ROCK_LIT = hexc("#2e2420"), hexc("#4e3c32"), hexc("#6e5644")
LAVA = hexc("#ff8a2a")
UFO_DOME, UFO_DOME_LIT, UFO_DOME_DARK = hexc("#6fd8f0"), hexc("#d4f8ff"), hexc("#3a9cbc")
UFO_TOP, UFO_RIM, UFO_UNDER, UFO_LINE = hexc("#c8cfda"), hexc("#9aa3b2"), hexc("#5e6676"), hexc("#3a404c")
UFO_LIGHTS = [hexc("#ffe14a"), hexc("#ff5a4a")]
GRASS_LIT, GRASS, GRASS_DARK = hexc("#8fd14f"), hexc("#6aa83a"), hexc("#4a8a2a")
DIRT, DIRT_DARK, DIRT_LIT = hexc("#8a5a3a"), hexc("#6e452c"), hexc("#a06b46")
TEXT_TOP, TEXT_MID, TEXT_LOW, TEXT_BOTTOM = hexc("#fff3a0"), hexc("#ffcc3a"), hexc("#ff9a2a"), hexc("#f0682a")
OUTLINE, SHADOW = hexc("#1a0e14"), hexc("#3a2218")

GROUND_Y = 54

FONT = {
    "T": ["#######", "#######", "..###..", "..###..", "..###..", "..###..", "..###..", "..###..", "..###.."],
    "O": [".#####.", "#######", "##...##", "##...##", "##...##", "##...##", "##...##", "#######", ".#####."],
    "M": ["##...##", "###.###", "#######", "##.#.##", "##...##", "##...##", "##...##", "##...##", "##...##"],
    "Y": ["##...##", "##...##", "##...##", ".##.##.", "..###..", "..###..", "..###..", "..###..", "..###.."],
    "E": ["#######", "#######", "##.....", "##.....", "######.", "##.....", "##.....", "#######", "#######"],
    "N": ["##...##", "###..##", "###..##", "####.##", "##.####", "##..###", "##..###", "##...##", "##...##"],
}


def blank():
    return [[CLEAR] * N for _ in range(N)]


def sky(img):
    for y in range(GROUND_Y):
        band = SKY[min(len(SKY) - 1, y * len(SKY) // GROUND_Y)]
        for x in range(N):
            img[y][x] = band
    for x, y in ((3, 22), (60, 50), (26, 9), (60, 22), (3, 37), (36, 7), (50, 52), (25, 52), (3, 3), (44, 2)):
        img[y][x] = STAR_DIM
    for x, y in ((31, 4), (58, 30), (5, 30), (59, 15)):
        img[y][x] = STAR
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            img[y + dy][x + dx] = STAR_DIM


def meteor(img):
    hx, hy, head = 44.5, 7.5, 3.6
    dx, dy = 1 / math.sqrt(2), -1 / math.sqrt(2)
    length = 26.0
    for y in range(GROUND_Y):
        for x in range(N):
            px, py = x + 0.5 - hx, y + 0.5 - hy
            t = px * dx + py * dy                   # distance back along the trail
            d = abs(px * -dy + py * dx)              # distance off the trail's centre line
            if t < -head - 1.5 or t > length:
                continue
            width = (head + 1.8) * (1 - max(0.0, t) / length) if t >= 0 else math.sqrt(max(0.0, (head + 1.8) ** 2 - t * t))
            if width <= 0 or d > width:
                continue
            r = d / width
            img[y][x] = FIRE_CORE if r < 0.3 else FIRE_MID if r < 0.58 else FIRE_OUT if r < 0.82 else FIRE_EDGE
    # The rock itself, lit from the fire behind it, with its leading edge glowing.
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5 - hx, y + 0.5 - hy
            dist = math.hypot(px, py)
            if dist > head:
                continue
            lead = -(px * dx + py * dy) / head       # 1 at the leading (lower left) edge
            if dist > head - 1.2 and lead > 0.35:
                img[y][x] = LAVA
            elif px * dx + py * dy > 1.5:
                img[y][x] = ROCK_LIT
            elif px * dx + py * dy < -1.5:
                img[y][x] = ROCK_DARK
            else:
                img[y][x] = ROCK
    for x, y in ((44, 7), (43, 8)):
        img[y][x] = LAVA


def ufo(img):
    cx, cy = 12, 6
    # Dome
    for y in range(cy - 5, cy + 1):
        for x in range(cx - 5, cx + 6):
            if math.hypot(x - cx, (y - cy) * 1.15) <= 4.8:
                img[y][x] = UFO_DOME_DARK if x >= cx + 2 else UFO_DOME
    img[cy - 4][cx - 2] = img[cy - 3][cx - 3] = img[cy - 4][cx - 1] = UFO_DOME_LIT
    # Saucer: a lit top, a rim of lights and a dark underside
    for x in range(cx - 9, cx + 10):
        half = 9 - abs(x - cx)
        if abs(x - cx) <= 7:
            img[cy + 1][x] = UFO_TOP
        img[cy + 2][x] = UFO_RIM
        if abs(x - cx) <= 7:
            img[cy + 3][x] = UFO_UNDER
        if abs(x - cx) <= 4:
            img[cy + 4][x] = UFO_LINE
        del half
    for i, x in enumerate(range(cx - 8, cx + 9, 3)):
        img[cy + 2][x] = UFO_LIGHTS[i % 2]
    img[cy + 1][cx - 8] = img[cy + 1][cx + 8] = UFO_RIM


def ground(img):
    for x in range(N):
        tuft = (x * 7) % 5 == 0
        img[GROUND_Y - 1][x] = GRASS_LIT if tuft else img[GROUND_Y - 1][x]
        img[GROUND_Y][x] = GRASS_LIT
        img[GROUND_Y + 1][x] = GRASS
        img[GROUND_Y + 2][x] = GRASS_DARK if x % 4 else GRASS
        img[GROUND_Y + 3][x] = GRASS_DARK if x % 3 == 0 else DIRT
        for y in range(GROUND_Y + 4, N):
            img[y][x] = DIRT
    # Even, deliberate dirt specks rather than noise.
    for x, y in ((3, 59), (12, 62), (20, 59), (33, 62), (41, 59), (50, 62), (58, 59), (27, 61), (46, 60)):
        img[y][x] = DIRT_DARK
        if x + 1 < N:
            img[y][x + 1] = DIRT_DARK
    for x, y in ((6, 61), (16, 60), (37, 61), (54, 61)):
        img[y][x] = DIRT_LIT


def text(img, lines=("TOM", "YEN"), scale=2, top=13, gap=3):
    """Each line in the 7x9 block font at double size, centred, with a dark outline, a drop shadow and a fiery fill."""
    height = 9 * scale
    mask = {}
    for n, word in enumerate(lines):
        width = len(word) * 7 * scale + (len(word) - 1) * 2
        left = (N - width) // 2
        y0 = top + n * (height + gap)
        for i, ch in enumerate(word):
            for row, line in enumerate(FONT[ch]):
                for col, c in enumerate(line):
                    if c != "#":
                        continue
                    for sy in range(scale):
                        for sx in range(scale):
                            mask[(left + i * (7 * scale + 2) + col * scale + sx, y0 + row * scale + sy)] = row * scale + sy
    for (x, y) in mask:
        for ox, oy in ((1, 1), (2, 2), (1, 2), (2, 1)):
            p = (x + ox, y + oy)
            if 0 <= p[0] < N and 0 <= p[1] < N and p not in mask:
                img[p[1]][p[0]] = SHADOW
    for (x, y) in mask:
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                p = (x + ox, y + oy)
                if p not in mask and 0 <= p[0] < N and 0 <= p[1] < N:
                    img[p[1]][p[0]] = OUTLINE
    for (x, y), row in mask.items():
        img[y][x] = TEXT_TOP if row < 4 else TEXT_MID if row < 9 else TEXT_LOW if row < 14 else TEXT_BOTTOM


def round_corners(img, r=5):
    for y in range(N):
        for x in range(N):
            cx = r if x < r else N - 1 - r if x > N - 1 - r else x
            cy = r if y < r else N - 1 - r if y > N - 1 - r else y
            if math.hypot(x - cx, y - cy) > r + 0.25:
                img[y][x] = CLEAR


def frame(img):
    """A one-pixel dark border just inside the rounded edge, so the icon holds its shape on light backgrounds."""
    out = [row[:] for row in img]
    for y in range(N):
        for x in range(N):
            if img[y][x][3] == 0:
                continue
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + ox, y + oy
                if not (0 <= nx < N and 0 <= ny < N) or img[ny][nx][3] == 0:
                    out[y][x] = OUTLINE
                    break
    return out


def write_png(path, img, scale):
    size = N * scale
    rows = []
    for y in range(size):
        line = img[y // scale]
        rows.append(b"\x00" + b"".join(struct.pack("BBBB", *line[x // scale]) for x in range(size)))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    img = blank()
    sky(img)
    meteor(img)
    ufo(img)
    ground(img)
    text(img)
    round_corners(img)
    img = frame(img)
    write_png(os.path.join(ROOT, "src", "main", "resources", "assets", "tomyen", "icon.png"), img, 2)
    write_png(os.path.join(ROOT, "dist", "tomyen-icon-512.png"), img, 8)
