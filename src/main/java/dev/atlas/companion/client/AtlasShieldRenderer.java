package dev.atlas.companion.client;

import dev.atlas.companion.AtlasShieldAccess;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class AtlasShieldRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/creeper/creeper_armor.png");
    public AtlasShieldRenderer(PlayerEntityRenderer renderer) { super(renderer); }
    private static RenderLayer layer(float age) { return RenderLayer.getEnergySwirl(TEXTURE, age * 0.008f, age * 0.005f); }
    @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float animation, float headYaw, float headPitch) {
        if (!((AtlasShieldAccess)player).atlas$shieldActive()) return;
        matrices.push(); matrices.scale(1.035f, 1.025f, 1.035f);
        getContextModel().render(matrices, vertices.getBuffer(layer(player.age + tickDelta)), LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV, 1.0f, 0.28f, 0.12f, 0.7f);
        matrices.pop();
    }
    public static void arm(MatrixStack matrices, VertexConsumerProvider vertices, AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve) {
        if (!((AtlasShieldAccess)player).atlas$shieldActive()) return;
        matrices.push(); matrices.scale(1.035f, 1.025f, 1.035f);
        var out = vertices.getBuffer(layer(player.age));
        arm.render(matrices, out, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 1, 0.28f, 0.12f, 0.7f);
        sleeve.render(matrices, out, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 1, 0.28f, 0.12f, 0.7f);
        matrices.pop();
    }
}
