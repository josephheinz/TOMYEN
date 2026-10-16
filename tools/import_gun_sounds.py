"""Imports real gun recordings into assets/tomyen/sounds/*.ogg and registers them in sounds.json.

Every gunshot and gun-handling sound in the mod comes from these free packs (see CREDITS.md):
  snake1/        "Snake's Authentic Gun Sounds" by SnakeF8 (public domain / free for any use)
  snake2/        "Snake's SECOND Authentic Gun Sounds" by SnakeF8 (same terms)
  oga_gunshots/  OpenGameArt "gunshot sounds" (Mosin Nagant and SKS field recordings)

Usage: python3 -I tools/import_gun_sounds.py [SOURCE_DIR] [--only EVENT ...]
The source folder holds the unzipped packs; it defaults to $GUN_SOUND_SRC, then to DEFAULT_SRC below. Needs ffmpeg and
ffprobe on the path. Only the events in TABLE are touched: their .ogg files are rewritten and their sounds.json
entries replaced; every other sound is left alone.

Each clip is one or more layers (a window of a source file). Every layer is downmixed to mono and aligned on its first
transient, the layers are summed, pitched (resampled, so lower is also slower), filtered, cut to length with a short
cosine fade-out and normalised:
  shots    to a target RMS over their first 250 ms (heavier calibers a little louder), a brick-wall limiter catching
           the few peaks that would pass -1 dBFS (never more than MAX_LIMIT_DB of gain reduction);
  handling to a target peak.
Output is mono (Minecraft only positions and attenuates mono sounds) 44.1 kHz Ogg Vorbis, written by vorbis_mono.py.
Shots use the packs' dry "Isolated" takes, except the blunderbuss, which keeps the 20 gauge's outdoor tail."""
import array
import json
import math
import os
import shutil
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vorbis_mono  # noqa: E402  (ffmpeg here has no libvorbis and its own Vorbis encoder is stereo-only)

DEFAULT_SRC = "/private/tmp/claude-501/-Users-josephheinz-Documents-my-mod/e5070e60-66ef-4cbd-b0af-d8b293198473/scratchpad/sound_src"
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "tomyen")
RATE = 44100
CEILING_DB = -1.0
MAX_LIMIT_DB = 6.5

S1 = "snake1/Snake's Authentic Gun Sounds/"
S1I = S1 + "Isolated/"
S1R = S1 + "Reloads, Cycling & More/WAV/"
S2 = "snake2/Snake's SECOND Authentic Gun Sounds/"
S2I = S2 + "Isolated/"
S2F = S2 + "Full Sounds/"
S2M = S2 + "& More/"
OGA = "oga_gunshots/sounds/"

MM9 = S2I + "9mm/WAV/9mm "
G20 = S2I + "20 Gauge/WAV/20 Gauge "
G20F = S2F + "20 Gauge/WAV/20 Gauge "
NATO308 = S2I + ".308 (7.62x51)/WAV/308 "
LR22 = S1I + ".22LR/WAV/22LR "
R556 = S1I + "5.56/WAV/556 "
AK = S1I + "7.62x39/WAV/762x39 "
R54 = S1I + "7.62x54R/WAV/762x54r "


def L(path, start=0.0, dur=1.5, gain=0.0):
    """A layer: `dur` seconds of `path` from `start`, at `gain` dB."""
    return {"path": path, "start": start, "dur": dur, "gain": gain}


def shot(*layers, pitch=1.0, length=0.9, fade=0.45, hp=60, lp=None, rms=-15.0):
    return {"layers": layers, "pitch": pitch, "length": length, "fade": fade, "hp": hp, "lp": lp, "rms": rms, "lead_db": 26.0}


def mech(*layers, pitch=1.0, length=None, fade=0.06, hp=80, lp=None, peak=-3.0):
    return {"layers": layers, "pitch": pitch, "length": length, "fade": fade, "hp": hp, "lp": lp, "peak": peak, "lead_db": 36.0}


# event -> list of (file name, clip, sounds.json pitch, sounds.json volume). The first file keeps the event's old name.
# Burst/spray variants take the last shot of the string, the one with a clean decay; earlier shots are cut off by the
# next one.
TABLE = {
    # --- 9mm: Glock, MP5, MAC-10 (and the M1911 a little heavier, the .357 heavier still) ---
    "glock_shot": [
        ("glock_shot", shot(L(MM9 + "Single Isolated.wav")), 1.0, 1.0),
        ("glock_shot_2", shot(L(MM9 + "Slow Burst Isolated.wav", 0.47)), 0.98, 1.0),
        ("glock_shot_3", shot(L(MM9 + "Burst Isolated.wav", 0.23)), 1.02, 0.95),
    ],
    "mp5_shot": [
        ("mp5_shot", shot(L(MM9 + "Slow Burst Isolated.wav", 0.47), pitch=0.96, lp=9000, length=0.75, rms=-15.5), 1.0, 1.0),
        ("mp5_shot_2", shot(L(MM9 + "Spray Isolated.wav", 0.47), pitch=0.96, lp=9000, length=0.75, rms=-15.5), 0.98, 1.0),
        ("mp5_shot_3", shot(L(MM9 + "Single Isolated.wav"), pitch=0.96, lp=9000, length=0.75, rms=-15.5), 1.02, 0.95),
    ],
    "mac10_shot": [
        ("mac10_shot", shot(L(MM9 + "Burst Isolated.wav", 0.23), pitch=1.05, length=0.6, fade=0.35, rms=-15.5), 1.0, 1.0),
        ("mac10_shot_2", shot(L(MM9 + "Spray Isolated.wav", 0.47), pitch=1.05, length=0.6, fade=0.35, rms=-15.5), 1.03, 0.95),
        ("mac10_shot_3", shot(L(MM9 + "Slow Burst Isolated.wav", 0.47), pitch=1.05, length=0.6, fade=0.35, rms=-15.5), 0.98, 1.0),
    ],
    "m1911_shot": [
        ("m1911_shot", shot(L(MM9 + "Single Isolated.wav"), pitch=0.89, length=0.95, rms=-14.5), 1.0, 1.0),
        ("m1911_shot_2", shot(L(MM9 + "Slow Burst Isolated.wav", 0.47), pitch=0.89, length=0.95, rms=-14.5), 0.97, 1.0),
        ("m1911_shot_3", shot(L(MM9 + "Burst Isolated.wav", 0.23), pitch=0.89, length=0.95, rms=-14.5), 1.02, 0.95),
    ],
    # No revolver report in the packs: a pitched-down 9mm for the body with a 7.62x39 under it for the magnum's blast.
    "magnum_shot": [
        ("magnum_shot", shot(L(MM9 + "Single Isolated.wav"), L(AK + "Single Isolated WAV.wav", 0.0, 1.0, -5.0),
                             pitch=0.88, length=1.1, fade=0.6, rms=-13.5), 1.0, 1.0),
        ("magnum_shot_2", shot(L(MM9 + "Slow Burst Isolated.wav", 0.47), L(AK + "Double Tap Isolated WAV.wav", 0.17, 0.7, -5.0),
                               pitch=0.88, length=1.1, fade=0.6, rms=-13.5), 0.97, 1.0),
        ("magnum_shot_3", shot(L(MM9 + "Burst Isolated.wav", 0.23), L(AK + "Burst Isolated WAV.wav", 0.36, 1.1, -5.0),
                               pitch=0.88, length=1.1, fade=0.6, rms=-13.5), 1.02, 0.95),
    ],
    # --- 5.56: M4, M249 ---
    "m4_shot": [
        ("m4_shot", shot(L(R556 + "Single Isolated WAV.wav"), rms=-14.0), 1.0, 1.0),
        ("m4_shot_2", shot(L(R556 + "Burst Isolated WAV.wav", 0.42), rms=-14.0), 0.98, 1.0),
        ("m4_shot_3", shot(L(R556 + "Spray Slow Isolated WAV.wav", 0.62), rms=-14.0), 1.02, 0.95),
    ],
    # Shorter and a touch lower: at 600 rpm the reports overlap.
    "m249_shot": [
        ("m249_shot", shot(L(R556 + "Spray Isolated WAV.wav", 0.42), pitch=0.94, length=0.6, fade=0.35, rms=-14.0), 1.0, 1.0),
        ("m249_shot_2", shot(L(R556 + "Spray Slow Isolated WAV.wav", 0.62), pitch=0.94, length=0.6, fade=0.35, rms=-14.0), 0.98, 1.0),
        ("m249_shot_3", shot(L(R556 + "Single Isolated WAV.wav"), pitch=0.94, length=0.6, fade=0.35, rms=-14.0), 1.02, 0.95),
    ],
    # --- 7.62x39: AKM ---
    "akm_shot": [
        ("akm_shot", shot(L(AK + "Single Isolated WAV.wav"), rms=-13.5), 1.0, 1.0),
        ("akm_shot_2", shot(L(AK + "Burst Isolated WAV.wav", 0.36), rms=-13.5), 0.98, 1.0),
        ("akm_shot_3", shot(L(AK + "Spray Isolated WAV.wav", 0.30), rms=-13.5), 1.02, 0.95),
    ],
    # --- .308: Galil ---
    "galil_shot": [
        ("galil_shot", shot(L(NATO308 + "Single Isolated.wav"), rms=-13.0), 1.0, 1.0),
        ("galil_shot_2", shot(L(NATO308 + "Burst Isolated.wav", 0.24), rms=-13.0), 0.98, 1.0),
        ("galil_shot_3", shot(L(NATO308 + "Spray Isolated.wav", 0.49), rms=-13.0), 1.02, 0.95),
    ],
    # --- 7.62x54R: AWP, a longer decay for a big rifle ---
    "awp_shot": [
        ("awp_shot", shot(L(R54 + "Single Isolated WAV.wav"),
                          length=1.2, fade=0.7, rms=-12.5), 1.0, 1.0),
        ("awp_shot_2", shot(L(R54 + "Spray Isolated WAV.wav", 0.51),
                            length=1.2, fade=0.7, rms=-12.5), 0.97, 1.0),
    ],
    # --- 20 gauge: the shotguns ---
    "remington_870_shot": [
        ("remington_870_shot", shot(L(G20 + "Single Isolated.wav"), length=1.0, fade=0.55, rms=-12.5), 1.0, 1.0),
        ("remington_870_shot_2", shot(L(G20 + "Slow Burst Isolated.wav", 0.50), length=1.0, fade=0.55, rms=-12.5), 0.97, 1.0),
        ("remington_870_shot_3", shot(L(G20 + "Double Tap Isolated.wav", 0.25), length=1.0, fade=0.55, rms=-12.5), 1.02, 0.95),
    ],
    "spas12_shot": [
        ("spas12_shot", shot(L(G20 + "Spray Isolated.wav", 0.56), pitch=1.03, length=1.0, fade=0.55, rms=-12.5), 1.0, 1.0),
        ("spas12_shot_2", shot(L(G20 + "Burst Isolated.wav", 0.31), pitch=1.03, length=1.0, fade=0.55, rms=-12.5), 0.98, 1.0),
        ("spas12_shot_3", shot(L(G20 + "Single Isolated.wav"), pitch=1.03, length=1.0, fade=0.55, rms=-12.5), 1.02, 0.95),
    ],
    # --- shoddy guns ---
    # A .22LR pop, dulled and dropped a little: a slam-fire pipe.
    "pipe_pistol_shot": [
        ("pipe_pistol_shot", shot(L(LR22 + "Single Isolated WAV.wav", 0.12), pitch=0.9, lp=7000, length=0.8, rms=-15.5), 1.0, 1.0),
        ("pipe_pistol_shot_2", shot(L(LR22 + "Double Tap Isolated WAV.wav", 0.20), pitch=0.9, lp=7000, length=0.8, rms=-15.5), 0.95, 1.0),
        ("pipe_pistol_shot_3", shot(L(LR22 + "Burst Isolated WAV.wav", 0.52), pitch=0.9, lp=7000, length=0.8, rms=-15.5), 1.04, 0.95),
    ],
    # A Mosin Nagant recorded outdoors: rough, with a real field echo.
    "pipe_rifle_shot": [
        ("pipe_rifle_shot", shot(L(OGA + "mosin.wav", 0.40, 1.3), hp=90, length=1.0, fade=0.55, rms=-13.5), 1.0, 1.0),
        ("pipe_rifle_shot_2", shot(L(OGA + "mosin.wav", 3.53, 1.3), hp=90, length=1.0, fade=0.55, rms=-13.5), 0.96, 1.0),
        ("pipe_rifle_shot_3", shot(L(OGA + "mosin.wav", 8.81, 1.3), hp=90, length=1.0, fade=0.55, rms=-13.5), 1.03, 0.95),
    ],
    # A 20 gauge with its tail, dropped a third and dulled: a fat black-powder boom.
    "blunderbuss_shot": [
        ("blunderbuss_shot", shot(L(G20F + "Single.wav", 0.0, 2.5), pitch=0.74, lp=4500, length=1.5, fade=0.9, rms=-12.0), 1.0, 1.0),
        ("blunderbuss_shot_2", shot(L(G20F + "Slow Burst.wav", 0.50, 2.5), pitch=0.74, lp=4500, length=1.5, fade=0.9, rms=-12.0), 0.95, 1.0),
    ],
    # An SKS recorded outdoors, a little lower: a rattly scrap repeater.
    "junk_repeater_shot": [
        ("junk_repeater_shot", shot(L(OGA + "sks.wav", 0.31, 1.2), pitch=0.92, hp=90, length=0.8, fade=0.45, rms=-14.5), 1.0, 1.0),
        ("junk_repeater_shot_2", shot(L(OGA + "sks.wav", 2.26, 1.2), pitch=0.92, hp=90, length=0.8, fade=0.45, rms=-14.5), 0.96, 1.0),
        ("junk_repeater_shot_3", shot(L(OGA + "sks.wav", 9.68, 1.2), pitch=0.92, hp=90, length=0.8, fade=0.45, rms=-14.5), 1.04, 0.95),
    ],
    # --- handling ---
    # Rifles (M4, AKM, Galil, MP5, MAC-10, M249): the AR charging handle.
    "rack_back": [("rack_back", mech(L(S1R + "AR Charging Handle WAV.wav", 0.0, 0.27), fade=0.04), 1.0, 1.0)],
    "rack_forward": [
        ("rack_forward", mech(L(S1R + "AR Charging Handle WAV.wav", 0.27, 0.6)), 1.0, 1.0),
        ("rack_forward_2", mech(L(S1R + "AR Bolt Release WAV.wav", 0.0, 0.5)), 1.0, 1.0),
    ],
    "pistol_slide_back": [("pistol_slide_back", mech(L(S2M + "Pistol/WAV/9mm Pistol Rack Part 1.wav", 0.0, 0.45)), 1.0, 1.0)],
    "pistol_slide_forward": [
        ("pistol_slide_forward", mech(L(S2M + "Pistol/WAV/9mm Pistol Rack Part 2.wav", 0.0, 0.5)), 1.0, 1.0),
        ("pistol_slide_forward_2", mech(L(S2M + "Pistol/WAV/9mm Pistol Slide Release.wav", 0.0, 0.3)), 1.0, 1.0),
    ],
    "mag_out": [
        ("mag_out", mech(L(S1R + "AR Reload Part 1 WAV.wav", 0.15, 0.42)), 1.0, 1.0),
        ("mag_out_2", mech(L(S1R + "AK Reload Part 1 WAV.wav", 0.10, 0.45)), 1.0, 1.0),
    ],
    "mag_in": [
        ("mag_in", mech(L(S1R + "AR Reload Part 2 WAV.wav", 0.27, 0.6)), 1.0, 1.0),
        ("mag_in_2", mech(L(S1R + "AK Reload Part 2 WAV.wav", 0.12, 0.6)), 1.0, 1.0),
    ],
    "pistol_mag_out": [("pistol_mag_out", mech(L(S2M + "Pistol/WAV/9mm Pistol Reload 1.wav", 0.0, 0.38), peak=-5.0), 1.0, 1.0)],
    "pistol_mag_in": [("pistol_mag_in", mech(L(S2M + "Pistol/WAV/9mm Pistol Reload 2.wav", 0.10, 0.47)), 1.0, 1.0)],
    # AWP: a .308 bolt cycle cut into its four strokes.
    "bolt_up": [("bolt_up", mech(L(S1R + "308 Bolt Cycle WAV.wav", 0.0, 0.13), fade=0.03), 1.0, 1.0)],
    "bolt_back": [("bolt_back", mech(L(S1R + "308 Bolt Cycle WAV.wav", 0.13, 0.29), fade=0.05), 1.0, 1.0)],
    "bolt_forward": [("bolt_forward", mech(L(S1R + "308 Bolt Cycle WAV.wav", 0.42, 0.25), fade=0.04), 1.0, 1.0)],
    "bolt_down": [("bolt_down", mech(L(S1R + "308 Bolt Cycle WAV.wav", 0.66, 0.30)), 1.0, 1.0)],
    # Revolver (.357): .44 Magnum and .32 S&W mechanisms.
    "revolver_cylinder_open": [("revolver_cylinder_open", mech(L(S2M + "Revolver/44 Magnum/WAV/44 Magnum Open Cylinder.wav", 0.15, 0.45), peak=-5.0), 1.0, 1.0)],
    "revolver_load": [
        ("revolver_load", mech(L(S2M + "Revolver/44 Magnum/WAV/44 Magnum Single Load.wav", 0.12, 0.26), peak=-5.0), 1.0, 1.0),
        ("revolver_load_2", mech(L(S2M + "Revolver/32 SW Long/WAV/32 SW Single Load.wav", 0.0, 0.4), peak=-5.0), 1.0, 1.0),
    ],
    "revolver_cylinder_close": [("revolver_cylinder_close", mech(L(S2M + "Revolver/32 SW Long/WAV/32 SW Close Cylinder.wav", 0.0, 0.5)), 1.0, 1.0)],
    "revolver_spin": [("revolver_spin", mech(L(S2M + "Revolver/32 SW Long/WAV/32 SW Rotate Cylinder.wav", 0.05, 0.4), peak=-6.0), 1.0, 1.0)],
    "revolver_hammer": [("revolver_hammer", mech(L(S2M + "Revolver/44 Magnum/WAV/44 Magnum Cock Hammer.wav", 0.0, 0.45), peak=-4.0), 1.0, 1.0)],
    # Shotguns: the pump stroke, a shell into the tube.
    "pump_back": [
        ("pump_back", mech(L(S1R + "Pump Reload Part 1 WAV.wav", 0.0, 0.6)), 1.0, 1.0),
        ("pump_back_2", mech(L(S1R + "Pump Reload Full WAV.wav", 0.05, 0.27), fade=0.04), 1.0, 1.0),
    ],
    "pump_forward": [
        ("pump_forward", mech(L(S1R + "Pump Reload Part 2 WAV.wav", 0.05, 0.6)), 1.0, 1.0),
        ("pump_forward_2", mech(L(S1R + "Pump Reload Full WAV.wav", 0.31, 0.6)), 1.0, 1.0),
    ],
    "shell_insert": [
        ("shell_insert", mech(L(S1R + "Pump Shell Load WAV.wav", 0.03, 0.55), peak=-5.0), 1.0, 1.0),
        ("shell_insert_2", mech(L(S2M + "Mag Pack/WAV/Single Shotgun Load.wav", 0.10, 0.39), peak=-5.0), 1.0, 1.0),
    ],
    # M249: a break action opening and closing for the feed cover, rounds clinking for the belt.
    "feed_cover_open": [("feed_cover_open", mech(L(S1R + "Single Shot Reload Part 1 WAV.wav", 0.15, 0.42)), 1.0, 1.0)],
    "feed_cover_close": [("feed_cover_close", mech(L(S1R + "Single Shot Reload Part 2 WAV.wav", 0.24, 0.6)), 1.0, 1.0)],
    "belt_rattle": [
        ("belt_rattle", mech(L(S2M + "Mag Pack/WAV/9mm Magazine Pack.wav", 0.20, 0.5), peak=-6.0), 1.0, 1.0),
        ("belt_rattle_2", mech(L(S2M + "Mag Pack/WAV/Mosin Top Load.wav", 0.22, 0.42), peak=-6.0), 1.0, 1.0),
    ],
    # --- the Gunsmith set ---
    # .38 Special from a two-inch barrel: a 9mm report dropped a little, with a .22's crack over it.
    "snub_revolver_shot": [
        ("snub_revolver_shot", shot(L(MM9 + "Single Isolated.wav"), L(LR22 + "Single Isolated WAV.wav", 0.12, 0.6, -7.0),
                                    pitch=0.93, length=0.85, fade=0.45, rms=-14.0), 1.0, 1.0),
        ("snub_revolver_shot_2", shot(L(MM9 + "Slow Burst Isolated.wav", 0.47), L(LR22 + "Double Tap Isolated WAV.wav", 0.20, 0.6, -7.0),
                                      pitch=0.93, length=0.85, fade=0.45, rms=-14.0), 0.97, 1.0),
    ],
    # The same round from the rifle's long barrel, outdoors: the 9mm's full take with its tail, lower.
    "lever_action_shot": [
        ("lever_action_shot", shot(L(S2F + "9mm/WAV/9mm Single.wav", 0.0, 2.0), pitch=0.84, length=1.2, fade=0.7, rms=-13.5), 1.0, 1.0),
        ("lever_action_shot_2", shot(L(S2F + "9mm/WAV/9mm Slow Burst.wav", 0.47, 2.0), pitch=0.84, length=1.2, fade=0.7, rms=-13.5),
         0.97, 1.0),
    ],
    # The 20 gauge, a touch lower for the short barrels; both barrels at once is two takes stacked on one onset, with a tail.
    "sawed_off_shot": [
        ("sawed_off_shot", shot(L(G20 + "Single Isolated.wav"), pitch=0.9, hp=50, length=1.0, fade=0.55, rms=-12.0), 1.0, 1.0),
        ("sawed_off_shot_2", shot(L(G20 + "Double Tap Isolated.wav", 0.25), pitch=0.9, hp=50, length=1.0, fade=0.55, rms=-12.0), 0.97, 1.0),
    ],
    "sawed_off_double_shot": [
        ("sawed_off_double_shot", shot(L(G20 + "Single Isolated.wav"), L(G20 + "Slow Burst Isolated.wav", 0.50, 1.5, -2.0),
                                       L(G20F + "Single.wav", 0.0, 2.5, -6.0), pitch=0.86, hp=45, length=1.5, fade=0.9, rms=-11.0),
         1.0, 1.0),
    ],
    # No flare pistol in the packs: the 20 gauge pitched up and dulled to a hollow pop.
    "flare_gun_shot": [
        ("flare_gun_shot", shot(L(G20 + "Single Isolated.wav"), pitch=1.35, lp=2500, length=0.6, fade=0.35, rms=-16.0), 1.0, 1.0),
    ],
    # No nailer either: a .22 pitched up and thinned, under the AR's bolt release for the driver's clack.
    "nail_gun_shot": [
        ("nail_gun_shot", shot(L(LR22 + "Single Isolated WAV.wav", 0.12), L(S1R + "AR Bolt Release WAV.wav", 0.0, 0.4, -3.0),
                               pitch=1.5, hp=300, lp=6000, length=0.35, fade=0.2, rms=-18.0), 1.0, 1.0),
        ("nail_gun_shot_2", shot(L(LR22 + "Double Tap Isolated WAV.wav", 0.20), L(S1R + "AR Bolt Release WAV.wav", 0.0, 0.4, -3.0),
                                 pitch=1.55, hp=300, lp=6000, length=0.35, fade=0.2, rms=-18.0), 1.03, 1.0),
    ],
    # Nor a spear gun: a bipod's legs slamming down, dropped low, over a muffled .22 for the air's thump.
    "harpoon_gun_shot": [
        ("harpoon_gun_shot", shot(L(S2M + "Bipod/WAV/Bipod Impact Both Legs.wav", 0.0, 0.6),
                                  L(LR22 + "Single Isolated WAV.wav", 0.12, 0.8, -6.0), pitch=0.72, lp=2500, length=0.7, fade=0.4,
                                  rms=-15.0), 1.0, 1.0),
    ],
    # The Fish Gun's FWOOMP: a 20 gauge slowed to half speed and muffled into a deep air thump, the bipod's slam under it
    # for the valve banging open. (The rush of the fish leaving is vanilla's wind charge, played alongside in game.)
    "fish_gun_fire": [
        ("fish_gun_fire", shot(L(G20 + "Single Isolated.wav"), L(S2M + "Bipod/WAV/Bipod Impact Both Legs.wav", 0.0, 0.6, -4.0),
                               pitch=0.5, hp=40, lp=1100, length=0.9, fade=0.55, rms=-15.0), 1.0, 1.0),
        ("fish_gun_fire_2", shot(L(G20 + "Single Isolated.wav"), L(S2M + "Bipod/WAV/Bipod Impact Both Legs.wav", 0.0, 0.6, -4.0),
                                 pitch=0.56, hp=40, lp=1250, length=0.85, fade=0.5, rms=-15.5), 0.96, 1.0),
    ],
    # A single-shot's break action opening and snapping shut.
    "break_open": [("break_open", mech(L(S1R + "Single Shot Reload Part 1 WAV.wav", 0.1, 0.46)), 1.0, 1.0)],
    "break_close": [("break_close", mech(L(S1R + "Single Shot Reload Part 2 WAV.wav", 0.15, 0.6)), 1.0, 1.0)],
    # A lever gun's stroke cut in two, and a round pushed through its loading gate.
    "lever_down": [("lever_down", mech(L(S1R + "Lever Cycle WAV.wav", 0.0, 0.3), fade=0.04), 1.0, 1.0)],
    "lever_up": [("lever_up", mech(L(S1R + "Lever Cycle WAV.wav", 0.3, 0.5)), 1.0, 1.0)],
    "lever_load": [("lever_load", mech(L(S1R + "Lever Reload WAV.wav", 0.5, 0.4), peak=-5.0), 1.0, 1.0)],
    "bipod_deploy": [("bipod_deploy", mech(L(S2M + "Bipod/WAV/Bipod Deploy Both Legs.wav", 0.0, 0.8), peak=-6.0), 1.0, 1.0)],
    "bipod_fold": [("bipod_fold", mech(L(S2M + "Bipod/WAV/Bipod Raise Both Legs.wav", 0.0, 0.8), peak=-6.0), 1.0, 1.0)],
    # The hammer falling on a dud.
    "misfire_click": [
        ("misfire_click", mech(L(S2M + "Pistol/WAV/9mm Pistol Dry Fire.wav", 0.0, 0.3), peak=-5.0), 1.0, 1.0),
        ("misfire_click_2", mech(L(S2M + "Revolver/32 SW Long/WAV/32 SW Hammer Decock.wav", 0.08, 0.4), peak=-5.0), 1.0, 1.0),
    ],
}


# ------------------------------------------------------------------------------------------------------------ audio

def ffmpeg(args, data=None):
    return subprocess.run(["ffmpeg", "-v", "error", "-y"] + args, input=data, capture_output=True, check=True).stdout


def decode(path, start, dur):
    raw = ffmpeg(["-ss", f"{start:.4f}", "-t", f"{dur:.4f}", "-i", path, "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"])
    return array.array("f", raw)


def db(x):
    return 20 * math.log10(x) if x > 0 else -120.0


def onset(xs, below_peak_db=36.0, back=0.002):
    """Index just before the first sample within `below_peak_db` of the peak: trims leading silence and handling noise."""
    peak = max((abs(x) for x in xs), default=0.0)
    if peak == 0.0:
        return 0
    limit = peak * 10 ** (-below_peak_db / 20)
    for i, x in enumerate(xs):
        if abs(x) >= limit:
            return max(0, i - int(back * RATE))
    return 0


def render(clip, src):
    """Layers aligned and summed, then pitched and filtered: float samples, not yet trimmed or normalised."""
    mixed = array.array("f")
    for layer in clip["layers"]:
        xs = decode(os.path.join(src, layer["path"]), layer["start"], layer["dur"])
        xs = xs[onset(xs, clip["lead_db"]):]
        g = 10 ** (layer["gain"] / 20)
        if len(xs) > len(mixed):
            mixed.extend([0.0] * (len(xs) - len(mixed)))
        for i, x in enumerate(xs):
            mixed[i] += x * g
    filters = []
    if clip["pitch"] != 1.0:
        filters.append(f"asetrate={RATE * clip['pitch']:.0f},aresample={RATE}")
    if clip["hp"]:
        filters.append(f"highpass=f={clip['hp']}")
    if clip["lp"]:
        filters.append(f"lowpass=f={clip['lp']}")
    if not filters:
        return mixed
    raw = ffmpeg(["-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-", "-af", ",".join(filters), "-f", "f32le", "-"],
                 mixed.tobytes())
    return array.array("f", raw)


def shape(xs, clip):
    """Trim the lead, cut to length with fades, and work out the gain (dB) and whether the limiter is needed."""
    xs = xs[onset(xs, clip["lead_db"]):]
    if clip["length"]:
        xs = xs[:int(clip["length"] * RATE)]
    n = len(xs)
    fade_in = int(0.002 * RATE)
    fade_out = min(n, int(clip["fade"] * RATE))
    for i in range(min(fade_in, n)):
        xs[i] *= i / fade_in
    for i in range(fade_out):
        xs[n - fade_out + i] *= 0.5 + 0.5 * math.cos(math.pi * i / fade_out)
    peak = db(max(abs(x) for x in xs))
    if "rms" in clip:
        body = xs[:int(0.25 * RATE)]
        rms = db(math.sqrt(sum(x * x for x in body) / len(body)))
        gain = clip["rms"] - rms
        over = peak + gain - CEILING_DB
        if over > MAX_LIMIT_DB:
            gain -= over - MAX_LIMIT_DB
        limit = peak + gain > CEILING_DB
    else:
        gain = clip["peak"] - peak
        limit = False
    return xs, gain, limit


def encode(xs, gain, limit, path):
    filters = [f"volume={gain:.2f}dB"]
    if limit:
        filters.append(f"alimiter=limit={10 ** (CEILING_DB / 20):.4f}:attack=1:release=40:level=disabled")
    raw = ffmpeg(["-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-", "-af", ",".join(filters), "-f", "f32le", "-"],
                 xs.tobytes())
    vorbis_mono.encode(array.array("f", raw).tolist(), path)


def measure(path):
    """Duration (s), channels, peak dBFS and RMS dBFS over the first 250 ms (the body) of an encoded file."""
    probe = json.loads(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=channels", "-of", "json", path],
                                      capture_output=True, text=True, check=True).stdout)
    xs = array.array("f", ffmpeg(["-i", path, "-f", "f32le", "-"]))
    channels = probe["streams"][0]["channels"]
    peak = db(max(abs(x) for x in xs))
    body = xs[:int(0.25 * RATE) * channels]
    rms = db(math.sqrt(sum(x * x for x in body) / len(body)))
    return len(xs) / channels / RATE, channels, peak, rms


# ------------------------------------------------------------------------------------------------------------- main

def main():
    args = sys.argv[1:]
    only = set()
    if "--only" in args:
        k = args.index("--only")
        only = set(args[k + 1:])
        args = args[:k]
    src = args[0] if args else os.environ.get("GUN_SOUND_SRC", DEFAULT_SRC)
    if shutil.which("ffmpeg") is None or shutil.which("ffprobe") is None:
        raise SystemExit("ffmpeg and ffprobe are needed on the path")
    if not os.path.isdir(src):
        raise SystemExit(f"source folder not found: {src} (pass it as an argument or set GUN_SOUND_SRC)")
    out = os.path.join(ASSETS, "sounds")
    sounds_path = os.path.join(ASSETS, "sounds.json")
    with open(sounds_path, encoding="utf-8") as f:
        sounds = json.load(f)

    rows = []
    for event, variants in TABLE.items():
        if only and event not in only:
            continue
        entries = []
        for name, clip, pitch, volume in variants:
            path = os.path.join(out, name + ".ogg")
            xs, gain, limit = shape(render(clip, src), clip)
            encode(xs, gain, limit, path)
            dur, ch, peak, rms = measure(path)
            source = " + ".join(f"{os.path.basename(l['path'])}@{l['start']:g}" for l in clip["layers"])
            rows.append((event, name, source, dur, ch, peak, rms, os.path.getsize(path)))
            entry = {"name": "tomyen:" + name}
            if pitch != 1.0:
                entry["pitch"] = pitch
            if volume != 1.0:
                entry["volume"] = volume
            entries.append(entry if len(entry) > 1 else "tomyen:" + name)
        old = sounds.get(event, {})
        sounds[event] = {"subtitle": old.get("subtitle", "subtitles.tomyen." + event), "sounds": entries}

    with open(sounds_path, "w", encoding="utf-8") as f:
        f.write(json.dumps(sounds, indent=2, ensure_ascii=False) + "\n")

    print(f"{'file':24s} {'dur':>5s} {'ch':>2s} {'peak':>6s} {'rms250':>6s} {'bytes':>6s}  source")
    for event, name, source, dur, ch, peak, rms, size in rows:
        print(f"{name:24s} {dur:5.2f} {ch:2d} {peak:6.1f} {rms:6.1f} {size:6d}  {source}")
    print(f"{len(rows)} files, {sum(r[7] for r in rows) / 1024:.0f} KiB")


if __name__ == "__main__":
    main()
