package dev.atlas.companion;

import com.google.gson.Gson;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Individual companion controls are separate from server-wide Vibe Coding settings. */
public final class AtlasControlNetworking {
    public static final Identifier REQUEST = AtlasMod.id("wheel_request"), ACTION = AtlasMod.id("wheel_action"), STATE = AtlasMod.id("wheel_state");
    private static final Gson GSON = new Gson();
    public record Snapshot(boolean available, boolean deployed, boolean silk, int flight, float currentScale, float targetScale,
                           float maxScale, int pendingEnemies, String token, boolean attacking, int protectionMode, boolean shield, String message) {}
    private AtlasControlNetworking() {}
    private static ItemStack held(ServerPlayerEntity player) {
        if (AtlasIdentity.belongsTo(player.getMainHandStack(), player)) return player.getMainHandStack();
        if (AtlasIdentity.belongsTo(player.getOffHandStack(), player)) return player.getOffHandStack();
        return ItemStack.EMPTY;
    }
    public static Snapshot snapshot(ServerPlayerEntity player, String message) {
        AtlasEntity atlas = AtlasManager.find(player); ItemStack stack = held(player);
        boolean available = AtlasManager.allowed(player) && (atlas != null || !stack.isEmpty() || AtlasShield.protects(player));
        float scale = atlas != null ? atlas.targetScale() : stack.hasNbt() && stack.getNbt().contains(AtlasScale.ITEM_KEY) ? stack.getNbt().getFloat(AtlasScale.ITEM_KEY) : 1;
        var offer = AtlasCombat.pending(player);
        return new Snapshot(available, atlas != null, atlas != null ? atlas.silkMode() : AtlasTool.silk(stack),
                atlas != null ? atlas.flightMode() : stack.hasNbt() && stack.getNbt().contains("AtlasFlightMode") ? stack.getNbt().getByte("AtlasFlightMode") : 0,
                atlas != null ? atlas.currentScale() : scale, scale, AtlasMod.CONFIG.maxCompanionScale,
                offer == null ? 0 : offer.targets().size(), offer == null ? "" : offer.token().toString(), atlas != null && atlas.attacking(),
                AtlasShield.mode(player), AtlasShield.protects(player), available ? message : "wheel.unavailable");
    }
    public static void action(ServerPlayerEntity player, String action, double value, String token) {
        if (!AtlasManager.allowed(player)) return;
        AtlasEntity atlas = AtlasManager.find(player); ItemStack stack = held(player);
        if (atlas == null && stack.isEmpty() && !AtlasShield.protects(player)) return;
        switch(action) {
            case "scale" -> {
                float scale = AtlasScale.validate(value, AtlasMod.CONFIG.maxCompanionScale);
                if (atlas != null) atlas.setTargetScale(scale);
                else if (!stack.isEmpty()) { AtlasIdentity.bind(stack, player); stack.getOrCreateNbt().putFloat(AtlasScale.ITEM_KEY, scale); }
                else throw new IllegalArgumentException("wheel.unavailable");
            }
            case "fortune", "silk" -> {
                if (atlas != null) atlas.setSilkMode(action.equals("silk"));
                else { AtlasIdentity.bind(stack, player); AtlasTool.setSilk(stack, action.equals("silk")); }
            }
            case "free", "follow", "stay" -> {
                int mode = action.equals("free") ? 0 : action.equals("follow") ? 1 : 2;
                if (atlas != null) atlas.setFlightMode(mode);
                else { AtlasIdentity.bind(stack, player); stack.getOrCreateNbt().putByte("AtlasFlightMode", (byte)mode); }
            }
            case "recall" -> { if (atlas != null) AtlasManager.pickUp(player, atlas); }
            case "attack_offer" -> AtlasCombat.offer(player);
            case "attack_confirm" -> AtlasCombat.confirm(player, token);
            case "attack_cancel" -> AtlasCombat.cancel(player);
            case "shield_held", "shield_permanent", "shield_off" -> AtlasShield.setMode(player, action.equals("shield_permanent") ? 1 : action.equals("shield_off") ? 2 : 0);
            default -> throw new IllegalArgumentException("Unknown companion control");
        }
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buffer, sender) -> {
            UUID request = buffer.readUuid(); server.execute(() -> send(player, request, "wheel.ready"));
        });
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buffer, sender) -> {
            UUID request = buffer.readUuid(); String action = buffer.readString(32); double value = buffer.readDouble(); String token = buffer.readString(64);
            server.execute(() -> {
                String message = "wheel.applied";
                try { action(player, action, value, token); } catch (IllegalArgumentException invalid) { message = invalid.getMessage(); }
                send(player, request, message);
            });
        });
    }
    private static void send(ServerPlayerEntity player, UUID request, String message) {
        if (!ServerPlayNetworking.canSend(player, STATE)) return;
        PacketByteBuf buffer = PacketByteBufs.create(); buffer.writeUuid(request); buffer.writeString(GSON.toJson(snapshot(player, message)), 4096);
        ServerPlayNetworking.send(player, STATE, buffer);
    }
}
