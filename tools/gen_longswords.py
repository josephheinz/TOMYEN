"""Builds the eleven long swords: one flat texture each, made from that tier's sword the way the recipe makes the item,
by laying three swords end to end on the diagonal. The seven vanilla tiers' (wooden, stone, copper, iron, golden,
diamond, netherite) come from vanilla's swords, the mod metals' (magmasteel, soul steel, froststeel, voidsteel) from
the mod's own sword textures (tools/gen_soul_metals.py and tools/gen_frost_void_metals.py, run first).

The vanilla sword sprite (16x16) runs from the pommel at the lower left to the point at the upper right. Its blade is
a band five texels wide across the diagonal, every row of it the row below moved one texel up and right. The long
sword keeps the vanilla hilt (pommel, grip and crossguard) where it is, at the lower left of a 32x32 canvas, and
lays three copies of the blade along the same diagonal, each starting where the one before ends, the last reaching
the canvas's upper right corner. Only the last copy keeps the point: the others carry the straight blade on through
the texels where their point would be. Every texel is the sword's own, outline and shading included. A mod metal's
sword may carry accents off its blade (soul steel's wisps, froststeel's crystals of frost): those stay with the point,
on the last copy only.

The vanilla textures are read from the Minecraft client jar Loom has already downloaded (see client_jar()), so the
output follows whatever the jar has. The item model is a flat item/handheld one, like a vanilla sword's, used in
every display context. The 32x32 texture covers the same 16 model pixels a 16x16 one does, so the held transforms
are vanilla's at twice the scale: the hilt comes out the size of a vanilla sword's, in the same place in the hand,
and the blade three times as long.

Usage: python3 tools/gen_longswords.py src/main/resources/assets/tomyen [path/to/minecraft-client.jar]"""
import glob, os, struct, sys, zipfile, zlib
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import display, display_matrix, placed, png, write

ASSETS = sys.argv[1] if len(sys.argv) > 1 else None
TIERS = ["wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"]
MOD_TIERS = ["magmasteel", "soul_steel", "froststeel", "voidsteel"]
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ======================================================================================================= vanilla


def minecraft_version():
    with open(os.path.join(REPO, "gradle.properties")) as f:
        for line in f:
            if line.startswith("minecraft_version="):
                return line.split("=", 1)[1].strip()
    sys.exit("minecraft_version not found in gradle.properties")


def client_jar():
    """The Minecraft client jar for this project's version: the one named on the command line, else Loom's global
    cache (~/.gradle/caches/fabric-loom/<version>/minecraft-client.jar), else the client-only jar in the project's
    own Loom cache (or, in a git worktree under .claude/worktrees, the main checkout's). Run ./gradlew build once
    if none is there."""
    if len(sys.argv) > 2:
        return sys.argv[2]
    version = minecraft_version()
    roots = [REPO]
    if os.sep + os.path.join(".claude", "worktrees") + os.sep in REPO:
        roots.append(REPO.split(os.sep + os.path.join(".claude", "worktrees") + os.sep)[0])
    candidates = [os.path.expanduser(os.path.join("~", ".gradle", "caches", "fabric-loom", version, "minecraft-client.jar"))]
    for root in roots:
        candidates += sorted(glob.glob(os.path.join(root, ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft",
                                                    "minecraft-clientOnly-*", version, f"minecraft-clientOnly-*-{version}.jar")))
    for path in candidates:
        if os.path.isfile(path):
            with zipfile.ZipFile(path) as z:
                if "assets/minecraft/textures/item/iron_sword.png" in z.namelist():
                    return path
    sys.exit(f"no Minecraft {version} client jar found; run ./gradlew build first or pass the jar's path")


def read_png(data):
    """Pixels (RGBA tuples, row by row) of a non-interlaced PNG: greyscale, RGB, palette or RGBA, any bit depth up
    to 8 (vanilla's item textures are 4 and 8 bit palette ones), with a tRNS transparent colour if there is one."""
    pos, idat, palette, alpha = 8, b"", b"", b""
    while pos < len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if kind == b"IHDR":
            w, h, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            palette = body
        elif kind == b"tRNS":
            alpha = body
        elif kind == b"IDAT":
            idat += body
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    if interlace or depth > 8:
        sys.exit("unsupported PNG (interlaced or 16 bit)")
    step, stride = max(1, channels * depth // 8), (w * channels * depth + 7) // 8
    raw, prev, px = zlib.decompress(idat), bytearray(stride), []
    for y in range(h):
        kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a, b, c = line[i - step] if i >= step else 0, prev[i], prev[i - step] if i >= step else 0
            p = a + b - c
            paeth = a if abs(p - a) <= abs(p - b) and abs(p - a) <= abs(p - c) else b if abs(p - b) <= abs(p - c) else c
            line[i] = (line[i] + (0, a, b, (a + b) // 2, paeth)[kind]) & 255
        prev = line
        samples = [(line[bit // 8] >> (8 - depth - bit % 8)) & ((1 << depth) - 1) for bit in range(0, w * channels * depth, depth)]
        for x in range(w):
            s = samples[x * channels:(x + 1) * channels]
            if colour == 3:
                i = s[0]
                px.append(tuple(palette[3 * i:3 * i + 3]) + (alpha[i] if i < len(alpha) else 255,))
            elif colour in (0, 4):
                clear = colour == 0 and len(alpha) >= 2 and s[0] == struct.unpack(">H", alpha[:2])[0]
                px.append((s[0],) * 3 + ((s[1],) if colour == 4 else (0 if clear else 255,)))
            else:
                clear = colour == 2 and len(alpha) >= 6 and tuple(s) == struct.unpack(">HHH", alpha[:6])
                px.append(tuple(s) + ((0 if clear else 255,) if colour == 2 else ()))
    return w, h, px


# ======================================================================================================= texture

# Vanilla sword texels by p = x - y (along the sword, toward the point) and q = x + y (across it). The blade is the
# band q = 13..17 from p = -2 (the first texels clear of the crossguard) to the point; each diagonal step along it is
# 2 in p. Its texels at p > 12 shape the point (the canvas corner, a highlight down its middle).
BAND = range(13, 18)
BASE = -2
STRAIGHT = 12
STEP = 8         # texels right (and up) between one blade copy and the next: a vanilla blade's length, p 16
SEGMENTS = 3
SIZE = 16 + STEP * (SEGMENTS - 1)   # 32: the vanilla sprite's span, plus the two extra blades


def long_sword(px, accents=False):
    """The long sword's SIZE x SIZE texture from a sword's 16x16 pixels; with accents, texels past the crossguard off
    the blade are allowed and go with the point."""
    blade, hilt, off_blade = {}, {}, {}
    for y in range(16):
        for x in range(16):
            c = px[y * 16 + x]
            if c[3] == 0:
                continue
            p, q = x - y, x + y
            (blade if p >= BASE and q in BAND else off_blade if p >= BASE else hilt)[(x, y)] = c
            if p >= BASE and q not in BAND and not accents:
                sys.exit(f"vanilla sword texel ({x}, {y}) lies past the crossguard outside the blade: layout changed")
    # Every texel of a copied stretch must be there, or the copies would leave holes where they meet.
    for p in range(BASE, BASE + 2 * STEP):
        for q in BAND:
            if (p + q) % 2 == 0 and ((p + q) // 2, (q - p) // 2) not in blade:
                sys.exit(f"vanilla sword blade has no texel at p {p}, q {q}: layout changed")

    out = [(0, 0, 0, 0)] * (SIZE * SIZE)

    def put(x, y, c):
        # Vanilla coordinates, the sprite at the canvas's lower left: SIZE - 16 down.
        out[(y + SIZE - 16) * SIZE + x] = c

    for (x, y), c in hilt.items():
        put(x, y, c)
    for k in range(SEGMENTS):
        last = k == SEGMENTS - 1
        for (x, y), c in blade.items():
            p, q = x - y, x + y
            if not last:
                if p >= BASE + 2 * STEP:
                    continue  # the next copy's base goes here
                if p > STRAIGHT:
                    # Carry the straight blade on: the texel a whole number of diagonal steps further back.
                    back = p - 2 * ((p - STRAIGHT + 1) // 2)
                    c = blade[((back + q) // 2, (q - back) // 2)]
            put(x + STEP * k, y - STEP * k, c)
    for (x, y), c in off_blade.items():
        put(x + STEP * (SEGMENTS - 1), y - STEP * (SEGMENTS - 1), c)
    return out


# ======================================================================================================= displays

# Vanilla's item/handheld transforms. The long sword's model is the vanilla sword's halved about the model's origin
# (corner (0, 0)), since each texture texel covers half a model pixel, so twice the scale and a translation moved by
# that corner's offset put every hilt texel where vanilla puts it.
HANDHELD = {
    "thirdperson_righthand": ([0, -90, 55], [0, 4.0, 0.5], 0.85),
    "firstperson_righthand": ([0, -90, 25], [1.13, 3.2, 1.13], 0.68),
}
TEXEL = 16 / SIZE   # model pixels per texture texel
# The middle of a vanilla sword's grip: texel (3, 12), in vanilla model pixels and in the long sword's.
VANILLA_GRIP = (3.5, 16 - 12.5, 8)
GRIP = (3.5 * TEXEL, 16 - (12.5 + SIZE - 16) * TEXEL, 8)
# First person: vanilla's pose would lay the blade back across the camera (a sword's point leans toward the viewer),
# so the sword stands up and leans away instead, its flat turned toward the viewer. Found with fp_check() below: the
# grip sits where a vanilla sword's does (just under the lower right of the screen), the whole sword stays a block
# from the camera, the blade leaves the top of the screen three quarters of the way across, its point well past the
# edge, and passes no nearer the crosshair than a quarter of the screen's width.
FP_ROTATION = [-20, -40, 40]


def held(context):
    rot, tr, sc = HANDHELD[context]
    m = display_matrix(rot)
    target = [tr[i] + sum(m[i][j] * (VANILLA_GRIP[j] - 8) for j in range(3)) * sc for i in range(3)]
    mine = FP_ROTATION if context.startswith("firstperson") else rot
    scale = round(sc / TEXEL, 4)
    return display(mine, [v + 0.0 for v in placed(mine, scale, GRIP, target)], scale)


def displays():
    out = OrderedDict()
    for side in ("thirdperson", "firstperson"):
        # The game mirrors a left-hand transform itself (negating its x shift and its y and z turns: vanilla's
        # handheld.json lists the mirror image, which it mirrors back), so both hands take the same values.
        out[side + "_righthand"] = out[side + "_lefthand"] = held(side + "_righthand")
    # Dropped: half again a vanilla sword's size (the item entity stands it on the ground by its bounding box).
    out["ground"] = display([0, 0, 0], [0.0, 2.0, 0.0], 0.75)
    # The inventory, item frames and heads keep item/generated's: the whole sword fills the slot or the frame.
    return out


def fp_check(t, points):
    """Where model points (pixels) land in the first-person view for a display transform t: per point, its distance
    from the camera (blocks) and its screen position (-1..1 left to right and bottom to top, 16:9, 70 degrees)."""
    import math
    m, s = display_matrix(t["rotation"]), t["scale"][0]
    tan = math.tan(math.radians(35))
    res = []
    for v in points:
        local = [(v[i] / 16 - 0.5) * s for i in range(3)]
        pos = [(0.56, -0.52, -0.72)[i] + t["translation"][i] / 16 + sum(m[i][j] * local[j] for j in range(3)) for i in range(3)]
        dist = math.sqrt(sum(c * c for c in pos))
        res.append((dist, pos[0] / -pos[2] / tan / (16 / 9), pos[1] / -pos[2] / tan) if pos[2] < 0 else (dist, None, None))
    return res


# ========================================================================================================= write


def main():
    if not ASSETS:
        sys.exit(__doc__.rsplit("Usage: ", 1)[1])
    jar = client_jar()
    print("vanilla textures from", jar)
    ds = displays()
    with zipfile.ZipFile(jar) as z:
        for tier in TIERS + MOD_TIERS:
            name = f"{tier}_long_sword"
            if tier in MOD_TIERS:
                with open(os.path.join(ASSETS, "textures", "item", f"{tier}_sword.png"), "rb") as f:
                    w, h, px = read_png(f.read())
            else:
                w, h, px = read_png(z.read(f"assets/minecraft/textures/item/{tier}_sword.png"))
            if (w, h) != (16, 16):
                sys.exit(f"{tier}_sword.png is {w}x{h}, not 16x16")
            png(os.path.join(ASSETS, "textures", "item", name + ".png"), SIZE, SIZE, long_sword(px, accents=tier in MOD_TIERS))
            write(os.path.join(ASSETS, "models", "item", name + ".json"), OrderedDict([
                ("parent", "minecraft:item/handheld"),
                ("textures", OrderedDict(layer0=f"tomyen:item/{name}")),
                ("display", ds)]))
            write(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([
                ("type", "minecraft:model"), ("model", f"tomyen:item/{name}")])})
    for context, t in ds.items():
        print(f"{context}: rotation {t['rotation']} translation {t['translation']} scale {t['scale'][0]}")
    print("long swords done")


if __name__ == "__main__":
    main()
