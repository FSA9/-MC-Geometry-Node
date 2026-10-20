package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.config.ConfigDraft;
import com.mine.geometry_node.client.config.ConfigEntry;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

final class NumberConfigEntryEditor extends AbstractConfigEntryEditor<Number> {
    private final UiTextInput mInput;
    private boolean mRefreshing;

    private NumberConfigEntryEditor(Context context, ConfigDraft draft, ConfigEntry<Number> entry,
                                    SettingsEditorEnvironment environment) {
        super(context, draft, entry, environment);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        mInput = new UiTextInput(context);
        mInput.setSingleLine(true);
        row.addView(mInput, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(150), UIUtils.dp2pxInt(28)));
        TextView range = text(context, rangeText(entry), 9.5f, SettingsEditorStyle.MUTED);
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
        rangeParams.leftMargin = UIUtils.dp2pxInt(8);
        row.addView(range, rangeParams);
        mControlHost.addView(row);
        mInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                if (!mRefreshing) commit(editable.toString());
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static ConfigEntryEditor<?> create(Context context, ConfigDraft draft, ConfigEntry<?> entry,
                                       SettingsEditorEnvironment environment) {
        return new NumberConfigEntryEditor(context, draft, (ConfigEntry) entry, environment);
    }

    @Override public void refresh() {
        mRefreshing = true;
        Number value = mDraft.get(mEntry);
        mInput.setText(format(value));
        mRefreshing = false;
        showValidity(true);
    }

    @Override public void focus() { mInput.requestFocus(); }
    @Override public boolean hasUncommittedChanges() { return !isValid(); }

    private void commit(String text) {
        try {
            Number value = parseValue(mEntry.editorType(), text);
            if (!mEntry.accepts(value)) throw new NumberFormatException();
            mDraft.set(mEntry, value);
            showValidity(true);
        } catch (Exception ignored) {
            showValidity(false);
        }
    }

    // A numeric ternary promotes Integer/Float to float before boxing, breaking
    // integer ConfigEntry validators. Keep the two return paths separate.
    static Number parseValue(ConfigEntry.EditorType type, String text) {
        if (type == ConfigEntry.EditorType.INTEGER) return Integer.valueOf(text.trim());
        return Float.valueOf(text.trim());
    }

    private void showValidity(boolean valid) {
        String message = valid ? "" : tr("geometry_node.settings.validation.number");
        mInput.setErrorMessage(message);
        setValidation(valid, message);
    }

    private static String rangeText(ConfigEntry<?> entry) {
        return format(entry.min()) + " - " + format(entry.max()) + "  (" + tr("geometry_node.settings.value.step")
                + " " + format(entry.step()) + ")";
    }

    private static String format(Number value) {
        if (value == null) return "";
        double number = value.doubleValue();
        return number == Math.rint(number) ? Long.toString((long) number) : Double.toString(number);
    }
}
