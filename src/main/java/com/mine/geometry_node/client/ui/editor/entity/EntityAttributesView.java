package com.mine.geometry_node.client.ui.editor.entity;

import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityPreviewCell;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.core.engine.system.entityeditor.EntityEditorChanges;
import icyllis.modernui.core.Context;
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
    private boolean updating;

    EntityAttributesView(Context context, UiTheme theme, EntityEditDraft draft, UUID entity, String dimension, Runnable changed) {
        super(context, theme.scroll()); this.draft = draft; this.changed = changed;
        setHorizontalScrollBarEnabled(false);
        LinearLayout body = row(context); body.setPadding(px(20), px(18), px(20), px(20));
        EntityPreviewCell preview = new EntityPreviewCell(context, 170, theme); preview.setReference(entity, dimension);
        body.addView(preview, size(200, 220));
        LinearLayout fields = column(context); body.addView(fields, new LinearLayout.LayoutParams(0, -2, 1));
        Map<String, Object> attrs = draft.dictionary("attributes");
        if (attrs.containsKey("name")) {
            fields.addView(text(context, tr("attributes.name"), theme.text().muted()), size(-1, 26));
            UiTextInput name = input(context, theme); name.setText(String.valueOf(attrs.get("name")));
            fields.addView(name, size(-1, 32)); bindInput("name", name, attrs.get("name"));
        }
        LinearLayout numberRow = null; int index = 0;
        for (String key : List.of("health", "maxHealth", "speed", "attack")) {
            if (!(attrs.get(key) instanceof Number number)) continue;
            if (index++ % 2 == 0) { numberRow = row(context); fields.addView(numberRow, size(-1, -2)); }
            LinearLayout field = column(context); field.setPadding(0, px(10), px(12), 0);
            field.addView(text(context, tr("attributes." + key), theme.text().muted()), size(-1, 25));
            UiTextInput input = input(context, theme); input.setText(String.valueOf(number));
            input.setContentDescription(tr("attributes." + key)); field.addView(input, size(-1, 32));
            numbers.put(key, input); bindInput(key, input, number);
            numberRow.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
        }
        fields.addView(text(context, tr("attributes.flags"), theme.text().muted()), size(-1, 42));
        for (String key : List.of("nameVisible", "noAI", "persistent", "silent", "invulnerable", "gravity")) {
            if (!(attrs.get(key) instanceof Boolean value)) continue;
            LinearLayout flag = row(context); UiCheckBox checkbox = UiCheckBox.createThemed(context, theme);
            checkbox.setChecked(value); checkbox.setContentDescription(tr("attributes." + key));
            flags.put(key, checkbox);
            checkbox.setOnCheckedChangeListener((view, checked) -> { if (!updating) draft.attribute(key, checked); });
            flag.addView(checkbox, size(16, 16));
            var label = text(context, tr("attributes." + key), theme.text().normal());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1); lp.leftMargin = px(9);
            flag.addView(label, lp); label.setOnClickListener(view -> checkbox.performClick());
            fields.addView(flag, size(-1, 32));
        }
        fields.addView(text(context, tr("attributes.description"), theme.text().muted()), size(-1, 54));
        addView(body, new FrameLayout.LayoutParams(-1, -2));
    }
    private void bindInput(String key, UiTextInput input, Object value) {
        inputs.put(key, input); stagedText.put(key, input.getText().toString()); stagedValues.put(key, value);
        watch(input, () -> { if (!updating) { input.setErrorMessage(null); changed.run(); } });
        input.setEditActions(() -> commitFields(List.of(key)), null);
    }
    private boolean pending(String key) {
        return !inputs.get(key).getText().toString().equals(stagedText.get(key));
    }
    boolean hasPendingChanges() {
        return inputs.keySet().stream().anyMatch(this::pending);
    }
    /** Prevent teardown blur from publishing unfinished input. */
    void dispose() { updating = true; }
    private boolean commitFields(Collection<String> keys) {
        Map<String, Object> parsed = new LinkedHashMap<>(); boolean valid = true;
        for (String key : keys) {
            // Base attributes do not describe the effective value after modifiers. Unedited
            // numbers must not block an unrelated tag/data edit or be rewritten by parsing.
            if (!pending(key)) continue;
            UiTextInput input = inputs.get(key);
            if (key.equals("name")) { parsed.put(key, input.getText().toString()); continue; }
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
        Map<String, Object> attributes = draft.dictionary("attributes");
        updating = true;
        try {
            for (var entry : inputs.entrySet()) {
                String key = entry.getKey(); UiTextInput input = entry.getValue();
                if (pending(key) || !attributes.containsKey(key)) continue;
                Object value = attributes.get(key);
                if (EntityEditorChanges.same(stagedValues.get(key), value)) continue;
                String text = String.valueOf(value);
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
        } finally { updating = false; }
    }
}
