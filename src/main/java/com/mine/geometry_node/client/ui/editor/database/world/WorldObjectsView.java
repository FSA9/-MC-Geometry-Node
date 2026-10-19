package com.mine.geometry_node.client.ui.editor.database.world;

import com.mine.geometry_node.client.worldquery.ClientWorldObjectRepository;
import com.mine.geometry_node.client.ui.components.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.list.UiNavigationRow;
import com.mine.geometry_node.client.ui.components.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.list.UiListSelection;
import com.mine.geometry_node.client.ui.components.display.UiTagStrip;
import com.mine.geometry_node.client.ui.components.valuepreview.EntityPreviewCell;
import com.mine.geometry_node.client.ui.components.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.input.UiSearchBar;
import com.mine.geometry_node.client.ui.components.menu.UiSearchableMenu;
import com.mine.geometry_node.client.ui.components.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.table.UiTableColumns;
import com.mine.geometry_node.client.ui.components.table.UiTableRow;
import com.mine.geometry_node.client.ui.components.table.UiTableRowFrame;
import com.mine.geometry_node.client.ui.components.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.table.UiTableViewport;
import com.mine.geometry_node.client.ui.editor.database.DatabaseSelection;
import com.mine.geometry_node.client.ui.editor.database.DatabaseViewState;
import com.mine.geometry_node.client.ui.workspace.state.*;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.worldquery.*;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
import static com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.*;

/** Server query presentation. Only immutable summaries cross from the client thread to this UI. */
public final class WorldObjectsView extends FrameLayout {
    private ClientWorldObjectRepository.Session session;
    private boolean awaitingScopeResponse;
    private final Consumer<DatabaseSelection> selectionChanged;
    private final LinearLayout rows;
    private final TextView status;
    private final UiActionButton dimensionButton, typeButton, providerButton, lifetimeButton, focusButton;
    private final UiSearchInput search, tags, source;
    private final UiSearchBar searchBar;
    private final UiActionButton filterButton;
    private final UiStateContext uiState;
    private String stateScope;
    private boolean restoringState;
    private String submittedSearch = "";
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, UiActionButton> rowViews = new LinkedHashMap<>();
    private final Map<String, RowBinding> rowBindings = new LinkedHashMap<>();
    private record RowBinding(UiActionButton button, UiCheckBox checkbox, View stripe,
                              UiTableRow cells, int baseColor) {}
    private final UiTableColumns columns = new UiTableColumns(List.of(
            UiTableStyle.rowNumberColumn(),
            UiTableStyle.selectionColumn(),
            UiTableStyle.column("name", 240),
            UiTableStyle.column("type", 140),
            UiTableStyle.column("value", 120),
            UiTableStyle.column("dimension", 150),
            UiTableStyle.column("position", 180),
            UiTableStyle.column("lifetime", 100),
            UiTableStyle.column("tags", 180)));
    private final LinearLayout table;
    private final UiCheckBox selectAll;
    private ClientWorldObjectRepository.Snapshot snapshot, renderedSnapshot;
    private List<WorldObjectEntry> mountedEntries;
    private List<WorldObjectType> mountedTypes;
    private DatabaseSelection publishedSelection;
    private List<WorldObjectType> typeCatalog = List.of();
    private List<String> dimensionCatalog = List.of();
    private WorldObjectQuery.Range range = WorldObjectQuery.Range.GLOBAL;
    private String dimension = "", type = "", typeProvider = "", provider = "", lifetime = "";
    private volatile boolean shown, disposed;
    private boolean sortAscending = true;
    private String sort = "name", anchorKey;
    private UiSearchableMenu menu;
    private UiPopover filterPopover;
    private final LinearLayout navigation, categoryRows, advancedFilters;
    private final ThinResizableDivider navigationDivider;
    private final UiTableRow tableHeader;
    private final TextView resultTitle;
    private final Map<String, UiActionButton> categoryButtons = new LinkedHashMap<>();
    private WorldObjectCategories.Category category;
    private String extraProvider = "";
    private long lastClickNanos;
    private String lastClickKey;

    private final Runnable scopeTask = () -> {
        if (disposed || Objects.equals(stateScope, UiStateScopes.current())) return;
        switchScope();
        if (shown) { if (!Boolean.getBoolean("gn.standalone")) queryNow(); else refresh(); }
    };
    private final Runnable scopeChanged = () -> { removeCallbacks(scopeTask); post(scopeTask); };
    private final Runnable queryTask = this::query;
    private final Runnable renderTask = this::refresh;
    private final Runnable changed = () -> {
        if (!disposed && shown) { removeCallbacks(renderTask); post(renderTask); }
    };

    public WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged) {
        this(context, selectionChanged, UiStateContext.ephemeral());
    }
    public WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged, UiStateContext state) {
        super(context);
        uiState = Objects.requireNonNull(state);
        columns.restoreCustomWidths(uiState.read(DatabaseViewState.WORLD_WIDTHS, DatabaseViewState.GLOBAL).columns());
        columns.addUserStateListener(() -> uiState.write(DatabaseViewState.WORLD_WIDTHS, DatabaseViewState.GLOBAL,
                new DatabaseViewState.Widths(columns.exportCustomWidths())));
        this.selectionChanged = selectionChanged;
        session = ClientWorldObjectRepository.INSTANCE.newSession();
        snapshot = session.snapshot(); session.addChangeListener(changed);
        UiFixedSplitLayout body = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.HORIZONTAL,
                UiFixedSplitLayout.Edge.FIRST, 216, 100, 240);
        body.restorePreferredExtentDp(uiState.read(DatabaseViewState.WORLD_SPLIT, DatabaseViewState.GLOBAL).extentDp());
        body.addUserStateListener(() -> uiState.write(DatabaseViewState.WORLD_SPLIT, DatabaseViewState.GLOBAL,
                new DatabaseViewState.Split(body.preferredExtentDp())));
        addView(body, new LayoutParams(-1, -1));
        navigation = new LinearLayout(context); navigation.setOrientation(LinearLayout.VERTICAL);
        navigation.setPadding(px(8), px(10), px(8), px(10));
        navigation.setBackground(background(UiTheme.Surface.ROOT));
        TextView categoryTitle = label(context, tr("object_types"), UiTheme.Text.MUTED);
        categoryTitle.setPadding(px(8),0,0,0);
        navigation.addView(categoryTitle, new LinearLayout.LayoutParams(-1, px(32)));
        categoryRows = new LinearLayout(context); categoryRows.setOrientation(LinearLayout.VERTICAL);
        UiScrollView navScroll = new UiScrollView(context); navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.addView(categoryRows, new LayoutParams(-1,-2));
        navigation.addView(navScroll, new LinearLayout.LayoutParams(-1,0,1));
        navigationDivider = body.divider();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(background(UiTheme.Surface.CANVAS));
        body.setPanes(navigation, column);
        LinearLayout queryPanel = new LinearLayout(context); queryPanel.setOrientation(LinearLayout.VERTICAL);
        queryPanel.setPadding(px(18),px(6),px(18),px(10));
        queryPanel.setBackground(background(UiTheme.Surface.PANEL));
        TextView breadcrumb = label(context,tr("world_breadcrumb"),UiTheme.Text.MUTED);
        queryPanel.addView(breadcrumb,new LinearLayout.LayoutParams(-1,px(30)));
        searchBar = new UiSearchBar(context, tr("search"),
                Component.translatable("geometry_node.asset_library.toolbar.search").getString(), this::submitSearch);
        search = searchBar.input();
        LinearLayout queryRow = new LinearLayout(context);
        queryRow.setBaselineAligned(false);
        queryRow.setGravity(Gravity.CENTER_VERTICAL);
        queryRow.addView(searchBar, new LinearLayout.LayoutParams(0, px(32), 1));
        lifetimeButton=button(context,"",this::chooseLifetime);
        filterButton=button(context,tr("filter"),this::showFilters);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(px(60), px(32));
        filterParams.leftMargin = px(8);
        queryRow.addView(filterButton, filterParams);
        queryPanel.addView(queryRow, new LinearLayout.LayoutParams(-1, px(32)));
        column.addView(queryPanel,new LinearLayout.LayoutParams(-1,-2));
        advancedFilters=new LinearLayout(context); advancedFilters.setOrientation(LinearLayout.VERTICAL);
        dimensionButton=button(context,"",this::chooseDimension);
        providerButton=button(context,"",this::chooseProvider);
        typeButton=button(context,"",this::chooseType);
        tags=input(tr("tags_hint")); source=input(tr("source_hint"));
        filterField(advancedFilters,tr("dimension"),dimensionButton);
        filterField(advancedFilters,tr("lifetime"),lifetimeButton);
        filterField(advancedFilters,tr("provider"),providerButton);
        filterField(advancedFilters,tr("type"),typeButton);
        filterField(advancedFilters,tr("tags"),tags);
        filterField(advancedFilters,tr("source"),source);
        filterReset(advancedFilters,this::resetFilters);
        LinearLayout resultBar=new LinearLayout(context); resultBar.setGravity(Gravity.CENTER_VERTICAL);
        resultBar.setPadding(px(14),0,px(10),0);
        resultTitle=label(context,tr("all_objects"),UiTheme.Text.NORMAL);
        UIUtils.setLockedTextSize(resultTitle,UiTableStyle.CELL_TEXT_SIZE_DP);
        resultTitle.setSingleLine(true);resultTitle.setEllipsize(TextUtils.TruncateAt.END);
        resultBar.addView(resultTitle,new LinearLayout.LayoutParams(0,-1,1));
        focusButton=button(context,tr("focus"),this::focusSelected); focusButton.setEnabled(false);
        resultBar.addView(focusButton,new LinearLayout.LayoutParams(-2,px(28)));
        column.addView(resultBar,new LinearLayout.LayoutParams(-1,px(UiTableStyle.RESULT_BAR_HEIGHT)));
        tableHeader=UiTableStyle.header(context, columns);
        tableHeader.addView(UiTableStyle.rowNumberCell(context, 0),new ViewGroup.LayoutParams(-1,-1));
        selectAll=new UiCheckBox(context);
        selectAll.setContentDescription(tr("select_all"));
        selectAll.setOnCheckedChangeListener((checkbox, checked)->toggleAll());
        tableHeader.addView(UiTableStyle.selectionCell(context, selectAll),new ViewGroup.LayoutParams(-1,-1));
        for(String key:List.of("name","type","value","dimension","position","lifetime","tags")) addHeader(key);
        table=new LinearLayout(context); table.setOrientation(LinearLayout.VERTICAL);
        table.addView(tableHeader,new LinearLayout.LayoutParams(-1,px(UiTableStyle.HEADER_HEIGHT)));
        UiScrollView scroll=new UiScrollView(context); scroll.setFillViewport(true);
        // Reserve the scrollbar outside the rows without changing their column widths.
        scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        rows=new LinearLayout(context); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows,new LayoutParams(-1,-2));
        table.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        UiTableViewport tableViewport=new UiTableViewport(context, columns, table);
        column.addView(tableViewport,new LinearLayout.LayoutParams(-1,0,1));
        status=label(context,tr("query_hint"),UiTheme.Text.MUTED);
        UIUtils.setLockedTextSize(status,11); status.setPadding(px(14),0,px(14),0); status.setSingleLine(true);
        status.setBackground(background(UiTheme.Surface.PANEL));
        column.addView(status,new LinearLayout.LayoutParams(-1,px(UiTableStyle.STATUS_BAR_HEIGHT)));
        for(UiSearchInput input:List.of(tags,source))input.setOnQueryChanged(value->scheduleQuery());
        selectAll.setEnabled(false);
        restoreState();
        rebuildNavigation(); updateFilters();
        UiStateScopes.addChangeListener(scopeChanged);
    }
    private void showFilters() {
        if (!shown || disposed) return;
        dismissMenu();
        if(filterPopover!=null&&filterPopover.isOpen()){filterPopover.dismiss();return;}
        if(filterPopover==null)filterPopover=new UiPopover(getContext(),advancedFilters,280);
        filterPopover.showBelow(filterButton,this);
    }
    private void submitSearch() {
        submittedSearch=search.getText().toString().trim();
        search.clearFocus();
        queryNow();
    }
    private void resetFilters() {
        range=WorldObjectQuery.Range.GLOBAL;
        dimension=type=typeProvider=provider=lifetime=extraProvider="";category=null;
        submittedSearch="";
        search.setText("");tags.setText("");source.setText("");
        updateFilters();rebuildNavigation();queryNow();
    }
    private void rebuildNavigation() {
        categoryRows.removeAllViews();categoryButtons.clear();
        addCategory("",tr("all_objects"),
                snapshot.entries().size(),()->{category=null;extraProvider=provider=type=typeProvider="";queryNow();});
        for(var value:WorldObjectCategories.all()) addCategory(value.id(),Component.translatable(value.labelKey()).getString(),
                snapshot.entries().stream().filter(value::matches).count(),()->{
                    category=value;extraProvider=provider=type=typeProvider="";queryNow();
                });
        Set<String> known=new HashSet<>();WorldObjectCategories.all().forEach(c->known.addAll(c.providers()));
        java.util.stream.Stream.concat(typeCatalog.stream().map(WorldObjectType::provider),
                snapshot.entries().stream().map(WorldObjectEntry::provider)).filter(p->!known.contains(p)&&!p.equals("scene"))
                .distinct().forEach(id->addCategory("provider:"+id,providerLabel(id),snapshot.entries().stream().filter(e->e.provider().equals(id)).count(),()->{
                            category=null;extraProvider=provider=id;type=typeProvider="";queryNow();
                        }));
        updateNavigationSelection();
    }
    private void addCategory(String id,String name,long count,Runnable action) {
        UiNavigationRow row=new UiNavigationRow(getContext(),name,count);
        row.button().setOnClickListener(view->action.run());
        categoryButtons.put(id,row.button());
        categoryRows.addView(row,new LinearLayout.LayoutParams(-1,px(33)));
    }
    private void updateNavigationSelection() {
        String id=category!=null?category.id():extraProvider.isEmpty()?"":"provider:"+extraProvider;
        categoryButtons.forEach((key,button)->button.setPalette(rowPalette(key.equals(id),UiTheme.Surface.ROOT)));
        resultTitle.setText((category==null?tr("all_objects"):Component.translatable(category.labelKey()).getString())
                +"  ·  "+sortedEntries().size());
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        navigation.setVisibility(MeasureSpec.getSize(widthSpec)<px(450)?GONE:VISIBLE);
        navigationDivider.setVisibility(navigation.getVisibility());
        super.onMeasure(widthSpec,heightSpec);
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        dismissMenu();if(filterPopover!=null)filterPopover.dismiss();
    }

    private UiSearchInput input(String hint) {
        UiSearchInput input = new UiSearchInput(getContext());
        UIUtils.setLockedTextSize(input, 12); input.setHint(hint); return input;
    }
    private void addHeader(String key) {
        String title = key.equals("value") ? Component.translatable("geometry_node.data_library.column_value").getString() : tr(key);
        UiActionButton button = UiTableStyle.headerCell(getContext(), title, key.equals("value") ? null : () -> {
            if (!shown || disposed) return;
            sortAscending = !sort.equals(key) || !sortAscending; sort = key; saveState(); resetDoubleClick();
            if (!snapshot.loading()) rebuildRows();
        });
        if (key.equals("value")) {
            button.setClickable(false);
            button.setFocusable(false);
        }
        tableHeader.addView(button, new ViewGroup.LayoutParams(-1, -1));
    }
    private void scheduleQuery() {
        if (restoringState) return;
        saveState();
        removeCallbacks(queryTask);
        if (shown && !disposed) postDelayed(queryTask, 300);
    }
    private void queryNow() { if (restoringState) return; saveState(); removeCallbacks(queryTask); query(); }
    private void query() {
        if (!shown || disposed) return;
        if (!Objects.equals(stateScope, UiStateScopes.current())) switchScope();
        final WorldObjectQuery query;
        try {
            Set<String> providers = one(typeProvider.isEmpty() ? provider : typeProvider), types = one(type);
            if (category != null && providers.isEmpty()) providers = category.providers();
            if (category != null && type.isEmpty()) {
                Set<String> matching = new LinkedHashSet<>();
                typeCatalog.stream().filter(t -> category.accepts().test(t.provider(),t.id())).forEach(t -> matching.add(t.id()));
                // Entity registries can exceed the wire budget; retain provider filtering plus a page predicate.
                if (!matching.isEmpty() && matching.size() <= WorldObjectQuery.MAX_FILTERS && !category.id().equals("entity")) types = matching;
                if(category.id().equals("interaction"))types=Set.of("minecraft:interaction");
            }
            query = new WorldObjectQuery(range, dimension, providers, types, submittedSearch,
                    tagFilters(tags.getText().toString()), one(source.getText().toString().trim()), one(lifetime),
                    128, WorldObjectQuery.MAX_RESULTS);
        } catch (IllegalArgumentException invalid) { setStatus(tr("invalid_query")); return; }
        dismissMenu(); resetDoubleClick();
        awaitingScopeResponse = false;
        session.query(query);
        refresh();
    }
    private static Set<String> one(String value) { return value.isEmpty() ? Set.of() : Set.of(value); }
    static Set<String> tagFilters(String input) {
        Set<String> result = new LinkedHashSet<>();
        for (String tag : input.split("[,，]")) if (!tag.isBlank()) result.add(tag.trim());
        return result;
    }
    private void refresh() {
        if (disposed || !shown || awaitingScopeResponse || !Objects.equals(stateScope, UiStateScopes.current())) return;
        snapshot = session.snapshot();
        if (snapshot.equals(renderedSnapshot)) { updateInteractionState(); return; }
        renderedSnapshot=snapshot;
        if (!snapshot.loading()) {
            typeCatalog = snapshot.types();
            dimensionCatalog = snapshot.dimensionIds();
        }
        updateFilters();
        rebuildNavigation();
        // Preserve selection while a replacement query is in flight; reconcile only against its final answer.
        if (!snapshot.loading()) {
            Set<String> live = new HashSet<>(); sortedEntries().forEach(entry -> live.add(entry.key()));
            selected.retainAll(live);
            if (!live.contains(anchorKey)) anchorKey = null;
            resetDoubleClick(); rebuildRows();
        } else {
            resetDoubleClick(); dismissMenu();
            updateSelection();
        }
        refreshStatus();
    }
    private void refreshStatus() {
        String text=snapshot.loading()?tr("loading"):tr("result_count",sortedEntries().size(),selected.size());
        if(!snapshot.message().isEmpty())text+=" · "+tr("message."+snapshot.message());
        setStatus(text);
    }
    private void setStatus(String text) { status.setText(text); }
    private String typeLabel(String providerId, String id) {
        for (WorldObjectType value : typeCatalog) if (value.provider().equals(providerId) && value.id().equals(id))
            return value.labelKey().isEmpty() ? id : Component.translatable(value.labelKey()).getString();
        return id;
    }
    private void updateFilters() {
        dimensionButton.setText(dimension.isEmpty() ? tr("range." + range.name().toLowerCase(Locale.ROOT)) : dimension);
        providerButton.setText(provider.isEmpty() ? tr("all") : providerLabel(provider));
        typeButton.setText(type.isEmpty() ? tr("all") : typeLabel(typeProvider, type));
        lifetimeButton.setText(lifetime.isEmpty() ? tr("all") : lifetimeLabel(lifetime));
        for (UiActionButton button : List.of(dimensionButton, providerButton, typeButton, lifetimeButton)) {
            button.setEllipsize(TextUtils.TruncateAt.END);
        }
    }
    private String providerLabel(String id) {
        String key = "geometry_node.database.provider." + id;
        String text = Component.translatable(key).getString();
        return text.equals(key) ? id : text;
    }
    private void chooseDimension() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        options.put(WorldObjectQuery.Range.GLOBAL.name(), tr("range.global"));
        options.put(WorldObjectQuery.Range.LOADED.name(), tr("range.loaded"));
        for (String id : dimensionCatalog) options.put(id, id);
        choose(dimensionButton, "dimension", options, this::selectDimension);
    }
    private void selectDimension(String value) {
        range = value.equals(WorldObjectQuery.Range.LOADED.name()) ? WorldObjectQuery.Range.LOADED : WorldObjectQuery.Range.GLOBAL;
        dimension = value.equals(WorldObjectQuery.Range.GLOBAL.name()) || value.equals(WorldObjectQuery.Range.LOADED.name()) ? "" : value;
        updateFilters(); queryNow();
    }
    private void chooseProvider() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>(); options.put("", tr("all"));
        java.util.stream.Stream.concat(typeCatalog.stream().map(WorldObjectType::provider),
                snapshot.entries().stream().map(WorldObjectEntry::provider))
                .distinct().forEach(id -> options.put(id, providerLabel(id)));
        choose(providerButton, "provider", options, value -> { provider = value; extraProvider=value; category=null; type = typeProvider = ""; updateFilters(); queryNow(); });
    }
    private void chooseType() {
        record TypeIdentity(String provider, String id) {}
        LinkedHashMap<String, TypeIdentity> identities = new LinkedHashMap<>();
        for (var value : typeCatalog) {
            if ((provider.isEmpty() || provider.equals(value.provider()))
                    && (category == null || category.accepts().test(value.provider(), value.id()))) {
                identities.put(WorldObjectEntry.key(value.provider(), "", value.id()),
                        new TypeIdentity(value.provider(), value.id()));
            }
        }
        for (var entry : snapshot.entries()) {
            if ((provider.isEmpty() || provider.equals(entry.provider())) && (category == null || category.matches(entry))) {
                identities.putIfAbsent(WorldObjectEntry.key(entry.provider(), "", entry.type()),
                        new TypeIdentity(entry.provider(), entry.type()));
            }
        }
        LinkedHashMap<String, String> options = new LinkedHashMap<>(); options.put("", tr("all"));
        identities.forEach((key, value) -> options.put(key,
                typeOption(value.provider(), value.id()) + " · " + providerLabel(value.provider())));
        choose(typeButton, "type", options, value -> {
            TypeIdentity identity = identities.get(value);
            type = identity == null ? "" : identity.id();
            typeProvider = identity == null ? "" : identity.provider();
            updateFilters(); queryNow();
        });
    }
    private String typeOption(String providerId, String id) {
        String label = typeLabel(providerId, id);
        return label.equals(id) ? id : label + " · " + id;
    }
    private String lifetimeLabel(String id) {
        String key = "geometry_node.database.lifetime." + id;
        String text = Component.translatable(key).getString();
        return text.equals(key) ? id : text;
    }
    private void chooseLifetime() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>(); options.put("", tr("all"));
        for (String id : List.of("persistent", "binding", "process", "runtime", "loaded")) options.put(id, tr("lifetime." + id));
        choose(lifetimeButton, "lifetime", options, value -> { lifetime = value; updateFilters(); queryNow(); });
    }
    private void choose(View anchor, String title, LinkedHashMap<String, String> labels, Consumer<String> callback) {
        dismissMenu();
        if (!shown || disposed) return;
        menu = new UiSearchableMenu(getContext(), tr(title), List.copyOf(labels.keySet()), labels,
                new UiSearchableMenu.Style(240, 8), value -> {
                    dismissMenu();
                    if (shown && !disposed) callback.accept(value);
                });
        menu.showAt(anchor, this);
    }
    private void dismissMenu() { if (menu != null) { menu.dismiss(); menu = null; } }
    private List<WorldObjectEntry> sortedEntries() {
        Comparator<WorldObjectEntry> comparator = switch (sort) {
            case "type" -> Comparator.comparing(entry -> typeLabel(entry.provider(), entry.type()), String.CASE_INSENSITIVE_ORDER);
            case "dimension" -> Comparator.comparing(WorldObjectEntry::dimension);
            case "lifetime" -> Comparator.comparing(WorldObjectEntry::lifetime);
            case "tags" -> Comparator.comparing(entry -> String.join(",",entry.tags()));
            case "position" -> Comparator.comparing(WorldObjectEntry::dimension)
                    .thenComparingDouble(entry -> entry.position().x)
                    .thenComparingDouble(entry -> entry.position().y)
                    .thenComparingDouble(entry -> entry.position().z);
            default -> Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER);
        };
        if (!sortAscending) comparator = comparator.reversed();
        return snapshot.entries().stream().filter(e -> category == null || category.matches(e)).sorted(comparator.thenComparing(WorldObjectEntry::key)).toList();
    }
    private String displayName(WorldObjectEntry entry) { return entry.name().isEmpty() ? typeLabel(entry.provider(), entry.type()) : entry.name(); }
    private void rebuildRows() {
        List<WorldObjectEntry> entries = sortedEntries();
        if (entries.equals(mountedEntries) && typeCatalog.equals(mountedTypes)) {
            updateSelection();
            return;
        }
        mountedEntries = entries;
        mountedTypes = typeCatalog;
        rows.removeAllViews(); rowViews.clear(); rowBindings.clear();
        if (entries.isEmpty()) {
            TextView empty = label(getContext(), tr("empty"), UiTheme.Text.MUTED);
            empty.setGravity(Gravity.CENTER); rows.addView(empty, new LinearLayout.LayoutParams(-1, px(96)));
        }
        int index=0;
        for (WorldObjectEntry entry : entries) {
            UiActionButton row = UiTableStyle.rowButton(getContext());
            row.setPadding(0, 0, 0, 0);
            int baseColor=UiTableStyle.rowColor(index++%2!=0);
            UiTableRow line = new UiTableRow(getContext(), columns);
            line.setMinimumHeight(px(UiTableStyle.ROW_HEIGHT));
            line.addView(UiTableStyle.rowNumberCell(getContext(), index),new ViewGroup.LayoutParams(-1,-1));
            UiCheckBox checkbox=new UiCheckBox(getContext());
            checkbox.setContentDescription(displayName(entry));
            checkbox.setOnCheckedChangeListener((view,checked)->toggleCheckbox(entry,checked));
            line.addView(UiTableStyle.selectionCell(getContext(), checkbox),new ViewGroup.LayoutParams(-1,-1));
            var presentation=WorldObjectCategories.forEntry(entry);
            cell(line,displayName(entry));
            cell(line,presentation==null?typeLabel(entry.provider(), entry.type()):Component.translatable(presentation.labelKey()).getString());
            if (entry.provider().equals("native") && !entry.type().equals("minecraft:interaction")) {
                EntityPreviewCell preview = new EntityPreviewCell(getContext());
                preview.setVisibilityRow(line);
                UUID entityId = null;
                try { entityId = UUID.fromString(entry.references().getOrDefault("entityUuid", "")); }
                catch (IllegalArgumentException ignored) { }
                preview.setReference(entityId, entry.dimension());
                line.addView(preview, new ViewGroup.LayoutParams(-1, -1));
            } else {
                cell(line, "");
            }
            cell(line,entry.dimension());
            cell(line,position(entry));
            cell(line,lifetimeLabel(entry.lifetime()));
            UiTagStrip tagCell=new UiTagStrip(getContext());tagCell.setTags(entry.tags());
            line.addView(tagCell,new ViewGroup.LayoutParams(-1,-1));

            // Cells determine height; background and selection overlays follow without stretching the row.
            FrameLayout host = new UiTableRowFrame(getContext(), line, row);
            View stripe=new View(getContext());stripe.setBackground(background(UiTheme.Control.FOCUS));
            host.addView(stripe,new LayoutParams(Math.max(1,px(2)),-1));
            View border=new View(getContext());border.setBackground(background(UiTableStyle.DIVIDER_COLOR));
            host.addView(border,new LayoutParams(-1,Math.max(1,px(1)),Gravity.BOTTOM));
            boolean[] modifiers = new boolean[2];
            row.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == icyllis.modernui.view.MotionEvent.ACTION_DOWN) {
                    modifiers[0] = event.isCtrlPressed(); modifiers[1] = event.isShiftPressed();
                } else if (event.getActionMasked() == icyllis.modernui.view.MotionEvent.ACTION_CANCEL) {
                    modifiers[0] = modifiers[1] = false;
                    resetDoubleClick();
                }
                return false;
            });
            row.setOnClickListener(view -> {
                boolean toggle=modifiers[0], extend=modifiers[1];
                modifiers[0] = modifiers[1] = false;
                if (!canSelect()) return;
                select(entry, toggle, extend);
                row.requestFocus();
                rowClicked(entry.key(),toggle||extend,System.nanoTime());
            });
            rowViews.put(entry.key(), row);
            rowBindings.put(entry.key(),new RowBinding(row,checkbox,stripe,line,baseColor));
            rows.addView(host, new LinearLayout.LayoutParams(-1, -2));
        }
        updateSelection();
    }
    private void cell(UiTableRow row, String text) {
        TextView cell = UiTableStyle.cell(getContext(), text, UiTheme.Text.NORMAL);
        row.addView(cell, new ViewGroup.LayoutParams(-1, -1));
    }
    private boolean canSelect() { return shown && !disposed && !awaitingScopeResponse && Objects.equals(stateScope, UiStateScopes.current()) && !snapshot.loading(); }
    private void toggleCheckbox(WorldObjectEntry entry, boolean checked) {
        resetDoubleClick();
        if (!canSelect()) { updateSelection(); return; }
        if (checked) selected.add(entry.key()); else selected.remove(entry.key());
        anchorKey=entry.key();
        updateSelection();
    }
    private void toggleAll() {
        resetDoubleClick();
        if (canSelect()) {
            List<String> keys=sortedEntries().stream().map(WorldObjectEntry::key).toList();
            if (selected.containsAll(keys)) selected.removeAll(keys); else selected.addAll(keys);
        }
        updateSelection();
    }
    private void resetDoubleClick() { lastClickKey=null; lastClickNanos=0; }
    private void rowClicked(String key, boolean modified, long now) {
        if (modified) { resetDoubleClick(); return; }
        if (key.equals(lastClickKey) && now-lastClickNanos<350_000_000L) {
            resetDoubleClick(); focusSelected();
        } else { lastClickKey=key; lastClickNanos=now; }
    }
    private void select(WorldObjectEntry entry, boolean toggle, boolean extend) {
        if (!canSelect()) return;
        anchorKey=UiListSelection.click(selected,sortedEntries().stream().map(WorldObjectEntry::key).toList(),
                anchorKey,entry.key(),toggle,extend);
        updateSelection();
    }
    private void updateSelection() {
        rowBindings.forEach((key,binding)->{
            boolean checked=selected.contains(key);
            binding.button().setPalette(rowPalette(checked,binding.baseColor()));
            binding.checkbox().setChecked(checked);
            binding.stripe().setVisibility(checked?VISIBLE:INVISIBLE);
        });
        List<WorldObjectEntry> entries=sortedEntries();
        long count=entries.stream().filter(entry->selected.contains(entry.key())).count();
        selectAll.setChecked(!entries.isEmpty() && count==entries.size());
        selectAll.setIndeterminate(count>0 && count<entries.size());
        selectAll.setContentDescription(tr("select_all")+" · "+tr("selected",count));
        updateInteractionState();
        DatabaseSelection next=selection();
        // Actions resolve live state when invoked; their newly allocated callbacks are not content changes.
        if (publishedSelection==null?next!=null:!publishedSelection.hasSamePresentation(next)) {
            publishedSelection=next;
            selectionChanged.accept(next);
        }
        refreshStatus();
    }
    private void updateInteractionState() {
        boolean enabled=canSelect();
        rowBindings.values().forEach(binding->{
            binding.button().setEnabled(enabled);
            binding.checkbox().setEnabled(enabled);
        });
        selectAll.setEnabled(enabled && !rowBindings.isEmpty());
        focusButton.setEnabled(enabled && !selectedEntries().isEmpty());
    }
    private List<WorldObjectEntry> selectedEntries() {
        return snapshot.entries().stream().filter(entry -> selected.contains(entry.key())).toList();
    }
    public DatabaseSelection selection() {
        if(awaitingScopeResponse || !Objects.equals(stateScope, UiStateScopes.current()) || snapshot.loading())return null;
        List<WorldObjectEntry> entries = selectedEntries();
        if (entries.isEmpty()) return null;
        if(entries.size()>1)return new DatabaseSelection(tr("selected",entries.size()),tr("world"),"",0,
                List.of(new DatabaseSelection.Field(tr("selected_count"),Integer.toString(entries.size()))),
                List.of(new DatabaseSelection.Action(tr("focus"),this::focusSelected)),List.of());
        var entry=entries.getFirst();
        var position=List.of(new DatabaseSelection.Field("X",number(entry.position().x)),
                new DatabaseSelection.Field("Y",number(entry.position().y)),new DatabaseSelection.Field("Z",number(entry.position().z)));
        var metadata=List.of(new DatabaseSelection.Field(tr("type"),typeOption(entry.provider(), entry.type())),
                new DatabaseSelection.Field(tr("lifetime"),lifetimeLabel(entry.lifetime())),
                new DatabaseSelection.Field(tr("id"),entry.key()));
        var source=new ArrayList<DatabaseSelection.Field>();
        source.add(new DatabaseSelection.Field(tr("source"),entry.source().isEmpty()?"—":entry.source()));
        source.add(new DatabaseSelection.Field(tr("provider"),providerLabel(entry.provider())));
        entry.references().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(ref->
                source.add(new DatabaseSelection.Field(ref.getKey(),ref.getValue())));
        return new DatabaseSelection(displayName(entry),typeLabel(entry.provider(), entry.type())+" · "+tr("world"),
                "",UiTheme.Text.MUTED,List.of(),
                List.of(new DatabaseSelection.Action(tr("focus"),this::focusSelected)),List.of(
                new DatabaseSelection.Section(tr("spatial_position"),entry.dimension(),DatabaseSelection.Kind.POSITION,position),
                new DatabaseSelection.Section(tr("object_information"),"",DatabaseSelection.Kind.METADATA,metadata),
                new DatabaseSelection.Section(tr("tags"),"",DatabaseSelection.Kind.TAGS,
                        entry.tags().stream().map(tag->new DatabaseSelection.Field("",tag)).toList()),
                new DatabaseSelection.Section(tr("source"),"",DatabaseSelection.Kind.SOURCE,source)));
    }
    private static String number(double value) {return String.format(Locale.ROOT,"%.2f",value);}
    private static String position(WorldObjectEntry entry) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", entry.position().x, entry.position().y, entry.position().z);
    }
    private void focusSelected() {
        if (canSelect() && !selectedEntries().isEmpty()) setStatus(WorldObjectNavigation.focus(selectedEntries()));
    }
    private void saveState() {
        if (restoringState || stateScope == null) return;
        uiState.write(DatabaseViewState.WORLD, stateScope, new DatabaseViewState.World(
                submittedSearch, tags.getText().toString(), source.getText().toString(), range.name(),
                dimension, provider, type, typeProvider, lifetime, category == null ? "" : category.id(),
                extraProvider, sort, sortAscending));
    }
    private void switchScope() {
        removeCallbacks(queryTask); removeCallbacks(renderTask);
        dismissMenu(); if (filterPopover != null) filterPopover.dismiss();
        saveState(); restoreState(); selected.clear(); anchorKey = null;
        session.removeChangeListener(changed); session.close();
        session = ClientWorldObjectRepository.INSTANCE.newSession(); session.addChangeListener(changed);
        awaitingScopeResponse = true;
        snapshot = new ClientWorldObjectRepository.Snapshot(WorldObjectQuery.defaults(), WorldObjectPage.empty(0, ""), true, 0);
        renderedSnapshot = null; mountedEntries = null; mountedTypes = null;
        typeCatalog = List.of(); dimensionCatalog = List.of();
        rows.removeAllViews(); rowViews.clear(); rowBindings.clear(); resetDoubleClick();
        updateFilters(); rebuildNavigation(); updateSelection();
    }
    private void restoreState() {
        stateScope = UiStateScopes.current();
        var value = uiState.read(DatabaseViewState.WORLD, stateScope);
        restoringState = true;
        try {
            submittedSearch = value.search(); search.setText(submittedSearch);
            tags.setText(value.tags()); source.setText(value.source());
            try { range = WorldObjectQuery.Range.valueOf(value.range()); }
            catch (IllegalArgumentException ignored) { range = WorldObjectQuery.Range.GLOBAL; }
            dimension = value.dimension(); provider = value.provider(); type = value.type();
            typeProvider = value.typeProvider(); lifetime = value.lifetime(); extraProvider = value.extraProvider();
            category = WorldObjectCategories.all().stream().filter(c -> c.id().equals(value.category())).findFirst().orElse(null);
            sort = value.sort(); sortAscending = value.ascending();
        } finally { restoringState = false; }
    }
    public void onShow() {
        if (disposed || shown) return;
        if (!Objects.equals(stateScope, UiStateScopes.current())) {
            switchScope();
        }
        shown = true;
        if (!Boolean.getBoolean("gn.standalone")) queryNow(); else refresh();
    }
    public void onHide() {
        saveState();
        shown = false; resetDoubleClick(); removeCallbacks(queryTask); removeCallbacks(renderTask); dismissMenu();
        if(filterPopover!=null)filterPopover.dismiss();
        updateInteractionState();
    }
    public void onDispose() {
        if (disposed) return;
        onHide(); disposed = true;
        UiStateScopes.removeChangeListener(scopeChanged); removeCallbacks(scopeTask);
        session.removeChangeListener(changed); session.close();
    }
}
