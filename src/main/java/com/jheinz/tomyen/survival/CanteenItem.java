package com.jheinz.tomyen.survival;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.jheinz.tomyen.ModEffects;

/**
 * A leather-cased canteen holding {@value #CAPACITY} sips of water or doses of one potion.
 * <ul>
 * <li>Use it on water to fill it with water (topping up water, not a potion).</li>
 * <li>In the inventory, click a water bottle or a drinkable potion onto it to pour in a dose (you keep the bottle):
 * an empty canteen takes any, a filled one more of the same.</li>
 * <li>Drink (hold use) for a sip. A potion dose works as drinking the potion. Water douses you: it puts out fire and
 * napalm (water is the only thing that does), and gives a mouthful of food and a few seconds' Regeneration.</li>
 * </ul>
 */
public class CanteenItem extends Item {
	public static final int CAPACITY = 3;
	private static final int DRINK_TICKS = 24;
	private static final int WATER_REGEN_TICKS = 60;

	public CanteenItem(Properties properties) {
		super(properties);
	}

	public static int sips(ItemStack stack) {
		return stack.getOrDefault(Survival.SIPS, 0);
	}

	private static boolean holdsWater(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.is(Potions.WATER);
	}

	private static void fill(ItemStack stack, PotionContents contents, int sips) {
		stack.set(DataComponents.POTION_CONTENTS, contents);
		stack.set(Survival.SIPS, sips);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		int sips = sips(stack);
		if (sips < CAPACITY && (sips == 0 || holdsWater(stack))) {
			BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
			if (hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = hit.getBlockPos();
				if (level.mayInteract(player, pos) && level.getFluidState(pos).is(FluidTags.WATER)) {
					level.playSound(player, player.getX(), player.getY(), player.getZ(), Survival.CANTEEN_FILL, SoundSource.NEUTRAL, 1.0f, 1.0f);
					level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
					fill(stack, new PotionContents(Potions.WATER), CAPACITY);
					return InteractionResult.SUCCESS;
				}
			}
		}
		if (sips > 0) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.DRINK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return DRINK_TICKS;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		int sips = sips(stack);
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (sips <= 0 || contents == null) {
			return stack;
		}
		level.playSound(null, user.getX(), user.getY(), user.getZ(), Survival.CANTEEN_SIP, SoundSource.PLAYERS, 0.8f, 1.0f);
		if (level instanceof ServerLevel) {
			if (contents.is(Potions.WATER)) {
				user.clearFire();
				user.removeEffect(ModEffects.NAPALM);
				user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, WATER_REGEN_TICKS));
				if (user instanceof Player player) {
					player.getFoodData().eat(1, 0.5f);
				}
			} else {
				contents.applyToLivingEntity(user, 1.0f);
			}
			if (user instanceof Player player && player.hasInfiniteMaterials()) {
				return stack;
			}
			if (sips - 1 <= 0) {
				stack.remove(DataComponents.POTION_CONTENTS);
				stack.set(Survival.SIPS, 0);
			} else {
				stack.set(Survival.SIPS, sips - 1);
			}
		}
		return stack;
	}

	/** Pouring a bottle into it from the inventory. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player,
			SlotAccess carriedItem) {
		if (clickAction != ClickAction.PRIMARY || !other.is(Items.POTION) || !slot.allowModification(player)) {
			return false;
		}
		PotionContents poured = other.get(DataComponents.POTION_CONTENTS);
		if (poured == null) {
			return false;
		}
		int sips = sips(self);
		PotionContents held = self.get(DataComponents.POTION_CONTENTS);
		if (sips >= CAPACITY || sips > 0 && !poured.equals(held)) {
			return false;
		}
		fill(self, poured, sips + 1);
		other.shrink(1);
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		if (other.isEmpty()) {
			carriedItem.set(bottle);
		} else {
			player.getInventory().placeItemBackInInventory(bottle, Prediction.PREDICTED);
		}
		player.playSound(Survival.CANTEEN_FILL, 0.8f, 1.2f);
		return true;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return sips(stack) > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Mth.clamp(Math.round(13.0f * sips(stack) / CAPACITY), 0, 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents == null ? 0x3F76E4 : contents.getColor() & 0xFFFFFF;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		int sips = sips(stack);
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (sips <= 0 || contents == null) {
			builder.accept(Component.translatable("item.tomyen.canteen.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		Component what = contents.getName(Items.POTION.getDescriptionId() + ".effect.");
		builder.accept(Component.translatable("item.tomyen.canteen.sips", what, sips, CAPACITY).withStyle(ChatFormatting.BLUE));
	}
}
