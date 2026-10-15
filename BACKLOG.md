# Backlog

Ideas approved for later. Not started yet.

## Early game
- **Field forge / bloomery.** A crude early Foundry that smelts iron into a weaker "bloom steel". It gives a few cheap steel-tier recipes so the jump to the real Foundry isn't a wall.
- **Herbalism.** A few new plants in vanilla biomes (yarrow, garlic, etc.) that feed bandages, wards against vampires and werewolves, anti-radiation tea and cooking.
- **Scrap Bench.** A cheap first workstation, before the Weapon Station. Breaks junk down into tech scrap, nuggets and springs, and assembles homemade guns and early gadgets. The Scavenger tier's home base.
- **Blueprints.** Found in loot. Each unlocks a Weapon Station or Scrap Bench recipe early (e.g. an "M1911 schematic"), so exploring has a progression payoff.
- **Scarecrow / decoy dummy.** A placeable that UFOs try to abduct instead of your cows, and that mobs briefly attack.

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

Undead and evil (silver and holy weapons matter):
- **Bog Hag.** A swamp witch whose curses make fishing and mining go badly for a while. Drops herbs and charms (ties to herbalism).
- **Grave Digger.** A zombie with a shovel that tunnels under walls at night.
- **Banshee.** A ghost whose wail blinds you and wakes nearby undead. Holy weapons one-shot it.

Nature and early game:
- **Wild Boar.** A jungle and taiga pig that charges. Drops tusks for spears and harpoon heads.
- **Giant Catfish / River Monster.** A rare river boss that drags you underwater. Its trophy goes on a giant Fish Mount.
- **Crow Murder.** Flocks of crows that steal crops and shiny items. The scarecrow counters them.
- **Moose.** A big neutral animal in snowy taiga. Drops leather and antler trophies.

Mutated by uranium:
- **Glowing Rat King.** A cluster of rats that splits when hit. Found near uranium deposits.
- **Two-Headed Cow.** A passive mutant you can milk for anti-radiation milk.

## Wacky weapons and gear
- **Fish gun.** Fires your raw fish as ammo, and damage scales with fish weight. A trophy tuna is a cannonball.
- **Leaf blower.** Knocks back mobs and items; upgrades into a vacuum that sucks up drops.
- **Cathode TV helmet.** Wear a Cathode Walker's screen: static night vision, and your face shows emotes to other players.
- **Soda-bottle jetpack.** An early-game, terrible jetpack that runs on sugar and slime.
- **Trombone.** Plays notes. A sad trombone curses mobs with Weakness; a war march buffs allies.

## Wacky mobs
- **Shrimp with a gun.** Human-sized (not tiny): an ocean mob that shoots.
- **Gnome army.** Garden gnomes that come alive at night near fae biomes and steal your crops in formation.
- **The Accountant.** A slime in a suit, linked to the banker. Audits you, steals emeralds if your debt is overdue, and you can also **take out loans from him**.
- **Josh (villager).** A villager named Josh who only sells Fish Fish.

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
- **Fish Fish.** A new fish species named "Fish Fish".
- **Josh.** A new fish species named "Josh".

## Gear
- **Weapon skins.** Cosmetic finishes from rare drops, e.g. UFO chrome, a werewolf-fur grip, a fae-wood stock.
- **Taxidermy.** Extend the Fish Mount idea to mobs you've killed, e.g. a werewolf head or a Cathode Walker screen on the wall.
