package dev.atlas.companion;

import net.minecraft.server.network.ServerPlayerEntity;

public final class AtlasShield {
    private AtlasShield() {}
    public static boolean protects(ServerPlayerEntity player) {
        if (!AtlasMod.CONFIG.protectOwner || !AtlasManager.allowed(player) || player.isSpectator()) return false;
        var binding = AtlasCompanionState.get(player.getServer()).binding(player.getUuid());
        int mode = binding == null ? -1 : binding.protectionMode();
        if (mode == 2) return false;
        if (mode == 1 || (mode < 0 && AtlasMod.CONFIG.permanentProtection && binding != null)) return true;
        return AtlasIdentity.belongsTo(player.getMainHandStack(), player) || AtlasIdentity.belongsTo(player.getOffHandStack(), player);
    }
    public static int mode(ServerPlayerEntity player) {
        var binding = AtlasCompanionState.get(player.getServer()).binding(player.getUuid());
        int mode = binding == null ? -1 : binding.protectionMode();
        return mode < 0 ? (AtlasMod.CONFIG.permanentProtection ? 1 : 0) : mode;
    }
    public static void setMode(ServerPlayerEntity player, int mode) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("protection mode");
        if (!AtlasManager.allowed(player)) return;
        var state = AtlasCompanionState.get(player.getServer());
        if (state.binding(player.getUuid()) == null) {
            var stack = AtlasIdentity.belongsTo(player.getMainHandStack(), player) ? player.getMainHandStack() : player.getOffHandStack();
            AtlasIdentity.bind(stack, player);
        }
        state.setProtectionMode(player.getUuid(), mode);
        AtlasManager.say(player, mode == 1 ? "Постоянная защита включена. Я прикрою тебя, даже когда предмет убран."
                : mode == 2 ? "Защита отключена. Если передумаешь — скажи." : "Защита включается, пока ты держишь меня в руке.");
    }
}
