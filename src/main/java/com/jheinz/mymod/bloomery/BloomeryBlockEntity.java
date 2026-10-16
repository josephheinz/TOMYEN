package com.jheinz.mymod.bloomery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Bloomery's charge and its heat. Raw iron (or iron ore) goes in the top slot, charcoal or coal in the fuel slot;
 * with both in, a piece of fuel lights and burns for {@link #BURN_TICKS}. A lit charge only smoulders, warming the
 * furnace to its {@link Fuel#smoulder} and no further. The bellows does the rest: every stroke drives heat in, up to
 * the fuel's {@link Fuel#ceiling}, and above the smoulder the furnace keeps losing heat, faster the hotter it is.
 * <p>
 * The bellows has a rhythm. After a stroke it takes {@link #REFILL_TICKS} to fill again; a stroke while it's still
 * filling wheezes (no heat, and the run of good strokes is broken). A stroke once it's full, within
 * {@link #WINDOW_TICKS} of filling, is a good one, worth more the longer the run of good strokes ({@link #combo}); a
 * later one still blows, for less, and starts the run over. Right-clicking the bellows, the screen's bellows button,
 * and a powered piston against any side of the furnace all work it.
 * <p>
 * At {@link #BLOOM_TEMPERATURE} the iron gathers into a bloom, twice as fast at a white heat ({@link #WHITE_HEAT}),
 * which only charcoal reaches: coal burns cooler. A finished bloom drops into the bloom slot. The forge step hammers
 * it into a Bloom Steel Ingot right there, {@link #FORGE_BLOWS} blows while the furnace is at least
 * {@link #FORGE_TEMPERATURE}, each drawing some heat; or take it to an anvil ({@link BloomHammering}).
 * <p>
 * Clients get the heat, the bellows' last stroke and the run in block entity updates (for the HUD); the screen reads
 * them from the menu's data slots.
 */
public class BloomeryBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int ORE_SLOT = 0;
	public static final int FUEL_SLOT = 1;
	public static final int BLOOM_SLOT = 2;
	public static final int INGOT_SLOT = 3;
	public static final int SLOTS = 4;

	public static final int AMBIENT_TEMPERATURE = 20;
	/** Where the bloom gathers. */
	public static final int BLOOM_TEMPERATURE = 1150;
	/** Where it gathers twice as fast. Only charcoal gets there. */
	public static final int WHITE_HEAT = 1300;
	/** The hottest the scale on the screen goes. */
	public static final int SCALE_TEMPERATURE = 1500;
	/** Progress a bloom takes: ten seconds at a bloom heat, five at a white heat. */
	public static final int BLOOM_WORK = 200;
	public static final int BURN_TICKS = 200;
	/** Degrees a tick a lit charge warms toward its smoulder. */
	public static final float SMOULDER_RISE = 4.0f;
	/** Degrees a tick a cold (unlit) furnace loses. */
	public static final float COOLING_UNLIT = 3.0f;

	public static final int REFILL_TICKS = 12;
	public static final int WINDOW_TICKS = 10;
	public static final int MAX_COMBO = 5;
	public static final int GOOD_HEAT = 55;
	public static final int COMBO_HEAT = 10;
	public static final int LATE_HEAT = 50;

	public static final int FORGE_BLOWS = 3;
	public static final int FORGE_TEMPERATURE = 900;
	public static final int FORGE_HEAT_COST = 40;
	private static final int FORGE_COOLDOWN = 6;
	private static final int SYNC_INTERVAL = 4;

	/** How a stroke of the bellows went. */
	public enum Stroke {
		NONE, EARLY, GOOD, LATE;

		public static Stroke of(int ordinal) {
			return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
		}
	}

	/** What burns in the charge: how hot it smoulders by itself, and the most the bellows can drive it to. */
	public enum Fuel {
		NONE(AMBIENT_TEMPERATURE, AMBIENT_TEMPERATURE), CHARCOAL(600, 1450), COAL(500, 1250);

		public final int smoulder;
		public final int ceiling;

		Fuel(int smoulder, int ceiling) {
			this.smoulder = smoulder;
			this.ceiling = ceiling;
		}

		public static Fuel of(ItemStack stack) {
			return stack.is(Items.CHARCOAL) ? CHARCOAL : stack.is(Items.COAL) ? COAL : NONE;
		}

		public static Fuel of(int ordinal) {
			return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
		}
	}

	// ContainerData indices, shared with the menu.
	public static final int DATA_TEMPERATURE = 0;
	public static final int DATA_BURN = 1;
	public static final int DATA_PROGRESS = 2;
	/** Ticks since the bellows' last stroke, up to {@link #SINCE_CAP}. */
	public static final int DATA_SINCE = 3;
	public static final int DATA_COMBO = 4;
	public static final int DATA_STROKE = 5;
	public static final int DATA_FORGE = 6;
	public static final int DATA_FUEL = 7;
	public static final int DATA_COUNT = 8;
	public static final int SINCE_CAP = 200;

	private static final int[] TOP_SLOTS = {ORE_SLOT};
	private static final int[] SIDE_SLOTS = {FUEL_SLOT};
	private static final int[] BOTTOM_SLOTS = {INGOT_SLOT, BLOOM_SLOT};
	private static final Component TITLE = Component.translatable("container.mymod.bloomery");

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private float temperature = AMBIENT_TEMPERATURE;
	private int burn;
	private Fuel fuel = Fuel.NONE;
	private int progress;
	/** Game time of the bellows' last stroke. */
	private long lastStroke = -SINCE_CAP;
	private int combo;
	private Stroke stroke = Stroke.NONE;
	private int forgeBlows;
	private long lastForge;
	/** The sides a powered piston pressed on last tick, as a bit per {@link Direction}. */
	private int pistons;
	private int syncTimer;
	private boolean syncDue;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_TEMPERATURE -> Math.round(temperature);
				case DATA_BURN -> burn;
				case DATA_PROGRESS -> progress;
				case DATA_SINCE -> level == null ? SINCE_CAP : sinceStroke(level.getGameTime());
				case DATA_COMBO -> combo;
				case DATA_STROKE -> stroke.ordinal();
				case DATA_FORGE -> forgeBlows;
				case DATA_FUEL -> fuel.ordinal();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public BloomeryBlockEntity(BlockPos pos, BlockState state) {
		super(Bloomery.BLOOMERY_ENTITY, pos, state);
	}

	public static boolean isOre(ItemStack stack) {
		return stack.is(Items.RAW_IRON) || stack.is(Items.IRON_ORE) || stack.is(Items.DEEPSLATE_IRON_ORE);
	}

	public static boolean isFuel(ItemStack stack) {
		return Fuel.of(stack) != Fuel.NONE;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BloomeryBlockEntity bloomery) {
		ServerLevel server = (ServerLevel) level;
		bloomery.checkPistons(server, pos);
		boolean changed = false;
		boolean canSmelt = bloomery.canSmelt();

		if (bloomery.burn <= 0 && canSmelt && isFuel(bloomery.items.get(FUEL_SLOT))) {
			ItemStack fuelStack = bloomery.items.get(FUEL_SLOT);
			bloomery.fuel = Fuel.of(fuelStack);
			fuelStack.shrink(1);
			bloomery.burn = BURN_TICKS;
			changed = true;
		}
		boolean lit = bloomery.burn > 0;
		if (lit) {
			bloomery.burn--;
		} else {
			bloomery.fuel = Fuel.NONE;
		}

		float before = bloomery.temperature;
		if (lit && bloomery.temperature < bloomery.fuel.smoulder) {
			bloomery.temperature = Math.min(bloomery.fuel.smoulder, bloomery.temperature + SMOULDER_RISE);
		} else if (lit) {
			// Air from the bellows dies away: the hotter, the faster.
			bloomery.temperature -= 1.0f + (bloomery.temperature - bloomery.fuel.smoulder) / 300.0f;
		} else {
			bloomery.temperature = Math.max(AMBIENT_TEMPERATURE, bloomery.temperature - COOLING_UNLIT);
		}
		if (Math.round(before) != Math.round(bloomery.temperature)) {
			changed = true;
			bloomery.syncDue = true;
		}

		if (canSmelt && bloomery.temperature >= BLOOM_TEMPERATURE) {
			bloomery.progress += bloomery.temperature >= WHITE_HEAT ? 2 : 1;
			if (bloomery.progress >= BLOOM_WORK) {
				bloomery.progress = 0;
				bloomery.items.get(ORE_SLOT).shrink(1);
				ItemStack blooms = bloomery.items.get(BLOOM_SLOT);
				if (blooms.isEmpty()) {
					bloomery.items.set(BLOOM_SLOT, new ItemStack(Bloomery.BLOOM));
				} else {
					blooms.grow(1);
				}
				level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8f, 0.7f);
			}
			changed = true;
		} else if (!canSmelt && bloomery.progress > 0) {
			bloomery.progress = 0;
			changed = true;
		}

		if (state.getValue(BloomeryBlock.LIT) != lit) {
			level.setBlock(pos, state.setValue(BloomeryBlock.LIT, lit), Block.UPDATE_ALL);
		}
		if (changed) {
			setChanged(level, pos, state);
		}
		if (bloomery.syncDue && ++bloomery.syncTimer >= SYNC_INTERVAL) {
			bloomery.sync();
		}
	}

	private boolean canSmelt() {
		if (!isOre(items.get(ORE_SLOT))) {
			return false;
		}
		ItemStack blooms = items.get(BLOOM_SLOT);
		return blooms.isEmpty() || blooms.is(Bloomery.BLOOM) && blooms.getCount() < blooms.getMaxStackSize();
	}

	/** A powered piston pushing on any side (it can't move the furnace, so it works the bellows), or one extended against it. */
	private void checkPistons(ServerLevel level, BlockPos pos) {
		int pressed = 0;
		for (Direction side : Direction.values()) {
			BlockPos at = pos.relative(side);
			BlockState neighbour = level.getBlockState(at);
			boolean pushing = false;
			if (neighbour.getBlock() instanceof PistonBaseBlock && neighbour.getValue(PistonBaseBlock.FACING) == side.getOpposite()) {
				pushing = level.hasNeighborSignal(at) || level.hasNeighborSignal(at.above());
			} else if (neighbour.is(Blocks.PISTON_HEAD)) {
				pushing = neighbour.getValue(PistonHeadBlock.FACING) == side.getOpposite();
			}
			if (pushing) {
				pressed |= 1 << side.ordinal();
			}
		}
		boolean newPress = (pressed & ~pistons) != 0;
		pistons = pressed;
		if (newPress) {
			level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.35f, 0.7f);
			pump(level);
		}
	}

	public int sinceStroke(long gameTime) {
		return (int) Mth.clamp(gameTime - lastStroke, 0, SINCE_CAP);
	}

	/** One stroke of the bellows, by hand or by piston. */
	public Stroke pump(ServerLevel level) {
		long now = level.getGameTime();
		long since = now - lastStroke;
		int heat;
		if (since < REFILL_TICKS) {
			stroke = Stroke.EARLY;
			combo = 0;
			heat = 0;
		} else if (since <= REFILL_TICKS + WINDOW_TICKS) {
			stroke = Stroke.GOOD;
			combo = Math.min(MAX_COMBO, combo + 1);
			heat = GOOD_HEAT + COMBO_HEAT * combo;
		} else {
			stroke = Stroke.LATE;
			combo = 0;
			heat = LATE_HEAT;
		}
		lastStroke = now;
		boolean lit = burn > 0;
		if (lit && heat > 0 && temperature < fuel.ceiling) {
			temperature = Math.min(fuel.ceiling, temperature + heat);
		}
		effects(level, lit);
		setChanged();
		sync();
		return stroke;
	}

	private void effects(ServerLevel level, boolean lit) {
		BlockState state = getBlockState();
		Direction facing = state.getValue(BloomeryBlock.FACING);
		Vec3 bellows = BloomeryBlock.toWorld(facing, worldPosition, 2.5, 5.0, 14.0);
		Vec3 throat = BloomeryBlock.toWorld(facing, worldPosition, 10.0, 16.2, 7.0);
		var random = level.getRandom();
		switch (stroke) {
			case EARLY -> {
				// A wheeze: the bag isn't full yet.
				level.playSound(null, worldPosition, SoundEvents.HORSE_BREATHE, SoundSource.BLOCKS, 0.5f, 1.6f + random.nextFloat() * 0.2f);
				level.sendParticles(ParticleTypes.POOF, bellows.x, bellows.y, bellows.z, 2, 0.05, 0.05, 0.05, 0.005);
			}
			case GOOD -> {
				level.playSound(null, worldPosition, SoundEvents.HORSE_BREATHE, SoundSource.BLOCKS, 1.0f, 0.55f + combo * 0.05f);
				if (lit) {
					level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.25f + combo * 0.05f, 0.6f + combo * 0.08f);
					level.sendParticles(ParticleTypes.FLAME, throat.x, throat.y, throat.z, 4 + combo * 2, 0.12, 0.05, 0.12, 0.02 + combo * 0.006);
					if (combo >= 3) {
						level.sendParticles(ParticleTypes.LAVA, throat.x, throat.y, throat.z, combo - 2, 0.1, 0.0, 0.1, 0.0);
					}
				}
			}
			case LATE -> {
				level.playSound(null, worldPosition, SoundEvents.HORSE_BREATHE, SoundSource.BLOCKS, 0.8f, 0.8f);
				if (lit) {
					level.sendParticles(ParticleTypes.FLAME, throat.x, throat.y, throat.z, 2, 0.1, 0.04, 0.1, 0.01);
				}
			}
			default -> {
			}
		}
		if (lit) {
			level.sendParticles(ParticleTypes.SMOKE, throat.x, throat.y, throat.z, 3, 0.12, 0.05, 0.12, 0.02);
		} else {
			// Cold: just ash puffed out of the arch.
			Vec3 arch = BloomeryBlock.toWorld(facing, worldPosition, 10.0, 3.5, 1.5);
			level.sendParticles(ParticleTypes.ASH, arch.x, arch.y, arch.z, 6, 0.15, 0.1, 0.1, 0.01);
		}
	}

	/** One blow of the forge step's hammer on the bloom in the bloom slot. */
	public void forge(ServerLevel level) {
		long now = level.getGameTime();
		if (now - lastForge < FORGE_COOLDOWN) {
			return;
		}
		ItemStack blooms = items.get(BLOOM_SLOT);
		ItemStack ingots = items.get(INGOT_SLOT);
		if (blooms.isEmpty() || !ingots.isEmpty() && (!ingots.is(Bloomery.SET.ingot) || ingots.getCount() >= ingots.getMaxStackSize())) {
			return;
		}
		lastForge = now;
		Vec3 arch = BloomeryBlock.toWorld(getBlockState().getValue(BloomeryBlock.FACING), worldPosition, 10.0, 2.5, 1.0);
		if (temperature < FORGE_TEMPERATURE) {
			// Too cold to work: the hammer just rings off it.
			level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 1.8f);
			return;
		}
		temperature -= FORGE_HEAT_COST;
		level.sendParticles(ParticleTypes.CRIT, arch.x, arch.y, arch.z, 8, 0.1, 0.05, 0.1, 0.25);
		if (++forgeBlows >= FORGE_BLOWS) {
			forgeBlows = 0;
			blooms.shrink(1);
			if (ingots.isEmpty()) {
				items.set(INGOT_SLOT, new ItemStack(Bloomery.SET.ingot));
			} else {
				ingots.grow(1);
			}
			level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.0f);
			level.sendParticles(ParticleTypes.LAVA, arch.x, arch.y, arch.z, 2, 0.1, 0.0, 0.1, 0.0);
		} else {
			level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.2f + forgeBlows * 0.15f);
		}
		setChanged();
		sync();
	}

	private void sync() {
		syncDue = false;
		syncTimer = 0;
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	// --- What the HUD reads (a client's copy is kept up to date by block entity updates) -----------------------

	public float temperature() {
		return temperature;
	}

	public long lastStroke() {
		return lastStroke;
	}

	public int combo() {
		return combo;
	}

	public Stroke stroke() {
		return stroke;
	}

	public Fuel fuel() {
		return fuel;
	}

	public boolean lit() {
		return burn > 0;
	}

	public float progress() {
		return (float) progress / BLOOM_WORK;
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putFloat("temperature", temperature);
		tag.putInt("burn", burn);
		tag.putInt("fuel", fuel.ordinal());
		tag.putInt("progress", progress);
		tag.putLong("last_stroke", lastStroke);
		tag.putInt("combo", combo);
		tag.putInt("stroke", stroke.ordinal());
		return tag;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		temperature = input.getFloatOr("temperature", AMBIENT_TEMPERATURE);
		burn = input.getIntOr("burn", 0);
		fuel = Fuel.of(input.getIntOr("fuel", 0));
		progress = input.getIntOr("progress", 0);
		lastStroke = input.getLongOr("last_stroke", -SINCE_CAP);
		combo = input.getIntOr("combo", 0);
		stroke = Stroke.of(input.getIntOr("stroke", 0));
		forgeBlows = input.getIntOr("forge_blows", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putFloat("temperature", temperature);
		output.putInt("burn", burn);
		output.putInt("fuel", fuel.ordinal());
		output.putInt("progress", progress);
		output.putLong("last_stroke", lastStroke);
		output.putInt("combo", combo);
		output.putInt("stroke", stroke.ordinal());
		output.putInt("forge_blows", forgeBlows);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected Component getDefaultName() {
		return TITLE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new BloomeryMenu(containerId, inventory, this, data);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case ORE_SLOT -> isOre(stack);
			case FUEL_SLOT -> isFuel(stack);
			default -> false;
		};
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return direction == Direction.DOWN ? BOTTOM_SLOTS : direction == Direction.UP ? TOP_SLOTS : SIDE_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return direction == Direction.DOWN && (slot == INGOT_SLOT || slot == BLOOM_SLOT);
	}
}
