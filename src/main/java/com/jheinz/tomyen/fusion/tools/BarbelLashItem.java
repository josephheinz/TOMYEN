package com.jheinz.tomyen.fusion.tools;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.catfish.GiantCatfishes;
import com.jheinz.tomyen.fusion.FusionTooltipItem;
import com.jheinz.tomyen.schnozzle.NoseHairWhipItem;

/**
 * The Barbel Lash (Nose Hair Whip + Catfish Whisker, at a pond): the nose-hair braid grown out into a catfish's
 * barbel, half as long again. Everything the Nose Hair Whip does (crack, snag, slime) at {@value #REACH} blocks and
 * {@value #SNAG} to snag, a diamond sword's bite, and the barbel feels for what it touches:
 * <ul>
 * <li>A hit drags the creature toward you, harder the further out it is (knockback resistance holds it back).
 * <li>Every lash sweeps the dropped items along its line to your feet.
 * </ul>
 * Being a Nose Hair Whip, it's drawn and swung by the whip's client code; its lash is its own texture.
 */
public class BarbelLashItem extends NoseHairWhipItem {
	public static final float REACH = 9.0f;
	public static final double SNAG = 14.0;
	/** A diamond sword's 7 with the player's base 1, at the whip's 1.8 swings a second. */
	private static final float ATTACK_DAMAGE = 6.0f;
	private static final float ATTACK_SPEED = -2.2f;
	private static final int DURABILITY = 900;
	/** A hit's drag: speed toward you per block away, capped, and a little lift. */
	private static final double DRAG_PER_BLOCK = 0.16;
	private static final double DRAG_MAX = 1.3;
	private static final double DRAG_LIFT = 0.3;
	/** Items within this far of the lash's line are swept in. */
	private static final double SWEEP_WIDTH = 1.25;
	private static final Identifier LASH = MyMod.id("textures/entity/barbel_lash_lash.png");

	public BarbelLashItem(Properties properties) {
		super(properties);
	}

	public static Properties properties() {
		return new Properties()
				.durability(DURABILITY)
				.repairable(GiantCatfishes.CATFISH_WHISKER)
				.enchantable(15)
				.rarity(Rarity.RARE)
				.component(DataComponents.ATTACK_RANGE, new AttackRange(0.0f, REACH, 0.0f, REACH + 0.5f, 0.2f, 1.0f))
				.component(DataComponents.ATTACK_ANIMATION, ATTACK_SWING)
				.component(DataComponents.WEAPON, new Weapon(1))
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.build());
	}

	@Override
	public float attackReach() {
		return REACH;
	}

	@Override
	public double snagRange() {
		return SNAG;
	}

	@Override
	public Identifier lashTexture() {
		return LASH;
	}

	/** The hit (cracked and maybe slimed by the whip), then dragged in. */
	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
		super.hurtEnemy(stack, mob, attacker);
		if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) {
			return;
		}
		Vec3 toAttacker = attacker.position().subtract(mob.position());
		Vec3 flat = new Vec3(toAttacker.x, 0.0, toAttacker.z);
		double distance = flat.length();
		if (distance < 2.0) {
			return;
		}
		double resist = 1.0 - Math.min(1.0, mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		double speed = Math.min((distance - 1.5) * DRAG_PER_BLOCK, DRAG_MAX) * resist;
		// Set after the hit, which knocks it the other way.
		mob.setDeltaMovement(flat.scale(speed / distance).add(0.0, DRAG_LIFT * resist, 0.0));
		mob.syncVelocity = true;
		level.sendParticles(ParticleTypes.SPLASH, mob.getX(), mob.getY(0.6), mob.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
	}

	/** Every swing: the whip's own crack on a charged miss, then the items along the lash are swept in. */
	@Override
	public void swung(ServerLevel level, Player player) {
		super.swung(level, player);
		Vec3 start = player.getEyePosition();
		Vec3 end = level.clip(new ClipContext(start, start.add(player.getViewVector(1.0f).scale(REACH)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player)).getLocation();
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(start, end).inflate(SWEEP_WIDTH),
				item -> item.isAlive() && distanceToLine(item.position().add(0.0, 0.2, 0.0), start, end) < SWEEP_WIDTH);
		for (ItemEntity item : items) {
			Vec3 toPlayer = player.position().subtract(item.position());
			// About ten ticks' flight, as the whip's snag throws an item.
			item.setDeltaMovement(toPlayer.x * 0.11, 0.2 + toPlayer.y * 0.1, toPlayer.z * 0.11);
			item.setNoPickUpDelay();
			item.syncVelocity = true;
			level.sendParticles(ParticleTypes.BUBBLE_POP, item.getX(), item.getY() + 0.2, item.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
		}
		if (!items.isEmpty()) {
			level.playSound(null, end.x, end.y, end.z, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8f, 1.3f);
		}
	}

	private static double distanceToLine(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double length = ab.lengthSqr();
		double t = length < 1.0e-6 ? 0.0 : Math.clamp(p.subtract(a).dot(ab) / length, 0.0, 1.0);
		return p.distanceTo(a.add(ab.scale(t)));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		FusionTooltipItem.appendFusionLines(tooltip, "item.tomyen.barbel_lash.reach", "item.tomyen.barbel_lash.drag",
				"item.tomyen.barbel_lash.sweep", "item.tomyen.barbel_lash.snag");
	}
}
