package com.jheinz.tomyen.isle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Everything a player has lost to the Isle of the Damned and not yet won back: the items that would have dropped when
 * they died (with anything they've since died holding on the Isle itself), a share of their experience, the gear their
 * Revenant wears and the face it wears it with, and how deep it's all sunk.
 *
 * <p>Stored as data (see {@link Hoards}), never as item entities, so nothing in it can despawn. One hoard per player;
 * dying again before it's won back adds to it. {@code id} is new for every hoard, so a Gravestone can tell whether it
 * still leads anywhere.
 *
 * @param gravestone where the Gravestone that leads to it stands, if anywhere
 * @param arena      the centre of the Revenant's arena at the current depth, once it's been built there
 */
public record Hoard(UUID id, UUID owner, String ownerName, ResolvableProfile profile, List<ItemStack> items, int experience,
		Loadout loadout, IsleDepth depth, Optional<GlobalPos> gravestone, Optional<BlockPos> arena) {
	private static final Codec<IsleDepth> DEPTH_CODEC = Codec.INT.xmap(IsleDepth::of, depth -> depth.index);

	public static final Codec<Hoard> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Hoard::id),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Hoard::owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Hoard::ownerName),
			ResolvableProfile.CODEC.optionalFieldOf("profile", ResolvableProfile.Static.EMPTY).forGetter(Hoard::profile),
			ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Hoard::items),
			Codec.INT.optionalFieldOf("experience", 0).forGetter(Hoard::experience),
			Loadout.CODEC.optionalFieldOf("loadout", Loadout.EMPTY).forGetter(Hoard::loadout),
			DEPTH_CODEC.optionalFieldOf("depth", IsleDepth.SHORE).forGetter(Hoard::depth),
			GlobalPos.CODEC.optionalFieldOf("gravestone").forGetter(Hoard::gravestone),
			BlockPos.CODEC.optionalFieldOf("arena").forGetter(Hoard::arena))
			.apply(i, Hoard::new));

	/** The items as copies, so a caller can't change the stored ones. */
	public List<ItemStack> itemCopies() {
		List<ItemStack> copies = new ArrayList<>(items.size());
		for (ItemStack stack : items) {
			copies.add(stack.copy());
		}
		return copies;
	}

	public Hoard withItems(List<ItemStack> newItems, int newExperience) {
		return new Hoard(id, owner, ownerName, profile, List.copyOf(newItems), newExperience, loadout, depth, gravestone, arena);
	}

	public Hoard withLoadout(Loadout newLoadout) {
		return new Hoard(id, owner, ownerName, profile, items, experience, newLoadout, depth, gravestone, arena);
	}

	/** Sunk (or raised) to another depth: its arena there isn't built yet. */
	public Hoard withDepth(IsleDepth newDepth) {
		return new Hoard(id, owner, ownerName, profile, items, experience, loadout, newDepth, gravestone, Optional.empty());
	}

	public Hoard withGravestone(Optional<GlobalPos> newGravestone) {
		return new Hoard(id, owner, ownerName, profile, items, experience, loadout, depth, newGravestone, arena);
	}

	public Hoard withArena(BlockPos centre) {
		return new Hoard(id, owner, ownerName, profile, items, experience, loadout, depth, gravestone, Optional.of(centre.immutable()));
	}

	public Hoard withProfile(String name, ResolvableProfile newProfile) {
		return new Hoard(id, owner, name, newProfile, items, experience, loadout, depth, gravestone, arena);
	}
}
