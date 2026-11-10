package dev.atlas.companion.client.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Client-side extension point for mods that supply a Vibe Coding settings screen. */
@Environment(EnvType.CLIENT)
public final class VibeCodingRegistry {
    public record Mod(String id, Text name, Text description, Function<Screen, Screen> screen) {}
    private static final Map<String, Mod> MODS = new LinkedHashMap<>();
    private VibeCodingRegistry() {}
    public static void register(String id, Text name, Text description, Function<Screen, Screen> screen) {
        MODS.put(id, new Mod(id, name, description, screen));
    }
    public static Collection<Mod> mods() { return java.util.List.copyOf(MODS.values()); }
}
