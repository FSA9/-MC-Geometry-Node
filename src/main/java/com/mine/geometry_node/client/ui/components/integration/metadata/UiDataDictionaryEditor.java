package com.mine.geometry_node.client.ui.components.integration.metadata;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.overlay.OverlayCloseReason;
import com.mine.geometry_node.client.ui.overlay.OverlayHost;
import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.text.style.RelativeSizeSpan;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import java.util.Objects;
import static com.mine.geometry_node.client.ui.components.integration.metadata.MetadataViews.*;

/** Shared custom-data manager, available as a standalone window or an embedded child view. */
public final class UiDataDictionaryEditor extends ModalWindowView {
    private final UiDataDictionaryContent content;

    public static boolean show(View anchor, DataDictionaryBinding binding) {
        return show(anchor, tr("title"), binding, null);
    }
    public static boolean show(View anchor, DataDictionaryBinding binding, UiTheme theme) {
        return show(anchor, tr("title"), binding, null, theme);
    }
    public static boolean show(View anchor, String title, DataDictionaryBinding binding) {
        return show(anchor, title, binding, null);
    }
    public static boolean show(View anchor, String title, DataDictionaryBinding binding, String initialKey) {
        return show(anchor, title, binding, initialKey, UiThemes.EDITOR);
    }
    public static boolean show(View anchor, String title, DataDictionaryBinding binding, String initialKey, UiTheme theme) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(anchor, "anchor"); Objects.requireNonNull(binding, "binding");
        OverlayHost host = OverlayHost.find(anchor);
        if (host == null) return false;
        DataDictionaryBinding sessionBinding = Objects.requireNonNull(binding.openSession(), "session binding");
        UiDataDictionaryEditor editor;
        try { editor = new UiDataDictionaryEditor(anchor.getContext(), anchor, title, sessionBinding, initialKey, theme); }
        catch (RuntimeException failure) { sessionBinding.close(); throw failure; }
        try {
            if (host.showModal(editor, new ModalOptions(false, true, anchor, theme.surface().modalScrim())) != null) return true;
            editor.content.dispose();
            return false;
        } catch (RuntimeException failure) { editor.content.dispose(); throw failure; }
    }

    /** Create a logical child session. Completed operations submit through the supplied binding;
     * unfinished forms remain local until explicitly saved. The parent owns the shared binding. */
    public static Embedded createEmbedded(View owner, DataDictionaryBinding binding, UiTheme theme) {
        return createEmbedded(owner, binding, theme, 11);
    }

    /** The host may choose its body text size without changing the shared editor defaults. */
    public static Embedded createEmbedded(View owner, DataDictionaryBinding binding, UiTheme theme, float textSizeDp) {
        Objects.requireNonNull(owner, "owner");
        UiDataDictionaryContent content = new UiDataDictionaryContent(owner.getContext(), owner, binding,
                null, theme, true, () -> {}, null, textSizeDp);
        content.start();
        return new Embedded(content);
    }

    public static final class Embedded implements AutoCloseable {
        private final UiDataDictionaryContent content;
        private Embedded(UiDataDictionaryContent content) { this.content = content; }
        public View view() { return content; }
        /** Explicit Save Entry. Never call to gate navigation, parent Apply, or closure.
         * True means accepted (possibly in flight); a synchronous rejection returns false. */
        public boolean saveEntry() { return content.saveEntry(); }
        /** Includes unfinished forms and failed/in-flight completed operations; never a close veto. */
        public boolean hasPendingChanges() { return content.hasPendingChanges(); }
        /** Re-read the binding snapshot after a parent acknowledgement; preserves unfinished forms. */
        public void refreshCommitted() { content.refreshCommitted(); }
        /** Release transient menus/focus only; retain the logical session and its binding listener. */
        public void suspend() { content.suspend(); }
        public void resume() { content.resume(); }
        /** Consume Escape when a child menu is open, before the parent requests window closure. */
        public boolean onEscapePressed() { return content.onEscapePressed(); }
        /** Notification only: update the parent's dirty indicator; do not call saveEntry here. */
        public void setOnPendingChangesChanged(Runnable listener) { content.setOnPendingChangesChanged(listener); }
        /** True close: discard unfinished forms; in-flight writes retain their listener until completion. */
        public void dispose() { content.dispose(); }
        @Override public void close() { dispose(); }
    }

    UiDataDictionaryEditor(Context context, View owner, String title, DataDictionaryBinding binding, String initialKey) {
        this(context, owner, title, binding, initialKey, UiThemes.EDITOR);
    }
    UiDataDictionaryEditor(Context context, View owner, String title, DataDictionaryBinding binding, String initialKey, UiTheme theme) {
        super(context, tr("title"), MovementMode.DRAGGABLE, Style.forTheme(theme));
        Objects.requireNonNull(theme, "theme");
        bindSessionOwner(owner);
        if (title != null && !title.isBlank() && !title.equals(tr("title"))) {
            String heading = tr("title");
            SpannableString caption = new SpannableString(heading + "   " + title);
            int start = heading.length() + 3;
            caption.setSpan(new ForegroundColorSpan(theme.text().muted()), start, caption.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            caption.setSpan(new RelativeSizeSpan(0.85f), start, caption.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setTitle(caption);
        }
        titleView().setSingleLine(true); titleView().setEllipsize(TextUtils.TruncateAt.END);
        setPreferredSizeDp(860, 590); setMinimumSizeDp(560, 400);
        contentHost().setPadding(0, 0, 0, 0);
        content = new UiDataDictionaryContent(context, owner, binding, initialKey, theme,
                false, this::requestClose, contentHost());
        setContent(content);
        setActions(content.actions()); content.actions().setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        actionsHost().setBackground(fill(theme.surface().panel()));
    }
    @Override protected void onWindowShown() { content.start(); }
    @Override protected void onWindowSuspended() { content.suspend(); }
    @Override protected void onWindowResumed() { content.resume(); }
    @Override protected void onWindowClosed(OverlayCloseReason reason) { content.dispose(); }
    @Override protected void onWindowDestroyed() { content.dispose(); }
    @Override public boolean onEscapePressed() { return content.onEscapePressed(); }
    private static ShapeDrawable fill(int color) {
        ShapeDrawable background = new ShapeDrawable(); background.setColor(color); return background;
    }
}
