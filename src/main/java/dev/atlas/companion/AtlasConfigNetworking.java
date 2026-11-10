package dev.atlas.companion;

import java.io.IOException;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class AtlasConfigNetworking {
    public static final Identifier REQUEST = AtlasMod.id("config_request");
    public static final Identifier SAVE = AtlasMod.id("config_save");
    public static final Identifier RESPONSE = AtlasMod.id("config_response");
    public static final int MAX_JSON_LENGTH = AtlasConfig.MAX_SERIALIZED_LENGTH;
    private static long revision;
    public record Result(boolean saved, String messageKey) {}
    private AtlasConfigNetworking() {}
    public static boolean canEdit(ServerPlayerEntity player) {
        return player.getServer().isHost(player.getGameProfile()) || player.hasPermissionLevel(2);
    }
    public static long revision() { return revision; }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buffer, sender) -> {
            UUID request = buffer.readUuid();
            server.execute(() -> respond(player, request, false, "vibe.atlas.loaded"));
        });
        ServerPlayNetworking.registerGlobalReceiver(SAVE, (server, player, handler, buffer, sender) -> {
            UUID request = buffer.readUuid(); long expected = buffer.readLong();
            String json = buffer.readString(MAX_JSON_LENGTH);
            boolean clearApiKey = buffer.isReadable() && buffer.readBoolean();
            server.execute(() -> {
                Result result = update(player, expected, json, clearApiKey);
                respond(player, request, result.saved, result.messageKey);
            });
        });
    }
    public static Result update(ServerPlayerEntity player, long expected, String json) {
        return update(player, expected, json, false);
    }
    public static Result update(ServerPlayerEntity player, long expected, String json, boolean clearApiKey) {
        if (!canEdit(player)) return new Result(false, "vibe.atlas.denied");
        if (expected != revision) return new Result(false, "vibe.atlas.conflict");
        try {
            if (json.length() > MAX_JSON_LENGTH) throw new IllegalArgumentException();
            AtlasConfig next = AtlasConfig.fromJson(json);
            next.mergeApiKeyFrom(AtlasMod.CONFIG, clearApiKey);
            next.save();
            AtlasMod.CONFIG = next;
            revision++;
            AtlasAi.clear();
            return new Result(true, "vibe.atlas.saved");
        } catch (IllegalArgumentException invalid) {
            return new Result(false, "vibe.atlas.invalid");
        } catch (IOException failed) {
            AtlasMod.LOGGER.error("Could not save Atlas settings", failed);
            return new Result(false, "vibe.atlas.save_failed");
        }
    }
    private static void respond(ServerPlayerEntity player, UUID request, boolean saved, String messageKey) {
        if (!ServerPlayNetworking.canSend(player, RESPONSE)) return;
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(request); buffer.writeBoolean(canEdit(player)); buffer.writeLong(revision);
        buffer.writeBoolean(saved); buffer.writeText(Text.translatable(messageKey));
        buffer.writeString(AtlasMod.CONFIG.publicCopy().toJson(), MAX_JSON_LENGTH);
        buffer.writeBoolean(!AtlasMod.CONFIG.aiApiKey.isBlank());
        ServerPlayNetworking.send(player, RESPONSE, buffer);
    }
}
