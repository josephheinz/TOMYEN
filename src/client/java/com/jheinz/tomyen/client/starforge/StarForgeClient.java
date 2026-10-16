package com.jheinz.tomyen.client.starforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.starforge.Infusion;
import com.jheinz.tomyen.starforge.InfusionKind;
import com.jheinz.tomyen.starforge.StarForge;

/**
 * The Star Forge's client side: its screen, the infusion lines in item tooltips (and the catalyst guide on the
 * forge's own), and the faint motes infused gear gives off in a player's hand or on their armor.
 */
public final class StarForgeClient {
	/** Chance a tick of a mote off an infused item in the hand, and off each infused armor piece. */
	private static final float HELD_MOTE_CHANCE = 0.08f;
	private static final float WORN_MOTE_CHANCE = 0.025f;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private StarForgeClient() {
	}

	public static void initialize() {
		MenuScreens.register(StarForge.STAR_FORGE_MENU, StarForgeScreen::new);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> tooltip(stack, lines));
		ClientTickEvents.END_CLIENT_TICK.register(StarForgeClient::motes);
	}

	private static void tooltip(ItemStack stack, List<Component> lines) {
		if (stack.is(StarForge.STAR_FORGE.asItem())) {
			int at = Math.min(1, lines.size());
			lines.add(at++, Component.translatable("block.tomyen.star_forge.desc").withStyle(ChatFormatting.GRAY));
			lines.add(at++, Component.translatable("block.tomyen.star_forge.rule").withStyle(ChatFormatting.DARK_AQUA));
			if (Minecraft.getInstance().hasShiftDown()) {
				lines.addAll(at, guide());
			} else {
				lines.add(at, Component.translatable("block.tomyen.star_forge.shift").withStyle(ChatFormatting.DARK_GRAY));
			}
			return;
		}
		Infusion infusion = Infusion.of(stack);
		if (infusion == null) {
			return;
		}
		// Just under the name, above vanilla's attribute lines.
		lines.addAll(Math.min(1, lines.size()), infusion.tooltip(InfusionKind.of(stack)));
	}

	/** Every catalyst and its infusion, and what that infusion takes: the forge's "?" and its item's shift tooltip. */
	public static List<Component> guide() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("block.tomyen.star_forge.guide").withStyle(ChatFormatting.WHITE));
		for (Infusion infusion : Infusion.values()) {
			MutableComponent catalysts = Component.empty();
			List<Item> items = infusion.catalysts();
			for (int i = 0; i < items.size(); i++) {
				if (i > 0) {
					catalysts.append(Component.translatable("block.tomyen.star_forge.guide.or"));
				}
				catalysts.append(new ItemStack(items.get(i)).getHoverName());
			}
			lines.add(Component.translatable("block.tomyen.star_forge.guide.line", catalysts.withStyle(ChatFormatting.GRAY),
					infusion.displayName().withColor(infusion.color()), takes(infusion).withStyle(ChatFormatting.DARK_GRAY)));
		}
		return lines;
	}

	/** The sorts of gear an infusion takes, as a short list: "weapons, guns, armor". */
	public static MutableComponent takes(Infusion infusion) {
		Set<InfusionKind> kinds = infusion.takes();
		if (kinds.size() == InfusionKind.values().length) {
			return Component.translatable("infusion.tomyen.takes.all");
		}
		MutableComponent list = Component.empty();
		boolean first = true;
		for (InfusionKind kind : kinds) {
			if (kind == InfusionKind.DRILL && kinds.contains(InfusionKind.TOOL)) {
				continue;
			}
			if (!first) {
				list.append(", ");
			}
			first = false;
			list.append(Component.translatable("infusion.tomyen.takes." + kind.name().toLowerCase(java.util.Locale.ROOT)));
		}
		return list;
	}

	/** A faint mote of each infusion's colour now and then off what a player holds and wears; Starlit glows by night. */
	private static void motes(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.isPaused()) {
			return;
		}
		RandomSource random = minecraft.level.getRandom();
		for (Player player : minecraft.level.players()) {
			boolean ownFirstPerson = player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
			if (ownFirstPerson || player.isInvisible()) {
				continue;
			}
			Infusion held = Infusion.of(player.getMainHandItem());
			if (held != null && random.nextFloat() < HELD_MOTE_CHANCE) {
				// Beside the main hand, about where the item is held.
				float yaw = player.yBodyRot * ((float) Math.PI / 180.0f);
				double side = player.getMainArm() == HumanoidArm.RIGHT ? -0.4 : 0.4;
				Vec3 hand = player.position().add(Math.cos(yaw) * side - Math.sin(yaw) * 0.3, player.getBbHeight() * 0.45,
						Math.sin(yaw) * side + Math.cos(yaw) * 0.3);
				mote(minecraft, player, held, hand.x + random.nextGaussian() * 0.08, hand.y + random.nextGaussian() * 0.08,
						hand.z + random.nextGaussian() * 0.08);
			}
			for (EquipmentSlot slot : ARMOR) {
				Infusion worn = Infusion.of(player.getItemBySlot(slot));
				if (worn != null && random.nextFloat() < WORN_MOTE_CHANCE) {
					mote(minecraft, player, worn, player.getRandomX(0.7), player.getRandomY(), player.getRandomZ(0.7));
				}
			}
		}
	}

	private static void mote(Minecraft minecraft, Player player, Infusion infusion, double x, double y, double z) {
		if (infusion == Infusion.STARLIT && player.level().isDarkOutside()) {
			minecraft.level.addParticle(ParticleTypes.GLOW, x, y, z, 0.0, 0.01, 0.0);
			return;
		}
		minecraft.level.addParticle(new DustParticleOptions(infusion.color(), 0.55f), x, y, z, 0.0, 0.015, 0.0);
	}
}
