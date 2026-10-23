package com.mine.geometry_node.client.ui.editor.entity;

import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.composite.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityPreviewCell;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.core.engine.system.entityeditor.EntityEditorChanges;
import com.mine.geometry_node.core.engine.system.entityeditor.EntityEditorNames;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.*;
import static com.mine.geometry_node.client.ui.editor.entity.EntityEditorViews.*;

/** Only advertised fields are editable. Enter completes text edits; navigation preserves raw input. */
final class EntityAttributesView extends UiScrollView {
    private final EntityEditDraft draft;
    private final Map<String, UiTextInput> numbers = new LinkedHashMap<>();
    private final Map<String, UiTextInput> inputs = new LinkedHashMap<>();
    private final Map<String, UiCheckBox> flags = new LinkedHashMap<>();
    private final Map<String, String> stagedText = new HashMap<>();
    private final Map<String, Object> stagedValues = new HashMap<>();
    private final Runnable changed;
    private final UiDisplayText registryValue, dimensionValue, positionValue;
    private final String dimension;
    private boolean updating;
    private boolean disposed;
    private RichTextValue unfinishedName;

    EntityAttributesView(Context context, UiTheme theme, EntityEditDraft draft, UUID entity, String dimension, Runnable changed) {
        super(context, theme.scroll()); this.draft = draft; this.changed = changed; this.dimension = dimension;
        setHorizontalScrollBarEnabled(false);
        LinearLayout body = row(context); body.setPadding(px(20), px(18), px(20), px(20));
        body.setGravity(Gravity.TOP);
        LinearLayout summary = column(context); summary.setPadding(0, 0, px(24), 0);
        body.addView(summary, size(248, -2));
        EntityPreviewCell preview = new EntityPreviewCell(context, 224, theme, EntityPreviewCell.Layout.SQUARE);
        preview.setReference(entity, dimension);
        summary.addView(preview, size(-1, 224));
        registryValue = metadata(context, theme);
        LinearLayout.LayoutParams registryParams = size(-1, -2); registryParams.topMargin = px(12);
        summary.addView(registryValue, registryParams);
        summary.addView(text(context, tr("attributes.dimension"), theme.text().muted()), size(-1, 32));
        dimensionValue = metadata(context, theme); summary.addView(dimensionValue, size(-1, -2));
        summary.addView(text(context, tr("attributes.position"), theme.text().muted()), size(-1, 32));
        positionValue = metadata(context, theme); summary.addView(positionValue, size(-1, -2));
        LinearLayout fields = column(context); fields.setPadding(px(24), 0, 0, 0);
        body.addView(divider(context, theme), size(1, -1));
        body.addView(fields, new LinearLayout.LayoutParams(0, -2, 1));
        Map<String, Object> attrs = draft.dictionary("attributes");
        if (attrs.containsKey("name")) {
            fields.addView(text(context, tr("attributes.names"), theme.text().normal()), size(-1, 30));
            fields.addView(text(context, tr("attributes.name"), theme.text().muted()), size(-1, 26));
            LinearLayout nameRow = row(context);
            UiTextInput name = input(context, theme); name.setText(RichTextValue.from(attrs.get("name")).plain());
            name.setContentDescription(tr("attributes.name"));
            nameRow.addView(name, new LinearLayout.LayoutParams(0, px(34), 1));
            UiIconButton rich = UiIconButton.createThemed(context, theme,
                    new SvgIconView(context, SvgIconView.Icon.MORE_HORIZONTAL, theme.text().normal()))
                    .tooltip(tr("attributes.edit_rich_name"));
            rich.setEnabled(EntityEditorNames.supportsRichText(attrs.get("name")));
            rich.setOnClickListener(view -> openRichName(theme, name));
            LinearLayout.LayoutParams action = size(34, 34); action.leftMargin = px(8);
            nameRow.addView(rich, action); fields.addView(nameRow, size(-1, 34));
            bindInput("name", name, attrs.get("name"));
        }
        addFlags(fields, context, theme, attrs, List.of("nameVisible"));
        if (List.of("health", "maxHealth", "speed", "attack").stream().anyMatch(key -> attrs.get(key) instanceof Number))
            section(fields, context, theme, "attributes.living");
        LinearLayout numberRow = null; int index = 0;
        for (String key : List.of("health", "maxHealth", "speed", "attack")) {
            if (!(attrs.get(key) instanceof Number number)) continue;
            if (index++ % 2 == 0) { numberRow = row(context); fields.addView(numberRow, size(-1, -2)); }
            LinearLayout field = column(context); field.setPadding(0, px(10), px(12), 0);
            field.addView(text(context, tr("attributes." + key), theme.text().muted()), size(-1, 25));
            UiTextInput input = input(context, theme); input.setText(String.valueOf(number));
            input.setContentDescription(tr("attributes." + key)); field.addView(input, size(-1, 36));
            numbers.put(key, input); bindInput(key, input, number);
            numberRow.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
        }
        List<String> behavior = List.of("noAI", "persistent", "silent", "invulnerable", "gravity");
        if (behavior.stream().anyMatch(key -> attrs.get(key) instanceof Boolean))
            section(fields, context, theme, "attributes.behavior");
        addFlags(fields, context, theme, attrs, behavior);
        refreshSummary();
        addView(body, new FrameLayout.LayoutParams(-1, -2));
    }
    private static UiDisplayText metadata(Context context, UiTheme theme) {
        UiDisplayText value = text(context, "", theme.text().secondary());
        value.setTypeface(Typeface.MONOSPACED); value.setMinHeight(px(24)); return value;
    }
    private static void section(LinearLayout fields, Context context, UiTheme theme, String key) {
        LinearLayout.LayoutParams line = size(-1, 1); line.topMargin = px(24); line.bottomMargin = px(14);
        fields.addView(divider(context, theme), line);
        fields.addView(text(context, tr(key), theme.text().normal()), size(-1, 30));
    }
    private void addFlags(LinearLayout fields, Context context, UiTheme theme, Map<String, Object> attrs, List<String> keys) {
        LinearLayout flagRow = null; int index = 0;
        for (String key : keys) {
            if (!(attrs.get(key) instanceof Boolean value)) continue;
            if (index++ % 2 == 0) { flagRow = row(context); fields.addView(flagRow, size(-1, 40)); }
            LinearLayout flag = row(context); UiCheckBox checkbox = UiCheckBox.createThemed(context, theme);
            checkbox.setChecked(value); checkbox.setContentDescription(tr("attributes." + key));
            flags.put(key, checkbox);
            checkbox.setOnCheckedChangeListener((view, checked) -> { if (!updating) draft.attribute(key, checked); });
            flag.addView(checkbox, size(16, 16));
            var label = text(context, tr("attributes." + key), theme.text().normal());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1); lp.leftMargin = px(9);
            flag.addView(label, lp); label.setOnClickListener(view -> checkbox.performClick());
            flagRow.addView(flag, new LinearLayout.LayoutParams(0, px(32), 1));
        }
        if (index % 2 != 0) flagRow.addView(new LinearLayout(context), new LinearLayout.LayoutParams(0, px(32), 1));
    }
    private void refreshSummary() {
        Map<String, Object> snapshot = draft.snapshot();
        registryValue.setText(snapshot.get("registryType") instanceof String id ? id : "");
        dimensionValue.setText(snapshot.get("dimension") instanceof String id ? id : dimension);
        Object value = snapshot.get("position");
        positionValue.setText(value instanceof Map<?, ?> position && position.get("x") instanceof Number x
                && position.get("y") instanceof Number y && position.get("z") instanceof Number z
                ? String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x.doubleValue(), y.doubleValue(), z.doubleValue()) : "");
    }
    private void openRichName(UiTheme theme, UiTextInput name) {
        if (disposed) return;
        Object original = draft.dictionary("attributes").get("name");
        String raw = name.getText().toString();
        RichTextValue initial = unfinishedName != null ? unfinishedName : raw.equals(RichTextValue.from(original).plain())
                ? RichTextValue.from(original) : RichTextValue.plain(raw);
        ExpandedTextInputOverlay.showRichText(getContext(), name, initial, rich -> {
            if (disposed) return;
            Object confirmed = draft.dictionary("attributes").get("name");
            if (!EntityEditorNames.sameAfterConfirmation(original, confirmed)
                    || !raw.equals(name.getText().toString())) {
                retainUnfinishedName(name, rich, "error.conflict"); return;
            }
            try { EntityEditorNames.validate(rich); }
            catch (IllegalArgumentException invalid) {
                // Keep the full unfinished form locally so reopening the shared editor can repair it.
                retainUnfinishedName(name, rich, "error.invalid_name"); return;
            }
            Object completed = EntityEditorNames.editable(rich).equals(EntityEditorNames.editable(RichTextValue.from(original)))
                    ? confirmed : rich;
            updating = true;
            try {
                unfinishedName = null;
                name.setText(RichTextValue.from(completed).plain()); name.setErrorMessage(null);
                stagedText.put("name", name.getText().toString()); stagedValues.put("name", completed);
            } finally { updating = false; }
            draft.attribute("name", completed); changed.run();
        }, null, theme);
    }
    private void retainUnfinishedName(UiTextInput name, RichTextValue rich, String error) {
        updating = true;
        try { unfinishedName = rich; name.setText(rich.plain()); name.setErrorMessage(tr(error)); }
        finally { updating = false; }
        changed.run();
    }
    private void bindInput(String key, UiTextInput input, Object value) {
        inputs.put(key, input); stagedText.put(key, input.getText().toString()); stagedValues.put(key, value);
        watch(input, () -> { if (!updating) {
            if (key.equals("name")) unfinishedName = null;
            input.setErrorMessage(null); changed.run();
        } });
        input.setEditActions(() -> commitFields(List.of(key)), null);
    }
    private boolean pending(String key) {
        return key.equals("name") && unfinishedName != null
                || !inputs.get(key).getText().toString().equals(stagedText.get(key));
    }
    boolean hasPendingChanges() {
        return inputs.keySet().stream().anyMatch(this::pending);
    }
    /** Prevent teardown blur from publishing unfinished input. */
    void dispose() { disposed = true; updating = true; }
    private boolean commitFields(Collection<String> keys) {
        if (disposed) return false;
        Map<String, Object> parsed = new LinkedHashMap<>(); boolean valid = true;
        for (String key : keys) {
            // Base attributes do not describe the effective value after modifiers. Unedited
            // numbers must not block an unrelated tag/data edit or be rewritten by parsing.
            if (!pending(key)) continue;
            UiTextInput input = inputs.get(key);
            if (key.equals("name")) {
                String raw = input.getText().toString();
                Object current = draft.dictionary("attributes").get(key);
                Object value = raw.equals(RichTextValue.from(current).plain()) ? current
                        : current instanceof String ? raw : RichTextValue.plain(raw);
                try { EntityEditorNames.validate(value); parsed.put(key, value); input.setErrorMessage(null); }
                catch (IllegalArgumentException bad) { input.setErrorMessage(tr("error.invalid_name")); valid = false; }
                continue;
            }
            String raw = input.getText().toString().trim();
            try {
                double number = Double.parseDouble(raw);
                if (!Double.isFinite(number) || number < 0
                        || (key.equals("maxHealth") || key.equals("health")) && number <= 0)
                    throw new IllegalArgumentException();
                Object original = draft.dictionary("attributes").get(key);
                Number value;
                if (original instanceof Float) {
                    float v = Float.parseFloat(raw);
                    if (!Float.isFinite(v) || (key.equals("health") || key.equals("maxHealth")) && v <= 0)
                        throw new IllegalArgumentException();
                    value = v;
                }
                else value = number;
                parsed.put(key, value); input.setErrorMessage(null);
            } catch (IllegalArgumentException bad) { input.setErrorMessage(tr("error.invalid_number")); valid = false; }
        }
        // The server validates health against the projected maximum including all modifiers.
        if (!valid) return false;
        if (parsed.isEmpty()) return true;
        Map<String, Object> attributes = new LinkedHashMap<>(draft.dictionary("attributes"));
        attributes.putAll(parsed);
        for (var entry : parsed.entrySet()) {
            if (entry.getKey().equals("name")) unfinishedName = null;
            stagedText.put(entry.getKey(), inputs.get(entry.getKey()).getText().toString());
            stagedValues.put(entry.getKey(), entry.getValue());
        }
        boolean different = !EntityEditorChanges.same(draft.dictionary("attributes"), attributes);
        draft.set("attributes", attributes);
        if (!different) changed.run(); // A textual edit can finish without changing its numeric value.
        return true;
    }
    /** Reconcile existing controls without replacing focused views or uncommitted text. */
    void refreshConfirmedValues() {
        if (disposed) return;
        Map<String, Object> attributes = draft.dictionary("attributes");
        updating = true;
        try {
            for (var entry : inputs.entrySet()) {
                String key = entry.getKey(); UiTextInput input = entry.getValue();
                if (pending(key) || !attributes.containsKey(key)) continue;
                Object value = attributes.get(key);
                if (EntityEditorChanges.same(stagedValues.get(key), value)) continue;
                String text = key.equals("name") ? RichTextValue.from(value).plain() : String.valueOf(value);
                int start = input.getSelectionStart(), end = input.getSelectionEnd();
                if (!input.getText().toString().equals(text)) {
                    input.setText(text);
                    if (start >= 0 && end >= 0) input.setSelection(Math.min(start, text.length()), Math.min(end, text.length()));
                }
                stagedText.put(key, text); stagedValues.put(key, value); input.setErrorMessage(null);
            }
            flags.forEach((key, checkbox) -> {
                if (attributes.get(key) instanceof Boolean value && checkbox.isChecked() != value) checkbox.setChecked(value);
            });
            refreshSummary();
        } finally { updating = false; }
    }
}
