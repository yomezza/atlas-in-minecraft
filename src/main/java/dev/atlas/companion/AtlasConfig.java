package dev.atlas.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AtlasConfig {
    public boolean allowNonOperators = false;
    public boolean protectOwner = true;
    public boolean harmlessPranks = true;
    public int maxBuildBlocks = 8192;
    public boolean aiEnabled = false;
    public String aiEndpoint = "https://api.openai.com/v1/chat/completions";
    public String aiModel = "gpt-4.1-mini";
    public String aiKeyEnvironmentVariable = "ATLAS_API_KEY";
    public int aiTimeoutSeconds = 35;

    public static AtlasConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("atlas-companion.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                AtlasConfig result = gson.fromJson(Files.readString(path), AtlasConfig.class);
                if (result != null) {
                    result.maxBuildBlocks = Math.max(64, Math.min(32768, result.maxBuildBlocks));
                    result.aiTimeoutSeconds = Math.max(5, Math.min(90, result.aiTimeoutSeconds));
                    return result;
                }
            }
            AtlasConfig config = new AtlasConfig();
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(config));
            return config;
        } catch (Exception e) {
            AtlasMod.LOGGER.error("Could not load Atlas config; using defaults", e);
            return new AtlasConfig();
        }
    }
}
