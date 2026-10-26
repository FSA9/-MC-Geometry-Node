package com.mine.geometry_node.client.ui.components.integration.entity.editor;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.composite.overlay.ExpandedTextInputOverlay;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityPreviewCell;
import com.mine.geometry_node.client.ui.components.integration.entity.EntityRegistrationView;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.core.engine.system.entity.edit.EntityEditorChanges;
import com.mine.geometry_node.core.engine.system.entity.edit.EntityEditorNames;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import net.minecraft.network.chat.Component;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.*;
import java.util.function.BooleanSupplier;
import com.mine.geometry_node.core.engine.system.entity.classification.EntityTypeTraits;
import static com.mine.geometry_node.client.ui.components.integration.entity.editor.EntityEditorViews.*;

/** Only advertised fields are editable. Enter completes text edits; navigation preserves raw input. */
final class EntityAttributesView extends LinearLayout {
    private static final List<String> CATEGORIES = java.util.stream.Stream.concat(
            EntityAttributeFields.CATEGORIES.stream(), java.util.stream.Stream.of("types")).toList();
    private final EntityEditDraft draft;
    private final EntityTypesView biologicalTypes;
    private final EntityRegistrationView registration;
    private final EntityAppearanceFieldView appearance;
    private final Map<String, UiTextInput> numbers = new LinkedHashMap<>();
    private final Map<String, UiTextInput> inputs = new LinkedHashMap<>();
    private final Map<String, UiCheckBox> flags = new LinkedHashMap<>();
    private final Map<String, String> stagedText = new HashMap<>();
    private final Map<String, Object> stagedValues = new HashMap<>();
    private final Runnable changed;
    private final UiTheme theme;
    private final Map<String, EntityAttributeFields.Field> fields = new LinkedHashMap<>();
    private final Map<String, LinearLayout> fieldRows = new LinkedHashMap<>();
    private final Map<String, UiDisplayText> fieldLabels = new LinkedHashMap<>();
    private final Map<String, LinearLayout> categoryPages = new LinkedHashMap<>();
    private final Map<String, UiActionButton> categoryButtons = new LinkedHashMap<>();
    private final Map<String, UiDisplayText> categoryCounts = new LinkedHashMap<>();
    private final UiScrollView fieldScroll;
    private String selectedCategory;
    private final UiDisplayText registryValue, dimensionValue, positionValue;
    private final String dimension;
    private boolean updating;
    private boolean disposed;
    private RichTextValue unfinishedName;

    EntityAttributesView(Context context, UiTheme theme, EntityEditDraft draft, UUID entity, String dimension, Runnable changed,
                         BooleanSupplier editable) {
        this(context, theme, draft, entity, dimension, changed, editable, null);
    }

    EntityAttributesView(Context context, UiTheme theme, EntityEditDraft draft, UUID entity, String dimension, Runnable changed,
                         BooleanSupplier editable, Runnable registrationApplied) {
        super(context);
        this.draft = draft; this.changed = changed; this.dimension = dimension; this.theme = theme;
        String registryType = String.valueOf(draft.snapshot().getOrDefault("registryType", ""));
        registration = registrationApplied != null && !registryType.isEmpty() && !registryType.equals("minecraft:interaction")
                ? new EntityRegistrationView(context, theme, entity, dimension, false,
                        () -> !disposed && editable.getAsBoolean(), registrationApplied) : null;
        biologicalTypes = draft.snapshot().containsKey("biologicalTypeDefaults")
                ? new EntityTypesView(context, theme, draft, () -> !disposed && editable.getAsBoolean()) : null;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.TOP);
        UiScrollView summaryScroll = new UiScrollView(context, theme.scroll());
        summaryScroll.setFillViewport(true);
        LinearLayout summary = column(context); summary.setPadding(px(20), px(18), px(20), px(20));
        EntityPreviewCell preview = new EntityPreviewCell(context, 224, theme, EntityPreviewCell.Layout.SQUARE);
        preview.setReference(entity, dimension);
        summary.addView(preview, size(-1, -2));
        registryValue = metadata(context, theme);
        LinearLayout.LayoutParams registryParams = size(-1, -2); registryParams.topMargin = px(12);
        summary.addView(registryValue, registryParams);
        summary.addView(text(context, tr("attributes.dimension"), theme.text().muted()), size(-1, 30));
        dimensionValue = metadata(context, theme); summary.addView(dimensionValue, size(-1, -2));
        summary.addView(text(context, tr("attributes.position"), theme.text().muted()), size(-1, 30));
        positionValue = metadata(context, theme); summary.addView(positionValue, size(-1, -2));
        LinearLayout.LayoutParams separator = size(-1, 1); separator.topMargin = px(20); separator.bottomMargin = px(14);
        summary.addView(divider(context, theme), separator);
        summaryScroll.addView(summary, new FrameLayout.LayoutParams(-1, -2));
        addView(summaryScroll, size(248, -1));
        addView(divider(context, theme), size(1, -1));
        fieldScroll = new UiScrollView(context, theme.scroll());
        fieldScroll.setFillViewport(true);
        LinearLayout pages = column(context); pages.setPadding(px(24), px(22), px(24), px(24));
        fieldScroll.addView(pages, new FrameLayout.LayoutParams(-1, -2));
        addView(fieldScroll, new LinearLayout.LayoutParams(0, -1, 1));
        for (String category : CATEGORIES) {
            UiActionButton button = categoryButton(context, theme, tr("attributes.category." + category),
                    () -> selectCategory(category));
            categoryButtons.put(category, button);
            LinearLayout.LayoutParams navParams = size(-1, 38); navParams.bottomMargin = px(3);
            summary.addView(button, navParams);
            LinearLayout page = column(context);
            LinearLayout heading = row(context);
            UiDisplayText title = text(context, tr("attributes.category." + category), theme.text().normal());
            UIUtils.setLockedTextSize(title, UiMetrics.TEXT_BODY);
            heading.addView(title, new LinearLayout.LayoutParams(0, px(34), 1));
            UiDisplayText count = text(context, "", theme.text().muted());
            count.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            heading.addView(count, size(-2, 34)); categoryCounts.put(category, count);
            page.addView(heading, size(-1, 42));
            LinearLayout.LayoutParams line = size(-1, 1); line.bottomMargin = px(8);
            page.addView(divider(context, theme), line);
            categoryPages.put(category, page); pages.addView(page, size(-1, -2));
        }
        appearance = new EntityAppearanceFieldView(context, theme, draft, () -> !disposed && editable.getAsBoolean());
        categoryPages.get("appearance").addView(appearance, size(-1, -2));
        if (biologicalTypes != null) categoryPages.get("types").addView(biologicalTypes, size(-1, -2));
        if (registration != null) {
            LinearLayout line = row(context);
            line.addView(text(context, Component.translatable("geometry_node.entity_registration.register").getString(),
                    theme.text().normal()), new LinearLayout.LayoutParams(0, px(44), 1));
            line.addView(registration, new LinearLayout.LayoutParams(0, px(44), 1));
            categoryPages.get("general").addView(line, size(-1, 66));
            registration.refresh();
        }
        reconcileFields();
        refreshSummary();
    }
    private static UiDisplayText metadata(Context context, UiTheme theme) {
        UiDisplayText value = text(context, "", theme.text().secondary());
        value.setTypeface(Typeface.MONOSPACED); value.setMinHeight(px(24)); return value;
    }
    private void selectCategory(String category) {
        if (categoryButtons.get(category).getVisibility() != VISIBLE) return;
        if (!Objects.equals(selectedCategory, category)) fieldScroll.scrollTo(0, 0);
        selectedCategory = category;
        categoryPages.forEach((key, page) -> page.setVisibility(key.equals(category) ? VISIBLE : GONE));
        categoryButtons.forEach((key, button) -> button.setPalette(UiActionButton.Palette.row(theme,
                key.equals(category), theme.surface().panel())));
    }
    /** Reconcile advertised capabilities in place: an acknowledgement must not discard raw forms. */
    private void reconcileFields() {
        Map<String, Object> attributes = draft.dictionary("attributes");
        List<EntityAttributeFields.Field> advertised = EntityAttributeFields.from(draft.snapshot(), attributes);
        fields.clear();
        Map<String, Integer> counts = new HashMap<>();
        if (registration != null) counts.put("general", 1);
        if (biologicalTypes != null) counts.put("types", EntityTypeTraits.IDS.size());
        appearance.refreshConfirmed();
        if (appearance.supported()) counts.put("appearance", 1);
        for (EntityAttributeFields.Field field : advertised) {
            fields.put(field.key(), field);
            LinearLayout fieldRow = fieldRows.get(field.key());
            if (fieldRow == null) {
                fieldRow = createFieldRow(field, attributes.get(field.key()));
                fieldRows.put(field.key(), fieldRow);
            }
            LinearLayout parent = categoryPages.get(field.category());
            if (fieldRow.getParent() != parent) {
                if (fieldRow.getParent() instanceof LinearLayout previous) previous.removeView(fieldRow);
                parent.addView(fieldRow, size(-1, 66));
            }
            fieldRow.setVisibility(VISIBLE);
            String label = Component.translatable(field.labelKey()).getString();
            fieldLabels.get(field.key()).setText(label);
            View control = inputs.containsKey(field.key()) ? inputs.get(field.key()) : flags.get(field.key());
            if (control != null) control.setContentDescription(label);
            counts.merge(field.category(), 1, Integer::sum);
        }
        fieldRows.forEach((key, row) -> row.setVisibility(fields.containsKey(key) ? VISIBLE : GONE));
        categoryButtons.forEach((category, button) -> {
            int count = counts.getOrDefault(category, 0);
            button.setVisibility(count > 0 ? VISIBLE : GONE);
            categoryCounts.get(category).setText(tr("attributes.count", count));
        });
        if (selectedCategory == null || counts.getOrDefault(selectedCategory, 0) == 0) {
            selectedCategory = CATEGORIES.stream().filter(counts::containsKey).findFirst().orElse(null);
        }
        if (selectedCategory != null) selectCategory(selectedCategory);
        else categoryPages.values().forEach(page -> page.setVisibility(GONE));
    }
    private LinearLayout createFieldRow(EntityAttributeFields.Field field, Object value) {
        Context context = getContext();
        String label = Component.translatable(field.labelKey()).getString();
        LinearLayout row = row(context);
        UiDisplayText caption = text(context, label, theme.text().normal());
        fieldLabels.put(field.key(), caption);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, px(44), 1);
        labelParams.rightMargin = px(20); row.addView(caption, labelParams);
        LinearLayout control = row(context);
        row.addView(control, new LinearLayout.LayoutParams(0, px(36), 1));
        switch (field.kind()) {
            case BOOLEAN -> {
                UiCheckBox checkbox = UiCheckBox.createThemed(context, theme);
                checkbox.setChecked(Boolean.TRUE.equals(value)); checkbox.setContentDescription(label);
                flags.put(field.key(), checkbox); control.addView(checkbox, size(18, 18));
                checkbox.setOnCheckedChangeListener((view, checked) -> {
                    if (!updating && !disposed && fields.containsKey(field.key())) draft.attribute(field.key(), checked);
                });
                caption.setOnClickListener(view -> checkbox.performClick());
            }
            case NAME -> {
                UiTextInput name = input(context, theme); name.setText(RichTextValue.from(value).plain());
                control.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
                UiIconButton rich = UiIconButton.createThemed(context, theme,
                        new SvgIconView(context, SvgIconView.Icon.MORE_HORIZONTAL, theme.text().normal()))
                        .tooltip(tr("attributes.edit_rich_name"));
                rich.setEnabled(EntityEditorNames.supportsRichText(value));
                rich.setOnClickListener(view -> openRichName(theme, name));
                LinearLayout.LayoutParams action = size(36, 36); action.leftMargin = px(8);
                control.addView(rich, action); bindInput(field.key(), name, value);
            }
            case NUMBER -> {
                UiTextInput input = input(context, theme); input.setText(field.display((Number) value));
                control.addView(input, new LinearLayout.LayoutParams(0, -1, 1));
                if (field.percentage()) {
                    UiDisplayText unit = text(context, "%", theme.text().muted());
                    LinearLayout.LayoutParams unitParams = size(-2, -1); unitParams.leftMargin = px(8);
                    control.addView(unit, unitParams);
                }
                numbers.put(field.key(), input); bindInput(field.key(), input, value);
            }
        }
        return row;
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
        if (disposed || !fields.containsKey("name")) return;
        Object original = draft.dictionary("attributes").get("name");
        String raw = name.getText().toString();
        RichTextValue initial = unfinishedName != null ? unfinishedName : raw.equals(RichTextValue.from(original).plain())
                ? RichTextValue.from(original) : RichTextValue.plain(raw);
        ExpandedTextInputOverlay.showRichText(getContext(), name, initial, rich -> {
            if (disposed || !fields.containsKey("name")) return;
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
        return fields.keySet().stream().filter(inputs::containsKey).anyMatch(this::pending);
    }
    /** Prevent teardown blur from publishing unfinished input. */
    void dispose() {
        disposed = true; updating = true;
        if (registration != null) registration.dispose();
        appearance.dispose();
    }
    private boolean commitFields(Collection<String> keys) {
        if (disposed) return false;
        Map<String, Object> parsed = new LinkedHashMap<>(); boolean valid = true;
        for (String key : keys) {
            // Base attributes do not describe the effective value after modifiers. Unedited
            // numbers must not block an unrelated tag/data edit or be rewritten by parsing.
            if (!fields.containsKey(key) || !pending(key)) continue;
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
            try {
                EntityAttributeFields.Field field = fields.get(key);
                Object original = draft.dictionary("attributes").get(key);
                if (field == null || field.kind() != EntityAttributeFields.Kind.NUMBER || !(original instanceof Number number))
                    throw new IllegalArgumentException();
                Number value = field.parse(input.getText().toString(), number);
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
    void refreshInputState() {
        if (!disposed && biologicalTypes != null) biologicalTypes.refreshConfirmed();
        if (registration != null) registration.refreshInputState();
        appearance.refreshInputState();
    }
    /** Reconcile existing controls without replacing focused views or uncommitted text. */
    void refreshConfirmedValues() {
        if (disposed) return;
        Map<String, Object> attributes = draft.dictionary("attributes");
        updating = true;
        try {
            reconcileFields();
            for (var entry : inputs.entrySet()) {
                String key = entry.getKey(); UiTextInput input = entry.getValue();
                if (pending(key) || !attributes.containsKey(key)) continue;
                Object value = attributes.get(key);
                if (EntityEditorChanges.same(stagedValues.get(key), value)) continue;
                EntityAttributeFields.Field field = fields.get(key);
                if (field == null) continue;
                String text = key.equals("name") ? RichTextValue.from(value).plain()
                        : value instanceof Number number ? field.display(number) : String.valueOf(value);
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
            refreshInputState();
        } finally { updating = false; }
    }
}
