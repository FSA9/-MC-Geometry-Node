package com.mine.geometry_node.client.ui.components.integration.metadata;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
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
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import static com.mine.geometry_node.client.ui.components.integration.metadata.MetadataViews.*;

/** Shared, bounded custom-data manager. The binding owns transport, validation and atomic persistence. */
public final class UiDataDictionaryEditor extends ModalWindowView {
    private static final int PAGE_SIZE = 20;
    private final UiTheme theme;
    private final DataDictionaryBinding binding;
    private final View owner;
    private final String initialKey;
    private final UiTextInput search;
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
    private final UiDisplayText selectionHint;
    private final UiTextInput keyInput;
    private final UiSelectButton valueType;
    private final UiTextInput valueInput;
    private final UiDisplayText tree;
    private final UiScrollView treeScroll;
    private final UiActionButton previous;
    private final UiActionButton next;
    private final UiActionButton add;
    private final UiActionButton remove;
    private final UiActionButton stage;
    private final UiActionButton apply;
    private final UiActionButton reload;
    private final UiActionButton discard;
    private final UiActionButton keep;
    private final Runnable changed;
    private final OnAttachStateChangeListener ownerListener;
    private DataDictionaryBinding.State state;
    private DataDictionaryDraft draft;
    private DataDictionaryDraft.Type filter;
    private DataDictionaryDraft.Type selectedType = DataDictionaryDraft.Type.STRING;
    private String selectedKey;
    private int page;
    private volatile long generation;
    private boolean active;
    private boolean updating;
    private boolean formDirty;
    private boolean adding;
    private boolean showingPreview;
    private boolean saving;
    private boolean allowClose;
    private Runnable pendingDiscard;
    private UiContextMenu selector;

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
        UiDataDictionaryEditor editor = new UiDataDictionaryEditor(anchor.getContext(), anchor, title, binding, initialKey, theme);
        return host.showModal(editor, new ModalOptions(false, true, anchor, theme.surface().modalScrim())) != null;
    }

    UiDataDictionaryEditor(Context context, View owner, String title, DataDictionaryBinding binding, String initialKey) {
        this(context, owner, title, binding, initialKey, UiThemes.EDITOR);
    }
    UiDataDictionaryEditor(Context context, View owner, String title, DataDictionaryBinding binding, String initialKey, UiTheme theme) {
        super(context, tr("title"), MovementMode.DRAGGABLE, Style.forTheme(theme));
        this.theme = Objects.requireNonNull(theme, "theme");
        this.owner = owner; this.binding = binding; this.initialKey = initialKey;
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
        LinearLayout root = column(context);
        LinearLayout toolbar = row(context); toolbar.setPadding(px(16), px(10), px(16), px(10));
        search = input(context, "search", true, theme); toolbar.addView(search, new LayoutParams(0, px(30), 1));
        filterButton = new UiSelectButton(context, tr("all_types"), 11, () -> 2, theme);
        LayoutParams filterParams = params(104, 30); filterParams.leftMargin = px(6);
        toolbar.addView(filterButton, filterParams);
        filterButton.setOnClickListener(view -> choose(filterButton, true, type -> {
            filter = type; page = 0; filterButton.setValue(type == null ? tr("all_types") : typeLabel(type)); renderList();
        }));
        add = themedButton(context, "add", this::newEntry);
        LayoutParams addParams = params(64, 30); addParams.leftMargin = px(6); toolbar.addView(add, addParams);
        root.addView(toolbar, params(-1, 50)); root.addView(divider(context), params(-1, 1));

        LinearLayout content = row(context);
        LinearLayout left = column(context);
        LinearLayout listHeading = row(context); listHeading.setPadding(px(16), 0, px(16), 0);
        listHeading.setBackground(fill(theme.surface().panel()));
        listCount = text(context, "", theme.text().muted());
        listHeading.addView(listCount, new LayoutParams(0, -1, 1));
        listHeading.addView(text(context, tr("key_value"), theme.text().muted()), params(-2, -1));
        left.addView(listHeading, params(-1, 28)); left.addView(divider(context), params(-1, 1));
        rows = column(context); UiScrollView listScroll = new UiScrollView(context, theme.scroll());
        listScroll.setHorizontalScrollBarEnabled(false);
        listScroll.addView(rows, new FrameLayout.LayoutParams(-1, -2));
        left.addView(listScroll, new LayoutParams(-1, 0, 1));
        left.addView(divider(context), params(-1, 1));
        LinearLayout pages = row(context); pages.setPadding(px(12), 0, px(12), 0);
        previous = themedButton(context, "previous", () -> { if (page > 0) { page--; renderList(); } });
        next = themedButton(context, "next", () -> { page++; renderList(); });
        previous.setText("‹"); next.setText("›"); previous.setContentDescription(tr("previous")); next.setContentDescription(tr("next"));
        previous.setPadding(0, 0, 0, 0); next.setPadding(0, 0, 0, 0);
        pageLabel = text(context, "", theme.text().muted());
        pages.addView(pageLabel, new LayoutParams(0, -1, 1));
        pages.addView(previous, params(24, 24));
        LayoutParams nextParams = params(24, 24); nextParams.leftMargin = px(4); pages.addView(next, nextParams);
        left.addView(pages, params(-1, 36));
        content.addView(left, new LayoutParams(0, -1, 0.4f)); content.addView(divider(context), params(1, -1));

        LinearLayout right = column(context); right.setPadding(px(20), px(16), px(20), px(16));
        selectionHint = text(context, tr("select"), theme.text().muted()); right.addView(selectionHint, params(-1, 28));
        form = column(context); right.addView(form, params(-1, -2));
        LinearLayout meta = row(context);
        LinearLayout keyColumn = column(context);
        keyColumn.addView(text(context, tr("key"), theme.text().muted()), params(-1, 24));
        keyInput = input(context, "key", true, theme); keyColumn.addView(keyInput, params(-1, 30));
        LayoutParams keyParams = new LayoutParams(0, -2, 1); keyParams.rightMargin = px(12);
        meta.addView(keyColumn, keyParams);
        LinearLayout typeColumn = column(context);
        typeColumn.addView(text(context, tr("type"), theme.text().muted()), params(-1, 24));
        valueType = new UiSelectButton(context, typeLabel(selectedType), 11, () -> 2, theme);
        typeColumn.addView(valueType, params(-1, 30)); meta.addView(typeColumn, params(108, -2));
        form.addView(meta, params(-1, -2));
        LinearLayout valueHeading = row(context);
        valueHeading.addView(text(context, tr("value"), theme.text().muted()), new LayoutParams(0, -1, 1));
        viewSwitch = row(context);
        previewMode = themedButton(context, "preview", () -> setPreviewMode(true));
        editMode = themedButton(context, "edit", () -> setPreviewMode(false));
        viewSwitch.addView(previewMode, params(48, 24)); viewSwitch.addView(editMode, params(48, 24));
        valueHeading.addView(viewSwitch, params(-2, -1));
        LayoutParams valueHeadingParams = params(-1, 28); valueHeadingParams.topMargin = px(14);
        form.addView(valueHeading, valueHeadingParams);
        valueInput = input(context, "value", false, theme); form.addView(valueInput, params(-1, 150));
        booleanRow = row(context); booleanRow.setPadding(px(10), 0, px(10), 0);
        ShapeDrawable booleanBackground = fill(theme.control().input()); booleanBackground.setStroke(px(1), theme.border().subtle());
        booleanRow.setBackground(booleanBackground);
        booleanInput = UiCheckBox.createThemed(context, theme); booleanInput.setContentDescription(tr("value"));
        booleanRow.addView(booleanInput, params(14, 14));
        booleanLabel = text(context, "false", theme.text().normal());
        LayoutParams booleanLabelParams = params(-2, -1); booleanLabelParams.leftMargin = px(8);
        booleanRow.addView(booleanLabel, booleanLabelParams); form.addView(booleanRow, params(-1, 36));
        booleanRow.setOnClickListener(view -> booleanInput.performClick());
        booleanInput.setOnCheckedChangeListener((view, checked) -> valueInput.setText(Boolean.toString(checked)));
        valueType.setOnClickListener(view -> choose(valueType, false, type -> {
            selectedType = type; valueType.setValue(typeLabel(type)); formDirty = true; showingPreview = false;
            valueInput.setText(defaultValue(type)); updateFormLayout(); updateButtons();
        }));
        tree = text(context, "", theme.text().secondary()); tree.setPadding(px(8), px(8), px(8), px(8));
        treeScroll = new UiScrollView(context, theme.scroll()); treeScroll.setBackground(fill(theme.control().input()));
        treeScroll.addView(tree, new FrameLayout.LayoutParams(-1, -2)); form.addView(treeScroll, params(-1, 180));
        View editDivider = divider(context); LayoutParams editDividerParams = params(-1, 1); editDividerParams.topMargin = px(16);
        form.addView(editDivider, editDividerParams);
        LinearLayout edits = row(context);
        remove = themedButton(context, "remove", this::removeEntry); stage = themedButton(context, "stage", () -> stageForm(true));
        restore = themedButton(context, "restore", this::restoreForm);
        edits.addView(remove, params(48, 28)); edits.addView(new View(context), new LayoutParams(0, 1, 1));
        edits.addView(restore, params(72, 28));
        LayoutParams stageParams = params(72, 28); stageParams.leftMargin = px(6); edits.addView(stage, stageParams);
        LayoutParams editsParams = params(-1, 28); editsParams.topMargin = px(12); form.addView(edits, editsParams);
        UiScrollView rightScroll = new UiScrollView(context, theme.scroll()); rightScroll.setHorizontalScrollBarEnabled(false);
        rightScroll.addView(right, new FrameLayout.LayoutParams(-1, -2));
        content.addView(rightScroll, new LayoutParams(0, -1, 0.6f));
        root.addView(content, new LayoutParams(-1, 0, 1));
        LinearLayout confirmation = row(context); confirmation.setPadding(px(16), px(6), px(16), px(6));
        discard = themedButton(context, "discard_confirm", () -> {
            Runnable action = pendingDiscard; showDiscard(false); if (action != null) action.run();
        });
        keep = themedButton(context, "keep_editing", () -> { showDiscard(false); updateButtons(); });
        confirmation.addView(discard, params(-2, 30));
        LayoutParams keepParams = params(-2, 30); keepParams.leftMargin = px(6); confirmation.addView(keep, keepParams);
        discard.setVisibility(GONE); keep.setVisibility(GONE);
        confirmation.setVisibility(GONE); root.addView(confirmation, params(-1, -2));
        setContent(root);
        LinearLayout actions = row(context);
        message = text(context, "", theme.text().muted()); message.setMaxLines(2);
        message.setEllipsize(TextUtils.TruncateAt.END); actions.addView(message, new LayoutParams(0, -1, 1));
        UiActionButton cancel = themedButton(context, "cancel", this::onCloseRequested);
        reload = themedButton(context, "reload", this::reloadDraft);
        apply = new UiActionButton(context, theme, tr("apply"), UiActionButton.Role.PRIMARY,
                UiActionButton.Density.NORMAL, UiActionButton.Style.forTheme(theme));
        apply.setOnClickListener(view -> applyDraft());
        actions.addView(reload, params(76, 28));
        LayoutParams cancelParams = params(64, 28); cancelParams.leftMargin = px(6); actions.addView(cancel, cancelParams);
        LayoutParams applyParams = params(64, 28); applyParams.leftMargin = px(6); actions.addView(apply, applyParams);
        setActions(actions); actions.setLayoutParams(new LayoutParams(-1, -1));
        actionsHost().setBackground(fill(theme.surface().panel()));
        watch(search, () -> { page = 0; renderList(); });
        watch(keyInput, this::formChanged); watch(valueInput, this::formChanged);
        keyInput.setEditActions(() -> stageForm(true), null);
        changed = () -> {
            long token = generation;
            post(() -> { if (active && generation == token) receive(binding.snapshot()); });
        };
        ownerListener = new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) { allowClose = true; requestClose(); }
        };
        receive(binding.snapshot());
    }

    @Override protected void onWindowShown() {
        active = true; generation++;
        owner.addOnAttachStateChangeListener(ownerListener); binding.addChangeListener(changed);
        receive(binding.snapshot()); binding.refresh();
    }
    @Override protected void onWindowClosed(OverlayCloseReason reason) { dispose(); }
    @Override protected void onWindowDestroyed() { dispose(); }
    private void dispose() {
        if (!active) return;
        active = false; generation++; binding.removeChangeListener(changed);
        owner.removeOnAttachStateChangeListener(ownerListener); dismissSelector();
    }
    @Override public boolean canClose(OverlayCloseReason reason) {
        if (allowClose || reason == OverlayCloseReason.OWNER_CLOSED || reason == OverlayCloseReason.HOST_DESTROYED
                || reason == OverlayCloseReason.REPLACED) return true;
        if (saving) return false;
        if (formDirty || draft != null && draft.dirty()) {
            confirmDiscard("discard_confirm", () -> { allowClose = true; requestClose(); }); return false;
        }
        return true;
    }
    private void confirmDiscard(String actionKey, Runnable action) {
        pendingDiscard = action; discard.setText(tr(actionKey)); showDiscard(true);
    }
    private void showDiscard(boolean show) {
        if (!show) pendingDiscard = null;
        ((View) discard.getParent()).setVisibility(show ? VISIBLE : GONE);
        discard.setVisibility(show ? VISIBLE : GONE); keep.setVisibility(show ? VISIBLE : GONE);
        message.setText(show ? tr("discard") : "");
    }
    private void reloadDraft() {
        if (!active || saving || state != null && state.saving()) return;
        if (formDirty || draft != null && draft.dirty()) confirmDiscard("discard_reload", this::performReload);
        else performReload();
    }
    private void performReload() {
        draft = null; formDirty = false; adding = false; selectedKey = null; page = 0;
        try { binding.reload(); receive(binding.snapshot()); }
        catch (RuntimeException failure) { message.setText(tr("failed")); updateButtons(); }
    }
    private void receive(DataDictionaryBinding.State next) {
        state = next;
        if (next.available() && !next.loading() && (draft == null || !draft.dirty() && !formDirty)) {
            boolean firstLoad = draft == null;
            int previousPage = page;
            String previousKey = selectedKey;
            draft = new DataDictionaryDraft(next.values());
            if (previousKey == null && initialKey != null && next.values().containsKey(initialKey)) previousKey = initialKey;
            if (previousKey == null || !next.values().containsKey(previousKey))
                previousKey = next.values().keySet().stream().findFirst().orElse(null);
            loadForm(previousKey);
            if (firstLoad) alignSelectionPage(); else page = previousPage;
        }
        if (!formDirty && !(draft != null && draft.dirty())) message.setText(status(next));
        renderList(); updateButtons();
    }
    private void alignSelectionPage() {
        if (draft == null || selectedKey == null) return;
        int index = DataDictionaryDraft.matching(draft.values(), search.getText().toString(), filter).indexOf(selectedKey);
        if (index >= 0) page = index / PAGE_SIZE;
    }
    private void renderList() {
        if (rows == null) return;
        rows.removeAllViews();
        List<String> keys = draft == null ? List.of() : DataDictionaryDraft.matching(draft.values(), search.getText().toString(), filter);
        int pages = Math.max(1, (keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        pageLabel.setText(tr("page", page + 1, pages));
        listCount.setText(tr("count", keys.size())); previous.setEnabled(page > 0); next.setEnabled(page + 1 < pages);
        int start = page * PAGE_SIZE;
        for (String key : keys.subList(start, Math.min(start + PAGE_SIZE, keys.size()))) {
            LinearLayout row = column(getContext()); row.setPadding(px(16), px(5), px(16), px(5));
            if (Objects.equals(key, selectedKey)) {
                ShapeDrawable selectedBackground = new ShapeDrawable();
                selectedBackground.setColor(theme.selection().fill()); row.setBackground(selectedBackground);
            }
            UiDisplayText name = text(getContext(), key, theme.text().normal()); name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            UiDisplayText summary = text(getContext(), DataDictionaryDraft.summary(draft.values().get(key)), theme.text().muted());
            summary.setSingleLine(true); summary.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout heading = row(getContext());
            heading.addView(name, new LayoutParams(0, -1, 1));
            UiDisplayText type = text(getContext(), typeLabel(DataDictionaryDraft.type(draft.values().get(key))), theme.text().muted());
            LayoutParams typeParams = params(-2, -1); typeParams.leftMargin = px(8); heading.addView(type, typeParams);
            row.addView(heading, params(-1, 22)); row.addView(summary, params(-1, 19));
            row.setOnClickListener(view -> { if (!saving && stageForm(false)) { loadForm(key); renderList(); } });
            rows.addView(row, params(-1, 50));
        }
    }
    private void loadForm(String key) {
        updating = true;
        try {
            selectedKey = key; adding = false; formDirty = false; showingPreview = false;
            Object value = key != null && draft != null ? draft.values().get(key) : "";
            selectedType = DataDictionaryDraft.type(value);
            keyInput.setText(key == null ? "" : key); valueType.setValue(typeLabel(selectedType));
            valueInput.setText(DataDictionaryDraft.format(value)); tree.setText(DataDictionaryDraft.tree(value));
            selectionHint.setText(key == null ? tr("select") : selectedType.editable() ? "" : tr("readonly"));
            updateFormLayout();
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
        loadForm(selectedKey);
        if (wasAdding) { adding = true; formDirty = true; selectionHint.setText(tr("add")); }
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
    private boolean stageForm(boolean explicit) {
        if (!formDirty) return true;
        if (draft == null || !editable() || !selectedType.editable()) return false;
        try {
            String key = keyInput.getText().toString();
            Object old = selectedKey == null ? null : draft.values().get(selectedKey);
            Object value = DataDictionaryDraft.parse(selectedType, valueInput.getText().toString(), old);
            draft.put(adding ? null : selectedKey, key, value);
            selectedKey = key; adding = false; formDirty = false;
            keyInput.setErrorMessage(null); valueInput.setErrorMessage(null);
            alignSelectionPage();
            if (explicit) message.setText(tr("staged"));
            renderList(); updateButtons(); return true;
        } catch (IllegalArgumentException invalid) {
            String reason = "duplicate".equals(invalid.getMessage()) ? "duplicate" : "invalid";
            message.setText(tr(reason)); valueInput.setErrorMessage(tr(reason)); return false;
        }
    }
    private void newEntry() {
        if (!editable() || !stageForm(false)) return;
        loadForm(null); adding = true; formDirty = true; selectionHint.setText(tr("add"));
        updateButtons(); keyInput.requestFocus();
    }
    private void removeEntry() {
        if (!editable() || selectedKey == null) return;
        draft.remove(selectedKey); formDirty = false;
        loadForm(draft.values().keySet().stream().findFirst().orElse(null));
        renderList(); updateButtons();
    }
    private boolean editable() {
        return state != null && state.available() && state.editable() && !state.loading() && !state.saving() && !saving;
    }
    private void updateButtons() {
        if (apply == null) return;
        boolean canEdit = editable() && draft != null;
        boolean selected = selectedKey != null || adding;
        form.setVisibility(selected ? VISIBLE : GONE);
        selectionHint.setVisibility(!selected || !selectedType.editable() ? VISIBLE : GONE);
        booleanInput.setEnabled(canEdit && selected); booleanRow.setEnabled(canEdit && selected);
        restore.setEnabled(canEdit && selected && formDirty);
        keyInput.setEnabled(canEdit && selected && selectedType.editable());
        valueType.setEnabled(canEdit && selected && selectedType.editable());
        valueInput.setEnabled(canEdit && selected && selectedType.editable());
        add.setEnabled(canEdit); remove.setEnabled(canEdit && selectedKey != null);
        reload.setEnabled(!saving && (state == null || !state.saving()));
        stage.setEnabled(canEdit && formDirty && selectedType.editable());
        apply.setEnabled(canEdit && (formDirty || draft.dirty()));
        if (message.getText().isEmpty() || message.getText().toString().equals(tr("dirty"))) {
            message.setText(formDirty || draft != null && draft.dirty() ? tr("dirty") : "");
        }
    }
    private void applyDraft() {
        if (!editable() || !stageForm(false) || draft == null || !draft.dirty()) return;
        Map<String, Object> expected = draft.baseline(), replacement = draft.values();
        saving = true; message.setText(tr("saving")); updateButtons(); long token = generation;
        try {
            binding.apply(expected, replacement, error -> post(() -> {
                if (!active || generation != token) return;
                saving = false;
                if (error == null || error.isEmpty()) { allowClose = true; requestClose(); }
                else { message.setText(error); updateButtons(); }
            }));
        } catch (RuntimeException failure) {
            saving = false; message.setText(tr("failed")); updateButtons();
        }
    }
    @Override public boolean onEscapePressed() {
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
        // Keep the popup inside this modal's elevated subtree, above its form and list.
        menu.showBelow(anchor, contentHost());
    }
    private static ShapeDrawable fill(int color) {
        ShapeDrawable background = new ShapeDrawable(); background.setColor(color); return background;
    }
    private UiActionButton themedButton(Context context, String key, Runnable action) {
        return button(context, key, action, theme);
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
