package dev.atlas.companion.client.config;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Masks both the rendered input and its screen-reader narration. */
public final class SecretTextFieldWidget extends TextFieldWidget {
    public SecretTextFieldWidget(TextRenderer renderer, int x, int y, int width, int height, Text label) {
        super(renderer, x, y, width, height, label);
        setRenderTextProvider((value, offset) -> Text.literal("•".repeat(value.length())).asOrderedText());
    }
    @Override protected MutableText getNarrationMessage() { return Text.translatable("vibe.atlas.key_narration"); }
}
