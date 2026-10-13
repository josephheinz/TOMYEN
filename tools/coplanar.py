"""Z-fight check in world space: flags every pair of faces that lie in the same plane, face the same way and overlap,
whatever rotation their cubes have (tools/zfight.py only compares cubes sharing one rotation).

Faces are convex polygons given by their corners (any rotation already applied). Two faces fight when their normals
match, their planes are within EPS_PLANE of each other, and the overlap of the two polygons in that plane is larger than
EPS_AREA square pixels. Back-to-back faces (opposite normals) don't fight: only one of them is ever seen.

Used by gen_minigun.py (item model elements, every layer that's drawn together) and gen_werewolf.py (the entity
model's cubes, posed)."""
import math

EPS_NORMAL = 1e-4
EPS_PLANE = 2e-3
EPS_AREA = 1e-3


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _unit(a):
    n = math.sqrt(_dot(a, a))
    return (a[0] / n, a[1] / n, a[2] / n) if n > 1e-12 else None


def _area2d(poly):
    return 0.5 * sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
                     for i in range(len(poly)))


def _clip(subject, clipper):
    """Sutherland-Hodgman: subject polygon clipped by the convex, counter-clockwise clipper."""
    out = list(subject)
    for i in range(len(clipper)):
        a, b = clipper[i], clipper[(i + 1) % len(clipper)]
        inp, out = out, []
        if not inp:
            break

        def side(p):
            return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
        for j in range(len(inp)):
            p, q = inp[j], inp[(j + 1) % len(inp)]
            sp, sq = side(p), side(q)
            if sp >= 0:
                out.append(p)
            if (sp >= 0) != (sq >= 0):
                t = sp / (sp - sq)
                out.append((p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t))
    return out


class Face:
    __slots__ = ("label", "pts", "normal", "offset")

    def __init__(self, label, pts, normal):
        """normal: the way the face looks (outward). Faces with no area get no normal and are skipped."""
        self.label, self.pts = label, pts
        flat = _cross(_sub(pts[1], pts[0]), _sub(pts[2], pts[0]))
        if len(pts) > 3:
            flat = tuple(c + d for c, d in zip(flat, _cross(_sub(pts[2], pts[0]), _sub(pts[3], pts[0]))))
        n = _unit(normal) if math.sqrt(_dot(flat, flat)) > 1e-9 else None
        self.normal = n
        self.offset = _dot(n, pts[0]) if n else 0.0


def overlap_area(a, b):
    n = a.normal
    # A basis in the plane.
    helper = (1.0, 0.0, 0.0) if abs(n[0]) < 0.9 else (0.0, 1.0, 0.0)
    u = _unit(_cross(n, helper))
    v = _cross(n, u)
    pa = [(_dot(p, u), _dot(p, v)) for p in a.pts]
    pb = [(_dot(p, u), _dot(p, v)) for p in b.pts]
    if _area2d(pb) < 0:
        pb.reverse()
    if _area2d(pa) < 0:
        pa.reverse()
    clipped = _clip(pa, pb)
    return abs(_area2d(clipped)) if len(clipped) >= 3 else 0.0


def fights(faces):
    """faces: list of Face (normal may be None for degenerate faces, which are skipped). Returns (label a, label b,
    area) for every fighting pair."""
    good = [f for f in faces if f.normal]
    # Bucket by rounded normal so only faces facing (nearly) the same way are compared.
    buckets = {}
    for f in good:
        key = tuple(round(c, 2) for c in f.normal)
        buckets.setdefault(key, []).append(f)
    out = []
    seen = set()
    for key, group in buckets.items():
        # Neighbouring buckets too, in case a normal rounds across a boundary.
        near = []
        for dx in (-0.01, 0, 0.01):
            for dy in (-0.01, 0, 0.01):
                for dz in (-0.01, 0, 0.01):
                    near += buckets.get((round(key[0] + dx, 2), round(key[1] + dy, 2), round(key[2] + dz, 2)), [])
        for a in group:
            for b in near:
                if a is b or (id(b), id(a)) in seen:
                    continue
                seen.add((id(a), id(b)))
                if _dot(a.normal, b.normal) < 1 - EPS_NORMAL or abs(a.offset - b.offset) > EPS_PLANE:
                    continue
                area = overlap_area(a, b)
                if area > EPS_AREA:
                    out.append((a.label, b.label, round(area, 4)))
    return out


# ------------------------------------------------------------------------------------------- item model elements
_DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
_CORNERS = {
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "up": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], b[1], a[2]), (a[0], b[1], a[2])],
    "down": lambda a, b: [(a[0], a[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
}


def element_faces(elements, tag=""):
    """Faces of item model elements (gunkit's element_rotation reads their rotation)."""
    from gunkit import element_rotation, apply
    out = []
    for i, e in enumerate(elements):
        rot = element_rotation(e)
        for name in e.get("faces", {}):
            pts = _CORNERS[name](e["from"], e["to"])
            normal = _DIRS[name]
            if rot:
                m, o = rot
                pts = [tuple(v + o[k] for k, v in enumerate(apply(m, [p[k] - o[k] for k in range(3)]))) for p in pts]
                normal = apply(m, normal)
            out.append(Face(f"{tag}{e.get('name', '#' + str(i))}.{name}", pts, normal))
    return out


def check_elements(name, *layers):
    """Raises if any faces across these element lists (drawn together) fight."""
    faces = []
    for k, layer in enumerate(layers):
        faces += element_faces(layer, tag=f"L{k}:" if len(layers) > 1 else "")
    hits = fights(faces)
    if hits:
        for h in hits[:20]:
            print("z-fight:", name, h)
        raise SystemExit(f"{name}: {len(hits)} coplanar overlapping face pairs")
