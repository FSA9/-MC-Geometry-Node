package com.mine.geometry_node.client.ui.components.integration.metadata;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchSubmit;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Handler;
import icyllis.modernui.core.Looper;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.View;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.Toast;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import static com.mine.geometry_node.client.ui.components.integration.metadata.MetadataViews.*;

/** Shared form and list; the modal wrapper or entity editor owns persistence and window chrome. */
final class UiDataDictionaryContent extends FrameLayout {
    private static final int PAGE_SIZE = 20;
    private final UiTheme theme;
    private final float textSizeDp;
    private final DataDictionarySession session;
    private final String initialKey;
    private final UiTextInput search;
    private String searchQuery = "";
    private final UiDataDictionaryEditor.CommitMode commitMode;
    private final Map<String, EntryRow> entryRows = new LinkedHashMap<>();
    private record EntryRow(LinearLayout row, UiCheckBox check, UiDisplayText name,
                            UiDisplayText summary, UiDisplayText type) {}
    private final UiSelectButton filterButton;
    private final LinearLayout rows;
    private final UiDisplayText pageLabel;
    private final UiDisplayText listCount;
    private final LinearLayout form;
    private final LinearLayout booleanRow;
    private final UiCheckBox booleanInput;
    private final UiDisplayText booleanLabel;
    private final LinearLayout viewSwitch;
    private final UiActionButton previewMode;
    private final UiActionButton editMode;
    private final UiActionButton restore;
    private final UiDisplayText message;
    private final UiDisplayText readonlyHint;
    private final UiTextInput keyInput;
    private final UiSelectButton valueType;
    private final UiTextInput valueInput;
    private final UiDisplayText tree;
    private final UiScrollView treeScroll;
    private final UiActionButton previous;
    private final UiActionButton next;
    private final UiIconButton add;
    private final UiIconButton deleteSelected;
    private final UiCheckBox selectAll;
    private final UiDisplayText selectedCount;
    private final Set<String> checkedKeys = new LinkedHashSet<>();
    private final boolean embedded;
    private final ViewGroup popupHost;
    private final LinearLayout actions;
    private Runnable pendingChangesChanged = () -> {};
    private final UiActionButton stage;
    private final UiActionButton reload;
    private DataDictionaryBinding.State state;
    private DataDictionaryDraft draft;
    private DataDictionaryDraft.Type filter;
    private DataDictionaryDraft.Type selectedType = DataDictionaryDraft.Type.STRING;
    private String selectedKey;
    private int page;
    private boolean active;
    private boolean disposed;
    private boolean updating;
    private boolean formDirty;
    private boolean adding;
    private boolean showingPreview;
    private boolean notifyingPendingChanges;
    private boolean suspended;
    private UiContextMenu selector;

    UiDataDictionaryContent(Context context, View owner, DataDictionaryBinding binding, String initialKey,
                            UiTheme theme, boolean embedded, Runnable close, ViewGroup popupHost) {
        this(context, owner, binding, initialKey, theme, embedded, close, popupHost, 11);
    }

    UiDataDictionaryContent(Context context, View owner, DataDictionaryBinding binding, String initialKey,
                            UiTheme theme, boolean embedded, Runnable close, ViewGroup popupHost, float textSizeDp) {
        this(context, owner, binding, initialKey, theme, embedded, close, popupHost, textSizeDp,
                UiDataDictionaryEditor.CommitMode.EXPLICIT);
    }

    UiDataDictionaryContent(Context context, View owner, DataDictionaryBinding binding, String initialKey,
                            UiTheme theme, boolean embedded, Runnable close, ViewGroup popupHost, float textSizeDp,
                            UiDataDictionaryEditor.CommitMode commitMode) {
        super(context);
        this.commitMode = Objects.requireNonNull(commitMode);
        if (!Float.isFinite(textSizeDp) || textSizeDp <= 0) throw new IllegalArgumentException("Invalid text size");
        this.textSizeDp = textSizeDp;
        this.theme = Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(owner, "owner");
        Looper looper = Looper.myLooper();
        Handler handler = new Handler(looper);
        session = new DataDictionarySession(binding, !embedded,
                action -> { if (Looper.myLooper() == looper) action.run(); else handler.post(action); },
                error -> reportError(context, error));
        this.initialKey = initialKey; this.embedded = embedded;
        this.popupHost = popupHost == null ? this : popupHost;
        setBackground(fill(theme.surface().panel()));
        LinearLayout root = column(context);
        LinearLayout toolbar = row(context); toolbar.setPadding(px(16), px(10), px(16), px(10));
        search = input(context, "search", true, theme); toolbar.addView(search, new LinearLayout.LayoutParams(0, px(30), 1));
        filterButton = new UiSelectButton(context, tr("all_types"), textSizeDp, () -> 2, theme);
        LinearLayout.LayoutParams filterParams = params(104, 30); filterParams.leftMargin = px(6);
        toolbar.addView(filterButton, filterParams);
        if (automatic()) {
            var submitSearch = UiIconButton.createThemed(context, theme, SvgIconView.Icon.SEARCH, tr("search"));
            LinearLayout.LayoutParams searchParams = params(30, 30); searchParams.leftMargin = px(6);
            toolbar.addView(submitSearch, searchParams);
            UiSearchSubmit.bind(submitSearch, () -> {
                searchQuery = search.getText().toString(); page = 0; renderList();
            }, search);
        }
        filterButton.setOnClickListener(view -> choose(filterButton, true, type -> {
            filter = type; page = 0; filterButton.setValue(type == null ? tr("all_types") : typeLabel(type)); renderList();
        }));
        add = UiIconButton.createThemed(context, theme, SvgIconView.Icon.PLUS, tr("add"));
        add.setOnClickListener(view -> newEntry());
        LinearLayout.LayoutParams addParams = params(30, 30); addParams.leftMargin = px(6); toolbar.addView(add, addParams);
        deleteSelected = UiIconButton.createThemed(context, theme, SvgIconView.Icon.CLEAR, tr("remove"));
        deleteSelected.setOnClickListener(view -> removeSelected());
        LinearLayout.LayoutParams deleteParams = params(30, 30); deleteParams.leftMargin = px(6);
        toolbar.addView(deleteSelected, deleteParams);
        root.addView(toolbar, params(-1, 50)); root.addView(divider(context), params(-1, 1));

        LinearLayout content = row(context);
        LinearLayout left = column(context);
        LinearLayout listHeading = row(context); listHeading.setPadding(px(16), 0, px(16), 0);
        listHeading.setBackground(fill(theme.surface().panel()));
        selectAll = UiCheckBox.createThemed(context, theme);
        selectAll.setContentDescription(tr("select_all"));
        LinearLayout.LayoutParams checkParams = params(14, 14); checkParams.rightMargin = px(10);
        listHeading.addView(selectAll, checkParams);
        selectAll.setOnCheckedChangeListener((view, checked) -> {
            if (!editable()) return;
            if (checked) checkedKeys.addAll(matchingKeys()); else checkedKeys.removeAll(matchingKeys());
            renderList();
        });
        listCount = text(context, "", theme.text().muted());
        listHeading.addView(listCount, new LinearLayout.LayoutParams(0, -1, 1));
        selectedCount = text(context, "", theme.text().muted());
        listHeading.addView(selectedCount, params(-2, -1));
        left.addView(listHeading, params(-1, 28)); left.addView(divider(context), params(-1, 1));
        rows = column(context); UiScrollView listScroll = new UiScrollView(context, theme.scroll());
        listScroll.setHorizontalScrollBarEnabled(false);
        listScroll.addView(rows, new FrameLayout.LayoutParams(-1, -2));
        left.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        left.addView(divider(context), params(-1, 1));
        LinearLayout pages = row(context); pages.setPadding(px(12), 0, px(12), 0);
        previous = themedButton(context, "previous", () -> { if (page > 0) { page--; renderList(); } });
        next = themedButton(context, "next", () -> { page++; renderList(); });
        previous.setText("‹"); next.setText("›"); previous.setContentDescription(tr("previous")); next.setContentDescription(tr("next"));
        previous.setPadding(0, 0, 0, 0); next.setPadding(0, 0, 0, 0);
        pageLabel = text(context, "", theme.text().muted());
        pages.addView(pageLabel, new LinearLayout.LayoutParams(0, -1, 1));
        pages.addView(previous, params(24, 24));
        LinearLayout.LayoutParams nextParams = params(24, 24); nextParams.leftMargin = px(4); pages.addView(next, nextParams);
        left.addView(pages, params(-1, 36));
        content.addView(left, new LinearLayout.LayoutParams(0, -1, 0.4f)); content.addView(divider(context), params(1, -1));

        LinearLayout right = column(context); right.setPadding(px(20), px(16), px(20), px(16));
        readonlyHint = text(context, tr("readonly"), theme.text().muted()); right.addView(readonlyHint, params(-1, 28));
        form = column(context); right.addView(form, params(-1, -2));
        LinearLayout meta = row(context);
        LinearLayout keyColumn = column(context);
        keyColumn.addView(text(context, tr("key"), theme.text().muted()), params(-1, 24));
        keyInput = input(context, "key", true, theme); keyColumn.addView(keyInput, params(-1, 30));
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, -2, 1); keyParams.rightMargin = px(12);
        meta.addView(keyColumn, keyParams);
        LinearLayout typeColumn = column(context);
        typeColumn.addView(text(context, tr("type"), theme.text().muted()), params(-1, 24));
        valueType = new UiSelectButton(context, typeLabel(selectedType), textSizeDp, () -> 2, theme);
        typeColumn.addView(valueType, params(-1, 30)); meta.addView(typeColumn, params(108, -2));
        form.addView(meta, params(-1, -2));
        LinearLayout valueHeading = row(context);
        valueHeading.addView(text(context, tr("value"), theme.text().muted()), new LinearLayout.LayoutParams(0, -1, 1));
        viewSwitch = row(context);
        previewMode = themedButton(context, "preview", () -> setPreviewMode(true));
        editMode = themedButton(context, "edit", () -> setPreviewMode(false));
        viewSwitch.addView(previewMode, params(48, 24)); viewSwitch.addView(editMode, params(48, 24));
        valueHeading.addView(viewSwitch, params(-2, -1));
        LinearLayout.LayoutParams valueHeadingParams = params(-1, 28); valueHeadingParams.topMargin = px(14);
        form.addView(valueHeading, valueHeadingParams);
        valueInput = input(context, "value", false, theme); form.addView(valueInput, params(-1, 150));
        booleanRow = row(context); booleanRow.setPadding(px(10), 0, px(10), 0);
        ShapeDrawable booleanBackground = fill(theme.control().input()); booleanBackground.setStroke(px(1), theme.border().subtle());
        booleanRow.setBackground(booleanBackground);
        booleanInput = UiCheckBox.createThemed(context, theme); booleanInput.setContentDescription(tr("value"));
        booleanRow.addView(booleanInput, params(14, 14));
        booleanLabel = text(context, "false", theme.text().normal());
        LinearLayout.LayoutParams booleanLabelParams = params(-2, -1); booleanLabelParams.leftMargin = px(8);
        booleanRow.addView(booleanLabel, booleanLabelParams); form.addView(booleanRow, params(-1, 36));
        booleanRow.setOnClickListener(view -> booleanInput.performClick());
        booleanInput.setOnCheckedChangeListener((view, checked) -> {
            valueInput.setText(Boolean.toString(checked));
            // UiCheckBox only notifies for user clicks; setChecked during form loading is silent.
            if (automatic()) commitFocusedForm();
            else if (!adding && selectedKey != null) saveEntry();
        });
        valueType.setOnClickListener(view -> choose(valueType, false, type -> {
            updating = true;
            try {
                selectedType = type; valueType.setValue(typeLabel(type)); formDirty = true; showingPreview = false;
                valueInput.setText(defaultValue(type)); updateFormLayout(); updateButtons();
            } finally { updating = false; }
            if (automatic() && !adding) commitFocusedForm();
        }));
        tree = text(context, "", theme.text().secondary()); tree.setPadding(px(8), px(8), px(8), px(8));
        treeScroll = new UiScrollView(context, theme.scroll()); treeScroll.setBackground(fill(theme.control().input()));
        treeScroll.addView(tree, new FrameLayout.LayoutParams(-1, -2)); form.addView(treeScroll, params(-1, 180));
        View editDivider = divider(context); LinearLayout.LayoutParams editDividerParams = params(-1, 1); editDividerParams.topMargin = px(16);
        if (!automatic()) form.addView(editDivider, editDividerParams);
        LinearLayout edits = row(context);
        stage = themedButton(context, "stage", this::saveEntry);
        restore = themedButton(context, "restore", this::restoreForm);
        edits.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
        int entryActionWidth = Math.max(72, Math.round(textSizeDp * 4 + 24));
        edits.addView(restore, params(entryActionWidth, 28));
        LinearLayout.LayoutParams stageParams = params(entryActionWidth, 28); stageParams.leftMargin = px(6); edits.addView(stage, stageParams);
        LinearLayout.LayoutParams editsParams = params(-1, 28); editsParams.topMargin = px(12);
        if (!automatic()) form.addView(edits, editsParams);
        UiScrollView rightScroll = new UiScrollView(context, theme.scroll()); rightScroll.setHorizontalScrollBarEnabled(false);
        rightScroll.addView(right, new FrameLayout.LayoutParams(-1, -2));
        content.addView(rightScroll, new LinearLayout.LayoutParams(0, -1, 0.6f));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        addView(root, new FrameLayout.LayoutParams(-1, -1));
        message = text(context, "", theme.text().muted()); message.setMaxLines(2);
        message.setEllipsize(TextUtils.TruncateAt.END);
        if (embedded) {
            actions = null;
            message.setPadding(px(16), px(4), px(16), px(4));
            if (automatic()) {
                LinearLayout status = row(context);
                status.addView(message, new LinearLayout.LayoutParams(0, -2, 1));
                reload = themedButton(context, "reload", this::reloadDraft);
                status.addView(reload, params(92, 28));
                root.addView(status, params(-1, -2));
            } else {
                reload = null;
                root.addView(message, params(-1, -2));
            }
        } else {
            actions = row(context); actions.addView(message, new LinearLayout.LayoutParams(0, -1, 1));
            reload = themedButton(context, "reload", this::reloadDraft);
            actions.addView(reload, params(76, 28));
        }
        if (!automatic()) watch(search, () -> { searchQuery = search.getText().toString(); page = 0; renderList(); });
        watch(keyInput, this::formChanged); watch(valueInput, this::formChanged);
        keyInput.setEditActions(this::saveEntry, null);
        if (automatic()) {
            keyInput.setEditActions(() -> commitFocusedForm(true), this::restoreForm);
            valueInput.setEditActions(() -> commitFocusedForm(true), this::restoreForm);
            // Completing the key of a new entry must let the user enter its value first.
            keyInput.setOnFocusChangeListener((view, focused) -> { if (!focused && !adding) commitFocusedForm(); });
            valueInput.setOnFocusChangeListener((view, focused) -> { if (!focused) commitFocusedForm(); });
            valueInput.setOnKeyListener((view, key, event) -> {
                if ((key != KeyEvent.KEY_ENTER && key != KeyEvent.KEY_KP_ENTER) || !event.isControlPressed()) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) commitFocusedForm(true);
                return true;
            });
        }
        session.observe(() -> { if (active) receive(session.state()); });
        receive(session.state());
    }

    LinearLayout actions() { return actions; }
    @Override protected void onDetachedFromWindow() { suspend(); super.onDetachedFromWindow(); }
    void start() {
        if (active || disposed) return;
        active = true;
        session.start();
        receive(session.state());
        if (!embedded) session.refresh();
    }
    void suspend() { suspended = true; rememberForm(); dismissSelector(); keyInput.clearFocus(); valueInput.clearFocus(); }
    void resume() { suspended = false; if (active) session.refreshCommitted(); }
    void refreshCommitted() { session.refreshCommitted(); }
    void dispose() {
        if (disposed) return;
        disposed = true; active = false;
        dismissSelector();
        pendingChangesChanged = () -> {};
        session.close();
        draft = null; selectedKey = null; adding = false; formDirty = false;
        entryRows.clear();
        updating = true;
        try { keyInput.setText(""); valueInput.setText(""); tree.setText(""); }
        finally { updating = false; }
    }
    private void reloadDraft() { rememberForm(); session.reload(); }
    private void receive(DataDictionaryBinding.State next) {
        if (formDirty) rememberForm();
        state = next;
        DataDictionaryDraft previousDraft = draft;
        draft = session.draft();
        if (draft != null && previousDraft != draft) {
            boolean firstLoad = previousDraft == null;
            int previousPage = page;
            checkedKeys.retainAll(session.entryKeys("", null));
            if (!adding && !formDirty) {
                String key = selectedKey;
                if (firstLoad && initialKey != null && draft.values().containsKey(initialKey)) key = initialKey;
                if (key == null || !draft.values().containsKey(key))
                    key = draft.values().keySet().stream().findFirst().orElse(null);
                // A commit acknowledgement does not reset caret/focus or clean form text.
                if (firstLoad || !Objects.equals(key, selectedKey)
                        || previousDraft != null && !Objects.equals(previousDraft.values().get(key), draft.values().get(key)))
                    loadForm(key);
            }
            if (firstLoad) alignSelectionPage(); else page = previousPage;
        }
        message.setText(!session.error().isEmpty() ? session.error() : session.submitting() ? tr("saving") : status(next));
        renderList(); updateButtons();
    }
    private void alignSelectionPage() {
        if (draft == null || selectedKey == null) return;
        int index = matchingKeys().indexOf(selectedKey);
        if (index >= 0) page = index / PAGE_SIZE;
    }
    private void renderList() {
        if (rows == null) return;
        List<String> keys = matchingKeys();
        updateSelection(keys);
        int pages = Math.max(1, (keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        pageLabel.setText(tr("page", page + 1, pages));
        listCount.setText(tr("count", keys.size())); previous.setEnabled(page > 0); next.setEnabled(page + 1 < pages);
        int start = page * PAGE_SIZE;
        List<String> visible = keys.subList(start, Math.min(start + PAGE_SIZE, keys.size()));
        for (String key : List.copyOf(entryRows.keySet())) if (!visible.contains(key))
            rows.removeView(entryRows.remove(key).row());
        for (int index = 0; index < visible.size(); index++) {
            String key = visible.get(index);
            EntryRow entry = entryRows.computeIfAbsent(key, this::createEntryRow);
            entry.check().setChecked(checkedKeys.contains(key)); entry.check().setEnabled(editable());
            entry.row().setBackground(Objects.equals(key, selectedKey) ? fill(theme.selection().fill()) : null);
            var orphan = session.orphanForm(key);
            entry.name().setText(orphan == null ? key : key + " · " + tr("dirty"));
            entry.summary().setText(orphan == null ? DataDictionaryDraft.summary(draft.values().get(key)) : orphan.valueText());
            entry.type().setText(typeLabel(orphan == null ? DataDictionaryDraft.type(draft.values().get(key)) : orphan.type()));
            if (entry.row().getParent() == null) rows.addView(entry.row(), index, params(-1, 50));
        }
        boolean reordered = false;
        for (int index = 0; index < visible.size(); index++)
            reordered |= rows.getChildAt(index) != entryRows.get(visible.get(index)).row();
        if (reordered) for (String key : visible) rows.bringChildToFront(entryRows.get(key).row());
    }
    private EntryRow createEntryRow(String key) {
        LinearLayout row = row(getContext()); row.setPadding(px(16), px(5), px(16), px(5));
        UiCheckBox checked = UiCheckBox.createThemed(getContext(), theme);
        checked.setContentDescription(tr("select_entry", key));
        checked.setOnCheckedChangeListener((view, selected) -> {
            if (!editable()) return;
            if (selected) checkedKeys.add(key); else checkedKeys.remove(key);
            updateSelection(matchingKeys());
        });
        LinearLayout.LayoutParams checkedParams = params(14, 14); checkedParams.rightMargin = px(10);
        row.addView(checked, checkedParams);
        LinearLayout entry = column(getContext()); row.addView(entry, new LinearLayout.LayoutParams(0, -1, 1));
        UiDisplayText name = text(getContext(), key, theme.text().normal()); name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        UiDisplayText summary = text(getContext(), "", theme.text().muted()); summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout heading = row(getContext()); heading.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
        UiDisplayText type = text(getContext(), "", theme.text().muted());
        LinearLayout.LayoutParams typeParams = params(-2, -1); typeParams.leftMargin = px(8); heading.addView(type, typeParams);
        entry.addView(heading, params(-1, 22)); entry.addView(summary, params(-1, 19));
        EntryRow binding = new EntryRow(row, checked, name, summary, type);
        row.setOnClickListener(view -> {
            if (!active || disposed || entryRows.get(key) != binding) return;
            if (automatic()) commitFocusedForm();
            rememberForm(); loadForm(key); renderList();
        });
        return binding;
    }
    private void rememberForm() {
        if (selectedKey == null && !adding || !active || updating) return;
        session.remember(adding ? null : selectedKey, new DataDictionarySession.TypedFormState(
                keyInput.getText().toString(), selectedType, valueInput.getText().toString(),
                formDirty, showingPreview, keyInput.getSelectionStart(), keyInput.getSelectionEnd(),
                valueInput.getSelectionStart(), valueInput.getSelectionEnd()));
    }
    private void loadForm(String key) { loadForm(key, false); }
    private void loadForm(String key, boolean newEntry) {
        updating = true;
        try {
            selectedKey = key; adding = newEntry; formDirty = false; showingPreview = false;
            Object value = key != null && draft != null ? draft.values().get(key) : "";
            selectedType = DataDictionaryDraft.type(value);
            DataDictionarySession.TypedFormState saved = key != null || newEntry ? session.form(key) : null;
            if (saved != null) {
                selectedType = saved.type(); formDirty = saved.dirty(); showingPreview = saved.preview();
                keyInput.setText(saved.keyText()); valueInput.setText(saved.valueText());
            } else {
                keyInput.setText(key == null ? "" : key);
                valueInput.setText(DataDictionaryDraft.format(value));
            }
            keyInput.setErrorMessage(null); valueInput.setErrorMessage(null);
            valueType.setValue(typeLabel(selectedType));
            try { tree.setText(DataDictionaryDraft.tree(DataDictionaryDraft.parse(selectedType, valueInput.getText().toString(), value))); }
            catch (IllegalArgumentException invalid) { tree.setText(""); showingPreview = false; }
            updateFormLayout();
            if (saved != null) {
                keyInput.setSelection(Math.max(0, saved.keyStart()), Math.max(0, saved.keyEnd()));
                valueInput.setSelection(Math.max(0, saved.valueStart()), Math.max(0, saved.valueEnd()));
            }
        } finally { updating = false; }
        updateButtons();
    }
    private void updateFormLayout() {
        boolean composite = selectedType == DataDictionaryDraft.Type.DICTIONARY || selectedType == DataDictionaryDraft.Type.LIST;
        boolean bool = selectedType == DataDictionaryDraft.Type.BOOLEAN;
        boolean multiline = composite || selectedType == DataDictionaryDraft.Type.STRING;
        valueInput.setSingleLine(!multiline);
        valueInput.setLayoutParams(params(-1, multiline ? 150 : 32));
        valueInput.setVisibility(bool || composite && showingPreview ? GONE : VISIBLE);
        booleanRow.setVisibility(bool ? VISIBLE : GONE);
        booleanInput.setChecked(Boolean.parseBoolean(valueInput.getText().toString()));
        booleanLabel.setText(Boolean.toString(booleanInput.isChecked()));
        viewSwitch.setVisibility(composite ? VISIBLE : GONE);
        treeScroll.setVisibility(composite && showingPreview ? VISIBLE : GONE);
        previewMode.setPalette(UiActionButton.Palette.toggle(theme, showingPreview));
        editMode.setPalette(UiActionButton.Palette.toggle(theme, !showingPreview));
    }
    private void setPreviewMode(boolean preview) {
        if (preview) {
            try {
                Object old = draft != null && selectedKey != null ? draft.values().get(selectedKey) : null;
                tree.setText(DataDictionaryDraft.tree(DataDictionaryDraft.parse(selectedType, valueInput.getText().toString(), old)));
            } catch (IllegalArgumentException invalid) { valueInput.setErrorMessage(tr("invalid")); return; }
        }
        showingPreview = preview; updateFormLayout();
    }
    private void restoreForm() {
        if (!editable()) return;
        boolean wasAdding = adding;
        session.forget(wasAdding ? null : selectedKey);
        if (!wasAdding && !draft.values().containsKey(selectedKey))
            selectedKey = draft.values().keySet().stream().findFirst().orElse(null);
        loadForm(selectedKey, wasAdding);
        message.setText(status(state)); renderList(); updateButtons();
    }
    private void formChanged() {
        if (updating) return;
        formDirty = true;
        booleanInput.setChecked(Boolean.parseBoolean(valueInput.getText().toString()));
        booleanLabel.setText(Boolean.toString(booleanInput.isChecked()));
        try {
            Object old = draft != null && selectedKey != null ? draft.values().get(selectedKey) : null;
            tree.setText(DataDictionaryDraft.tree(DataDictionaryDraft.parse(selectedType, valueInput.getText().toString(), old)));
            valueInput.setErrorMessage(null);
        } catch (IllegalArgumentException invalid) { valueInput.setErrorMessage(tr("invalid")); }
        updateButtons();
    }
    /** Submit validated, completed input. Suspension and closure never parse unfinished forms. */
    boolean saveEntry() {
        if (notifyingPendingChanges || !active || session.submitting()) return false;
        if (!formDirty) return session.submit();
        if (draft == null || !editable() || !selectedType.editable()) return false;
        try {
            String key = keyInput.getText().toString();
            Object old = selectedKey == null ? null : draft.values().get(selectedKey);
            Object value = DataDictionaryDraft.parse(selectedType, valueInput.getText().toString(), old);
            draft.put(adding || !draft.values().containsKey(selectedKey) ? null : selectedKey, key, value);
            session.forget(adding ? null : selectedKey);
            if (!adding && checkedKeys.remove(selectedKey)) checkedKeys.add(key);
            selectedKey = key; adding = false; formDirty = false;
            keyInput.setErrorMessage(null); valueInput.setErrorMessage(null);
            alignSelectionPage();
            renderList(); updateButtons(); return session.submit();
        } catch (IllegalArgumentException invalid) {
            String reason = "duplicate".equals(invalid.getMessage()) ? "duplicate" : "invalid";
            message.setText(tr(reason)); valueInput.setErrorMessage(tr(reason)); return false;
        }
    }
    private void newEntry() {
        if (draft == null || !active) return;
        rememberForm(); loadForm(null, true);
        updateButtons(); keyInput.requestFocus();
    }
    private List<String> matchingKeys() {
        return session.entryKeys(searchQuery, filter);
    }
    private boolean automatic() { return commitMode == UiDataDictionaryEditor.CommitMode.ON_FOCUS_LOSS; }
    private void commitFocusedForm() { commitFocusedForm(false); }
    private void commitFocusedForm(boolean confirmEmptyValue) {
        if (suspended || updating || disposed || !formDirty || !editable()) return;
        if (adding && (keyInput.getText().toString().isBlank()
                || !confirmEmptyValue && valueInput.getText().toString().isEmpty())) return;
        saveEntry();
    }
    private void updateSelection(List<String> matching) {
        long selected = matching.stream().filter(checkedKeys::contains).count();
        selectAll.setChecked(!matching.isEmpty() && selected == matching.size());
        selectAll.setIndeterminate(selected > 0 && selected < matching.size());
        selectAll.setEnabled(editable() && !matching.isEmpty());
        selectedCount.setText(checkedKeys.isEmpty() ? "" : tr("selected_count", checkedKeys.size()));
        deleteSelected.setEnabled(editable() && !checkedKeys.isEmpty());
    }
    private void removeSelected() {
        if (!editable() || draft == null || checkedKeys.isEmpty()) return;
        Set<String> deleting = new LinkedHashSet<>(checkedKeys);
        boolean deletingCurrent = !adding && deleting.contains(selectedKey);
        deleting.forEach(key -> { draft.remove(key); session.forget(key); });
        checkedKeys.removeAll(deleting);
        if (deletingCurrent) loadForm(draft.values().keySet().stream().findFirst().orElse(null));
        renderList(); updateButtons();
        session.submit();
    }
    boolean hasPendingChanges() { return formDirty || session.formDirty() || session.pendingCompleted(); }
    void setOnPendingChangesChanged(Runnable listener) {
        pendingChangesChanged = listener == null ? () -> {} : listener;
    }
    private boolean editable() { return active && session.editable(); }
    private void updateButtons() {
        boolean canEdit = editable() && draft != null;
        boolean selected = selectedKey != null || adding;
        form.setVisibility(selected ? VISIBLE : GONE);
        readonlyHint.setVisibility(selected && !selectedType.editable() ? VISIBLE : GONE);
        booleanInput.setEnabled(canEdit && selected); booleanRow.setEnabled(canEdit && selected);
        restore.setEnabled(canEdit && selected && formDirty);
        keyInput.setEnabled(canEdit && selected && selectedType.editable());
        valueType.setEnabled(canEdit && selected && selectedType.editable());
        valueInput.setEnabled(canEdit && selected && selectedType.editable());
        add.setEnabled(canEdit); updateSelection(matchingKeys());
        if (reload != null) {
            reload.setEnabled(session.canReload() && (state == null || !state.saving()));
            if (embedded) reload.setVisibility(session.error().isEmpty() ? GONE : VISIBLE);
        }
        stage.setEnabled(canEdit && (formDirty || draft.dirty()) && selectedType.editable());
        if (message.getText().isEmpty() || message.getText().toString().equals(tr("dirty"))) {
            message.setText(formDirty || draft != null && draft.dirty() ? tr("dirty") : "");
        }
        if (!notifyingPendingChanges) {
            notifyingPendingChanges = true;
            try { pendingChangesChanged.run(); }
            finally { notifyingPendingChanges = false; }
        }
    }
    boolean onEscapePressed() {
        if (selector == null || !selector.isOpen()) return false;
        dismissSelector(); return true;
    }
    private void choose(View anchor, boolean all, Consumer<DataDictionaryDraft.Type> selected) {
        dismissSelector();
        UiContextMenu menu = new UiContextMenu(getContext(), theme);
        if (all) menu.addMenuItem(tr("all_types"), () -> selected.accept(null));
        for (DataDictionaryDraft.Type type : DataDictionaryDraft.Type.values()) {
            if (!all && !type.editable()) continue;
            menu.addMenuItem(typeLabel(type), () -> selected.accept(type));
        }
        selector = menu;
        // Keep the popup in the owning window's elevated subtree, above this content.
        menu.showBelow(anchor, popupHost);
    }
    private static void reportError(Context context, String error) {
        System.getLogger(UiDataDictionaryContent.class.getName()).log(System.Logger.Level.ERROR, error);
        Toast.makeText(context, error, Toast.LENGTH_LONG).show();
    }
    private static ShapeDrawable fill(int color) {
        ShapeDrawable background = new ShapeDrawable(); background.setColor(color); return background;
    }
    private UiActionButton themedButton(Context context, String key, Runnable action) {
        UiActionButton button = button(context, key, action, theme);
        UIUtils.setLockedTextSize(button, textSizeDp);
        return button;
    }
    private UiDisplayText text(Context context, String value, int color) {
        UiDisplayText text = MetadataViews.text(context, value, color);
        UIUtils.setLockedTextSize(text, textSizeDp);
        return text;
    }
    private UiTextInput input(Context context, String hint, boolean singleLine, UiTheme theme) {
        UiTextInput input = MetadataViews.input(context, hint, singleLine, theme);
        UIUtils.setLockedTextSize(input, textSizeDp);
        return input;
    }
    private View divider(Context context) {
        View view = new View(context); view.setBackground(fill(theme.border().divider())); return view;
    }
    private void dismissSelector() { if (selector != null) { selector.dismiss(); selector = null; } }
    private static String typeLabel(DataDictionaryDraft.Type type) { return tr("type." + type.id()); }
    private static String defaultValue(DataDictionaryDraft.Type type) {
        return switch (type) {
            case BOOLEAN -> "false";
            case INTEGER, LONG, FLOAT, DOUBLE -> "0";
            case DICTIONARY -> "{}";
            case LIST -> "[]";
            case UUID -> "00000000-0000-0000-0000-000000000000";
            default -> "";
        };
    }
}
