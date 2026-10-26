package com.mine.geometry_node.client.ui.components.integration.entity.appearance;

import com.mine.geometry_node.client.runtime.entity.appearance.EntityAppearanceCatalog;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchSubmit;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityTemplatePreviewView;
import com.mine.geometry_node.client.ui.overlay.OverlayCloseReason;
import com.mine.geometry_node.client.ui.overlay.OverlayHost;
import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSpec;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceVariants;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Reusable themed appearance draft picker; only the footer confirmation calls its consumer. */
public final class EntityAppearancePicker extends ModalWindowView {
    /** Detached source snapshot: reading or previewing it never changes the replacement appearance. */
    public record Source(String typeId, String variantId, EntityTemplateValue preview) {
        public Source {
            Objects.requireNonNull(typeId);
            variantId = variantId == null ? "" : variantId;
            preview = preview != null && preview.entityTypeId().equals(typeId) ? preview : EntityTemplateValue.EMPTY;
        }
        public Source(String typeId) { this(typeId, "", EntityTemplateValue.EMPTY); }
    }
    private static final int ROW_HEIGHT_DP = 68;
    private final UiTheme theme;
    private final AppearanceSelection selection;
    private final Source source;
    private final Consumer<EntityAppearanceSpec> onSelect;
    private final Consumer<Runnable> clientQueue, uiQueue;
    private final Supplier<List<AppearanceSelection.Option>> catalog;
    private final Function<String, List<String>> variants;
    private final UiSearchInput search;
    private final UiSelectButton namespace;
    private final UiDisplayText count;
    private final LinearLayout list;
    private final CandidateScroll scroll;
    private final AppearancePreviewPane pane;
    private final UiActionButton confirm;
    private final Map<AppearanceSelection.Option, UiActionButton> rows = new LinkedHashMap<>();
    private UiContextMenu menu;
    private String namespaceId = "";
    private String appliedQuery = "";
    private boolean loaded, loadFailed;
    private volatile boolean disposed;

    public static void show(Context context, View anchor, UiTheme theme, String sourceTypeId,
                            EntityAppearanceSpec current, Consumer<EntityAppearanceSpec> onSelect) {
        show(context, anchor, theme, new Source(sourceTypeId), current, onSelect);
    }

    public static void show(Context context, View anchor, UiTheme theme, Source source,
                            EntityAppearanceSpec current, Consumer<EntityAppearanceSpec> onSelect) {
        Objects.requireNonNull(context); Objects.requireNonNull(anchor); Objects.requireNonNull(theme);
        Objects.requireNonNull(source); Objects.requireNonNull(onSelect);
        MuiModApi.postToUiThread(() -> {
            OverlayHost host = OverlayHost.find(anchor);
            if (host == null) return;
            EntityAppearancePicker window = new EntityAppearancePicker(context, anchor, theme, source,
                    current, onSelect, task -> Minecraft.getInstance().execute(task), MuiModApi::postToUiThread,
                    () -> EntityAppearanceCatalog.entries().stream().map(entry -> new AppearanceSelection.Option(
                            entry.targetTypeId(), Component.translatable(entry.descriptionId()).getString())).toList(),
                    id -> {
                        var level = Minecraft.getInstance().level;
                        return level == null ? List.of() : EntityAppearanceVariants.variantIds(level, id);
                    });
            host.showModal(window, new ModalOptions(false, true, anchor, theme.surface().modalScrim()));
        });
    }

    /** Queue/catalog injection keeps registry enumeration and lifecycle independently testable. */
    EntityAppearancePicker(Context context, View anchor, UiTheme theme, String sourceTypeId,
                           EntityAppearanceSpec current, Consumer<EntityAppearanceSpec> onSelect,
                           Consumer<Runnable> clientQueue, Consumer<Runnable> uiQueue,
                           Supplier<List<AppearanceSelection.Option>> catalog) {
        this(context, anchor, theme, sourceTypeId, current, onSelect, clientQueue, uiQueue, catalog, id -> List.of());
    }

    EntityAppearancePicker(Context context, View anchor, UiTheme theme, String sourceTypeId,
                           EntityAppearanceSpec current, Consumer<EntityAppearanceSpec> onSelect,
                           Consumer<Runnable> clientQueue, Consumer<Runnable> uiQueue,
                           Supplier<List<AppearanceSelection.Option>> catalog, Function<String, List<String>> variants) {
        this(context, anchor, theme, new Source(sourceTypeId), current, onSelect, clientQueue, uiQueue, catalog, variants);
    }

    EntityAppearancePicker(Context context, View anchor, UiTheme theme, Source source,
                           EntityAppearanceSpec current, Consumer<EntityAppearanceSpec> onSelect,
                           Consumer<Runnable> clientQueue, Consumer<Runnable> uiQueue,
                           Supplier<List<AppearanceSelection.Option>> catalog, Function<String, List<String>> variants) {
        super(context, tr("title"), MovementMode.DRAGGABLE, Style.forTheme(theme));
        if (anchor != null) bindSessionOwner(anchor);
        this.theme = Objects.requireNonNull(theme);
        this.source = Objects.requireNonNull(source);
        selection = new AppearanceSelection(source.typeId(), current, source.variantId());
        this.onSelect = Objects.requireNonNull(onSelect);
        this.clientQueue = Objects.requireNonNull(clientQueue);
        this.uiQueue = Objects.requireNonNull(uiQueue);
        this.catalog = Objects.requireNonNull(catalog);
        this.variants = Objects.requireNonNull(variants);
        setPreferredSizeDp(880, 590);
        setMinimumSizeDp(360, 300);
        contentHost().setPadding(0, 0, 0, 0);
        titleView().setSingleLine(true);
        titleView().setEllipsize(TextUtils.TruncateAt.END);

        LinearLayout content = column();
        LinearLayout toolbar = row();
        toolbar.setPadding(px(14), px(10), px(14), px(10));
        search = UiSearchInput.createThemed(context, theme);
        search.setHint(tr("search_hint"));
        search.setContentDescription(tr("search_hint"));
        toolbar.addView(search, new LinearLayout.LayoutParams(0, px(32), 1));
        namespace = new UiSelectButton(context, tr("namespace_all"), 12, () -> 2, theme);
        namespace.setContentDescription(tr("namespace"));
        namespace.setOnClickListener(view -> showNamespaces());
        LinearLayout.LayoutParams namespaceParams = new LinearLayout.LayoutParams(px(150), px(32));
        namespaceParams.leftMargin = px(8);
        toolbar.addView(namespace, namespaceParams);
        UiIconButton searchButton = UiIconButton.createThemed(context, theme, SvgIconView.Icon.SEARCH, tr("search"));
        UiSearchSubmit.bind(searchButton, this::applySearch, search);
        toolbar.addView(searchButton, iconParams());
        UiIconButton clearButton = UiIconButton.createThemed(context, theme, SvgIconView.Icon.CLOSE, tr("clear_search"));
        clearButton.setOnClickListener(view -> clearSearch());
        toolbar.addView(clearButton, iconParams());
        content.addView(toolbar, new LinearLayout.LayoutParams(-1, px(52)));
        content.addView(divider(), new LinearLayout.LayoutParams(-1, px(1)));

        LinearLayout body = row();
        LinearLayout left = column();
        LinearLayout heading = row();
        heading.setPadding(px(18), 0, px(14), 0);
        heading.addView(text(tr("candidates"), 12, theme.text().secondary()),
                new LinearLayout.LayoutParams(0, -1, 1));
        count = text("", 11, theme.text().muted());
        heading.addView(count, new LinearLayout.LayoutParams(-2, -1));
        left.addView(heading, new LinearLayout.LayoutParams(-1, px(36)));
        left.addView(divider(), new LinearLayout.LayoutParams(-1, px(1)));
        list = column();
        scroll = new CandidateScroll(context);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        left.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1.15f));
        body.addView(divider(), new LinearLayout.LayoutParams(px(1), -1));
        pane = new AppearancePreviewPane(context, theme);
        pane.variant.setOnClickListener(view -> showVariants());
        pane.random.setOnClickListener(view -> randomVariant());
        body.addView(pane, new LinearLayout.LayoutParams(0, -1, 1));
        content.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContent(content);

        LinearLayout actions = row();
        UiActionButton cancel = action("cancel", UiActionButton.Role.SECONDARY);
        cancel.setSingleLine(true); cancel.setPadding(px(12), 0, px(12), 0);
        cancel.setOnClickListener(view -> requestClose());
        actions.addView(cancel, new LinearLayout.LayoutParams(px(96), px(32)));
        confirm = action("select", UiActionButton.Role.PRIMARY);
        confirm.setSingleLine(true); confirm.setPadding(px(12), 0, px(12), 0);
        confirm.setEnabled(false);
        confirm.setOnClickListener(view -> commit());
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(px(96), px(32));
        confirmParams.leftMargin = px(8);
        actions.addView(confirm, confirmParams);
        setActions(actions);
        pane.preview.setOnPreviewStatusChanged(this::previewStatus);
        pane.status.setText(tr("loading"));
        showListMessage("loading");
        namespace.setEnabled(false);
    }

    @Override protected void onWindowShown() {
        search.requestFocus();
        clientQueue.accept(() -> {
            if (disposed) return;
            List<AppearanceSelection.Option> entries;
            boolean failed;
            try { entries = List.copyOf(catalog.get()); failed = false; }
            catch (RuntimeException failure) { entries = List.of(); failed = true; }
            List<AppearanceSelection.Option> snapshot = entries;
            Map<String, List<String>> variantSnapshot = new LinkedHashMap<>();
            // Enumerate registry data on the client thread. UI owns IDs only, never a Level or holder.
            for (var entry : snapshot) {
                try { variantSnapshot.put(entry.id(), List.copyOf(variants.apply(entry.id()))); }
                catch (RuntimeException failure) { variantSnapshot.put(entry.id(), List.of()); }
            }
            Map<String, List<String>> choices = Map.copyOf(variantSnapshot);
            boolean unavailable = failed;
            uiQueue.accept(() -> {
                if (disposed) return;
                selection.load(snapshot, choices);
                loaded = true;
                loadFailed = unavailable;
                namespace.setEnabled(true);
                render(true);
            });
        });
    }

    private void render(boolean alignCurrent) {
        List<AppearanceSelection.Option> visible = selection.filter(appliedQuery, namespaceId);
        rows.clear();
        list.removeAllViews();
        count.setText(visible.size() + " / " + selection.size());
        for (AppearanceSelection.Option option : visible) addCandidate(option);
        if (visible.isEmpty()) showListMessage(loadFailed ? "unavailable" : "empty");
        updateSelection();
        scroll.alignCurrent = alignCurrent;
        scroll.scrollTo(0, 0);
        scroll.requestLayout();
    }

    private void addCandidate(AppearanceSelection.Option option) {
        FrameLayout item = new FrameLayout(getContext());
        UiActionButton button = action("", UiActionButton.Role.QUIET);
        button.setText("");
        button.setMinHeight(0); button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setStyle(new UiActionButton.Style(0, 0, theme.border().subtle(),
                UiMetrics.DISABLED_ALPHA, theme.border().focus()));
        button.setContentDescription(option.name() + " " + option.id());
        button.setOnClickListener(view -> {
            if (disposed) return;
            selection.select(option);
            updateSelection();
        });
        item.addView(button, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout labels = column();
        labels.setPadding(px(18), px(10), px(18), px(10));
        LinearLayout top = row();
        top.addView(text(option.name(), 13, theme.text().normal()), new LinearLayout.LayoutParams(0, -1, 1));
        String badge = selection.isCurrent(option) ? tr("current") : selection.isDefault(option) ? tr("default") : "";
        if (!badge.isEmpty()) top.addView(text(badge, 10, theme.border().focus()), new LinearLayout.LayoutParams(-2, -1));
        labels.addView(top, new LinearLayout.LayoutParams(-1, 0, 1));
        UiDisplayText id = text(option.id(), 11, theme.text().muted());
        id.setTooltipText(option.id());
        labels.addView(id, new LinearLayout.LayoutParams(-1, 0, 1));
        item.addView(labels, new FrameLayout.LayoutParams(-1, -1));
        list.addView(item, new LinearLayout.LayoutParams(-1, px(ROW_HEIGHT_DP)));
        rows.put(option, button);
    }

    private void updateSelection() {
        dismissMenu();
        AppearanceSelection.Option selected = selection.selected();
        rows.forEach((option, button) -> {
            boolean active = option.equals(selected);
            button.setSelected(active);
            button.setPalette(active ? new UiActionButton.Palette(theme.selection().fill(), theme.selection().hover(),
                    theme.selection().pressed(), theme.selection().text())
                    : UiActionButton.Palette.forRole(theme, UiActionButton.Role.QUIET));
        });
        confirm.setEnabled(selection.canConfirm());
        pane.show(selected);
        pane.showVariant(selection.variantId(), !selection.variantIds().isEmpty());
        if (selected == null) pane.preview.setAppearance(EntityAppearanceSpec.NONE);
        else {
            EntityAppearanceSpec spec = selection.spec();
            // NONE means the original entity. A template can also represent nonliving source types
            // which the appearance schema deliberately excludes as replacement targets.
            if (spec.isNone()) {
                if (!source.preview().isEmpty()) pane.preview.setDisplayTemplate(source.preview());
                else if (!source.variantId().isEmpty()) pane.preview.setAppearance(EntityAppearanceSpec.of(selected.id(),
                        Map.of(EntityAppearanceVariants.PARAMETER, source.variantId())));
                else pane.preview.setDisplayTemplate(new EntityTemplateValue(selected.id(), new CompoundTag()));
            }
            else pane.preview.setAppearance(spec);
        }
        previewStatus(pane.preview.getPreviewStatus());
    }

    private void previewStatus(EntityTemplatePreviewView.Status status) {
        if (disposed) return;
        pane.status.setVisibility(status == EntityTemplatePreviewView.Status.READY ? GONE : VISIBLE);
        pane.status.setText(tr(switch (status) {
            case EMPTY -> "empty";
            case LOADING -> "loading";
            case UNAVAILABLE -> "unavailable";
            case NO_APPEARANCE -> "no_appearance";
            case ERROR -> "error";
            case READY -> "preview";
        }));
    }

    private void showNamespaces() {
        if (disposed || !loaded) return;
        dismissMenu();
        menu = new UiContextMenu(getContext(), theme);
        menu.addChoiceItem(tr("namespace_all"), namespaceId.isEmpty(), () -> chooseNamespace(""));
        for (String id : selection.namespaces())
            menu.addChoiceItem(id, namespaceId.equals(id), () -> chooseNamespace(id));
        menu.showBelow(namespace, contentHost());
    }

    private void chooseNamespace(String id) {
        if (disposed) return;
        namespaceId = id;
        namespace.setValue(id.isEmpty() ? tr("namespace_all") : id);
        applySearch();
    }

    private void showVariants() {
        if (disposed || !loaded || selection.variantIds().isEmpty()) return;
        dismissMenu();
        menu = new UiContextMenu(getContext(), theme);
        for (String id : selection.variantIds())
            menu.addChoiceItem(id, selection.variantId().equals(id), () -> chooseVariant(id));
        menu.showBelow(pane.variant, contentHost());
    }

    private void chooseVariant(String id) {
        if (disposed || !loaded || !selection.variantIds().contains(id)) return;
        selection.chooseVariant(id);
        updateSelection();
    }

    private void randomVariant() {
        if (disposed || !loaded) return;
        List<String> choices = selection.variantIds();
        if (!choices.isEmpty()) chooseVariant(choices.get(ThreadLocalRandom.current().nextInt(choices.size())));
    }

    private void applySearch() {
        if (disposed) return;
        appliedQuery = search.getText().toString();
        if (loaded) render(false);
    }

    private void clearSearch() {
        if (disposed) return;
        search.setText("");
        namespaceId = "";
        namespace.setValue(tr("namespace_all"));
        applySearch();
        search.requestFocus();
    }

    private void commit() {
        if (disposed || !loaded || !selection.canConfirm()) return;
        EntityAppearanceSpec result = selection.spec();
        dispose();
        try { onSelect.accept(result); }
        finally { requestClose(); }
    }

    private void dispose() {
        if (disposed) return;
        disposed = true;
        dismissMenu();
        pane.preview.setOnPreviewStatusChanged(null);
        pane.preview.setAppearance(EntityAppearanceSpec.NONE);
        pane.showVariant("", false);
        confirm.setEnabled(false);
    }

    private void dismissMenu() { if (menu != null) { menu.dismiss(); menu = null; } }
    @Override public boolean onEscapePressed() {
        if (menu != null && menu.isOpen()) { dismissMenu(); return true; }
        return false;
    }
    @Override protected void onWindowSuspended() { dismissMenu(); }
    @Override protected void onWindowClosed(OverlayCloseReason reason) { dispose(); }
    @Override protected void onWindowDestroyed() { dispose(); }

    private void showListMessage(String key) {
        UiDisplayText message = text(tr(key), 12, theme.text().muted());
        message.setGravity(Gravity.CENTER);
        list.addView(message, new LinearLayout.LayoutParams(-1, px(ROW_HEIGHT_DP)));
    }

    private UiActionButton action(String key, UiActionButton.Role role) {
        return new UiActionButton(getContext(), theme, key.isEmpty() ? "" : tr(key), role,
                UiActionButton.Density.NORMAL, UiActionButton.Style.forTheme(theme));
    }
    private UiDisplayText text(String value, float size, int color) {
        UiDisplayText text = new UiDisplayText(getContext(), value, size, color);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(VERTICAL); return view; }
    private LinearLayout row() {
        LinearLayout view = new LinearLayout(getContext());
        view.setOrientation(HORIZONTAL); view.setBaselineAligned(false); view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private View divider() {
        View view = new View(getContext());
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(theme.border().divider());
        view.setBackground(background);
        return view;
    }
    static String tr(String key) { return Component.translatable("geometry_node.entity_editor.appearance." + key).getString(); }
    private static int px(float dp) { return UIUtils.dp2pxInt(dp); }
    private static LinearLayout.LayoutParams iconParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(px(32), px(32));
        params.leftMargin = px(6);
        return params;
    }

    private final class CandidateScroll extends UiScrollView {
        private boolean alignCurrent;
        CandidateScroll(Context context) { super(context, theme.scroll()); }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int tail = Math.max(0, MeasureSpec.getSize(heightMeasureSpec) - px(ROW_HEIGHT_DP));
            if (list.getPaddingBottom() != tail) list.setPadding(0, 0, 0, tail);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (!alignCurrent || disposed) return;
            UiActionButton selected = rows.get(selection.selected());
            if (selected != null && selected.getParent() instanceof View item) scrollTo(0, item.getTop());
            alignCurrent = false;
        }
    }
}
