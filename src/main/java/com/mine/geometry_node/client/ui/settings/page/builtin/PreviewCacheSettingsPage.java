package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.asset.preview.ClientAssetPreviewService;
import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.settings.editor.ConfigEntryEditor;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPage;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageContext;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class PreviewCacheSettingsPage implements SettingsPage {
    private static final int COLOR_GROUP = 0xFF242424;
    private static final int COLOR_HEADER = 0xFF292929;
    private static final int COLOR_BORDER = 0xFF444444;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF929292;

    private final ScrollView root;
    private final LinearLayout group;
    private final LinearLayout entriesHost;
    private final TextView disclosure;
    private final TextView usageValue;
    private final TextView statusValue;
    private final List<ConfigEntryEditor<?>> editors = new ArrayList<>();
    private boolean expanded;
    private boolean disposed;

    public PreviewCacheSettingsPage(SettingsPageContext context) {
        root = new ScrollView(context.uiContext());
        root.setFillViewport(true);
        LinearLayout content = new LinearLayout(context.uiContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = UIUtils.dp2pxInt(12);
        content.setPadding(padding, padding, padding, padding);
        root.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        group = new LinearLayout(context.uiContext());
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(rect(COLOR_GROUP, COLOR_BORDER));
        LinearLayout header = new LinearLayout(context.uiContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        header.setBackground(rect(COLOR_HEADER, 0));
        disclosure = text(context, ">", 11.0f, COLOR_MUTED);
        disclosure.setGravity(Gravity.CENTER);
        header.addView(disclosure, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(20),
                ViewGroup.LayoutParams.MATCH_PARENT));
        TextView title = text(context, tr("geometry_node.settings.category.preview_cache"), 10.5f, COLOR_TEXT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        titleParams.leftMargin = UIUtils.dp2pxInt(3);
        header.addView(title, titleParams);
        header.setOnClickListener(view -> setExpanded(!expanded));
        group.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(30)));

        entriesHost = new LinearLayout(context.uiContext());
        entriesHost.setOrientation(LinearLayout.VERTICAL);
        entriesHost.setPadding(UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(3),
                UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(8));
        entriesHost.setVisibility(View.GONE);
        group.addView(entriesHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addEditor(context, BuiltinConfigEntries.PREVIEW_MAX_SIZE_MIB);
        addEditor(context, BuiltinConfigEntries.PREVIEW_LOCATION);
        usageValue = addInfoRow(context, tr("geometry_node.settings.preview_cache.usage"), "...");
        statusValue = text(context, "", 9.5f, COLOR_MUTED);
        entriesHost.addView(statusValue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(24)));

        LinearLayout actions = new LinearLayout(context.uiContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        UiActionButton clearCurrent = UiActionButton.create(context.uiContext(),
                tr("geometry_node.settings.preview_cache.clear_current"), UiActionButton.Role.SECONDARY,
                view -> clearCurrentServerCache());
        UiActionButton clearAll = UiActionButton.create(context.uiContext(),
                tr("geometry_node.settings.preview_cache.clear_all"), UiActionButton.Role.DANGER,
                view -> runClear(ClientAssetPreviewService.INSTANCE.clearAllCaches()));
        actions.addView(clearCurrent, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f));
        LinearLayout.LayoutParams clearAllParams = new LinearLayout.LayoutParams(
                0, UIUtils.dp2pxInt(28), 1.0f);
        clearAllParams.leftMargin = UIUtils.dp2pxInt(6);
        actions.addView(clearAll, clearAllParams);
        entriesHost.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(34)));
        content.addView(group, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshUsage();
    }

    private void addEditor(SettingsPageContext context,
                           com.mine.geometry_node.client.ui.persistence.config.ConfigEntry<?> entry) {
        ConfigEntryEditor<?> editor = context.editorFactory().create(context.uiContext(), context.draft(), entry,
                context.editorEnvironment());
        editor.setOnStateChangedListener(context.onStateChanged());
        editors.add(editor);
        entriesHost.addView(editor.getView(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView addInfoRow(SettingsPageContext context, String label, String value) {
        LinearLayout row = new LinearLayout(context.uiContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = text(context, label, 10.5f, COLOR_TEXT);
        TextView valueView = text(context, value, 9.5f, COLOR_MUTED);
        valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(labelView, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(30), 0.32f));
        row.addView(valueView, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(30), 0.68f));
        entriesHost.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(30)));
        return valueView;
    }

    private void runClear(CompletableFuture<Void> operation) {
        statusValue.setText(tr("geometry_node.settings.preview_cache.clearing"));
        operation.whenComplete((ignored, error) -> MuiModApi.postToUiThread(() -> {
            if (disposed) return;
            statusValue.setText(tr(error == null
                    ? "geometry_node.settings.preview_cache.cleared"
                    : "geometry_node.settings.preview_cache.clear_failed"));
            refreshUsage();
        }));
    }

    private void clearCurrentServerCache() {
        try {
            runClear(ClientAssetPreviewService.INSTANCE.clearCurrentServerCache());
        } catch (RuntimeException exception) {
            statusValue.setText(tr("geometry_node.settings.preview_cache.clear_failed"));
        }
    }

    private void refreshUsage() {
        ClientAssetPreviewService.INSTANCE.cacheSizeBytes().whenComplete((bytes, error) ->
                MuiModApi.postToUiThread(() -> {
                    if (disposed) return;
                    usageValue.setText(error == null ? formatBytes(bytes) : "-");
                }));
    }

    private void setExpanded(boolean value) {
        expanded = value;
        entriesHost.setVisibility(value ? View.VISIBLE : View.GONE);
        disclosure.setText(value ? "v" : ">");
        if (value) refreshUsage();
    }

    @Override public View getView() { return root; }
    @Override public List<ConfigEntryEditor<?>> editors() { return List.copyOf(editors); }

    @Override
    public void refresh() {
        SettingsPage.super.refresh();
        refreshUsage();
    }

    @Override
    public boolean applySearch(String query) {
        String value = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        boolean matches = value.isEmpty() || (tr("geometry_node.settings.category.preview_cache") + " "
                + tr("geometry_node.settings.preview_cache.location") + " "
                + tr("geometry_node.settings.preview_cache.usage")).toLowerCase(Locale.ROOT).contains(value);
        group.setVisibility(matches ? View.VISIBLE : View.GONE);
        if (!value.isEmpty() && matches) setExpanded(true);
        return matches;
    }

    @Override
    public void dispose() {
        disposed = true;
        for (ConfigEntryEditor<?> editor : editors) editor.dispose();
        editors.clear();
        root.removeAllViews();
    }

    private static TextView text(SettingsPageContext context, String value, float size, int color) {
        TextView view = UIUtils.createLockedTextView(context.uiContext(), value, size, color);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        return view;
    }

    private static ShapeDrawable rect(int color, int stroke) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(2));
        if (stroke != 0) drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(1)), stroke);
        return drawable;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024.0) return String.format(Locale.ROOT, "%.1f KiB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024.0) return String.format(Locale.ROOT, "%.1f MiB", mib);
        return String.format(Locale.ROOT, "%.2f GiB", mib / 1024.0);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
