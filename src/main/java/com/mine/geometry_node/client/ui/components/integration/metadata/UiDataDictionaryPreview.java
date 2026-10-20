package com.mine.geometry_node.client.ui.components.integration.metadata;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.widget.LinearLayout;
import java.util.function.Consumer;

import static com.mine.geometry_node.client.ui.components.integration.metadata.MetadataViews.*;

/** Sidebar preview: at most eight rows, search over the snapshot, full editing through the shared modal. */
public final class UiDataDictionaryPreview extends MetadataViews.BoundView {
    private final UiTextInput search;
    private final LinearLayout entries;
    private final UiDisplayText status;
    private final UiActionButton manageButton;
    private DataDictionaryBinding.State state;
    private Runnable manage;
    private Consumer<String> manageEntry;

    public UiDataDictionaryPreview(Context context) {
        super(context); setOrientation(VERTICAL);
        search = input(context, "search", true); addView(search, params(-1, 30));
        status = text(context, "", UiTheme.Text.MUTED); addView(status, params(-1, 28));
        entries = column(context); addView(entries, params(-1, -2));
        manageButton = button(context, "manage", this::manage);
        LayoutParams buttons = params(-1, 30); buttons.topMargin = px(8); addView(manageButton, buttons);
        watch(search, this::renderEntries);
        render(null);
    }
    public UiDataDictionaryPreview(Context context, DataDictionaryBinding binding, Runnable manage) {
        this(context); this.manage = manage; setBinding(binding);
    }
    public void setManageAction(Runnable action) { manage = action; }
    public void setManageEntryAction(Consumer<String> action) { manageEntry = action; }
    private void manage() {
        if (binding == null || state == null || !state.available() || state.loading()) return;
        if (manage != null) manage.run(); else UiDataDictionaryEditor.show(this, binding);
    }
    @Override protected boolean refreshOnAttach() { return true; }
    @Override protected void render(DataDictionaryBinding.State state) {
        this.state = state; renderEntries();
    }
    private void renderEntries() {
        if (entries == null) return;
        entries.removeAllViews();
        boolean available = state != null && state.available() && !state.loading();
        search.setEnabled(available); manageButton.setEnabled(available && !state.saving());
        if (!available) {
            status.setText(state == null ? tr("unavailable") : status(state)); return;
        }
        var matches = DataDictionaryDraft.matching(state.values(), search.getText().toString(), null);
        status.setText(status(state).isEmpty() ? tr("showing", Math.min(8, matches.size()), matches.size()) : status(state));
        if (matches.isEmpty()) {
            entries.addView(text(getContext(), tr("empty"), UiTheme.Text.MUTED), params(-1, 36)); return;
        }
        for (String key : matches.subList(0, Math.min(8, matches.size()))) {
            LinearLayout row = column(getContext()); row.setPadding(px(8), px(6), px(8), px(6));
            UiDisplayText name = text(getContext(), key, UiTheme.Text.NORMAL);
            UiDisplayText value = text(getContext(), DataDictionaryDraft.summary(state.values().get(key)), UiTheme.Text.MUTED);
            name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
            value.setSingleLine(true); value.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name, params(-1, 20)); row.addView(value, params(-1, 20));
            row.setOnClickListener(view -> {
                if (manageEntry != null) manageEntry.accept(key);
                else if (manage != null) manage.run();
                else UiDataDictionaryEditor.show(this, tr("title"), binding, key);
            }); entries.addView(row, params(-1, -2));
        }
    }
}
