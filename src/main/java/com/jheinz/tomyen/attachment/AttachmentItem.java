package com.jheinz.tomyen.attachment;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import com.jheinz.tomyen.GunStats;

/**
 * A part that fits onto a gun: right-click it onto the gun in an inventory, or combine the two in the
 * Weapon Station. It changes how the gun handles through {@code effect}.
 *
 * @param guns   the guns it fits
 * @param effect applied to the gun's stats while fitted
 */
public class AttachmentItem extends Item {
	private final AttachmentSlot slot;
	private final List<Item> guns;
	private final UnaryOperator<GunStats> effect;

	public AttachmentItem(Properties properties, AttachmentSlot slot, List<Item> guns, UnaryOperator<GunStats> effect) {
		super(properties);
		this.slot = slot;
		this.guns = guns;
		this.effect = effect;
	}

	public AttachmentSlot slot() {
		return slot;
	}

	public boolean fits(Item gun) {
		return guns.contains(gun);
	}

	public GunStats apply(GunStats stats) {
		return effect.apply(stats);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable(getDescriptionId() + ".effect").withStyle(ChatFormatting.BLUE));
		MutableComponent names = Component.empty();
		for (int i = 0; i < guns.size(); i++) {
			names.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Component.translatable(guns.get(i).getDescriptionId()));
		}
		tooltip.accept(Component.translatable("item.tomyen.attachment.fits", names).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.attachment.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
