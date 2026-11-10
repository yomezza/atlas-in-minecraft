package dev.atlas.companion.client.config;

import dev.atlas.companion.AtlasConfig;
import dev.atlas.companion.AtlasSetting;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class AtlasConfigScreen extends Screen {
    private final Screen parent;
    private final UUID request = UUID.randomUUID();
    private final Map<AtlasSetting, String> drafts = new EnumMap<>(AtlasSetting.class);
    private AtlasConfig base = new AtlasConfig();
    private boolean requested, editable, loading = true, saving;
    private boolean keyConfigured, clearApiKey;
    private long revision, pendingSince;
    private Text status = Text.translatable("vibe.atlas.loading");
    private int statusColor = 0xAAB2C3;
    private SettingsList list;
    private ButtonWidget save, reload;
    private double scroll;
    public AtlasConfigScreen(Screen parent) { super(Text.translatable("vibe.atlas.title")); this.parent = parent; }
    public UUID requestId() { return request; }
    public boolean clearApiKey() { return clearApiKey; }
    @Override protected void init() {
        list = addDrawableChild(new SettingsList(client, width, height));
        for (AtlasSetting setting : AtlasSetting.values()) list.addSetting(new SettingRow(setting));
        list.setScrollAmount(scroll);
        int buttonWidth = Math.min(140, (width - 48) / 3);
        int start = (width - (buttonWidth * 3 + 16)) / 2;
        save = addDrawableChild(ButtonWidget.builder(Text.translatable("vibe.atlas.save"), b -> save())
                .dimensions(start, height - 28, buttonWidth, 20).build());
        reload = addDrawableChild(ButtonWidget.builder(Text.translatable("vibe.atlas.reload"), b -> load())
                .dimensions(start + buttonWidth + 8, height - 28, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> close())
                .dimensions(start + (buttonWidth + 8) * 2, height - 28, buttonWidth, 20).build());
        refreshControls();
        if (!requested) { requested = true; load(); }
    }
    private void load() {
        loading = true; saving = false; pendingSince = System.currentTimeMillis();
        status = Text.translatable("vibe.atlas.loading"); statusColor = 0xAAB2C3;
        refreshControls(); AtlasSettingsClient.request(this);
    }
    public void receive(AtlasSettingsClient.Snapshot snapshot) {
        base = snapshot.config(); editable = snapshot.editable(); revision = snapshot.revision();
        loading = false; saving = false;
        keyConfigured = snapshot.keyConfigured(); clearApiKey = false;
        for (AtlasSetting setting : AtlasSetting.values()) drafts.put(setting, setting.value(base));
        status = editable ? snapshot.message() : Text.translatable("vibe.atlas.readonly");
        statusColor = snapshot.saved() ? 0x81D69A : 0xAAB2C3;
        if (list != null) scroll = list.getScrollAmount();
        clearAndInit();
    }
    public void fail(Text message) {
        loading = false; saving = false; status = message; statusColor = 0xFF8787;
        refreshControls();
    }
    private AtlasConfig draftConfig() {
        AtlasConfig result = base.copy();
        for (AtlasSetting setting : AtlasSetting.values()) setting.apply(result, drafts.getOrDefault(setting, setting.value(base)));
        result.validate(); return result;
    }
    private void save() {
        if (!editable || loading || saving) return;
        try {
            AtlasConfig config = draftConfig();
            saving = true; pendingSince = System.currentTimeMillis(); status = Text.translatable("vibe.atlas.saving");
            statusColor = 0xAAB2C3; refreshControls(); AtlasSettingsClient.save(this, config, revision);
        } catch (IllegalArgumentException invalid) { fail(Text.translatable("vibe.atlas.field_error", invalid.getMessage())); }
    }
    private void refreshControls() {
        boolean valid = true;
        if (list != null) for (SettingRow row : list.children()) {
            try {
                AtlasConfig candidate = base.copy(); row.setting.apply(candidate, drafts.getOrDefault(row.setting, row.setting.value(base)));
                candidate.validate(); row.field.setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR);
            } catch (IllegalArgumentException invalid) { valid = false; row.field.setEditableColor(0xFF7777); }
            row.field.active = editable && !loading && !saving;
            row.field.setEditable(row.setting != AtlasSetting.AI_PROMPT && row.field.active);
            if (row.edit != null) row.edit.active = row.field.active;
        }
        if (save != null) save.active = valid && editable && !loading && !saving;
        if (reload != null) reload.active = !loading && !saving;
    }
    @Override public void tick() {
        if (list != null) for (SettingRow row : list.children()) row.field.tick();
        if ((loading || saving) && System.currentTimeMillis() - pendingSince > 10000) fail(Text.translatable("vibe.atlas.timeout"));
    }
    @Override public void removed() { if (list != null) scroll = list.getScrollAmount(); }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context); super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, 0xFFFFFF);
        Text scope = Text.translatable(client.getNetworkHandler() == null ? "vibe.atlas.scope_local" : "vibe.atlas.scope_server");
        context.drawCenteredTextWithShadow(textRenderer, scope, width / 2, 24, 0xAAB2C3);
        var lines = textRenderer.wrapLines(status, width - 36);
        for (int i = 0; i < Math.min(2, lines.size()); i++)
            context.drawCenteredTextWithShadow(textRenderer, lines.get(i), width / 2, height - 57 + i * 10, statusColor);
    }
    private final class SettingsList extends ElementListWidget<SettingRow> {
        SettingsList(MinecraftClient client, int width, int height) { super(client, width, height, 41, height - 65, 46); }
        void addSetting(SettingRow row) { addEntry(row); }
        @Override public int getRowWidth() { return Math.min(620, width - 40); }
        @Override protected int getScrollbarPositionX() { return width / 2 + getRowWidth() / 2 + 5; }
    }
    private final class SettingRow extends ElementListWidget.Entry<SettingRow> {
        final AtlasSetting setting;
        final TextFieldWidget field;
        final ButtonWidget edit;
        SettingRow(AtlasSetting setting) {
            this.setting = setting;
            field = setting == AtlasSetting.AI_KEY
                    ? new SecretTextFieldWidget(textRenderer, 0, 0, 150, 20, Text.translatable(setting.labelKey()))
                    : new TextFieldWidget(textRenderer, 0, 0, 150, 20, Text.translatable(setting.labelKey()));
            field.setMaxLength(setting.maxLength);
            String value = drafts.getOrDefault(setting, setting.value(base));
            field.setText(setting == AtlasSetting.AI_PROMPT ? value.replace('\n', ' ') : value);
            field.setCursorToStart(); field.setTooltip(Tooltip.of(Text.translatable(setting.hintKey())));
            if (setting == AtlasSetting.AI_KEY) field.setPlaceholder(Text.translatable(keyConfigured && !clearApiKey ? "vibe.atlas.key_saved" : "vibe.atlas.key_placeholder"));
            field.setChangedListener(text -> {
                drafts.put(setting, text); status = Text.translatable("vibe.atlas.unsaved"); statusColor = 0xE9C579;
                if (setting == AtlasSetting.AI_KEY && !text.isBlank()) clearApiKey = false;
                refreshControls();
            });
            edit = setting == AtlasSetting.AI_PROMPT ? ButtonWidget.builder(Text.literal("…"), b -> client.setScreen(
                    new AtlasPromptScreen(AtlasConfigScreen.this, drafts.getOrDefault(setting, setting.value(base)), text -> {
                        drafts.put(setting, text); status = Text.translatable("vibe.atlas.unsaved"); statusColor = 0xE9C579;
                    }))).dimensions(0, 0, 22, 20).tooltip(Tooltip.of(Text.translatable("vibe.prompt.title"))).build()
                    : setting == AtlasSetting.AI_KEY ? ButtonWidget.builder(Text.literal("×"), b -> {
                        clearApiKey = true; field.setText(""); field.setPlaceholder(Text.translatable("vibe.atlas.key_placeholder"));
                        status = Text.translatable("vibe.atlas.key_clear_pending"); statusColor = 0xE9C579;
                    }).dimensions(0, 0, 22, 20).tooltip(Tooltip.of(Text.translatable("vibe.atlas.key_clear"))).build() : null;
        }
        @Override public void render(DrawContext context, int index, int y, int x, int rowWidth, int rowHeight,
                int mouseX, int mouseY, boolean hovered, float delta) {
            int leftWidth = rowWidth / 2;
            String label = textRenderer.trimToWidth(Text.translatable(setting.labelKey()).getString(), leftWidth - 8);
            context.drawTextWithShadow(textRenderer, label, x + 2, y + 7, 0xE4E7EF);
            field.setX(x + leftWidth); field.setY(y + 1); field.setWidth(rowWidth - leftWidth - (edit == null ? 2 : 28));
            field.render(context, mouseX, mouseY, delta);
            if (edit != null) { edit.setX(x + rowWidth - 24); edit.setY(y + 1); edit.render(context, mouseX, mouseY, delta); }
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(Text.translatable(setting.hintKey()).getString(), rowWidth - 6),
                    x + 2, y + 27, 0x9099AC);
        }
        @Override public List<? extends Element> children() { return edit == null ? List.of(field) : List.of(field, edit); }
        @Override public List<? extends Selectable> selectableChildren() { return edit == null ? List.of(field) : List.of(field, edit); }
    }
}
