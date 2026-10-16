package com.jheinz.tomyen.starforge;

import static com.jheinz.tomyen.starforge.InfusionKind.ARMOR;
import static com.jheinz.tomyen.starforge.InfusionKind.DRILL;
import static com.jheinz.tomyen.starforge.InfusionKind.GUN;
import static com.jheinz.tomyen.starforge.InfusionKind.MELEE;
import static com.jheinz.tomyen.starforge.InfusionKind.ROD;
import static com.jheinz.tomyen.starforge.InfusionKind.TOOL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ARGB;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.alien.Aliens;
import com.jheinz.tomyen.dragonscale.Dragonscales;
import com.jheinz.tomyen.fae.Fae;
import com.jheinz.tomyen.froststeel.Froststeel;
import com.jheinz.tomyen.magmasteel.Magmasteel;
import com.jheinz.tomyen.meteorite.Meteorites;
import com.jheinz.tomyen.silver.Silver;
import com.jheinz.tomyen.soul.Souls;
import com.jheinz.tomyen.uranium.Uranium;
import com.jheinz.tomyen.voidsteel.Voidsteel;

/**
 * An infusion the Star Forge puts into a piece of gear, stored on it as {@link StarForge#INFUSION}. One to an item:
 * forging another replaces it. Each comes from one catalyst (a rare material from one of the mod's threads) and does
 * something on each {@link InfusionKind} it takes (see {@link InfusionEffects} for the numbers).
 */
public enum Infusion implements StringRepresentable {
	STARLIT("starlit", 0xB4C6FF, () -> List.of(Meteorites.STAR_CRYSTAL_BLOCK.asItem()), MELEE, GUN, TOOL, DRILL, ARMOR, ROD),
	FEATHERWEIGHT("featherweight", 0xE6F59A, () -> List.of(Aliens.ALIEN_ALLOY), MELEE, GUN, TOOL, DRILL, ARMOR, ROD),
	VOIDTOUCHED("voidtouched", 0xB26BFF, () -> List.of(Voidsteel.VOID_FLUX), MELEE, GUN, TOOL, DRILL, ARMOR, ROD),
	SOULBOUND("soulbound", 0x3ED8C3, () -> List.of(Souls.SOUL_CHARGE), MELEE, GUN, TOOL, DRILL, ARMOR, ROD),
	FAE_KISSED("fae_kissed", 0xFF8FD8, () -> List.of(Fae.FAE_DUST), MELEE, GUN, TOOL, DRILL, ARMOR, ROD),
	RIMED("rimed", 0xBDF0FF, () -> List.of(Froststeel.SET.ingot), MELEE, GUN, ARMOR),
	MOLTEN("molten", 0xFF8C3A, () -> List.of(Magmasteel.SET.ingot), MELEE, GUN, TOOL, DRILL, ARMOR),
	CONSECRATED("consecrated", 0xFFD45E, () -> List.of(Silver.HOLY_SILVER_INGOT), MELEE, GUN, ARMOR),
	DRAKEHIDE("drakehide", 0xE0484E, () -> List.of(Dragonscales.ANCIENT_DRAGON_SCALE, Dragonscales.ENDER_DRAGON_SCALE), ARMOR),
	IRRADIATED("irradiated", 0x8CFF4A, () -> List.of(Uranium.ENRICHED_URANIUM), MELEE, GUN);

	public static final Codec<Infusion> CODEC = StringRepresentable.fromEnum(Infusion::values);
	public static final StreamCodec<ByteBuf, Infusion> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Infusion::ordinal);

	private final String id;
	private final int color;
	private final Supplier<List<Item>> catalysts;
	private final Set<InfusionKind> takes;

	Infusion(String id, int color, Supplier<List<Item>> catalysts, InfusionKind... takes) {
		this.id = id;
		this.color = color;
		this.catalysts = catalysts;
		this.takes = Collections.unmodifiableSet(EnumSet.of(takes[0], takes));
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public String id() {
		return id;
	}

	/** The infusion's colour: its tooltip line, the infused item's name and its motes. */
	public int color() {
		return color;
	}

	public List<Item> catalysts() {
		return catalysts.get();
	}

	/** The sorts of gear it can go into. */
	public Set<InfusionKind> takes() {
		return takes;
	}

	/** Whether it can go into gear of these sorts (any one of them is enough). */
	public boolean fits(Set<InfusionKind> kinds) {
		return kinds.stream().anyMatch(takes::contains);
	}

	/** What it does on gear of these sorts, one line each (a tool's line once, for an axe's two sorts). */
	public List<Component> effects(Set<InfusionKind> kinds) {
		List<Component> lines = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		for (InfusionKind kind : kinds) {
			if (takes.contains(kind) && !keys.contains(kind.key)) {
				keys.add(kind.key);
				lines.add(Component.translatable("infusion.tomyen." + id + "." + kind.key));
			}
		}
		return lines;
	}

	/** Its colour darkened to read on a light grey screen background (the light ones vanish on it as they are). */
	public int screenColor() {
		return ARGB.scaleRGB(color, 0.55f);
	}

	/** Its name: "Starlit". */
	public MutableComponent displayName() {
		return Component.translatable("infusion.tomyen." + id);
	}

	/** Its name in its colour, starred: "✦ Starlit". */
	public MutableComponent title() {
		return Component.translatable("infusion.tomyen.title", displayName()).withColor(color);
	}

	/** The tooltip lines for gear of these sorts carrying it. */
	public List<Component> tooltip(Set<InfusionKind> kinds) {
		List<Component> lines = new ArrayList<>();
		lines.add(title());
		for (Component effect : effects(kinds)) {
			lines.add(Component.literal(" ").append(effect).withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	/** The infusion this item makes, or null if it isn't a catalyst. */
	public static @Nullable Infusion byCatalyst(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		for (Infusion infusion : values()) {
			for (Item catalyst : infusion.catalysts()) {
				if (stack.is(catalyst)) {
					return infusion;
				}
			}
		}
		return null;
	}

	/** The infusion on this item, if any. */
	public static @Nullable Infusion of(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(StarForge.INFUSION);
	}
}
