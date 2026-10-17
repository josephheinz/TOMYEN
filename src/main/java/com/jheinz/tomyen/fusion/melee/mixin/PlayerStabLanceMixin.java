package com.jheinz.tomyen.fusion.melee.mixin;

import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.jheinz.tomyen.fusion.melee.GnomeLanceItem;

/** The Gnome Lance's jabs and charges hit harder the faster the player is going (GnomeLanceItem). */
@Mixin(Player.class)
public abstract class PlayerStabLanceMixin {
	@ModifyVariable(method = "stabAttack", at = @At("HEAD"), argsOnly = true)
	private float tomyen$joust(float baseDamage) {
		Player self = (Player) (Object) this;
		if (!self.level().isClientSide() && self.getMainHandItem().getItem() instanceof GnomeLanceItem) {
			return GnomeLanceItem.joust(self, baseDamage);
		}
		return baseDamage;
	}
}
