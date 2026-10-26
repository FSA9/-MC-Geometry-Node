package com.mine.geometry_node.client.ui.editor.datalibrary.global;

import com.mine.geometry_node.client.feature.faction.ClientFactionRepository;
import com.mine.geometry_node.client.ui.components.base.button.*;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.*;
import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.integration.faction.*;
import com.mine.geometry_node.client.ui.overlay.OverlayHost;
import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import com.mine.geometry_node.client.ui.theme.*;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.state.*;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static com.mine.geometry_node.client.ui.components.integration.faction.FactionUi.*;

/** Faction-specific catalog and retained details, hosted as one global-data section. */
final class FactionCatalogView extends LinearLayout implements GlobalDataSection {
    private record Row(LinearLayout view, UiCheckBox check, UiActionButton name) {}
    private final ClientFactionRepository repository;
    private final UiStateContext state;
    private final Consumer<Runnable> postToUi;
    private final UiTheme theme = UiThemes.EDITOR;
    private final UiFixedSplitLayout factionSplit;
    private final UiSearchInput search;
    private final UiCheckBox selectAll;
    private final UiIconButton add, remove;
    private final UiDisplayText count, status;
    private final LinearLayout list;
    private final FrameLayout details;
    private final Map<UUID, Row> rows = new LinkedHashMap<>();
    private final Map<UUID, FactionEditorView> editors = new HashMap<>();
    private final Set<UUID> checked = new LinkedHashSet<>();
    private final FactionCatalogSubscription subscription;
    private String scope, query = "", section = "info", localError = "";
    private UUID selected;
    private boolean shown, disposed, writing;
    private long generation, navigationRevision;

    FactionCatalogView(Context context, UiStateContext state) { this(context, state, ClientFactionRepository.INSTANCE); }
    FactionCatalogView(Context context, UiStateContext state, ClientFactionRepository repository) {
        this(context, state, repository, MuiModApi::postToUiThread);
    }
    FactionCatalogView(Context context, UiStateContext state, ClientFactionRepository repository, Consumer<Runnable> postToUi) {
        super(context); this.repository = Objects.requireNonNull(repository); this.state = Objects.requireNonNull(state);
        this.postToUi = Objects.requireNonNull(postToUi);
        setOrientation(VERTICAL); setBackground(fill(theme.surface().canvas()));
        LinearLayout catalog = column(context); catalog.setBackground(fill(theme.surface().root()));
        LinearLayout toolbar = row(context); toolbar.setPadding(px(10), px(8), px(10), px(8));
        search = UiSearchInput.createThemed(context, theme); search.setHint(tr("search")); search.setSingleLine(true);
        toolbar.addView(search, new LayoutParams(0, px(30), 1));
        UiIconButton submitSearch = icon(context, SvgIconView.Icon.SEARCH, "search"); toolbar.addView(submitSearch, actionParams());
        add = icon(context, SvgIconView.Icon.PLUS, "add"); remove = icon(context, SvgIconView.Icon.CLEAR, "delete");
        toolbar.addView(add, actionParams()); toolbar.addView(remove, actionParams());
        UiSearchSubmit.bind(submitSearch, () -> {
            navigationRevision++; query = search.getText().toString().trim(); savePage(); refresh();
        }, search);
        catalog.addView(toolbar, new LayoutParams(-1, -2));
        LinearLayout selection = row(context); selection.setPadding(px(10), 0, px(10), 0);
        selectAll = UiCheckBox.createThemed(context, theme); selectAll.setContentDescription(tr("select_all"));
        selection.addView(UiTableStyle.selectionCell(context, selectAll), new LayoutParams(px(26), -1));
        count = new UiDisplayText(context, "", 12, theme.text().muted()); count.setGravity(Gravity.CENTER_VERTICAL);
        selection.addView(count, new LayoutParams(0, -1, 1)); catalog.addView(selection, new LayoutParams(-1, px(32)));
        selectAll.setOnCheckedChangeListener((view, value) -> {
            for (FactionDefinition faction : visible()) { if (value) checked.add(faction.id()); else checked.remove(faction.id()); }
            savePage(); updateSelection();
        });
        list = column(context); UiScrollView scroll = new UiScrollView(context, theme.scroll());
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2)); catalog.addView(scroll, new LayoutParams(-1, 0, 1));
        details = new FrameLayout(context);
        details.setBackground(fill(theme.surface().sidebar()));
        factionSplit = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.HORIZONTAL,
                UiFixedSplitLayout.Edge.FIRST, 260, 180, 300, theme); factionSplit.setPanes(catalog, details);
        addView(factionSplit, new LayoutParams(-1, 0, 1));
        status = UiTableStyle.statusBar(context, "", theme);
        addView(status, new LayoutParams(-1, px(UiTableStyle.STATUS_BAR_HEIGHT)));
        add.setOnClickListener(view -> create()); remove.setOnClickListener(view -> confirmDelete());
        subscription = new FactionCatalogSubscription(this, repository, this::refresh);
        factionSplit.addUserStateListener(this::saveSplit);
        restoreScope();
    }

    @Override public View view() { return this; }
    @Override public void onShow() {
        if (disposed || shown) return;
        shown = true; localError = ""; restoreScope(); subscription.open(); refresh();
    }
    @Override public void onHide() {
        if (!shown) return;
        savePage(); saveSplit(); shown = false; navigationRevision++; subscription.close();
        editors.values().forEach(editor -> editor.setActive(false));
    }
    @Override public void onDispose() {
        if (disposed) return; onHide(); disposed = true; generation++;
        editors.values().forEach(FactionEditorView::dispose); editors.clear();
    }
    private void restoreScope() {
        if (disposed || Objects.equals(scope, UiStateScopes.current())) return;
        savePage(); saveSplit(); generation++; writing = false; localError = "";
        editors.values().forEach(FactionEditorView::dispose); editors.clear(); details.removeAllViews();
        scope = UiStateScopes.current();
        var page = state.read(FactionViewState.PAGE, scope);
        selected = uuid(page.selected()); section = page.section(); query = page.query(); search.setText(query);
        checked.clear(); page.checked().forEach(value -> { UUID id = uuid(value); if (id != null) checked.add(id); });
        var split = state.read(FactionViewState.SPLIT, UiStateScopes.GLOBAL);
        factionSplit.restorePreferredExtentDp(split.extent());
        refresh();
        if (shown) repository.refresh();
    }
    private void refresh() {
        if (disposed) return;
        FactionCatalog catalog = repository.snapshot();
        // Before a scope transition has arrived on the UI thread, old callbacks cannot reveal a new world's catalog.
        if (!Objects.equals(scope, UiStateScopes.current())) return;
        Set<UUID> live = new HashSet<>(); catalog.factions().forEach(f -> live.add(f.id()));
        if (repository.loaded()) {
            checked.retainAll(live);
            if (selected == null || !live.contains(selected)) selected = catalog.factions().stream().findFirst().map(FactionDefinition::id).orElse(null);
            for (UUID id : List.copyOf(editors.keySet())) if (!live.contains(id)) {
                FactionEditorView editor = editors.remove(id); editor.dispose(); details.removeView(editor);
            }
        }
        List<FactionDefinition> visible = visible(); Set<UUID> displayed = new HashSet<>(); int index = 0;
        for (FactionDefinition faction : visible) {
            displayed.add(faction.id()); Row row = rows.computeIfAbsent(faction.id(), this::createRow);
            row.name().setText(FactionTextViews.display(faction.name())); row.name().setTooltipText(faction.name().plain());
            if (row.view().getParent() == null) list.addView(row.view(), index, new LayoutParams(-1, px(42)));
            index++;
        }
        for (UUID id : List.copyOf(rows.keySet())) if (!displayed.contains(id)) list.removeView(rows.remove(id).view());
        showDetail(); updateSelection();
        boolean editable = canEdit(); add.setEnabled(editable); remove.setEnabled(editable && !checked.isEmpty());
    }
    private void refreshStatus() {
        String error = localError.isEmpty() ? repository.error() : localError;
        // The members page already owns the shared entity table's result footer.
        status.setVisibility(error.isEmpty() && section.equals("members") && selected != null
                && repository.snapshot().find(selected).isPresent() ? GONE : VISIBLE);
        String results = Component.translatable("geometry_node.database.result_count", visible().size(), checked.size()).getString();
        status.setText(results + (!error.isEmpty() ? " · " + Component.translatable(error).getString()
                : repository.loading() || writing ? " · " + tr("saving") : ""));
        status.setTextColor(error.isEmpty() ? theme.text().muted() : theme.status().error());
    }
    private Row createRow(UUID id) {
        LinearLayout view = row(getContext()); view.setPadding(px(10), 0, px(10), 0);
        UiCheckBox check = UiCheckBox.createThemed(getContext(), theme);
        check.setOnCheckedChangeListener((button, value) -> { if (value) checked.add(id); else checked.remove(id); savePage(); updateSelection(); });
        view.addView(UiTableStyle.selectionCell(getContext(), check), new LayoutParams(px(26), -1));
        UiActionButton name = UiTableStyle.rowButton(getContext(), theme); name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        UIUtils.setLockedTextSize(name, UiMetrics.TEXT_BODY);
        name.setPadding(px(6), 0, 0, 0); name.setSingleLine(true);
        name.setOnClickListener(button -> { navigationRevision++; selected = id; savePage(); showDetail(); updateSelection(); });
        view.addView(name, new LayoutParams(0, -1, 1)); return new Row(view, check, name);
    }
    private void updateSelection() {
        long selectedVisible = visible().stream().filter(f -> checked.contains(f.id())).count();
        selectAll.setChecked(selectedVisible > 0 && selectedVisible == rows.size());
        selectAll.setIndeterminate(selectedVisible > 0 && selectedVisible < rows.size());
        selectAll.setEnabled(!rows.isEmpty()); count.setText(tr("count", visible().size(), checked.size()));
        rows.forEach((id, row) -> {
            row.check().setChecked(checked.contains(id));
            row.view().setBackground(fill(id.equals(selected) ? theme.selection().fill() : theme.surface().root()));
            row.name().setPalette(UiTableStyle.rowPalette(id.equals(selected), theme.surface().root(), theme));
        });
        remove.setEnabled(canEdit() && !checked.isEmpty());
        refreshStatus();
    }
    private void showDetail() {
        if (selected != null && repository.snapshot().find(selected).isPresent()) {
            FactionEditorView current = editors.computeIfAbsent(selected, id -> {
                var editor = new FactionEditorView(getContext(), theme, id, repository, state, scope, this::canEdit,
                        this::submit, next -> { navigationRevision++; section = next; savePage(); refreshStatus(); });
                details.addView(editor, new FrameLayout.LayoutParams(-1, -1)); return editor;
            });
            editors.forEach((id, editor) -> { boolean active = id.equals(selected); editor.setActive(shown && active); editor.setVisibility(active ? VISIBLE : GONE); });
            current.showSection(section); current.refresh();
        } else editors.values().forEach(editor -> { editor.setActive(false); editor.setVisibility(GONE); });
    }
    private List<FactionDefinition> visible() {
        String keyword = query.toLowerCase(Locale.ROOT);
        return repository.snapshot().factions().stream().filter(f ->
                f.name().plain().toLowerCase(Locale.ROOT).contains(keyword)).toList();
    }
    private boolean canEdit() {
        return shown && !disposed && !writing && repository.loaded() && !repository.loading()
                && Objects.equals(scope, UiStateScopes.current()) && !scope.equals("disconnected");
    }
    private void create() {
        if (!canEdit()) return;
        Set<UUID> before = new HashSet<>(); repository.snapshot().factions().forEach(f -> before.add(f.id()));
        String base = tr("new_faction"), name = base; int suffix = 2;
        while (!repository.snapshot().nameAvailable(RichTextValue.plain(name), null)) name = base + " " + suffix++;
        RichTextValue newName = RichTextValue.plain(name);
        long expectedGeneration = generation, expectedNavigation = navigationRevision;
        submit(() -> repository.create(newName).thenRun(() -> postToUi.accept(() -> {
            if (shown && !disposed && generation == expectedGeneration && navigationRevision == expectedNavigation
                    && Objects.equals(scope, UiStateScopes.current())) {
                repository.snapshot().factions().stream().filter(f -> !before.contains(f.id())).findFirst().ifPresent(f -> selected = f.id());
                query = ""; search.setText(""); section = "info"; savePage(); refresh();
            }
        })));
    }
    private void submit(Supplier<CompletableFuture<Void>> operation) {
        if (!canEdit()) return;
        writing = true; localError = ""; long expected = generation;
        try {
            // Navigation may retire the automatic selection, but must not strand command bookkeeping.
            operation.get().whenComplete((ignored, failed) -> postToUi.accept(() -> {
                if (disposed || generation != expected) return;
                writing = false;
                localError = failed == null ? "" : repository.error().isEmpty()
                        ? "geometry_node.faction.error.failed" : repository.error();
                refresh();
            }));
        } catch (RuntimeException failed) { writing = false; localError = "geometry_node.faction.error.invalid"; }
        refresh();
    }
    private void confirmDelete() {
        if (!canEdit() || checked.isEmpty()) return;
        OverlayHost host = OverlayHost.find(this); if (host == null) return;
        Set<UUID> ids = Set.copyOf(checked); String expected = scope;
        ModalWindowView dialog = new ModalWindowView(getContext(), tr("delete"), ModalWindowView.MovementMode.FIXED_CENTER,
                ModalWindowView.Style.forTheme(theme));
        dialog.bindSessionOwner(this); dialog.setPreferredSizeDp(480, 230);
        UiDisplayText message = new UiDisplayText(getContext(), tr("confirm_delete", ids.size()), 13, theme.text().normal());
        dialog.setContent(message); LinearLayout actions = row(getContext()); actions.setGravity(Gravity.RIGHT);
        UiActionButton cancel = new UiActionButton(getContext(), theme, tr("cancel"), UiActionButton.Role.SECONDARY, UiActionButton.Density.NORMAL, UiActionButton.Style.forTheme(theme));
        UiActionButton confirm = new UiActionButton(getContext(), theme, tr("delete"), UiActionButton.Role.DANGER, UiActionButton.Density.NORMAL, UiActionButton.Style.forTheme(theme));
        cancel.setOnClickListener(view -> dialog.requestClose());
        confirm.setOnClickListener(view -> {
            if (dialog.requestClose() && Objects.equals(expected, scope) && canEdit()) submit(() -> repository.delete(ids));
        });
        actions.addView(cancel, new LayoutParams(px(92), px(32))); LayoutParams action = new LayoutParams(px(92), px(32)); action.leftMargin = px(8);
        actions.addView(confirm, action); dialog.setActions(actions);
        host.showModal(dialog, new ModalOptions(false, true, this, theme.surface().modalScrim()));
    }
    private void savePage() {
        if (scope == null || disposed) return;
        state.write(FactionViewState.PAGE, scope, new FactionViewState.Page(selected == null ? "" : selected.toString(),
                section, query, checked.stream().map(UUID::toString).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))));
    }
    private void saveSplit() {
        if (scope != null && !disposed) state.write(FactionViewState.SPLIT, UiStateScopes.GLOBAL,
                new FactionViewState.Split(factionSplit.preferredExtentDp()));
    }
    private UiIconButton icon(Context context, SvgIconView.Icon icon, String key) { return UiIconButton.createThemed(context, theme, icon, tr(key)); }
    private static LinearLayout column(Context context) { LinearLayout view = new LinearLayout(context); view.setOrientation(VERTICAL); return view; }
    private static LinearLayout row(Context context) { LinearLayout view = new LinearLayout(context); view.setGravity(Gravity.CENTER_VERTICAL); view.setBaselineAligned(false); return view; }
    private static LayoutParams actionParams() { LayoutParams value = new LayoutParams(px(30), px(30)); value.leftMargin = px(6); return value; }
    private static UUID uuid(String value) { try { return UUID.fromString(value); } catch (IllegalArgumentException invalid) { return null; } }
}
