"""Bigger magazines for two existing guns (the Gunsmith set): a twin-drum 5.56 magazine for the M4 and a long 40-round stick
for the MP5. Each is made from the gun's own models, every frame of them (racking 1-6, the magazine leaving the well 7-12,
and the iron-sight-less copies tools/gen_attachments.py makes from these): the M4's box magazine is swapped for the twin
drum, riding the same way out of the well, and the MP5's magazine body is lengthened along its own curve. The gun's item model
then picks the variant by tomyen:loaded_magazine_type.

Run after the guns' own models exist and before tools/gen_attachments.py (which wraps the item models again):
    python3 tools/gen_gunsmith_mags.py src/main/resources/assets/tomyen"""
import copy, json, math, os, sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
import gunkit
from gunsmith_kit import Atlas, Parts, RAMPS, BLACK, CLEAR, paint_materials, check_overlaps, find_overlaps

ASSETS = sys.argv[1]
MODELS, ITEMS, TEX = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items"), os.path.join(ASSETS, "textures")

# A small atlas for the drum: black polymer and the drum's face.
K = Atlas("drum", "item/gunsmith_mags", seed=60, size=64)
paint_materials(K, ramps={"black": RAMPS["black"], "smoke": (0x7A7E86, 0x5E626A, 0x4A4E56, 0x3C4048, 0x2A2E34)}, woods={},
                wraps={})


def drum_face(i, j):
    """A C-Mag drum's side: a rim, a smoked window showing the rounds' brass in a ring, the hub."""
    d = math.hypot(i - 7.5, j - 7.5)
    if d > 7.9:
        return CLEAR
    if d > 6.8:
        return RAMPS["black"][1]
    if d > 4.6:
        return RAMPS["brass"][1] if int(math.degrees(math.atan2(j - 7.5, i - 7.5)) // 20) % 2 else RAMPS["brass"][3]
    if d > 3.9:
        return RAMPS["black"][2]
    return RAMPS["black"][3] if d > 1.4 else RAMPS["black"][0]


K.add("drum_face", 16, 16, drum_face, picture=True)
K.save(TEX)
P = Parts(K)


def tube_x(out, x0, x1, cy, cz, r, ramp, face=None):
    """A short cylinder along x (a drum): an octagon built along z and turned a quarter."""
    part = []
    P.tube(part, -(x1 - x0) / 2, (x1 - x0) / 2, r, ramp, cx=0.0, cy=0.0, end=f"{ramp}_md")
    if face:
        h = r * 0.98
        K.sticker(part, -h, -h, -(x1 - x0) / 2 - 0.03, h, h, -(x1 - x0) / 2 - 0.02, face, "north")
        K.sticker(part, -h, -h, (x1 - x0) / 2 + 0.02, h, h, (x1 - x0) / 2 + 0.03, face, "south")
    gunkit.rotate(part, gunkit.ry(90), (0, 0, 0))
    out += gunkit.move(part, dx=(x0 + x1) / 2, dy=cy, dz=cz)


def m4_drum():
    """The M4's twin drum, seated: a feed tower up into the magwell and a drum either side under it, joined by a spine."""
    out = []
    P.metal(out, 7.55, 0.6, 4.25, 8.45, 3.6, 7.35, "black", name="drum_tower")
    P.metal(out, 7.35, -1.3, 4.6, 8.65, 0.6, 7.0, "black", name="drum_spine")
    for x0, x1 in ((5.75, 7.35), (8.65, 10.25)):
        tube_x(out, x0, x1, -0.6, 5.8, 1.85, "black", face="drum_face")
    return out


def magazine_offset(model, base):
    """How far the magazine has moved out of the well in this model, from its upper part."""
    def find(m):
        for e in m["elements"]:
            if e.get("name") == "magazine_upper":
                return e["from"]
        return None
    a, b = find(model), find(base)
    return None if a is None or b is None else [a[k] - b[k] for k in range(3)]


def write(name, model):
    with open(os.path.join(MODELS, name + ".json"), "w") as f:
        json.dump(model, f, indent=2)
        f.write("\n")


def drum_variant(gun, name, base):
    model = json.load(open(os.path.join(MODELS, name + ".json")), object_pairs_hook=OrderedDict)
    offset = magazine_offset(model, base)
    if offset is None:
        return None
    model = copy.deepcopy(model)
    model["elements"] = [e for e in model["elements"] if not e.get("name", "").startswith("magazine_")]
    drum = gunkit.move(m4_drum(), *offset)
    first = len(model["elements"])
    model["elements"] += drum
    model["textures"]["drum"] = "tomyen:item/gunsmith_mags"
    check_overlaps(name + "_drum", model["elements"], only=set(range(first, len(model["elements"]))))
    variant = name.replace(gun, gun + "_drum", 1)
    write(variant, model)
    return variant


EXTEND = 2.2


def extended_variant(gun, name, base):
    """The MP5's magazine body (its curved lower half and the bevels at its foot) carried EXTEND pixels further down its curve."""
    model = json.load(open(os.path.join(MODELS, name + ".json")), object_pairs_hook=OrderedDict)
    if magazine_offset(model, base) is None:
        return None
    model = copy.deepcopy(model)
    changed = set()
    for i, e in enumerate(model["elements"]):
        n = e.get("name", "")
        if n.startswith("magazine_lower"):
            changed.add(i)
        if n in ("magazine_lower", "magazine_lower~l", "magazine_lower~r"):
            e["from"][1] = round(e["from"][1] - EXTEND, 4)
        elif n in ("magazine_lower~bpn", "magazine_lower~bnn"):
            # The foot's bevels, along the body's own axis (turned 22.5 degrees about x).
            a = math.radians(22.5)
            gunkit.move([e], dy=-EXTEND * math.cos(a), dz=-EXTEND * math.sin(a))
    # The MP5's own magazine already has its upper and lower halves' flanks in one plane; only new overlaps count.
    had = {h[:4] for h in find_overlaps(json.load(open(os.path.join(MODELS, name + ".json")))["elements"], only=changed)}
    check_overlaps(name + "_ext", model["elements"], only=changed, allowed=had)
    variant = name.replace(gun, gun + "_ext", 1)
    write(variant, model)
    return variant


def inject(gun, magazine, make):
    """Wraps every loaded-magazine model in the gun's own item tree with a choice of the new magazine's variant."""
    path = os.path.join(ITEMS, gun + ".json")
    item = json.load(open(path), object_pairs_hook=OrderedDict)
    tree = item["model"]
    if tree.get("type") == "minecraft:composite":
        tree = tree["models"][0]
    if tree.get("type") == "minecraft:condition" and tree.get("component") == "tomyen:optic_attachment":
        tree = tree["on_false"]
    base = json.load(open(os.path.join(MODELS, gun + ".json")), object_pairs_hook=OrderedDict)
    made = {}

    def variant_of(model_ref):
        name = model_ref[len("tomyen:item/"):]
        if name not in made:
            # A frame with no magazine in it (gone from the well) stays as it is.
            made[name] = make(gun, name, base) or name
        return made[name]

    def wrap(node):
        if isinstance(node, dict):
            if node.get("type") == "minecraft:condition" and node.get("component") == "tomyen:loaded_magazine":
                node["on_true"] = choose(node["on_true"])
                return node
            for k, v in list(node.items()):
                node[k] = wrap(v)
        elif isinstance(node, list):
            return [wrap(v) for v in node]
        return node

    def choose(on_true):
        if on_true.get("type") == "minecraft:select" and on_true.get("component") == "tomyen:loaded_magazine_type":
            cases = [c for c in on_true["cases"] if c["when"] != magazine]
            fallback = on_true["fallback"]
        elif on_true.get("type") == "minecraft:model":
            cases, fallback = [], on_true
        else:
            raise SystemExit(f"{gun}: unexpected loaded-magazine model {on_true.get('type')}")
        variant = variant_of(fallback["model"])
        cases.append(OrderedDict([("when", magazine), ("model", OrderedDict([("type", "minecraft:model"),
                                                                             ("model", "tomyen:item/" + variant)]))]))
        return OrderedDict([("type", "minecraft:select"), ("property", "minecraft:component"),
                            ("component", "tomyen:loaded_magazine_type"), ("cases", cases), ("fallback", fallback)])

    tree = wrap(tree)
    item["model"] = tree
    with open(path, "w") as f:
        json.dump(item, f, indent=2)
        f.write("\n")
    print(gun, magazine, len(made), "variants")


inject("m4", "tomyen:stanag_drum_magazine", drum_variant)
inject("mp5", "tomyen:mp5_extended_magazine", extended_variant)
