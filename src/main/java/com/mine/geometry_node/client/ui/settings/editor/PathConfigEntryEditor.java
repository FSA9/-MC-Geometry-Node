package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.persistence.config.ConfigDraft;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

/** Single-directory editor backed by the shared asset-browser folder picker. */
final class PathConfigEntryEditor extends AbstractConfigEntryEditor<String> {
    private final TextView mValue;

    private PathConfigEntryEditor(Context context, ConfigDraft draft, ConfigEntry<String> entry,
                                  SettingsEditorEnvironment environment) {
        super(context, draft, entry, environment);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        mValue = text(context, "", 10.0f, SettingsEditorStyle.TEXT);
        mValue.setSingleLine(true);
        mValue.setEllipsize(TextUtils.TruncateAt.START);
        mValue.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        mValue.setBackground(SettingsEditorStyle.rect(
                SettingsEditorStyle.CONTROL, 3.0f, SettingsEditorStyle.BORDER));
        row.addView(mValue, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
        UiActionButton choose = UiActionButton.create(context,
                tr("geometry_node.settings.preview_cache.choose_location"), UiActionButton.Role.SECONDARY,
                view -> mEnvironment.requestDirectory(view, mDraft.get(mEntry), this::select));
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(96), UIUtils.dp2pxInt(28));
        chooseParams.leftMargin = UIUtils.dp2pxInt(6);
        row.addView(choose, chooseParams);
        mControlHost.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28)));
    }

    @SuppressWarnings("unchecked")
    static ConfigEntryEditor<?> create(Context context, ConfigDraft draft, ConfigEntry<?> entry,
                                       SettingsEditorEnvironment environment) {
        return new PathConfigEntryEditor(context, draft, (ConfigEntry<String>) entry, environment);
    }

    @Override
    public void refresh() {
        mValue.setText(mDraft.get(mEntry));
        setValidationSilently(true, "");
    }

    private void select(String path) {
        if (path == null || path.isBlank()) return;
        mDraft.set(mEntry, path);
        refresh();
        notifyStateChanged();
    }
}
