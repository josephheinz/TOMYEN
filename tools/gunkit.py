"""Shared helpers for the gun model generators (gen_shotguns.py, gen_m249.py).

A Kit paints one texture atlas from named swatches and builds model elements textured from it: plain boxes, boxes
with chamfered long edges, and octagonal rods (the same construction as gen_rpg.py). Elements can carry any
rotation: Minecraft reads either a single axis and angle or x/y/z Euler angles (applied x, then y, then z), and
rotate()/move() compose further rotations and translations onto finished elements, which is how moving parts are
posed for each animation frame. Also: z-fight and bounds checks, display-transform maths, and JSON output.

Model space is in pixels: +x is the gun's right, +y up, the muzzle toward -z."""
import json, math, os, random, struct, zlib
from collections import OrderedDict

from zfight import check as zfight_check

FACES = ["north", "east", "south", "west", "up", "down"]
# Which two of the face's edges (u along, v along) each face spans, for sizing texture cuts.
DIRS = {"north": ("x", "y"), "south": ("x", "y"), "east": ("z", "y"), "west": ("z", "y"), "up": ("x", "z"), "down": ("x", "z")}
CONTEXTS = ["thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand", "gui", "ground", "fixed"]


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def mix(a, b, t):
    ca = [(a >> s) & 255 for s in (16, 8, 0)]
    cb = [(b >> s) & 255 for s in (16, 8, 0)]
    return sum(int(ca[i] + (cb[i] - ca[i]) * t) << s for i, s in enumerate((16, 8, 0)))


# ---------------------------------------------------------------------------------------------------- rotations


def rx(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mul(p, q):
    return [[sum(p[i][k] * q[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


IDENTITY = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def euler(x=0.0, y=0.0, z=0.0):
    """The matrix Minecraft builds from an element's x/y/z rotation: about x first, then y, then z."""
    return mul(rz(z), mul(ry(y), rx(x)))


def to_euler(m):
    """Degrees (x, y, z) with euler(x, y, z) == m."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    a = math.atan2(m[2][1], m[2][2])
    c = math.atan2(m[1][0], m[0][0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def element_rotation(e):
    """(matrix, origin) of an element's rotation, or None."""
    r = e.get("rotation")
    if not r:
        return None
    if "axis" in r:
        m = {"x": rx, "y": ry, "z": rz}[r["axis"]](r["angle"])
    else:
        m = euler(r.get("x", 0), r.get("y", 0), r.get("z", 0))
    return m, list(r["origin"])


def set_rotation(e, m, origin):
    """Writes a rotation, as a single axis and angle when that's all it is (easier to read and to z-fight check)."""
    x, y, z = (round(v, 4) for v in to_euler(m))
    x, y, z = (0.0 if abs(v) < 1e-4 else v for v in (x, y, z))
    if x == y == z == 0:
        e.pop("rotation", None)
        return
    rot = OrderedDict()
    if [x, y, z].count(0.0) == 2:
        axis = "x" if x else "y" if y else "z"
        rot["angle"] = x or y or z
        rot["axis"] = axis
    else:
        rot["x"], rot["y"], rot["z"] = x, y, z
    rot["origin"] = [round(v, 4) for v in origin]
    e["rotation"] = rot
    # Keep the key order of hand-written models: rotation before faces.
    faces = e.pop("faces")
    e["faces"] = faces


def move(elements, dx=0.0, dy=0.0, dz=0.0):
    """Translates elements (and their rotation origins) in place; returns them."""
    for e in elements:
        for k in ("from", "to"):
            e[k] = [round(e[k][0] + dx, 4), round(e[k][1] + dy, 4), round(e[k][2] + dz, 4)]
        if "rotation" in e:
            o = e["rotation"]["origin"]
            e["rotation"]["origin"] = [round(o[0] + dx, 4), round(o[1] + dy, 4), round(o[2] + dz, 4)]
    return elements


def rotate(elements, m, pivot):
    """Turns elements in place by matrix m about pivot, on top of whatever rotation each already has.

    Each element keeps its shape: it is moved so its own rotation origin lands where the turn takes it, and its
    rotation becomes m times the old one about that new origin."""
    for e in elements:
        old = element_rotation(e)
        if old:
            r, o = old
        else:
            r = IDENTITY
            o = [(e["from"][a] + e["to"][a]) / 2 for a in range(3)]
        rel = [o[a] - pivot[a] for a in range(3)]
        new_o = [v + pivot[a] for a, v in enumerate(apply(m, rel))]
        t = [new_o[a] - o[a] for a in range(3)]
        for k in ("from", "to"):
            e[k] = [round(e[k][a] + t[a], 4) for a in range(3)]
        set_rotation(e, mul(m, r), new_o)
    return elements


def copy(elements):
    return json.loads(json.dumps(elements), object_pairs_hook=OrderedDict)


# ---------------------------------------------------------------------------------------------------------- kit


class Kit:
    """One gun's texture atlas (size x size texels; swatch rectangles are in model UV units, 0-16) and the element
    builders that texture from it. key: the texture variable the elements use (#key)."""

    def __init__(self, key, texture, seed, size=64):
        self.key, self.texture, self.size = key, texture, size
        self.rnd = random.Random(seed)
        self.atlas = [(0, 0, 0, 0)] * (size * size)
        self.sw = {}

    def rgb(self, hx, a=255, jitter=0):
        r, g, b = (hx >> 16) & 255, (hx >> 8) & 255, hx & 255
        d = self.rnd.randint(-jitter, jitter) if jitter else 0
        c = lambda v: max(0, min(255, v + d))
        return (c(r), c(g), c(b), a)

    def swatch(self, name, u0, v0, u1, v1, f, picture=False):
        """f(x, y, w, h) -> RGBA for texel (x, y) of a w x h texel swatch. A picture swatch maps whole onto a face;
        others are cut from at a varying spot so neighbouring faces don't repeat."""
        assert name not in self.sw, name
        self.sw[name] = ((u0, v0, u1, v1), picture)
        k = self.size // 16
        w, h = (u1 - u0) * k, (v1 - v0) * k
        for y in range(h):
            for x in range(w):
                i = (v0 * k + y) * self.size + u0 * k + x
                assert self.atlas[i][3] == 0, ("swatches overlap", name, u0, v0)
                self.atlas[i] = f(x, y, w, h)

    def save(self, textures_root):
        png(os.path.join(textures_root, self.texture + ".png"), self.size, self.size, self.atlas)

    def uv(self, name, du, dv):
        (u0, v0, u1, v1), picture = self.sw[name]
        if picture:
            return [u0, v0, u1, v1]
        texel = 16.0 / self.size
        # About one texel per half pixel of face, stretched if the face is bigger than the swatch.
        su, sv = min(u1 - u0, max(du * 0.5 * texel * 4, texel)), min(v1 - v0, max(dv * 0.5 * texel * 4, texel))
        ou = self.rnd.uniform(0, (u1 - u0) - su)
        ov = self.rnd.uniform(0, (v1 - v0) - sv)
        return [round(u0 + ou, 3), round(v0 + ov, 3), round(u0 + ou + su, 3), round(v0 + ov + sv, 3)]

    def box(self, out, x0, y0, z0, x1, y1, z1, mat, rot=None, emit=False, name=None, **face_mats):
        """A box. mat: swatch for every face; face_mats override per face (None drops the face).
        rot: (axis, angle, origin) or (matrix, origin)."""
        e = OrderedDict()
        if name:
            e["name"] = name
        e["from"] = [round(min(x0, x1), 4), round(min(y0, y1), 4), round(min(z0, z1), 4)]
        e["to"] = [round(max(x0, x1), 4), round(max(y0, y1), 4), round(max(z0, z1), 4)]
        if emit:
            e["shade"] = False
            e["light_emission"] = 15
        size = {"x": abs(x1 - x0), "y": abs(y1 - y0), "z": abs(z1 - z0)}
        e["faces"] = OrderedDict()
        for f in FACES:
            m = face_mats.get(f, mat)
            if m is None:
                continue
            a, b = DIRS[f]
            e["faces"][f] = OrderedDict([("uv", self.uv(m, size[a], size[b])), ("texture", "#" + self.key)])
        if rot:
            if isinstance(rot[0], str):
                axis, angle, origin = rot
                m = {"x": rx, "y": ry, "z": rz}[axis](angle)
            else:
                m, origin = rot
            set_rotation(e, m, origin)
        out.append(e)
        return e

    def chamfered(self, out, x0, y0, z0, x1, y1, z1, r, mat, name=None, cap=None, caps=(True, True), **face_mats):
        """A box running along z with its four long edges bevelled at 45 degrees by r: a core, two side slabs and four
        corner prisms turned 45 degrees (their ends set in a hair so they hide inside the core's)."""
        cap = cap or mat
        ends = dict(north=cap if caps[0] else None, south=cap if caps[1] else None)
        fm = dict(face_mats)
        self.box(out, x0 + r, y0, z0, x1 - r, y1, z1, mat, name=name, **{**ends, **fm})
        sides = {k: v for k, v in fm.items() if k not in ("up", "down")}
        self.box(out, x0, y0 + r, z0, x0 + r, y1 - r, z1, mat, **{**ends, **sides, "east": None})
        self.box(out, x1 - r, y0 + r, z0, x1, y1 - r, z1, mat, **{**ends, **sides, "west": None})
        h = r / math.sqrt(2)
        for cx, cy in ((x0 + r, y0 + r), (x1 - r, y0 + r), (x0 + r, y1 - r), (x1 - r, y1 - r)):
            self.box(out, cx - h, cy - h, z0 + 0.02, cx + h, cy + h, z1 - 0.02, mat, rot=("z", 45, (cx, cy, z0)),
                     north=None, south=None)

    def rod(self, out, z0, z1, r, side, cx=8.0, cy=8.0, cap=None, along=None, name=None, emit=False, caps=(True, True), mark=None):
        """A solid regular octagonal prism along z: four bands through the axis at 0, 90, 45 and -45 degrees, each as
        wide as an octagon side, so their ends make up the octagon exactly. Each band's ends are staggered a hair so
        overlapping caps don't z-fight. along: swatch for east/west faces (whose u runs along z) when the material has
        grain. mark: a picture for the two faces of the level band."""
        a = 2 * r * math.tan(math.pi / 8)
        cap = cap or side
        ew = along or side
        first = None
        for k, (angle, w, h) in enumerate([(0, r, a / 2), (0, a / 2, r), (45, r, a / 2), (-45, r, a / 2)]):
            d = 0.01 * k
            faces = dict(east=ew, west=ew, up=side, down=side,
                         north=cap if caps[0] else None, south=cap if caps[1] else None)
            if mark and k == 0:
                faces.update(east=mark, west=mark)
            e = self.box(out, cx - w, cy - h, z0 + d, cx + w, cy + h, z1 - d, side, emit=emit,
                         rot=("z", angle, (cx, cy, z0)) if angle else None, name=(name if k == 0 else None), **faces)
            first = first or e
        return first

    def steps(self, out, profile, side, **kw):
        """Stacked rods: profile is a list of (z0, z1, radius)."""
        for z0, z1, r in profile:
            self.rod(out, z0, z1, r, side, **kw)

    def sticker(self, out, x0, y0, z0, x1, y1, z1, mat, face, emit=False, name=None):
        """A single face (a decal just proud of a surface)."""
        return self.box(out, x0, y0, z0, x1, y1, z1, mat, emit=emit, name=name, **{f: None for f in FACES if f != face})


# ------------------------------------------------------------------------------------------------------- checks


def check(name, elements):
    fights = zfight_check(elements)
    if fights:
        for f in fights:
            print("z-fight:", name, f)
        raise SystemExit(name + " has z-fighting")
    for e in elements:
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, (name, e)


# ----------------------------------------------------------------------------------------------------- displays


def look(muzzle, top):
    """A display rotation turning the muzzle (model -z) and the top (model +y) to these screen directions."""
    f = [-v for v in muzzle]
    up = top
    side = [up[1] * f[2] - up[2] * f[1], up[2] * f[0] - up[0] * f[2], up[0] * f[1] - up[1] * f[0]]
    m = [[side[i], up[i], f[i]] for i in range(3)]
    # Display rotations are applied as Rx * Ry * Rz.
    b = math.asin(max(-1.0, min(1.0, m[0][2])))
    a = math.atan2(-m[1][2], m[2][2])
    c = math.atan2(-m[0][1], m[0][0])
    return [round(math.degrees(v), 2) for v in (a, b, c)]


def display_matrix(rotation):
    return mul(mul(rx(rotation[0]), ry(rotation[1])), rz(rotation[2]))


def centred(rotation, scale, centre, lift=0.0):
    """Translation (pixels) that puts the model point `centre` at the middle of the slot after rotation and scale."""
    m = display_matrix(rotation)
    c = [v - 8 for v in centre]
    return [round(-sum(m[i][k] * c[k] for k in range(3)) * scale + (lift if i == 1 else 0), 3) for i in range(3)]


def placed(rotation, scale, point, target):
    """Translation (pixels) that puts the model point `point` at `target` (pixels from the slot's middle)."""
    m = display_matrix(rotation)
    c = [v - 8 for v in point]
    return [round(target[i] - sum(m[i][k] * c[k] for k in range(3)) * scale, 3) for i in range(3)]


def display(rot, tr, sc):
    return OrderedDict([("rotation", [round(v, 3) for v in rot]), ("translation", tr), ("scale", [sc, sc, sc])])


def bounds(elements):
    lo = [min(min(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
    hi = [max(max(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
    return lo, hi


def bounds_centre(elements):
    lo, hi = bounds(elements)
    return [(lo[a] + hi[a]) / 2 for a in range(3)]


S2 = math.sqrt(0.5)
# Inventory icons of long guns and parts lie on the diagonal, muzzle to the upper left, right side toward the viewer.
DIAGONAL = look([-S2, S2, 0], [S2, S2, 0])


# ------------------------------------------------------------------------------------------------------- output


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def model(kit, elements, displays=None, parent=None, credit=None):
    m = OrderedDict([("credit", credit or f"mymod {kit.key} - generated"), ("texture_size", [kit.size, kit.size])])
    if parent:
        m["parent"] = parent
    m["textures"] = OrderedDict([(kit.key, "mymod:" + kit.texture), ("particle", "mymod:" + kit.texture)])
    if displays:
        m["display"] = displays
    m["elements"] = elements
    return m


def ref(path):
    return OrderedDict([("type", "minecraft:model"), ("model", "mymod:item/" + path)])


EMPTY = OrderedDict([("type", "minecraft:empty")])


def range_dispatch(prop, entries, fallback):
    """entries: (threshold, model) pairs."""
    return OrderedDict([("type", "minecraft:range_dispatch"), ("property", prop),
                        ("entries", [OrderedDict([("threshold", t), ("model", m)]) for t, m in entries]),
                        ("fallback", fallback)])


def condition(prop, on_true, on_false, **extra):
    node = OrderedDict([("type", "minecraft:condition"), ("property", prop)])
    node.update(extra)
    node["on_true"] = on_true
    node["on_false"] = on_false
    return node


def has_component(component, on_true, on_false):
    return condition("minecraft:has_component", on_true, on_false, component=component)


def composite(*models):
    return OrderedDict([("type", "minecraft:composite"), ("models", list(models))])


def flat_icon(models_root, items_root, name, item_model=None):
    """A plain 2D inventory sprite (textures/item/<name>.png), like the bullets and magazines."""
    write(os.path.join(models_root, name + ".json"), OrderedDict([
        ("parent", "minecraft:item/generated"), ("textures", OrderedDict(layer0="mymod:item/" + name))]))
    if items_root:
        write(os.path.join(items_root, name + ".json"), {"model": item_model or ref(name)})


def sprite(path, rows, palette, rnd=None):
    """A 16x16 sprite from 16 strings of palette keys ('.' transparent)."""
    px = []
    for row in rows:
        assert len(row) == 16, row
        for ch in row:
            px.append((0, 0, 0, 0) if ch == "." else palette[ch])
    assert len(px) == 256
    png(path, 16, 16, px)


# ------------------------------------------------------------------------------------------------- muzzle flash


def flash_kit():
    """The shared muzzle flash atlas (textures/item/muzzle_flash.png): a diamond-shaped flame lobe seen side on, the
    same turned a quarter, and a starburst seen end on. All pictures, so every generator paints it identically."""
    k = Kit("flash", "item/muzzle_flash", seed=31, size=32)

    def lobe(along_u):
        def f(x, y, w, h):
            a, b = (x, y) if along_u else (y, x)
            la, lb = (w, h) if along_u else (h, w)
            u = (a + 0.5) / la                 # along the flame
            v = abs((b + 0.5) / lb - 0.5) * 2  # across it
            width = 1.0 - (u - 0.38) / 0.62 if u > 0.38 else u / 0.38
            if v > width:
                return (0, 0, 0, 0)
            heat = 1.0 - v / max(width, 0.01)
            col = mix(0xFF8A1C, 0xFFF4C8, heat)
            return ((col >> 16) & 255, (col >> 8) & 255, col & 255, int(90 + 165 * heat))
        return f

    def star(x, y, w, h):
        dx, dy = (x + 0.5) / w - 0.5, (y + 0.5) / h - 0.5
        r = math.hypot(dx, dy) * 2
        spike = 0.35 + 0.65 * abs(math.cos(math.atan2(dy, dx) * 3)) ** 3
        if r > spike:
            return (0, 0, 0, 0)
        heat = 1.0 - r / spike
        col = mix(0xFF9A2A, 0xFFFBE0, heat)
        return ((col >> 16) & 255, (col >> 8) & 255, col & 255, int(110 + 145 * heat))
    k.swatch("lobe_u", 0, 0, 16, 6, lobe(True), picture=True)
    k.swatch("lobe_v", 0, 6, 6, 16, lobe(False), picture=True)
    k.swatch("star", 6, 6, 16, 16, star, picture=True)
    return k


FLASH = flash_kit()


def muzzle_flash(z, y, variant):
    """A flash in front of a muzzle at (8, y, z): crossed flame lobes along the bore and a starburst at the mouth, all
    glowing. Each variant turns the cross and changes its length, so rapid fire flickers."""
    out = []
    length, radius, turn = [(4.2, 1.5, 0), (3.4, 1.8, 30), (4.8, 1.25, 60)][variant]
    z0, z1 = z - length, z - 0.05
    FLASH.box(out, 7.99, y - radius, z0, 8.01, y + radius, z1, "lobe_u", emit=True, north=None, south=None, up=None, down=None)
    FLASH.box(out, 8 - radius, y - 0.01, z0 - 0.07, 8 + radius, y + 0.01, z1 - 0.07, "lobe_v", emit=True,
              north=None, south=None, east=None, west=None)
    side = FLASH.box(out, 7.99, y - radius * 0.8, z0 + 0.6, 8.01, y + radius * 0.8, z1 - 0.02, "lobe_u", emit=True,
                     north=None, south=None, up=None, down=None)
    rotate([side], rz(45), (8, y, z))
    s = radius * 0.9
    FLASH.box(out, 8 - s, y - s, z - 0.45, 8 + s, y + s, z - 0.44, "star", emit=True, east=None, west=None, up=None, down=None)
    return rotate(out, rz(turn), (8, y, z))


def shifted(displays, dz):
    """Display transforms that undo shifting every element by dz pixels along z (elements must stay within -16..32)."""
    if dz == 0:
        return OrderedDict(displays)
    out = OrderedDict()
    for ctx, t in displays.items():
        m = display_matrix(t["rotation"])
        s = t["scale"][2]
        out[ctx] = display(t["rotation"], [round(t["translation"][i] - m[i][2] * s * dz, 4) for i in range(3)], t["scale"][0])
    return out


def flash_models(models_dir, gun, z, y, displays):
    """Writes a gun's three flash models (item/<gun>_flash_<n>) with the gun's display transforms; returns their names."""
    names = []
    for v in range(3):
        elements = muzzle_flash(z, y, v)
        lo, _ = bounds(elements)
        dz = 16.0 if lo[2] < -16 else 0.0
        move(elements, dz=dz)
        check(f"{gun}_flash_{v}", elements)
        flash_displays = shifted(displays, dz)
        # Only in the hands: the hotbar icon is the held stack too, and shouldn't flash.
        for ctx in ("gui", "ground", "fixed"):
            flash_displays[ctx] = display([0, 0, 0], [0, 0, 0], 0)
        write(os.path.join(models_dir, f"{gun}_flash_{v}.json"),
              model(FLASH, elements, flash_displays, credit="mymod muzzle flash - generated"))
        names.append(f"{gun}_flash_{v}")
    return names


def with_flash(body, flashes, hidden_by=()):
    """The gun's item model with its flash: mymod:muzzle_flash is 0 with none showing, else 1-3 for the variant. Not a
    composite at the top, which tools/gen_attachments.py would take for its own wrapper. hidden_by: muzzle attachments
    (item ids) that smother the flash."""
    def flash(name):
        if not hidden_by:
            return ref(name)
        return OrderedDict([("type", "minecraft:select"), ("property", "minecraft:component"), ("component", "mymod:muzzle_attachment"),
                            ("cases", [OrderedDict([("when", list(hidden_by)), ("model", EMPTY)])]), ("fallback", ref(name))])
    return range_dispatch("mymod:muzzle_flash", [(k + 0.5, composite(body, flash(f))) for k, f in enumerate(flashes)], body)
