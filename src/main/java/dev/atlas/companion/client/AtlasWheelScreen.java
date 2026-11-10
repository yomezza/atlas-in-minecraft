package dev.atlas.companion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.atlas.companion.AtlasControlNetworking;
import dev.atlas.companion.AtlasMod;
import dev.atlas.companion.AtlasScale;
import dev.atlas.companion.client.config.AtlasConfigScreen;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

/** Add a new Option and panel to extend the wheel with future companion parameters. */
public final class AtlasWheelScreen extends Screen {
    public enum Option { SIZE, TOOL, FLIGHT, ATTACK, SHIELD, RECALL, SETTINGS }
    private final UUID request = UUID.randomUUID();
    private AtlasControlNetworking.Snapshot state;
    private Option selected = Option.SIZE;
    private String status = "wheel.loading";
    private double centerX, centerY, radius;
    private int panelX, panelWidth;
    private SizeSlider slider;
    private TextFieldWidget exact;
    private float pendingScale = Float.NaN, sentScale = Float.NaN;
    private long sentAt;
    private boolean requested;
    public AtlasWheelScreen() { super(Text.translatable("wheel.title")); }
    public UUID requestId() { return request; }
    public String token() { return state == null ? "" : state.token(); }
    public void fail(String message) { status = message; }
    public void receive(AtlasControlNetworking.Snapshot snapshot) {
        boolean first = state == null, maximumChanged = state != null && state.maxScale() != snapshot.maxScale();
        state = snapshot; status = snapshot.message();
        if (maximumChanged && Float.isFinite(pendingScale)) pendingScale = Math.min(pendingScale, snapshot.maxScale());
        if (first || maximumChanged || selected != Option.SIZE) rebuild();
    }
    @Override protected void init() {
        radius = Math.min(100, Math.min(height * 0.32, width * 0.21));
        centerX = Math.max(radius + 12, width * 0.29); centerY = (height - 20) / 2.0;
        panelX = (int)Math.max(centerX + radius + 14, width * 0.54); panelWidth = width - panelX - 12;
        rebuild();
        if (!requested || client.getNetworkHandler() != null) { requested = true; AtlasWheelClient.request(this); }
    }
    public void select(Option option) {
        flushScale(); selected = option;
        if (option == Option.RECALL) AtlasWheelClient.send(this, "recall", 0);
        rebuild();
    }
    private String key(Option option) { return "wheel.option." + option.name().toLowerCase(Locale.ROOT); }
    private void rebuild() {
        clearChildren(); slider = null; exact = null;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close()).dimensions(width / 2 - 55, height - 25, 110, 20).build());
        if (state == null || !state.available()) return;
        int y = 52;
        switch (selected) {
            case SIZE -> {
                float value = Float.isFinite(pendingScale) ? pendingScale : state.targetScale();
                slider = addDrawableChild(new SizeSlider(panelX, y, panelWidth, value));
                exact = addDrawableChild(new TextFieldWidget(textRenderer, panelX, y + 24, Math.max(45, panelWidth - 40), 20, Text.translatable("wheel.exact")));
                exact.setMaxLength(10); exact.setText(AtlasScale.format(value));
                addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> applyExact()).dimensions(panelX + panelWidth - 36, y + 24, 36, 20).build());
                float[] presets = {0.25f, 1, 4, 32, 64, 128}; int cell = (panelWidth - 8) / 3;
                for (int i = 0; i < presets.length; i++) {
                    float factor = presets[i];
                    var button = addDrawableChild(ButtonWidget.builder(Text.literal(AtlasScale.format(factor) + "×"), b -> chooseScale(factor, true))
                            .dimensions(panelX + (i % 3) * (cell + 4), y + 48 + (i / 3) * 23, cell, 20).build());
                    button.active = factor <= state.maxScale();
                }
            }
            case TOOL -> {
                actionButton("wheel.fortune", "fortune", y, !state.silk());
                actionButton("wheel.silk", "silk", y + 25, state.silk());
            }
            case FLIGHT -> {
                actionButton("wheel.free", "free", y, state.flight() == 0);
                actionButton("wheel.follow", "follow", y + 25, state.flight() == 1);
                actionButton("wheel.stay", "stay", y + 50, state.flight() == 2);
            }
            case ATTACK -> {
                actionButton("wheel.find_enemies", "attack_offer", y, false).active = state.deployed();
                actionButton("wheel.confirm_attack", "attack_confirm", y + 25, false).active = state.pendingEnemies() > 0 && state.deployed();
                actionButton("wheel.cancel_attack", "attack_cancel", y + 50, false).active = state.pendingEnemies() > 0 || state.attacking();
            }
            case SHIELD -> {
                actionButton("wheel.shield_held", "shield_held", y, state.protectionMode() == 0);
                actionButton("wheel.shield_permanent", "shield_permanent", y + 25, state.protectionMode() == 1);
                actionButton("wheel.shield_off", "shield_off", y + 50, state.protectionMode() == 2);
            }
            case RECALL -> { }
            case SETTINGS -> addDrawableChild(ButtonWidget.builder(Text.literal("Vibe Coding"), b -> client.setScreen(new AtlasConfigScreen(this)))
                    .dimensions(panelX, y, panelWidth, 20).build());
        }
    }
    private ButtonWidget actionButton(String label, String action, int y, boolean activeChoice) {
        Text text = activeChoice ? Text.literal("✓ ").append(Text.translatable(label)) : Text.translatable(label);
        return addDrawableChild(ButtonWidget.builder(text, b -> AtlasWheelClient.send(this, action, 0)).dimensions(panelX, y, panelWidth, 20).build());
    }
    private void applyExact() {
        try { chooseScale(AtlasScale.validate(Double.parseDouble(exact.getText().replace(',', '.')), state.maxScale()), true); }
        catch (IllegalArgumentException invalid) { status = "wheel.invalid_scale"; }
    }
    private void chooseScale(float value, boolean immediate) {
        pendingScale = AtlasScale.sanitize(value, state.maxScale());
        if (slider != null) slider.factor(pendingScale);
        if (exact != null && !exact.isFocused()) exact.setText(AtlasScale.format(pendingScale));
        if (immediate) flushScale();
    }
    private void flushScale() {
        if (Float.isFinite(pendingScale) && pendingScale != sentScale) {
            AtlasWheelClient.send(this, "scale", pendingScale); sentScale = pendingScale; sentAt = System.currentTimeMillis();
        }
    }
    @Override public void tick() {
        if (exact != null) exact.tick();
        if (System.currentTimeMillis() - sentAt > 150) flushScale();
    }
    @Override public boolean mouseReleased(double x, double y, int button) { boolean handled = super.mouseReleased(x, y, button); flushScale(); return handled; }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (selected == Option.SIZE && state != null && state.available() && x >= panelX) {
            float value = Float.isFinite(pendingScale) ? pendingScale : state.targetScale();
            chooseScale((float)(value * Math.pow(2, amount * (hasShiftDown() ? 0.05 : 0.25))), false); return true;
        }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        int option = hovered(x, y);
        if (button == 0 && option >= 0) { select(Option.values()[option]); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 && exact != null && exact.isFocused()) { applyExact(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void close() { flushScale(); client.setScreen(null); }
    @Override public boolean shouldPause() { return false; }
    private int hovered(double x, double y) {
        double distance = Math.hypot(x - centerX, y - centerY);
        if (distance < radius * 0.49 || distance > radius) return -1;
        double angle = (Math.atan2(y - centerY, x - centerX) + Math.PI / 2 + Math.PI * 2) % (Math.PI * 2);
        return Math.min(Option.values().length - 1, (int)(angle / (Math.PI * 2 / Option.values().length)));
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client.world == null) renderBackground(context); else context.fill(0, 0, width, height, 0x55000000);
        context.fill(panelX - 6, 33, width - 7, height - 47, 0xD00D1119);
        drawWheel(context, hovered(mouseX, mouseY));
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFE7DC);
        context.drawTextWithShadow(textRenderer, Text.translatable(key(selected)), panelX, 38, 0xFFB093);
        context.drawItem(new ItemStack(AtlasMod.ATLAS), (int)centerX - 8, (int)centerY - 22);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("АТЛАС"), (int)centerX, (int)centerY + 1, 0xFFE2CF);
        String scale = state == null ? "…" : AtlasScale.format(Float.isFinite(pendingScale) ? pendingScale : state.targetScale()) + "×";
        context.drawCenteredTextWithShadow(textRenderer, scale, (int)centerX, (int)centerY + 14, 0xF37250);
        double angleStep = Math.PI * 2 / Option.values().length;
        for (int i = 0; i < Option.values().length; i++) {
            double angle = -Math.PI / 2 + (i + 0.5) * angleStep;
            String label = Text.translatable(key(Option.values()[i]) + (width < 420 ? ".short" : "")).getString();
            context.drawCenteredTextWithShadow(textRenderer, label, (int)(centerX + Math.cos(angle) * radius * 0.75),
                    (int)(centerY + Math.sin(angle) * radius * 0.75) - 4, selected.ordinal() == i ? 0xFFFFFF : 0xC9CFDC);
        }
        Text info = Text.translatable(status == null ? "wheel.ready" : status);
        var lines = textRenderer.wrapLines(info, width - 24);
        for (int i = 0; i < Math.min(2, lines.size()); i++)
            context.drawCenteredTextWithShadow(textRenderer, lines.get(i), width / 2, height - 46 + i * 10, 0xC2C7D3);
        if (selected == Option.ATTACK && state != null && state.pendingEnemies() > 0)
            context.drawTextWithShadow(textRenderer, Text.translatable("wheel.targets", state.pendingEnemies()), panelX, 131, 0xFF9B76);
    }
    private void drawWheel(DrawContext context, int hovered) {
        context.draw(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer(); buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        double step = Math.PI * 2 / Option.values().length;
        for (int i = 0; i < Option.values().length; i++) {
            int color = i == selected.ordinal() ? 0xEEA83B35 : i == hovered ? 0xEE663039 : 0xE51C2330;
            for (int j = 0; j < 12; j++) {
                double a = -Math.PI / 2 + i * step + 0.025 + (step - 0.05) * j / 12;
                double b = -Math.PI / 2 + i * step + 0.025 + (step - 0.05) * (j + 1) / 12;
                triangle(buffer, matrix, a, radius * 0.5, a, radius, b, radius, color);
                triangle(buffer, matrix, a, radius * 0.5, b, radius, b, radius * 0.5, color);
            }
        }
        for (int i = 0; i < 64; i++) triangle(buffer, matrix, 0, 0, i * Math.PI / 32, radius * 0.48, (i + 1) * Math.PI / 32, radius * 0.48, 0xE60D1119);
        BufferRenderer.drawWithGlobalProgram(buffer.end()); RenderSystem.enableCull(); RenderSystem.disableBlend();
    }
    private void triangle(BufferBuilder out, Matrix4f matrix, double a, double ra, double b, double rb, double c, double rc, int color) {
        for (double[] p : new double[][]{{a,ra},{b,rb},{c,rc}})
            out.vertex(matrix, (float)(centerX + Math.cos(p[0]) * p[1]), (float)(centerY + Math.sin(p[0]) * p[1]), 0)
                    .color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255).next();
    }
    private final class SizeSlider extends SliderWidget {
        SizeSlider(int x, int y, int width, float factor) { super(x, y, width, 20, Text.empty(), normalized(factor)); updateMessage(); }
        private static double normalized(float value) { return Math.log(value / AtlasScale.MIN) / Math.log(AtlasScale.MAX / AtlasScale.MIN); }
        void factor(float factor) { value = normalized(factor); updateMessage(); }
        private float factor() { return (float)(AtlasScale.MIN * Math.pow(AtlasScale.MAX / AtlasScale.MIN, value)); }
        @Override protected void updateMessage() { setMessage(Text.literal(AtlasScale.format(Math.min(state.maxScale(), factor())) + "×")); }
        @Override protected void applyValue() { chooseScale(factor(), false); }
    }
}
