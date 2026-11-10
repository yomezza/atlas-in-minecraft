package dev.atlas.companion.mixin;

import dev.atlas.companion.AtlasManager;
import dev.atlas.companion.AtlasMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void atlas$protectFromKill(CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayerEntity player && AtlasMod.CONFIG != null
                && AtlasMod.CONFIG.protectOwner && AtlasManager.active(player)) {
            AtlasManager.say(player, "Нет. Убивать моего человека запрещено даже командам.");
            ci.cancel();
        }
    }
}
