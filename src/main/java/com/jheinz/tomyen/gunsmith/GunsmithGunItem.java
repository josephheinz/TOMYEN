package com.jheinz.tomyen.gunsmith;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.GunSpread;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.ModDamageTypes;
import com.jheinz.tomyen.attachment.AttachmentItem;
import com.jheinz.tomyen.attachment.Attachments;
import com.jheinz.tomyen.shotgun.ShotgunAction;
import com.jheinz.tomyen.shotgun.ShotgunItem;
import com.jheinz.tomyen.shotgun.Shotguns;
import com.jheinz.tomyen.silver.EvilBane;

/**
 * A gun from the Gunsmith set that takes its rounds one at a time, as a shotgun's tube does (see {@link ShotgunItem}): the
 * Slingshot's pouch, the Sawed-off's and the Flare Gun's break-open chambers, the Lever-Action's tube, the Snub Revolver's
 * cylinder and the Harpoon Gun's barrel. Loading, the action and its timing are the shotgun's, kept on the stack so every
 * client can animate them (client: GunsmithAnimation).
 *
 * <p>A gun that {@link Traits#closes() closes} has to be opened to load (broken open, its cylinder swung out) and closed again
 * however many rounds went in, so every load ends with the closing stroke; firing while it's open just closes it.
 */
public class GunsmithGunItem extends ShotgunItem {
	/** Pellets that draw a trail (the rest would just make a mess). */
	private static final int TRAILS = 3;

	/**
	 * How the gun loads and shoots.
	 *
	 * @param shellTicks ticks to load one round
	 * @param closeTicks ticks to work the action once loading stops: close the breech, swing the cylinder in, cock it
	 * @param closes     every load ends by closing the action, not only one that started from empty
	 * @param handgun    shoots fine one-handed
	 * @param powder     a gunpowder gun: smoke and flame at the muzzle and smoke trails; otherwise quiet trails of the round
	 * @param volume     how loud a shot is
	 */
	public record Traits(int shellTicks, int closeTicks, boolean closes, boolean handgun, boolean powder, float volume) {
	}

	private final Traits traits;

	public GunsmithGunItem(Properties properties, GunStats stats, List<Shell> shells, int capacity, Map<FireMode, Handling> handling,
			Traits traits) {
		super(properties, stats, shells, capacity, handling);
		this.traits = traits;
	}

	public Traits traits() {
		return traits;
	}

	@Override
	public boolean isHandgun() {
		return traits.handgun();
	}

	@Override
	protected int shellTicks() {
		return traits.shellTicks();
	}

	@Override
	protected int chamberTicks() {
		return traits.closeTicks();
	}

	/** A gun with no action to work (the Slingshot) is ready as soon as the round is in. */
	@Override
	protected boolean chambersAfterLoading() {
		return traits.closeTicks() > 0;
	}

	/** A gun opened to load is closed again after any load; the rest only chamber after loading from empty. */
	@Override
	public void reload(ServerPlayer player, ItemStack gun) {
		super.reload(player, gun);
		ShotgunAction action = gun.get(Shotguns.SHOTGUN_ACTION);
		if (traits.closes() && action != null && action.kind() == ShotgunAction.Kind.LOAD && !action.chambering()) {
			gun.set(Shotguns.SHOTGUN_ACTION, new ShotgunAction(action.kind(), action.start(), action.ticks(), action.shell(), true));
		}
	}

	protected Shell shellOf(Holder<Item> item) {
		for (Shell shell : shells()) {
			if (item.value() == shell.item()) {
				return shell;
			}
		}
		return null;
	}

	/** How many rounds one pull of the trigger fires (both barrels of a double, say). */
	protected int roundsPerShot(ItemStack gun, int loaded) {
		return 1;
	}

	/** The shot's report. */
	protected SoundEvent shotSound(ItemStack gun, int rounds) {
		return stats().sound();
	}

	/** Fires the loaded round (or rounds). Returns false (and does nothing) if there's nothing to fire. */
	@Override
	public boolean fireRound(ServerLevel level, Player player, ItemStack gun, InteractionHand hand) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		boolean free = player.hasInfiniteMaterials();
		Shell shell = loaded == null || loaded.isEmpty() ? (free ? shells().getFirst() : null) : shellOf(loaded.ammo());
		if (shell == null) {
			return false;
		}
		int rounds = Math.max(1, Math.min(roundsPerShot(gun, loaded == null ? 0 : loaded.rounds()),
				free ? Integer.MAX_VALUE : loaded.rounds()));
		discharge(level, player, gun, shell, rounds);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), shotSound(gun, rounds), SoundSource.PLAYERS,
				traits.volume() * Attachments.loudness(gun), 0.95f + player.getRandom().nextFloat() * 0.1f);
		player.awardStat(Stats.ITEM_USED.get(this));
		if (!free) {
			MagazineContents left = loaded.withRounds(loaded.rounds() - rounds);
			gun.set(ModComponents.LOADED_MAGAZINE, left);
			if (gun.getDamageValue() + rounds >= gun.getMaxDamage() && !left.isEmpty()) {
				// It's about to fall apart: what's still loaded comes back out.
				gun.remove(ModComponents.LOADED_MAGAZINE);
				player.getInventory().placeItemBackInInventory(new ItemStack(left.ammo(), left.rounds()), Prediction.SERVER_ONLY);
			}
			gun.hurtAndBreak(rounds, player, hand);
		}
		return true;
	}

	/**
	 * Throws {@code rounds} rounds' worth of pellets: each pellet that finds a target adds to one blow, so they aren't lost to
	 * the target's hurt cooldown, and damage falls off with distance as the shell says.
	 */
	protected void discharge(ServerLevel level, Player player, ItemStack gun, Shell shell, int rounds) {
		Handling handled = handling(gun);
		float aim = player instanceof ServerPlayer serverPlayer ? GunSpread.serverShot(serverPlayer, stats(gun, player)) : 0.0f;
		float[] centre = scatter(player, player.getXRot(), player.getYRot(), aim);
		Vec3 eye = player.getEyePosition();
		Vec3 muzzle = muzzle(player);
		DamageSource source = new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.BULLET), player);
		float spread = shell.spreadDegrees() * handled.pelletSpread() * GunsmithAttachments.pelletSpread(gun);

		Map<Entity, Float> blows = new LinkedHashMap<>();
		int pellets = shell.pellets() * rounds;
		for (int i = 0; i < pellets; i++) {
			float[] dir = scatter(player, centre[0], centre[1], spread);
			Vec3 look = Vec3.directionFromRotation(dir[0], dir[1]);
			Vec3 end = eye.add(look.scale(shell.range()));
			BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (blockHit.getType() != HitResult.Type.MISS) {
				end = blockHit.getLocation();
			}
			AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
			EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
					entity -> !entity.isSpectator() && entity.isPickable(), eye.distanceToSqr(end));
			Vec3 stop;
			if (entityHit != null) {
				stop = entityHit.getLocation();
				blows.merge(entityHit.getEntity(), damageAt(shell, eye.distanceTo(stop)) * handled.damage(), Float::sum);
				level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 3, 0.08, 0.08, 0.08, 0.2);
			} else if (blockHit.getType() == HitResult.Type.BLOCK) {
				stop = blockHit.getLocation();
				BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockHit.getBlockPos()));
				level.sendParticles(debris, stop.x, stop.y, stop.z, 3, 0.04, 0.04, 0.04, 0.1);
			} else {
				stop = end;
			}
			if (i < TRAILS) {
				trail(level, shell, muzzle, stop, Attachments.suppressed(gun));
			}
		}
		blows.forEach((target, damage) -> {
			target.hurtServer(level, source, damage * EvilBane.multiplier(shell.item(), target));
			EvilBane.afterHit(level, shell.item(), target);
		});
		if (traits.powder()) {
			level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4 + 3 * rounds, 0.06, 0.06, 0.06, 0.02);
			level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 2 * rounds, 0.03, 0.03, 0.03, 0.01);
		}
	}

	/** Pellets do full damage out to the shell's full-damage range, falling off linearly to its minimum by its falloff range. */
	protected static float damageAt(Shell shell, double distance) {
		double t = Math.clamp((distance - shell.fullDamageRange()) / (shell.falloffRange() - shell.fullDamageRange()), 0.0, 1.0);
		return shell.pelletDamage() * (float) (1.0 - t * (1.0 - shell.minDamageFactor()));
	}

	protected static Vec3 muzzle(Player player) {
		return player.getEyePosition().add(player.getViewVector(1.0f).scale(1.2)).add(0, -0.15, 0);
	}

	/** A smoke trail for a gunpowder gun; for the quiet ones, a faint trail of bits of the round itself. */
	protected void trail(ServerLevel level, Shell shell, Vec3 from, Vec3 to, boolean suppressed) {
		if (traits.powder()) {
			GunItem.drawTracer(level, from, to, suppressed);
			return;
		}
		ItemParticleOption bits = new ItemParticleOption(ParticleTypes.ITEM, shell.item());
		Vec3 path = to.subtract(from);
		int steps = (int) (path.length() / 2.0);
		for (int i = 1; i < steps; i++) {
			Vec3 p = from.add(path.scale(i / (double) steps));
			level.sendParticles(bits, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}

	/** A direction (pitch, yaw) within {@code spread} degrees of the given one, uniform over the cone's disc. */
	protected static float[] scatter(Player player, float pitch, float yaw, float spread) {
		if (spread <= 0.0f) {
			return new float[] {pitch, yaw};
		}
		float radius = spread * (float) Math.sqrt(player.getRandom().nextFloat());
		float angle = player.getRandom().nextFloat() * (float) (Math.PI * 2.0);
		return new float[] {pitch + radius * (float) Math.sin(angle), yaw + radius * (float) Math.cos(angle)};
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		MagazineContents loaded = stack.get(ModComponents.LOADED_MAGAZINE);
		if (loaded == null || loaded.isEmpty()) {
			tooltip.accept(Component.translatable("item.tomyen.gunsmith.empty", capacity(stack)).withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("item.tomyen.magazine.rounds", loaded.rounds(), capacity(stack),
					new ItemStack(loaded.ammo()).getHoverName()).withStyle(ChatFormatting.GRAY));
		}
		if (stats(stack).fireModes().size() > 1 || fireMode(stack) != FireMode.SEMI) {
			tooltip.accept(Component.translatable("item.tomyen.gun.fire_mode", Component.translatable(fireMode(stack).translationKey()))
					.withStyle(ChatFormatting.GRAY));
		}
		for (AttachmentItem attachment : Attachments.all(stack)) {
			tooltip.accept(Component.translatable("item.tomyen.gun.attachment", Component.translatable(attachment.getDescriptionId()))
					.withStyle(ChatFormatting.BLUE));
		}
		tooltip.accept(Component.translatable(getDescriptionId() + ".hint").withStyle(ChatFormatting.DARK_GRAY));
		if (stats(stack).fireModes().size() > 1) {
			tooltip.accept(Component.translatable("item.tomyen.gun.switch_fire_mode", Component.keybind("key.tomyen.fire_mode"))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		if (stats(stack).scoped()) {
			tooltip.accept(Component.translatable("item.tomyen.gun.scope_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		tooltip.accept(Component.translatable("item.tomyen.gunsmith.load_hint").withStyle(ChatFormatting.DARK_GRAY));
		if (!Attachments.all(stack).isEmpty()) {
			tooltip.accept(Component.translatable("item.tomyen.gun.remove_attachment_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
