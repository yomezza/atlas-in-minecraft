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
import net.minecraft.item.ItemStack;

/** Real client smoke test; this source set is excluded from the distributed mod. */
public final class VibeClientTest implements ClientModInitializer {
    private int stage, delay;
    private boolean started, finished;
    private AtlasConfig original;
    private AtlasConfigScreen configScreen;
    private static final String CUSTOM = "Я Атлас — создатель этого мира.\nМой напарник любит кварцевые башни.\nБудь внимателен к его постройкам.";
    private static final String KEY_FIXTURE = "demo-key-for-client-test";
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            if (!started) {
                if (!(client.currentScreen instanceof TitleScreen) || client.getOverlay() != null) return;
                started = true; original = AtlasMod.CONFIG.copy(); delay = 10;
            }
            if (delay-- > 0) return;
            try { delay = 6; step(client); }
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
                client.setScreen(new dev.atlas.companion.client.AtlasWheelScreen());
            }
            case 10 -> {
                var wheel = (dev.atlas.companion.client.AtlasWheelScreen)client.currentScreen;
                wheel.receive(new AtlasControlNetworking.Snapshot(true, true, false, 0, 1, 1, 128, 2,
                        java.util.UUID.randomUUID().toString(), false, 0, true, "wheel.ready"));
            }
            case 11 -> {
                screenshot(client, "06-wheel-size.png");
                var wheel = (dev.atlas.companion.client.AtlasWheelScreen)client.currentScreen;
                TextFieldWidget exact = fields(wheel).get(0); exact.setText("64"); button(wheel, "OK").onPress();
                require(wheel.children().stream().anyMatch(e -> e instanceof net.minecraft.client.gui.widget.SliderWidget s && s.getMessage().getString().contains("64")),
                        "The radial scale slider must accept an exact giant size");
                wheel.select(dev.atlas.companion.client.AtlasWheelScreen.Option.ATTACK);
                require(button(wheel, Text.translatable("wheel.confirm_attack").getString()).active, "Offered targets must expose a human confirmation control");
            }
            case 12 -> { screenshot(client, "07-wheel-attack.png"); ((dev.atlas.companion.client.AtlasWheelScreen)client.currentScreen).select(dev.atlas.companion.client.AtlasWheelScreen.Option.SHIELD); }
            case 13 -> {
                screenshot(client, "08-wheel-shield.png");
                original.save(); AtlasMod.CONFIG = original;
                if (Boolean.getBoolean("atlas.worldtest")) client.createIntegratedServerLoader().start(new TitleScreen(), "AtlasPreview");
                else finish(client, null);
            }
            case 14 -> {
                if (client.player == null || client.world == null || client.getServer() == null || client.currentScreen != null || client.getOverlay() != null) { stage--; return; }
                client.getServer().execute(() -> {
                    ServerSetup.prepare(client);
                });
                delay = 100;
            }
            case 15 -> {
                require(((AtlasShieldAccess)client.player).atlas$shieldActive(), "Owner must receive the synchronized shield flag");
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
                client.player.setYaw(0); client.player.setPitch(0);
            }
            case 16 -> {
                screenshot(client, "09-energy-shield.png");
                client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.player.setYaw(0); client.player.setPitch(-73);
            }
            case 17 -> {
                screenshot(client, "10-giant-atlas.png");
                client.getServer().execute(() -> {
                    var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                    AtlasManager.find(player).setTargetScale(4);
                });
                delay = 100;
            }
            case 18 -> {
                client.player.setPitch(-20); client.player.setYaw(0);
                client.getServer().execute(() -> ServerSetup.laser(client));
                delay = 10;
            }
            case 19 -> { screenshot(client, "11-laser.png"); middleClick(client); }
            case 20 -> {
                require(client.currentScreen instanceof dev.atlas.companion.client.AtlasWheelScreen, "Middle mouse button must open the actual in-world wheel");
                screenshot(client, "12-wheel-in-world.png");
                ((dev.atlas.companion.client.AtlasWheelScreen)client.currentScreen).select(dev.atlas.companion.client.AtlasWheelScreen.Option.SHIELD);
            }
            case 21 -> button(client.currentScreen, Text.translatable("wheel.shield_held").getString()).onPress();
            case 22 -> {
                require(!((AtlasShieldAccess)client.player).atlas$shieldActive(), "Energy field must disappear after choosing held-only protection with empty hands");
                button(client.currentScreen, Text.translatable("wheel.shield_permanent").getString()).onPress();
            }
            case 23 -> {
                require(((AtlasShieldAccess)client.player).atlas$shieldActive(), "Permanent shield control must synchronize back to the actual owner client");
                middleClick(client); require(client.currentScreen == null, "Middle click must close the radial wheel");
                client.setScreen(new AtlasConfigScreen(null));
            }
            case 24 -> {
                configScreen = (AtlasConfigScreen)client.currentScreen;
                TextFieldWidget key = fields(configScreen).stream().filter(f -> f.getMessage().getString().equals(Text.translatable("vibe.atlas.field.aiApiKey").getString())).findFirst().orElseThrow();
                require(key instanceof dev.atlas.companion.client.config.SecretTextFieldWidget, "API key input must mask its contents");
                key.setText(KEY_FIXTURE);
                fields(configScreen).stream().filter(f -> f.getMessage().getString().equals(Text.translatable("vibe.atlas.field.aiResponseFormat").getString()))
                        .findFirst().orElseThrow().setText("json_schema");
                fields(configScreen).stream().filter(f -> f.getMessage().getString().equals(Text.translatable("vibe.atlas.field.aiTimeoutSeconds").getString()))
                        .findFirst().orElseThrow().setText("180");
                ElementListWidget<?> list = (ElementListWidget<?>)configScreen.children().stream().filter(e -> e instanceof ElementListWidget<?>).findFirst().orElseThrow();
                list.setScrollAmount(list.getMaxScroll());
            }
            case 25 -> { screenshot(client, "13-api-key-input.png"); button(configScreen, Text.translatable("vibe.atlas.save").getString()).onPress(); }
            case 26 -> {
                require(AtlasMod.CONFIG.aiApiKey.equals(KEY_FIXTURE), "The key entered in-game must reach the actual server configuration");
                require(AtlasMod.CONFIG.aiResponseFormat.equals("json_schema") && AtlasMod.CONFIG.aiTimeoutSeconds == 180,
                        "LM Studio format and extended timeout must save through the actual in-game settings");
                TextFieldWidget key = fields(configScreen).stream().filter(f -> f instanceof dev.atlas.companion.client.config.SecretTextFieldWidget).findFirst().orElseThrow();
                require(key.getText().isEmpty(), "Saved server credentials must not be sent back to the editor");
                button(configScreen, Text.translatable("vibe.atlas.save").getString()).onPress();
            }
            case 27 -> {
                require(AtlasMod.CONFIG.aiApiKey.equals(KEY_FIXTURE), "Saving another setting must preserve the existing API key");
                button(configScreen, "×").onPress(); button(configScreen, Text.translatable("vibe.atlas.save").getString()).onPress();
            }
            case 28 -> {
                require(AtlasMod.CONFIG.aiApiKey.isEmpty(), "The explicit clear control must remove the saved server key");
                finish(client, null);
            }
        }
    }
    private static void middleClick(MinecraftClient client) throws Exception {
        var method = net.minecraft.client.Mouse.class.getDeclaredMethod("onMouseButton", long.class, int.class, int.class, int.class);
        method.setAccessible(true); method.invoke(client.mouse, client.getWindow().getHandle(), 2, 1, 0);
    }
    private static final class ServerSetup {
        static void prepare(MinecraftClient client) {
            var server = client.getServer(); var player = server.getPlayerManager().getPlayer(client.player.getUuid());
            var world = player.getServerWorld();
            var ground = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new net.minecraft.util.math.BlockPos(64, 0, 64));
            player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            player.teleport(world, 64.5, ground.getY() + 0.1, 64.5, 0, 0);
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(AtlasMod.ATLAS));
            AtlasShield.setMode(player, 1);
            AtlasMod.ATLAS.use(world, player, net.minecraft.util.Hand.MAIN_HAND);
            var atlas = AtlasManager.find(player);
            atlas.setPosition(player.getPos().add(0, 1.05, 6)); atlas.setFlightMode(2); atlas.setTargetScale(16);
            AtlasIdentity.deployed(player, atlas);
        }
        static void laser(MinecraftClient client) {
            var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            var mob = net.minecraft.entity.EntityType.CREEPER.create(player.getWorld());
            mob.setPosition(player.getPos().add(0, 0, 11)); mob.setAiDisabled(true);
            mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(500); mob.setHealth(500);
            player.getWorld().spawnEntity(mob);
            AtlasCombat.offer(player); var offer = AtlasCombat.pending(player);
            if (offer != null) AtlasCombat.confirm(player, offer.token().toString());
        }
    }
    private void finish(MinecraftClient client, Throwable failure) {
        finished = true;
        try {
            if (original != null) { original.save(); AtlasMod.CONFIG = original; }
            Path result = Path.of("vibe-gui-result.txt");
            Files.writeString(result, failure == null ? "PASS: Vibe Coding, context, radial wheel, shield, masked API-key entry, server save, redaction, preservation and clear\n" : "FAIL: " + failure + "\n");
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
