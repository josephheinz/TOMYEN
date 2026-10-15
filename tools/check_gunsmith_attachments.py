"""Z-fight check for the Gunsmith attachments as fitted: each attachment model on its own, and against the gun it sits on
(only faces of the attachment are checked against the gun, so the gun's own faces aren't re-judged).

Usage: python3 tools/check_gunsmith_attachments.py src/main/resources/assets/mymod"""
import glob, json, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunsmith_kit import find_overlaps

ASSETS = sys.argv[1]
MODELS = os.path.join(ASSETS, "models/item")
NAMES = ("hunting_scope_2x", "hunting_scope_4x", "flashlight", "bipod", "bipod_deployed", "rifle_sling", "bayonet", "shotgun_choke",
         "compensator")
bad = 0
count = 0
for path in sorted(glob.glob(os.path.join(MODELS, "attachment", "*.json"))):
    stem = os.path.basename(path)[:-5]
    name = next((n for n in sorted(NAMES, key=len, reverse=True) if stem.endswith("_" + n) or ("_" + n + "_o") in stem), None)
    if not name:
        continue
    gun = stem[:stem.index("_" + name)]
    att = json.load(open(path))
    base = json.load(open(os.path.join(MODELS, gun + ".json")))
    els = [json.loads(json.dumps(e)) for e in att["elements"]]
    # write_model shifts everything 16 pixels along z when it reaches past -16; undo that to compare with the gun.
    if att["display"]["firstperson_righthand"]["translation"] != base["display"]["firstperson_righthand"]["translation"]:
        for e in els:
            e["from"][2] -= 16
            e["to"][2] -= 16
            if "rotation" in e:
                e["rotation"]["origin"][2] -= 16
    own = find_overlaps(els)
    gun_els = base["elements"]
    if name.startswith("hunting_scope"):
        # Iron sights come off while an optic is fitted (tools/gen_attachments.py IRON_SIGHTS).
        gun_els = [e for e in gun_els if "sight" not in e.get("name", "")]
    combined = els + gun_els
    against = find_overlaps(combined, only=set(range(len(els))))
    hits = own + [h for h in against if h not in own]
    count += 1
    if hits:
        bad += 1
        for h in hits[:6]:
            print(stem, h)
print(count, "attachment models,", bad, "with overlaps")
