package com.jheinz.tomyen.fae;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * A caught fairy. Used on a block it's placed as a lantern-like light (see {@link FairyInABottleBlock}); used in the
 * air, the fairy is let go, and heals its rescuer {@value #HEAL} health (with a moment of Regeneration) on its way.
 */
public class FairyInABottleItem extends BlockItem {
	private static final float HEAL = 6.0f;

	public FairyInABottleItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel serverLevel) {
			player.heal(HEAL);
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0));
			Vec3 at = player.getEyePosition().add(player.getViewVector(1.0f).scale(0.8));
			Fairy fairy = Fae.FAIRY.create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
			if (fairy != null) {
				fairy.snapTo(at.x, at.y - 0.3, at.z, player.getYRot(), 0.0f);
				fairy.setFriend(player);
				serverLevel.addFreshEntity(fairy);
				fairy.poof();
			}
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), Fae.FAIRY_GIGGLE, SoundSource.PLAYERS, 1.0f, 1.2f);
		}
		return InteractionResult.SUCCESS.heldItemTransformedTo(ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
	}
}
