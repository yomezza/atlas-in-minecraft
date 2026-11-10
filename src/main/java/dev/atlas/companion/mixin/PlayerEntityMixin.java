package dev.atlas.companion.mixin;

import dev.atlas.companion.AtlasShieldAccess;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements AtlasShieldAccess {
    @Unique private static final TrackedData<Boolean> ATLAS_SHIELD = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void atlas$initShield(CallbackInfo ci) { ((PlayerEntity)(Object)this).getDataTracker().startTracking(ATLAS_SHIELD, false); }
    @Override public boolean atlas$shieldActive() { return ((PlayerEntity)(Object)this).getDataTracker().get(ATLAS_SHIELD); }
    @Override public void atlas$setShieldActive(boolean active) { ((PlayerEntity)(Object)this).getDataTracker().set(ATLAS_SHIELD, active); }
}
