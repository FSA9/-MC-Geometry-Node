package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;

import com.mine.geometry_node.client.asset.preview.ClientAssetPreviewService;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.config.ConfigEntry;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class PreviewCacheSettingsPage implements SettingsPage {
    interface CacheOperations {
        CompletableFuture<Long> cacheSizeBytes();
        CompletableFuture<Void> clearCurrentServerCache();
        CompletableFuture<Void> clearAllCaches();
    }

    private static final int COLOR_GROUP = 0xFF242424;
    private static final int COLOR_HEADER = 0xFF292929;
    private static final int COLOR_BORDER = 0xFF444444;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF929292;

    private final ScrollView root;
    private final CacheOperations cache;
    private final Executor uiExecutor;
    private final LinearLayout group;
    private final LinearLayout entriesHost;
    private final SvgIconView disclosure;
    private final TextView usageValue;
    private final TextView statusValue;
    private final UiActionButton clearCurrent;
    private final UiActionButton clearAll;
    private final List<ConfigEntryEditor<?>> editors = new ArrayList<>();
    private boolean expanded;
    private boolean searchExpanded;
    private boolean clearing;
    private long usageRequest;
    private boolean disposed;

    public PreviewCacheSettingsPage(SettingsPageContext context) {
        this(context, new CacheOperations() {
            @Override public CompletableFuture<Long> cacheSizeBytes() {
                return ClientAssetPreviewService.INSTANCE.cacheSizeBytes();
            }
            @Override public CompletableFuture<Void> clearCurrentServerCache() {
                return ClientAssetPreviewService.INSTANCE.clearCurrentServerCache();
            }
            @Override public CompletableFuture<Void> clearAllCaches() {
                return ClientAssetPreviewService.INSTANCE.clearAllCaches();
            }
        }, MuiModApi::postToUiThread);
    }

    PreviewCacheSettingsPage(SettingsPageContext context, CacheOperations cache, Executor uiExecutor) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
        root = new UiScrollView(context.uiContext());
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
        disclosure = new SvgIconView(context.uiContext(),
                SvgIconView.Icon.forExpandedState(false), COLOR_MUTED);
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

        try {
            context.configRegistry().entries().stream()
                    .filter(ConfigEntry::isVisibleInSettings)
                    .filter(BuiltinSettingsProjection::isPreviewCache)
                    .sorted(Comparator.comparingInt((ConfigEntry<?> entry) -> entry.order()).thenComparing(ConfigEntry::id))
                    .forEach(entry -> addEditor(context, entry));
            usageValue = addInfoRow(context, tr("geometry_node.settings.preview_cache.usage"), "...");
            statusValue = text(context, "", 9.5f, COLOR_MUTED);
            entriesHost.addView(statusValue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(24)));

            LinearLayout actions = new LinearLayout(context.uiContext());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            clearCurrent = UiActionButton.create(context.uiContext(),
                    tr("geometry_node.settings.preview_cache.clear_current"), UiActionButton.Role.SECONDARY,
                    view -> runClear(cache::clearCurrentServerCache));
            clearAll = UiActionButton.create(context.uiContext(),
                    tr("geometry_node.settings.preview_cache.clear_all"), UiActionButton.Role.DANGER,
                    view -> runClear(cache::clearAllCaches));
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
        } catch (RuntimeException exception) {
            dispose();
            throw exception;
        }
    }

    private void addEditor(SettingsPageContext context,
                           ConfigEntry<?> entry) {
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

    private void runClear(Supplier<CompletableFuture<Void>> operation) {
        if (disposed || clearing) return;
        clearing = true;
        clearCurrent.setEnabled(false);
        clearAll.setEnabled(false);
        statusValue.setText(tr("geometry_node.settings.preview_cache.clearing"));
        try {
            operation.get().whenCompleteAsync((ignored, error) -> finishClear(error), uiExecutor);
        } catch (RuntimeException exception) {
            finishClear(exception);
        }
    }

    private void finishClear(Throwable error) {
        if (disposed) return;
        clearing = false;
        clearCurrent.setEnabled(true);
        clearAll.setEnabled(true);
        statusValue.setText(tr(error == null
                ? "geometry_node.settings.preview_cache.cleared"
                : "geometry_node.settings.preview_cache.clear_failed"));
        refreshUsage();
    }

    private void refreshUsage() {
        if (disposed) return;
        long request = ++usageRequest;
        try {
            cache.cacheSizeBytes().whenCompleteAsync((bytes, error) -> {
                    if (disposed || request != usageRequest) return;
                    usageValue.setText(error == null ? formatBytes(bytes) : "-");
                }, uiExecutor);
        } catch (RuntimeException exception) {
            usageValue.setText("-");
        }
    }

    private void setExpanded(boolean value) {
        expanded = value;
        updateExpansion();
    }

    private void updateExpansion() {
        boolean visible = expanded || searchExpanded;
        boolean opening = visible && entriesHost.getVisibility() != View.VISIBLE;
        entriesHost.setVisibility(visible ? View.VISIBLE : View.GONE);
        disclosure.setIcon(SvgIconView.Icon.forExpandedState(visible));
        if (opening) refreshUsage();
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
        StringBuilder searchText = new StringBuilder(tr("geometry_node.settings.category.preview_cache"));
        for (String key : List.of("usage", "clear_current", "clear_all")) {
            searchText.append('\n').append(tr("geometry_node.settings.preview_cache." + key));
        }
        for (ConfigEntryEditor<?> editor : editors) {
            ConfigEntry<?> entry = editor.entry();
            searchText.append('\n').append(entry.id())
                    .append('\n').append(tr(entry.labelTranslationKey()))
                    .append('\n').append(tr(entry.descriptionTranslationKey()));
        }
        boolean matches = value.isEmpty() || searchText.toString().toLowerCase(Locale.ROOT).contains(value);
        group.setVisibility(matches ? View.VISIBLE : View.GONE);
        searchExpanded = !value.isEmpty() && matches;
        updateExpansion();
        return matches;
    }

    @Override
    public void revealEditor(ConfigEntryEditor<?> editor) {
        if (editors.contains(editor)) {
            setExpanded(true);
            editor.focus();
        }
    }

    @Override
    public void dispose() {
        if (disposed) return;
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
