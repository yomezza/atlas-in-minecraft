package dev.atlas.companion.mixin;

import dev.atlas.companion.client.AtlasWheelClient;
import dev.atlas.companion.client.AtlasWheelScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void atlas$middleButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || action != GLFW.GLFW_PRESS) return;
        if (client.currentScreen instanceof AtlasWheelScreen) { client.setScreen(null); ci.cancel(); }
        else if (client.currentScreen == null && AtlasWheelClient.canOpen(client)) { client.setScreen(new AtlasWheelScreen()); ci.cancel(); }
    }
}
