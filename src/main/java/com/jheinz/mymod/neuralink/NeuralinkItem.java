package com.jheinz.mymod.neuralink;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import com.jheinz.mymod.ModComponents;

/**
 * Worn on the head. Using it in hand opens its interface, where up to {@link #MODULE_SLOTS} modules are
 * installed; sneak-use puts it on. Its modules work only while it's worn.
 */
public class NeuralinkItem extends Item {
	public static final int MODULE_SLOTS = 3;
	private static final Component TITLE = Component.translatable("container.mymod.neuralink");

	public NeuralinkItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isSecondaryUseActive()) {
			return super.use(level, player, hand);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, user) -> new NeuralinkMenu(containerId, inventory, hand), TITLE));
		}
		return InteractionResult.SUCCESS;
	}

	/** The modules installed in a Neuralink stack. */
	public static Set<NeuralinkModule> modules(ItemStack neuralink) {
		Set<NeuralinkModule> modules = EnumSet.noneOf(NeuralinkModule.class);
		neuralink.getOrDefault(ModComponents.NEURALINK_MODULES, ItemContainerContents.EMPTY).nonEmptyItemCopyStream()
				.forEach(stack -> {
					if (stack.getItem() instanceof NeuralinkModuleItem module) {
						modules.add(module.module());
					}
				});
		return modules;
	}

	/** The modules working for an entity: those in the Neuralink on its head, if it wears one. */
	public static Set<NeuralinkModule> activeModules(LivingEntity entity) {
		ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
		return head.getItem() instanceof NeuralinkItem ? modules(head) : EnumSet.noneOf(NeuralinkModule.class);
	}

	public static boolean isActive(LivingEntity entity, NeuralinkModule module) {
		return activeModules(entity).contains(module);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		Set<NeuralinkModule> modules = modules(stack);
		if (modules.isEmpty()) {
			tooltip.accept(Component.translatable("item.mymod.neuralink.no_modules").withStyle(ChatFormatting.DARK_GRAY));
		}
		for (NeuralinkModule module : modules) {
			tooltip.accept(Component.literal("▪ ").append(module.displayName()).withColor(module.color()));
		}
		tooltip.accept(Component.translatable("item.mymod.neuralink.open").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.neuralink.wear").withStyle(ChatFormatting.GRAY));
	}
}
