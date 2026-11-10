package dev.atlas.companion.mixin;

import dev.atlas.companion.client.AtlasShieldRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEnergyMixin {
    @Inject(method = "renderArm", at = @At("TAIL"))
    private void atlas$armShield(MatrixStack matrices, VertexConsumerProvider vertices, int light,
            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        AtlasShieldRenderer.arm(matrices, vertices, player, arm, sleeve);
    }
}
