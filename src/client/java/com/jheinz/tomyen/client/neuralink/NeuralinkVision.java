package com.jheinz.tomyen.client.neuralink;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.meteorite.StarlightLensItem;
import com.jheinz.tomyen.neuralink.NeuralNetworking;
import com.jheinz.tomyen.neuralink.NeuralinkItem;
import com.jheinz.tomyen.neuralink.NeuralinkModule;

/**
 * Draws what the wearer's modules see, through walls: ESP boxes around mobs and players, and x-ray
 * highlights on ores. A Starlight Lens looked through at night shows both. With Hive Mind too, ESP also boxes what teammates' implants see beyond the wearer's own
 * range, edged in the hive's amber.
 */
public final class NeuralinkVision {
	private static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(MyMod.id("pipeline/neuralink_lines"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
	private static final RenderPipeline FILL_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(MyMod.id("pipeline/neuralink_fill"))
			.withCull(false)
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
	private static final RenderType LINES = RenderType.create("tomyen_neuralink_lines", RenderSetup.builder(LINES_PIPELINE).createRenderSetup());
	private static final RenderType FILL = RenderType.create("tomyen_neuralink_fill", RenderSetup.builder(FILL_PIPELINE).createRenderSetup());

	private static final int HOSTILE = 0xFF3B4F;
	private static final int PASSIVE = 0x5CFF7A;
	private static final int PLAYER = 0x3EF2FF;
	/** How far along each edge the bright corner brackets of an ESP box run. */
	private static final double BRACKET = 0.28;
	/** Drawing more ore than this would cost frames in ore-rich areas. */
	private static final int MAX_ORES = 6000;

	private static final int HIVE = NeuralinkModule.HIVE_MIND.color();

	/** @param shared seen by a Hive Mind teammate rather than the wearer */
	private record Target(AABB box, int color, boolean shared) {
	}

	private NeuralinkVision() {
	}

	public static void submit(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		Set<NeuralinkModule> active = NeuralinkItem.activeModules(player);
		// A Starlight Lens looked through at night sees what both modules do.
		boolean lens = StarlightLensItem.isRevealing(player);
		boolean esp = lens || active.contains(NeuralinkModule.ESP);
		boolean xray = lens || active.contains(NeuralinkModule.XRAY);
		if (!esp && !xray) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float width = minecraft.getWindow().getAppropriateLineWidth();
		Vec3 eye = player.getEyePosition(partialTick);

		List<Target> targets = new ArrayList<>();
		if (esp) {
			double range = NeuralinkModule.ESP_RANGE * NeuralinkModule.ESP_RANGE;
			for (Entity entity : minecraft.level.entitiesForRendering()) {
				if (entity == player || !entity.isAlive() || !(entity instanceof Mob || entity instanceof Player)) {
					continue;
				}
				Vec3 at = entity.getPosition(partialTick);
				if (at.distanceToSqr(eye) > range) {
					continue;
				}
				int color = entity instanceof Player ? PLAYER : entity instanceof Enemy ? HOSTILE : PASSIVE;
				AABB box = entity.getBoundingBox().move(at.subtract(entity.position())).inflate(0.06).move(camera.reverse());
				targets.add(new Target(box, color, false));
			}
			for (NeuralNetworking.Contact contact : NeuralClient.contacts) {
				// Anything this close our own ESP is already boxing.
				if (contact.pos().distanceToSqr(eye) <= range) {
					continue;
				}
				double half = contact.width() / 2.0;
				Vec3 at = contact.pos();
				AABB box = new AABB(at.x - half, at.y, at.z - half, at.x + half, at.y + contact.height(), at.z + half).inflate(0.06)
						.move(camera.reverse());
				int color = switch (contact.kind()) {
					case NeuralNetworking.Contact.PLAYER -> PLAYER;
					case NeuralNetworking.Contact.HOSTILE -> HOSTILE;
					default -> PASSIVE;
				};
				targets.add(new Target(box, color, true));
			}
		}
		List<OreScanner.Ore> ores = new ArrayList<>();
		if (xray) {
			double range = NeuralinkModule.XRAY_RANGE * NeuralinkModule.XRAY_RANGE;
			for (OreScanner.Ore ore : OreScanner.ores()) {
				if (ore.pos().distToCenterSqr(eye) <= range) {
					ores.add(ore);
					if (ores.size() >= MAX_ORES) {
						break;
					}
				}
			}
		}
		if (targets.isEmpty() && ores.isEmpty()) {
			return;
		}
		float pulse = 0.75f + 0.25f * Mth.sin(Util.getMillis() / 250.0f);

		PoseStack poseStack = context.poseStack();
		if (!ores.isEmpty()) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, FILL, (pose, buffer) -> {
				for (OreScanner.Ore ore : ores) {
					AABB box = new AABB(ore.pos()).move(camera.reverse());
					faces(pose, buffer, box, ore.openFaces(), argb(0.16f, ore.color()));
				}
			});
		}
		context.submitNodeCollector().submitCustomGeometry(poseStack, LINES, (pose, buffer) -> {
			for (OreScanner.Ore ore : ores) {
				edges(pose, buffer, new AABB(ore.pos()).deflate(0.002).move(camera.reverse()), argb(0.8f, ore.color()), width);
			}
			for (Target target : targets) {
				edges(pose, buffer, target.box(), argb(target.shared() ? 0.45f : 0.3f, target.shared() ? HIVE : target.color()), width);
				brackets(pose, buffer, target.box(), argb(pulse, target.color()), width * 1.6f);
			}
		});
	}

	private static int argb(float alpha, int rgb) {
		return (Mth.clamp((int) (alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void edges(PoseStack.Pose pose, VertexConsumer buffer, AABB b, int color, float width) {
		double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
		for (double y : ys) {
			for (double z : zs) {
				line(pose, buffer, b.minX, y, z, b.maxX, y, z, color, width);
			}
			for (double x : xs) {
				line(pose, buffer, x, y, b.minZ, x, y, b.maxZ, color, width);
			}
		}
		for (double x : xs) {
			for (double z : zs) {
				line(pose, buffer, x, b.minY, z, x, b.maxY, z, color, width);
			}
		}
	}

	/** Short bright lines from each corner along its three edges. */
	private static void brackets(PoseStack.Pose pose, VertexConsumer buffer, AABB b, int color, float width) {
		double lx = b.getXsize() * BRACKET, ly = b.getYsize() * BRACKET, lz = b.getZsize() * BRACKET;
		for (int corner = 0; corner < 8; corner++) {
			boolean east = (corner & 1) != 0, top = (corner & 2) != 0, south = (corner & 4) != 0;
			double x = east ? b.maxX : b.minX, y = top ? b.maxY : b.minY, z = south ? b.maxZ : b.minZ;
			line(pose, buffer, x, y, z, x + (east ? -lx : lx), y, z, color, width);
			line(pose, buffer, x, y, z, x, y + (top ? -ly : ly), z, color, width);
			line(pose, buffer, x, y, z, x, y, z + (south ? -lz : lz), color, width);
		}
	}

	private static void line(PoseStack.Pose pose, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2,
			double z2, int color, float width) {
		float nx = (float) (x2 - x1), ny = (float) (y2 - y1), nz = (float) (z2 - z1);
		float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (length < 1.0e-4f) {
			return;
		}
		nx /= length;
		ny /= length;
		nz /= length;
		buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
		buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
	}

	/** The box's faces picked by a bit per {@link Direction}, as quads. */
	private static void faces(PoseStack.Pose pose, VertexConsumer buffer, AABB b, int faces, int color) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		for (Direction direction : Direction.values()) {
			if ((faces & (1 << direction.ordinal())) == 0) {
				continue;
			}
			switch (direction) {
				case DOWN -> quad(pose, buffer, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
				case UP -> quad(pose, buffer, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
				case NORTH -> quad(pose, buffer, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
				case SOUTH -> quad(pose, buffer, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
				case WEST -> quad(pose, buffer, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
				case EAST -> quad(pose, buffer, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
			}
		}
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color, float ax, float ay, float az, float bx, float by,
			float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		buffer.addVertex(pose, ax, ay, az).setColor(color);
		buffer.addVertex(pose, bx, by, bz).setColor(color);
		buffer.addVertex(pose, cx, cy, cz).setColor(color);
		buffer.addVertex(pose, dx, dy, dz).setColor(color);
	}
}
