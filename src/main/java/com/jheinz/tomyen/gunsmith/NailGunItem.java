package com.jheinz.tomyen.gunsmith;

import java.util.List;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.Ammo;
import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.GunSpread;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.MagazineItem;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.attachment.Attachments;

/**
 * A framing nailer rebuilt as a weapon: a salvaged Skullcrane servo arm drives the driver blade, so it fires nails as fast as
 * the trigger can be pulled, quietly and without powder, but they don't carry far. Fed from a nail strip or a coil drum, packed
 * and swapped like any magazine.
 */
public class NailGunItem extends GunItem {
	public NailGunItem(Properties properties, GunStats stats, List<MagazineItem> magazines, List<Ammo> ammo) {
		super(properties, stats, magazines, ammo);
	}

	@Override
	public boolean fireRound(ServerLevel level, Player player, ItemStack gun, InteractionHand hand) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		boolean free = player.hasInfiniteMaterials();
		Ammo round = loaded == null || loaded.isEmpty() ? (free ? ammo().getFirst() : null) : Ammo.find(ammo(), loaded.ammo());
		if (round == null) {
			return false;
		}
		GunStats fitted = stats(gun, player);
		float spread = player instanceof ServerPlayer serverPlayer ? GunSpread.serverShot(serverPlayer, fitted) : 0.0f;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		if (spread > 0.0f) {
			float radius = spread * (float) Math.sqrt(player.getRandom().nextFloat());
			float angle = player.getRandom().nextFloat() * (float) (Math.PI * 2.0);
			look = Vec3.directionFromRotation(player.getXRot() + radius * (float) Math.sin(angle),
					player.getYRot() + radius * (float) Math.cos(angle));
		}
		Vec3 end = eye.add(look.scale(fitted.range()));
		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
				entity -> !entity.isSpectator() && entity.isPickable(), eye.distanceToSqr(end));
		Vec3 stop = end;
		if (entityHit != null) {
			stop = entityHit.getLocation();
			GunItem.hit(level, player, round, entityHit.getEntity());
			level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 4, 0.08, 0.08, 0.08, 0.2);
		} else if (blockHit.getType() == HitResult.Type.BLOCK) {
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockHit.getBlockPos()));
			level.sendParticles(debris, stop.x, stop.y, stop.z, 4, 0.03, 0.03, 0.03, 0.08);
		}
		// No powder: a puff of air at the nose and a glint of the nail, no smoke.
		Vec3 muzzle = eye.add(look.scale(1.1)).add(0, -0.15, 0);
		level.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 1, 0.02, 0.02, 0.02, 0.01);
		ItemParticleOption glint = new ItemParticleOption(ParticleTypes.ITEM, round.item());
		Vec3 path = stop.subtract(muzzle);
		int steps = (int) (path.length() / 2.5);
		for (int i = 1; i < steps; i++) {
			Vec3 p = muzzle.add(path.scale(i / (double) steps));
			level.sendParticles(glint, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), stats().sound(), SoundSource.PLAYERS,
				0.7f * Attachments.loudness(gun), 0.95f + player.getRandom().nextFloat() * 0.1f);
		player.awardStat(Stats.ITEM_USED.get(this));

		if (!free) {
			MagazineContents left = loaded.withRounds(loaded.rounds() - 1);
			gun.set(ModComponents.LOADED_MAGAZINE, left);
			if (gun.nextDamageWillBreak()) {
				// It's about to fall apart: the magazine comes out, with whatever's left in it.
				ItemStack magazine = new ItemStack(loadedMagazine(gun));
				if (!left.isEmpty()) {
					magazine.set(ModComponents.MAGAZINE_CONTENTS, left);
				}
				gun.remove(ModComponents.LOADED_MAGAZINE);
				gun.remove(ModComponents.LOADED_MAGAZINE_TYPE);
				player.getInventory().placeItemBackInInventory(magazine, Prediction.SERVER_ONLY);
			}
			gun.hurtAndBreak(1, player, hand);
		}
		return true;
	}
}
