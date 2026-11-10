package dev.atlas.companion.client.config;

import dev.atlas.companion.AtlasConfig;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

public final class AtlasPromptScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> apply;
    private String draft;
    private EditBoxWidget editor;
    public AtlasPromptScreen(Screen parent, String value, Consumer<String> apply) {
        super(Text.translatable("vibe.prompt.title")); this.parent = parent; this.draft = value; this.apply = apply;
    }
    @Override protected void init() {
        PromptBox box = new PromptBox();
        editor = addDrawableChild(box);
        editor.setMaxLength(AtlasConfig.MAX_PROMPT_LENGTH); editor.setText(draft);
        editor.setChangeListener(value -> draft = value);
        box.showStart();
        int buttonWidth = Math.min(140, (width - 48) / 3);
        int start = (width - (buttonWidth * 3 + 16)) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("vibe.prompt.apply"), b -> {
            apply.accept(editor.getText()); client.setScreen(parent);
        }).dimensions(start, height - 28, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("vibe.prompt.defaults"), b -> editor.setText(AtlasConfig.DEFAULT_SYSTEM_PROMPT))
                .dimensions(start + buttonWidth + 8, height - 28, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
                .dimensions(start + (buttonWidth + 8) * 2, height - 28, buttonWidth, 20).build());
        setInitialFocus(editor);
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
    @Override public void tick() { editor.tick(); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context); super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("vibe.prompt.subtitle"), 18, 26, 0xAAB2C3);
    }
    private final class PromptBox extends EditBoxWidget {
        PromptBox() { super(AtlasPromptScreen.this.textRenderer, 18, 48, AtlasPromptScreen.this.width - 36,
                Math.max(32, AtlasPromptScreen.this.height - 92), Text.translatable("vibe.prompt.placeholder"), AtlasPromptScreen.this.title); }
        void showStart() { setScrollY(0); }
    }
}
