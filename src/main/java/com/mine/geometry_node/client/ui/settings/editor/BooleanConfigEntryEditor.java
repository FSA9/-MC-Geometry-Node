package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.config.ConfigDraft;
import com.mine.geometry_node.client.config.ConfigEntry;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;

final class BooleanConfigEntryEditor extends AbstractConfigEntryEditor<Boolean> {
    private final UiActionButton mToggle;

    private BooleanConfigEntryEditor(Context context, ConfigDraft draft, ConfigEntry<Boolean> entry,
                                     SettingsEditorEnvironment environment) {
        super(context, draft, entry, environment);
        mToggle = new UiActionButton(context, "", UiActionButton.Role.SECONDARY, UiActionButton.Density.NORMAL);
        mToggle.setMinWidth(0);
        mToggle.setMinHeight(0);
        mToggle.setMinimumWidth(0);
        mToggle.setMinimumHeight(0);
        mToggle.setGravity(Gravity.CENTER);
        mToggle.setOnClickListener(view -> {
            mDraft.set(mEntry, !mDraft.get(mEntry));
            refresh();
            notifyStateChanged();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(72), UIUtils.dp2pxInt(28));
        mControlHost.addView(mToggle, params);
    }

    @SuppressWarnings("unchecked")
    static ConfigEntryEditor<?> create(Context context, ConfigDraft draft, ConfigEntry<?> entry,
                                       SettingsEditorEnvironment environment) {
        return new BooleanConfigEntryEditor(context, draft, (ConfigEntry<Boolean>) entry, environment);
    }

    @Override public void refresh() {
        boolean enabled = Boolean.TRUE.equals(mDraft.get(mEntry));
        mToggle.setText(enabled ? tr("geometry_node.settings.value.enabled") : tr("geometry_node.settings.value.disabled"));
        mToggle.setPalette(UiActionButton.Palette.toggle(enabled));
        setValidationSilently(true, "");
    }
}
