package com.jheinz.mymod;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * The "My Mod" creative tab: every item registered under the {@code mymod} namespace, in registration order.
 * Contents are built from the item registry each time the tab is populated, so newly added items show up
 * without being listed here. Items also keep their placements in the vanilla tabs.
 */
public final class ModCreativeTabs {
	public static final ResourceKey<CreativeModeTab> MY_MOD_TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, MyMod.id("my_mod"));

	public static final CreativeModeTab MY_MOD_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MY_MOD_TAB_KEY,
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.mymod.my_mod"))
					.icon(() -> new ItemStack(ModItems.NEURALINK))
					.displayItems((parameters, output) -> BuiltInRegistries.ITEM.forEach(item -> {
						if (MyMod.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
							output.accept(item);
						}
					}))
					.build());

	private ModCreativeTabs() {
	}

	public static void initialize() {
		// Registration happens in the static initializer; this just forces the class to load.
	}
}
