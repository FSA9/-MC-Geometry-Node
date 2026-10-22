package com.mine.geometry_node.client.ui.editor.entity;

import com.mine.geometry_node.client.entityeditor.ClientEntityEditor;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataDictionaryEditor;
import com.mine.geometry_node.client.ui.components.integration.equipment.EntityEquipmentView;
import com.mine.geometry_node.client.ui.editor.entity.graph.EntityGraphsView;
import com.mine.geometry_node.client.ui.overlay.OverlayCloseReason;
import com.mine.geometry_node.client.ui.overlay.OverlayHost;
import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntry;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static com.mine.geometry_node.client.ui.editor.entity.EntityEditorViews.*;

/** Direct-edit entity window. Completed operations are serialized through server-owned transactions. */
public final class EntityEditorWindow extends ModalWindowView {
    interface Gateway extends EntityEditSession.Gateway {}
    private static final Gateway LIVE = new Gateway() {
        public ClientEntityEditor.Request read(String dimension, UUID entity) { return ClientEntityEditor.INSTANCE.read(dimension, entity); }
        public ClientEntityEditor.Request apply(String dimension, UUID entity, String revision, Map<String, Object> values) {
            return ClientEntityEditor.INSTANCE.apply(dimension, entity, revision, values);
        }
    };
    private final UUID entity;
    private final String dimension;
    private final UiTheme theme;
    private final EntityEditSession session;
    private final LinearLayout tabs;
    private final FrameLayout pages;
    private final View blocker;
    private final UiDisplayText status;
    private final UiActionButton reload, close;
    private final List<View> pageViews = new ArrayList<>();
    private final List<UiActionButton> tabButtons = new ArrayList<>();
    private EntityEditDraft draft;
    private EntityAttributesView attributes;
    private EntityEquipmentView equipment;
    private EntityGraphsView graphs;
    private UiDataDictionaryEditor.Embedded data;
    private int selectedTab;
    private long acknowledged;
    private boolean active, changing, inputBlocked;

    public static void show(View anchor, WorldObjectEntry entry, Runnable applied) {
        Objects.requireNonNull(anchor); Objects.requireNonNull(entry); Objects.requireNonNull(applied);
        OverlayHost host = OverlayHost.find(anchor);
        if (host == null || !entry.provider().equals("native")) return;
        UUID entity;
        try { entity = UUID.fromString(entry.references().getOrDefault("entityUuid", "")); }
        catch (IllegalArgumentException invalid) { return; }
        UiTheme theme = UiThemes.EDITOR;
        EntityEditorWindow window = new EntityEditorWindow(anchor, entry.name(), entity, entry.dimension(),
                theme, LIVE, MuiModApi::postToUiThread, applied);
        host.showModal(window, new ModalOptions(false, true, anchor, theme.surface().modalScrim()));
    }

    EntityEditorWindow(View owner, String name, UUID entity, String dimension, UiTheme theme,
                       Gateway gateway, Consumer<Runnable> uiQueue, Runnable applied) {
        this(owner, name, entity, dimension, theme, gateway, uiQueue, applied,
                (task, delay) -> CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)
                        .execute(() -> uiQueue.accept(task)),
                failure -> ClientEntityEditor.reportFailure(name, failure.message()));
    }

    EntityEditorWindow(View owner, String name, UUID entity, String dimension, UiTheme theme,
                       Gateway gateway, Consumer<Runnable> uiQueue, Runnable applied,
                       EntityEditSession.Scheduler scheduler, Consumer<EntityEditSession.Failure> failures) {
        super(owner.getContext(), tr("title", name), MovementMode.DRAGGABLE, Style.forTheme(theme));
        bindSessionOwner(owner);
        this.entity = entity; this.dimension = dimension; this.theme = theme;
        session = new EntityEditSession(dimension, entity, gateway, scheduler, uiQueue, applied, failures);
        setPreferredSizeDp(1040, 670); setMinimumSizeDp(640, 460);
        titleView().setSingleLine(true); titleView().setEllipsize(TextUtils.TruncateAt.END);
        contentHost().setPadding(0, 0, 0, 0);
        LinearLayout root = column(getContext());
        tabs = row(getContext()); tabs.setPadding(px(14), px(8), px(14), px(8));
        root.addView(tabs, size(-1, 48)); root.addView(divider(getContext(), theme), size(-1, 1));
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
        if (data != null) data.dispose();
        if (graphs != null) graphs.dismissMenus();
        session.close();
        pages.removeAllViews(); pageViews.clear(); tabs.removeAllViews(); tabButtons.clear();
        attributes = null; data = null; equipment = null; graphs = null;
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
            if (data != null) data.dispose();
            if (graphs != null) graphs.dismissMenus();
            pages.removeAllViews(); tabs.removeAllViews(); pageViews.clear(); tabButtons.clear();
            draft = next;
            attributes = new EntityAttributesView(getContext(), theme, draft, entity, dimension, this::changed);
            addPage("tab.attributes", attributes);
            equipment = null;
            if (draft.flag("equipmentSupported")) {
                equipment = new EntityEquipmentView(getContext(), theme,
                        (Map<String, ItemStack>)(Map<?, ?>) draft.dictionary("equipment"),
                        (List<ItemStack>) draft.snapshot().get("inventory"), this::changed);
                equipment.setCreative(draft.flag("equipmentCreative"));
                equipment.setOnOperationFinished(this::changed);
                if (draft.snapshot().get("preview") instanceof EntityTemplateValue preview) equipment.setPreview(preview);
                else equipment.setPreviewReference(entity, dimension);
                addPage("tab.equipment", equipment);
            }
            addPage("tab.tags", new EntityTagsView(getContext(), theme, draft));
            data = UiDataDictionaryEditor.createEmbedded(this, draft.dataBinding(), theme);
            data.setOnPendingChangesChanged(this::changed); addPage("tab.data", data.view());
            graphs = new EntityGraphsView(getContext(), theme, draft.graphs("graphs"),
                    draft.graphs("availableGraphs"), bindings -> draft.set("graphs", bindings));
            addPage("tab.graphs", graphs);
            pages.addView(blocker, new FrameLayout.LayoutParams(-1, -1));
            selectedTab = Math.min(selectedTab, pageViews.size() - 1); selectTab(selectedTab);
        } finally { changing = false; }
    }
    private void addPage(String label, View page) {
        int index = pageViews.size(); pageViews.add(page); page.setVisibility(GONE);
        pages.addView(page, new FrameLayout.LayoutParams(-1, -1));
        UiActionButton tab = button(getContext(), theme, label, () -> switchTab(index));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(32), 1); lp.rightMargin = px(4);
        tabs.addView(tab, lp); tabButtons.add(tab);
    }
    private void switchTab(int index) {
        if (session.loading() || index == selectedTab) return;
        // Page instances own unfinished forms. Navigation never stages or validates them.
        if (data != null) data.suspend();
        if (graphs != null) graphs.dismissMenus();
        selectTab(index);
        if (data != null && pageViews.get(index) == data.view()) data.resume();
        updateState();
    }
    private void selectTab(int index) {
        selectedTab = index;
        for (int i = 0; i < pageViews.size(); i++) {
            pageViews.get(i).setVisibility(i == index ? VISIBLE : GONE);
            tabButtons.get(i).setPalette(UiActionButton.Palette.forRole(theme,
                    i == index ? UiActionButton.Role.PRIMARY : UiActionButton.Role.QUIET));
        }
    }
    private void changed() {
        if (changing || !active || session.reconciling()) return;
        changing = true;
        try { stageEquipment(); } finally { changing = false; }
        updateState();
    }
    private boolean dirty() {
        return draft != null && (draft.dirty() || attributes != null && attributes.hasPendingChanges()
                || data != null && data.hasPendingChanges() || equipmentChanged());
    }
    private boolean equipmentChanged() {
        if (equipment == null || draft == null) return false;
        return !equipment.dropped().isEmpty()
                || !com.mine.geometry_node.core.engine.system.worldquery.WorldObjectDataValues.equivalent(
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
            if (equipment != null && equipment.isCommitReady() && !equipmentPending()) equipment.acceptCommitted(
                    (Map<String, ItemStack>)(Map<?, ?>)draft.dictionary("equipment"),
                    (List<ItemStack>)draft.snapshot().get("inventory"), draft.flag("equipmentCreative"));
            if (data != null) data.refreshCommitted();
        } finally { changing = false; }
    }
    private void updateState() {
        if (status == null) return;
        boolean block = session.loading();
        blocker.setVisibility(block ? VISIBLE : GONE);
        if (block != inputBlocked) {
            inputBlocked = block;
            pages.setDescendantFocusability(block ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (block) { pages.clearFocus(); requestFocus(); }
        }
        for (UiActionButton tab : tabButtons) tab.setEnabled(!session.loading());
        reload.setEnabled(active && !session.loading() && !session.saving()
                && (session.blocked() || draft == null || !draft.dirty())); close.setEnabled(active);
        if (equipment != null) equipment.setInputBlocked(session.blocked()
                || equipment.isCommitReady() && (session.saving() || equipmentPending()));
        String error = session.error().isEmpty() ? "" : message(session.error())
                + (session.failure() == null ? "" : " " + tr("reload_required"));
        status.setTextColor(error.isEmpty() ? theme.text().muted() : theme.status().error());
        status.setText(session.loading() || session.saving() ? tr(session.saving() ? "saving" : "loading")
                : !error.isEmpty() ? error : tr(dirty() ? "draft_dirty" : "draft_clean"));
    }
}
