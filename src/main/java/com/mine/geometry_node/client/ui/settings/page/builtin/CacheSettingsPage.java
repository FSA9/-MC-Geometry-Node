package com.mine.geometry_node.client.ui.settings.page.builtin;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableColumns;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.composite.table.UiTable;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableColumn;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableItems;
import com.mine.geometry_node.client.platform.ClientServerIdentity;
import com.mine.geometry_node.core.cache.CacheClearRequest;
import com.mine.geometry_node.core.cache.CacheClearResult;
import com.mine.geometry_node.core.cache.CacheDescriptor;
import com.mine.geometry_node.core.cache.CacheRegistry;
import com.mine.geometry_node.core.cache.CacheReport;
import com.mine.geometry_node.core.cache.CacheStats;
import net.minecraft.client.Minecraft;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
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
import icyllis.modernui.widget.FrameLayout;
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
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class CacheSettingsPage implements SettingsPage {
    interface CacheOperations {
        CompletionStage<List<CacheReport>> inspect();
        /** A null ID selects the client/common bulk scope; an ID selects one locally registered category. */
        CompletionStage<List<CacheClearResult>> clear(String id, CacheClearRequest request);
        CompletionStage<String> currentNamespace();
    }

    private static final Predicate<CacheDescriptor> CLEARABLE_CACHES =
            descriptor -> descriptor.domain() != CacheDescriptor.Domain.SERVER;

    private static final int COLOR_GROUP = UiThemes.EDITOR.surface().panel();
    private static final int COLOR_HEADER = UiThemes.EDITOR.surface().header();
    private static final int COLOR_BORDER = UiThemes.EDITOR.border().divider();
    private static final int COLOR_TEXT = UiThemes.EDITOR.text().normal();
    private static final int COLOR_MUTED = UiThemes.EDITOR.text().muted();

    private final ScrollView root;
    private final CacheOperations cache;
    private final Executor uiExecutor;
    private final SettingsPageContext context;
    private final TextView totalsValue;
    private final UiActionButton refreshButton;
    private final LinearLayout detailsGroup;
    private final UiTable detailsBody;
    private final UiTableItems<String, CacheReport> reportItems;
    private final SvgIconView detailsDisclosure;
    private final UiTableColumns columns = new UiTableColumns(List.of(
            new UiTableColumns.Column("name", 260, 150, true),
            new UiTableColumns.Column("domain", 110, 85, true),
            new UiTableColumns.Column("size", 160, 120, true),
            new UiTableColumns.Column("clear", 40, 40, false)));
    private final List<UiIconButton> rowClearButtons = new ArrayList<>();
    private boolean detailsExpanded;
    private boolean detailsSearchExpanded;
    private boolean sizeDescending = true;
    private List<CacheReport> reports = List.of();
    private String searchQuery = "";
    private final TextView statusValue;
    private final UiActionButton clearCurrent;
    private final UiActionButton clearAll;
    private boolean clearing;
    private long usageRequest;
    private boolean disposed;

    public CacheSettingsPage(SettingsPageContext context) {
        this(context, new CacheOperations() {
            @Override public CompletionStage<List<CacheReport>> inspect() {
                return CacheRegistry.GLOBAL.inspect(descriptor -> true);
            }
            @Override public CompletionStage<List<CacheClearResult>> clear(String id, CacheClearRequest request) {
                return CacheRegistry.GLOBAL.clear(id == null ? CLEARABLE_CACHES
                        : descriptor -> descriptor.id().equals(id), request);
            }
            @Override public CompletionStage<String> currentNamespace() {
                var result = new CompletableFuture<String>();
                Minecraft.getInstance().execute(() -> {
                    try {
                        Minecraft minecraft = Minecraft.getInstance();
                        if (minecraft.getConnection() == null || minecraft.level == null) {
                            throw new IllegalStateException("No active server connection");
                        }
                        result.complete(ClientServerIdentity.current());
                    } catch (RuntimeException error) {
                        result.completeExceptionally(error);
                    }
                });
                return result;
            }
        }, MuiModApi::postToUiThread);
    }

    CacheSettingsPage(SettingsPageContext context, CacheOperations cache, Executor uiExecutor) {
        this.context = context;
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

        LinearLayout actions = new LinearLayout(context.uiContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        refreshButton = UiActionButton.create(context.uiContext(), tr(KEY + "refresh"),
                UiActionButton.Role.SECONDARY, view -> refreshUsage());
        clearCurrent = UiActionButton.create(context.uiContext(), tr(KEY + "clear_current"),
                UiActionButton.Role.SECONDARY, view -> runClear(() -> cache.currentNamespace().thenComposeAsync(namespace -> {
                    if (disposed) return CompletableFuture.completedFuture(List.of());
                    if (namespace == null || namespace.isBlank()) {
                        return CompletableFuture.failedFuture(new IllegalStateException("No active server identity"));
                    }
                    return cache.clear(null, CacheClearRequest.user(namespace));
                }, uiExecutor)));
        clearAll = UiActionButton.create(context.uiContext(), tr(KEY + "clear_all"),
                UiActionButton.Role.DANGER, view -> runClear(() -> cache.clear(null, CacheClearRequest.user())));
        for (UiActionButton button : List.of(refreshButton, clearCurrent, clearAll)) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(28), 1.0f);
            params.leftMargin = button == refreshButton ? 0 : UIUtils.dp2pxInt(6);
            actions.addView(button, params);
        }
        totalsValue = text(context, tr(KEY + "loading"), 10.0f, COLOR_TEXT);
        statusValue = text(context, "", 9.5f, COLOR_MUTED);
        statusValue.setSingleLine(false);
        content.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(34)));
        content.addView(totalsValue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));
        content.addView(statusValue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        detailsGroup = new LinearLayout(context.uiContext());
        detailsGroup.setOrientation(LinearLayout.VERTICAL);
        detailsGroup.setBackground(rect(COLOR_GROUP, COLOR_BORDER));
        LinearLayout detailsHeader = new LinearLayout(context.uiContext()) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        detailsHeader.setOrientation(LinearLayout.HORIZONTAL);
        detailsHeader.setGravity(Gravity.CENTER_VERTICAL);
        detailsHeader.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        UiMenuItems.decorate(detailsHeader, COLOR_HEADER, 2.0f, UiThemes.EDITOR);
        detailsDisclosure = new SvgIconView(context.uiContext(), SvgIconView.Icon.forExpandedState(false), COLOR_MUTED);
        detailsHeader.addView(detailsDisclosure, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(20), -1));
        TextView detailsTitle = text(context, tr(KEY + "details"), 10.5f, COLOR_TEXT);
        detailsTitle.setDuplicateParentStateEnabled(true);
        detailsTitle.setTextColor(UiMenuItems.textColors(COLOR_TEXT, UiThemes.EDITOR));
        detailsHeader.addView(detailsTitle, new LinearLayout.LayoutParams(0, -1, 1));
        detailsHeader.setOnClickListener(view -> {
            detailsExpanded = !detailsExpanded;
            updateDetailsExpansion();
        });
        detailsGroup.addView(detailsHeader, new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(30)));
        LinearLayout body = UiTableItems.body(context.uiContext());
        detailsBody = new UiTable(context.uiContext(), UiThemes.EDITOR, columns, List.of(
                UiTableColumn.label("name", tr(KEY + "name")),
                UiTableColumn.label("domain", tr(KEY + "type")),
                UiTableColumn.interactive("size", tr(KEY + "size"), this::toggleSizeOrder, null),
                UiTableColumn.label("clear", "")), body, UiTable.Layout.CONTENT);
        detailsBody.setSort("size", 2);
        reportItems = new UiTableItems<>(detailsBody, report -> report.descriptor().id(), report -> new ReportRow());
        detailsGroup.addView(detailsBody, new LinearLayout.LayoutParams(-1, -2));
        updateDetailsExpansion();
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2);
        detailsParams.topMargin = UIUtils.dp2pxInt(12);
        content.addView(detailsGroup, detailsParams);
        refreshUsage();
    }

    private static final String KEY = "geometry_node.settings.cache.";

    private void runClear(Supplier<CompletionStage<List<CacheClearResult>>> operation) {
        if (disposed || clearing) return;
        clearing = true;
        ++usageRequest; // A pre-clear snapshot must never overwrite post-clear data.
        setActionsEnabled(false);
        statusValue.setText(tr(KEY + "clearing"));
        try {
            operation.get().whenCompleteAsync(this::finishClear, uiExecutor);
        } catch (RuntimeException exception) {
            finishClear(null, exception);
        }
    }

    private void setActionsEnabled(boolean enabled) {
        clearCurrent.setEnabled(enabled);
        clearAll.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        for (UiIconButton button : rowClearButtons) {
            button.setEnabled(enabled && Boolean.TRUE.equals(button.getTag()));
        }
    }

    private void finishClear(List<CacheClearResult> results, Throwable error) {
        if (disposed) return;
        clearing = false;
        setActionsEnabled(true);
        if (error != null) {
            statusValue.setText(tr(KEY + "clear_failed"));
        } else {
            long cleared = 0, skipped = 0, failed = 0, retained = 0;
            boolean retainedUnknown = false;
            StringBuilder details = new StringBuilder();
            for (CacheClearResult result : results) {
                cleared = saturatedAdd(cleared, result.cleared());
                skipped = saturatedAdd(skipped, result.skipped());
                failed = saturatedAdd(failed, result.errors().size());
                if (result.retainedEntries() < 0) retainedUnknown = true;
                else retained = saturatedAdd(retained, result.retainedEntries());
                if (!result.errors().isEmpty()) details.append('\n').append(cacheName(result.descriptor()))
                        .append(": ").append(String.join("; ", result.errors()));
            }
            statusValue.setText(Component.translatable(KEY + "clear_result", cleared, skipped, failed,
                    formatCount(retainedUnknown ? -1 : retained)).getString() + details);
        }
        refreshUsage();
    }

    private void refreshUsage() {
        if (disposed || clearing) return;
        long request = ++usageRequest;
        try {
            cache.inspect().whenCompleteAsync((values, error) -> {
                if (disposed || request != usageRequest) return;
                if (error != null) {
                    totalsValue.setText(tr(KEY + "inspect_failed"));
                    return;
                }
                reports = List.copyOf(values);
                rebuildReports();
                applySearch(searchQuery);
            }, uiExecutor);
        } catch (RuntimeException exception) {
            totalsValue.setText(tr(KEY + "inspect_failed"));
        }
    }

    private void rebuildReports() {
        reports = reports.stream().sorted(usageOrder(sizeDescending)).toList();
        detailsBody.setSort("size", sizeDescending ? 2 : 1);
        long memory = 0, disk = 0;
        boolean memoryUnknown = false, diskUnknown = false;
        for (CacheReport report : reports) {
            CacheStats stats = report.stats();
            if (stats.memoryBytes() < 0) memoryUnknown = true;
            else memory = saturatedAdd(memory, stats.memoryBytes());
            if (stats.diskBytes() < 0) diskUnknown = true;
            else disk = saturatedAdd(disk, stats.diskBytes());
        }
        totalsValue.setText(Component.translatable(KEY + "total", reports.size(), partialBytes(memory, memoryUnknown),
                partialBytes(disk, diskUnknown)).getString());
    }

    private void toggleSizeOrder() {
        sizeDescending = detailsBody.toggleSort("size").direction() == 2;
        rebuildReports();
        applySearch(searchQuery);
    }

    /** Reused by cache ID; actions always read the report from the latest bind. */
    private final class ReportRow implements UiTableItems.Row<CacheReport> {
        private final TextView name = UiTableStyle.cell(context.uiContext(), "", COLOR_TEXT);
        private final TextView domain = UiTableStyle.cell(context.uiContext(), "", COLOR_MUTED);
        private final TextView size = UiTableStyle.cell(context.uiContext(), "", COLOR_TEXT);
        private final UiIconButton clear = UiIconButton.createThemed(context.uiContext(), UiThemes.EDITOR,
                SvgIconView.Icon.CLEAR, tr(KEY + "clear"), UiIconButton.Preset.INLINE);
        private final UiTableRow row;
        private CacheReport report;

        private ReportRow() {
            FrameLayout action = new FrameLayout(context.uiContext());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    UIUtils.dp2pxInt(24), UIUtils.dp2pxInt(24), Gravity.CENTER);
            action.addView(clear, params);
            row = detailsBody.createRow(id -> switch (id) {
                case "name" -> name;
                case "domain" -> domain;
                case "size" -> size;
                case "clear" -> action;
                default -> throw new IllegalArgumentException("Unknown cache column: " + id);
            });
            clear.setOnClickListener(view -> {
                CacheReport current = report;
                if (current != null && current.clearable()) runClear(() ->
                        cache.clear(current.descriptor().id(), CacheClearRequest.user()));
            });
            rowClearButtons.add(clear);
        }

        @Override public View view() { return row; }

        @Override public void bind(CacheReport item, int index) {
            report = item;
            name.setText(cacheName(item.descriptor()));
            domain.setText(tr(KEY + "domain." + item.descriptor().domain().name().toLowerCase(Locale.ROOT)));
            size.setText(displaySize(item));
            CacheStats stats = item.stats();
            size.setTooltipText(hasError(item) ? tr(KEY + "inspect_failed") + ": " + item.error()
                    : Component.translatable(KEY + "size_breakdown", formatBytes(stats.memoryBytes()),
                            formatBytes(stats.diskBytes())).getString());
            boolean actionable = index >= 0 && item.clearable();
            clear.setTag(actionable);
            clear.setEnabled(!clearing && actionable);
        }

        @Override public void dispose() {
            report = null;
            rowClearButtons.remove(clear);
        }
    }

    /** Incomplete/failed measurements remain last in both directions; ties have a stable identity order. */
    static Comparator<CacheReport> usageOrder(boolean descending) {
        return (left, right) -> {
            long a = measuredSize(left), b = measuredSize(right);
            if ((a < 0) != (b < 0)) return a < 0 ? 1 : -1;
            int sizeOrder = a < 0 ? 0 : descending ? Long.compare(b, a) : Long.compare(a, b);
            return sizeOrder != 0 ? sizeOrder : left.descriptor().id().compareTo(right.descriptor().id());
        };
    }

    private static long measuredSize(CacheReport report) {
        CacheStats stats = report.stats();
        return stats.memoryBytes() < 0 || stats.diskBytes() < 0 || hasError(report) ? -1
                : saturatedAdd(stats.memoryBytes(), stats.diskBytes());
    }

    private static boolean hasError(CacheReport report) {
        return report.error() != null && !report.error().isBlank();
    }

    private static String displaySize(CacheReport report) {
        if (hasError(report)) return tr(KEY + "unknown");
        CacheStats stats = report.stats();
        return partialBytes(saturatedAdd(Math.max(0, stats.memoryBytes()), Math.max(0, stats.diskBytes())),
                stats.memoryBytes() < 0 || stats.diskBytes() < 0);
    }

    private void updateDetailsExpansion() {
        boolean visible = detailsExpanded || detailsSearchExpanded;
        detailsBody.setVisibility(visible ? View.VISIBLE : View.GONE);
        detailsDisclosure.setIcon(SvgIconView.Icon.forExpandedState(visible));
    }

    static long saturatedAdd(long first, long second) {
        return Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second;
    }

    private static String partialBytes(long known, boolean unknown) {
        if (!unknown) return formatBytes(known);
        if (known == 0) return tr(KEY + "unknown");
        return Component.translatable(KEY + "partial", formatBytes(known)).getString();
    }

    private static String formatCount(long value) {
        return value < 0 ? tr(KEY + "unknown") : Long.toString(value);
    }

    private static String cacheName(CacheDescriptor descriptor) {
        String name = tr(descriptor.translationKey());
        return name.equals(descriptor.translationKey()) ? descriptor.id() : name;
    }

    @Override public View getView() { return root; }
    @Override public List<ConfigEntryEditor<?>> editors() { return List.of(); }

    @Override
    public void refresh() {
        SettingsPage.super.refresh();
        refreshUsage();
    }

    @Override
    public boolean applySearch(String query) {
        String value = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        searchQuery = value;
        boolean registryMatches = value.isEmpty() || (tr("geometry_node.settings.page.cache") + " " + tr(KEY + "details"))
                .toLowerCase(Locale.ROOT).contains(value);
        Predicate<CacheReport> visible = report -> {
            CacheDescriptor descriptor = report.descriptor();
            return registryMatches || (cacheName(descriptor) + " " + descriptor.id()
                    + " " + tr(KEY + "domain." + descriptor.domain().name().toLowerCase(Locale.ROOT)))
                    .toLowerCase(Locale.ROOT).contains(value);
        };
        reportItems.replace(reports, visible);
        boolean anyRowMatches = reports.stream().anyMatch(visible);
        detailsGroup.setVisibility(registryMatches || anyRowMatches ? View.VISIBLE : View.GONE);
        detailsSearchExpanded = !value.isEmpty() && (registryMatches || anyRowMatches);
        updateDetailsExpansion();
        return registryMatches || anyRowMatches;
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        ++usageRequest;
        reports = List.of();
        reportItems.dispose();
        detailsBody.dispose();
        rowClearButtons.clear();
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
        if (bytes < 0) return tr(KEY + "unknown");
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
