"""Inventory sprites, models and item definitions for the MP5 extended magazine and the STANAG drum.

gen_gunsmith_mags.py fits both onto their guns but never gave them an inventory look of their own, so they showed as
the missing-texture box. The extended MP5 magazine is the standard one with its curve carried two segments further; the
STANAG drum is the AK drum's outline in the STANAG magazine's colours.

Run from the repo root: python3 tools/gen_extended_mag_items.py
"""
import json
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(__file__))
from gen_longswords import read_png  # noqa: E402

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "mymod")
TEX = os.path.join(ASSETS, "textures", "item")
CLEAR = (0, 0, 0, 0)


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def load(name):
    with open(os.path.join(TEX, name + ".png"), "rb") as f:
        return read_png(f.read())


def extended_mp5(variant):
    """The MP5 magazine's three-row segments, re-stacked five deep so the curve runs on down the slot."""
    w, h, px = load("mp5_magazine" + variant)
    out = [CLEAR] * (w * h)
    # (target first row, source first row, extra shift right): cap, two body segments, the second repeated, base.
    for target, source, shift in ((0, 2, 0), (3, 5, 0), (6, 8, 0), (9, 8, 1), (12, 11, 1)):
        for dy in range(3):
            for x in range(w):
                pixel = px[(source + dy) * w + x]
                if pixel[3] and 0 <= x + shift < w:
                    out[(target + dy) * w + x + shift] = pixel
    return w, h, out


def luminance(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def stanag_drum(variant):
    """The AK drum's outline, each shade swapped for the STANAG magazine shade of the same rank."""
    w, h, drum = load("ak_drum_magazine" + variant)
    _, _, stanag = load("stanag_magazine" + variant)
    drum_shades = sorted({p for p in drum if p[3]}, key=luminance)
    stanag_shades = sorted({p for p in stanag if p[3]}, key=luminance)
    def remap(p):
        if not p[3]:
            return p
        rank = drum_shades.index(p) / max(1, len(drum_shades) - 1)
        return stanag_shades[round(rank * (len(stanag_shades) - 1))]
    return w, h, [remap(p) for p in drum]


def write_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def item(name, make):
    for variant in ("", "_loaded"):
        w, h, px = make(variant)
        png(os.path.join(TEX, name + variant + ".png"), w, h, px)
        write_json(os.path.join(ASSETS, "models", "item", name + variant + ".json"),
                {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name + variant}})
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {
        "type": "minecraft:condition",
        "property": "minecraft:has_component",
        "component": "mymod:magazine_contents",
        "on_true": {"type": "minecraft:model", "model": "mymod:item/" + name + "_loaded"},
        "on_false": {"type": "minecraft:model", "model": "mymod:item/" + name}}})


if __name__ == "__main__":
    item("mp5_extended_magazine", extended_mp5)
    item("stanag_drum_magazine", stanag_drum)
