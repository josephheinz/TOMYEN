package com.jheinz.tomyen.herbalism;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Kettle: a copper kettle for brewing herbal teas. Set over a campfire it hangs from a little iron tripod
 * ({@link #HUNG}); it also sits on a magma block. Either way it needs the heat to brew.
 * <ol>
 * <li>Fill it: a water bucket fills it (three cups), a water bottle adds a cup.</li>
 * <li>Drop in a tea herb (see {@link #brews}): it steeps for {@link KettleBlockEntity#STEEP_TICKS} ticks over the heat,
 * steaming.</li>
 * <li>Pour: a glass bottle takes a cup of the tea, until it's empty. (Before an herb goes in, a bottle takes the water
 * back.)</li>
 * </ol>
 * Use it empty-handed to see how it's getting on.
 */
public class KettleBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final int MAX_WATER = 3;
	public static final IntegerProperty WATER = IntegerProperty.create("water", 0, MAX_WATER);
	public static final EnumProperty<Tea> TEA = EnumProperty.create("tea", Tea.class);
	/** Over a campfire, hanging from its tripod. */
	public static final BooleanProperty HUNG = BooleanProperty.create("hung");
	private static final VoxelShape SHAPE = Block.column(10.0, 0.0, 10.0);

	public enum Tea implements StringRepresentable {
		/** Plain water, or nothing. */
		NONE("none"),
		STEEPING("steeping"),
		READY("ready");

		private final String name;

		Tea(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public KettleBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATER, 0).setValue(TEA, Tea.NONE)
				.setValue(HUNG, false));
	}

	/** Tea herb to the tea it brews. Built late: the items it names are registered after this block. */
	public static Map<Item, Item> brews() {
		return Map.of(
				Herbalism.RADROOT.asItem(), Herbalism.ANTI_RADIATION_TEA,
				Herbalism.CHAMOMILE.asItem(), Herbalism.CALMING_TEA,
				Herbalism.GINSENG.asItem(), Herbalism.VIGOR_TONIC,
				Herbalism.MOONPETAL.asItem(), Herbalism.MOONPETAL_TEA);
	}

	/** Whether there's heat under it: a lit campfire or a magma block. */
	public static boolean isHeated(Level level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return CampfireBlock.isLitCampfire(below) || below.is(Blocks.MAGMA_BLOCK);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KettleBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel ? createTickerHelper(type, Herbalism.KETTLE_ENTITY, KettleBlockEntity::serverTick) : null;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		int water = state.getValue(WATER);
		Tea tea = state.getValue(TEA);
		if (stack.is(Items.WATER_BUCKET)) {
			if (tea != Tea.NONE || water == MAX_WATER) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			pour(level, pos, state.setValue(WATER, MAX_WATER), player, hand, stack, new ItemStack(Items.BUCKET), SoundEvents.BUCKET_EMPTY,
					GameEvent.FLUID_PLACE);
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.BUCKET)) {
			if (tea != Tea.NONE || water < MAX_WATER) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			pour(level, pos, state.setValue(WATER, 0), player, hand, stack, new ItemStack(Items.WATER_BUCKET), SoundEvents.BUCKET_FILL,
					GameEvent.FLUID_PICKUP);
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.POTION)) {
			PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
			if (potion == null || !potion.is(Potions.WATER) || tea != Tea.NONE || water == MAX_WATER) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			pour(level, pos, state.setValue(WATER, water + 1), player, hand, stack, new ItemStack(Items.GLASS_BOTTLE),
					SoundEvents.BOTTLE_EMPTY, GameEvent.FLUID_PLACE);
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.GLASS_BOTTLE)) {
			if (water == 0 || tea == Tea.STEEPING) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			ItemStack cup = PotionContents.createItemStack(Items.POTION, Potions.WATER);
			BlockState after = state.setValue(WATER, water - 1);
			if (tea == Tea.READY && level.getBlockEntity(pos) instanceof KettleBlockEntity kettle) {
				Item brewed = kettle.herb() == null ? null : brews().get(kettle.herb());
				if (brewed != null) {
					cup = new ItemStack(brewed);
				}
				if (water == 1) {
					after = after.setValue(TEA, Tea.NONE);
					if (!level.isClientSide()) {
						kettle.clear();
					}
				}
			}
			pour(level, pos, after, player, hand, stack, cup, SoundEvents.BOTTLE_FILL, GameEvent.FLUID_PICKUP);
			return InteractionResult.SUCCESS;
		}
		if (brews().containsKey(stack.getItem())) {
			if (tea != Tea.NONE || water == 0) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (!level.isClientSide() && level.getBlockEntity(pos) instanceof KettleBlockEntity kettle) {
				kettle.steep(stack.getItem());
				player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
				stack.consume(1, player);
				level.setBlock(pos, state.setValue(TEA, Tea.STEEPING), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 1.4f);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** Swaps what's in the player's hand for what came out, and the kettle for its new state. */
	private static void pour(Level level, BlockPos pos, BlockState after, Player player, InteractionHand hand, ItemStack held, ItemStack result,
			SoundEvent sound, net.minecraft.core.Holder<GameEvent> event) {
		if (level.isClientSide()) {
			return;
		}
		player.awardStat(Stats.ITEM_USED.get(held.getItem()));
		player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, result));
		level.setBlock(pos, after, Block.UPDATE_ALL);
		level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
		level.gameEvent(null, event, pos);
	}

	/** Empty-handed: how it's getting on, over the hotbar. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		int water = state.getValue(WATER);
		Component message;
		if (water == 0) {
			message = Component.translatable("message.tomyen.kettle.empty");
		} else if (state.getValue(TEA) == Tea.NONE) {
			message = Component.translatable("message.tomyen.kettle.water", water);
		} else if (state.getValue(TEA) == Tea.READY) {
			message = Component.translatable("message.tomyen.kettle.ready", water);
		} else if (!isHeated(level, pos)) {
			message = Component.translatable("message.tomyen.kettle.cold");
		} else {
			float progress = level.getBlockEntity(pos) instanceof KettleBlockEntity kettle ? kettle.progress() : 0.0f;
			message = Component.translatable("message.tomyen.kettle.steeping", Math.round(progress * 100.0f));
		}
		player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.GOLD));
		return InteractionResult.SUCCESS;
	}

	/** Steam from the spout while there's water over the heat; a busier plume once the tea is ready. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(WATER) == 0 || !isHeated(level, pos)) {
			return;
		}
		Tea tea = state.getValue(TEA);
		if (random.nextInt(tea == Tea.READY ? 2 : tea == Tea.STEEPING ? 3 : 6) != 0) {
			return;
		}
		Direction facing = state.getValue(FACING);
		// The spout's tip, on the kettle's facing side.
		double x = pos.getX() + 0.5 + facing.getStepX() * 0.43;
		double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.43;
		level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 0.62, z, facing.getStepX() * 0.01, 0.03, facing.getStepZ() * 0.01);
		if (tea == Tea.READY && random.nextInt(8) == 0) {
			level.playLocalSound(pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.3f, 1.4f, false);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(BlockTags.CAMPFIRES) || canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HUNG, below.is(BlockTags.CAMPFIRES));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (direction != Direction.DOWN) {
			return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
		}
		if (!state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return state.setValue(HUNG, neighbourState.is(BlockTags.CAMPFIRES));
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return state.getValue(TEA) == Tea.READY ? state.getValue(WATER) * 5 : state.getValue(WATER);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WATER, TEA, HUNG);
	}
}
