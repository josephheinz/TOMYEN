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
- **Abandoned shopping mall structure.** Vending mimics, mannequins that move when you aren't looking, and a food court full of loot. We might base the building on an existing structure, e.g. from Planet Minecraft (check the license and give credit).
- **Dance-off.** Villagers challenge you to a rhythm minigame that reuses the fishing QTE system.
- **Slot machine block.** Eats emeralds. The jackpot is guaranteed trophy-class bait (plus other prizes).
- **Item fusing: the ranged batch.** Fusing is built (pond, reactor, hints) with 21 fusions in. Still to build, paused partway with unfinished work in `.claude/worktrees/agent-ac00d875ed4ca8956`: Fish Gun + Remington 870 → Chum Cannon (pond); Grapple Claw + Harpoon Gun → Harpoon Hookshot (pond); Crucifix + Ray Gun → Holy Ray (reactor); Shrimp Pistol + Crow Feather → Feather Duster (pond); Leaf Blower + Soul Vacuum → Soul Gale (reactor). Their recipes and riddles are already in `tools/fusion_data.py`.
  - Rejected, don't pitch again: banana bomb, pistol shrimp blaster, comet launcher, snot rocket launcher, olfactory Neuralink module, Skullcrane rig, Sea Monster Mortar, Exorcist.

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

## Isle of the Damned follow-ups
The Isle itself is built (gravestone, Revenant, depths, shades, Soul Tide). Still to do:
- **Soul Wisp item.** Shades drop it; three plus soul sand or soul soil make a Soul Charge, and it works as a gravestone offering. Shades drop Ectoplasm for now.
- **Shade Heart.** A boss shade's drop. Offered at your gravestone, it raises your hoard one depth (the `raiseHoard` code in `isle/PayingRespects` is already there, just unused).
- **Ghost shader (maybe).** A real shader for shades and Revenants: a glowing rim and a slight shimmer, instead of the current tint and transparency.
- **Other ways in (not chosen yet):** a Soul Compass, a Ferryman who rows you over at night for emeralds or fish, or an overcharged Soul Vacuum tearing a rift.
- **Not tested yet:** multiplayer, Revenants that use guns, and pass 2 hasn't been run in game.

## Fishing
- **Josh.** A new fish species named "Josh".

## Gear
- **Weapon skins.** Cosmetic finishes from rare drops, e.g. UFO chrome, a werewolf-fur grip, a fae-wood stock.
- **Taxidermy.** Extend the Fish Mount idea to mobs you've killed, e.g. a werewolf head or a Cathode Walker screen on the wall.
