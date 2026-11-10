package dev.atlas.companion.client.config;

import dev.atlas.companion.AtlasConfig;
import dev.atlas.companion.AtlasConfigNetworking;
import dev.atlas.companion.AtlasMod;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public final class AtlasSettingsClient {
    public record Snapshot(AtlasConfig config, boolean editable, long revision, boolean saved, Text message, boolean keyConfigured) {}
    private AtlasSettingsClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AtlasConfigNetworking.RESPONSE, (client, handler, buffer, sender) -> {
            UUID request = buffer.readUuid(); boolean editable = buffer.readBoolean(); long revision = buffer.readLong();
            boolean saved = buffer.readBoolean(); Text message = buffer.readText();
            String json = buffer.readString(AtlasConfigNetworking.MAX_JSON_LENGTH);
            boolean keyConfigured = buffer.isReadable() && buffer.readBoolean();
            client.execute(() -> {
                if (client.currentScreen instanceof AtlasConfigScreen screen && screen.requestId().equals(request)) {
                    try { screen.receive(new Snapshot(AtlasConfig.fromJson(json), editable, revision, saved, message, keyConfigured)); }
                    catch (RuntimeException invalid) { screen.fail(Text.translatable("vibe.atlas.invalid")); }
                }
            });
        });
    }
    public static void request(AtlasConfigScreen screen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            screen.receive(new Snapshot(AtlasMod.CONFIG.publicCopy(), true, 0, false, Text.translatable("vibe.atlas.local"), !AtlasMod.CONFIG.aiApiKey.isBlank()));
        } else if (ClientPlayNetworking.canSend(AtlasConfigNetworking.REQUEST)) {
            PacketByteBuf buffer = PacketByteBufs.create(); buffer.writeUuid(screen.requestId());
            ClientPlayNetworking.send(AtlasConfigNetworking.REQUEST, buffer);
        } else screen.fail(Text.translatable("vibe.atlas.unsupported"));
    }
    public static void save(AtlasConfigScreen screen, AtlasConfig config, long revision) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            try {
                config.mergeApiKeyFrom(AtlasMod.CONFIG, screen.clearApiKey());
                config.save(); AtlasMod.CONFIG = config;
                screen.receive(new Snapshot(config.publicCopy(), true, 0, true, Text.translatable("vibe.atlas.saved"), !config.aiApiKey.isBlank()));
            } catch (Exception failed) { screen.fail(Text.translatable("vibe.atlas.save_failed")); }
        } else if (ClientPlayNetworking.canSend(AtlasConfigNetworking.SAVE)) {
            PacketByteBuf buffer = PacketByteBufs.create(); buffer.writeUuid(screen.requestId());
            buffer.writeLong(revision); buffer.writeString(config.toJson(), AtlasConfigNetworking.MAX_JSON_LENGTH);
            buffer.writeBoolean(screen.clearApiKey());
            ClientPlayNetworking.send(AtlasConfigNetworking.SAVE, buffer);
        } else screen.fail(Text.translatable("vibe.atlas.unsupported"));
    }
}
