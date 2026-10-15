package com.jheinz.mymod.survival;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModItems;

/**
 * A crowbar. Swung, it's a heavy club with a big shove (see {@link Survival} for its stats), and it breaks crates,
 * barrels and other {@link Survival#PRYABLE} blocks quickly. Used:
 * <ul>
 * <li>on an iron door or iron trapdoor (anything that won't open by hand) it levers it open for
 * {@value #PRY_OPEN_TICKS} ticks, then lets it slam shut;</li>
 * <li>on a machine pinned in a bear trap or stunned by a pipe bomb, it tears a panel off: a hit and a piece of tech
 * scrap.</li>
 * </ul>
 * A machine finished off with a crowbar in hand also drops a bit of extra scrap, pried out of the wreck.
 */
public class CrowbarItem extends Item {
	public static final int PRY_OPEN_TICKS = 60;
	private static final int PRY_COOLDOWN = 20;
	private static final int STRIP_COOLDOWN = 40;
	private static final float STRIP_DAMAGE = 4.0f;

	/** Doors and trapdoors levered open, waiting to slam shut. */
	private static final List<Pried> PRIED = new ArrayList<>();

	private record Pried(ResourceKey<Level> dimension, BlockPos pos, long closeAt) {
	}

	public CrowbarItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		Player player = context.getPlayer();
		boolean door = state.getBlock() instanceof DoorBlock doorBlock && !doorBlock.type().canOpenByHand();
		boolean trapdoor = state.getBlock() instanceof TrapDoorBlock && state.is(Blocks.IRON_TRAPDOOR);
		if (!door && !trapdoor || state.getValue(BlockStateProperties.OPEN)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			if (door) {
				// Pry the lower half, so the pair stays together.
				BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
				BlockState lowerState = level.getBlockState(lower);
				((DoorBlock) state.getBlock()).setOpen(player, level, lowerState, lower, true);
				pos = lower;
			} else {
				level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
			}
			PRIED.add(new Pried(server.dimension(), pos.immutable(), server.getGameTime() + PRY_OPEN_TICKS));
			server.playSound(null, pos, Survival.CROWBAR_PRY, SoundSource.BLOCKS, 1.0f, 0.9f + server.getRandom().nextFloat() * 0.2f);
			Vec3 at = context.getClickLocation();
			server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.2, 0.1, 0.1);
			if (player != null) {
				context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
				player.getCooldowns().addCooldown(context.getItemInHand(), PRY_COOLDOWN);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!target.is(Survival.MACHINES) || !(target.hasEffect(Survival.TRAPPED) || target.hasEffect(Survival.STUNNED))) {
			return InteractionResult.PASS;
		}
		if (player.level() instanceof ServerLevel level) {
			target.hurtServer(level, level.damageSources().playerAttack(player), STRIP_DAMAGE);
			Vec3 at = target.getBoundingBox().getCenter();
			target.spawnAtLocation(level, new ItemStack(ModItems.TECH_SCRAP), (float) (at.y - target.getY()));
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), at.x, at.y, at.z, 14,
					0.25, 0.25, 0.25, 0.1);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), Survival.CROWBAR_PRY, SoundSource.PLAYERS, 1.0f, 0.75f);
			stack.hurtAndBreak(3, player, hand.asEquipmentSlot());
			player.getCooldowns().addCooldown(stack, STRIP_COOLDOWN);
		}
		return InteractionResult.SUCCESS;
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PRIED.isEmpty()) {
				return;
			}
			PRIED.removeIf(pried -> {
				ServerLevel level = server.getLevel(pried.dimension());
				if (level == null) {
					return true;
				}
				if (level.getGameTime() < pried.closeAt()) {
					return false;
				}
				BlockState state = level.getBlockState(pried.pos());
				// A redstone signal holding it open wins.
				if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)
						&& !level.hasNeighborSignal(pried.pos())) {
					if (state.getBlock() instanceof DoorBlock door) {
						door.setOpen(null, level, state, pried.pos(), false);
					} else if (state.getBlock() instanceof TrapDoorBlock) {
						level.setBlock(pried.pos(), state.setValue(BlockStateProperties.OPEN, false), Block.UPDATE_ALL);
						level.playSound(null, pried.pos(), net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
					}
				}
				return true;
			});
		});
		// A machine finished with a crowbar: a piece or two of scrap pried out of the wreck.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!entity.is(Survival.MACHINES) || !(entity.level() instanceof ServerLevel level)
					|| !(source.getEntity() instanceof Player player) || source.getDirectEntity() != player
					|| !(player.getMainHandItem().getItem() instanceof CrowbarItem)) {
				return;
			}
			int scrap = 1 + (level.getRandom().nextBoolean() ? 1 : 0);
			entity.spawnAtLocation(level, new ItemStack(ModItems.TECH_SCRAP, scrap), 0.5f);
		});
	}
}
