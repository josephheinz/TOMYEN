"""Preview sheets for tools/gen_fusion.py (run it with --preview DIR): each 3D model from the side, at three quarters
front and back, in first person as vanilla's handheld transform holds it, and as its inventory icon at 3x; the block
from four angles; sprites and textures blown up."""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import apply, display_matrix, mul, rx, ry, rz  # noqa: E402
from gunsmith_kit import render, ortho, first_person, gui_icon, compose, bounds  # noqa: E402

FRONT = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]          # looking at the south face
TILT = rx(25)


def atlases_of(K):
    return {"#" + K.key: (K.atlas, K.size)}


def model_cells(K, elements, displays, w=420, h=300):
    atl = atlases_of(K)
    lo, hi = bounds(elements)
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    k = min((w - 30) / max(hi[0] - lo[0], 1), (h - 30) / max(hi[1] - lo[1], 1))
    cells = []
    for view in (FRONT, mul(TILT, ry(35)), mul(TILT, ry(145)), mul(rx(10), ry(90))):
        img = render(elements, atl, ortho(view, k, w, h, c), lambda n, v=view: apply(v, n), w, h)
        cells.append(([(150, 160, 175) if v is None else v for v in img], w, h, 1))
    for fov in (70, 110):
        project, turn = first_person(displays, 480, 300, fov=fov)
        fp = render(elements, atl, project, turn, 480, 300)
        cells.append(([(110, 130, 160) if v is None else v for v in fp], 480, 300, 1))
    project, turn = gui_icon(displays, 32)
    ic = render(elements, atl, project, turn, 32, 32)
    cells.append(([(139, 139, 139) if v is None else v for v in ic], 32, 32, 4))
    return cells


def block_cells(K, elements, w=300, h=300):
    atl = atlases_of(K)
    cells = []
    for view in (mul(rx(20), ry(30)), mul(rx(30), ry(-60)), mul(rx(-15), ry(160)), mul(rx(70), ry(20))):
        img = render(elements, atl, ortho(view, 14.0, w, h, [8, 8, 8]), lambda n, v=view: apply(v, n), w, h)
        cells.append(([(150, 160, 175) if v is None else v for v in img], w, h, 1))
    return cells


def sprite_cell(px, w, h, k=12):
    out = []
    for y in range(h):
        for x in range(w):
            c = px[y * w + x]
            bg = (110, 110, 110) if ((x + y) % 2) else (140, 140, 140)
            a = c[3] / 255
            out.append(tuple(int(c[i] * a + bg[i] * (1 - a)) for i in range(3)))
    return (out, w, h, k)


def vanilla_sword_cells(read, size=480):
    """Vanilla's diamond sword as its sprite's texels (one thin box each) held the same way, for comparing."""
    w, h, px = read("item/diamond_sword.png")
    els = []
    for y in range(h):
        for x in range(w):
            c = px[y * w + x]
            if c[3] < 128:
                continue
            els.append({"from": [x, 15 - y, 7.5], "to": [x + 1, 16 - y, 8.5],
                        "faces": {f: {"uv": [x * 16 / w, y * 16 / h, (x + 1) * 16 / w, (y + 1) * 16 / h], "texture": "#v"}
                                  for f in ("north", "south", "east", "west", "up", "down")}})
    from gen_fusion import HANDHELD
    cells = []
    for fov in (70, 110):
        project, turn = first_person(HANDHELD, 480, 300, fov=fov)
        fp = render(els, {"#v": (px, w)}, project, turn, 480, 300)
        cells.append(([(110, 130, 160) if v is None else v for v in fp], 480, 300, 1))
    return cells


def items(built, out_dir, name="items"):
    os.makedirs(out_dir, exist_ok=True)
    rows = []
    for key, value in built.items():
        if value[0] == "block":
            rows.append(block_cells(value[1], value[2]))
        elif value[0] == "sprites":
            rows.append([sprite_cell(*s) for s in value[1]])
        else:
            K, elements, displays = value
            rows.append(model_cells(K, elements, displays))
    path = os.path.join(out_dir, name + ".png")
    compose(rows, path)
    print("preview:", path)
