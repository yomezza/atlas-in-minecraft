package dev.atlas.companion;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Shared field definitions keep GUI parsing independent of the Minecraft client. */
public enum AtlasSetting {
    PROTECTION("protectOwner", 5, c -> Boolean.toString(c.protectOwner), (c,v) -> c.protectOwner = bool(v)),
    PERMANENT_PROTECTION("permanentProtection", 5, c -> Boolean.toString(c.permanentProtection), (c,v) -> c.permanentProtection = bool(v)),
    PRANKS("harmlessPranks", 5, c -> Boolean.toString(c.harmlessPranks), (c,v) -> c.harmlessPranks = bool(v)),
    AUTONOMOUS("autonomousFlight", 5, c -> Boolean.toString(c.autonomousFlight), (c,v) -> c.autonomousFlight = bool(v)),
    MAX_SCALE("maxCompanionScale", 3, c -> Integer.toString(c.maxCompanionScale), (c,v) -> c.maxCompanionScale = Integer.parseInt(v.strip())),
    GROWTH_DURATION("growthDurationSeconds", 2, c -> Integer.toString(c.growthDurationSeconds), (c,v) -> c.growthDurationSeconds = Integer.parseInt(v.strip())),
    COMMENTS("ambientComments", 5, c -> Boolean.toString(c.ambientComments), (c,v) -> c.ambientComments = bool(v)),
    COMMENT_INTERVAL("commentIntervalSeconds", 3, c -> Integer.toString(c.commentIntervalSeconds), (c,v) -> c.commentIntervalSeconds = Integer.parseInt(v.strip())),
    NON_OPERATORS("allowNonOperators", 5, c -> Boolean.toString(c.allowNonOperators), (c,v) -> c.allowNonOperators = bool(v)),
    BUILD_LIMIT("maxBuildBlocks", 5, c -> Integer.toString(c.maxBuildBlocks), (c,v) -> c.maxBuildBlocks = Integer.parseInt(v.strip())),
    AI_ENABLED("aiEnabled", 5, c -> Boolean.toString(c.aiEnabled), (c,v) -> c.aiEnabled = bool(v)),
    AI_ENDPOINT("aiEndpoint", 2048, c -> c.aiEndpoint, (c,v) -> c.aiEndpoint = v.strip()),
    AI_MODEL("aiModel", 256, c -> c.aiModel, (c,v) -> c.aiModel = v.strip()),
    AI_KEY("aiApiKey", 4096, c -> c.aiApiKey, (c,v) -> c.aiApiKey = v.strip()),
    AI_KEY_VARIABLE("aiKeyEnvironmentVariable", 128, c -> c.aiKeyEnvironmentVariable, (c,v) -> c.aiKeyEnvironmentVariable = v.strip()),
    AI_FORMAT("aiResponseFormat", 11, c -> c.aiResponseFormat, (c,v) -> c.aiResponseFormat = v.strip().toLowerCase(Locale.ROOT)),
    AI_TIMEOUT("aiTimeoutSeconds", 3, c -> Integer.toString(c.aiTimeoutSeconds), (c,v) -> c.aiTimeoutSeconds = Integer.parseInt(v.strip())),
    AI_PROMPT("aiSystemPrompt", AtlasConfig.MAX_PROMPT_LENGTH, c -> c.aiSystemPrompt, (c,v) -> c.aiSystemPrompt = v);

    public final String key;
    public final int maxLength;
    private final Function<AtlasConfig,String> read;
    private final BiConsumer<AtlasConfig,String> write;
    AtlasSetting(String key, int maxLength, Function<AtlasConfig,String> read, BiConsumer<AtlasConfig,String> write) {
        this.key = key; this.maxLength = maxLength; this.read = read; this.write = write;
    }
    public String value(AtlasConfig config) { return read.apply(config); }
    public String labelKey() { return "vibe.atlas.field." + key; }
    public String hintKey() { return labelKey() + ".hint"; }
    public void apply(AtlasConfig config, String value) {
        try { write.accept(config, value); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException(key + ": invalid value"); }
    }
    private static boolean bool(String value) {
        return switch(value.strip().toLowerCase(Locale.ROOT)) {
            case "true", "да", "1" -> true;
            case "false", "нет", "0" -> false;
            default -> throw new IllegalArgumentException("true / false");
        };
    }
}
