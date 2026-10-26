package com.mine.geometry_node.client.ui.components.integration.entity.editor;

import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataDataValues;

import com.mine.geometry_node.client.feature.entity.edit.ClientEntityEditor;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataDictionaryEditor;
import com.mine.geometry_node.client.ui.components.integration.equipment.EntityEquipmentView;
import com.mine.geometry_node.client.ui.components.integration.entity.editor.graph.EntityGraphsView;
import com.mine.geometry_node.client.ui.components.integration.entity.editor.graph.EntityGraphActions;
import com.mine.geometry_node.client.ui.overlay.OverlayCloseReason;
import com.mine.geometry_node.client.ui.overlay.OverlayHost;
import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.text.style.RelativeSizeSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static com.mine.geometry_node.client.ui.components.integration.entity.editor.EntityEditorViews.*;

/** Direct-edit entity modal. Completed operations are serialized through server-owned transactions. */
public final class EntityEditorDialog extends ModalWindowView {
    interface Gateway extends EntityEditSession.Gateway {}
    @FunctionalInterface
    interface FactionsFactory {
        EntityFactionsView create(Context context, UiTheme theme, EntityEditDraft draft,
                                  BooleanSupplier editable, Runnable changed);
    }
    private static final Gateway LIVE = new Gateway() {
        public ClientEntityEditor.Request read(String dimension, UUID entity) { return ClientEntityEditor.INSTANCE.read(dimension, entity); }
        public ClientEntityEditor.Request apply(String dimension, UUID entity, String revision, Map<String, Object> values) {
            return ClientEntityEditor.INSTANCE.apply(dimension, entity, revision, values);
        }
    };
    private final UUID entity;
    private final String dimension;
    private final String initialName;
    private final UiTheme theme;
    private final EntityEditSession session;
    private final FactionsFactory factionsFactory;
    private final EntityGraphActions.Factory graphActionsFactory;
    private final Runnable registrationApplied;
    private final LinearLayout tabs;
    private final FrameLayout pages;
    private final View blocker;
    private final UiDisplayText status;
    private final UiActionButton reload, close;
    private final List<View> pageViews = new ArrayList<>();
    private final List<UiActionButton> tabButtons = new ArrayList<>();
    private final Map<UiActionButton, TabPresentation> tabPresentations = new LinkedHashMap<>();
    private record TabPresentation(String key, View underline) {}
    private EntityEditDraft draft;
    private EntityAttributesView attributes;
    private EntityFactionsView factions;
    private EntityTagsView tags;
    private EntityEquipmentView equipment;
    private EntityGraphsView graphs;
    private UiDataDictionaryEditor.Embedded data;
    private int selectedTab;
    private long acknowledged;
    private boolean active, changing, inputBlocked;
    private String registryType = "";

    public static void show(View anchor, WorldObjectEntry entry, Runnable applied,
                            EntityGraphActions.Factory graphActionsFactory) {
        Objects.requireNonNull(anchor); Objects.requireNonNull(entry); Objects.requireNonNull(applied);
        OverlayHost host = OverlayHost.find(anchor);
        if (host == null || !entry.provider().equals("native")) return;
        if (entry.entityState() != null && (!entry.entityState().loaded() || !entry.entityState().alive())) return;
        UUID entity;
        try { entity = entry.entityState() == null ? UUID.fromString(entry.references().getOrDefault("entityUuid", "")) : entry.entityState().entityUuid(); }
        catch (IllegalArgumentException invalid) { return; }
        UiTheme theme = UiThemes.EDITOR;
        EntityEditorDialog window = new EntityEditorDialog(anchor, entry.name(), entity, entry.dimension(),
                theme, LIVE, MuiModApi::postToUiThread, applied,
                (task, delay) -> CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)
                        .execute(() -> MuiModApi.postToUiThread(task)),
                failure -> reportFailure(entry.name(), failure.message()),
                EntityFactionsView::new, graphActionsFactory);
        host.showModal(window, new ModalOptions(false, true, anchor, theme.surface().modalScrim()));
    }

    EntityEditorDialog(View owner, String name, UUID entity, String dimension, UiTheme theme,
                       Gateway gateway, Consumer<Runnable> uiQueue, Runnable applied) {
        this(owner, name, entity, dimension, theme, gateway, uiQueue, applied,
                (task, delay) -> CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)
                        .execute(() -> uiQueue.accept(task)),
                failure -> reportFailure(name, failure.message()));
    }

    EntityEditorDialog(View owner, String name, UUID entity, String dimension, UiTheme theme,
                       Gateway gateway, Consumer<Runnable> uiQueue, Runnable applied,
                       EntityEditSession.Scheduler scheduler, Consumer<EntityEditSession.Failure> failures) {
        this(owner, name, entity, dimension, theme, gateway, uiQueue, applied, scheduler, failures,
                EntityFactionsView::new);
    }

    /** Detached faction pages keep window tests independent of the live catalog connection. */
    EntityEditorDialog(View owner, String name, UUID entity, String dimension, UiTheme theme,
                       Gateway gateway, Consumer<Runnable> uiQueue, Runnable applied,
                       EntityEditSession.Scheduler scheduler, Consumer<EntityEditSession.Failure> failures,
                       FactionsFactory factionsFactory) {
        this(owner, name, entity, dimension, theme, gateway, uiQueue, applied, scheduler, failures,
                factionsFactory, (page, pageTheme) -> EntityGraphActions.unavailable());
    }

    private EntityEditorDialog(View owner, String name, UUID entity, String dimension, UiTheme theme,
                       Gateway gateway, Consumer<Runnable> uiQueue, Runnable applied,
                       EntityEditSession.Scheduler scheduler, Consumer<EntityEditSession.Failure> failures,
                       FactionsFactory factionsFactory, EntityGraphActions.Factory graphActionsFactory) {
        super(owner.getContext(), tr("title", name), MovementMode.DRAGGABLE, Style.forTheme(theme));
        bindSessionOwner(owner);
        this.entity = entity; this.dimension = dimension; this.theme = theme; initialName = name;
        session = new EntityEditSession(dimension, entity, gateway, scheduler, uiQueue, applied, failures);
        registrationApplied = applied;
        this.factionsFactory = Objects.requireNonNull(factionsFactory);
        this.graphActionsFactory = Objects.requireNonNull(graphActionsFactory);
        setPreferredSizeDp(1120, 750); setMinimumSizeDp(640, 460);
        titleView().setSingleLine(true); titleView().setEllipsize(TextUtils.TruncateAt.END);
        UIUtils.setLockedTextSize(titleView(), 15);
        contentHost().setPadding(0, 0, 0, 0);
        LinearLayout root = column(getContext());
        tabs = row(getContext()); tabs.setPadding(px(20), 0, px(20), 0);
        tabs.setBackground(fill(theme.surface().header()));
        root.addView(tabs, size(-1, 44)); root.addView(divider(getContext(), theme), size(-1, 1));
        pages = new FrameLayout(getContext()); root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        blocker = new View(getContext()); blocker.setClickable(true); blocker.setFocusable(true);
        blocker.setBackground(fill(UiTheme.withAlpha(theme.surface().panel(), 150)));
        pages.addView(blocker, new FrameLayout.LayoutParams(-1, -1));
        root.addView(divider(getContext(), theme), size(-1, 1));
        status = text(getContext(), tr("loading"), theme.text().muted());
        reload = button(getContext(), theme, "reload", this::reload);
        close = button(getContext(), theme, "close", this::requestClose);
        root.addView(new Footer(getContext(), status, reload, close), size(-1, -2));
        setContent(root); updateState();
    }

    @Override protected void onWindowShown() {
        if (active || session.closed()) return;
        active = true; session.observe(this::renderSession); session.start();
    }
    @Override protected void onWindowSuspended() {
        if (factions != null) factions.suspend();
        if (graphs != null) graphs.dismissMenus();
        if (data != null) data.suspend();
    }
    @Override protected void onWindowResumed() {
        if (data != null) data.resume();
        updateState();
    }
    @Override protected void onWindowClosed(OverlayCloseReason reason) { dispose(); }
    @Override protected void onWindowDestroyed() { dispose(); }
    private void dispose() {
        if (session.closed()) return;
        active = false;
        // Disarm blur handlers before removing controls. Closing never completes raw input.
        if (attributes != null) attributes.dispose();
        if (factions != null) factions.dispose();
        if (data != null) data.dispose();
        if (graphs != null) graphs.dispose();
        session.close();
        pages.removeAllViews(); pageViews.clear(); tabs.removeAllViews(); tabButtons.clear(); tabPresentations.clear();
        attributes = null; factions = null; tags = null; data = null; equipment = null; graphs = null;
    }
    @Override public boolean canClose(OverlayCloseReason reason) { return true; }
    private void renderSession() {
        if (!active) return;
        if (session.draft() != null && session.draft() != draft) install(session.draft());
        else if (acknowledged != session.acknowledged()) refreshConfirmed();
        acknowledged = session.acknowledged();
        updateState();
    }
    @Override public boolean onEscapePressed() {
        if (equipment != null && equipment.getVisibility() == VISIBLE && equipment.onEscapePressed()) return true;
        if (data != null && data.onEscapePressed()) return true;
        if (graphs != null && graphs.onEscapePressed()) return true;
        return false;
    }
    private void reload() {
        if (active) session.reload();
    }
    @SuppressWarnings("unchecked")
    private void install(EntityEditDraft next) {
        changing = true;
        try {
            if (attributes != null) attributes.dispose();
            if (factions != null) factions.dispose();
            if (data != null) data.dispose();
            if (graphs != null) graphs.dispose();
            pages.removeAllViews(); tabs.removeAllViews(); pageViews.clear(); tabButtons.clear(); tabPresentations.clear();
            draft = next;
            registryType = String.valueOf(draft.snapshot().getOrDefault("registryType", ""));
            EntityEditDraft attributesDraft = draft;
            attributes = new EntityAttributesView(getContext(), theme, attributesDraft, entity, dimension, this::changed,
                    () -> active && !session.closed() && !session.blocked() && !session.loading() && session.draft() == attributesDraft,
                    registrationApplied);
            addPage("tab.attributes", attributes);
            equipment = null;
            if (draft.flag("equipmentSupported")) {
                equipment = new EntityEquipmentView(getContext(), theme,
                        (Map<String, ItemStack>)(Map<?, ?>) draft.dictionary("equipment"),
                        (List<ItemStack>) draft.snapshot().get("inventory"), this::changed);
                equipment.setCreative(draft.flag("equipmentCreative"));
                equipment.setOnOperationFinished(this::changed);
                if (draft.snapshot().containsKey("appearance")) equipment.setAppearanceSpec(
                        com.mine.geometry_node.core.engine.system.entity.edit.EntityEditorAppearance.read(draft.dictionary("appearance")));
                if (draft.snapshot().get("preview") instanceof EntityTemplateValue preview) equipment.setPreview(preview);
                else equipment.setPreviewReference(entity, dimension);
                addPage("tab.equipment", equipment);
            }
            tags = new EntityTagsView(getContext(), theme, draft);
            addPage("tab.tags", tags);
            data = UiDataDictionaryEditor.createEmbedded(this, draft.dataBinding(), theme, UiMetrics.TEXT_BODY);
            data.setOnPendingChangesChanged(this::changed); addPage("tab.data", data.view());
            factions = null;
            if (draft.snapshot().containsKey("factions")) {
                EntityEditDraft factionsDraft = draft;
                factions = factionsFactory.create(getContext(), theme, factionsDraft,
                        () -> active && !session.closed() && !session.blocked() && !session.loading()
                                && session.draft() == factionsDraft, this::changed);
                addPage("tab.factions", factions);
            }
            EntityEditDraft graphDraft = draft;
            graphs = new EntityGraphsView(getContext(), theme, graphDraft.graphs("graphs"),
                    graphDraft.graphs("availableGraphs"), bindings -> graphDraft.set("graphs", bindings),
                    () -> active && !session.closed() && !session.blocked() && !session.loading()
                            && session.draft() == graphDraft, graphActionsFactory, this::requestClose);
            addPage("tab.graphs", graphs);
            pages.addView(blocker, new FrameLayout.LayoutParams(-1, -1));
            selectedTab = Math.min(selectedTab, pageViews.size() - 1); selectTab(selectedTab);
            if (factions != null) factions.start();
        } finally { changing = false; }
    }
    private void addPage(String label, View page) {
        int index = pageViews.size(); pageViews.add(page); page.setVisibility(GONE);
        pages.addView(page, new FrameLayout.LayoutParams(-1, -1));
        UiActionButton tab = UiTableStyle.rowButton(getContext(), theme);
        UIUtils.setLockedTextSize(tab, UiMetrics.TEXT_BODY);
        tab.setSingleLine(true); tab.setEllipsize(TextUtils.TruncateAt.END);
        tab.setPadding(px(12), 0, px(12), 0);
        tab.setOnClickListener(view -> switchTab(index));
        FrameLayout host = new FrameLayout(getContext()) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                // Only the caption determines a compact tab's width. The match-parent
                // underline must follow that width, not expand it to the whole tab bar.
                View caption = getChildAt(0);
                measureChildWithMargins(caption, widthSpec, 0, heightSpec, 0);
                setMeasuredDimension(resolveSize(caption.getMeasuredWidth(), widthSpec),
                        resolveSize(caption.getMeasuredHeight(), heightSpec));
                getChildAt(1).measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(px(2), MeasureSpec.EXACTLY));
            }
        };
        host.addView(tab, new FrameLayout.LayoutParams(-2, -1));
        View underline = divider(getContext(), theme);
        underline.setBackground(fill(theme.selection().outline()));
        host.addView(underline, new FrameLayout.LayoutParams(-1, px(2), Gravity.BOTTOM));
        tabs.addView(host, new LinearLayout.LayoutParams(-2, -1));
        tabButtons.add(tab); tabPresentations.put(tab, new TabPresentation(label, underline));
    }
    private void switchTab(int index) {
        if (session.loading() || index == selectedTab) return;
        // Page instances own unfinished forms. Navigation never stages or validates them.
        if (data != null) data.suspend();
        if (factions != null) factions.suspend();
        if (graphs != null) graphs.dismissMenus();
        selectTab(index);
        if (data != null && pageViews.get(index) == data.view()) data.resume();
        updateState();
    }
    private void selectTab(int index) {
        selectedTab = index;
        for (int i = 0; i < pageViews.size(); i++) {
            pageViews.get(i).setVisibility(i == index ? VISIBLE : GONE);
        }
        updateTabs();
    }
    private void updateTabs() {
        for (int i = 0; i < tabButtons.size(); i++) {
            UiActionButton button = tabButtons.get(i);
            TabPresentation tab = tabPresentations.get(button);
            boolean selected = i == selectedTab;
            String label = tr(tab.key());
            int count = draft == null ? -1 : switch (tab.key()) {
                case "tab.tags" -> draft.tags().size();
                case "tab.data" -> draft.dictionary("data").size();
                case "tab.factions" -> factions == null ? -1 : factions.factionCount();
                case "tab.graphs" -> draft.graphs("graphs").size();
                default -> -1;
            };
            CharSequence caption = label;
            if (count >= 0) {
                SpannableString counted = new SpannableString(label + "  " + count);
                counted.setSpan(new ForegroundColorSpan(theme.text().muted()), label.length(), counted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                counted.setSpan(new RelativeSizeSpan(.85f), label.length(), counted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                caption = counted;
            }
            if (!button.getText().toString().contentEquals(caption)) button.setText(caption);
            button.setPalette(new UiActionButton.Palette(theme.surface().header(), theme.control().iconHover(),
                    theme.control().iconPressed(), selected ? theme.text().strong() : theme.text().muted()));
            tab.underline().setVisibility(selected ? VISIBLE : INVISIBLE);
        }
    }
    private void updateTitle() {
        String heading = tr("heading");
        String name = draft == null ? initialName : RichTextValue.from(draft.dictionary("attributes").get("name")).plain();
        if (name.isBlank()) name = initialName;
        String detail = name + (registryType.isBlank() ? "" : " · " + registryType);
        if (titleView().getText().toString().equals(heading + "   " + detail)) return;
        SpannableString caption = new SpannableString(heading + "   " + detail);
        int start = heading.length() + 3;
        caption.setSpan(new ForegroundColorSpan(theme.text().muted()), start, caption.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        caption.setSpan(new RelativeSizeSpan(.85f), start, caption.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setTitle(caption);
    }
    private void changed() {
        if (changing || !active || session.reconciling()) return;
        changing = true;
        try { stageEquipment(); } finally { changing = false; }
        updateState();
    }
    private boolean dirty() {
        return draft != null && (draft.dirty() || attributes != null && attributes.hasPendingChanges()
                || factions != null && factions.hasPendingChanges()
                || data != null && data.hasPendingChanges() || equipmentChanged());
    }
    private boolean equipmentChanged() {
        if (equipment == null || draft == null) return false;
        return !equipment.dropped().isEmpty()
                || !com.mine.geometry_node.core.engine.metadata.value.GraphMetadataDataValues.equivalent(
                Map.of("equipment", draft.dictionary("equipment"), "inventory", draft.snapshot().get("inventory")),
                Map.of("equipment", equipment.equipment(), "inventory", equipment.inventory()));
    }
    private void stageEquipment() {
        if (equipment != null && equipment.isCommitReady() && equipmentChanged()) {
            draft.setAll(Map.of("equipment", equipment.equipment(), "inventory", equipment.inventory(),
                    "equipmentDrops", equipment.dropped()));
        }
    }
    private boolean equipmentPending() {
        if (draft == null) return false;
        return draft.changes().keySet().stream().anyMatch(key ->
                key.equals("equipment") || key.equals("inventory") || key.equals("equipmentDrops"));
    }
    @SuppressWarnings("unchecked")
    private void refreshConfirmed() {
        changing = true;
        try {
            if (attributes != null) attributes.refreshConfirmedValues();
            if (factions != null) factions.refreshConfirmed();
            if (tags != null) tags.refreshConfirmed();
            if (graphs != null) graphs.refreshConfirmed(draft.graphs("graphs"), draft.graphs("availableGraphs"));
            if (equipment != null && draft.snapshot().containsKey("appearance")) equipment.setAppearanceSpec(
                    com.mine.geometry_node.core.engine.system.entity.edit.EntityEditorAppearance.read(draft.dictionary("appearance")));
            if (equipment != null && equipment.isCommitReady() && !equipmentPending()) equipment.acceptCommitted(
                    (Map<String, ItemStack>)(Map<?, ?>)draft.dictionary("equipment"),
                    (List<ItemStack>)draft.snapshot().get("inventory"), draft.flag("equipmentCreative"));
            if (data != null) data.refreshCommitted();
        } finally { changing = false; }
    }
    private void updateState() {
        if (status == null) return;
        updateTabs(); updateTitle();
        boolean block = session.loading();
        blocker.setVisibility(block ? VISIBLE : GONE);
        if (block != inputBlocked) {
            inputBlocked = block;
            pages.setDescendantFocusability(block ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (block) { pages.clearFocus(); requestFocus(); }
        }
        for (UiActionButton tab : tabButtons) tab.setEnabled(!session.loading());
        if (attributes != null) attributes.refreshInputState();
        if (factions != null) factions.refreshInputState();
        reload.setEnabled(active && !session.loading() && !session.saving()
                && (session.blocked() || draft == null || !draft.dirty())); close.setEnabled(active);
        reload.setVisibility(session.blocked() || !session.error().isEmpty() ? VISIBLE : GONE);
        if (equipment != null) equipment.setInputBlocked(session.blocked()
                || equipment.isCommitReady() && (session.saving() || equipmentPending()));
        String error = session.error().isEmpty() ? "" : message(session.error())
                + (session.failure() == null ? "" : " " + tr("reload_required"));
        status.setTextColor(error.isEmpty() ? theme.text().muted() : theme.status().error());
        status.setText(session.loading() || session.saving() ? tr(session.saving() ? "saving" : "loading")
                : !error.isEmpty() ? error : tr(dirty() ? "draft_dirty" : "draft_clean"));
    }
}
