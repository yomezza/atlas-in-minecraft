package dev.atlas.companion.client;

import com.google.gson.Gson;
import dev.atlas.companion.AtlasControlNetworking;
import dev.atlas.companion.AtlasEntity;
import dev.atlas.companion.AtlasIdentity;
import dev.atlas.companion.AtlasMod;
import dev.atlas.companion.AtlasShieldAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public final class AtlasWheelClient {
    private static final Gson GSON = new Gson();
    private AtlasWheelClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AtlasControlNetworking.STATE, (client, handler, buffer, sender) -> {
            var id = buffer.readUuid(); String json = buffer.readString(4096);
            client.execute(() -> {
                if (client.currentScreen instanceof AtlasWheelScreen wheel && wheel.requestId().equals(id))
                    wheel.receive(GSON.fromJson(json, AtlasControlNetworking.Snapshot.class));
            });
        });
    }
    public static boolean canOpen(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;
        for (ItemStack stack : new ItemStack[]{client.player.getMainHandStack(), client.player.getOffHandStack()})
            if (stack.isOf(AtlasMod.ATLAS) && (AtlasIdentity.owner(stack) == null || client.player.getUuid().equals(AtlasIdentity.owner(stack)))) return true;
        if (((AtlasShieldAccess)client.player).atlas$shieldActive()) return true;
        for (var entity : client.world.getEntities())
            if (entity instanceof AtlasEntity atlas && client.player.getUuid().equals(atlas.ownerId())) return true;
        return false;
    }
    public static void request(AtlasWheelScreen wheel) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null || !ClientPlayNetworking.canSend(AtlasControlNetworking.REQUEST)) { wheel.fail("wheel.unsupported"); return; }
        var buffer = PacketByteBufs.create(); buffer.writeUuid(wheel.requestId());
        ClientPlayNetworking.send(AtlasControlNetworking.REQUEST, buffer);
    }
    public static void send(AtlasWheelScreen wheel, String action, double value) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null || !ClientPlayNetworking.canSend(AtlasControlNetworking.ACTION)) { wheel.fail("wheel.unsupported"); return; }
        var buffer = PacketByteBufs.create(); buffer.writeUuid(wheel.requestId()); buffer.writeString(action, 32);
        buffer.writeDouble(value); buffer.writeString(wheel.token(), 64);
        ClientPlayNetworking.send(AtlasControlNetworking.ACTION, buffer);
    }
}
