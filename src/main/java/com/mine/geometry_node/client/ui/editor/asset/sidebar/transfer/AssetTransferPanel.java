package com.mine.geometry_node.client.ui.editor.asset.sidebar.transfer;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanel;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelDefinition;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferFileSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class AssetTransferPanel extends FrameLayout implements SidebarPanel {
    public static final String PANEL_ID = "asset_transfers";
    public static final SidebarPanelDefinition DEFINITION = new SidebarPanelDefinition(
            PANEL_ID,
            "geometry_node.asset_transfer.panel.title",
            200,
            Set.of(SidebarPanelScope.ASSET_BROWSER),
            AssetTransferPanel::create, com.mine.geometry_node.client.ui.components.base.icon.SvgIconView.Icon.TRANSFERS);

    private static final int COLOR_BACKGROUND = UiThemes.EDITOR.surface().sidebar();
    private static final int COLOR_SECTION = UiThemes.EDITOR.surface().panel();
    private static final int COLOR_ROW = UiThemes.EDITOR.surface().sidebar();
    private static final int COLOR_ROW_ALT = UiThemes.EDITOR.surface().header();
    private static final int COLOR_BORDER = UiThemes.EDITOR.border().subtle();
    private static final int COLOR_TEXT = UiThemes.EDITOR.text().normal();
    private static final int COLOR_MUTED = UiThemes.EDITOR.text().muted();
    private static final int COLOR_UPLOAD = UiThemes.EDITOR.status().info();
    private static final int COLOR_DOWNLOAD = UiThemes.EDITOR.status().success();
    private static final int COLOR_ERROR = UiThemes.EDITOR.status().error();
    private static final int COLOR_WARNING = UiThemes.EDITOR.status().warning();
    private static final long UI_UPDATE_DELAY_MS = 100L;

    private final ScrollView scroll;
    private final LinearLayout content;
    private final Map<Section, Boolean> expandedSections = new EnumMap<>(Section.class);
    private final Map<Section, SectionContent> sections = new EnumMap<>(Section.class);
    private final Map<AssetTransferFileSnapshot, View> completedRows = new HashMap<>();
    private final Map<AssetTransferFileSnapshot, View> failedRows = new HashMap<>();
    private final View clearCompleted;
    private final LinearLayout failedActions;
    private ClientAssetTransferService.Subscription subscription;
    private long subscriptionGeneration;
    private AssetTransferSnapshot pendingSnapshot = AssetTransferSnapshot.empty();
    private boolean renderScheduled;
    private final Runnable renderPending = () -> {
        renderScheduled = false;
        if (subscription != null) render(pendingSnapshot);
    };

    public AssetTransferPanel(Context context) {
        super(context);
        setBackground(rect(COLOR_BACKGROUND, 0, 0));
        scroll = new UiScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        for (Section section : Section.values()) expandedSections.put(section, true);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        clearCompleted = iconButton(SvgIconView.Icon.CLEAR, COLOR_MUTED,
                tr("geometry_node.asset_transfer.action.clear"),
                () -> ClientAssetTransferService.INSTANCE.clearCompletedHistory());
        failedActions = new LinearLayout(context);
        failedActions.setOrientation(LinearLayout.HORIZONTAL);
        failedActions.addView(iconButton(SvgIconView.Icon.RESET, COLOR_MUTED,
                tr("geometry_node.asset_transfer.action.retry_all"),
                () -> ClientAssetTransferService.INSTANCE.retryAll()), new LinearLayout.LayoutParams(px(24), px(24)));
        failedActions.addView(iconButton(SvgIconView.Icon.CLEAR, COLOR_MUTED,
                tr("geometry_node.asset_transfer.action.clear"),
                () -> ClientAssetTransferService.INSTANCE.clearFailedHistory()), new LinearLayout.LayoutParams(px(24), px(24)));
        render(AssetTransferSnapshot.empty());
    }

    private static SidebarPanel create(SidebarPanelContext context) {
        return new AssetTransferPanel(context.uiContext());
    }

    @Override public View getView() { return this; }

    @Override
    public void onSelected() {
        if (subscription != null) return;
        long generation = ++subscriptionGeneration;
        subscription = ClientAssetTransferService.INSTANCE.subscribe(snapshot -> post(() -> {
            if (generation == subscriptionGeneration) acceptSnapshot(snapshot);
        }));
    }

    @Override
    public void onDeselected() {
        subscriptionGeneration++;
        if (subscription != null) subscription.close();
        subscription = null;
        removeCallbacks(renderPending);
        renderScheduled = false;
    }

    private void acceptSnapshot(AssetTransferSnapshot snapshot) {
        pendingSnapshot = snapshot;
        if (renderScheduled) return;
        renderScheduled = true;
        postDelayed(renderPending, UI_UPDATE_DELAY_MS);
    }

    private void render(AssetTransferSnapshot snapshot) {
        // Keep input targets attached while unrelated transfer progress changes.
        addActiveSection(snapshot.activeJobs());
        addTotalSection(snapshot.activeJobs());
        addCompletedSection(snapshot.completedHistory());
        addFailedSection(snapshot.failedHistory());
    }

    private void addActiveSection(List<AssetTransferJobSnapshot> jobs) {
        addSection(Section.ACTIVE, tr("geometry_node.asset_transfer.panel.active"), null, jobs, host -> {
            host.removeAllViews();
            if (jobs.isEmpty()) {
                addEmpty(host, tr("geometry_node.asset_transfer.panel.no_active"));
                return;
            }
            int rowIndex = 0;
            for (AssetTransferJobSnapshot job : jobs) {
                host.addView(jobHeader(job), match(28));
                for (AssetTransferFileSnapshot file : job.files()) {
                    host.addView(fileRow(file, rowIndex++ % 2 == 0), match(30));
                }
            }
        });
    }

    private View jobHeader(AssetTransferJobSnapshot job) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(8), 0, px(4), 0);
        row.setBackground(rect(UiThemes.EDITOR.surface().panel(), 1, COLOR_BORDER));
        TextView title = label(directionText(job.direction()) + "  " + job.processedFileCount() + "/" + job.files().size(),
                10, job.direction() == AssetTransferDirection.UPLOAD ? COLOR_UPLOAD : COLOR_DOWNLOAD);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return row;
    }

    private View fileRow(AssetTransferFileSnapshot file, boolean alternate) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(8), px(5), px(8), px(5));
        row.setBackground(rect(alternate ? COLOR_ROW : COLOR_ROW_ALT, 1, COLOR_BORDER));

        TextView name = label(fileName(file), 10, COLOR_TEXT);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView state = label(stateText(file.state()), 9, stateColor(file.state()));
        state.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(state, new LinearLayout.LayoutParams(px(58), ViewGroup.LayoutParams.MATCH_PARENT));
        return row;
    }

    private void addTotalSection(List<AssetTransferJobSnapshot> jobs) {
        long totalFiles = jobs.stream().mapToLong(job -> job.files().size()).sum();
        long processedFiles = jobs.stream().mapToLong(AssetTransferJobSnapshot::processedFileCount).sum();

        addSection(Section.TOTAL, tr("geometry_node.asset_transfer.panel.total"), null,
                List.of(processedFiles, totalFiles), host -> {
            host.removeAllViews();
            LinearLayout block = new LinearLayout(getContext());
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(px(8), px(7), px(8), px(7));
            TextView files = label(processedFiles + " / " + totalFiles + " "
                    + tr("geometry_node.asset_transfer.panel.files"), 9, COLOR_TEXT);
            block.addView(files, match(18));
            host.addView(block, wrapMatch());
        });
    }

    private void addCompletedSection(List<AssetTransferFileSnapshot> history) {
        clearCompleted.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
        addSection(Section.COMPLETED, tr("geometry_node.asset_transfer.panel.completed"), clearCompleted,
                history, host -> updateHistory(host, history, false, completedRows));
    }

    private void addFailedSection(List<AssetTransferFileSnapshot> history) {
        failedActions.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
        addSection(Section.FAILED, tr("geometry_node.asset_transfer.panel.failed"), failedActions,
                history, host -> updateHistory(host, history, true, failedRows));
    }

    private void updateHistory(LinearLayout host, List<AssetTransferFileSnapshot> history, boolean failed,
                               Map<AssetTransferFileSnapshot, View> rows) {
        rows.keySet().retainAll(history);
        List<View> desired = new ArrayList<>();
        for (AssetTransferFileSnapshot file : history) {
            desired.add(rows.computeIfAbsent(file, key -> historyRow(key, failed)));
        }
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            if (!desired.contains(host.getChildAt(i))) host.removeViewAt(i);
        }
        if (history.isEmpty()) {
            addEmpty(host, tr(failed ? "geometry_node.asset_transfer.panel.no_failed"
                    : "geometry_node.asset_transfer.panel.no_completed"));
            return;
        }
        for (int i = 0; i < desired.size(); i++) {
            View row = desired.get(i);
            if (host.getChildAt(i) == row) continue;
            if (row.getParent() == host) host.removeView(row);
            host.addView(row, i, match(failed ? 48 : 42));
        }
    }

    private View historyRow(AssetTransferFileSnapshot file, boolean failed) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(8), px(3), px(4), px(3));
        row.setBackground(rect(COLOR_ROW_ALT, 1, COLOR_BORDER));

        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(LinearLayout.VERTICAL);
        boolean hasIssue = file.failure() != null;
        TextView name = label(fileName(file), 9, failed ? COLOR_ERROR : hasIssue ? COLOR_WARNING : COLOR_TEXT);
        name.setSingleLine(true);
        text.addView(name, match(18));
        String secondary = hasIssue
                ? Component.translatable(file.failure().messageKey(), file.failure().messageArguments().toArray()).getString()
                : stateText(file.state());
        TextView detail = label(secondary, 8, COLOR_MUTED);
        detail.setSingleLine(true);
        text.addView(detail, match(17));
        if (hasIssue && !file.failure().detail().isBlank()) {
            row.setTooltipText(secondary + "\n" + file.failure().detail());
        }
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        if (failed && file.failure() != null && file.failure().isRetryable()) {
            row.addView(iconButton(SvgIconView.Icon.RESET, COLOR_MUTED,
                            tr("geometry_node.asset_transfer.action.retry"),
                            () -> ClientAssetTransferService.INSTANCE.retry(file.transferId())),
                    new LinearLayout.LayoutParams(px(26), px(26)));
        }
        return row;
    }

    private void addSection(Section section, String title, View action, Object state,
                            Consumer<LinearLayout> bodyBuilder) {
        SectionContent existing = sections.get(section);
        if (existing != null) {
            if (!existing.state().equals(state)) {
                bodyBuilder.accept(existing.body());
                sections.put(section, new SectionContent(existing.body(), state));
            }
            return;
        }
        LinearLayout sectionView = new LinearLayout(getContext());
        sectionView.setOrientation(LinearLayout.VERTICAL);
        sectionView.setBackground(rect(COLOR_SECTION, 1, COLOR_BORDER));

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(px(4), 0, px(4), 0);
        UiMenuItems.decorate(header, COLOR_SECTION, 0, UiThemes.EDITOR);

        boolean expanded = expandedSections.getOrDefault(section, true);
        SvgIconView disclosure = new SvgIconView(getContext(),
                SvgIconView.Icon.forExpandedState(expanded), COLOR_MUTED);
        LinearLayout.LayoutParams disclosureParams = new LinearLayout.LayoutParams(px(12), px(12));
        disclosureParams.setMargins(px(4), 0, px(6), 0);
        header.addView(disclosure, disclosureParams);
        TextView label = label(title, 10, COLOR_TEXT);
        header.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        if (action != null) header.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sectionView.addView(header, match(28));

        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        bodyBuilder.accept(body);
        sections.put(section, new SectionContent(body, state));
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        sectionView.addView(body, wrapMatch());

        header.setContentDescription(title);
        header.setOnClickListener(view -> {
            boolean next = !expandedSections.getOrDefault(section, true);
            expandedSections.put(section, next);
            body.setVisibility(next ? View.VISIBLE : View.GONE);
            disclosure.setIcon(SvgIconView.Icon.forExpandedState(next));
        });

        LinearLayout.LayoutParams params = wrapMatch();
        params.topMargin = content.getChildCount() == 0 ? 0 : px(6);
        content.addView(sectionView, params);
    }

    private void addEmpty(LinearLayout host, String text) {
        TextView empty = label(text, 9, COLOR_MUTED);
        empty.setGravity(Gravity.CENTER_VERTICAL);
        empty.setPadding(px(8), 0, px(8), 0);
        host.addView(empty, match(30));
    }

    private View iconButton(SvgIconView.Icon icon, int color, String tooltip, Runnable action) {
        UiIconButton button = UiIconButton.createThemed(getContext(), UiThemes.EDITOR,
                new SvgIconView(getContext(), icon, color), UiIconButton.Preset.INLINE)
                .tooltip(tooltip);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView label(String text, float sizeSp, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(0, UIUtils.dp2px(sizeSp));
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        return view;
    }

    private static String fileName(AssetTransferFileSnapshot file) {
        String value = file.direction() == AssetTransferDirection.UPLOAD ? file.sourcePath() : file.targetPath();
        try {
            Path path = Path.of(value);
            return path.getFileName() != null ? path.getFileName().toString() : value;
        } catch (RuntimeException ignored) {
            int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
            return slash >= 0 ? value.substring(slash + 1) : value;
        }
    }

    private static String directionText(AssetTransferDirection direction) {
        return tr(direction == AssetTransferDirection.UPLOAD
                ? "geometry_node.asset_transfer.direction.upload"
                : "geometry_node.asset_transfer.direction.download");
    }

    private static String stateText(AssetTransferState state) {
        return tr("geometry_node.asset_transfer.state." + state.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static int stateColor(AssetTransferState state) {
        return switch (state) {
            case COMPLETED -> COLOR_DOWNLOAD;
            case FAILED -> COLOR_ERROR;
            case QUEUED -> COLOR_MUTED;
            default -> COLOR_UPLOAD;
        };
    }

    private static String tr(String key) { return Component.translatable(key).getString(); }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
    private static LinearLayout.LayoutParams match(int heightDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(heightDp));
    }
    private static LinearLayout.LayoutParams wrapMatch() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private static ShapeDrawable rect(int color, int strokeDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        if (strokeDp > 0) drawable.setStroke(px(strokeDp), strokeColor);
        return drawable;
    }

    private enum Section { ACTIVE, TOTAL, COMPLETED, FAILED }
    private record SectionContent(LinearLayout body, Object state) { }
}
