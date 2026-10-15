"""Renders a preview sheet of the Gunsmith set from the generated model files: each new gun from the side, with its moving
part worked, in first person and as its inventory icon; attachments fitted to guns (as the item models combine them); and
the inventory sprites of the attachments and ammo.

Usage: python3 tools/preview_gunsmith.py src/main/resources/assets/mymod out.png"""
import json, math, os, struct, sys, zlib

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import apply, display_matrix, ry, mul
from gunsmith_kit import render, ortho, first_person, gui_icon, compose, SIDE, RIGHT_FRONT, LEFT_REAR, bounds

ASSETS, OUT = sys.argv[1], sys.argv[2]
MODELS = os.path.join(ASSETS, "models/item")
TEX = os.path.join(ASSETS, "textures")


def read_png(path):
    """RGBA pixels of a PNG (8-bit RGBA or RGB, any filter)."""
    data = open(path, "rb").read()
    i, idat, w, h, ctype = 8, b"", 0, 0, 6
    palette = None
    while i < len(data):
        n = struct.unpack(">I", data[i:i + 4])[0]
        kind, chunk = data[i + 4:i + 8], data[i + 8:i + 8 + n]
        if kind == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", chunk[:10])
            assert depth == 8, (path, depth)
        elif kind == b"PLTE":
            palette = [tuple(chunk[k:k + 3]) for k in range(0, len(chunk), 3)]
        elif kind == b"IDAT":
            idat += chunk
        i += 12 + n
    bpp = {6: 4, 2: 3, 3: 1, 4: 2, 0: 1}[ctype]
    raw = zlib.decompress(idat)
    stride = w * bpp
    rows, prev = [], bytearray(stride)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    px = []
    for line in rows:
        for x in range(w):
            if ctype == 6:
                px.append(tuple(line[x * 4:x * 4 + 4]))
            elif ctype == 2:
                px.append(tuple(line[x * 3:x * 3 + 3]) + (255,))
            elif ctype == 3:
                px.append(palette[line[x]] + (255,))
            elif ctype == 4:
                px.append((line[x * 2], line[x * 2], line[x * 2], line[x * 2 + 1]))
            else:
                px.append((line[x], line[x], line[x], 255))
    return w, h, px


ATLAS_CACHE = {}


def load(name, shift_back=False):
    """A model's elements and its texture atlases (keyed '#var'), following parents for textures."""
    m = json.load(open(os.path.join(MODELS, name + ".json")))
    textures = dict(m.get("textures", {}))
    atlases = {}
    for key, ref in textures.items():
        while ref.startswith("#"):
            ref = textures[ref[1:]]
        path = os.path.join(TEX, ref.split(":")[1] + ".png")
        if path not in ATLAS_CACHE:
            w, h, px = read_png(path)
            ATLAS_CACHE[path] = (px, w)
        atlases["#" + key] = ATLAS_CACHE[path]
    elements = json.loads(json.dumps(m["elements"]))
    if shift_back:
        for e in elements:
            e["from"][2] -= 16
            e["to"][2] -= 16
            if "rotation" in e:
                e["rotation"]["origin"][2] -= 16
    return elements, atlases, m.get("display")


def fitted(gun, attachments, frame=""):
    """The gun (one of its frame models) with attachment models on it, the way the item model composes them."""
    elements, atlases, display = load(gun + frame)
    base_display = load(gun)[2]
    for a in attachments:
        name = f"attachment/{gun}_{a}"
        att = json.load(open(os.path.join(MODELS, name + ".json")))
        shifted = att["display"]["firstperson_righthand"]["translation"] != base_display["firstperson_righthand"]["translation"]
        e, at, _ = load(name, shifted)
        # Keys can collide between models (each has '#parts' or '#gun'): give the attachment's its own.
        for el in e:
            for f in el["faces"].values():
                f["texture"] = "#att_" + f["texture"][1:]
        for k, v in at.items():
            atlases["#att_" + k[1:]] = v
        elements = elements + e
    return elements, atlases, base_display


def views(elements, atlases, display, worked=None, big=False):
    """Side at rest, right-front worked, first person, GUI icon."""
    lo, hi = bounds(elements)
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    w, h = (520, 250) if not big else (620, 300)
    # Fit the length across and the height down (the slingshot is taller than it is long).
    k = min((w - 40) / max(hi[2] - lo[2], 4.0), (h - 30) / max(hi[1] - lo[1], 4.0))
    cells = [(render(elements, atlases, ortho(SIDE, k, w, h, c), lambda n: n, w, h), w, h, 1)]
    if worked:
        we, wa = worked
        cells.append((render(we, wa, ortho(RIGHT_FRONT, k * 0.95, w, h, c), lambda n: n, w, h), w, h, 1))
    else:
        cells.append((render(elements, atlases, ortho(LEFT_REAR, k * 0.95, w, h, c), lambda n: n, w, h), w, h, 1))
    project, turn = first_person(display, 480, 270)
    fp = render(elements, atlases, project, turn, 480, 270)
    cells.append(([(110, 130, 160) if v is None else v for v in fp], 480, 270, 1))
    project, turn = gui_icon(display, 48)
    ic = render(elements, atlases, project, turn, 48, 48)
    cells.append(([(139, 139, 139) if v is None else v for v in ic], 48, 48, 2))
    return cells


def sprite_cell(name, scale=4, bg=(139, 139, 139)):
    w, h, px = read_png(os.path.join(TEX, "item", name + ".png"))
    img = []
    for p in px:
        img.append(bg if p[3] < 128 else p[:3])
    return (img, w, h, scale)


rows = []
for gun, worked_frame, loaded in (("slingshot", "_loaded_f6", "_loaded"), ("flare_gun", "_f6", ""), ("snub_revolver", "_f6", ""),
                                  ("sawed_off", "_f6", ""), ("lever_action", "_f6", ""), ("harpoon_gun", "_loaded_f6", "_loaded"),
                                  ("nail_gun", "", "")):
    e, a, d = load(gun + loaded)
    if gun == "nail_gun":
        worked = load("nail_gun_coil")[:2]
    else:
        worked = load(gun + worked_frame)[:2]
    rows.append(views(e, a, d, worked))

# Attachments on guns: each slot's new parts somewhere.
for gun, atts in (("m4", ["hunting_scope_2x", "flashlight", "compensator", "rifle_sling"]),
                  ("akm", ["hunting_scope_4x", "bipod_deployed", "bayonet"]),
                  ("lever_action", ["hunting_scope_4x", "rifle_sling", "bayonet"]),
                  ("lever_action", ["hunting_scope_2x", "bipod", "flashlight"]),
                  ("remington_870", ["shotgun_choke", "flashlight", "rifle_sling"]),
                  ("spas12", ["hunting_scope_2x", "bayonet"]),
                  ("harpoon_gun", ["flashlight", "rifle_sling"])):
    e, a, d = fitted(gun, atts, "" if gun not in ("m4", "akm", "lever_action", "spas12") else "")
    if any(x.startswith("hunting_scope") for x in atts) and os.path.exists(os.path.join(MODELS, "no_iron_sights", gun + ".json")):
        e0, a0, _ = load("no_iron_sights/" + gun)
        e = e0 + [el for el in e if el["faces"] and next(iter(el["faces"].values()))["texture"].startswith("#att_")]
        a.update(a0)
    rows.append(views(e, a, d))
# The bigger magazines.
for gun in ("m4_drum", "mp5_ext"):
    e, a, d = load(gun)
    rows.append(views(e, a, load(gun.split("_")[0])[2]))
rows.append([sprite_cell(n) for n in ("hunting_scope_2x", "hunting_scope_4x", "flashlight", "bipod", "rifle_sling", "bayonet",
                                     "shotgun_choke", "compensator")])
rows.append([sprite_cell(n) for n in ("bullet_casing_38", "bullet_38_fmj", "bullet_38_silver", "iron_nail", "nail_strip", "nail_coil",
                                     "harpoon_spear", "flare_shell")])
compose(rows, OUT)
print("preview:", OUT)
