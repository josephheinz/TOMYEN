# Backlog

Ideas approved for later. Not started yet.

## Mobs
Rogue machines (early to mid game; drop parts):
- **Rust Crawler.** A dog-sized scrap spider that comes in packs out of junk and drags small items away. An early source of tech scrap.
- **Vending Mimic.** A vending machine that lurks in villages. It sells you a snack, or bites if you shake it. Drops coins and tubes.
- **Roomba Swarm.** Tiny vacuum bots that eat dropped items. Kill them to get the items back.
- **Drone Hornet.** A small flying quadcopter that strafes you with BBs. Countered by the shotgun and flare gun.

Alien and meteor (mid game):
- **Grey Scout.** A taller alien that blinks short distances and carries a stun wand. Comes with UFO landings.
- **Crystal Mite.** Grows out of meteorite stone and shatters into star crystal shards.
- **Probe Drone.** A floating orb that scans you. Stay in its beam too long and a UFO comes.

Fae and Feywild:
- **Will-o'-Wisp.** A floating light at night that lures you off paths, toward treasure or a trap.
- **Changeling.** Looks like a villager or a cow until you get close. Steals items and flees to a fae ring.
- **Toadstool Troll.** A big, slow Feywild brute covered in Fae Mushrooms. Drops Fae Dust and fae-wood.

Mutated by uranium:
- **Glowing Rat King.** A cluster of rats that splits when hit. Found near uranium deposits.
- **Two-Headed Cow.** A passive mutant you can milk for anti-radiation milk.

## Vehicles, structures and big systems
- **Hogfire air force.** Rocket-saddle any animal: cow bombers, chicken interceptors, a horse with a minigun.
- **Bathtub boat.** A fast speedboat made from a bathtub and an outboard motor that runs on kerosene.
- **Abandoned shopping mall structure.** Vending mimics, mannequins that move when you aren't looking, and a food court full of loot. We might base the building on an existing structure, e.g. from Planet Minecraft (check the license and give credit).
- **Dance-off.** Villagers challenge you to a rhythm minigame that reuses the fishing QTE system.
- **Slot machine block.** Eats emeralds. The jackpot is guaranteed trophy-class bait (plus other prizes).
- **Item fusing (late game).** Throw two items into a fae pond (or a late-game fusion setup) and they fuse into a new hybrid item, e.g. fish + sword = swordfish sword. Not every pair has to combine, but **design a large set of brand-new fusion-only items** so it's worth experimenting with.

## Cosmic and Feywild
- **Moon cheese meteorite.** A meteorite variant that's literally cheese. Mine it and eat it for low gravity.
- **Upside-down Feywild region.** A biome where you walk on the ceiling.

## World events
- **Raining fish.** A rare weather event where fish fall from the sky; you can catch them mid-air with a rod.
- **Meteor of bees.** A meteorite variant that cracks open into an angry alien hive. It drops cosmic honey that gives Jump Boost.

## Wacky machines
- **Microwave block.** Put anything in. Food gets cooked, metal sparks and explodes, eggs pop, and a fish comes out as "Cursed Fish". The results are deliberately chaotic.
- **Blender.** Blends two foods into a smoothie with both their effects. Blending a mob egg is... a choice.
- **Fax machine.** Sends items to another fax machine anywhere, but sometimes it only prints half the stack.

## Isle of the Damned (death dimension)
When you die, a copy of you, as you were at death (gear, held weapon, armor), is summoned in a new dimension called **the Isle of the Damned**, holding your dropped items. You have to get there and beat your dead self to get your stuff back. A "Dark Souls corpse run" for the mod.

**Getting there** (undecided; options):
- **Soul compass / Damned Key:** dropped by your death, or crafted from a Soul Charge plus something you lost. Use it at a gravestone that appears where you died.
- **Gravestone portal:** your death spot spawns a gravestone. Right-click it with a holy or soul item to be pulled down to the Isle.
- **Ferryman:** a boatman NPC at night on rivers who rows you over, paying in emeralds or fish (ties to fishing and the bank).
- **Soul Vacuum reversed:** overcharge the existing Soul Vacuum to tear a rift.
- **Dying with no way back:** your items stay safe on the Isle until you reach it. Nothing despawns there, which removes the usual 5-minute item-despawn panic.

**The fight:**
- Your Revenant copies your death loadout and fights with your weapons. It's harder if you died geared up, and easy if you died naked.
- Beat it to recover your items and some of your XP.
- Die on the Isle and the Revenant gets stronger, or your items move deeper into the Isle.
- Multiplayer: other players' Revenants are there too.

**Every mob that dies being there:** this is the lag risk. Never keep real entities for every death. Options:
- Store a tiny **death record** (mob type, position, maybe name or gear), not an entity, in a capped ring buffer per region. Most deaths aren't kept at all.
- **Spawn only near players on the Isle:** shades materialise from the records around you and dissolve when you leave. Cap active shades (e.g. 20–40).
- **Notable deaths only:** player deaths, named mobs, elites (Schnozzle, Pack Leader, werewolves, mech pilots, trophy fish?), and things the player killed. Ordinary despawns and farm kills are skipped.
- **Aggregate the rest:** routine deaths add to a "Soul Tide" counter per mob type. The Isle spawns waves scaled to the tide instead of 1:1 copies, so a big mob farm makes the Isle more dangerous without storing thousands of entries.
- Older records fade over time.

**Ties to existing content:**
- The soul and soul steel line (Soul Vacuum, Soul Charges).
- Holy and evil damage (Revenants are `#mymod:evil`).
- The Star Forge's Soulbound infusion: Soulbound gear stays with you, so it doesn't end up on the Isle.

## Fishing
- **Josh.** A new fish species named "Josh".

## Gear
- **Weapon skins.** Cosmetic finishes from rare drops, e.g. UFO chrome, a werewolf-fur grip, a fae-wood stock.
- **Taxidermy.** Extend the Fish Mount idea to mobs you've killed, e.g. a werewolf head or a Cathode Walker screen on the wall.
