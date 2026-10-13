package com.jheinz.mymod.client.tungsten;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.mymod.tungsten.TungstenDrillItem;

/**
 * Outlines everything a Tungsten Carbide Drill is about to dig: the block outline grows to take in the neighbours
 * {@link TungstenDrillItem#area} picks, merged into one shape, so a wall of stone shows a single 3x3 square.
 */
public final class DrillOutline {
	private DrillOutline() {
	}

	public static void initialize() {
		LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register(DrillOutline::extend);
	}

	private static void extend(LevelExtractionContext context, HitResult hit) {
		BlockOutlineRenderState outline = context.levelState().blockOutlineRenderState;
		LocalPlayer player = Minecraft.getInstance().player;
		if (outline == null || player == null || !TungstenDrillItem.digsArea(player) || !(hit instanceof BlockHitResult blockHit)
				|| !blockHit.getBlockPos().equals(outline.pos())) {
			return;
		}
		BlockPos centre = outline.pos();
		VoxelShape shape = outline.shape();
		CollisionContext collision = CollisionContext.of(player);
		for (BlockPos pos : TungstenDrillItem.area(context.level(), player.getMainHandItem(), centre, blockHit.getDirection())) {
			shape = Shapes.or(shape, context.level().getBlockState(pos).getShape(context.level(), pos, collision)
					.move(pos.getX() - centre.getX(), pos.getY() - centre.getY(), pos.getZ() - centre.getZ()));
		}
		context.levelState().blockOutlineRenderState = new BlockOutlineRenderState(centre, outline.isTranslucent(), outline.highContrast(), shape);
	}
}
