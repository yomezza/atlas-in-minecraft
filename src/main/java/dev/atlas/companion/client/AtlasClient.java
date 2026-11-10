package dev.atlas.companion.client;

import dev.atlas.companion.AtlasMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.util.math.RotationAxis;

public final class AtlasClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(AtlasMod.ENTITY, AtlasEntityRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(AtlasMod.ATLAS,
                (stack, mode, matrices, consumers, light, overlay) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    float time = client.world == null ? 0 : client.world.getTime() + client.getTickDelta();
                    boolean held = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
                            || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                            || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                            || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
                    matrices.push();
                    matrices.translate(0.5, held ? 0.95 + Math.sin(time * 0.09) * 0.06 : 0.5, 0.5);
                    float scale = mode == ModelTransformationMode.GUI ? 0.63f : held ? 0.46f : 0.55f;
                    matrices.scale(scale, scale, scale);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 2.0f));
                    if (mode == ModelTransformationMode.GUI) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(18));
                    AtlasMesh.render(matrices, consumers, light, time);
                    matrices.pop();
                });
    }
}
