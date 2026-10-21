package com.mine.geometry_node.client.ui.components.integration.metadata;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import java.util.Objects;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import java.util.Map;
import static com.mine.geometry_node.client.ui.components.integration.metadata.MetadataViews.*;

/** Count plus bounded KV synopsis. Summary-only usage never loads the dictionary per table row. */
public final class UiDataSummaryCell extends MetadataViews.BoundView {
    private final UiDisplayText count;
    private final UiDisplayText synopsis;
    private Runnable manage;

    public UiDataSummaryCell(Context context) {
        this(context, UiThemes.EDITOR);
    }
    public UiDataSummaryCell(Context context, UiTheme theme) {
        super(context);
        Objects.requireNonNull(theme, "theme");
        setOrientation(VERTICAL); setGravity(Gravity.CENTER_VERTICAL);
        setPadding(px(6), px(4), px(6), px(4));
        count = text(context, "", theme.text().normal());
        synopsis = text(context, "", theme.text().muted());
        count.setSingleLine(true); synopsis.setSingleLine(true);
        count.setEllipsize(TextUtils.TruncateAt.END); synopsis.setEllipsize(TextUtils.TruncateAt.END);
        addView(count, params(-1, 20)); addView(synopsis, params(-1, 18));
        setOnClickListener(view -> { if (isEnabled() && manage != null) manage.run(); });
    }
    public UiDataSummaryCell(Context context, int count, String summary, Runnable manage) {
        this(context, count, summary, manage, UiThemes.EDITOR);
    }
    public UiDataSummaryCell(Context context, int count, String summary, Runnable manage, UiTheme theme) {
        this(context, theme); setSummary(count, summary, manage);
    }
    public UiDataSummaryCell(Context context, DataDictionaryBinding binding, Runnable manage) {
        this(context, binding, manage, UiThemes.EDITOR);
    }
    public UiDataSummaryCell(Context context, DataDictionaryBinding binding, Runnable manage, UiTheme theme) {
        this(context, theme); this.manage = manage; setBinding(binding);
    }
    public void setSummary(int total, String summary, Runnable onManage) {
        manage = onManage; setSummary(total, summary);
    }
    public void setSummary(int total, String summary) {
        count.setText(total <= 0 ? "" : tr("count", total));
        String brief = total == 0 || summary == null ? "" : summary;
        synopsis.setText(brief);
        synopsis.setVisibility(brief.isBlank() ? GONE : VISIBLE);
        setClickable(manage != null); setFocusable(manage != null);
        setContentDescription(count.getText() + " " + synopsis.getText());
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return manage != null && super.onTouchEvent(event);
    }

    /** Bounded scalar/container synopsis; never traverses nested dictionaries or registered objects. */
    public static String summaryValue(Object value) { return DataDictionaryDraft.summary(value); }

    @Override protected void render(DataDictionaryBinding.State state) {
        if (state == null) return;
        if (!state.available() || state.loading()) {
            setSummary(-1, state.loading() ? tr("loading") : ""); return;
        }
        StringBuilder brief = new StringBuilder(); int entries = 0;
        for (Map.Entry<String, Object> entry : state.values().entrySet()) {
            if (entries++ >= 2) break;
            if (!brief.isEmpty()) brief.append(" · ");
            brief.append(entry.getKey()).append(": ").append(DataDictionaryDraft.summary(entry.getValue()));
        }
        setSummary(state.values().size(), brief.toString());
    }
}
