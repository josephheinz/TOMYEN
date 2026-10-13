package com.jheinz.mymod.shoddy;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.FireMode;
import com.jheinz.mymod.GunStats;
import com.jheinz.mymod.MagazineContents;
import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.shotgun.ShotgunAction;
import com.jheinz.mymod.shotgun.ShotgunItem;
import com.jheinz.mymod.shotgun.Shotguns;

/**
 * A homemade gun: pipe, tape and scrap, loaded a round at a time like a shotgun's tube (see {@link ShotgunItem}). Each
 * pull of the trigger can go wrong, and more often the more worn the gun is:
 * <ul>
 * <li>a <b>misfire</b>: the hammer falls with a dry click, nothing goes off and the round stays in for another try;</li>
 * <li>a <b>jam</b>: the shot goes off but the case sticks, and the gun won't fire or load until a punch clears it (a
 * slow, rattling working of the action);</li>
 * <li>a <b>backfire</b>: gas blows back out of the breech in a puff of smoke and burns the shooter a little.</li>
 * </ul>
 * Misfires are rolled from {@link ShoddyState} so the client knows of them too; jams and backfires only the server
 * decides. Durability is low and a worn-out gun falls apart for good; an anvil and iron mend it.
 */
public class ShoddyGunItem extends ShotgunItem {
	/** How long a misfire's dry click holds the trigger. */
	public static final int MISFIRE_TICKS = 10;
	/** How long clearing a jam takes (before an Overclock). */
	public static final int CLEAR_TICKS = 30;

	/**
	 * How badly the gun behaves, new and worn out: each chance runs from its new value to its worn one with the square
	 * of the wear (damage over durability), so a gun stays fairly honest for its first half.
	 *
	 * @param backfireDamage what a backfire does to the shooter
	 */
	public record Quirks(float misfire, float misfireWorn, float jam, float jamWorn, float backfire, float backfireWorn,
			float backfireDamage) {
		private static float at(float fresh, float worn, float wear) {
			return fresh + (worn - fresh) * wear * wear;
		}
	}

	/**
	 * How the gun loads and handles.
	 *
	 * @param shellTicks   ticks to load one round
	 * @param chamberTicks ticks to work the action once loading an empty gun is done (cocking it, ramming the charge)
	 * @param chambers     whether loading an empty gun ends by working the action (else the round goes straight in)
	 * @param handgun      shoots fine one-handed
	 * @param shove        how hard each shot shoves the shooter back (blocks per tick)
	 * @param looseShot    loads that are poured in rather than a made round, each taking a pinch of gunpowder with it
	 */
	public record Traits(int shellTicks, int chamberTicks, boolean chambers, boolean handgun, float shove, List<Item> looseShot) {
	}

	private final Quirks quirks;
	private final Traits traits;

	public ShoddyGunItem(Properties properties, GunStats stats, List<Shell> shells, int capacity, int cycleTicks, Quirks quirks,
			Traits traits) {
		super(properties, stats, shells, capacity, Map.of(stats.fireModes().getFirst(), new Handling(1.0f, 1.0f, 1.0f, cycleTicks)));
		this.quirks = quirks;
		this.traits = traits;
	}

	public Traits traits() {
		return traits;
	}

	// ------------------------------------------------------------------------------------------------ condition

	public static ShoddyState state(ItemStack gun) {
		return gun.getOrDefault(ShoddyGuns.SHODDY_STATE, ShoddyState.NEW);
	}

	public static boolean jammed(ItemStack gun) {
		return state(gun).jammed();
	}

	/** How worn the gun is: 0 new, 1 about to fall apart. */
	public static float wear(ItemStack gun) {
		int max = gun.getMaxDamage();
		return max <= 0 ? 0.0f : Math.clamp(gun.getDamageValue() / (float) max, 0.0f, 1.0f);
	}

	public float misfireChance(ItemStack gun) {
		return Quirks.at(quirks.misfire(), quirks.misfireWorn(), wear(gun));
	}

	public float jamChance(ItemStack gun) {
		return Quirks.at(quirks.jam(), quirks.jamWorn(), wear(gun));
	}

	public float backfireChance(ItemStack gun) {
		return Quirks.at(quirks.backfire(), quirks.backfireWorn(), wear(gun));
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
		return traits.chamberTicks();
	}

	@Override
	protected boolean chambersAfterLoading() {
		return traits.chambers();
	}

	// ------------------------------------------------------------------------------------------------- shooting

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		if (jammed(gun)) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("item.mymod.shoddy.jammed").withStyle(ChatFormatting.RED));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), ShoddyGuns.MISFIRE_CLICK, SoundSource.PLAYERS, 0.5f, 0.8f);
			}
			return InteractionResult.FAIL;
		}
		return super.use(level, player, hand);
	}

	/** Rolls for a misfire (the same roll on both sides), and shoves the shooter back when it does go off. */
	@Override
	protected InteractionResult beforeShot(Level level, Player player, ItemStack gun) {
		ShoddyState state = state(gun);
		boolean misfire = state.roll(1) < misfireChance(gun);
		if (level instanceof ServerLevel server) {
			int seed = state.seed() != 0 ? state.seed() : server.getRandom().nextInt() | 1;
			gun.set(ShoddyGuns.SHODDY_STATE, new ShoddyState(seed, state.pulls() + 1, state.jammed()));
		}
		if (misfire) {
			player.getCooldowns().addCooldown(gun, MISFIRE_TICKS);
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("item.mymod.shoddy.misfire").withStyle(ChatFormatting.YELLOW));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), ShoddyGuns.MISFIRE_CLICK, SoundSource.PLAYERS, 0.8f,
						0.95f + player.getRandom().nextFloat() * 0.1f);
			}
			return InteractionResult.CONSUME;
		}
		if (level.isClientSide() && traits.shove() > 0.0f) {
			// The client moves its own player: a kick straight back from where it's pointed, and a little hop.
			Vec3 look = player.getLookAngle();
			float shove = traits.shove() * (player.onGround() ? 1.0f : 0.6f);
			player.push(-look.x * shove, Math.max(0.0, -look.y * shove * 0.5) + 0.12, -look.z * shove);
		}
		return null;
	}

	/** After the shot: a chance the breech blows back on the shooter, and a chance the case sticks. */
	@Override
	protected boolean afterShot(ServerLevel level, Player player, ItemStack gun, Holder<Item> fired, long now) {
		if (gun.isEmpty()) {
			// It fell apart with that shot.
			return true;
		}
		if (level.getRandom().nextFloat() < backfireChance(gun)) {
			backfire(level, player);
		}
		if (level.getRandom().nextFloat() < jamChance(gun)) {
			gun.set(ShoddyGuns.SHODDY_STATE, state(gun).withJammed(true));
			gun.remove(Shotguns.SHOTGUN_ACTION);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), ShoddyGuns.JAM, SoundSource.PLAYERS, 0.9f, 1.0f);
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("item.mymod.shoddy.jammed").withStyle(ChatFormatting.RED));
			}
			return true;
		}
		return false;
	}

	private void backfire(ServerLevel level, Player player) {
		Vec3 breech = player.getEyePosition().add(player.getViewVector(1.0f).scale(0.6)).add(0, -0.25, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, breech.x, breech.y, breech.z, 6, 0.08, 0.08, 0.08, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, breech.x, breech.y, breech.z, 10, 0.12, 0.1, 0.12, 0.03);
		level.sendParticles(ParticleTypes.FLAME, breech.x, breech.y, breech.z, 4, 0.05, 0.05, 0.05, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), ShoddyGuns.BACKFIRE, SoundSource.PLAYERS, 1.0f,
				0.9f + level.getRandom().nextFloat() * 0.2f);
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ShoddyGuns.BACKFIRE_DAMAGE));
		player.hurtServer(level, source, quirks.backfireDamage());
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("item.mymod.shoddy.backfire").withStyle(ChatFormatting.GOLD));
		}
	}

	// ------------------------------------------------------------------------------------------------- loading

	/** Punching a jammed gun clears it; otherwise it loads as a shotgun does. */
	@Override
	public void reload(ServerPlayer player, ItemStack gun) {
		if (jammed(gun)) {
			clear(player, gun);
			return;
		}
		super.reload(player, gun);
	}

	/** Knocks the stuck case out: a slow rattle of the action, and the gun is free once it's done. */
	private void clear(ServerPlayer player, ItemStack gun) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		Holder<Item> stuck = loaded != null && !loaded.isEmpty() ? loaded.ammo() : BuiltInRegistries.ITEM.wrapAsHolder(shells().getFirst().item());
		int ticks = Math.max(1, Math.round(CLEAR_TICKS * reloadFactor(player)));
		long now = player.level().getGameTime();
		gun.set(Shotguns.SHOTGUN_ACTION, new ShotgunAction(ShotgunAction.Kind.CLEAR, now, ticks, stuck, false));
		gun.set(ShoddyGuns.SHODDY_STATE, state(gun).withJammed(false));
		player.getCooldowns().addCooldown(gun, ticks);
		// The holder hears the clearing from the animation's cues; this is for everyone else.
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), ShoddyGuns.JAM_CLEAR, SoundSource.PLAYERS, 0.8f, 1.0f);
	}

	/** A loose load (gravel, flint) needs a pinch of gunpowder poured in first. */
	@Override
	protected void loadShell(ServerPlayer player, ItemStack gun, ShotgunAction action, long now) {
		if (!traits.looseShot().contains(action.shell().value()) || player.hasInfiniteMaterials()) {
			super.loadShell(player, gun, action, now);
			return;
		}
		int powder = player.getInventory().findSlotMatchingItem(new ItemStack(Items.GUNPOWDER));
		if (powder < 0) {
			gun.remove(Shotguns.SHOTGUN_ACTION);
			player.sendOverlayMessage(Component.translatable("item.mymod.shoddy.needs_powder").withStyle(ChatFormatting.RED));
			return;
		}
		int before = rounds(gun);
		super.loadShell(player, gun, action, now);
		if (rounds(gun) > before) {
			player.getInventory().getItem(powder).shrink(1);
		}
	}

	private static int rounds(ItemStack gun) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		return loaded == null ? 0 : loaded.rounds();
	}

	// ------------------------------------------------------------------------------------------------- tooltip

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		MagazineContents loaded = stack.get(ModComponents.LOADED_MAGAZINE);
		if (loaded == null || loaded.isEmpty()) {
			tooltip.accept(Component.translatable("item.mymod.shoddy.empty", capacity(stack)).withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("item.mymod.magazine.rounds", loaded.rounds(), capacity(stack),
					new ItemStack(loaded.ammo()).getHoverName()).withStyle(ChatFormatting.GRAY));
		}
		float wear = wear(stack);
		String condition = wear < 0.25f ? "sound" : wear < 0.5f ? "worn" : wear < 0.75f ? "battered" : "falling_apart";
		ChatFormatting color = wear < 0.25f ? ChatFormatting.GREEN : wear < 0.5f ? ChatFormatting.YELLOW
				: wear < 0.75f ? ChatFormatting.GOLD : ChatFormatting.RED;
		tooltip.accept(Component.translatable("item.mymod.shoddy.condition",
				Component.translatable("item.mymod.shoddy.condition." + condition).withStyle(color)).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.shoddy.odds", percent(misfireChance(stack)), percent(jamChance(stack)),
				percent(backfireChance(stack))).withStyle(ChatFormatting.DARK_GRAY));
		if (jammed(stack)) {
			tooltip.accept(Component.translatable("item.mymod.shoddy.jammed").withStyle(ChatFormatting.RED));
		}
		tooltip.accept(Component.translatable(getDescriptionId() + ".hint").withStyle(ChatFormatting.DARK_GRAY));
		if (stats().fireModes().contains(FireMode.BOLT)) {
			tooltip.accept(Component.translatable("item.mymod.shoddy.bolt_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		tooltip.accept(Component.translatable("item.mymod.shoddy.hint").withStyle(ChatFormatting.DARK_GRAY));
	}

	private static int percent(float chance) {
		return Math.round(chance * 100.0f);
	}
}
