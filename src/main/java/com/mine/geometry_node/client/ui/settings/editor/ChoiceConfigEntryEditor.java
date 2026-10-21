package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.config.ConfigDraft;
import com.mine.geometry_node.client.config.ConfigEntry;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import java.util.List;

final class ChoiceConfigEntryEditor extends AbstractConfigEntryEditor<String> {
    private final TextView mButton;

    private ChoiceConfigEntryEditor(Context context, ConfigDraft draft, ConfigEntry<String> entry,
                                    SettingsEditorEnvironment environment) {
        super(context, draft, entry, environment);
        mButton = SettingsEditorStyle.actionButton(context, "");
        mButton.setGravity(Gravity.CENTER_VERTICAL);
        mButton.setPadding(UIUtils.dp2pxInt(9), 0, UIUtils.dp2pxInt(9), 0);
        mButton.setFocusable(true);
        mButton.setFocusableInTouchMode(true);
        mButton.setOnClickListener(view -> openChoices());
        mControlHost.addView(mButton, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(220), UIUtils.dp2pxInt(28)));
    }

    @SuppressWarnings("unchecked")
    static ConfigEntryEditor<?> create(Context context, ConfigDraft draft, ConfigEntry<?> entry,
                                       SettingsEditorEnvironment environment) {
        return new ChoiceConfigEntryEditor(context, draft, (ConfigEntry<String>) entry, environment);
    }

    @Override public void refresh() {
        mButton.setText(label(mDraft.get(mEntry)) + "  v");
        setValidation(true, "");
    }

    private void openChoices() {
        String selected = mDraft.get(mEntry);
        List<SettingsChoice> choices = mEntry.choices().stream()
                .map(value -> new SettingsChoice(value, label(value))).toList();
        if (mEnvironment.showChoices(mButton, choices, selected, this::select)) return;
        int index = mEntry.choices().indexOf(selected);
        select(mEntry.choices().get((index + 1) % mEntry.choices().size()));
    }

    private void select(String value) {
        mDraft.set(mEntry, value);
        refresh();
        notifyStateChanged();
    }

    private String label(String value) {
        String key = mEntry.choiceTranslationKey(value);
        return key.isBlank() ? value : tr(key);
    }
}
