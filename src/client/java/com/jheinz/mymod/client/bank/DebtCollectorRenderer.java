package com.jheinz.mymod.client.bank;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.BabyVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerType;

import com.jheinz.mymod.bank.Banking;
import com.jheinz.mymod.bank.DebtCollector;

/**
 * Draws a debt collector as a master banker villager (suit, green eyeshade, diamond badge) with its gun
 * held in its crossed arms, using vanilla's villager model and layers.
 */
public class DebtCollectorRenderer extends MobRenderer<DebtCollector, VillagerRenderState, VillagerModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

	private VillagerData banker;

	public DebtCollectorRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
		addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager",
				new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT)),
				new BabyVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY_NO_HAT))));
		addLayer(new CrossedArmsItemLayer<>(this));
	}

	@Override
	public Identifier getTextureLocation(VillagerRenderState state) {
		return TEXTURE;
	}

	@Override
	public VillagerRenderState createRenderState() {
		return new VillagerRenderState();
	}

	@Override
	public void extractRenderState(DebtCollector collector, VillagerRenderState state, float partialTicks) {
		super.extractRenderState(collector, state, partialTicks);
		HoldingEntityRenderState.extractHoldingEntityRenderState(collector, state, itemModelResolver);
		if (banker == null) {
			banker = new VillagerData(BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS),
					BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(Banking.BANKER_KEY), 5);
		}
		state.villagerData = banker;
	}
}
