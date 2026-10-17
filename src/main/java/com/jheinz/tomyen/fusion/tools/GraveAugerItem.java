package com.jheinz.tomyen.fusion.tools;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.jheinz.tomyen.drill.DrillItem;
import com.jheinz.tomyen.fusion.FusionTooltipItem;
import com.jheinz.tomyen.gravedigger.GraveDiggers;
import com.jheinz.tomyen.werewolf.WerewolfCurse;

/**
 * The Grave Auger (Gravedigger's Shovel + Steel Drill, at a pond): a heavy-engine drill (a 4000-unit tank of kerosene
 * or a uranium fuel rod) whose bit is the gravedigger's blade wound into a screw. It is a drill and a shovel at once,
 * boring 3x3 tunnels (sneak to take one block, sneak-use to switch) through stone and earth alike, burning a unit of
 * fuel a block. And it works the night shift: after dark, while it runs, dirt, sand, gravel and the like give way
 * instantly ({@value #NIGHT_SPEED} speed), and soft ground turns up old bones now and then.
 * <p>
 * Night is the server's to say: like the shovel, it marks itself ({@link GraveDiggers#NIGHT_SHIFT}) while carried.
 */
public class GraveAugerItem extends DrillItem {
	/** Far past what any earth needs to break in one tick. */
	public static final float NIGHT_SPEED = 1000.0f;
	public static final float BONE_CHANCE = 0.05f;

	public GraveAugerItem(Properties properties) {
		super(properties, Engine.HEAVY, true, true);
	}

	/** A pickaxe and a shovel in one (#tomyen:mineable/grave_auger), with a drill's spin for a swing. */
	public static Item.Properties properties() {
		return FusionTools.GRAVE_AUGER_MATERIAL.applyToolProperties(new Item.Properties(), FusionTools.MINEABLE_WITH_GRAVE_AUGER, 1.5f, -3.1f, 0.0f)
				.component(DataComponents.ATTACK_ANIMATION, new SwingAnimation(SwingAnimationType.NONE, 6))
				.rarity(Rarity.RARE);
	}

	public static boolean nightShift(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(GraveDiggers.NIGHT_SHIFT));
	}

	private static boolean earth(BlockState state) {
		return state.is(BlockTags.MINEABLE_WITH_SHOVEL) && !state.is(BlockTags.MINEABLE_WITH_PICKAXE);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (owner.tickCount % 20 != 0) {
			return;
		}
		boolean night = WerewolfCurse.isNight(level);
		if (night != nightShift(stack)) {
			if (night) {
				stack.set(GraveDiggers.NIGHT_SHIFT, true);
			} else {
				stack.remove(GraveDiggers.NIGHT_SHIFT);
			}
		}
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		if (powered(stack)) {
			if (earth(state) && nightShift(stack)) {
				return NIGHT_SPEED;
			}
			return super.getDestroySpeed(stack, state);
		}
		// Dry, it digs earth as well as a wooden shovel and rock as an unpowered drill does.
		return earth(state) ? UNPOWERED_SPEED : super.getDestroySpeed(stack, state);
	}

	@Override
	public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
		return earth(state) || super.isCorrectToolForDrops(stack, state);
	}

	/** The drill's wear and fuel for stone; earth burns fuel too, and turns up a bone now and then. */
	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		boolean mined = super.mineBlock(stack, level, state, pos, owner);
		if (level instanceof ServerLevel serverLevel && !stack.isEmpty() && earth(state)) {
			if (hasTank() && !(owner instanceof Player player && player.hasInfiniteMaterials())) {
				setFuel(stack, fuel(stack) - 1);
			}
			if (serverLevel.getRandom().nextFloat() < BONE_CHANCE * (nightShift(stack) ? 2.0f : 1.0f)) {
				Block.popResource(serverLevel, pos, new ItemStack(Items.BONE));
			}
		}
		return mined;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		FusionTooltipItem.appendFusionLines(tooltip, "item.tomyen.grave_auger.both");
		tooltip.accept(Component.translatable(nightShift(stack) ? "item.tomyen.grave_auger.night_now" : "item.tomyen.grave_auger.night")
				.withStyle(nightShift(stack) ? ChatFormatting.DARK_AQUA : ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.grave_auger.bones").withStyle(ChatFormatting.GRAY));
		super.appendHoverText(stack, context, display, tooltip, flag);
	}
}
