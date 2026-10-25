package com.mine.geometry_node.client.ui.editor.globaldata;

import com.mine.geometry_node.client.faction.ClientFactionRepository;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.navigation.UiSubTabs;
import com.mine.geometry_node.client.ui.components.integration.faction.*;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.workspace.state.UiStateContext;
import com.mine.geometry_node.core.engine.system.faction.model.FactionDefinition;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import static com.mine.geometry_node.client.ui.components.integration.faction.FactionUi.*;

/** One faction's retained forms. Completed commands go directly to the repository. */
final class FactionEditorView extends LinearLayout {
    private final UUID id;
    private final UiTheme theme;
    private final ClientFactionRepository repository;
    private final BooleanSupplier editable;
    private final Consumer<Supplier<CompletableFuture<Void>>> submit;
    private final Consumer<String> changedTab;
    private final UiSubTabs tabs;
    private final Map<String, View> pages = new LinkedHashMap<>();
    private final FactionRichField name, description;
    private final UiDisplayText heading;
    private final FactionRelationsView relations;
    private final FactionRanksView ranks;
    private final Set<String> initializedSections = new HashSet<>();
    private String section = "info";
    private boolean active;

    FactionEditorView(Context context, UiTheme theme, UUID id, ClientFactionRepository repository,
                      UiStateContext state, String scope, BooleanSupplier editable,
                      Consumer<Supplier<CompletableFuture<Void>>> submit, Consumer<String> changedTab) {
        super(context); this.id = id; this.theme = theme; this.repository = repository;
        this.editable = editable; this.submit = submit; this.changedTab = changedTab; setOrientation(VERTICAL);
        setBackground(fill(theme.surface().sidebar()));
        LinearLayout identity = new LinearLayout(context); identity.setOrientation(VERTICAL); identity.setPadding(px(18), px(17), px(18), px(17));
        heading = new UiDisplayText(context, "", 17, theme.text().strong());
        identity.addView(heading, new LayoutParams(-1, -2));
        addView(identity, new LayoutParams(-1, -2));
        addDivider();
        tabs = new UiSubTabs(context, theme);
        for (String key : List.of("info", "relations", "ranks")) {
            tabs.addTab(key, tr(key), () -> { showSection(key); changedTab.accept(key); });
        }
        addView(tabs, new LayoutParams(-1, px(UiSubTabs.HEIGHT)));
        addDivider();
        FrameLayout host = new FrameLayout(context); addView(host, new LayoutParams(-1, 0, 1));
        LinearLayout info = new LinearLayout(context); info.setOrientation(VERTICAL); info.setPadding(px(18), px(18), px(18), px(18));
        info.addView(label(tr("name")), new LayoutParams(-1, px(28)));
        name = new FactionRichField(context, theme, tr("edit_name"), false, next -> {
            var current = current();
            if (canEdit() && current != null) submit.accept(() -> repository.rename(id, next));
        }); info.addView(name, new LayoutParams(-1, -2));
        LayoutParams descriptionLabel = new LayoutParams(-1, px(28)); descriptionLabel.topMargin = px(20);
        info.addView(label(tr("description")), descriptionLabel);
        description = new FactionRichField(context, theme, tr("edit_description"), true, next -> {
            var current = current();
            if (canEdit() && current != null) submit.accept(() -> repository.describe(id, next));
        }); info.addView(description, new LayoutParams(-1, -2));
        UiScrollView scroll = new UiScrollView(context, theme.scroll()); scroll.addView(info, new FrameLayout.LayoutParams(-1, -2));
        pages.put("info", scroll);
        relations = new FactionRelationsView(context, theme, repository::snapshot,
                () -> { var current = current(); return current == null ? Map.of() : current.relations(); },
                (target, value) -> { if (canEdit()) submit.accept(() -> repository.relation(id, target, value)); });
        relations.setPadding(px(18), px(18), px(18), px(18));
        // Table preferences are shared across definitions within this editor, not one unbounded scope per UUID.
        relations.bindState(state, scope + ":faction-relations"); relations.setEditable(this::canEdit); pages.put("relations", relations);
        ranks = new FactionRanksView(context, theme, this::canEdit, new FactionRanksView.Actions() {
            public void put(Integer previous, int level, RichTextValue name) {
                if (canEdit()) submit.accept(() -> repository.rank(id, previous, level, name));
            }
            public void remove(int level) { if (canEdit()) submit.accept(() -> repository.removeRank(id, level)); }
        });
        ranks.setPadding(px(18), px(18), px(18), px(18));
        pages.put("ranks", ranks);
        pages.values().forEach(page -> host.addView(page, new FrameLayout.LayoutParams(-1, -1)));
        showSection("info");
    }
    void showSection(String value) {
        if (!pages.containsKey(value)) value = "info";
        if (!value.equals("relations")) relations.suspend();
        section = value;
        pages.forEach((key, page) -> page.setVisibility(key.equals(section) ? VISIBLE : GONE));
        tabs.setSelectedTab(section);
        refresh();
    }
    void setActive(boolean value) {
        active = value;
        if (!value) relations.suspend();
        ranks.setActive(value && section.equals("ranks")); refresh();
    }
    void refresh() {
        FactionDefinition faction = current(); if (faction == null) { deactivate(); return; }
        heading.setText(FactionTextViews.display(faction.name())); name.refresh(faction.name()); description.refresh(faction.description());
        name.setEditable(canEdit() && section.equals("info")); description.setEditable(canEdit() && section.equals("info"));
        relations.setEditable(() -> canEdit() && section.equals("relations"));
        ranks.setActive(active && section.equals("ranks"));
        // A queued write still exposes the previous snapshot. Do not flash that old value over
        // the editor's just-submitted number while awaiting the authoritative response.
        if (canEdit() || initializedSections.add(section)) {
            if (section.equals("relations")) relations.refresh();
            if (section.equals("ranks")) ranks.refresh(faction);
            initializedSections.add(section);
        }
    }
    private void deactivate() { name.setEditable(false); description.setEditable(false); ranks.setActive(false); relations.setEditable(() -> false); relations.suspend(); }
    void dispose() { active = false; deactivate(); relations.dispose(); }
    private boolean canEdit() { return active && editable.getAsBoolean() && current() != null; }
    private FactionDefinition current() { return repository.snapshot().find(id).orElse(null); }
    private UiDisplayText label(String text) { return new UiDisplayText(getContext(), text, 13, theme.text().muted()); }
    private void addDivider() {
        View divider = new View(getContext()); divider.setBackground(fill(theme.border().divider()));
        addView(divider, new LayoutParams(-1, Math.max(1, px(1))));
    }
}
