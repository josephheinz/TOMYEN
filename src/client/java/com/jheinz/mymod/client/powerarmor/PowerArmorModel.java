package com.jheinz.mymod.client.powerarmor;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.powerarmor.PowerArmorPiece;
import com.jheinz.mymod.powerarmor.PowerArmorTier;
import com.jheinz.mymod.powerarmor.SuitLook;

/**
 * A suit of T-51b power armor, built on the humanoid skeleton (same part names and pivots, so it can take a
 * player's pose part for part) but far bulkier than the body inside it: a rounded helmet with a slit visor, round
 * ear vents and a lamp, a huge rounded chest with a recessed unibeam emitter, the back hatch holding the fusion
 * core and exhaust stacks, round pauldrons, ribbed and segmented arms with a repulsor port on each forearm,
 * heavy knee caps and big boots with a jet exhaust under each sole while flying.
 *
 * <p>Every piece is built three times, once per mark, as children {@code mk1}, {@code mk2} and {@code mk3} of its
 * part; only the installed mark's is shown, so a mixed suit shows its mix. All are grey T-51b plate; they differ
 * in finish and detail: Mk I battered and rust-streaked with mismatched patch plates, Mk II clean factory grey
 * with hazard-stripe stencils, Mk III dark polished gunmetal with extra reinforcing plates.
 *
 * <p>The texture (tools/gen_power_armor.py) is 256x256: eleven 64x64 tileable materials (paint, trim and metal
 * for each mark, then rubber and soot) and six 32x32 small ones (visor glass, port bore, lamp, fusion core, jet
 * flame, emitter lens), of which only the lamp and flame glow. Every cube unwraps inside its material's tile.
 * Drawing is deferred, so each submission carries a {@link Pose} and the model is posed from it when it's
 * actually drawn; {@link Pose#only()} picks one piece so each can be tinted by its own condition.
 */
public class PowerArmorModel extends Model<PowerArmorModel.Pose> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("power_armor"), "main");

	// Materials: texture offsets. Per mark: paint, trim (patch plates, hazard stripes or reinforcing plate) and metal.
	private static final int[][] PAINT = {{0, 0}, {192, 0}, {128, 64}};
	private static final int[][] TRIM = {{64, 0}, {0, 64}, {192, 64}};
	private static final int[][] METAL = {{128, 0}, {64, 64}, {0, 128}};
	private static final int[] RUBBER = {64, 128};
	private static final int[] SOOT = {128, 128};
	private static final int[] VISOR = {192, 128};
	private static final int[] BORE = {224, 128};
	private static final int[] LAMP = {192, 160};
	private static final int[] CORE = {224, 160};
	private static final int[] FLAME = {32, 192};
	private static final int[] EMITTER = {0, 224};

	private static final String[] TIERS = {"mk1", "mk2", "mk3"};

	private final ModelPart[] pieces = new ModelPart[PowerArmorPiece.COUNT];
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	/**
	 * Everything needed to pose the model for one submission.
	 *
	 * @param parts the wearer's part poses in {@link PowerArmorPiece} order, or null for the suit's rest pose
	 * @param only  the piece to draw, or null for all of them
	 * @param open  how far the suit stands open, 0 to 1: back hatch up, helmet raised, arms out
	 * @param flame thruster flame length, 0 (off) to about 1.5
	 * @param time  ticks, for the flames' flicker
	 */
	public record Pose(PartPose @Nullable [] parts, SuitLook look, @Nullable PowerArmorPiece only, float open, float flame, float time) {
	}

	public PowerArmorModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
		head = root.getChild("head");
		body = root.getChild("body");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
		pieces[PowerArmorPiece.HELMET.ordinal()] = head;
		pieces[PowerArmorPiece.TORSO.ordinal()] = body;
		pieces[PowerArmorPiece.LEFT_ARM.ordinal()] = leftArm;
		pieces[PowerArmorPiece.RIGHT_ARM.ordinal()] = rightArm;
		pieces[PowerArmorPiece.LEFT_LEG.ordinal()] = leftLeg;
		pieces[PowerArmorPiece.RIGHT_LEG.ordinal()] = rightLeg;
	}

	public ModelPart part(PowerArmorPiece piece) {
		return pieces[piece.ordinal()];
	}

	@Override
	public void setupAnim(Pose pose) {
		super.setupAnim(pose);
		SuitLook look = pose.look();
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			ModelPart part = part(piece);
			if (pose.parts() != null && pose.parts()[piece.ordinal()] != null) {
				part.loadPose(pose.parts()[piece.ordinal()]);
			}
			PowerArmorTier tier = look.tier(piece);
			part.visible = tier != null && (pose.only() == null || pose.only() == piece);
			for (int i = 0; i < TIERS.length; i++) {
				part.getChild(TIERS[i]).visible = tier != null && tier.ordinal() == i;
			}
		}

		// Standing open: the back hatch swings up, the helmet lifts and tips back, the arms hang out.
		float open = pose.open();
		for (String tier : TIERS) {
			ModelPart hatch = body.getChild(tier).getChild("back_hatch");
			hatch.xRot = open * 1.15f;
			hatch.getChild("core").visible = look.coreLit();
		}
		head.y -= open * 2.5f;
		head.z += open * 1.0f;
		head.xRot -= open * 0.35f;
		rightArm.zRot += open * 0.22f;
		leftArm.zRot -= open * 0.22f;

		for (ModelPart leg : new ModelPart[] {rightLeg, leftLeg}) {
			ModelPart flame = leg.getChild("flame");
			flame.visible = pose.flame() > 0.0f;
			float flicker = 0.85f + 0.15f * Mth.sin(pose.time() * 2.3f + (leg == leftLeg ? 1.7f : 0.0f));
			flame.yScale = pose.flame() * flicker;
			flame.xScale = flame.zScale = 0.75f + 0.2f * Math.min(1.0f, pose.flame());
		}
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0f, 2.0f, 0.0f));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0f, 2.0f, 0.0f));
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12.0f, 0.0f));
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12.0f, 0.0f));
		for (int tier = 0; tier < TIERS.length; tier++) {
			head.addOrReplaceChild(TIERS[tier], helmet(tier), PartPose.ZERO);
			PartDefinition torso = body.addOrReplaceChild(TIERS[tier], torso(tier), PartPose.ZERO);
			PartDefinition hatch = torso.addOrReplaceChild("back_hatch", hatch(tier), PartPose.offset(0.0f, -1.0f, 3.5f));
			hatch.addOrReplaceChild("core", box(CubeListBuilder.create(), CORE, false, -1.3f, 1.3f, 2.2f, 6.4f, 4.2f, 4.45f), PartPose.ZERO);
			rightArm.addOrReplaceChild(TIERS[tier], arm(tier, false), PartPose.ZERO);
			leftArm.addOrReplaceChild(TIERS[tier], arm(tier, true), PartPose.ZERO);
			rightLeg.addOrReplaceChild(TIERS[tier], leg(tier, false), PartPose.ZERO);
			leftLeg.addOrReplaceChild(TIERS[tier], leg(tier, true), PartPose.ZERO);
		}
		// Jet exhaust from the thruster grate under each sole.
		rightLeg.addOrReplaceChild("flame", box(CubeListBuilder.create(), FLAME, false, -1.4f, 1.4f, 0.0f, 7.0f, -1.4f, 1.4f),
				PartPose.offset(-0.8f, 12.15f, -1.0f));
		leftLeg.addOrReplaceChild("flame", box(CubeListBuilder.create(), FLAME, false, -1.4f, 1.4f, 0.0f, 7.0f, -1.4f, 1.4f),
				PartPose.offset(0.8f, 12.15f, -1.0f));
		return LayerDefinition.create(mesh, 256, 256);
	}

	// Coordinates are model pixels from each part's pivot: y runs down, the front is -z, and the right side of
	// the body is -x. Limbs are built as the right one and mirrored across x for the left. Each piece is the same
	// T-51b shell in every mark, plus the mark's own extras.

	/** Head pivot at the neck; the head runs y -8..0. */
	private static CubeListBuilder helmet(int tier) {
		CubeListBuilder b = CubeListBuilder.create();
		int[] paint = PAINT[tier], trim = TRIM[tier], metal = METAL[tier];
		// The rounded dome, a brow over the slit visor (dark glass set back behind its frame), the protruding
		// rebreather snout with its grille and side cans, the round ear vents, and the lamp on the right one.
		box(b, paint, false, -5.0f, 5.0f, -9.5f, 0.0f, -5.0f, 5.0f);
		box(b, paint, false, -4.2f, 4.2f, -10.3f, -9.5f, -4.2f, 4.2f);
		box(b, paint, false, -4.6f, 4.6f, -8.6f, -6.9f, -5.6f, -5.0f);
		box(b, metal, false, -4.4f, 4.4f, -6.9f, -6.4f, -5.6f, -5.0f);
		box(b, metal, false, -4.4f, 4.4f, -5.2f, -4.7f, -5.6f, -5.0f);
		box(b, metal, false, -4.4f, -4.0f, -6.4f, -5.2f, -5.6f, -5.0f);
		box(b, metal, true, -4.4f, -4.0f, -6.4f, -5.2f, -5.6f, -5.0f);
		box(b, VISOR, false, -4.0f, 4.0f, -6.4f, -5.2f, -5.3f, -5.0f);
		box(b, paint, false, -3.0f, 3.0f, -4.7f, -0.5f, -6.2f, -5.0f);
		box(b, metal, false, -2.2f, 2.2f, -3.8f, -1.2f, -6.5f, -6.2f);
		box(b, metal, false, -3.8f, -3.0f, -3.5f, -1.0f, -5.8f, -4.6f);
		box(b, metal, true, -3.8f, -3.0f, -3.5f, -1.0f, -5.8f, -4.6f);
		box(b, metal, false, -6.6f, -5.0f, -6.0f, -2.0f, -2.0f, 2.0f);
		box(b, metal, true, -6.6f, -5.0f, -6.0f, -2.0f, -2.0f, 2.0f);
		box(b, SOOT, false, -6.8f, -6.6f, -5.4f, -2.6f, -1.4f, 1.4f);
		box(b, SOOT, true, -6.8f, -6.6f, -5.4f, -2.6f, -1.4f, 1.4f);
		box(b, metal, false, -6.4f, -5.2f, -7.4f, -6.0f, -1.5f, 1.5f);
		box(b, LAMP, false, -6.3f, -5.3f, -7.2f, -6.2f, -1.8f, -1.5f);
		box(b, RUBBER, false, -4.5f, 4.5f, -0.5f, 1.5f, -4.5f, 4.5f);
		switch (tier) {
			case 0 -> {
				// Battered: mismatched plates bolted over damage on the side and crown.
				box(b, trim, false, 2.5f, 5.3f, -8.5f, -5.0f, 1.0f, 4.5f);
				box(b, trim, false, -2.5f, 1.5f, -10.5f, -10.3f, -3.0f, 1.0f);
			}
			case 1 -> {
				// Factory finish: a hazard-striped crest down the crown.
				box(b, trim, false, -0.7f, 0.7f, -10.6f, -10.3f, -4.5f, 4.0f);
			}
			default -> {
				// Reinforced: armour over the temples, a heavier brow and a crown plate.
				box(b, trim, false, -5.4f, -5.0f, -9.0f, -6.5f, -4.0f, 3.0f);
				box(b, trim, true, -5.4f, -5.0f, -9.0f, -6.5f, -4.0f, 3.0f);
				box(b, trim, false, -4.8f, 4.8f, -9.2f, -8.6f, -5.8f, -4.8f);
				box(b, trim, false, -3.0f, 3.0f, -10.6f, -10.3f, -3.0f, 3.0f);
			}
		}
		return b;
	}

	/** Body pivot at the neck; the body runs y 0..12, z -2..2. */
	private static CubeListBuilder torso(int tier) {
		CubeListBuilder b = CubeListBuilder.create();
		int[] paint = PAINT[tier], trim = TRIM[tier], metal = METAL[tier];
		// The huge rounded chest in layers, the recessed unibeam emitter (a frame round a dark lens set back in it),
		// the collar, the ribbed abdomen behind its plate, and the belt.
		box(b, paint, false, -5.5f, 5.5f, -1.0f, 7.5f, -3.5f, 3.5f);
		box(b, paint, false, -5.2f, 5.2f, -0.5f, 6.8f, -4.4f, -3.5f);
		box(b, paint, false, -4.2f, 4.2f, 0.0f, 6.0f, -5.0f, -4.4f);
		box(b, metal, false, -1.9f, 1.9f, 1.5f, 2.1f, -5.4f, -5.0f);
		box(b, metal, false, -1.9f, 1.9f, 4.3f, 4.9f, -5.4f, -5.0f);
		box(b, metal, false, -1.9f, -1.3f, 2.1f, 4.3f, -5.4f, -5.0f);
		box(b, metal, true, -1.9f, -1.3f, 2.1f, 4.3f, -5.4f, -5.0f);
		box(b, EMITTER, false, -1.3f, 1.3f, 2.1f, 4.3f, -5.1f, -5.0f);
		box(b, metal, false, -5.6f, 5.6f, -2.0f, -0.5f, -3.6f, 3.6f);
		box(b, RUBBER, false, -4.6f, 4.6f, 7.0f, 10.5f, -3.0f, 3.0f);
		box(b, paint, false, -3.5f, 3.5f, 7.5f, 9.5f, -3.4f, -3.0f);
		box(b, metal, false, -5.6f, 5.6f, 10.0f, 12.3f, -3.6f, 3.6f);
		box(b, paint, false, -1.5f, 1.5f, 10.2f, 12.1f, -3.9f, -3.6f);
		switch (tier) {
			case 0 -> {
				box(b, trim, false, 2.0f, 4.0f, 0.6f, 3.6f, -5.25f, -5.0f);
				box(b, trim, false, -4.9f, -3.4f, 7.2f, 9.6f, -3.3f, -3.0f);
			}
			case 1 -> box(b, trim, false, -5.3f, 5.3f, 6.8f, 7.5f, -4.5f, 3.7f);
			default -> {
				box(b, trim, false, -4.6f, 4.6f, 0.2f, 1.4f, -5.3f, -4.4f);
				box(b, trim, false, -5.9f, -5.5f, 0.0f, 6.5f, -3.0f, 3.0f);
				box(b, trim, true, -5.9f, -5.5f, 0.0f, 6.5f, -3.0f, 3.0f);
			}
		}
		return b;
	}

	/** The back hatch, hinged at the top of the back: the plate, the fusion core housing and the exhaust stacks. */
	private static CubeListBuilder hatch(int tier) {
		CubeListBuilder b = CubeListBuilder.create();
		int[] paint = PAINT[tier], trim = TRIM[tier], metal = METAL[tier];
		box(b, paint, false, -5.5f, 5.5f, 0.0f, 9.0f, 0.0f, 1.6f);
		box(b, metal, false, -2.4f, 2.4f, 1.0f, 7.6f, 1.6f, 4.2f);
		box(b, SOOT, false, -4.6f, -3.0f, -4.0f, 5.0f, 1.8f, 3.4f);
		box(b, SOOT, true, -4.6f, -3.0f, -4.0f, 5.0f, 1.8f, 3.4f);
		box(b, metal, false, -4.8f, -2.8f, -4.6f, -4.0f, 1.6f, 3.6f);
		box(b, metal, true, -4.8f, -2.8f, -4.6f, -4.0f, 1.6f, 3.6f);
		box(b, SOOT, false, -1.8f, 1.8f, 7.8f, 8.6f, 1.6f, 2.2f);
		switch (tier) {
			case 0 -> box(b, trim, false, 2.8f, 5.3f, 5.2f, 8.4f, 1.6f, 1.9f);
			case 1 -> box(b, trim, false, -5.6f, 5.6f, 0.0f, 0.9f, -0.1f, 1.8f);
			default -> {
				box(b, trim, false, -2.8f, 2.8f, 0.6f, 1.2f, 1.4f, 4.6f);
				box(b, trim, false, -2.8f, 2.8f, 7.4f, 8.0f, 1.4f, 4.6f);
			}
		}
		return b;
	}

	/** The right arm from its pivot at the shoulder; a bare arm runs x -3..1, y -2..10. */
	private static CubeListBuilder arm(int tier, boolean mirror) {
		CubeListBuilder b = CubeListBuilder.create();
		int[] paint = PAINT[tier], trim = TRIM[tier], metal = METAL[tier];
		// A big round pauldron with its rim, the ribbed upper arm, a round elbow, the gauntlet in two segments,
		// the repulsor port along the outside of the forearm (its bore points down the arm), and the fist.
		box(b, paint, mirror, -6.8f, 1.6f, -4.2f, 0.8f, -4.0f, 4.0f);
		box(b, paint, mirror, -6.2f, 1.0f, -5.0f, -4.2f, -3.3f, 3.3f);
		box(b, metal, mirror, -7.0f, 1.8f, 1.0f, 1.6f, -4.2f, 4.2f);
		box(b, RUBBER, mirror, -4.0f, 0.5f, 1.6f, 4.6f, -2.4f, 2.4f);
		box(b, metal, mirror, -4.2f, 0.7f, 2.4f, 2.9f, -2.6f, 2.6f);
		box(b, metal, mirror, -4.2f, 0.7f, 3.6f, 4.1f, -2.6f, 2.6f);
		box(b, metal, mirror, -4.5f, 1.0f, 4.4f, 6.2f, -2.8f, 2.8f);
		box(b, paint, mirror, -4.7f, 1.2f, 6.0f, 8.0f, -3.1f, 3.1f);
		box(b, RUBBER, mirror, -4.2f, 0.6f, 7.6f, 8.8f, -2.6f, 2.6f);
		box(b, paint, mirror, -4.6f, 1.1f, 8.3f, 10.4f, -3.0f, 3.0f);
		box(b, metal, mirror, -5.3f, -4.6f, 7.0f, 11.2f, -1.0f, 1.0f);
		box(b, BORE, mirror, -5.2f, -4.7f, 11.2f, 11.4f, -0.8f, 0.8f);
		box(b, metal, mirror, -4.0f, 0.6f, 10.4f, 12.8f, -2.4f, 2.4f);
		box(b, paint, mirror, -4.3f, -4.0f, 10.6f, 12.4f, -2.2f, 2.2f);
		switch (tier) {
			case 0 -> {
				box(b, trim, mirror, -7.1f, -6.8f, -3.5f, 0.2f, -2.5f, 2.0f);
				box(b, trim, mirror, -4.9f, 1.4f, 9.0f, 9.6f, -3.2f, 3.2f);
			}
			case 1 -> box(b, trim, mirror, -6.9f, 1.7f, 0.6f, 1.0f, -4.1f, 4.1f);
			default -> {
				box(b, trim, mirror, -7.2f, 0.6f, -3.6f, 0.4f, -4.4f, 4.4f);
				box(b, trim, mirror, -5.0f, -4.7f, 6.2f, 8.0f, -2.6f, 2.6f);
			}
		}
		return b;
	}

	/** The right leg from its pivot at the hip; a bare leg runs x -2..2, y 0..12. Kept short of x 1.8 inside. */
	private static CubeListBuilder leg(int tier, boolean mirror) {
		CubeListBuilder b = CubeListBuilder.create();
		int[] paint = PAINT[tier], trim = TRIM[tier], metal = METAL[tier];
		// The thigh with its hydraulic ram, the heavy round knee and cap, the ribbed shin, and the big boot with
		// its toe cap, heel nozzle and the thruster grate under the sole.
		box(b, paint, mirror, -3.3f, 1.8f, -0.5f, 5.2f, -3.2f, 3.0f);
		box(b, metal, mirror, -4.0f, -3.3f, 0.5f, 8.5f, -0.7f, 0.7f);
		box(b, metal, mirror, -3.2f, 1.7f, 4.8f, 7.4f, -3.3f, 2.6f);
		box(b, paint, mirror, -2.6f, 1.2f, 4.6f, 7.6f, -4.2f, -3.3f);
		box(b, paint, mirror, -2.0f, 0.6f, 5.0f, 7.2f, -4.6f, -4.2f);
		box(b, paint, mirror, -3.0f, 1.6f, 7.4f, 9.6f, -3.1f, 2.8f);
		box(b, metal, mirror, -3.2f, 1.75f, 8.2f, 8.7f, -3.3f, 3.0f);
		box(b, paint, mirror, -3.5f, 1.8f, 9.6f, 12.0f, -5.2f, 3.1f);
		box(b, metal, mirror, -3.2f, 1.5f, 10.6f, 12.0f, -5.6f, -5.2f);
		box(b, metal, mirror, -2.3f, 0.6f, 10.2f, 11.9f, 3.1f, 4.1f);
		box(b, SOOT, mirror, -2.6f, 1.0f, 12.0f, 12.15f, -4.0f, 2.0f);
		switch (tier) {
			case 0 -> {
				box(b, trim, mirror, -3.6f, -3.3f, 1.0f, 4.5f, -2.5f, 1.5f);
				box(b, trim, mirror, -3.1f, 1.7f, 9.2f, 9.6f, -3.3f, 2.9f);
			}
			case 1 -> box(b, trim, mirror, -3.6f, 1.75f, 9.6f, 10.2f, -5.3f, 3.2f);
			default -> {
				box(b, trim, mirror, -2.4f, 1.0f, 7.2f, 10.4f, -3.6f, -3.1f);
				box(b, trim, mirror, -2.8f, 1.2f, 0.2f, 4.2f, -3.6f, -3.2f);
			}
		}
		return b;
	}

	/** Adds a box spanning x0..x1, y0..y1, z0..z1 in a material; mirrored, it's flipped across x. */
	private static CubeListBuilder box(CubeListBuilder builder, int[] material, boolean mirror, float x0, float x1, float y0, float y1,
			float z0, float z1) {
		float left = mirror ? -x1 : x0;
		return builder.texOffs(material[0], material[1]).addBox(left, y0, z0, x1 - x0, y1 - y0, z1 - z0);
	}
}
