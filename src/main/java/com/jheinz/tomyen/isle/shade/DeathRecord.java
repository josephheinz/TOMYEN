package com.jheinz.tomyen.isle.shade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * What the Isle remembers of one notable death: a few dozen bytes, never an entity.
 *
 * @param id       unique for the world's records, so a shade can lay its own record to rest
 * @param type     the mob's entity type
 * @param name     its custom name, if it had one
 * @param gear     the item ids it wore and held (main hand, off hand, head, chest, legs, feet; {@code minecraft:air} for an
 *                 empty slot), or empty if it had none
 * @param killer   the player who killed it, if one did
 * @param owner    the player whose named pet it was, if it was one
 * @param time     the game time it died at (records fade, see {@link IsleSouls#FADE_TICKS})
 * @param origin   a short description of where it fell: dimension and a rough position (its 512-block region's centre)
 */
public record DeathRecord(long id, Identifier type, Optional<String> name, List<Identifier> gear, Optional<UUID> killer,
		Optional<UUID> owner, long time, String origin) {
	public static final Codec<DeathRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(DeathRecord::id),
			Identifier.CODEC.fieldOf("type").forGetter(DeathRecord::type),
			Codec.STRING.optionalFieldOf("name").forGetter(DeathRecord::name),
			Identifier.CODEC.listOf().optionalFieldOf("gear", List.of()).forGetter(DeathRecord::gear),
			UUIDUtil.CODEC.optionalFieldOf("killer").forGetter(DeathRecord::killer),
			UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(DeathRecord::owner),
			Codec.LONG.fieldOf("time").forGetter(DeathRecord::time),
			Codec.STRING.optionalFieldOf("origin", "").forGetter(DeathRecord::origin))
			.apply(i, DeathRecord::new));

	public boolean isPet() {
		return owner.isPresent();
	}

	public Optional<EntityType<?>> entityType() {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(type);
	}
}
