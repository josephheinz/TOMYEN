package com.jheinz.tomyen.client.cart;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import com.jheinz.tomyen.MyMod;

/**
 * A wire shopping cart on four casters, handle at the back. The lower half of the front panel is hinged
 * at the top as a jaw, with a row of teeth behind it; the eyes are on the panel above.
 */
public class ShoppingCartModel extends EntityModel<ShoppingCartRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MyMod.id("shopping_cart"), "main");
	private static final String[] WHEELS = {"front_left_wheel", "front_right_wheel", "back_left_wheel", "back_right_wheel"};

	private final ModelPart cart;
	private final ModelPart jaw;
	private final ModelPart[] wheels = new ModelPart[WHEELS.length];

	public ShoppingCartModel(ModelPart root) {
		super(root);
		cart = root.getChild("cart");
		jaw = cart.getChild("jaw");
		for (int i = 0; i < WHEELS.length; i++) {
			wheels[i] = cart.getChild(WHEELS[i]);
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// Everything hangs off a part pivoting on the ground under the middle, so the whole cart can rock and rear.
		PartDefinition cart = root.addOrReplaceChild("cart", CubeListBuilder.create()
				// Basket: sides, back gate, upper front panel (with the eyes) and floor.
				.texOffs(0, 0).addBox(-7.0f, -20.0f, -9.0f, 1.0f, 10.0f, 17.0f)
				.texOffs(0, 0).mirror().addBox(6.0f, -20.0f, -9.0f, 1.0f, 10.0f, 17.0f).mirror(false)
				.texOffs(36, 0).addBox(-6.0f, -20.0f, 7.0f, 12.0f, 10.0f, 1.0f)
				.texOffs(36, 11).addBox(-6.0f, -20.0f, -9.0f, 12.0f, 5.0f, 1.0f)
				.texOffs(64, 0).addBox(-6.0f, -11.0f, -8.0f, 12.0f, 1.0f, 15.0f)
				// Chassis: rear posts (the basket overhangs the front), rails and the bottom rack.
				.texOffs(40, 31).addBox(-5.0f, -10.0f, 6.0f, 1.0f, 6.0f, 1.0f)
				.texOffs(40, 31).addBox(4.0f, -10.0f, 6.0f, 1.0f, 6.0f, 1.0f)
				.texOffs(0, 27).addBox(-5.0f, -4.0f, -8.0f, 1.0f, 1.0f, 15.0f)
				.texOffs(0, 27).addBox(4.0f, -4.0f, -8.0f, 1.0f, 1.0f, 15.0f)
				.texOffs(64, 16).addBox(-4.0f, -6.0f, -7.0f, 8.0f, 1.0f, 13.0f)
				// Handle.
				.texOffs(32, 31).addBox(-7.0f, -21.0f, 8.0f, 1.0f, 1.0f, 3.0f)
				.texOffs(32, 31).addBox(6.0f, -21.0f, 8.0f, 1.0f, 1.0f, 3.0f)
				.texOffs(32, 27).addBox(-8.0f, -22.0f, 10.0f, 16.0f, 2.0f, 2.0f)
				// Upper teeth, hanging just behind the jaw.
				.texOffs(52, 31).addBox(-5.0f, -15.0f, -8.0f, 1.0f, 1.0f, 1.0f)
				.texOffs(52, 31).addBox(-3.0f, -15.0f, -8.0f, 1.0f, 1.0f, 1.0f)
				.texOffs(52, 31).addBox(-1.0f, -15.0f, -8.0f, 1.0f, 1.0f, 1.0f)
				.texOffs(52, 31).addBox(1.0f, -15.0f, -8.0f, 1.0f, 1.0f, 1.0f)
				.texOffs(52, 31).addBox(3.0f, -15.0f, -8.0f, 1.0f, 1.0f, 1.0f),
				PartPose.offset(0.0f, 24.0f, 0.0f));
		cart.addOrReplaceChild("jaw", CubeListBuilder.create()
				.texOffs(36, 17).addBox(-6.0f, 0.0f, 0.0f, 12.0f, 5.0f, 1.0f)
				.texOffs(52, 31).addBox(-4.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
				.texOffs(52, 31).addBox(-2.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
				.texOffs(52, 31).addBox(0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
				.texOffs(52, 31).addBox(2.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f),
				PartPose.offset(0.0f, -15.0f, -9.0f));
		CubeListBuilder wheel = CubeListBuilder.create().texOffs(44, 31).addBox(-0.5f, -1.5f, -1.5f, 1.0f, 3.0f, 3.0f);
		cart.addOrReplaceChild("front_left_wheel", wheel, PartPose.offset(4.5f, -1.5f, -7.0f));
		cart.addOrReplaceChild("front_right_wheel", wheel, PartPose.offset(-4.5f, -1.5f, -7.0f));
		cart.addOrReplaceChild("back_left_wheel", wheel, PartPose.offset(4.5f, -1.5f, 6.0f));
		cart.addOrReplaceChild("back_right_wheel", wheel, PartPose.offset(-4.5f, -1.5f, 6.0f));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(ShoppingCartRenderState state) {
		super.setupAnim(state);
		float speed = state.walkAnimationSpeed;
		float age = state.ageInTicks;

		// Wheels roll with the ground; the front casters wobble side to side.
		float roll = state.walkAnimationPos * 1.6f;
		for (ModelPart wheel : wheels) {
			wheel.xRot = roll;
		}
		float wobble = Mth.sin(age * 1.3f) * 0.35f * speed;
		wheels[0].yRot = wobble;
		wheels[1].yRot = wobble;

		// Rattles and bounces over the bumps as it rolls.
		cart.zRot = Mth.sin(age * 2.1f) * 0.035f * speed;
		cart.y -= Math.abs(Mth.sin(age * 1.7f)) * 0.4f * speed;

		// Rears up mid-leap, then lunges forward on the bite.
		float bite = Mth.sin(state.swing * Mth.PI);
		cart.xRot = -0.45f * state.pounce + 0.12f * bite;
		cart.z -= 2.0f * bite;

		// The jaw hangs open and pants while it hunts, gapes on the leap and snaps on the bite.
		float open = state.aggressive ? 0.12f + 0.08f * Mth.sin(age * 0.9f) : 0.0f;
		open = Math.max(open, 0.9f * state.pounce);
		open = Math.max(open, 1.1f * bite);
		jaw.xRot = -open;
	}
}
