"""Preview sheet for the Herbalism set (run through gen_herbalism.py --preview out.png): small in-world mockups built
from the real block models (vanilla's read from the client jar), then every sprite enlarged.

Rows: the eight herbs growing on their own ground; the garlic stages on farmland; the kettle hung over a campfire, the
kettle on magma, the mortar & pestle on planks, and the braid and ward on a stone-brick wall (each seen from the
front, and the wall pieces from the side too); then the sprites at 4x."""
import json, math, os, zipfile

from gen_longswords import client_jar, read_png
from gunkit import png, apply, rotate, copy, ry, mul
from gunsmith_kit import render, LIGHTS

GRASS = (124, 189, 107)
FAE_GRASS = (152, 214, 128)
BG = (52, 56, 64, 255)

_jar = None


def jar():
    global _jar
    if _jar is None:
        _jar = zipfile.ZipFile(client_jar())
    return _jar


def resource(assets, ref, kind, ext):
    """Bytes of a model or texture, from our assets or the vanilla jar."""
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    if ns == "tomyen":
        with open(os.path.join(assets, kind, path + ext), "rb") as f:
            return f.read()
    return jar().read(f"assets/minecraft/{kind}/{path}{ext}")


def load_model(assets, ref):
    """A block model with its parents' textures and elements resolved: (elements, textures)."""
    model = json.loads(resource(assets, ref, "models", ".json"))
    textures, elements = {}, None
    chain = [model]
    while "parent" in chain[-1] and not chain[-1]["parent"].endswith("block/block"):
        chain.append(json.loads(resource(assets, chain[-1]["parent"], "models", ".json")))
    for m in reversed(chain):
        textures.update(m.get("textures", {}))
        if "elements" in m:
            elements = m["elements"]
    return copy(elements or []), textures


def resolve(textures, key):
    seen = 0
    while key.startswith("#") and seen < 10:
        key = textures.get(key[1:], key)
        seen += 1
    return key if ":" in key else "minecraft:" + key


_tex = {}


def texture(assets, ref, tint=None):
    key = (ref, tint)
    if key not in _tex:
        w, h, px = read_png(resource(assets, ref, "textures", ".png"))
        px = px[:w * w]  # animated strips: the first frame
        if tint:
            px = [(c[0] * tint[0] // 255, c[1] * tint[1] // 255, c[2] * tint[2] // 255, c[3]) for c in px]
        _tex[key] = (px, w)
    return _tex[key]


AUTO = {
    "down": lambda a, b: [a[0], 16 - b[2], b[0], 16 - a[2]],
    "up": lambda a, b: [a[0], a[2], b[0], b[2]],
    "north": lambda a, b: [16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1]],
    "south": lambda a, b: [a[0], 16 - b[1], b[0], 16 - a[1]],
    "west": lambda a, b: [a[2], 16 - b[1], b[2], 16 - a[1]],
    "east": lambda a, b: [16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1]],
}


def prepare(assets, ref, atlases, offset=(0, 0, 0), turn=0, tint=GRASS):
    """The model's elements ready for gunsmith_kit.render: texture keys resolved into atlases, UVs filled in,
    'rescale' applied, turned about the block's centre (as a block state's y) and moved into place."""
    elements, textures = load_model(assets, ref)
    out = []
    for e in elements:
        e = copy([e])[0]
        r = e.get("rotation")
        if r and r.get("rescale") and "axis" in r:
            k = 1.0 / math.cos(math.radians(r["angle"]))
            o = r["origin"]
            for axis in range(3):
                if "xyz"[axis] != r["axis"]:
                    e["from"][axis] = o[axis] + (e["from"][axis] - o[axis]) * k
                    e["to"][axis] = o[axis] + (e["to"][axis] - o[axis]) * k
        for name, f in e["faces"].items():
            ref_t = resolve(textures, f["texture"])
            tinted = tint if "tintindex" in f else None
            key = ref_t + ("#tint" if tinted else "")
            if key not in atlases:
                atlases[key] = texture(assets, ref_t, tinted)
            f["texture"] = key
            if "uv" not in f:
                f["uv"] = AUTO[name](e["from"], e["to"])
        out.append(e)
    if turn:
        rotate(out, ry(-turn), [8, 8, 8])
    for e in out:
        for k in ("from", "to"):
            e[k] = [e[k][a] + offset[a] for a in range(3)]
        if "rotation" in e:
            e["rotation"]["origin"] = [e["rotation"]["origin"][a] + offset[a] for a in range(3)]
    return out


def camera(direction):
    """An orthographic view looking back along direction (the way from the scene to the camera)."""
    n = math.sqrt(sum(v * v for v in direction))
    f = [v / n for v in direction]
    r = [f[2], 0.0, -f[0]]
    rn = math.sqrt(sum(v * v for v in r))
    r = [v / rn for v in r]
    u = [f[1] * r[2] - f[2] * r[1], f[2] * r[0] - f[0] * r[2], f[0] * r[1] - f[1] * r[0]]
    return [r, u, f]


def scene(elements, atlases, view, w, h, centre, scale):
    def project(p):
        v = apply(view, [p[k] - centre[k] for k in range(3)])
        return (w / 2 + v[0] * scale, h / 2 - v[1] * scale, v[2])
    img = render(elements, atlases, project, lambda n: n, w, h)
    return img


FRONT_LEFT = camera((-0.55, 0.5, -0.75))   # from the north-west, above
FRONT_RIGHT = camera((0.55, 0.5, -0.75))   # from the north-east, above
SIDE = camera((-0.95, 0.25, -0.3))          # nearly side on, from the west


def herb_scene(assets, herb, soil, tint=GRASS):
    atlases = {}
    els = prepare(assets, soil, atlases, tint=tint) + prepare(assets, "tomyen:block/" + herb, atlases, (0, 16, 0))
    return scene(els, atlases, FRONT_LEFT, 96, 120, (8, 14, 8), 3.4), 96, 120


def render_sheet(assets, out):
    rows = []
    soils = {
        "wild_garlic": "minecraft:block/grass_block", "wolfsbane": "minecraft:block/podzol", "yarrow": "minecraft:block/grass_block",
        "radroot": "minecraft:block/red_sand", "chamomile": "minecraft:block/grass_block", "ginseng": "minecraft:block/grass_block",
        "moonpetal": "minecraft:block/grass_block", "water_mint": "minecraft:block/mud",
    }
    row = []
    for herb, soil in soils.items():
        img, w, h = herb_scene(assets, herb, soil, FAE_GRASS if herb == "moonpetal" else GRASS)
        row.append((img, w, h, 1))
    rows.append(row)

    row = []
    for stage in range(4):
        atlases = {}
        els = prepare(assets, "minecraft:block/farmland", atlases) + prepare(assets, f"tomyen:block/garlic_stage{stage}", atlases, (0, 15, 0))
        row.append((scene(els, atlases, FRONT_LEFT, 96, 120, (8, 14, 8), 3.4), 96, 120, 1))
    # The wild herbs, close up from the other side.
    for herb in ("wolfsbane", "ginseng", "moonpetal", "radroot"):
        atlases = {}
        els = prepare(assets, "tomyen:block/" + herb, atlases)
        row.append((scene(els, atlases, FRONT_RIGHT, 96, 120, (8, 8, 8), 5.0), 96, 120, 1))
    rows.append(row)

    row = []
    atlases = {}
    els = prepare(assets, "minecraft:block/campfire", atlases) + prepare(assets, "tomyen:block/kettle_hung", atlases, (0, 16, 0))
    row.append((scene(els, atlases, FRONT_LEFT, 150, 210, (8, 16, 8), 4.6), 150, 210, 1))
    atlases = {}
    els = prepare(assets, "minecraft:block/campfire", atlases) + prepare(assets, "tomyen:block/kettle_hung", atlases, (0, 16, 0))
    row.append((scene(els, atlases, FRONT_RIGHT, 150, 210, (8, 16, 8), 4.6), 150, 210, 1))
    atlases = {}
    els = prepare(assets, "minecraft:block/magma_block", atlases) + prepare(assets, "tomyen:block/kettle", atlases, (0, 16, 0))
    row.append((scene(els, atlases, FRONT_LEFT, 150, 210, (8, 18, 8), 4.6), 150, 210, 1))
    atlases = {}
    els = prepare(assets, "minecraft:block/spruce_planks", atlases) + prepare(assets, "tomyen:block/mortar_and_pestle", atlases, (0, 16, 0))
    row.append((scene(els, atlases, FRONT_RIGHT, 150, 210, (8, 18, 8), 4.6), 150, 210, 1))
    rows.append(row)

    row = []
    for name in ("garlic_braid", "wolfsbane_ward"):
        for view in (camera((-0.3, 0.2, -1.0)), SIDE):
            atlases = {}
            els = prepare(assets, "minecraft:block/stone_bricks", atlases, (0, 0, 16)) + prepare(assets, "tomyen:block/" + name, atlases)
            row.append((scene(els, atlases, view, 150, 170, (8, 8, 12), 7.0), 150, 170, 1))
    rows.append(row)

    sprites = ["wild_garlic", "wolfsbane", "yarrow", "radroot", "chamomile", "ginseng", "moonpetal", "water_mint"]
    items = ["garlic", "roasted_garlic", "garlic_bread", "herb_stew", "yarrow_poultice", "wolfsbane_paste", "wolfsbane_arrow",
             "mint_bait", "moonpetal_bait", "anti_radiation_tea", "calming_tea", "vigor_tonic", "moonpetal_tea", "garlic_braid",
             "wolfsbane_ward"]
    paths = ["block/" + s for s in sprites] + [f"block/garlic_stage{i}" for i in range(4)] + ["item/" + i for i in items] \
        + ["mob_effect/garlic_breath", "mob_effect/radioprotection", "mob_effect/calm"]
    flat = []
    for p in paths:
        px, w = texture(assets, "tomyen:" + p)
        flat.append(([c[:3] if c[3] > 0 else None for c in px], w, w, 4))
    for i in range(0, len(flat), 15):
        rows.append(flat[i:i + 15])

    gap = 8
    W = max(sum(w * k + gap for _, w, _, k in r) for r in rows) + gap
    heights = [max(h * k for _, _, h, k in r) + gap for r in rows]
    H = sum(heights) + gap
    canvas = [BG] * (W * H)
    y = gap
    for r, rh in zip(rows, heights):
        x = gap
        for img, w, h, k in r:
            oy = y + (rh - gap - h * k) // 2
            for yy in range(h * k):
                for xx in range(w * k):
                    c = img[(yy // k) * w + xx // k]
                    if c is not None:
                        canvas[(oy + yy) * W + x + xx] = tuple(c[:3]) + (255,)
            x += w * k + gap
        y += rh
    png(out, W, H, canvas)
    print("preview:", out)
