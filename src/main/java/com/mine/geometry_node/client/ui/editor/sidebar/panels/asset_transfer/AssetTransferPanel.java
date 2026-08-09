package com.mine.geometry_node.client.ui.editor.sidebar.panels.asset_transfer;

import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.ui.common.SvgIconView;
import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanel;
import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanelDefinition;
import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferFileSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AssetTransferPanel extends FrameLayout implements SidebarPanel {
    public static final String PANEL_ID = "asset_transfers";
    public static final SidebarPanelDefinition DEFINITION = new SidebarPanelDefinition(
            PANEL_ID,
            "geometry_node.asset_transfer.panel.title",
            200,
            Set.of(SidebarPanelScope.GRAPH_EDITOR, SidebarPanelScope.ASSET_BROWSER),
            AssetTransferPanel::create);

    private static final int COLOR_BACKGROUND = 0xFF303030;
    private static final int COLOR_SECTION = 0xFF292929;
    private static final int COLOR_ROW = 0xFF333333;
    private static final int COLOR_ROW_ALT = 0xFF303030;
    private static final int COLOR_BORDER = 0xFF1D1D1D;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF919191;
    private static final int COLOR_UPLOAD = 0xFF5793C1;
    private static final int COLOR_DOWNLOAD = 0xFF62A56B;
    private static final int COLOR_ERROR = 0xFFD87575;
    private static final int COLOR_HOVER = 0xFF4A4A4A;
    private static final long UI_UPDATE_DELAY_MS = 100L;

    private final ScrollView scroll;
    private final LinearLayout content;
    private ClientAssetTransferService.Subscription subscription;
    private AssetTransferSnapshot pendingSnapshot = AssetTransferSnapshot.empty();
    private boolean renderScheduled;

    public AssetTransferPanel(Context context) {
        super(context);
        setBackground(rect(COLOR_BACKGROUND, 0, 0));
        scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        render(AssetTransferSnapshot.empty());
    }

    private static SidebarPanel create(SidebarPanelContext context) {
        return new AssetTransferPanel(context.uiContext());
    }

    @Override public View getView() { return this; }

    @Override
    public void onSelected() {
        if (subscription != null) return;
        subscription = ClientAssetTransferService.INSTANCE.subscribe(this::acceptSnapshot);
    }

    @Override
    public void onDeselected() {
        if (subscription != null) subscription.close();
        subscription = null;
        renderScheduled = false;
    }

    private void acceptSnapshot(AssetTransferSnapshot snapshot) {
        pendingSnapshot = snapshot;
        if (renderScheduled) return;
        renderScheduled = true;
        postDelayed(() -> {
            renderScheduled = false;
            if (subscription != null) render(pendingSnapshot);
        }, UI_UPDATE_DELAY_MS);
    }

    private void render(AssetTransferSnapshot snapshot) {
        content.removeAllViews();
        addActiveSection(snapshot.activeJobs());
        addTotalSection(snapshot.activeJobs());
        addCompletedSection(snapshot.completedHistory());
        addFailedSection(snapshot.failedHistory());
    }

    private void addActiveSection(List<AssetTransferJobSnapshot> jobs) {
        addSectionHeader(tr("geometry_node.asset_transfer.panel.active"), null);
        if (jobs.isEmpty()) {
            addEmpty(tr("geometry_node.asset_transfer.panel.no_active"));
            return;
        }
        int rowIndex = 0;
        for (AssetTransferJobSnapshot job : jobs) {
            content.addView(jobHeader(job), match(28));
            for (AssetTransferFileSnapshot file : job.files()) {
                content.addView(fileRow(file, rowIndex++ % 2 == 0), match(52));
            }
        }
    }

    private View jobHeader(AssetTransferJobSnapshot job) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(8), 0, px(4), 0);
        row.setBackground(rect(0xFF272727, 1, COLOR_BORDER));
        TextView title = label(directionText(job.direction()) + "  " + job.completedFileCount() + "/" + job.files().size(),
                10, job.direction() == AssetTransferDirection.UPLOAD ? COLOR_UPLOAD : COLOR_DOWNLOAD);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        row.addView(iconButton(SvgIconView.Icon.CLOSE, COLOR_ERROR,
                        tr("geometry_node.asset_transfer.action.cancel"), () -> ClientAssetTransferService.INSTANCE.cancel(job.jobId())),
                new LinearLayout.LayoutParams(px(24), px(24)));
        return row;
    }

    private View fileRow(AssetTransferFileSnapshot file, boolean alternate) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(px(8), px(5), px(8), px(5));
        row.setBackground(rect(alternate ? COLOR_ROW : COLOR_ROW_ALT, 1, COLOR_BORDER));

        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(fileName(file), 10, COLOR_TEXT);
        name.setSingleLine(true);
        top.addView(name, new LinearLayout.LayoutParams(0, px(19), 1));
        TextView state = label(stateText(file.state()), 9, stateColor(file.state()));
        state.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(state, new LinearLayout.LayoutParams(px(58), px(19)));
        row.addView(top, match(19));

        row.addView(new TransferProgressView(getContext(), file.progress()), match(5));
        TextView detail = label(formatBytes(file.transferredBytes()) + " / " + formatBytes(file.totalBytes())
                + (file.bytesPerSecond() > 0 ? "  " + formatBytes(file.bytesPerSecond()) + "/s" : ""), 8, COLOR_MUTED);
        row.addView(detail, match(17));
        return row;
    }

    private void addTotalSection(List<AssetTransferJobSnapshot> jobs) {
        addSectionHeader(tr("geometry_node.asset_transfer.panel.total"), null);
        long totalBytes = jobs.stream().mapToLong(AssetTransferJobSnapshot::totalBytes).sum();
        long transferred = jobs.stream().mapToLong(AssetTransferJobSnapshot::transferredBytes).sum();
        long totalFiles = jobs.stream().mapToLong(job -> job.files().size()).sum();
        long completedFiles = jobs.stream().mapToLong(AssetTransferJobSnapshot::completedFileCount).sum();
        long speed = jobs.stream().flatMap(job -> job.files().stream())
                .mapToLong(AssetTransferFileSnapshot::bytesPerSecond).sum();

        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(px(8), px(7), px(8), px(7));
        block.addView(new TransferProgressView(getContext(), totalBytes == 0 ? 0 : (double) transferred / totalBytes), match(7));
        TextView bytes = label(formatBytes(transferred) + " / " + formatBytes(totalBytes), 9, COLOR_TEXT);
        block.addView(bytes, match(18));
        TextView files = label(completedFiles + " / " + totalFiles + " "
                + tr("geometry_node.asset_transfer.panel.files") + "  " + formatBytes(speed) + "/s", 9, COLOR_MUTED);
        block.addView(files, match(18));
        content.addView(block, match(50));
    }

    private void addCompletedSection(List<AssetTransferFileSnapshot> history) {
        addSectionHeader(tr("geometry_node.asset_transfer.panel.completed"),
                history.isEmpty() ? null : iconButton(SvgIconView.Icon.CLEAR, COLOR_MUTED,
                        tr("geometry_node.asset_transfer.action.clear"),
                        ClientAssetTransferService.INSTANCE::clearCompletedHistory));
        if (history.isEmpty()) {
            addEmpty(tr("geometry_node.asset_transfer.panel.no_completed"));
            return;
        }
        for (AssetTransferFileSnapshot file : history) content.addView(historyRow(file, false), match(42));
    }

    private void addFailedSection(List<AssetTransferFileSnapshot> history) {
        LinearLayout actions = null;
        if (!history.isEmpty()) {
            actions = new LinearLayout(getContext());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(iconButton(SvgIconView.Icon.RESET, COLOR_MUTED,
                    tr("geometry_node.asset_transfer.action.retry_all"),
                    ClientAssetTransferService.INSTANCE::retryAll), new LinearLayout.LayoutParams(px(24), px(24)));
            actions.addView(iconButton(SvgIconView.Icon.CLEAR, COLOR_MUTED,
                    tr("geometry_node.asset_transfer.action.clear"),
                    ClientAssetTransferService.INSTANCE::clearFailedHistory), new LinearLayout.LayoutParams(px(24), px(24)));
        }
        addSectionHeader(tr("geometry_node.asset_transfer.panel.failed"), actions);
        if (history.isEmpty()) {
            addEmpty(tr("geometry_node.asset_transfer.panel.no_failed"));
            return;
        }
        for (AssetTransferFileSnapshot file : history) content.addView(historyRow(file, true), match(48));
    }

    private View historyRow(AssetTransferFileSnapshot file, boolean failed) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(8), px(3), px(4), px(3));
        row.setBackground(rect(COLOR_ROW_ALT, 1, COLOR_BORDER));

        LinearLayout text = new LinearLayout(getContext());
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(fileName(file), 9, failed ? COLOR_ERROR : COLOR_TEXT);
        name.setSingleLine(true);
        text.addView(name, match(18));
        String secondary = failed && file.failure() != null
                ? Component.translatable(file.failure().messageKey(), file.failure().messageArguments().toArray()).getString()
                : formatBytes(file.totalBytes());
        TextView detail = label(secondary, 8, COLOR_MUTED);
        detail.setSingleLine(true);
        text.addView(detail, match(17));
        if (failed && file.failure() != null && !file.failure().detail().isBlank()) {
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

    private void addSectionHeader(String title, View action) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(px(8), 0, px(4), 0);
        header.setBackground(rect(COLOR_SECTION, 1, COLOR_BORDER));
        TextView label = label(title, 10, COLOR_TEXT);
        header.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        if (action != null) header.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams params = match(28);
        params.topMargin = content.getChildCount() == 0 ? 0 : px(6);
        content.addView(header, params);
    }

    private void addEmpty(String text) {
        TextView empty = label(text, 9, COLOR_MUTED);
        empty.setGravity(Gravity.CENTER_VERTICAL);
        empty.setPadding(px(8), 0, px(8), 0);
        content.addView(empty, match(30));
    }

    private View iconButton(SvgIconView.Icon icon, int color, String tooltip, Runnable action) {
        FrameLayout button = new FrameLayout(getContext());
        button.setContentDescription(tooltip);
        button.setTooltipText(tooltip);
        button.setBackground(rect(0x00000000, 0, 0));
        SvgIconView image = new SvgIconView(getContext(), icon, color);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(px(14), px(14), Gravity.CENTER);
        button.addView(image, iconParams);
        button.setOnHoverListener((view, event) -> {
            button.setBackground(rect(event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? COLOR_HOVER : 0x00000000, 0, 0));
            return false;
        });
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

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024) return String.format(java.util.Locale.ROOT, "%.1f KiB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024) return String.format(java.util.Locale.ROOT, "%.1f MiB", mib);
        return String.format(java.util.Locale.ROOT, "%.1f GiB", mib / 1024.0);
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
            case FAILED, CANCELLED -> COLOR_ERROR;
            case QUEUED -> COLOR_MUTED;
            default -> COLOR_UPLOAD;
        };
    }

    private static String tr(String key) { return Component.translatable(key).getString(); }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
    private static LinearLayout.LayoutParams match(int heightDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(heightDp));
    }
    private static ShapeDrawable rect(int color, int strokeDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        if (strokeDp > 0) drawable.setStroke(px(strokeDp), strokeColor);
        return drawable;
    }
}
