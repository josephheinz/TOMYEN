package com.jheinz.mymod.fishing;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import org.jspecify.annotations.Nullable;

/**
 * Bait, for a mod rod's bait slot at the Tackle Bench. A stack goes on the rod ({@link RodBait}) and one is eaten per
 * fish landed. While it lasts it changes the fishing:
 * <ul>
 * <li>{@code modifiers}: the usual rod numbers, mostly a quicker bite (added to the rod's totals, {@link RodStats});</li>
 * <li>{@code size}: a push toward heavier fish (the weight roll's bonus, see {@link FishSize#roll}), and
 * {@code attractedSize} more for the species it {@code attracts};</li>
 * <li>{@code attracts}/{@code attraction}: species whose items are in that tag are this many times likelier to bite;</li>
 * <li>{@code lavaOnly}: it does nothing out of lava (it burns, or sinks uselessly in water);</li>
 * <li>{@code radiation}: ticks of radiation sickness the angler takes with every fish it lands.</li>
 * </ul>
 */
public class BaitItem extends Item {
	public record Effect(RodModifiers modifiers, float size, @Nullable TagKey<Item> attracts, float attraction, float attractedSize,
			boolean lavaOnly, int radiation) {
	}

	private final Effect effect;

	public BaitItem(Properties properties, Effect effect) {
		super(properties);
		this.effect = effect;
	}

	public Effect effect() {
		return effect;
	}

	/** Whether it works where the hook is (a lava-only bait does nothing in water). */
	public boolean works(boolean inLava) {
		return !effect.lavaOnly() || inLava;
	}

	/** Its push toward heavier fish for {@code species} (fishing in lava or not). */
	public float sizeBonus(FishingSpecies species, boolean inLava) {
		if (!works(inLava)) {
			return 0.0f;
		}
		return effect.size() + (attracts(species) ? effect.attractedSize() : 0.0f);
	}

	/** How much likelier {@code species} is to bite on it. */
	public double attraction(FishingSpecies species, boolean inLava) {
		return works(inLava) && attracts(species) ? effect.attraction() : 1.0;
	}

	private boolean attracts(FishingSpecies species) {
		return effect.attracts() != null && species.stack().is(effect.attracts());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.mymod.bait").withStyle(ChatFormatting.GRAY));
		effect.modifiers().describe().forEach(tooltip);
		if (effect.size() > 0.0f) {
			tooltip.accept(Component.literal(" ").append(Component.translatable("tooltip.mymod.bait.size",
					Math.round(effect.size() * 100.0f) + "%")).withStyle(ChatFormatting.BLUE));
		} else if (effect.size() < 0.0f) {
			tooltip.accept(Component.literal(" ").append(Component.translatable("tooltip.mymod.bait.smaller",
					Math.round(-effect.size() * 100.0f) + "%")).withStyle(ChatFormatting.RED));
		}
		if (effect.attracts() != null) {
			String tag = effect.attracts().location().getPath();
			tooltip.accept(Component.literal(" ").append(Component.translatable("tooltip.mymod.bait.attracts." + tag))
					.withStyle(ChatFormatting.BLUE));
			if (effect.attractedSize() > 0.0f) {
				tooltip.accept(Component.literal(" ").append(Component.translatable("tooltip.mymod.bait.attracted_size." + tag,
						Math.round(effect.attractedSize() * 100.0f) + "%")).withStyle(ChatFormatting.BLUE));
			}
		}
		if (effect.lavaOnly()) {
			tooltip.accept(Component.literal(" ").append(Component.translatable("tooltip.mymod.bait.lava_only"))
					.withStyle(ChatFormatting.GOLD));
		}
		if (effect.radiation() > 0) {
			tooltip.accept(Component.literal(" ").append(Component.translatable("tooltip.mymod.bait.radiation"))
					.withStyle(ChatFormatting.RED));
		}
		tooltip.accept(Component.translatable("tooltip.mymod.bait.fit").withStyle(ChatFormatting.DARK_GRAY));
	}
}
