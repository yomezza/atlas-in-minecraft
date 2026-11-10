package dev.atlas.companion.client.config;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

public final class VibeModsScreen extends Screen {
    private final Screen parent;
    public VibeModsScreen(Screen parent) { super(Text.literal("Vibe Coding")); this.parent = parent; }
    @Override protected void init() {
        ModList list = addDrawableChild(new ModList(client, width, height));
        for (var mod : VibeCodingRegistry.mods()) list.addMod(new ModRow(mod));
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> close())
                .dimensions(width / 2 - 75, height - 28, 150, 20).build());
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context); super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("vibe.mods.subtitle"), width / 2, 25, 0xAAB2C3);
    }
    private final class ModList extends ElementListWidget<ModRow> {
        ModList(MinecraftClient client, int width, int height) { super(client, width, height, 43, height - 40, 36); }
        void addMod(ModRow row) { addEntry(row); }
        @Override public int getRowWidth() { return Math.min(420, width - 36); }
        @Override protected int getScrollbarPositionX() { return width / 2 + getRowWidth() / 2 + 5; }
    }
    private final class ModRow extends ElementListWidget.Entry<ModRow> {
        private final ButtonWidget button;
        ModRow(VibeCodingRegistry.Mod mod) {
            button = ButtonWidget.builder(mod.name(), b -> client.setScreen(mod.screen().apply(VibeModsScreen.this)))
                    .dimensions(0, 0, 200, 24).tooltip(Tooltip.of(mod.description())).build();
        }
        @Override public void render(DrawContext context, int index, int y, int x, int rowWidth, int rowHeight,
                int mouseX, int mouseY, boolean hovered, float delta) {
            button.setX(x); button.setY(y + 2); button.setWidth(rowWidth); button.render(context, mouseX, mouseY, delta);
        }
        @Override public List<? extends Element> children() { return List.of(button); }
        @Override public List<? extends Selectable> selectableChildren() { return List.of(button); }
    }
}
