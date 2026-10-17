package com.jheinz.tomyen.fusion;

import java.util.function.Consumer;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.fish.Fishes;
import com.jheinz.tomyen.soul.Souls;

/**
 * Fish Fish Fish (two Fish Fish, at a pond): three fish, each biting the next one's tail, swung as a club. Deliberately
 * feeble, a wooden sword's damage. But each hit has a {@value #CHANCE_PERCENT}% chance to turn what it hits into a
 * Fish Fish (the living fish, flopping where the mob stood; it drops nothing else).
 *
 * <p>Never players, villagers or wandering traders, anything named, tamed, leashed, ridden or riding, bosses (the
 * {@code c:bosses} tag), anything with over {@value #MAX_HEALTH} max health, or anything in
 * {@code tomyen:fish_fish_fish_immune} (the Soul Vacuum's immune list, and the mod's big set pieces).
 */
public class FishFishFishItem extends Item {
	public static final int CHANCE_PERCENT = 10;
	public static final float MAX_HEALTH = 60.0f;
	public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("fish_fish_fish_immune"));

	public FishFishFishItem(Properties properties) {
		super(properties);
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);
		if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || target.getRandom().nextInt(100) >= CHANCE_PERCENT
				|| !canBecomeFish(target)) {
			return;
		}
		EntityType<?> fishType = BuiltInRegistries.ENTITY_TYPE.getOptional(MyMod.id("fish_fish")).orElse(null);
		Entity fish = fishType == null ? null : fishType.create(level, EntitySpawnReason.CONVERSION);
		if (fish == null) {
			fish = new ItemEntity(level, target.getX(), target.getY(), target.getZ(), new ItemStack(Fishes.FISH_FISH));
		}
		fish.snapTo(target.getX(), target.getY() + 0.2, target.getZ(), target.getYRot(), 0.0f);
		Component name = target.getDisplayName();
		level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY(0.5), target.getZ(), 20, target.getBbWidth() * 0.4,
				target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.04);
		level.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY(0.5), target.getZ(), 30, 0.4, 0.4, 0.4, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.NEUTRAL, 1.0f, 1.3f);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.COD_FLOP, SoundSource.NEUTRAL, 1.0f, 1.0f);
		target.discard();
		level.addFreshEntity(fish);
		if (attacker instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.fish_fish_fish.converted", name).withStyle(ChatFormatting.AQUA));
		}
	}

	/** Whether a hit may turn this into a fish: an ordinary, unnamed, unowned, non-boss mob. */
	public static boolean canBecomeFish(LivingEntity target) {
		if (!(target instanceof Mob mob) || target instanceof AbstractVillager || target.hasCustomName()) {
			return false;
		}
		if (target instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
			return false;
		}
		EntityType<?> type = target.getType();
		return !mob.isLeashed() && !mob.isPassenger() && !mob.isVehicle() && target.getMaxHealth() <= MAX_HEALTH
				&& !target.is(ConventionalEntityTypeTags.BOSSES) && !target.is(Souls.VACUUM_IMMUNE) && !target.is(IMMUNE)
				&& !BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(MyMod.id("fish_fish"));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		FusionTooltipItem.appendFusionLines(tooltip, "item.tomyen.fish_fish_fish.club", "item.tomyen.fish_fish_fish.fish");
	}
}
