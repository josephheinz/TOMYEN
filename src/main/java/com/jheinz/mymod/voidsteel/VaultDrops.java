package com.jheinz.mymod.voidsteel;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.bank.VaultContainer;
import com.jheinz.mymod.bank.Vaults;

/**
 * Voidsteel tools send what they mine through the void: every drop of a block a player breaks with one goes into
 * that player's bank vault (see {@link Vaults}) as far as it has room, the rest into their inventory, and only what
 * fits in neither falls to the ground. The block's spot gives off a soft swirl of reverse-portal motes when anything
 * was taken.
 */
public final class VaultDrops {
	private VaultDrops() {
	}

	static void modifyDrops(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		if (!context.hasParameter(LootContextParams.BLOCK_STATE) || drops.isEmpty()) {
			return;
		}
		ItemInstance tool = context.getOptional(LootContextParams.TOOL);
		if (tool == null || !Voidsteel.SET.minesWith(tool) || !(context.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) {
			return;
		}
		VaultContainer vault = Vaults.of(player);
		boolean taken = false;
		for (int i = 0; i < drops.size(); i++) {
			ItemStack drop = drops.get(i);
			ItemStack rest = vault.addItem(drop);
			if (!rest.isEmpty()) {
				// Shrinks what it takes from the stack.
				player.getInventory().add(rest);
			}
			taken |= rest.getCount() < drop.getCount();
			drops.set(i, rest);
		}
		drops.removeIf(ItemStack::isEmpty);
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		if (taken && origin != null) {
			context.getLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, origin.x, origin.y, origin.z, 10, 0.2, 0.2, 0.2, 0.02);
		}
	}
}
