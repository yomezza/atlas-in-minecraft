package dev.atlas.companion;

import dev.atlas.companion.client.config.AtlasConfigScreen;
import dev.atlas.companion.client.config.AtlasPromptScreen;
import dev.atlas.companion.client.config.VibeModsScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

/** Real client smoke test; this source set is excluded from the distributed mod. */
public final class VibeClientTest implements ClientModInitializer {
    private int stage, delay;
    private boolean started, finished;
    private AtlasConfig original;
    private AtlasConfigScreen configScreen;
    private static final String CUSTOM = "Я Атлас — создатель этого мира.\nМой напарник любит кварцевые башни.\nБудь внимателен к его постройкам.";
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            if (!started) {
                if (!(client.currentScreen instanceof TitleScreen) || client.getOverlay() != null) return;
                started = true; original = AtlasMod.CONFIG.copy(); delay = 10;
            }
            if (delay-- > 0) return;
            try { step(client); delay = 6; }
            catch (Throwable failed) { finish(client, failed); }
        });
    }
    private void step(MinecraftClient client) throws Exception {
        switch (stage++) {
            case 0 -> client.setScreen(new OptionsScreen(client.currentScreen, client.options));
            case 1 -> { screenshot(client, "01-options.png"); button(client.currentScreen, "Vibe Coding").onPress(); }
            case 2 -> {
                require(client.currentScreen instanceof VibeModsScreen, "Vibe Coding must open the mod list");
                screenshot(client, "02-mod-list.png"); button(client.currentScreen, Text.translatable("vibe.atlas.mod_name").getString()).onPress();
            }
            case 3 -> {
                require(client.currentScreen instanceof AtlasConfigScreen, "Choosing Atlas must open its settings");
                configScreen = (AtlasConfigScreen) client.currentScreen;
                screenshot(client, "03-settings.png");
                TextFieldWidget protection = fields(configScreen).get(0);
                protection.setText("maybe");
                require(!button(configScreen, Text.translatable("vibe.atlas.save").getString()).active, "Invalid booleans must disable Save");
                protection.setText(Boolean.toString(!original.protectOwner));
                require(button(configScreen, Text.translatable("vibe.atlas.save").getString()).active, "Valid values must enable Save");
                ElementListWidget<?> list = (ElementListWidget<?>)configScreen.children().stream().filter(e -> e instanceof ElementListWidget<?>).findFirst().orElseThrow();
                list.setScrollAmount(list.getMaxScroll());
            }
            case 4 -> { screenshot(client, "04-context-row.png"); button(configScreen, "…").onPress(); }
            case 5 -> {
                require(client.currentScreen instanceof AtlasPromptScreen, "Prompt control must open the multiline editor");
                screenshot(client, "05-default-context.png");
                editBox(client.currentScreen).setText(CUSTOM);
                button(client.currentScreen, Text.translatable("vibe.prompt.apply").getString()).onPress();
            }
            case 6 -> button(configScreen, Text.translatable("vibe.atlas.save").getString()).onPress();
            case 7 -> {
                AtlasConfig disk = AtlasConfig.fromJson(Files.readString(AtlasConfig.path()));
                require(disk.aiSystemPrompt.equals(CUSTOM), "Multiline context must save to disk");
                require(AtlasMod.CONFIG.aiSystemPrompt.equals(CUSTOM), "Context must apply without restarting");
                require(AtlasMod.CONFIG.protectOwner != original.protectOwner, "Boolean settings must apply");
                button(configScreen, "…").onPress();
            }
            case 8 -> {
                button(client.currentScreen, Text.translatable("vibe.prompt.defaults").getString()).onPress();
                require(editBox(client.currentScreen).getText().equals(AtlasConfig.DEFAULT_SYSTEM_PROMPT), "Default button must restore the entire original context");
                button(client.currentScreen, Text.translatable("vibe.prompt.apply").getString()).onPress();
            }
            case 9 -> {
                button(configScreen, Text.translatable("vibe.atlas.save").getString()).onPress();
                require(AtlasMod.CONFIG.aiSystemPrompt.equals(AtlasConfig.DEFAULT_SYSTEM_PROMPT), "Restored context must apply");
                finish(client, null);
            }
        }
    }
    private void finish(MinecraftClient client, Throwable failure) {
        finished = true;
        try {
            if (original != null) { original.save(); AtlasMod.CONFIG = original; }
            Path result = Path.of("vibe-gui-result.txt");
            Files.writeString(result, failure == null ? "PASS: button, mod list, scrolling, validation, multiline context, save, apply, defaults\n" : "FAIL: " + failure + "\n");
        } catch (Exception error) { error.printStackTrace(); }
        if (failure != null) failure.printStackTrace();
        client.scheduleStop();
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), text -> {});
    }
    private static ButtonWidget button(ParentElement parent, String text) {
        for (Element child : parent.children()) {
            if (child instanceof ButtonWidget button && button.getMessage().getString().equals(text)) return button;
            if (child instanceof ParentElement nested) { try { return button(nested, text); } catch (IllegalStateException ignored) {} }
        }
        throw new IllegalStateException("Missing button: " + text);
    }
    private static List<TextFieldWidget> fields(ParentElement parent) {
        java.util.ArrayList<TextFieldWidget> result = new java.util.ArrayList<>();
        for (Element child : parent.children()) {
            if (child instanceof TextFieldWidget field) result.add(field);
            if (child instanceof ParentElement nested) result.addAll(fields(nested));
        }
        return result;
    }
    private static EditBoxWidget editBox(Screen screen) {
        return (EditBoxWidget) screen.children().stream().filter(e -> e instanceof EditBoxWidget).findFirst().orElseThrow();
    }
}
