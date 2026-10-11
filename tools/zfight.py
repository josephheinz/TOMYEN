"""Lists z-fighting in item models: two faces in the same plane, facing the same way, overlapping.
Elements with rotations are compared only with elements sharing the exact same rotation."""
import json, sys, os, itertools

AXES = {"east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}
EPS = 1e-4


def faces(e):
    f, t = e["from"], e["to"]
    for name in e.get("faces", {}):
        axis, sign = AXES[name]
        plane = t[axis] if sign > 0 else f[axis]
        others = [a for a in range(3) if a != axis]
        yield name, axis, sign, plane, [(f[a], t[a]) for a in others]


def rot_key(e):
    r = e.get("rotation")
    return None if not r or r.get("angle", 0) == 0 else json.dumps(r, sort_keys=True)


def check(elements):
    out = []
    for (i, a), (j, b) in itertools.combinations(enumerate(elements), 2):
        if rot_key(a) != rot_key(b):
            continue
        for fa in faces(a):
            for fb in faces(b):
                if fa[1] == fb[1] and fa[2] == fb[2] and abs(fa[3] - fb[3]) < EPS:
                    area = 1.0
                    for (a0, a1), (b0, b1) in zip(fa[4], fb[4]):
                        area *= max(0.0, min(a1, b1) - max(a0, b0))
                    if area > EPS:
                        out.append((i, a.get("name", i), j, b.get("name", j), fa[0], round(area, 4)))
    return out


if __name__ == "__main__":
    for path in sys.argv[1:]:
        model = json.load(open(path))
        hits = check(model.get("elements", []))
        if hits:
            print(f"{os.path.relpath(path)}: {len(hits)}")
            for h in hits[:6]:
                print("   ", h)
