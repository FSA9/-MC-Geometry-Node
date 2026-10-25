package com.mine.geometry_node.client.ui.editor.entity;

import com.mine.geometry_node.client.faction.ClientFactionRepository;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.composite.navigation.UiSubTabs;
import com.mine.geometry_node.client.ui.components.integration.faction.*;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.engine.system.faction.storage.FactionJsonCodec;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static com.mine.geometry_node.client.ui.editor.entity.EntityEditorViews.*;

/** Entity faction page: owns the draft bridge and catalog subscription, not table widgets. */
final class EntityFactionsView extends LinearLayout {
    private final UiTheme theme;
    private final EntityEditDraft draft;
    private final BooleanSupplier editable, catalogLoaded;
    private final Supplier<FactionCatalog> catalog;
    private final Runnable changed;
    private final UiSubTabs tabs;
    private final UiActionButton membershipTab, relationsTab;
    private final UiDisplayText status;
    private final FactionMembershipsView memberships;
    private final FactionRelationsView relations;
    private final Runnable catalogListener = () -> MuiModApi.postToUiThread(this::catalogChanged);
    private ClientFactionRepository repository;
    private String selectedPage = "membership";
    private boolean disposed, listening;

    EntityFactionsView(Context context, UiTheme theme, EntityEditDraft draft, BooleanSupplier editable, Runnable changed) {
        this(context, theme, draft, editable, changed, ClientFactionRepository.INSTANCE::snapshot, ClientFactionRepository.INSTANCE::loaded);
        repository = ClientFactionRepository.INSTANCE;
    }
    EntityFactionsView(Context context, UiTheme theme, EntityEditDraft draft, BooleanSupplier editable, Runnable changed,
                       Supplier<FactionCatalog> catalog, BooleanSupplier catalogLoaded) {
        super(context); this.theme = theme; this.draft = draft; this.editable = editable; this.changed = changed;
        this.catalog = catalog; this.catalogLoaded = catalogLoaded; setOrientation(VERTICAL); setPadding(px(18), px(8), px(18), px(18));
        setBackground(fill(theme.surface().panel()));
        tabs = new UiSubTabs(context, theme);
        membershipTab = tabs.addTab("membership", FactionUi.tr("membership"), () -> selectPage("membership"));
        relationsTab = tabs.addTab("relations", FactionUi.tr("relations"), () -> selectPage("relations"));
        addView(tabs, size(-1, UiSubTabs.HEIGHT));
        status = text(context, "", theme.text().muted()); status.setSingleLine(true); status.setEllipsize(TextUtils.TruncateAt.END); addView(status, size(-1, 30));
        FrameLayout pages = new FrameLayout(context); addView(pages, new LayoutParams(-1, 0, 1));
        memberships = new FactionMembershipsView(context, theme, catalog, () -> profile().memberships(), catalogLoaded, this::setJoined, this::setRank);
        memberships.setEditable(() -> !disposed && editable.getAsBoolean());
        relations = new FactionRelationsView(context, theme, catalog, () -> profile().relations(), this::setRelation); relations.setEditable(this::canEditCatalog);
        pages.addView(memberships, new FrameLayout.LayoutParams(-1, -1)); pages.addView(relations, new FrameLayout.LayoutParams(-1, -1));
        refreshConfirmed(); selectPage(selectedPage);
    }
    void start() {
        if (disposed || listening || repository == null) return;
        listening = true; repository.addChangeListener(catalogListener); repository.refresh();
    }
    private void catalogChanged() { if (!disposed) { refreshConfirmed(); changed.run(); } }
    int factionCount() { return catalog.get().factions().size(); }
    private EntityFactionProfile profile() { return FactionJsonCodec.profile(draft.dictionary("factions")); }
    private boolean canEditCatalog() { return !disposed && editable.getAsBoolean() && catalogLoaded.getAsBoolean(); }
    private void selectPage(String page) {
        if (disposed) return;
        suspend(); selectedPage = page; boolean membership = page.equals("membership");
        memberships.setVisibility(membership ? VISIBLE : GONE); relations.setVisibility(membership ? GONE : VISIBLE);
        tabs.setSelectedTab(page);
    }
    private void setJoined(UUID id, boolean joined) {
        EntityFactionProfile current = profile();
        if (disposed || !editable.getAsBoolean() || joined && (!canEditCatalog() || catalog.get().find(id).isEmpty())) return;
        Map<UUID, Integer> memberships = new LinkedHashMap<>(current.memberships());
        if (joined) memberships.putIfAbsent(id, 0); else memberships.remove(id);
        publish(new EntityFactionProfile(memberships, current.relations()));
    }
    private void setRank(UUID id, int value) {
        EntityFactionProfile current = profile();
        if (!canEditCatalog() || !current.memberships().containsKey(id)
                || catalog.get().find(id).filter(f -> f.ranks().containsKey(value)).isEmpty())
            throw new IllegalArgumentException("Inactive or unregistered entity faction rank");
        Map<UUID, Integer> memberships = new LinkedHashMap<>(current.memberships()); memberships.put(id, value);
        publish(new EntityFactionProfile(memberships, current.relations()));
    }
    private void setRelation(UUID id, int value) {
        EntityFactionProfile current = profile(); boolean removingStale = value == 0 && current.relations().containsKey(id);
        if (!canEditCatalog() || catalog.get().find(id).isEmpty() && !removingStale)
            throw new IllegalArgumentException("Inactive entity faction relation");
        Map<UUID, Integer> scores = new LinkedHashMap<>(current.relations()); if (value == 0) scores.remove(id); else scores.put(id, value);
        publish(new EntityFactionProfile(current.memberships(), scores));
    }
    private void publish(EntityFactionProfile value) { draft.set("factions", FactionJsonCodec.profileValues(value)); refreshConfirmed(); changed.run(); }
    void refreshConfirmed() { if (!disposed) { memberships.refresh(); relations.refresh(); refreshInputState(); } }
    void refreshInputState() {
        if (disposed) return;
        memberships.refreshInputState(); relations.setEditable(this::canEditCatalog);
        String error = repository == null ? "" : repository.error();
        status.setText(!error.isEmpty() ? message(error) : !catalogLoaded.getAsBoolean() ? FactionUi.tr("loading") : "");
        status.setTextColor(error.isEmpty() ? theme.text().muted() : theme.status().error()); status.setVisibility(status.getText().isEmpty() ? GONE : VISIBLE);
    }
    boolean hasPendingChanges() { return relations.hasPendingChanges(); }
    void suspend() { memberships.suspend(); relations.suspend(); }
    void dispose() {
        if (disposed) return; disposed = true;
        if (listening) repository.removeChangeListener(catalogListener);
        memberships.dispose(); relations.dispose();
    }
}
