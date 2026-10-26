package com.mine.geometry_node.client.ui.components.integration.entity.editor.graph;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchSubmit;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagStrip;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
import com.mine.geometry_node.client.ui.components.composite.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.components.composite.table.UiColumnFilterPopover;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Handler;
import icyllis.modernui.core.Looper;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Binding changes autosave through the caller; document edits use the existing workspace editor. */
public final class EntityGraphsView extends FrameLayout {
    private static final int ROW_HEIGHT = 112;
    private final UiTheme theme;
    private final EntityGraphBindings bindings;
    private final Consumer<List<Map<String, Object>>> changed;
    private final BooleanSupplier bindingAllowed;
    private final EntityGraphActions actions;
    private final Runnable graphOpened;
    private final UiDisplayText openingStatus;
    private int pickerSession;
    private int openSession;
    private boolean disposed;

    private final Set<GraphBindingKey> selected = new LinkedHashSet<>();
    private final Map<GraphBindingKey, BindingRow> rows = new LinkedHashMap<>();
    private final UiCheckBox selectAll;
    private final UiDisplayText summary;
    private final UiIconButton add;
    private final UiIconButton remove;
    private final UiSearchInput name;
    private final UiSelectButton type;
    private final UiSelectButton tags;
    private final UiScrollView scroll;
    private final FrameLayout list;
    private List<GraphBindingKey> visible = List.of();
    private String draftType = "";
    private Set<String> draftIncluded = Set.of(), draftExcluded = Set.of();
    private EntityGraphFilter applied = EntityGraphFilter.EMPTY;
    private UiContextMenu menu;
    private UiColumnFilterPopover tagEditor;
    private UiPopover tagPopup;
    private boolean renderQueued;
    private final Runnable renderWindow = () -> { renderQueued = false; renderVisibleRows(); };

    public EntityGraphsView(Context context, UiTheme theme, List<Map<String, Object>> bound,
                            List<Map<String, Object>> available,
                            Consumer<List<Map<String, Object>>> changed) {
        this(context, theme, bound, available, changed, () -> true);
    }

    public EntityGraphsView(Context context, UiTheme theme, List<Map<String, Object>> bound,
                            List<Map<String, Object>> available,
                            Consumer<List<Map<String, Object>>> changed, BooleanSupplier bindingAllowed) {
        this(context, theme, bound, available, changed, bindingAllowed,
                (owner, ownerTheme) -> EntityGraphActions.unavailable(), () -> {});
    }

    public EntityGraphsView(Context context, UiTheme theme, List<Map<String, Object>> bound,
                            List<Map<String, Object>> available,
                            Consumer<List<Map<String, Object>>> changed, BooleanSupplier bindingAllowed,
                            EntityGraphActions.Factory actionsFactory, Runnable graphOpened) {
        super(context);
        this.theme = Objects.requireNonNull(theme, "theme");
        this.changed = Objects.requireNonNull(changed, "changed");
        this.bindingAllowed = Objects.requireNonNull(bindingAllowed, "bindingAllowed");
        this.graphOpened = Objects.requireNonNull(graphOpened, "graphOpened");
        actions = Objects.requireNonNull(Objects.requireNonNull(actionsFactory, "actionsFactory")
                .create(this, theme), "actions");
        bindings = new EntityGraphBindings(bound, available);
        LinearLayout content = column();
        LinearLayout toolbar = row();
        toolbar.setPadding(px(20), px(12), px(20), px(12));
        name = UiSearchInput.createThemed(context, theme);
        name.setHint(tr("search_name"));
        name.setContentDescription(tr("search_name"));
        toolbar.addView(name, new LinearLayout.LayoutParams(0, px(32), 1));
        type = select(tr("all_types"));
        type.setContentDescription(tr("filter_type"));
        type.setOnClickListener(view -> showTypes());
        toolbar.addView(type, spaced(120, 32));
        tags = select(tr("filter_tags"));
        tags.setContentDescription(tr("filter_tags"));
        tags.setOnClickListener(view -> showTags());
        toolbar.addView(tags, spaced(92, 32));
        UiIconButton search = icon(SvgIconView.Icon.SEARCH, "search", this::search);
        UiSearchSubmit.bind(search, this::search, name);
        toolbar.addView(search, spaced(32, 32));
        add = icon(SvgIconView.Icon.PLUS, "add_binding", this::showPicker);
        toolbar.addView(add, spaced(32, 32));
        remove = icon(SvgIconView.Icon.CLEAR, "remove_selected", this::removeSelected);
        toolbar.addView(remove, spaced(32, 32));
        content.addView(toolbar, new LinearLayout.LayoutParams(-1, px(58)));
        content.addView(divider(), new LinearLayout.LayoutParams(-1, px(1)));
        openingStatus = text("", theme.text().muted(), UiMetrics.TEXT_CAPTION);
        openingStatus.setPadding(px(20), 0, px(20), 0);
        openingStatus.setVisibility(GONE);
        content.addView(openingStatus, new LinearLayout.LayoutParams(-1, px(26)));

        LinearLayout selection = row();
        selection.setPadding(px(22), 0, px(20), 0);
        selectAll = UiCheckBox.createThemed(context, theme);
        selectAll.setTooltipText(tr("select_all"));
        selectAll.setContentDescription(tr("select_all"));
        selectAll.setOnCheckedChangeListener((box, checked) -> {
            if (checked) selected.addAll(visible); else selected.removeAll(visible);
            updateSelection();
        });
        selection.addView(selectAll, new LinearLayout.LayoutParams(px(16), px(16)));
        summary = text("", theme.text().muted(), UiMetrics.TEXT_CAPTION);
        selection.addView(summary, spaced(-1, 32));
        content.addView(selection, new LinearLayout.LayoutParams(-1, px(38)));
        content.addView(divider(), new LinearLayout.LayoutParams(-1, px(1)));

        scroll = new UiScrollView(context, theme.scroll());
        // Row content already reserves the right edge; let its background reach behind the track.
        scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        list = new FrameLayout(context);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> queueRender());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addView(content, new FrameLayout.LayoutParams(-1, -1));
        render();
    }

    public List<Map<String, Object>> graphs() { return bindings.graphs(); }
    public boolean hasPendingChanges() { return bindings.dirty(); }

    /** Parent passes its rebased graph draft on acknowledgement; no change callback is emitted. */
    public void refreshConfirmed(List<Map<String, Object>> bindings, List<Map<String, Object>> available) {
        boolean rowsChanged = this.bindings.refreshConfirmed(bindings, available);
        if (rowsChanged) dismissMenus();
        if (rowsChanged) render(); else updateSelection();
    }

    private void search() {
        applied = new EntityGraphFilter(name.getText().toString(), draftType, draftIncluded, draftExcluded);
        scroll.scrollTo(0, 0);
        render();
    }

    private void showTypes() {
        dismissMenus();
        menu = new UiContextMenu(getContext(), theme);
        menu.addMenuItem(tr("all_types"), () -> setType("", tr("all_types")));
        for (GraphType graphType : GraphTypeRegistry.INSTANCE.all()) {
            String label = Component.translatable(graphType.translationKey()).getString();
            menu.addMenuItem(label, () -> setType(graphType.id(), label));
        }
        menu.showBelow(type, menuHost());
    }
    private void setType(String id, String label) { draftType = id; type.setValue(label); }

    private void showTags() {
        dismissMenus();
        // Keep the shared editor's local chips and unfinished token even across page switching.
        // Its Apply callback stages criteria; only the toolbar Search changes the applied snapshot.
        if (tagPopup != null) { tagPopup.showBelow(tags, (FrameLayout) menuHost()); return; }
        FrameLayout host = (FrameLayout) menuHost();
        tagEditor = new UiColumnFilterPopover(getContext(), host, theme);
        tagEditor.showTags(tags, draftIncluded, draftExcluded, (included, excluded) -> {
            draftIncluded = included; draftExcluded = excluded;
            int count = included.size() + excluded.size();
            tags.setValue(count == 0 ? tr("filter_tags") : tr("filter_tags_count", count));
            tagPopup = null; tagEditor = null;
        });
        for (int i = 0; i < host.getChildCount(); i++) {
            if (host.getChildAt(i) instanceof UiPopover popup && popup.isOpen()) tagPopup = popup;
        }
    }

    private void render() {
        selected.retainAll(bindings.keys());
        visible = bindings.filtered(applied);
        rows.clear(); list.removeAllViews();
        int height = visible.size() * px(ROW_HEIGHT);
        // ScrollView measures its child with an unspecified height. Minimum height retains
        // the full result extent even though most row widgets are deliberately absent.
        list.setMinimumHeight(height);
        renderVisibleRows();
        queueRender();
        updateSelection();
    }

    /** Scroll height covers all results; only the viewport and two spare rows own widgets. */
    private void renderVisibleRows() {
        int height = px(ROW_HEIGHT);
        int first = Math.max(0, scroll.getScrollY() / height - 1);
        int last = Math.min(visible.size(), first + Math.max(2, (scroll.getHeight() + height - 1) / height) + 2);
        Set<GraphBindingKey> window = new LinkedHashSet<>(visible.subList(Math.min(first, last), last));
        rows.entrySet().removeIf(entry -> {
            if (window.contains(entry.getKey())) return false;
            list.removeView(entry.getValue()); return true;
        });
        for (int index = first; index < last; index++) {
            GraphBindingKey key = visible.get(index);
            if (rows.containsKey(key)) continue;
            BindingRow entry = new BindingRow(key);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, height);
            params.topMargin = index * height;
            list.addView(entry, params); rows.put(key, entry);
        }
    }

    private void queueRender() {
        if (renderQueued) return;
        renderQueued = true; post(renderWindow);
    }

    private void updateSelection() {
        long count = visible.stream().filter(selected::contains).count();
        selectAll.setEnabled(!visible.isEmpty());
        selectAll.setChecked(!visible.isEmpty() && count == visible.size());
        selectAll.setIndeterminate(count > 0 && count < visible.size());
        summary.setText(tr("filtered_selection_count", visible.size(), bindings.keys().size(), selected.size()));
        remove.setEnabled(!selected.isEmpty());
        add.setEnabled(bindingAllowed.getAsBoolean());
        rows.values().forEach(BindingRow::updateSelection);
    }

    private final class BindingRow extends LinearLayout {
        private final GraphBindingKey key;
        private final UiCheckBox check;
        private final ShapeDrawable background = new ShapeDrawable();
        BindingRow(GraphBindingKey key) {
            super(EntityGraphsView.this.getContext());
            this.key = key;
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(px(22), px(14), px(30), px(14));
            setBackground(background);
            setWillNotDraw(false);
            check = UiCheckBox.createThemed(getContext(), theme);
            check.setTooltipText(tr("select_binding", bindings.name(key)));
            check.setContentDescription(tr("select_binding", bindings.name(key)));
            check.setOnCheckedChangeListener((box, checked) -> {
                if (checked) selected.add(key); else selected.remove(key);
                EntityGraphsView.this.updateSelection();
            });
            addView(check, new LinearLayout.LayoutParams(px(16), px(16)));
            var data = bindings.presentation(key);
            GraphType graphType = GraphTypeRegistry.INSTANCE.get(data.type());
            SvgIconView.Icon svg = SvgIconView.Icon.forGraphType(data.type());
            View icon = svg == null ? new View(getContext()) : new SvgIconView(getContext(), svg,
                    graphType == null ? theme.text().muted() : graphType.assetIconColor());
            addView(icon, spaced(30, 30));
            LinearLayout labels = column();
            LinearLayout title = row();
            UiDisplayText label = ellipsis(bindings.name(key), theme.text().normal(), UiMetrics.TEXT_BODY);
            title.addView(label, new LinearLayout.LayoutParams(-2, -1));
            UiDisplayText typeLabel = ellipsis(graphType == null ? tr("unknown_type")
                    : Component.translatable(graphType.translationKey()).getString(), theme.text().muted(), UiMetrics.TEXT_CAPTION);
            title.addView(typeLabel, spaced(-2, -1));
            labels.addView(title, new LinearLayout.LayoutParams(-1, px(26)));
            labels.addView(ellipsis(key.graphId(), theme.text().muted(), UiMetrics.TEXT_CAPTION), new LinearLayout.LayoutParams(-1, px(24)));
            UiTagStrip strip = new UiTagStrip(getContext(), theme, UiMetrics.TEXT_CAPTION);
            strip.setPadding(0, 0, 0, 0); strip.setTags(data.tags());
            labels.addView(strip, new LinearLayout.LayoutParams(-1, px(26)));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.leftMargin = px(12); labelParams.rightMargin = px(12);
            addView(labels, labelParams);
            UiActionButton edit = new UiActionButton(getContext(), theme, tr("edit"), UiActionButton.Role.SECONDARY,
                    UiActionButton.Density.NORMAL, UiActionButton.Style.forTheme(theme));
            UIUtils.setLockedTextSize(edit, UiMetrics.TEXT_BODY);
            edit.setOnClickListener(view -> openGraph(key.graphId()));
            addView(edit, new LinearLayout.LayoutParams(px(64), px(32)));
            setOnClickListener(view -> check.performClick());
            updateSelection();
        }
        void updateSelection() {
            boolean checked = selected.contains(key);
            check.setChecked(checked);
            background.setColor(checked ? theme.selection().subtle() : UiTheme.withAlpha(theme.surface().panel(), 0));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getHeight() > 0) {
                canvas.save();
                canvas.translate(0, getHeight() - px(1));
                ShapeDrawable line = rowDivider;
                line.setBounds(0, 0, getWidth(), px(1));
                line.draw(canvas);
                canvas.restore();
            }
        }
        private final ShapeDrawable rowDivider = lineDrawable();
    }

    private void removeSelected() {
        dismissMenus();
        if (!bindings.remove(selected)) return;
        selected.clear(); render(); changed.accept(graphs());
    }

    private void showPicker() {
        dismissMenus();
        if (disposed || !bindingAllowed.getAsBoolean()) return;
        int session = ++pickerSession;
        actions.chooseRemote(this, tr("add_binding"), entry -> {
            // Modal suspension may hide/detach this page. Only the owning edit session
            // decides whether the endpoint is still valid; attachment is not its lifetime.
            if (disposed || session != pickerSession || !bindingAllowed.getAsBoolean() || !bindings.add(entry)) return;
            render(); changed.accept(graphs());
        });
    }

    private void openGraph(String graphId) {
        if (disposed) return;
        int session = ++openSession;
        openingStatus.setText(tr("loading"));
        openingStatus.setVisibility(VISIBLE);
        actions.openRemote(graphId, outcome -> {
            if (disposed || session != openSession) return;
            if (outcome == EntityGraphActions.Outcome.OPENED) {
                openingStatus.setVisibility(GONE);
                graphOpened.run();
                return;
            }
            openingStatus.setText(tr(switch (outcome) {
                case NO_EDITOR -> "no_editor";
                case INVALID_ID -> "invalid_id";
                case CONNECTION_CHANGED -> "connection_changed";
                case DOWNLOAD_FAILED -> "download_failed";
                default -> "load_failed";
            }));
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        pickerSession++;
        openSession++;
        dismissMenus();
        actions.close();
    }

    FrameLayout menuHost() {
        for (View current = this; current != null;
             current = current.getParent() instanceof View parent ? parent : null) {
            if (current instanceof ModalWindowView modal) return modal.contentHost();
        }
        return this;
    }

    public void dismissMenus() {
        if (menu != null) { menu.dismiss(); menu = null; }
        if (tagPopup != null) tagPopup.dismiss();
    }
    public boolean onEscapePressed() {
        boolean open = menu != null && menu.isOpen() || tagPopup != null && tagPopup.isOpen();
        if (open) dismissMenus();
        return open;
    }
    private void hideMenusDeferred() {
        UiContextMenu closing = menu; menu = null;
        UiPopover closingTags = tagPopup;
        if (closing != null) closing.setVisibility(GONE);
        if (closingTags != null) closingTags.setVisibility(GONE);
        if (closing != null || closingTags != null) new Handler(Looper.myLooper()).post(() -> {
            if (closing != null) closing.dismiss();
            if (closingTags != null && closingTags.getVisibility() == GONE) closingTags.dismiss();
        });
    }
    @Override public void setVisibility(int visibility) {
        if (visibility != VISIBLE) hideMenusDeferred();
        super.setVisibility(visibility);
    }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) hideMenusDeferred();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); queueRender();
    }
    @Override protected void onDetachedFromWindow() {
        hideMenusDeferred();
        removeCallbacks(renderWindow); renderQueued = false;
        super.onDetachedFromWindow();
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (scroll != null) queueRender();
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(getContext()); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private LinearLayout row() {
        LinearLayout view = new LinearLayout(getContext()); view.setBaselineAligned(false);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private UiDisplayText text(String value, int color, float size) {
        UiDisplayText view = new UiDisplayText(getContext(), value, size, color);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private UiDisplayText ellipsis(String value, int color, float size) {
        UiDisplayText view = text(value, color, size); view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END); view.setTooltipText(value); return view;
    }
    private UiSelectButton select(String label) {
        return new UiSelectButton(getContext(), label, UiMetrics.TEXT_BODY, () -> 2, theme);
    }
    private UiIconButton icon(SvgIconView.Icon svg, String label, Runnable action) {
        UiIconButton view = UiIconButton.createThemed(getContext(), theme, svg, tr(label));
        view.setOnClickListener(ignored -> action.run()); return view;
    }
    private static LinearLayout.LayoutParams spaced(int width, int height) {
        var params = new LinearLayout.LayoutParams(width < 0 ? width : px(width), height < 0 ? height : px(height));
        params.leftMargin = px(8); return params;
    }
    private ShapeDrawable lineDrawable() {
        ShapeDrawable line = new ShapeDrawable(); line.setColor(theme.border().divider()); return line;
    }
    private View divider() {
        View line = new View(getContext()); line.setBackground(lineDrawable()); return line;
    }
    private static int px(float dp) { return UIUtils.dp2pxInt(dp); }
    private static String tr(String key, Object... args) {
        return Component.translatable("geometry_node.entity_editor.graph." + key, args).getString();
    }
}
