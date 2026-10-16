package com.jheinz.tomyen.neuralink;

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

import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.ModEffects;
import com.jheinz.tomyen.powerarmor.PowerArmor;
import com.jheinz.tomyen.powerarmor.PowerArmorPieceItem;

/**
 * Worn on the head. Using it in hand opens its interface, where up to {@link #moduleSlots()} modules are
 * installed (3 for the original, 5 for the Mk II); sneak-use puts it on. Its modules work only while it's worn,
 * and not at all while an EMP has knocked it out ({@link ModEffects#NEURAL_STATIC}). A power armor helmet has the
 * same five sockets built in, and its modules work the same way while the suit is worn.
 */
public class NeuralinkItem extends Item {
	private static final Component TITLE = Component.translatable("container.tomyen.neuralink");

	private final int moduleSlots;

	public NeuralinkItem(Properties properties, int moduleSlots) {
		super(properties);
		this.moduleSlots = moduleSlots;
	}

	public int moduleSlots() {
		return moduleSlots;
	}

	/**
	 * The socket count of anything with Neuralink sockets: a Neuralink, or a power armor helmet (a built-in Mk II).
	 * 0 for anything else.
	 */
	public static int moduleSlots(ItemStack stack) {
		if (PowerArmorPieceItem.isHelmet(stack)) {
			return PowerArmorPieceItem.HELMET_SOCKETS;
		}
		return stack.getItem() instanceof NeuralinkItem neuralink ? neuralink.moduleSlots : 0;
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

	/**
	 * The modules in the Neuralink on an entity's head, working or not; inside power armor, those in the suit's
	 * helmet. Works on both sides: the worn suit, helmet modules and all, is synced to every client.
	 */
	public static Set<NeuralinkModule> installedModules(LivingEntity entity) {
		ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
		if (head.getItem() instanceof NeuralinkItem) {
			return modules(head);
		}
		ItemStack helmet = PowerArmor.wornHelmet(entity);
		return helmet.isEmpty() ? EnumSet.noneOf(NeuralinkModule.class) : modules(helmet);
	}

	/** Whether the entity's Neuralink is knocked out by an EMP. */
	public static boolean isJammed(LivingEntity entity) {
		return entity.hasEffect(ModEffects.NEURAL_STATIC);
	}

	/** The modules working for an entity: those in the Neuralink on its head, if it wears one and it isn't jammed. */
	public static Set<NeuralinkModule> activeModules(LivingEntity entity) {
		return isJammed(entity) ? EnumSet.noneOf(NeuralinkModule.class) : installedModules(entity);
	}

	public static boolean isActive(LivingEntity entity, NeuralinkModule module) {
		return activeModules(entity).contains(module);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		Set<NeuralinkModule> modules = modules(stack);
		tooltip.accept(Component.translatable("item.tomyen.neuralink.sockets", modules.size(), moduleSlots).withStyle(ChatFormatting.DARK_AQUA));
		if (modules.isEmpty()) {
			tooltip.accept(Component.translatable("item.tomyen.neuralink.no_modules").withStyle(ChatFormatting.DARK_GRAY));
		}
		for (NeuralinkModule module : modules) {
			tooltip.accept(Component.literal("▪ ").append(module.displayName()).withColor(module.color()));
		}
		tooltip.accept(Component.translatable("item.tomyen.neuralink.open").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.neuralink.wear").withStyle(ChatFormatting.GRAY));
		if (modules.stream().anyMatch(NeuralinkModule::isActive)) {
			tooltip.accept(Component.translatable("item.tomyen.neuralink.trigger", Component.keybind("key.tomyen.neural_trigger"))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
