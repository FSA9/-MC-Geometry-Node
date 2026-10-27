package com.mine.geometry_node.client.ui.editor.datalibrary.global;

import com.mine.geometry_node.client.feature.scopeddata.ClientScopedDataRepository;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchSubmit;
import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.composite.list.UiNavigationRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.integration.metadata.ScopedDataDictionaryBinding;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataDictionaryEditor;
import com.mine.geometry_node.client.ui.theme.*;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.state.*;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;

/** GLOBAL and server dimension selection. Data operations belong to the borrowed dictionary binding. */
final class ScopedDataCatalogView extends LinearLayout implements GlobalDataSection {
    private static final String GLOBAL = "GLOBAL";
    private record Row(UiNavigationRow view) {}

    private final ClientScopedDataRepository repository;
    private final UiStateContext state;
    private final Consumer<Runnable> postToUi;
    private final UiTheme theme = UiThemes.EDITOR;
    private final UiFixedSplitLayout split;
    private final UiSearchInput search;
    private final UiScrollView scroll;
    private final LinearLayout targetRows, feedback;
    private final UiDisplayText count, catalogStatus, title, kind, dataStatus, placeholder;
    private final UiIconButton retry;
    private final FrameLayout details;
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final Map<String, UiDataDictionaryEditor.Embedded> editors = new LinkedHashMap<>();
    private final Runnable scopeChanged;
    private ClientScopedDataRepository.Session session;
    private Runnable sessionChanged;
    private String scope, selected = GLOBAL, query = "", activeTarget;
    private long repositoryGeneration;
    private int savedScroll;
    private boolean shown, disposed, restoreScroll, scrollRestoreQueued;

    ScopedDataCatalogView(Context context, UiStateContext state) {
        this(context, state, ClientScopedDataRepository.INSTANCE, MuiModApi::postToUiThread);
    }
    ScopedDataCatalogView(Context context, UiStateContext state, ClientScopedDataRepository repository, Consumer<Runnable> postToUi) {
        super(context);
        this.repository = Objects.requireNonNull(repository); this.state = Objects.requireNonNull(state);
        this.postToUi = Objects.requireNonNull(postToUi);
        scopeChanged = () -> postToUi.accept(this::ownerChanged);
        setOrientation(VERTICAL); setBackground(fill(theme.surface().canvas()));

        LinearLayout catalog = column(context); catalog.setBackground(fill(theme.surface().root()));
        LinearLayout toolbar = row(context); toolbar.setPadding(px(10), px(8), px(10), px(8));
        search = UiSearchInput.createThemed(context, theme); search.setHint(tr("search")); search.setSingleLine(true);
        toolbar.addView(search, new LayoutParams(0, px(30), 1));
        UiIconButton find = UiIconButton.createThemed(context, theme, SvgIconView.Icon.SEARCH, tr("search"));
        LayoutParams findParams = new LayoutParams(px(30), px(30)); findParams.leftMargin = px(6);
        toolbar.addView(find, findParams);
        UiSearchSubmit.bind(find, this::submitSearch, search);
        catalog.addView(toolbar, new LayoutParams(-1, -2));
        count = label(context, "", UiMetrics.TEXT_CAPTION, theme.text().muted());
        count.setPadding(px(16), 0, px(16), 0); count.setGravity(Gravity.CENTER_VERTICAL);
        catalog.addView(count, new LayoutParams(-1, px(40)));
        catalog.addView(divider(context), new LayoutParams(-1, px(UiMetrics.BORDER)));
        catalogStatus = label(context, "", UiMetrics.TEXT_CAPTION, theme.text().muted());
        catalogStatus.setPadding(px(16), px(6), px(16), px(6)); catalog.addView(catalogStatus, new LayoutParams(-1, -2));
        targetRows = column(context);
        scroll = new UiScrollView(context, theme.scroll()); scroll.addView(targetRows, new FrameLayout.LayoutParams(-1, -2));
        catalog.addView(scroll, new LayoutParams(-1, 0, 1));

        LinearLayout detail = column(context); detail.setBackground(fill(theme.surface().sidebar()));
        LinearLayout identity = row(context); identity.setPadding(px(18), px(14), px(18), px(14));
        identity.setMinimumHeight(px(60));
        LinearLayout names = column(context);
        title = label(context, "", UiMetrics.TEXT_BODY, theme.text().normal()); title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(title, new LayoutParams(-1, px(26)));
        identity.addView(names, new LayoutParams(0, -2, 1));
        kind = label(context, "", UiMetrics.TEXT_CAPTION, theme.text().muted());
        identity.addView(kind, new LayoutParams(-2, -2)); detail.addView(identity, new LayoutParams(-1, -2));
        detail.addView(divider(context), new LayoutParams(-1, px(UiMetrics.BORDER)));
        feedback = row(context); feedback.setPadding(px(16), 0, px(16), 0);
        dataStatus = label(context, "", UiMetrics.TEXT_CAPTION, theme.text().muted());
        feedback.addView(dataStatus, new LayoutParams(0, -2, 1));
        retry = UiIconButton.createThemed(context, theme, SvgIconView.Icon.RESET, tr("reload"));
        retry.setOnClickListener(view -> reload()); feedback.addView(retry, new LayoutParams(px(30), px(30)));
        detail.addView(feedback, new LayoutParams(-1, -2));
        details = new FrameLayout(context);
        placeholder = label(context, "", UiMetrics.TEXT_BODY, theme.text().muted()); placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(px(20), px(20), px(20), px(20)); details.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        detail.addView(details, new LayoutParams(-1, 0, 1));
        split = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.HORIZONTAL,
                UiFixedSplitLayout.Edge.FIRST, 246, 180, 380, theme);
        split.setPanes(catalog, detail); split.restorePreferredExtentDp(state.read(ScopedDataViewState.SPLIT, UiStateScopes.GLOBAL).extent());
        split.addUserStateListener(this::saveSplit); addView(split, new LayoutParams(-1, 0, 1));
        restoreOwner(); render();
    }

    @Override public View view() { return this; }
    @Override public void onShow() {
        if (disposed || shown) return;
        shown = true; restoreOwner(); UiStateScopes.addChangeListener(scopeChanged);
        session.addChangeListener(sessionChanged);
        render(); reload();
    }
    @Override public void onHide() {
        if (!shown) return;
        // Suspension must precede any visibility/focus change by the category host.
        suspendActive(); savePage(); saveSplit(); shown = false;
        UiStateScopes.removeChangeListener(scopeChanged); session.removeChangeListener(sessionChanged);
    }
    @Override public void onDispose() {
        if (disposed) return;
        onHide(); disposed = true; releaseSession();
    }
    private boolean ownerCurrent() {
        return !disposed && session != null && repositoryGeneration == repository.generation()
                && Objects.equals(scope, UiStateScopes.current()) && session.current();
    }
    private void ownerChanged() {
        if (!shown || disposed) return;
        boolean changed = restoreOwner(); boolean fallback = render();
        if (changed) reload();
        else if (fallback) session.read(selected, false);
    }
    private boolean restoreOwner() {
        if (ownerCurrent()) return false;
        savePage(); releaseSession();
        scope = UiStateScopes.current(); repositoryGeneration = repository.generation();
        var page = state.read(ScopedDataViewState.PAGE, scope);
        selected = page.target(); query = page.query(); savedScroll = page.scroll(); search.setText(query);
        restoreScroll = true; scrollRestoreQueued = false;
        rows.clear(); targetRows.removeAllViews();
        session = repository.newSession();
        var expected = session;
        sessionChanged = () -> postToUi.accept(() -> {
            if (!shown || disposed || expected != session) return;
            ownerChanged();
        });
        if (shown) session.addChangeListener(sessionChanged);
        return true;
    }
    private void releaseSession() {
        suspendActive();
        editors.values().forEach(UiDataDictionaryEditor.Embedded::dispose); editors.clear();
        details.removeAllViews(); details.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        if (session != null) { session.removeChangeListener(sessionChanged); session.close(); session = null; }
    }
    private void reload() {
        if (!shown || !ownerCurrent()) return;
        // Forced reloads originate only from lifecycle/user actions, never snapshot notifications.
        session.refresh(); session.read(selected, true); render();
    }
    private void submitSearch() {
        if (!ownerCurrent()) return;
        query = search.getText().toString().trim(); restoreScroll = false; scroll.scrollTo(0, 0); savedScroll = 0;
        savePage(); renderCatalog();
    }
    private void select(String target) {
        if (!shown || !ownerCurrent() || Objects.equals(selected, target)) return;
        suspendActive(); selected = target; savePage();
        session.read(target, false); render();
    }
    private boolean render() {
        if (!ownerCurrent()) return false;
        boolean fallback = false;
        var catalog = session.snapshot();
        if (catalog.loaded() && !selected.equals(GLOBAL) && !catalog.dimensions().contains(selected)) {
            suspendActive(); selected = GLOBAL; savePage();
            fallback = true;
        }
        renderCatalog(); renderDetail();
        return fallback;
    }
    private void renderCatalog() {
        if (!ownerCurrent()) return;
        var catalog = session.snapshot();
        List<String> targets = new ArrayList<>(); targets.add(GLOBAL); targets.addAll(catalog.dimensions());
        Set<String> live = new HashSet<>(targets);
        // Updating counts must not replace the pressed navigation button between down and up.
        for (String target : List.copyOf(rows.keySet())) if (!live.contains(target)) {
            Row row = rows.remove(target); targetRows.removeView(row.view());
        }
        String needle = query.toLowerCase(Locale.ROOT); int visible = 0;
        for (String target : targets) {
            Row row = rows.computeIfAbsent(target, this::createRow);
            boolean matches = target.toLowerCase(Locale.ROOT).contains(needle);
            row.view().setVisibility(matches ? VISIBLE : GONE);
            if (matches) visible++;
            var data = session.data(target);
            row.view().setBadgeText(data.fetched() && data.available() ? tr("entries", data.values().size())
                    : data.fetched() ? tr("unavailable") : tr("unread"));
            boolean current = target.equals(selected);
            row.view().setPalette(UiTableStyle.rowPalette(current, theme.surface().root(), theme));
        }
        count.setText(catalog.loaded() ? tr("scope_count", visible, targets.size()) : tr("catalog_unknown"));
        String message = ScopedDataDictionaryBinding.message(catalog.message());
        if (catalog.loading()) message = join(tr("catalog_loading"), message);
        else if (!catalog.loaded() && message.isEmpty()) message = tr("catalog_unknown");
        catalogStatus.setText(message); catalogStatus.setVisibility(message.isEmpty() ? GONE : VISIBLE);
        catalogStatus.setTextColor(catalog.message().isEmpty() ? theme.text().muted() : theme.status().error());
        if (shown && restoreScroll && !scrollRestoreQueued && catalog.loaded()) {
            scrollRestoreQueued = true; var expected = session;
            scroll.post(() -> {
                if (session != expected) return;
                scrollRestoreQueued = false;
                if (ownerCurrent() && shown && restoreScroll) {
                    scroll.scrollTo(0, savedScroll); restoreScroll = false;
                }
            });
        }
    }
    private Row createRow(String target) {
        UiNavigationRow view = new UiNavigationRow(getContext(), target, tr("unread"), theme);
        view.button().setOnClickListener(button -> select(target)); view.setTooltipText(target);
        targetRows.addView(view, new LayoutParams(-1, px(UiTableStyle.ROW_HEIGHT)));
        return new Row(view);
    }
    private void renderDetail() {
        title.setText(selected); title.setTooltipText(selected);
        kind.setText(tr(selected.equals(GLOBAL) ? "global" : "dimension"));
        var data = session.data(selected);
        String message = ScopedDataDictionaryBinding.message(data.message());
        if (data.loading()) message = join(tr("loading"), message);
        else if (data.pending()) message = join(tr("saving"), message);
        else if (!data.fetched() && message.isEmpty()) message = tr("unread");
        else if (!data.available() && message.isEmpty()) message = tr("unavailable");
        dataStatus.setText(message); dataStatus.setTextColor(data.message().isEmpty() ? theme.text().muted() : theme.status().error());
        boolean failed = !data.message().isEmpty() || !session.snapshot().message().isEmpty();
        feedback.setVisibility(!message.isEmpty() || failed ? VISIBLE : GONE);
        retry.setVisibility(failed ? VISIBLE : GONE);
        retry.setEnabled(shown && !session.snapshot().loading() && !data.loading() && !data.pending());
        UiDataDictionaryEditor.Embedded editor = editors.get(selected);
        boolean readable = data.fetched() && data.available();
        if (editor == null && readable) {
            var expectedSession = session; String expectedScope = scope, target = selected; long expectedGeneration = repositoryGeneration;
            var binding = new ScopedDataDictionaryBinding(expectedSession, target,
                    () -> !disposed && session == expectedSession && expectedGeneration == repository.generation()
                            && Objects.equals(expectedScope, UiStateScopes.current()),
                    () -> shown && target.equals(selected) && target.equals(activeTarget));
            editor = UiDataDictionaryEditor.createEmbedded(this, binding, theme, UiMetrics.TEXT_BODY,
                    UiDataDictionaryEditor.CommitMode.ON_FOCUS_LOSS);
            editor.suspend(); editor.view().setVisibility(GONE);
            editors.put(target, editor); details.addView(editor.view(), new FrameLayout.LayoutParams(-1, -1));
        }
        boolean display = editor != null && (readable || editor.hasPendingChanges());
        String nextActive = shown && display ? selected : null;
        if (!Objects.equals(activeTarget, nextActive)) {
            suspendActive(); activeTarget = nextActive;
            if (activeTarget != null) { editor.view().setVisibility(VISIBLE); editor.resume(); }
        }
        for (var entry : editors.entrySet()) entry.getValue().view().setVisibility(display && entry.getKey().equals(selected) ? VISIBLE : GONE);
        placeholder.setText(message); placeholder.setVisibility(display ? GONE : VISIBLE);
    }
    private void suspendActive() {
        if (activeTarget != null) { var editor = editors.get(activeTarget); if (editor != null) editor.suspend(); activeTarget = null; }
    }
    private void savePage() {
        if (scope != null && !disposed) {
            if (!restoreScroll) savedScroll = scroll.getScrollY();
            state.write(ScopedDataViewState.PAGE, scope, new ScopedDataViewState.Page(selected, query, savedScroll));
        }
    }
    private void saveSplit() {
        if (!disposed) state.write(ScopedDataViewState.SPLIT, UiStateScopes.GLOBAL, new ScopedDataViewState.Split(split.preferredExtentDp()));
    }
    private View divider(Context context) { View view = new View(context); view.setBackground(fill(theme.border().divider())); return view; }
    private static UiDisplayText label(Context context, String text, float size, int color) { return new UiDisplayText(context, text, size, color); }
    private static LinearLayout column(Context context) { LinearLayout view = new LinearLayout(context); view.setOrientation(VERTICAL); return view; }
    private static LinearLayout row(Context context) { LinearLayout view = new LinearLayout(context); view.setGravity(Gravity.CENTER_VERTICAL); view.setBaselineAligned(false); return view; }
    private static ShapeDrawable fill(int color) { ShapeDrawable result = new ShapeDrawable(); result.setColor(color); return result; }
    private static String join(String first, String second) { return second.isEmpty() ? first : first + " · " + second; }
    private static String tr(String key, Object... args) { return Component.translatable("geometry_node.database.scoped_data." + key, args).getString(); }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
