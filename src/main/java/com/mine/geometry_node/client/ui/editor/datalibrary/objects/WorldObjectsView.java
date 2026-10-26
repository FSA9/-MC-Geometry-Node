package com.mine.geometry_node.client.ui.editor.datalibrary.objects;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.mine.geometry_node.client.feature.worldquery.ClientWorldObjectRepository;
import com.mine.geometry_node.client.feature.entity.index.ClientEntityIndex;
import com.mine.geometry_node.client.feature.faction.ClientFactionRepository;
import com.mine.geometry_node.client.ui.components.integration.faction.FactionCatalogSubscription;
import com.mine.geometry_node.client.ui.components.integration.faction.FactionUi;
import com.mine.geometry_node.client.ui.components.integration.entity.EntityRegistrationView;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.composite.list.UiNavigationRow;
import com.mine.geometry_node.client.ui.components.composite.input.UiSearchBar;
import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.composite.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.list.UiListSelection;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagStrip;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataSummaryCell;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataDictionaryEditor;
import com.mine.geometry_node.client.ui.components.integration.metadata.DataDictionaryBinding;
import com.mine.geometry_node.client.ui.components.integration.metadata.RepositoryDataDictionaryBindings;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityPreviewCell;
import com.mine.geometry_node.client.ui.components.composite.table.UiColumnFilterPopover;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableFilterHeader;
import com.mine.geometry_node.core.utils.ColumnFilters;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableColumns;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableBatchWork;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRowFrame;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableViewport;
import com.mine.geometry_node.client.ui.editor.datalibrary.inspector.DatabaseSelection;
import com.mine.geometry_node.client.integration.entity.WorkspaceEntityEditor;
import com.mine.geometry_node.client.ui.editor.datalibrary.state.DatabaseViewState;
import com.mine.geometry_node.client.ui.workspace.state.*;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectCategories;
import com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectDataEdits;
import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntries;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectGraphFilter;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectPage;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectQuery;
import com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectTagEdits;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectType;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntityFilter;
import static com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntityFilter.*;
import static com.mine.geometry_node.client.ui.editor.datalibrary.objects.WorldObjectPresentation.*;
import com.mine.geometry_node.client.integration.game.WorkspaceWorldObjectNavigation;
import com.mine.geometry_node.core.node.RegistryDataManager;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.util.function.Consumer;
import static com.mine.geometry_node.client.ui.editor.datalibrary.presentation.DatabasePresentation.*;

/** Server query presentation. Only immutable summaries cross from the client thread to this UI. */
public final class WorldObjectsView extends FrameLayout {
    public enum Mode { WORLD, ENTITIES }
    private final Mode mode;
    private final UUID faction;
    private final String factionOwnerScope;
    private WorldObjectEntityFilter entityFilter;
    private final Runnable registrationTask = () -> { if (this.shown && !this.disposed) queryNow(); };
    private final Runnable registrationChanged = () -> { removeCallbacks(registrationTask); post(registrationTask); };
    private ClientWorldObjectRepository.Session session;
    private boolean awaitingScopeResponse;
    private final Consumer<DatabaseSelection> selectionChanged;
    private final LinearLayout rows;
    private final UiScrollView scroll;
    private ScrollAnchor pendingScrollAnchor;
    private record ScrollAnchor(String key, int offset, int fallbackY) {}
    private final UiTableBatchWork rowBatch;
    private final NativeTypeLabels nativeTypeLabels;
    private final FactionCatalogSubscription factionSubscription;

    /** Client registry seam: tests supply labels without booting Minecraft or an FML loader. */
    interface NativeTypeLabels {
        Iterable<String> ids();
        String label(String id);
    }
    private static final class RegistryNativeTypeLabels implements NativeTypeLabels {
        @Override public Iterable<String> ids() {
            return RegistryDataManager.getAllEntityTypes();
        }
        @Override public String label(String id) {
            Identifier identifier = Identifier.tryParse(id);
            var type = identifier == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
            return type == null ? id : Component.translatable(type.getDescriptionId()).getString();
        }
    }
    private final TextView status;
    private final UiActionButton focusButton;
    private UiSearchBar entitySearch;
    private ColumnFilters columnFilters = ColumnFilters.empty();
    private WorldObjectGraphFilter graphFilter = WorldObjectGraphFilter.empty();
    private String registryTypeFilter = "";
    private final Map<String, UiTableFilterHeader> filterHeaders = new LinkedHashMap<>();
    private final UiStateContext uiState;
    private String stateScope;
    private boolean restoringState;
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, UiActionButton> rowViews = new LinkedHashMap<>();
    private final Map<String, RowBinding> rowBindings = new LinkedHashMap<>();
    private record RowBinding(UiActionButton button, UiCheckBox checkbox, View stripe,
                              UiTableRow cells, int baseColor, WorldObjectEntry entry) {}
    private final UiTableColumns columns;
    private static UiTableColumns createColumns(Mode mode) {
        List<UiTableColumns.Column> values = new ArrayList<>(List.of(
            UiTableStyle.rowNumberColumn(), UiTableStyle.selectionColumn()));
        if (mode == Mode.ENTITIES) values.addAll(List.of(
            UiTableStyle.column("value", 120),
            UiTableStyle.column("registry_type", 180),
            UiTableStyle.column("dimension", 150),
            UiTableStyle.column("loaded", 100),
            UiTableStyle.column("registered", 140),
            UiTableStyle.column("position", 180)));
        else values.addAll(List.of(
            UiTableStyle.column("name", 240),
            UiTableStyle.column("type", 140),
            UiTableStyle.column("value", 120),
            UiTableStyle.column("dimension", 150),
            UiTableStyle.column("position", 180),
            UiTableStyle.column("lifetime", 100)));
        values.addAll(List.of(
            UiTableStyle.column("tags", 180),
            UiTableStyle.column("data", 220),
            UiTableStyle.column("graphs", 220)));
        if (mode == Mode.ENTITIES) values.addAll(List.of(
                UiTableStyle.column("factions", 220), UiTableStyle.column("updated", 180)));
        return new UiTableColumns(values, UiTableColumns.Sizing.INDEPENDENT);
    }
    private int columnIndex(String id) {
        for (int index = 0; index < columns.size(); index++)
            if (columns.column(index).id().equals(id)) return index;
        throw new IllegalArgumentException("Unknown world-query column: " + id);
    }
    private String typeColumnId() { return mode == Mode.ENTITIES ? "registered" : "type"; }
    private String lifetimeColumnId() { return mode == Mode.ENTITIES ? "loaded" : "lifetime"; }
    private final LinearLayout table;
    private final UiCheckBox selectAll;
    private ClientWorldObjectRepository.Snapshot snapshot, renderedSnapshot;
    private WorldObjectQuery completedQuery;
    private List<WorldObjectEntry> mountedEntries;
    private List<WorldObjectType> mountedTypes;
    private DatabaseSelection publishedSelection;
    private boolean inspectorWaitingForQuery;
    private List<WorldObjectType> typeCatalog = List.of();
    private List<WorldObjectType> indexedTypeCatalog;
    private final Map<String, WorldObjectType> typeLookup = new HashMap<>();
    private List<String> dimensionCatalog = List.of();
    private WorldObjectPage.Counts navigationCounts = WorldObjectPage.Counts.empty();
    private WorldObjectQuery.Range range = WorldObjectQuery.Range.GLOBAL;
    private String provider = "";
    private volatile boolean shown, disposed;
    private boolean sortAscending = true;
    private String sort = "name", anchorKey;
    private UiColumnFilterPopover filterPopover;
    private final LinearLayout navigation, categoryRows;
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
        if (fixedFactionScopeExpired()) { onDispose(); return; }
        switchScope();
        if (shown) { if (!Boolean.getBoolean("gn.standalone")) queryNow(); else refresh(); }
    };
    private final Runnable scopeChanged = () -> { removeCallbacks(scopeTask); post(scopeTask); };
    private final Runnable renderTask = this::refresh;
    private final Runnable fallbackQueryTask = () -> {
        if (!shown || disposed || snapshot.loading() || !Objects.equals(stateScope, UiStateScopes.current())) return;
        if (!needsFallbackQuery()) return;
        try { submitQuery(buildQuery(columnFilters, range)); }
        catch (IllegalArgumentException invalid) { setStatus(tr("invalid_query")); }
    };
    private final Runnable changed = () -> {
        if (!disposed && shown) { removeCallbacks(renderTask); removeCallbacks(fallbackQueryTask); post(renderTask); }
    };

    public WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged) {
        this(context, selectionChanged, UiStateContext.ephemeral());
    }
    public WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged, UiStateContext state) {
        this(context, selectionChanged, state, new RegistryNativeTypeLabels());
    }
    public WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged, UiStateContext state, Mode mode) {
        this(context, selectionChanged, state, new RegistryNativeTypeLabels(), mode);
    }
    WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged, UiStateContext state,
                     NativeTypeLabels nativeTypeLabels) {
        this(context, selectionChanged, state, nativeTypeLabels, Mode.WORLD);
    }
    /** A fixed faction constraint reuses the entity table, including its inspector and row actions. */
    public WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged, UiStateContext state,
                            UUID faction) {
        this(context, selectionChanged, state, new RegistryNativeTypeLabels(), Mode.ENTITIES, Objects.requireNonNull(faction));
    }
    WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged, UiStateContext state,
                     NativeTypeLabels nativeTypeLabels, Mode mode) {
        this(context, selectionChanged, state, nativeTypeLabels, mode, null);
    }
    WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged, UiStateContext state,
                     NativeTypeLabels nativeTypeLabels, Mode mode, UUID faction) {
        super(context);
        if (faction != null && mode != Mode.ENTITIES) throw new IllegalArgumentException("Faction members require entity mode");
        this.faction = faction;
        factionSubscription = mode == Mode.ENTITIES
                ? new FactionCatalogSubscription(this, ClientFactionRepository.INSTANCE, this::refreshFactionCells) : null;
        factionOwnerScope = UiStateScopes.current();
        this.mode = Objects.requireNonNull(mode); columns = createColumns(mode);
        entityFilter = new WorldObjectEntityFilter(mode == Mode.ENTITIES ? Scope.ENTITIES : Scope.WORLD_OBJECTS,
                Load.ANY, Registration.ANY);
        this.nativeTypeLabels = Objects.requireNonNull(nativeTypeLabels);
        rowBatch = new UiTableBatchWork(this);
        uiState = Objects.requireNonNull(state);
        columns.restoreCustomWidths(uiState.read(widthsKey(), DatabaseViewState.GLOBAL).columns());
        columns.addUserStateListener(() -> uiState.write(widthsKey(), DatabaseViewState.GLOBAL,
                new DatabaseViewState.Widths(columns.exportCustomWidths())));
        this.selectionChanged = selectionChanged;
        session = ClientWorldObjectRepository.INSTANCE.newSession();
        snapshot = session.snapshot(); session.addChangeListener(changed);
        UiFixedSplitLayout body = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.HORIZONTAL,
                UiFixedSplitLayout.Edge.FIRST, 216, 100, 240);
        body.restorePreferredExtentDp(uiState.read(DatabaseViewState.WORLD_SPLIT, DatabaseViewState.GLOBAL).extentDp());
        body.addUserStateListener(() -> uiState.write(DatabaseViewState.WORLD_SPLIT, DatabaseViewState.GLOBAL,
                new DatabaseViewState.Split(body.preferredExtentDp())));
        navigation = new LinearLayout(context); navigation.setOrientation(LinearLayout.VERTICAL);
        navigation.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(10));
        ShapeDrawable navigationBackground = new ShapeDrawable(); navigationBackground.setColor(UiThemes.EDITOR.surface().root());
        navigation.setBackground(navigationBackground);
        TextView categoryTitle = UIUtils.createLockedTextView(context, tr("object_types"), 12, UiThemes.EDITOR.text().muted());
        categoryTitle.setGravity(Gravity.CENTER_VERTICAL);
        categoryTitle.setPadding(UIUtils.dp2pxInt(8),0,0,0);
        navigation.addView(categoryTitle, new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(32)));
        categoryRows = new LinearLayout(context); categoryRows.setOrientation(LinearLayout.VERTICAL);
        UiScrollView navScroll = new UiScrollView(context); navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.addView(categoryRows, new LayoutParams(-1,-2));
        navigation.addView(navScroll, new LinearLayout.LayoutParams(-1,0,1));
        navigationDivider = body.divider();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        ShapeDrawable columnBackground = new ShapeDrawable(); columnBackground.setColor(UiThemes.EDITOR.surface().canvas());
        column.setBackground(columnBackground);
        if (mode == Mode.WORLD) {
            body.setPanes(navigation, column); addView(body, new LayoutParams(-1, -1));
        } else addView(column, new LayoutParams(-1, -1));
        filterPopover = new UiColumnFilterPopover(context, this);
        if (mode == Mode.ENTITIES) {
            LinearLayout toolbar = new LinearLayout(context); toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setPadding(UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(10), UIUtils.dp2pxInt(6));
            entitySearch = new UiSearchBar(context, tr("entity_search"), tr("entity_search"),
                    () -> applyColumns(columnFilters.withName(entitySearch.input().getText().toString().trim())));
            toolbar.addView(entitySearch, new LinearLayout.LayoutParams(0, -2, 1));
            column.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout resultBar=new LinearLayout(context); resultBar.setGravity(Gravity.CENTER_VERTICAL);
        resultBar.setPadding(UIUtils.dp2pxInt(14),0,UIUtils.dp2pxInt(10),0);
        resultTitle=UIUtils.createLockedTextView(context,tr("all_objects"),12,UiThemes.EDITOR.text().normal());
        resultTitle.setGravity(Gravity.CENTER_VERTICAL);
        UIUtils.setLockedTextSize(resultTitle,UiTableStyle.CELL_TEXT_SIZE_DP);
        resultTitle.setSingleLine(true);resultTitle.setEllipsize(TextUtils.TruncateAt.END);
        resultBar.addView(resultTitle,new LinearLayout.LayoutParams(0,-1,1));
        focusButton=button(context,tr("focus"),this::focusSelected); focusButton.setEnabled(false);
        resultBar.addView(focusButton,new LinearLayout.LayoutParams(-2,UIUtils.dp2pxInt(28)));
        column.addView(resultBar,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(UiTableStyle.RESULT_BAR_HEIGHT)));
        tableHeader=UiTableStyle.header(context, columns);
        tableHeader.addView(UiTableStyle.rowNumberCell(context, 0),new ViewGroup.LayoutParams(-1,-1));
        selectAll=new UiCheckBox(context);
        selectAll.setContentDescription(tr("select_all"));
        selectAll.setOnCheckedChangeListener((checkbox, checked)->toggleAll());
        tableHeader.addView(UiTableStyle.selectionCell(context, selectAll),new ViewGroup.LayoutParams(-1,-1));
        for (int index = 2; index < columns.size(); index++) {
            String key = columns.column(index).id();
            String caption = key.equals("data") ? "data.title" : key.equals("updated") ? "updated"
                    : mode == Mode.ENTITIES && key.equals("value") ? "entity_preview" : null;
            if (key.equals("factions"))
                tableHeader.addView(UiTableStyle.cell(context, FactionUi.tr("factions"), UiThemes.EDITOR.text().normal()), new ViewGroup.LayoutParams(-1, -1));
            else if (caption != null)
                tableHeader.addView(UiTableStyle.cell(context, tr(caption), UiThemes.EDITOR.text().normal()), new ViewGroup.LayoutParams(-1, -1));
            else addHeader(key);
        }
        table=new LinearLayout(context); table.setOrientation(LinearLayout.VERTICAL);
        table.addView(tableHeader,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(UiTableStyle.HEADER_HEIGHT)));
        scroll=new UiScrollView(context); scroll.setFillViewport(true);
        // Reserve the scrollbar outside the rows without changing their column widths.
        scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        rows=new LinearLayout(context); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows,new LayoutParams(-1,-2));
        table.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        UiTableViewport tableViewport=new UiTableViewport(context, columns, table);
        column.addView(tableViewport,new LinearLayout.LayoutParams(-1,0,1));
        status=UiTableStyle.statusBar(context,tr("query_hint"),UiThemes.EDITOR);
        column.addView(status,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(UiTableStyle.STATUS_BAR_HEIGHT)));
        selectAll.setEnabled(false);
        restoreState();
        rebuildNavigation(); updateFilters();
        UiStateScopes.addChangeListener(scopeChanged);
    }
    private void rebuildNavigation() {
        categoryRows.removeAllViews();categoryButtons.clear();
        addCategory("",tr("all_objects"),
                Math.max(0, navigationCounts.total() - navigationCounts.categories().getOrDefault("entity", 0L)),()->selectCategory(null, ""));
        for(var value:WorldObjectCategories.all()) if (!value.id().equals("entity")) addCategory(value.id(),Component.translatable(value.labelKey()).getString(),
                navigationCounts.categories().getOrDefault(value.id(), 0L),()->selectCategory(value, ""));
        Set<String> known=new HashSet<>();WorldObjectCategories.all().forEach(c->known.addAll(c.providers()));
        java.util.stream.Stream.concat(typeCatalog.stream().map(WorldObjectType::provider).filter(p -> !known.contains(p)),
                navigationCounts.otherProviders().keySet().stream())
                .distinct().forEach(id->addCategory("provider:"+id,providerLabel(id),navigationCounts.otherProviders().getOrDefault(id, 0L),
                        ()->selectCategory(null, id)));
        updateNavigationSelection();
    }
    private void selectCategory(WorldObjectCategories.Category nextCategory, String nextProvider) {
        if (!shown || disposed || !Objects.equals(stateScope, UiStateScopes.current())) return;
        final WorldObjectQuery nextQuery;
        try { nextQuery = buildQuery(columnFilters, range, registryTypeFilter, nextCategory, nextProvider); }
        catch (IllegalArgumentException invalid) { setStatus(tr("invalid_query")); return; }
        category = nextCategory; extraProvider = provider = nextProvider;
        saveState(); submitQuery(nextQuery);
    }
    private void addCategory(String id,String name,long count,Runnable action) {
        UiNavigationRow row=new UiNavigationRow(getContext(),name,count);
        row.button().setOnClickListener(view->action.run());
        categoryButtons.put(id,row.button());
        categoryRows.addView(row,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(33)));
    }
    private void updateNavigationSelection() {
        String id=category!=null?category.id():extraProvider.isEmpty()?"":"provider:"+extraProvider;
        categoryButtons.forEach((key,button)->button.setPalette(UiTableStyle.rowPalette(key.equals(id),UiThemes.EDITOR.surface().root())));
        resultTitle.setText((mode == Mode.ENTITIES ? tr("entities") : category==null?tr("all_objects"):Component.translatable(category.labelKey()).getString())
                +"  ·  "+sortedEntries().size());
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        navigation.setVisibility(mode == Mode.ENTITIES || MeasureSpec.getSize(widthSpec)<UIUtils.dp2pxInt(450)?GONE:VISIBLE);
        navigationDivider.setVisibility(navigation.getVisibility());
        super.onMeasure(widthSpec,heightSpec);
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        dismissMenu();
    }

    private void addHeader(String key) {
        String title = key.equals("value") ? Component.translatable("geometry_node.data_library.column_value").getString()
                : tr(key.equals("registered") ? "registration" : key.equals("loaded") ? "load_status" : key);
        UiTableFilterHeader header = new UiTableFilterHeader(getContext(), title, key.equals("value") ? null : () -> {
            if (!shown || disposed) return;
            sortAscending = !sort.equals(key) || !sortAscending; sort = key; saveState(); resetDoubleClick();
            if (!snapshot.loading()) rebuildRows();
        }, () -> openColumnFilter(key));
        filterHeaders.put(key, header);
        tableHeader.addView(header, new ViewGroup.LayoutParams(-1, -1));
    }

    private void applyColumns(ColumnFilters next) { applyColumns(next, range); }
    private void applyColumns(ColumnFilters next, WorldObjectQuery.Range nextRange) {
        applyColumns(next, nextRange, registryTypeFilter);
    }
    private void applyColumns(ColumnFilters next, WorldObjectQuery.Range nextRange, String nextRegistryType) {
        applyColumns(next, nextRange, nextRegistryType, graphFilter);
    }
    private void applyColumns(ColumnFilters next, WorldObjectQuery.Range nextRange, String nextRegistryType,
                              WorldObjectGraphFilter nextGraphFilter) {
        if (!shown || disposed || !Objects.equals(stateScope, UiStateScopes.current())) return;
        // Let the popover report validation errors while keeping every committed field intact.
        WorldObjectQuery nextQuery = buildQuery(next, nextRange, nextRegistryType, nextGraphFilter, category, provider);
        cancelMount();
        columnFilters = next;
        registryTypeFilter = mode == Mode.ENTITIES ? nextRegistryType : "";
        graphFilter = nextGraphFilter;
        range = nextRange;
        selected.clear(); anchorKey = null; resetDoubleClick();
        saveState(); updateFilters(); updateSelection(); submitQuery(nextQuery);
    }
    private void applyGraphs(String type, String name) {
        applyColumns(columnFilters, range, registryTypeFilter, new WorldObjectGraphFilter(type, name));
    }
    private LinkedHashMap<String, String> graphTypeOptions() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (var type : GraphTypeRegistry.INSTANCE.all())
            options.put(type.id(), Component.translatable(type.translationKey()).getString());
        return options;
    }
    private void applyDimensions(ColumnFilters.Choice choice, boolean loaded) {
        ColumnFilters nextColumns = columnFilters.withDimensions(choice);
        WorldObjectQuery.Range nextRange = loaded ? WorldObjectQuery.Range.LOADED : WorldObjectQuery.Range.GLOBAL;
        applyColumns(nextColumns, nextRange);
    }
    private LinkedHashMap<String, String> typeOptions() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (var c : WorldObjectCategories.all()) if (!c.id().equals("entity"))
            options.put("category:" + c.id(), Component.translatable(c.labelKey()).getString());
        for (var t : typeCatalog) if (WorldObjectCategories.all().stream().noneMatch(c -> c.accepts().test(t.provider(), t.id())))
            options.put(WorldObjectEntry.key(t.provider(), "", t.id()), typeOption(t.provider(), t.id()) + " · " + providerLabel(t.provider()));
        for (var e : snapshot.entries()) if (WorldObjectCategories.forEntry(e) == null)
            options.putIfAbsent(WorldObjectQuery.filterType(e), typeOption(e.provider(), e.type()) + " · " + providerLabel(e.provider()));
        return options;
    }
    private static String migratedType(String provider, String type) {
        return WorldObjectCategories.all().stream().filter(c -> c.accepts().test(provider, type))
                .map(c -> "category:" + c.id()).findFirst().orElseGet(() -> WorldObjectEntry.key(provider, "", type));
    }
    private void openColumnFilter(String key) {
        if (!shown || disposed || !Objects.equals(stateScope, UiStateScopes.current())) return;
        if (faction != null && key.equals("registered")) return;
        View anchor = filterHeaders.get(key).filterButton();
        switch (key) {
            case "registered", "loaded" -> {
                LinkedHashMap<String, String> choices = new LinkedHashMap<>();
                boolean registration = key.equals("registered");
                choices.put("yes", tr(registration ? "registered" : "loaded"));
                choices.put("no", tr(registration ? "unregistered" : "unloaded"));
                String selected = registration ? entityFilter.registration() == Registration.ANY ? "" : entityFilter.registration() == Registration.REGISTERED ? "yes" : "no"
                        : entityFilter.load() == Load.ANY ? "" : entityFilter.load() == Load.LOADED ? "yes" : "no";
                filterPopover.showChoices(anchor, tr(registration ? "registration" : "load_status"), choices,
                        new ColumnFilters.Choice(!selected.isEmpty(), selected.isEmpty() ? Set.of() : Set.of(selected)), choice -> {
                            boolean yes = choice.matches("yes"), no = choice.matches("no");
                            entityFilter = new WorldObjectEntityFilter(Scope.ENTITIES,
                                    registration ? entityFilter.load() : yes == no ? Load.ANY : yes ? Load.LOADED : Load.UNLOADED,
                                    faction != null ? Registration.REGISTERED : !registration ? entityFilter.registration() : yes == no ? Registration.ANY : yes ? Registration.REGISTERED : Registration.UNREGISTERED, faction);
                            queryNow(); updateFilters();
                        });
            }
            case "name" -> filterPopover.showText(anchor, tr("name"), columnFilters.name(), v -> applyColumns(columnFilters.withName(v)));
            case "type" -> {
                filterPopover.showChoices(anchor, tr("type"), typeOptions(), columnFilters.types(), v -> applyColumns(columnFilters.withTypes(v)));
            }
            case "registry_type" -> filterPopover.showText(anchor, tr("registry_type"), registryTypeFilter,
                    v -> applyColumns(columnFilters, range, v));
            case "value" -> filterPopover.showNumber(anchor, Component.translatable("geometry_node.data_library.column_value").getString(), columnFilters.value(), v -> applyColumns(columnFilters.withValue(v)));
            case "dimension" -> {
                LinkedHashMap<String, String> options = new LinkedHashMap<>();
                dimensionCatalog.forEach(id -> options.put(id, id));
                if (mode == Mode.ENTITIES) filterPopover.showChoices(anchor, tr("dimension"), options, columnFilters.dimensions(),
                        choice -> applyColumns(columnFilters.withDimensions(choice)));
                else filterPopover.showChoices(anchor, tr("dimension"), options, columnFilters.dimensions(),
                        tr("range.loaded"), range == WorldObjectQuery.Range.LOADED,
                        this::applyDimensions);
            }
            case "position" -> filterPopover.showPosition(anchor, columnFilters.x(), columnFilters.y(), columnFilters.z(), v -> applyColumns(columnFilters.withX(v.get(0)).withY(v.get(1)).withZ(v.get(2))));
            case "lifetime" -> {
                LinkedHashMap<String, String> options = new LinkedHashMap<>();
                for (String id : List.of("persistent", "binding", "process", "runtime", "loaded")) options.put(id, lifetimeLabel(id));
                snapshot.entries().forEach(e -> options.putIfAbsent(e.lifetime(), lifetimeLabel(e.lifetime())));
                filterPopover.showChoices(anchor, tr("lifetime"), options, columnFilters.lifetimes(), v -> applyColumns(columnFilters.withLifetimes(v)));
            }
            case "tags" -> filterPopover.showTags(anchor, columnFilters.includedTags(), columnFilters.excludedTags(), (include, exclude) -> applyColumns(columnFilters.withTags(include, exclude)));
            case "graphs" -> filterPopover.showTypeAndName(anchor, tr("graphs"), graphTypeOptions(),
                    graphFilter.type(), graphFilter.name(), this::applyGraphs);
        }
    }
    private void queryNow() {
        if (restoringState || !shown || disposed || !Objects.equals(stateScope, UiStateScopes.current())) return;
        saveState();
        query();
    }
    private void query() {
        if (!shown || disposed || faction != null && !Objects.equals(factionOwnerScope, UiStateScopes.current())) return;
        cancelMount();
        if (!Objects.equals(stateScope, UiStateScopes.current())) switchScope();
        try { submitQuery(buildQuery(columnFilters, range)); }
        catch (IllegalArgumentException invalid) { setStatus(tr("invalid_query")); }
    }
    private WorldObjectQuery buildQuery(ColumnFilters filters, WorldObjectQuery.Range nextRange) {
        return buildQuery(filters, nextRange, registryTypeFilter);
    }
    private WorldObjectQuery buildQuery(ColumnFilters filters, WorldObjectQuery.Range nextRange, String nextRegistryType) {
        return buildQuery(filters, nextRange, nextRegistryType, category, provider);
    }
    private WorldObjectQuery buildQuery(ColumnFilters filters, WorldObjectQuery.Range nextRange, String nextRegistryType,
                                        WorldObjectCategories.Category nextCategory, String nextProvider) {
        return buildQuery(filters, nextRange, nextRegistryType, graphFilter, nextCategory, nextProvider);
    }
    private WorldObjectQuery buildQuery(ColumnFilters filters, WorldObjectQuery.Range nextRange, String nextRegistryType,
                                        WorldObjectGraphFilter nextGraphFilter,
                                        WorldObjectCategories.Category nextCategory, String nextProvider) {
        ColumnFilters effectiveFilters = filters;
        if (nextCategory != null) {
            String id = "category:" + nextCategory.id();
            // Compile sidebar and column choices into one AND predicate before the server result limit.
            Set<String> selectedTypes = filters.types().matches(id) ? Set.of(id) : Set.of();
            effectiveFilters = filters.withTypes(new ColumnFilters.Choice(true, selectedTypes));
        }
        return new WorldObjectQuery(nextRange, "", one(nextProvider), Set.of(), "",
                Set.of(), Set.of(), Set.of(),
                128, WorldObjectQuery.MAX_RESULTS, effectiveFilters, Set.of(), compileNameFallbacks(filters.name()),
                mode == Mode.ENTITIES ? nextRegistryType : "", nextGraphFilter).withEntityFilter(effectiveEntityFilter());
    }
    private Set<String> compileNameFallbacks(String keyword) {
        if (keyword.isEmpty()) return Set.of();
        String needle = keyword.toLowerCase(Locale.ROOT);
        Set<String> matches = new LinkedHashSet<>();
        for (var type : typeCatalog)
            if (typeLabel(type.provider(), type.id()).toLowerCase(Locale.ROOT).contains(needle))
                matches.add(WorldObjectEntry.key(type.provider(), "", type.id()));
        for (var entry : snapshot.entries())
            if (typeLabel(entry.provider(), entry.type()).toLowerCase(Locale.ROOT).contains(needle))
                matches.add(WorldObjectQuery.rawType(entry));
        // Native registry coverage is complete even before the first server catalog arrives.
        for (String id : nativeTypeLabels.ids()) {
            if (typeLabel("native", id).toLowerCase(Locale.ROOT).contains(needle))
                matches.add(WorldObjectEntry.key("native", "", id));
        }
        return Set.copyOf(matches);
    }
    private boolean needsFallbackQuery() {
        // Only successful results can discover additional labels. Errors must not silently retry;
        // partial catalogs may omit previously known labels and must not cause alternating queries.
        if (snapshot.loading() || !Set.of("", "partial_results", "partial_metadata").contains(snapshot.message())) return false;
        return !snapshot.query().columnNameFallbackTypes().containsAll(compileNameFallbacks(columnFilters.name()));
    }
    private void submitQuery(WorldObjectQuery query) {
        cancelMount();
        removeCallbacks(fallbackQueryTask);
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
        if (snapshot.equals(renderedSnapshot)) {
            if (!snapshot.loading() && mountedEntries == null && !rowBatch.isRunning()) rebuildRows();
            else updateSelection();
            return;
        }
        cancelMount();
        renderedSnapshot=snapshot;
        if (!snapshot.loading()) {
            completedQuery = snapshot.query();
            typeCatalog = snapshot.types();
            dimensionCatalog = snapshot.dimensionIds();
            if (snapshot.page().counts() != null) navigationCounts = snapshot.page().counts();
            else if (snapshot.message().equals("disconnected") || snapshot.message().equals("permission_denied"))
                navigationCounts = WorldObjectPage.Counts.empty();
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
        removeCallbacks(fallbackQueryTask);
        if (!snapshot.loading() && needsFallbackQuery()) post(fallbackQueryTask);
    }
    private void refreshStatus() {
        String text=(snapshot.loading() || rowBatch.isRunning())?tr("loading"):tr("result_count",sortedEntries().size(),selected.size());
        if(!snapshot.message().isEmpty())text+=" · "+tr("message."+snapshot.message());
        setStatus(text);
    }
    private void setStatus(String text) { status.setText(text); }
    private String typeLabel(String providerId, String id) {
        if (indexedTypeCatalog != typeCatalog) {
            typeLookup.clear();
            for (var value : typeCatalog)
                typeLookup.putIfAbsent(WorldObjectEntry.key(value.provider(), "", value.id()), value);
            indexedTypeCatalog = typeCatalog;
        }
        WorldObjectType value = typeLookup.get(WorldObjectEntry.key(providerId, "", id));
        if (value != null) return value.labelKey().isEmpty() ? id : Component.translatable(value.labelKey()).getString();
        if (providerId.equals("native")) return nativeTypeLabels.label(id);
        return id;
    }
    private WorldObjectEntityFilter effectiveEntityFilter() {
        return faction == null ? entityFilter : new WorldObjectEntityFilter(Scope.ENTITIES,
                entityFilter.load(), Registration.REGISTERED, faction);
    }
    private String tableStateScope() {
        return faction == null ? stateScope : stateScope + ":faction-members";
    }
    private boolean fixedFactionScopeExpired() {
        return faction != null && !Objects.equals(factionOwnerScope, UiStateScopes.current());
    }
    private void updateFilters() {
        if (faction != null) filterHeaders.get("registered").filterButton().setEnabled(false);
        if (entitySearch != null && !entitySearch.input().getText().toString().equals(columnFilters.name()))
            entitySearch.input().setText(columnFilters.name());
        filterHeaders.forEach((key, header) -> header.setFilterActive(switch (key) {
            case "registered" -> entityFilter.registration() != Registration.ANY;
            case "loaded" -> entityFilter.load() != Load.ANY;
            case "name" -> !columnFilters.name().isEmpty();
            case "type" -> columnFilters.types().active();
            case "registry_type" -> !registryTypeFilter.isEmpty();
            case "value" -> columnFilters.value().active();
            case "dimension" -> columnFilters.dimensions().active() || range == WorldObjectQuery.Range.LOADED;
            case "position" -> columnFilters.x().active() || columnFilters.y().active() || columnFilters.z().active();
            case "lifetime" -> columnFilters.lifetimes().active();
            case "tags" -> !columnFilters.includedTags().isEmpty() || !columnFilters.excludedTags().isEmpty();
            case "graphs" -> graphFilter.active();
            default -> false;
        }));
    }
    private String providerLabel(String id) {
        String key = "geometry_node.database.provider." + id;
        String text = Component.translatable(key).getString();
        return text.equals(key) ? id : text;
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
    private void dismissMenu() { if (filterPopover != null) filterPopover.dismiss(); }
    private List<WorldObjectEntry> sortedEntries() {
        Comparator<WorldObjectEntry> comparator = switch (sort) {
            case "registered" -> Comparator.comparing(e -> e.entityState() != null && e.entityState().registered());
            case "loaded" -> Comparator.comparing(WorldObjectPresentation::isLoaded);
            case "type" -> Comparator.comparing(this::displayType, String.CASE_INSENSITIVE_ORDER);
            case "registry_type" -> Comparator.comparing(WorldObjectEntries::registryType, String.CASE_INSENSITIVE_ORDER);
            case "dimension" -> Comparator.comparing(WorldObjectEntry::dimension);
            case "lifetime" -> Comparator.comparing(WorldObjectEntry::lifetime);
            case "tags" -> Comparator.comparing(entry -> String.join(",",entry.tags()));
            case "graphs" -> Comparator.comparing(entry -> String.join(",", graphNames(entry)), String.CASE_INSENSITIVE_ORDER);
            case "position" -> Comparator.comparing(WorldObjectEntry::dimension)
                    .thenComparingDouble(entry -> entry.position().x)
                    .thenComparingDouble(entry -> entry.position().y)
                    .thenComparingDouble(entry -> entry.position().z);
            default -> Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER);
        };
        if (!sortAscending) comparator = comparator.reversed();
        return snapshot.entries().stream().filter(e -> mode != Mode.ENTITIES || effectiveEntityFilter().matches(e))
                .filter(e -> category == null || category.matches(e))
                .filter(e -> provider.isEmpty() || provider.equals(e.provider()))
                .filter(e -> mode != Mode.ENTITIES || WorldObjectQuery.matchesRegistryType(e, registryTypeFilter))
                .filter(e -> graphFilter.matches(e.graphs()))
                .filter(e -> columnFilters.matches(displayName(e), WorldObjectQuery.filterType(e), WorldObjectQuery.numericValue(e),
                        e.dimension(), e.position().x, e.position().y, e.position().z, e.lifetime(), e.tags())).sorted(comparator.thenComparing(WorldObjectEntry::key)).toList();
    }
    private String displayType(WorldObjectEntry entry) {
        var presentation = WorldObjectCategories.forEntry(entry);
        return presentation == null ? typeLabel(entry.provider(), entry.type()) : Component.translatable(presentation.labelKey()).getString();
    }
    private String displayName(WorldObjectEntry entry) { return entry.name().isEmpty() ? typeLabel(entry.provider(), entry.type()) : entry.name(); }
    private ScrollAnchor captureScrollAnchor(String preferredKey, boolean reveal) {
        int y = scroll.getScrollY();
        int height = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        UiActionButton preferred = rowViews.get(preferredKey);
        if (preferred != null) {
            View host = (View) preferred.getParent();
            int offset = rows.getTop() + host.getTop() - y;
            if (reveal || offset < height && offset + host.getHeight() > 0)
                return new ScrollAnchor(preferredKey, reveal
                        ? Math.max(0, Math.min(offset, Math.max(0, height - host.getHeight()))) : offset, y);
        }
        for (int i = 0; i < rows.getChildCount(); i++) {
            View host = rows.getChildAt(i);
            if (rows.getTop() + host.getBottom() <= y) continue;
            for (var row : rowViews.entrySet())
                if (row.getValue().getParent() == host)
                    return new ScrollAnchor(row.getKey(), rows.getTop() + host.getTop() - y, y);
        }
        return new ScrollAnchor(null, 0, y);
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (pendingScrollAnchor == null || rowBatch.isRunning() || snapshot.loading() || !shown || disposed) return;
        ScrollAnchor anchor = pendingScrollAnchor;
        pendingScrollAnchor = null;
        UiActionButton row = rowViews.get(anchor.key());
        int y = row == null ? anchor.fallbackY()
                : rows.getTop() + ((View) row.getParent()).getTop() - anchor.offset();
        scroll.scrollTo(scroll.getScrollX(), Math.max(0, y));
    }
    private void rebuildRows() {
        List<WorldObjectEntry> entries = sortedEntries();
        if (entries.equals(mountedEntries) && typeCatalog.equals(mountedTypes)) {
            updateSelection();
            if (pendingScrollAnchor != null) requestLayout();
            return;
        }
        if (pendingScrollAnchor == null)
            pendingScrollAnchor = captureScrollAnchor(selected.contains(anchorKey) ? anchorKey : null, false);
        cancelMount();
        mountedEntries = null; mountedTypes = null;
        List<WorldObjectType> mountingTypes = List.copyOf(typeCatalog);
        Runnable complete = () -> {
            // Commit the order together: moving each row to the end in separate frame batches
            // exposes intermediate permutations to layout, focus scrolling and user input.
            boolean reorder = false;
            for (int i = 0; !reorder && i < entries.size(); i++)
                reorder = rowBindings.get(entries.get(i).key()).button().getParent() != rows.getChildAt(i);
            if (reorder) for (var entry : entries)
                rows.bringChildToFront((View) rowBindings.get(entry.key()).button().getParent());
            mountedEntries = entries;
            mountedTypes = mountingTypes;
            updateSelection();
            requestLayout(); // Restore only after the final row geometry has been laid out.
        };
        // A changed DTO is not a changed view identity. Retire only keys absent from the new
        // result, including when a previous frame batch was interrupted by a newer query.
        Set<String> liveKeys = new HashSet<>();
        entries.forEach(entry -> liveKeys.add(entry.key()));
        for (String key : List.copyOf(rowBindings.keySet())) {
            if (liveKeys.contains(key)) continue;
            RowBinding removed = rowBindings.remove(key);
            disposeRegistration(removed);
            rowViews.remove(key);
            rows.removeView((View) removed.button().getParent());
        }
        if (rowBindings.isEmpty()) {
            rows.removeAllViews(); // Only the empty-state label can remain here.
            if (entries.isEmpty()) {
                TextView empty = UIUtils.createLockedTextView(getContext(), tr("empty"), 12, UiThemes.EDITOR.text().muted());
                empty.setGravity(Gravity.CENTER); rows.addView(empty, new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(96)));
            }
        }
        Iterator<WorldObjectEntry> iterator = entries.iterator();
        rowBatch.start(new Iterator<Runnable>() {
            private int index;
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public Runnable next() {
                WorldObjectEntry entry = iterator.next();
                int rowIndex = index++;
                return () -> {
                    if (rowBindings.containsKey(entry.key())) updateRow(entry, rowIndex);
                    else mountRow(entry, rowIndex);
                };
            }
        }, complete);
        updateSelection();
    }
    private void updateRow(WorldObjectEntry entry, int index) {
        String key = entry.key();
        RowBinding binding = rowBindings.remove(key);
        UiTableRow cells = binding.cells();
        updateText(cells, 0, Integer.toString(index + 1));
        if (mode == Mode.WORLD) updateText(cells, columnIndex("name"), displayName(entry));
        if (mode == Mode.ENTITIES) bindRegistration((EntityRegistrationView)cells.getChildAt(columnIndex(typeColumnId())), entry);
        else updateText(cells, columnIndex(typeColumnId()), displayType(entry));
        if (mode == Mode.ENTITIES) updateText(cells, columnIndex("registry_type"), WorldObjectEntries.registryType(entry));
        binding.checkbox().setContentDescription(displayName(entry));
        int valueColumn = columnIndex("value");
        View value = cells.getChildAt(valueColumn);
        if (hasEntityPreview(entry)) {
            if (!(value instanceof EntityPreviewCell)) {
                cells.removeViewAt(valueColumn);
                value = createValueCell(entry, cells);
                cells.addView(value, valueColumn, new ViewGroup.LayoutParams(-1, -1));
            } else bindPreview((EntityPreviewCell) value, entry);
        } else if (value instanceof EntityPreviewCell) {
            cells.removeViewAt(valueColumn);
            cells.addView(createValueCell(entry, cells), valueColumn, new ViewGroup.LayoutParams(-1, -1));
        }
        updateText(cells, columnIndex("dimension"), entry.dimension());
        updateText(cells, columnIndex("position"), position(entry));
        updateText(cells, columnIndex(lifetimeColumnId()), mode == Mode.ENTITIES ? tr(isLoaded(entry) ? "loaded" : "unloaded") : lifetimeLabel(entry.lifetime()));
        if (mode == Mode.ENTITIES) updateText(cells, columnIndex("updated"), capturedAt(entry));
        ((UiTagStrip) cells.getChildAt(columnIndex("tags"))).setTags(entry.tags());
        ((UiTagStrip) cells.getChildAt(columnIndex("graphs"))).setTags(graphNames(entry));
        if (mode == Mode.ENTITIES)
            ((UiTagStrip) cells.getChildAt(columnIndex("factions"))).setTags(factionNames(entry));
        WorldObjectEntry previous = binding.entry();
        if (dataCount(previous) != dataCount(entry) || !previous.references().equals(entry.references())
                || isNativeEntity(previous) != isNativeEntity(entry) || isLoaded(previous) != isLoaded(entry))
            bindDataSummary((UiDataSummaryCell) cells.getChildAt(columnIndex("data")), entry);
        int baseColor = UiTableStyle.rowColor(index % 2 != 0);
        binding.button().setPalette(UiTableStyle.rowPalette(selected.contains(key), baseColor));
        rowBindings.put(key, new RowBinding(binding.button(), binding.checkbox(), binding.stripe(), cells, baseColor, entry));
        rowViews.remove(key);
        rowViews.put(key, binding.button());
    }
    private static void updateText(UiTableRow row, int column, String value) {
        TextView cell = (TextView) row.getChildAt(column);
        if (!value.contentEquals(cell.getText())) cell.setText(value);
    }
    private List<String> factionNames(WorldObjectEntry entry) {
        var repository = ClientFactionRepository.INSTANCE;
        return WorldObjectPresentation.factionNames(entry, repository.snapshot(),
                FactionUi.tr(repository.loaded() ? "deleted" : "unavailable"));
    }
    /** Catalog renames update only the existing chips, preserving rows, focus and entity previews. */
    private void refreshFactionCells() {
        if (!shown || disposed || !Objects.equals(stateScope, UiStateScopes.current())) return;
        for (RowBinding binding : rowBindings.values())
            ((UiTagStrip) binding.cells().getChildAt(columnIndex("factions"))).setTags(factionNames(binding.entry()));
    }
    private View createValueCell(WorldObjectEntry entry, UiTableRow row) {
        if (!hasEntityPreview(entry)) return UiTableStyle.cell(getContext(), "", UiThemes.EDITOR.text().normal());
        EntityPreviewCell preview = new EntityPreviewCell(getContext());
        preview.setVisibilityRow(row);
        bindPreview(preview, entry);
        return preview;
    }
    private static void bindPreview(EntityPreviewCell preview, WorldObjectEntry entry) {
        UUID entityId = entry.entityState() == null ? null : entry.entityState().entityUuid();
        try { if (entityId == null) entityId = UUID.fromString(entry.references().getOrDefault("entityUuid", "")); }
        catch (IllegalArgumentException ignored) { }
        // The shared preview setter is idempotent for an unchanged UUID and dimension.
        preview.setReference(entityId, entry.dimension());
    }
    private static void bindRegistration(EntityRegistrationView view, WorldObjectEntry entry) {
        var state = entry.entityState();
        view.bindConfirmed(state != null && state.registered(), state == null ? 0 : state.revision());
    }
    private void disposeRegistration(RowBinding binding) {
        if (binding.cells().getChildAt(columnIndex(typeColumnId())) instanceof EntityRegistrationView control) control.dispose();
    }
    private void bindDataSummary(UiDataSummaryCell cell, WorldObjectEntry entry) {
        cell.setSummary(dataCount(entry), entry.references().getOrDefault(WorldObjectDataEdits.SUMMARY_REFERENCE, ""),
                (!isNativeEntity(entry) || !isLoaded(entry)) && entry.references().containsKey(WorldObjectDataEdits.TARGET_REFERENCE)
                        ? () -> {
                            RowBinding live = rowBindings.get(entry.key());
                            if (live != null && live.cells().getChildAt(columnIndex("data")) == cell) openDataEditor(live.entry());
                        } : null);
    }
    private void cancelMount() {
        if (rowBatch.isRunning()) { mountedEntries = null; mountedTypes = null; }
        rowBatch.cancel();
    }
    private void mountRow(WorldObjectEntry entry, int index) {
        UiActionButton row = UiTableStyle.rowButton(getContext());
        row.setPadding(0, 0, 0, 0);
        int baseColor=UiTableStyle.rowColor(index%2!=0);
        UiTableRow line = new UiTableRow(getContext(), columns);
        line.setMinimumHeight(UIUtils.dp2pxInt(UiTableStyle.ROW_HEIGHT));
        line.addView(UiTableStyle.rowNumberCell(getContext(), index + 1),new ViewGroup.LayoutParams(-1,-1));
        UiCheckBox checkbox=new UiCheckBox(getContext());
        checkbox.setContentDescription(displayName(entry));
        checkbox.setOnCheckedChangeListener((view,checked)-> {
            RowBinding live = rowBindings.get(entry.key());
            if (live != null && live.checkbox() == checkbox) toggleCheckbox(live.entry(), checked);
        });
        line.addView(UiTableStyle.selectionCell(getContext(), checkbox),new ViewGroup.LayoutParams(-1,-1));
        // Build cells from the same schema as headers so each mode has a single column order.
        for (int column = 2; column < columns.size(); column++) {
            String id = columns.column(column).id();
            switch (id) {
                case "registered" -> {
                    EntityRegistrationView registration = new EntityRegistrationView(getContext(), UiThemes.EDITOR, entityId(entry), entry.dimension(), true,
                            () -> canSelect() && rowBindings.containsKey(entry.key()) && isAlive(rowBindings.get(entry.key()).entry()), this::queryNow);
                    bindRegistration(registration, entry); line.addView(registration, new ViewGroup.LayoutParams(-1, -1));
                }
                case "value" -> line.addView(createValueCell(entry, line), new ViewGroup.LayoutParams(-1, -1));
                case "tags", "graphs", "factions" -> {
                    UiTagStrip tags = new UiTagStrip(getContext());
                    tags.setTags(switch (id) {
                        case "tags" -> entry.tags();
                        case "graphs" -> graphNames(entry);
                        default -> factionNames(entry);
                    });
                    line.addView(tags, new ViewGroup.LayoutParams(-1, -1));
                }
                case "data" -> {
                    UiDataSummaryCell data = new UiDataSummaryCell(getContext()); bindDataSummary(data, entry);
                    line.addView(data, new ViewGroup.LayoutParams(-1, -1));
                }
                default -> cell(line, switch (id) {
                    case "name" -> displayName(entry);
                    case "type" -> displayType(entry);
                    case "registry_type" -> WorldObjectEntries.registryType(entry);
                    case "dimension" -> entry.dimension();
                    case "position" -> position(entry);
                    case "loaded" -> tr(isLoaded(entry) ? "loaded" : "unloaded");
                    case "lifetime" -> lifetimeLabel(entry.lifetime());
                    case "updated" -> capturedAt(entry);
                    default -> throw new IllegalArgumentException("Unknown world-query column: " + id);
                });
            }
        }

        // Cells determine height; background and selection overlays follow without stretching the row.
        FrameLayout host = new UiTableRowFrame(getContext(), line, row);
        View stripe=new View(getContext());ShapeDrawable stripeBackground = new ShapeDrawable(); stripeBackground.setColor(UiThemes.EDITOR.selection().outline());
        stripe.setBackground(stripeBackground);
        host.addView(stripe,new LayoutParams(Math.max(1,UIUtils.dp2pxInt(2)),-1));
        View border=new View(getContext());ShapeDrawable borderBackground = new ShapeDrawable(); borderBackground.setColor(UiTableStyle.dividerColor(UiThemes.EDITOR));
        border.setBackground(borderBackground);
        host.addView(border,new LayoutParams(-1,Math.max(1,UIUtils.dp2pxInt(1)),Gravity.BOTTOM));
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
            RowBinding live = rowBindings.get(entry.key());
            if (live == null || live.button() != row) return;
            select(live.entry(), toggle, extend);
            row.requestFocus();
            rowClicked(entry.key(),toggle||extend,System.nanoTime());
        });
        rowViews.put(entry.key(), row);
        rowBindings.put(entry.key(),new RowBinding(row,checkbox,stripe,line,baseColor,entry));
        row.setEnabled(false);
        checkbox.setEnabled(false);
        rows.addView(host, new LinearLayout.LayoutParams(-1, -2));
    }
    private void cell(UiTableRow row, String text) {
        TextView cell = UiTableStyle.cell(getContext(), text, UiThemes.EDITOR.text().normal());
        row.addView(cell, new ViewGroup.LayoutParams(-1, -1));
    }
    private boolean canSelect() { return !rowBatch.isRunning() && shown && !disposed && !awaitingScopeResponse && Objects.equals(stateScope, UiStateScopes.current()) && !snapshot.loading(); }
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
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!shown || disposed) return false;
        if (event.isCanceled() || findFocus() instanceof EditText) return super.dispatchKeyEvent(event);
        KeyBinding binding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_SELECT_ALL));
        if (UiInputBindings.matches(binding, event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && canSelect()) {
                resetDoubleClick();
                selected.addAll(sortedEntries().stream().map(WorldObjectEntry::key).toList());
                updateSelection();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
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
            binding.button().setPalette(UiTableStyle.rowPalette(checked,binding.baseColor()));
            binding.checkbox().setChecked(checked);
            binding.stripe().setVisibility(checked?VISIBLE:INVISIBLE);
        });
        List<WorldObjectEntry> entries=sortedEntries();
        long count=entries.stream().filter(entry->selected.contains(entry.key())).count();
        selectAll.setChecked(!entries.isEmpty() && count==entries.size());
        selectAll.setIndeterminate(count>0 && count<entries.size());
        selectAll.setContentDescription(tr("select_all")+" · "+tr("selected",count));
        updateInteractionState();
        // Keep the last inspector mounted during a replacement query. Commands still resolve
        // live state and reject loading/stale targets; scope changes must clear it immediately.
        boolean completedRefresh = inspectorWaitingForQuery && !snapshot.loading();
        inspectorWaitingForQuery = retainInspector() && publishedSelection != null;
        DatabaseSelection next=presentationSelection();
        // Actions resolve live state when invoked; their newly allocated callbacks are not content changes.
        // A completed refresh must also reconnect passive DATA bindings whose read cache was reset.
        if (completedRefresh || (publishedSelection==null?next!=null:!publishedSelection.hasSamePresentation(next))) {
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
            if (binding.cells().getChildAt(columnIndex(typeColumnId())) instanceof EntityRegistrationView control) control.refreshInputState();
        });
        selectAll.setEnabled(enabled && !rowBindings.isEmpty());
        List<WorldObjectEntry> entries = selectedEntries();
        focusButton.setEnabled(enabled && !entries.isEmpty());
    }
    private List<WorldObjectEntry> selectedEntries() {
        return snapshot.entries().stream().filter(entry -> selected.contains(entry.key())).toList();
    }
    private boolean retainInspector() {
        return snapshot.loading() && !awaitingScopeResponse
                && Objects.equals(stateScope, UiStateScopes.current())
                && snapshot.query().equals(completedQuery);
    }
    /** Display-only snapshot for owners reactivating this tab; commands use selection()/canSelect(). */
    public DatabaseSelection presentationSelection() {
        return retainInspector() ? publishedSelection : selection();
    }
    public DatabaseSelection selection() {
        // Visual row mounting does not invalidate the authoritative selected objects.
        if(awaitingScopeResponse || !Objects.equals(stateScope, UiStateScopes.current()) || snapshot.loading())return null;
        List<WorldObjectEntry> entries = selectedEntries();
        if (entries.isEmpty()) return null;
        if(entries.size()>1)return new DatabaseSelection(tr("selected",entries.size()),tr(mode == Mode.ENTITIES ? "entities" : "world"),"",0,
                List.of(new DatabaseSelection.Field(tr("selected_count"),Integer.toString(entries.size()))),
                List.of(new DatabaseSelection.Action(tr("focus"),this::focusSelected)),List.of());
        var entry=entries.getFirst();
        boolean nativeEntity = isNativeEntity(entry);
        List<DatabaseSelection.Action> actions = new ArrayList<>();
        actions.add(new DatabaseSelection.Action(tr("focus"), this::focusSelected));
        if (nativeEntity && isLoaded(entry) && isAlive(entry)) actions.add(new DatabaseSelection.Action(entityEditLabel(), this::editSelected));
        var position=List.of(new DatabaseSelection.Field("X",number(entry.position().x)),
                new DatabaseSelection.Field("Y",number(entry.position().y)),new DatabaseSelection.Field("Z",number(entry.position().z)));
        var metadata=new ArrayList<>(List.of(new DatabaseSelection.Field(tr("type"), entry.provider().equals("native")
                ? WorldObjectEntries.registryType(entry) : entry.type()),
                new DatabaseSelection.Field(tr("lifetime"),lifetimeLabel(entry.lifetime())),
                new DatabaseSelection.Field(tr("id"),entry.key())));
        if (mode == Mode.ENTITIES) {
            metadata.remove(1); // Loading state has its own field; an unloaded snapshot has no live lifetime.
            metadata.add(new DatabaseSelection.Field(tr("load_status"), tr(isLoaded(entry) ? "loaded" : "unloaded")));
            metadata.add(new DatabaseSelection.Field(tr("registration"), tr(entry.entityState() != null && entry.entityState().registered() ? "registered" : "unregistered")));
            metadata.add(new DatabaseSelection.Field(tr("updated"), capturedAt(entry)));
        }
        var source=new ArrayList<DatabaseSelection.Field>();
        source.add(new DatabaseSelection.Field(tr("source"),entry.source().isEmpty()?"—":entry.source()));
        source.add(new DatabaseSelection.Field(tr("provider"),providerLabel(entry.provider())));
        entry.references().entrySet().stream()
                .filter(ref -> !ref.getKey().equals(WorldObjectTagEdits.TARGET_REFERENCE)
                        && !Set.of(WorldObjectDataEdits.TARGET_REFERENCE, WorldObjectDataEdits.COUNT_REFERENCE,
                        WorldObjectDataEdits.SUMMARY_REFERENCE).contains(ref.getKey()))
                .sorted(Map.Entry.comparingByKey()).forEach(ref->
                source.add(new DatabaseSelection.Field(ref.getKey(),ref.getValue())));
        List<DatabaseSelection.Section> sections = new ArrayList<>(List.of(
                new DatabaseSelection.Section(tr("spatial_position"),entry.dimension(),DatabaseSelection.Kind.POSITION,position),
                new DatabaseSelection.Section(tr("object_information"),"",DatabaseSelection.Kind.METADATA,metadata),
                new DatabaseSelection.Section(tr("tags"),"",DatabaseSelection.Kind.TAGS,
                        entry.tags().stream().map(tag->new DatabaseSelection.Field("",tag)).toList(),
                        !nativeEntity && session.canEditTags(entry) ? new DatabaseSelection.TagEditor(
                                entry.references().get(WorldObjectTagEdits.TARGET_REFERENCE),
                                !session.isTagUpdatePending(), session.tagUpdateMessage().isEmpty() ? ""
                                        : Component.translatable("geometry_node." + session.tagUpdateMessage()).getString(),
                                tag -> submitTagEdit(entry, tag, true),
                                tag -> submitTagEdit(entry, tag, false)) : null),
                new DatabaseSelection.Section(tr("source"),"",DatabaseSelection.Kind.SOURCE,source)));
        if (mode == Mode.ENTITIES) sections.add(new DatabaseSelection.Section(tr("graphs"), "", DatabaseSelection.Kind.REFERENCE,
                entry.graphs().stream().map(graph -> new DatabaseSelection.Field(graph.name().isEmpty() ? graph.id() : graph.name(), graph.type())).toList()));
        return new DatabaseSelection(displayName(entry),typeLabel(entry.provider(), entry.type())+" · "+tr(mode == Mode.ENTITIES ? "entities" : "world"),
                "",UiThemes.EDITOR.text().muted(),List.of(), actions, sections, entityPreview(entry),
                entry.references().containsKey(WorldObjectDataEdits.TARGET_REFERENCE)
                        ? new DatabaseSelection.DataEditor(stateScope + ":" + entry.references().get(WorldObjectDataEdits.TARGET_REFERENCE),
                        dataCount(entry), dataBinding(entry), !nativeEntity) : null);
    }
    private DataDictionaryBinding dataBinding(WorldObjectEntry entry) {
        var source = session;
        String scope = stateScope;
        return RepositoryDataDictionaryBindings.world(source, entry,
                () -> Objects.equals(scope, UiStateScopes.current()));
    }
    private void openDataEditor(WorldObjectEntry expected) {
        if (!canSelect()) return;
        var live = snapshot.entries().stream().filter(row -> row.key().equals(expected.key())
                && Objects.equals(row.references().get(WorldObjectDataEdits.TARGET_REFERENCE),
                expected.references().get(WorldObjectDataEdits.TARGET_REFERENCE))).findFirst().orElse(null);
        if (live == null || isNativeEntity(live) && isLoaded(live)) return;
        selected.clear(); selected.add(live.key()); anchorKey = live.key();
        updateSelection();
        UiDataDictionaryEditor.show(this, displayName(live) + " · " + tr(mode == Mode.ENTITIES ? "entities" : "world"), dataBinding(live));
    }
    private void submitTagEdit(WorldObjectEntry expected, String tag, boolean add) {
        if (!canSelect() || selected.size() != 1 || !selected.contains(expected.key()))
            throw new IllegalStateException("Stale tag selection");
        // Equal inspector text may hide bounds/sub-pixel motion changes. Resolve the latest DTO while
        // retaining the original owner lease and tag precondition, rather than rejecting harmless motion.
        WorldObjectEntry live = session.snapshot().entries().stream().filter(entry -> entry.key().equals(expected.key())
                && Objects.equals(entry.references().get(WorldObjectTagEdits.TARGET_REFERENCE),
                        expected.references().get(WorldObjectTagEdits.TARGET_REFERENCE))
                && entry.tags().equals(expected.tags())).findFirst().orElseThrow(() -> new IllegalStateException("Stale tag target"));
        if (!(add ? session.addTag(live, tag) : session.removeTag(live, tag)))
            throw new IllegalStateException("Tag edit rejected");
    }
    private static String entityEditLabel() {
        return Component.translatable("geometry_node.entity_editor.edit").getString();
    }
    private void editSelected() {
        if (!canSelect()) return;
        List<WorldObjectEntry> entries = selectedEntries();
        if (entries.size() == 1 && isNativeEntity(entries.getFirst()) && isLoaded(entries.getFirst()) && isAlive(entries.getFirst())) {
            String key = entries.getFirst().key(), scope = stateScope;
            WorkspaceEntityEditor.show(this, entries.getFirst(), () -> refreshEditedEntity(key, scope));
        }
    }
    private void refreshEditedEntity(String key, String scope) {
        if (!shown || disposed || !Objects.equals(scope, stateScope) || !Objects.equals(scope, UiStateScopes.current())) return;
        pendingScrollAnchor = captureScrollAnchor(key, true);
        // Query the current controls. An editor completion must never restore older filters or selection.
        queryNow();
    }
    private void focusSelected() {
        if (canSelect() && !selectedEntries().isEmpty()) setStatus(WorkspaceWorldObjectNavigation.focus(selectedEntries()));
    }
    private void saveState() {
        if (restoringState || stateScope == null) return;
        uiState.write(mode == Mode.ENTITIES ? DatabaseViewState.ENTITY_COLUMN_FILTERS : DatabaseViewState.WORLD_COLUMN_FILTERS, tableStateScope(), columnFilters);
        uiState.write(mode == Mode.ENTITIES ? DatabaseViewState.ENTITY_GRAPH_FILTER : DatabaseViewState.WORLD_GRAPH_FILTER, tableStateScope(), graphFilter);
        if (mode == Mode.ENTITIES) {
            uiState.write(DatabaseViewState.ENTITY_FILTER, tableStateScope(), entityFilter);
            // Member selections belong to retained page instances, never an unbounded scope per UUID.
            if (faction == null) uiState.write(DatabaseViewState.ENTITY_SELECTION, stateScope, new DatabaseViewState.EntitySelection(selected));
        }
        uiState.write(mode == Mode.ENTITIES ? DatabaseViewState.ENTITIES : DatabaseViewState.WORLD, tableStateScope(), new DatabaseViewState.World(
                "", "", range.name(),
                "", provider, "", "", "", category == null ? "" : category.id(),
                extraProvider, sort, sortAscending, mode == Mode.ENTITIES ? registryTypeFilter : ""));
    }
    private void switchScope() {
        cancelMount();
        pendingScrollAnchor = null;
        removeCallbacks(renderTask); removeCallbacks(fallbackQueryTask);
        dismissMenu();
        saveState(); selected.clear(); restoreState(); anchorKey = null;
        session.removeChangeListener(changed); session.close();
        session = ClientWorldObjectRepository.INSTANCE.newSession(); session.addChangeListener(changed);
        awaitingScopeResponse = true;
        snapshot = new ClientWorldObjectRepository.Snapshot(WorldObjectQuery.defaults(), WorldObjectPage.empty(0, ""), true, 0);
        renderedSnapshot = null; mountedEntries = null; mountedTypes = null;
        completedQuery = null;
        typeCatalog = List.of(); dimensionCatalog = List.of();
        navigationCounts = WorldObjectPage.Counts.empty();
        indexedTypeCatalog = null; typeLookup.clear();
        rowBindings.values().forEach(this::disposeRegistration);
        rows.removeAllViews(); rowViews.clear(); rowBindings.clear(); resetDoubleClick();
        updateFilters(); rebuildNavigation(); updateSelection();
    }
    private void restoreState() {
        stateScope = UiStateScopes.current();
        var value = uiState.read(mode == Mode.ENTITIES ? DatabaseViewState.ENTITIES : DatabaseViewState.WORLD, tableStateScope());
        restoringState = true;
        try {
            columnFilters = uiState.read(mode == Mode.ENTITIES ? DatabaseViewState.ENTITY_COLUMN_FILTERS : DatabaseViewState.WORLD_COLUMN_FILTERS, tableStateScope());
            if (mode == Mode.WORLD && columnFilters.types().selected().contains("category:entity")) {
                Set<String> types = new LinkedHashSet<>(columnFilters.types().selected()); types.remove("category:entity");
                columnFilters = columnFilters.withTypes(types.isEmpty() ? ColumnFilters.Choice.all() : new ColumnFilters.Choice(true, types));
            }
            graphFilter = uiState.read(mode == Mode.ENTITIES ? DatabaseViewState.ENTITY_GRAPH_FILTER : DatabaseViewState.WORLD_GRAPH_FILTER, tableStateScope());
            if (mode == Mode.ENTITIES) {
                entityFilter = uiState.read(DatabaseViewState.ENTITY_FILTER, tableStateScope());
                entityFilter = effectiveEntityFilter();
                if (faction == null) {
                    selected.clear(); selected.addAll(uiState.read(DatabaseViewState.ENTITY_SELECTION, stateScope).keys());
                }
            }
            registryTypeFilter = mode == Mode.ENTITIES ? value.registryType() : "";
            try { range = WorldObjectQuery.Range.valueOf(value.range()); }
            catch (IllegalArgumentException ignored) { range = WorldObjectQuery.Range.GLOBAL; }
            // Migrate former single-choice filters into the independent in-memory column state.
            if (!columnFilters.active()) {
                if (!value.dimension().isEmpty()) columnFilters = columnFilters.withDimensions(new ColumnFilters.Choice(true, Set.of(value.dimension())));
                if (!value.type().isEmpty()) columnFilters = columnFilters.withTypes(new ColumnFilters.Choice(true, Set.of(migratedType(value.typeProvider().isEmpty() ? value.provider() : value.typeProvider(), value.type()))));
                if (!value.lifetime().isEmpty()) columnFilters = columnFilters.withLifetimes(new ColumnFilters.Choice(true, Set.of(value.lifetime())));
                columnFilters = columnFilters.withTags(tagFilters(value.tags()), Set.of());
            }
            provider = value.provider(); extraProvider = value.extraProvider();
            category = WorldObjectCategories.all().stream().filter(c -> c.id().equals(value.category())).findFirst().orElse(null);
            if (mode == Mode.ENTITIES || category != null && category.id().equals("entity")) category = null;
            if (mode == Mode.ENTITIES) { provider = extraProvider = ""; range = WorldObjectQuery.Range.GLOBAL; }
            sort = mode == Mode.ENTITIES && value.sort().equals("name") ? "registry_type" : value.sort();
            if (mode == Mode.WORLD && sort.equals("registry_type")) sort = "name";
            sortAscending = value.ascending();
        } finally { restoringState = false; }
    }
    public void onShow() {
        if (disposed || shown || faction != null && !Objects.equals(factionOwnerScope, UiStateScopes.current())) return;
        if (!Objects.equals(stateScope, UiStateScopes.current())) {
            switchScope();
        }
        shown = true;
        if (factionSubscription != null && !Boolean.getBoolean("gn.standalone")) factionSubscription.open();
        if (mode == Mode.ENTITIES) ClientEntityIndex.INSTANCE.addChangeListener(registrationChanged);
        if (!Boolean.getBoolean("gn.standalone")) queryNow(); else refresh();
    }
    public void onHide() {
        cancelMount();
        pendingScrollAnchor = null;
        saveState();
        shown = false; resetDoubleClick(); removeCallbacks(renderTask); removeCallbacks(fallbackQueryTask); dismissMenu();
        if (factionSubscription != null) factionSubscription.close();
        ClientEntityIndex.INSTANCE.removeChangeListener(registrationChanged); removeCallbacks(registrationTask);
        updateInteractionState();
    }
    public void onDispose() {
        if (disposed) return;
        onHide(); disposed = true;
        UiStateScopes.removeChangeListener(scopeChanged); removeCallbacks(scopeTask);
        session.removeChangeListener(changed); session.close();
        rowBindings.values().forEach(this::disposeRegistration);
    }
    private UiStateKey<DatabaseViewState.Widths> widthsKey() {
        return mode == Mode.ENTITIES ? DatabaseViewState.ENTITY_WIDTHS : DatabaseViewState.WORLD_WIDTHS;
    }
}
