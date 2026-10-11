package com.jheinz.mymod;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A box or drum magazine. Players pack it one round at a time by sneaking and holding attack. */
public class MagazineItem extends Item {
	private final int capacity;
	private final List<Ammo> ammo;
	private final int extraReloadTicks;

	/** @param extraReloadTicks added to the gun's reload time when swapping this magazine in */
	public MagazineItem(Properties properties, int capacity, List<Ammo> ammo, int extraReloadTicks) {
		super(properties);
		this.capacity = capacity;
		this.ammo = ammo;
		this.extraReloadTicks = extraReloadTicks;
	}

	public int capacity() {
		return capacity;
	}

	public int extraReloadTicks() {
		return extraReloadTicks;
	}

	/** Packing changes the stack's data every round; that shouldn't replay the equip animation. */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	public static int rounds(ItemStack magazine) {
		MagazineContents contents = magazine.get(ModComponents.MAGAZINE_CONTENTS);
		return contents == null ? 0 : contents.rounds();
	}

	/** Moves one round from the player's inventory into the magazine. */
	public void loadRound(ServerPlayer player, ItemStack magazine) {
		MagazineContents contents = magazine.get(ModComponents.MAGAZINE_CONTENTS);
		if (contents != null && contents.rounds() >= capacity) {
			return;
		}

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			boolean matches = contents == null
					? ammo.stream().anyMatch(round -> stack.is(round.item()))
					: contents.holds(stack);
			if (!matches) {
				continue;
			}

			MagazineContents loaded = contents == null
					? new MagazineContents(stack.typeHolder(), 1)
					: contents.withRounds(contents.rounds() + 1);
			stack.shrink(1);
			magazine.set(ModComponents.MAGAZINE_CONTENTS, loaded);

			float pitch = 1.4f + 0.4f * loaded.rounds() / capacity;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 0.5f, pitch);
			if (loaded.rounds() == capacity) {
				player.sendOverlayMessage(Component.translatable("item.mymod.magazine.full"));
			}
			return;
		}

		player.sendOverlayMessage(contents == null
				? Component.translatable("item.mymod.magazine.no_ammo")
				: Component.translatable("item.mymod.magazine.no_matching_ammo", new ItemStack(contents.ammo()).getHoverName()));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return rounds(stack) > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Mth.clamp(Math.round(13.0f * rounds(stack) / capacity), 0, 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		MagazineContents contents = stack.get(ModComponents.MAGAZINE_CONTENTS);
		Ammo round = contents == null ? null : Ammo.find(ammo, contents.ammo());
		return round == null ? 0xFFFFFF : round.color();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		MagazineContents contents = stack.get(ModComponents.MAGAZINE_CONTENTS);
		if (contents == null) {
			tooltip.accept(Component.translatable("item.mymod.magazine.empty", capacity).withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("item.mymod.magazine.rounds", contents.rounds(), capacity,
					new ItemStack(contents.ammo()).getHoverName()).withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.mymod.magazine.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
