package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.persistence.config.ConfigDraft;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

final class PathListConfigEntryEditor extends AbstractConfigEntryEditor<List<String>> {
    private final LinearLayout mRows;
    private final TextView mAdd;

    private PathListConfigEntryEditor(Context context, ConfigDraft draft, ConfigEntry<List<String>> entry,
                                      SettingsEditorEnvironment environment) {
        super(context, draft, entry, environment);
        mRows = new LinearLayout(context);
        mRows.setOrientation(VERTICAL);
        mControlHost.addView(mRows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mAdd = text(context, "+  " + tr("geometry_node.settings.action.add_path"), 10.5f, SettingsEditorStyle.TEXT);
        mAdd.setGravity(Gravity.CENTER);
        mAdd.setFocusable(true);
        mAdd.setFocusableInTouchMode(true);
        mAdd.setBackground(SettingsEditorStyle.rect(SettingsEditorStyle.CONTROL, 3.0f, SettingsEditorStyle.BORDER));
        mAdd.setOnClickListener(view -> mEnvironment.requestDirectory(mAdd, this::addPath));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(130), UIUtils.dp2pxInt(28));
        addParams.topMargin = UIUtils.dp2pxInt(4);
        mControlHost.addView(mAdd, addParams);
    }

    @SuppressWarnings("unchecked")
    static ConfigEntryEditor<?> create(Context context, ConfigDraft draft, ConfigEntry<?> entry,
                                       SettingsEditorEnvironment environment) {
        return new PathListConfigEntryEditor(context, draft, (ConfigEntry<List<String>>) entry, environment);
    }

    @Override public void refresh() {
        mRows.removeAllViews();
        for (String path : mDraft.get(mEntry)) mRows.addView(createRow(path));
        setValidation(true, "");
    }

    private LinearLayout createRow(String path) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView value = text(getContext(), path, 10.0f, SettingsEditorStyle.TEXT);
        value.setSingleLine(true);
        value.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        value.setBackground(SettingsEditorStyle.rect(SettingsEditorStyle.CONTROL, 3.0f, SettingsEditorStyle.BORDER));
        row.addView(value, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
        FrameLayout remove = SettingsEditorStyle.iconButton(getContext(), SvgIconView.Icon.CLOSE,
                tr("geometry_node.common.delete"));
        remove.setOnClickListener(view -> removePath(path));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(30), UIUtils.dp2pxInt(28));
        removeParams.leftMargin = UIUtils.dp2pxInt(4);
        row.addView(remove, removeParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(28));
        rowParams.bottomMargin = UIUtils.dp2pxInt(4);
        row.setLayoutParams(rowParams);
        return row;
    }

    private void addPath(String path) {
        if (path == null || path.isBlank()) return;
        List<String> paths = new ArrayList<>(mDraft.get(mEntry));
        paths.add(path);
        mDraft.set(mEntry, paths);
        refresh();
        notifyStateChanged();
    }

    private void removePath(String path) {
        List<String> paths = new ArrayList<>(mDraft.get(mEntry));
        paths.remove(path);
        mDraft.set(mEntry, paths);
        refresh();
        notifyStateChanged();
    }
}
