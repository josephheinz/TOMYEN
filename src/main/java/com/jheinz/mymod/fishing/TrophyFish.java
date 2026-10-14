package com.jheinz.mymod.fishing;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;

/**
 * On a trophy fish ({@link Fishing#TROPHY}): what it weighed, its species' typical weight when it was caught (so a
 * Fish Mount can size it without the species files, which only the server has), who caught it and on which day.
 * A trophy is named "Trophy Pike" in gold and never stacks; ordinary catches carry nothing and stack as usual (their
 * weight is only announced). See {@link FishSize} for where the trophy line is.
 */
public record TrophyFish(float weight, float averageWeight, String catcher, long day) implements TooltipProvider {
	public static final Codec<TrophyFish> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.fieldOf("weight_lb").forGetter(TrophyFish::weight),
			Codec.FLOAT.fieldOf("average_lb").forGetter(TrophyFish::averageWeight),
			Codec.STRING.fieldOf("caught_by").forGetter(TrophyFish::catcher),
			Codec.LONG.fieldOf("day").forGetter(TrophyFish::day)).apply(instance, TrophyFish::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, TrophyFish> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, TrophyFish::weight,
			ByteBufCodecs.FLOAT, TrophyFish::averageWeight,
			ByteBufCodecs.STRING_UTF8, TrophyFish::catcher,
			ByteBufCodecs.VAR_LONG, TrophyFish::day,
			TrophyFish::new);

	public static final ChatFormatting COLOR = ChatFormatting.GOLD;

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
		tooltip.accept(Component.translatable("tooltip.mymod.trophy.weight", FishSize.format(weight)).withStyle(ChatFormatting.YELLOW));
		tooltip.accept(Component.translatable("tooltip.mymod.trophy.caught_by", catcher).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("tooltip.mymod.trophy.day", day).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** The in-game day, as the trophy records it: day 1 is the world's first. */
	public static long day(Level level) {
		return level.getOverworldClockTime() / 24000L + 1L;
	}

	/** A trophy of {@code fish}'s kind: one fish, gold "Trophy" name, unstackable. */
	public static ItemStack make(ItemStack fish, TrophyFish trophy) {
		ItemStack stack = new ItemStack(fish.getItem());
		stack.set(Fishing.TROPHY, trophy);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.mymod.trophy_fish", speciesName(fish)).withStyle(COLOR));
		return stack;
	}

	/** The fish's plain name ("Pike"), whether or not it's a trophy. */
	public static Component speciesName(ItemStack fish) {
		return new ItemStack(fish.getItem()).getItemName();
	}

	/** "Pike, 12.3 lb". */
	public static Component describe(ItemStack fish, float weight) {
		return Component.translatable("hud.mymod.fishing.weighed", speciesName(fish), FishSize.format(weight));
	}
}
