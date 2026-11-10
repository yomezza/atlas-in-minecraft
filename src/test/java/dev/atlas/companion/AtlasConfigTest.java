package dev.atlas.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtlasConfigTest {
    @Test void detailedDefaultContextAndLegacyConfigAreSupported() {
        AtlasConfig config = AtlasConfig.fromJson("{\"protectOwner\":false,\"aiModel\":\"local-model\"}");
        assertFalse(config.protectOwner);
        assertEquals(AtlasConfig.DEFAULT_SYSTEM_PROMPT, config.aiSystemPrompt);
        assertTrue(config.aiSystemPrompt.length() > 2500);
        assertTrue(config.aiSystemPrompt.length() <= AtlasConfig.MAX_PROMPT_LENGTH);
        assertTrue(config.aiSystemPrompt.contains("создателем"));
        assertTrue(config.aiSystemPrompt.contains("игровые команды"));
    }
    @Test void textFieldsRejectInvalidBooleanAndNumbers() {
        AtlasConfig config = new AtlasConfig();
        AtlasSetting.AI_ENABLED.apply(config, "да"); assertTrue(config.aiEnabled);
        AtlasSetting.AI_ENABLED.apply(config, "false"); assertFalse(config.aiEnabled);
        assertThrows(IllegalArgumentException.class, () -> AtlasSetting.AI_ENABLED.apply(config, "maybe"));
        assertThrows(IllegalArgumentException.class, () -> AtlasSetting.BUILD_LIMIT.apply(config, "1.5"));
        AtlasSetting.BUILD_LIMIT.apply(config, "32769");
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    @Test void endpointAndPromptLimitsAreValidated() {
        AtlasConfig config = new AtlasConfig();
        config.aiEndpoint = "http://127.0.0.1:8080/v1/chat/completions"; assertDoesNotThrow(config::validate);
        config.aiEndpoint = "http://example.org/v1/chat/completions";
        assertDoesNotThrow(config::validate);
        config.aiEndpoint = "192.168.1.10:1234/v1/chat/completions";
        assertDoesNotThrow(config::validate);
        assertEquals("http://192.168.1.10:1234/v1/chat/completions", config.aiEndpoint);
        config.aiEndpoint = "ftp://example.org/api";
        assertThrows(IllegalArgumentException.class, config::validate);
        config.aiEndpoint = "https://example.org/v1/chat/completions";
        config.aiSystemPrompt = "x".repeat(AtlasConfig.MAX_PROMPT_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    @Test void unicodeMultilinePromptSurvivesSaveAndInvalidSavePreservesFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("atlas-companion.json");
        AtlasConfig config = new AtlasConfig();
        config.aiSystemPrompt = "Ты создал этот мир.\nИгрок — твой напарник.\nНе бойся строить!";
        config.save(file);
        byte[] saved = Files.readAllBytes(file);
        AtlasConfig restored = AtlasConfig.fromJson(Files.readString(file));
        assertEquals(config.aiSystemPrompt, restored.aiSystemPrompt);
        config.aiTimeoutSeconds = 0;
        assertThrows(IllegalArgumentException.class, () -> config.save(file));
        assertArrayEquals(saved, Files.readAllBytes(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void malformedNetworkJsonIsRejectedWithoutLeakingParsingExceptions() {
        assertThrows(IllegalArgumentException.class, () -> AtlasConfig.fromJson("{broken"));
        assertThrows(IllegalArgumentException.class, () -> AtlasConfig.fromJson("null"));
        assertThrows(IllegalArgumentException.class, () -> AtlasConfig.fromJson("[]"));
        assertThrows(IllegalArgumentException.class, () -> AtlasConfig.fromJson("{\"aiSystemPrompt\":null}"));
    }
}
