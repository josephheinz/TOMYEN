package com.jheinz.tomyen.dragonscale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;

/**
 * The Ender Dragon's scales: at the end of every dragon's death, first or respawned, it drops loot table
 * {@code tomyen:entities/ender_dragon_scales} (12 to 16 Ender Dragon Scales). The dragon is never killed the usual
 * way (its health is set to nothing at the end of its death flight), so its own loot table never drops; this is
 * hooked into the end of its death animation instead (EnderDragonScalesMixin), alongside its last burst of XP.
 * <p>
 * The dragon dies hovering over the exit portal, where anything dropped would fall through to the Overworld, so in
 * its own fight the scales land on top of the portal's bedrock pillar, still, beside the egg. A dragon summoned
 * elsewhere drops them where it is. Like the XP, they need the {@code mob_drops} game rule.
 */
public final class EnderDragonScales {
	public static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(Registries.LOOT_TABLE, MyMod.id("entities/ender_dragon_scales"));

	private EnderDragonScales() {
	}

	public static void drop(EnderDragon dragon, ServerLevel level) {
		if (!level.getGameRules().get(GameRules.MOB_DROPS)) {
			return;
		}
		DamageSource source = dragon.getLastDamageSource() != null ? dragon.getLastDamageSource() : level.damageSources().generic();
		EnderDragonFight fight = dragon.getDragonFight();
		Vec3 at;
		if (fight != null) {
			BlockPos pillarTop = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EnderDragonFight.getPodiumLocation(dragon.getFightOrigin()));
			at = Vec3.atBottomCenterOf(pillarTop).add(0.0, 0.1, 0.0);
		} else {
			at = dragon.position();
		}
		dragon.dropFromLootTable(level, source, true, LOOT_TABLE, stack -> {
			// Dropped dead still: anything that rolled off the pillar would fall into the portal.
			ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack, 0.0, 0.0, 0.0);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		});
	}
}
