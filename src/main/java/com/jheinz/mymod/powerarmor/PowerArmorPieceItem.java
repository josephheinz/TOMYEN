package com.jheinz.mymod.powerarmor;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import com.jheinz.mymod.neuralink.NeuralinkItem;
import com.jheinz.mymod.neuralink.NeuralinkMenu;
import com.jheinz.mymod.neuralink.NeuralinkModule;

/**
 * One piece of power armor, of one mark. It can't be worn on its own: it goes on a Power Armor Station's rack,
 * and the suit is climbed into from there. Its durability is its condition; a piece run down to nothing stays on
 * the suit but stops protecting (and stops powering what it governs) until it's repaired at an anvil.
 *
 * <p>Every helmet doubles as a Neuralink Mk II: it has {@link #HELMET_SOCKETS} module sockets of its own, opened
 * by using it in hand, and its modules work while the suit is worn.
 */
public class PowerArmorPieceItem extends Item {
	public static final int HELMET_SOCKETS = 5;
	private static final Component SOCKETS_TITLE = Component.translatable("container.mymod.power_armor_helmet");

	private final PowerArmorPiece piece;
	private final PowerArmorTier tier;

	public PowerArmorPieceItem(Properties properties, PowerArmorPiece piece, PowerArmorTier tier) {
		super(properties);
		this.piece = piece;
		this.tier = tier;
	}

	public static Item.Properties properties(PowerArmorPiece piece, PowerArmorTier tier) {
		Rarity rarity = switch (tier) {
			case MK1 -> Rarity.UNCOMMON;
			case MK2 -> Rarity.RARE;
			case MK3 -> Rarity.EPIC;
		};
		Item.Properties properties = new Item.Properties().stacksTo(1).rarity(rarity).durability(piece.durability(tier));
		return tier == PowerArmorTier.MK3 ? properties.repairable(Items.NETHERITE_SCRAP).fireResistant()
				: properties.repairable(Items.IRON_INGOT);
	}

	public PowerArmorPiece piece() {
		return piece;
	}

	public PowerArmorTier tier() {
		return tier;
	}

	/** How intact a piece is, 0 (broken) to 1; 0 for anything that isn't a piece. */
	public static float condition(ItemStack stack) {
		if (!(stack.getItem() instanceof PowerArmorPieceItem) || stack.getMaxDamage() <= 0) {
			return 0.0f;
		}
		return 1.0f - stack.getDamageValue() / (float) stack.getMaxDamage();
	}

	/** Wears a piece down, stopping at broken: unlike armor, it never shatters. Returns the worn copy. */
	public static ItemStack wear(ItemStack stack, int amount) {
		ItemStack worn = stack.copy();
		worn.setDamageValue(Math.min(worn.getMaxDamage(), worn.getDamageValue() + Math.max(0, amount)));
		return worn;
	}

	public static boolean isHelmet(ItemStack stack) {
		return stack.getItem() instanceof PowerArmorPieceItem item && item.piece == PowerArmorPiece.HELMET;
	}

	/** A helmet opens its Neuralink sockets. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (piece != PowerArmorPiece.HELMET) {
			return super.use(level, player, hand);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, user) -> new NeuralinkMenu(containerId, inventory, hand),
					SOCKETS_TITLE));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(tier.displayName().copy().withColor(tier.color()));
		float condition = condition(stack);
		ChatFormatting conditionColour = condition <= 0.0f ? ChatFormatting.DARK_RED
				: condition < 0.25f ? ChatFormatting.RED : condition < 0.6f ? ChatFormatting.GOLD : ChatFormatting.GREEN;
		tooltip.accept(Component.translatable(condition <= 0.0f ? "item.mymod.power_armor.broken" : "item.mymod.power_armor.condition",
				Math.round(condition * 100.0f)).withStyle(conditionColour));
		tooltip.accept(Component.translatable("item.mymod.power_armor.protection", Math.round(tier.protection() * 100.0f))
				.withStyle(ChatFormatting.GRAY));
		tooltip.accept(governs().withStyle(ChatFormatting.DARK_AQUA));
		if (piece == PowerArmorPiece.HELMET) {
			Set<NeuralinkModule> modules = NeuralinkItem.modules(stack);
			tooltip.accept(Component.translatable("item.mymod.neuralink.sockets", modules.size(), HELMET_SOCKETS).withStyle(ChatFormatting.DARK_AQUA));
			for (NeuralinkModule module : modules) {
				tooltip.accept(Component.literal("▪ ").append(module.displayName()).withColor(module.color()));
			}
			tooltip.accept(Component.translatable("item.mymod.power_armor_helmet.open").withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.mymod.power_armor.rack").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** What this piece does at its mark, as one line. */
	private MutableComponent governs() {
		return switch (piece) {
			case HELMET -> Component.translatable("item.mymod.power_armor.governs.helmet." + tier.id(),
					PowerArmorStats.airFor(tier) / 20);
			case TORSO -> Component.translatable("item.mymod.power_armor.governs.torso",
					number(PowerArmorStats.unibeamDamageFor(tier)), Math.round((1.0f - PowerArmorStats.fuelEfficiencyFor(tier)) * 100.0f),
					PowerArmorStats.maxBurnFor(tier) / 20);
			case LEFT_ARM, RIGHT_ARM -> Component.translatable("item.mymod.power_armor.governs.arm",
					number(PowerArmorStats.repulsorDamageFor(tier)), String.format(Locale.ROOT, "%.1f", PowerArmorStats.repulsorCooldownFor(tier) / 20.0f));
			case LEFT_LEG, RIGHT_LEG -> Component.translatable("item.mymod.power_armor.governs.leg",
					Math.round(PowerArmorStats.topSpeedFor(tier, false)), Math.round(PowerArmorStats.topSpeedFor(tier, true)),
					number(PowerArmorStats.shockwaveFor(tier)));
		};
	}

	private static String number(float value) {
		return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
	}
}
