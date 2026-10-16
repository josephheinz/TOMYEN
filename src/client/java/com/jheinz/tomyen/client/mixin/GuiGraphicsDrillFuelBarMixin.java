package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.drill.DrillItem;

/**
 * A motor drill's fuel gauge on its slot: a bar like the durability bar, amber (red when low), always shown, just
 * above the durability bar when that's showing and in its place when it isn't.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsDrillFuelBarMixin {
	@Inject(method = "itemBar", at = @At("TAIL"))
	private void tomyen$drillFuelBar(ItemStack stack, int x, int y, CallbackInfo ci) {
		if (!(stack.getItem() instanceof DrillItem drill) || !drill.hasTank()) {
			return;
		}
		GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) (Object) this;
		int left = x + 2;
		int top = y + (stack.isBarVisible() ? 11 : 13);
		graphics.fill(RenderPipelines.GUI, left, top, left + 13, top + 2, 0xFF000000);
		graphics.fill(RenderPipelines.GUI, left, top, left + drill.fuelBarWidth(stack), top + 1, ARGB.opaque(drill.fuelBarColor(stack)));
	}
}
