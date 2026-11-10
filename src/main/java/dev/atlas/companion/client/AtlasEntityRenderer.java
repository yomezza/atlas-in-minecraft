package dev.atlas.companion.client;

import dev.atlas.companion.AtlasEntity;
import dev.atlas.companion.AtlasMod;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class AtlasEntityRenderer extends EntityRenderer<AtlasEntity> {
    public AtlasEntityRenderer(EntityRendererFactory.Context context) { super(context); shadowRadius = 0; }
    @Override public Identifier getTexture(AtlasEntity entity) { return AtlasMod.id("textures/entity/atlas.png"); }
    @Override public void render(AtlasEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.translate(0, 0.4, 0);
        matrices.scale(0.68f, 0.68f, 0.68f);
        float time = entity.age + tickDelta;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 2));
        AtlasMesh.render(matrices, consumers, light, time);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }
}
