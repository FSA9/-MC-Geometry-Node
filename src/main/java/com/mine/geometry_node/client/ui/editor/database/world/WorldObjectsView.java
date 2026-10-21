package com.mine.geometry_node.client.ui.editor.database.world;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.mine.geometry_node.client.worldquery.ClientWorldObjectRepository;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.composite.list.UiNavigationRow;
import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.composite.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.list.UiListSelection;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagStrip;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataSummaryCell;
import com.mine.geometry_node.client.ui.components.integration.metadata.UiDataDictionaryEditor;
import com.mine.geometry_node.client.ui.components.integration.metadata.DataDictionaryBinding;
import com.mine.geometry_node.client.ui.editor.database.metadata.DatabaseDataBindings;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityPreviewCell;
import com.mine.geometry_node.client.ui.components.composite.table.UiColumnFilterPopover;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableFilterHeader;
import com.mine.geometry_node.core.query.ColumnFilters;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableColumns;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableBatchWork;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRowFrame;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableViewport;
import com.mine.geometry_node.client.ui.editor.database.DatabaseSelection;
import com.mine.geometry_node.client.ui.editor.database.DatabaseViewState;
import com.mine.geometry_node.client.ui.workspace.state.*;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.worldquery.*;
import com.mine.geometry_node.core.node.RegistryDataManager;
import icyllis.modernui.core.Context;
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
import static com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets.*;

/** Server query presentation. Only immutable summaries cross from the client thread to this UI. */
public final class WorldObjectsView extends FrameLayout {
    private ClientWorldObjectRepository.Session session;
    private boolean awaitingScopeResponse;
    private final Consumer<DatabaseSelection> selectionChanged;
    private final LinearLayout rows;
    private final UiTableBatchWork rowBatch;
    private final NativeTypeLabels nativeTypeLabels;

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
    private ColumnFilters columnFilters = ColumnFilters.empty();
    private String registryTypeFilter = "";
    private final Map<String, UiTableFilterHeader> filterHeaders = new LinkedHashMap<>();
    private final UiStateContext uiState;
    private String stateScope;
    private boolean restoringState;
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
            UiTableStyle.column("registry_type", 180),
            UiTableStyle.column("value", 120),
            UiTableStyle.column("dimension", 150),
            UiTableStyle.column("position", 180),
            UiTableStyle.column("lifetime", 100),
            UiTableStyle.column("tags", 180),
            UiTableStyle.column("data", 220)));
    private final LinearLayout table;
    private final UiCheckBox selectAll;
    private ClientWorldObjectRepository.Snapshot snapshot, renderedSnapshot;
    private List<WorldObjectEntry> mountedEntries;
    private List<WorldObjectType> mountedTypes;
    // Includes pending rows so a sort can reuse the attached subset of an interrupted batch.
    private List<WorldObjectEntry> plannedEntries;
    private List<WorldObjectType> plannedTypes;
    private DatabaseSelection publishedSelection;
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
    WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged, UiStateContext state,
                     NativeTypeLabels nativeTypeLabels) {
        super(context);
        this.nativeTypeLabels = Objects.requireNonNull(nativeTypeLabels);
        rowBatch = new UiTableBatchWork(this);
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
        navigation.setBackground(background(UiThemes.EDITOR.surface().root()));
        TextView categoryTitle = label(context, tr("object_types"), UiThemes.EDITOR.text().muted());
        categoryTitle.setPadding(px(8),0,0,0);
        navigation.addView(categoryTitle, new LinearLayout.LayoutParams(-1, px(32)));
        categoryRows = new LinearLayout(context); categoryRows.setOrientation(LinearLayout.VERTICAL);
        UiScrollView navScroll = new UiScrollView(context); navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.addView(categoryRows, new LayoutParams(-1,-2));
        navigation.addView(navScroll, new LinearLayout.LayoutParams(-1,0,1));
        navigationDivider = body.divider();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(background(UiThemes.EDITOR.surface().canvas()));
        body.setPanes(navigation, column);
        filterPopover = new UiColumnFilterPopover(context, this);
        LinearLayout resultBar=new LinearLayout(context); resultBar.setGravity(Gravity.CENTER_VERTICAL);
        resultBar.setPadding(px(14),0,px(10),0);
        resultTitle=label(context,tr("all_objects"),UiThemes.EDITOR.text().normal());
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
        for(String key:List.of("name","type","registry_type","value","dimension","position","lifetime","tags")) addHeader(key);
        tableHeader.addView(UiTableStyle.cell(context, tr("data.title"), UiThemes.EDITOR.text().normal()),
                new ViewGroup.LayoutParams(-1, -1));
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
        status=label(context,tr("query_hint"),UiThemes.EDITOR.text().muted());
        UIUtils.setLockedTextSize(status,11); status.setPadding(px(14),0,px(14),0); status.setSingleLine(true);
        status.setBackground(background(UiThemes.EDITOR.surface().panel()));
        column.addView(status,new LinearLayout.LayoutParams(-1,px(UiTableStyle.STATUS_BAR_HEIGHT)));
        selectAll.setEnabled(false);
        restoreState();
        rebuildNavigation(); updateFilters();
        UiStateScopes.addChangeListener(scopeChanged);
    }
    private void rebuildNavigation() {
        categoryRows.removeAllViews();categoryButtons.clear();
        addCategory("",tr("all_objects"),
                navigationCounts.total(),()->selectCategory(null, ""));
        for(var value:WorldObjectCategories.all()) addCategory(value.id(),Component.translatable(value.labelKey()).getString(),
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
        categoryRows.addView(row,new LinearLayout.LayoutParams(-1,px(33)));
    }
    private void updateNavigationSelection() {
        String id=category!=null?category.id():extraProvider.isEmpty()?"":"provider:"+extraProvider;
        categoryButtons.forEach((key,button)->button.setPalette(rowPalette(key.equals(id),UiThemes.EDITOR.surface().root())));
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
        dismissMenu();
    }

    private void addHeader(String key) {
        String title = key.equals("value") ? Component.translatable("geometry_node.data_library.column_value").getString() : tr(key);
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
        if (!shown || disposed || !Objects.equals(stateScope, UiStateScopes.current())) return;
        // Let the popover report validation errors while keeping every committed field intact.
        WorldObjectQuery nextQuery = buildQuery(next, nextRange, nextRegistryType);
        cancelMount();
        columnFilters = next;
        registryTypeFilter = nextRegistryType;
        range = nextRange;
        selected.clear(); anchorKey = null; resetDoubleClick();
        saveState(); updateFilters(); updateSelection(); submitQuery(nextQuery);
    }
    private void applyDimensions(ColumnFilters.Choice choice, boolean loaded) {
        ColumnFilters nextColumns = columnFilters.withDimensions(choice);
        WorldObjectQuery.Range nextRange = loaded ? WorldObjectQuery.Range.LOADED : WorldObjectQuery.Range.GLOBAL;
        applyColumns(nextColumns, nextRange);
    }
    private LinkedHashMap<String, String> typeOptions() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (var c : WorldObjectCategories.all())
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
        if (!shown || disposed) return;
        View anchor = filterHeaders.get(key).filterButton();
        switch (key) {
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
                filterPopover.showChoices(anchor, tr("dimension"), options, columnFilters.dimensions(),
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
        }
    }
    private void queryNow() { if (restoringState) return; saveState(); query(); }
    private void query() {
        if (!shown || disposed) return;
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
                nextRegistryType);
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
    private void updateFilters() {
        filterHeaders.forEach((key, header) -> header.setFilterActive(switch (key) {
            case "name" -> !columnFilters.name().isEmpty();
            case "type" -> columnFilters.types().active();
            case "registry_type" -> !registryTypeFilter.isEmpty();
            case "value" -> columnFilters.value().active();
            case "dimension" -> columnFilters.dimensions().active() || range == WorldObjectQuery.Range.LOADED;
            case "position" -> columnFilters.x().active() || columnFilters.y().active() || columnFilters.z().active();
            case "lifetime" -> columnFilters.lifetimes().active();
            case "tags" -> !columnFilters.includedTags().isEmpty() || !columnFilters.excludedTags().isEmpty();
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
            case "type" -> Comparator.comparing(this::displayType, String.CASE_INSENSITIVE_ORDER);
            case "registry_type" -> Comparator.comparing(WorldObjectEntries::registryType, String.CASE_INSENSITIVE_ORDER);
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
        return snapshot.entries().stream().filter(e -> category == null || category.matches(e))
                .filter(e -> provider.isEmpty() || provider.equals(e.provider()))
                .filter(e -> WorldObjectQuery.matchesRegistryType(e, registryTypeFilter))
                .filter(e -> columnFilters.matches(displayName(e), WorldObjectQuery.filterType(e), WorldObjectQuery.numericValue(e),
                        e.dimension(), e.position().x, e.position().y, e.position().z, e.lifetime(), e.tags())).sorted(comparator.thenComparing(WorldObjectEntry::key)).toList();
    }
    private String displayType(WorldObjectEntry entry) {
        var presentation = WorldObjectCategories.forEntry(entry);
        return presentation == null ? typeLabel(entry.provider(), entry.type()) : Component.translatable(presentation.labelKey()).getString();
    }
    private String displayName(WorldObjectEntry entry) { return entry.name().isEmpty() ? typeLabel(entry.provider(), entry.type()) : entry.name(); }
    private void rebuildRows() {
        List<WorldObjectEntry> entries = sortedEntries();
        if (entries.equals(mountedEntries) && typeCatalog.equals(mountedTypes)) {
            updateSelection();
            return;
        }
        boolean reuse = hasSameRowData(entries);
        cancelMount();
        mountedEntries = null; mountedTypes = null;
        List<WorldObjectType> mountingTypes = List.copyOf(typeCatalog);
        plannedEntries = entries; plannedTypes = mountingTypes;
        Runnable complete = () -> {
            mountedEntries = entries;
            mountedTypes = mountingTypes;
            updateSelection();
        };
        if (!reuse) {
            rows.removeAllViews(); rowViews.clear(); rowBindings.clear();
            if (entries.isEmpty()) {
                TextView empty = label(getContext(), tr("empty"), UiThemes.EDITOR.text().muted());
                empty.setGravity(Gravity.CENTER); rows.addView(empty, new LinearLayout.LayoutParams(-1, px(96)));
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
                    if (rowBindings.containsKey(entry.key())) reorderRow(entry.key(), rowIndex);
                    else mountRow(entry, rowIndex);
                };
            }
        }, complete);
        updateSelection();
    }
    private boolean hasSameRowData(List<WorldObjectEntry> entries) {
        if (plannedEntries == null || !typeCatalog.equals(plannedTypes) || entries.size() != plannedEntries.size()) return false;
        Map<String, WorldObjectEntry> previous = new HashMap<>();
        for (WorldObjectEntry entry : plannedEntries) previous.put(entry.key(), entry);
        for (WorldObjectEntry entry : entries)
            if (!entry.equals(previous.remove(entry.key()))) return false;
        return previous.isEmpty();
    }
    private void reorderRow(String key, int index) {
        RowBinding binding = rowBindings.remove(key);
        ((TextView)binding.cells().getChildAt(0)).setText(Integer.toString(index + 1));
        int baseColor = UiTableStyle.rowColor(index % 2 != 0);
        binding.button().setPalette(rowPalette(selected.contains(key), baseColor));
        rowBindings.put(key, new RowBinding(binding.button(), binding.checkbox(), binding.stripe(), binding.cells(), baseColor));
        rowViews.remove(key);
        rowViews.put(key, binding.button());
        // Moving within the same parent preserves native preview registration and focus/attach state.
        rows.bringChildToFront((View)binding.button().getParent());
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
        line.setMinimumHeight(px(UiTableStyle.ROW_HEIGHT));
        line.addView(UiTableStyle.rowNumberCell(getContext(), index + 1),new ViewGroup.LayoutParams(-1,-1));
        UiCheckBox checkbox=new UiCheckBox(getContext());
        checkbox.setContentDescription(displayName(entry));
        checkbox.setOnCheckedChangeListener((view,checked)->toggleCheckbox(entry,checked));
        line.addView(UiTableStyle.selectionCell(getContext(), checkbox),new ViewGroup.LayoutParams(-1,-1));
        cell(line,displayName(entry));
        cell(line,displayType(entry));
        cell(line,WorldObjectEntries.registryType(entry));
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
        UiDataSummaryCell dataCell = new UiDataSummaryCell(getContext());
        dataCell.setSummary(dataCount(entry), entry.references().getOrDefault(WorldObjectDataEdits.SUMMARY_REFERENCE, ""),
                entry.references().containsKey(WorldObjectDataEdits.TARGET_REFERENCE) ? () -> openDataEditor(entry) : null);
        line.addView(dataCell, new ViewGroup.LayoutParams(-1, -1));

        // Cells determine height; background and selection overlays follow without stretching the row.
        FrameLayout host = new UiTableRowFrame(getContext(), line, row);
        View stripe=new View(getContext());stripe.setBackground(background(UiThemes.EDITOR.selection().outline()));
        host.addView(stripe,new LayoutParams(Math.max(1,px(2)),-1));
        View border=new View(getContext());border.setBackground(background(UiTableStyle.dividerColor(UiThemes.EDITOR)));
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
        // Visual row mounting does not invalidate the authoritative selected objects.
        if(awaitingScopeResponse || !Objects.equals(stateScope, UiStateScopes.current()) || snapshot.loading())return null;
        List<WorldObjectEntry> entries = selectedEntries();
        if (entries.isEmpty()) return null;
        if(entries.size()>1)return new DatabaseSelection(tr("selected",entries.size()),tr("world"),"",0,
                List.of(new DatabaseSelection.Field(tr("selected_count"),Integer.toString(entries.size()))),
                List.of(new DatabaseSelection.Action(tr("focus"),this::focusSelected)),List.of());
        var entry=entries.getFirst();
        var position=List.of(new DatabaseSelection.Field("X",number(entry.position().x)),
                new DatabaseSelection.Field("Y",number(entry.position().y)),new DatabaseSelection.Field("Z",number(entry.position().z)));
        var metadata=List.of(new DatabaseSelection.Field(tr("type"), entry.provider().equals("native")
                ? WorldObjectEntries.registryType(entry) : entry.type()),
                new DatabaseSelection.Field(tr("lifetime"),lifetimeLabel(entry.lifetime())),
                new DatabaseSelection.Field(tr("id"),entry.key()));
        var source=new ArrayList<DatabaseSelection.Field>();
        source.add(new DatabaseSelection.Field(tr("source"),entry.source().isEmpty()?"—":entry.source()));
        source.add(new DatabaseSelection.Field(tr("provider"),providerLabel(entry.provider())));
        entry.references().entrySet().stream()
                .filter(ref -> !ref.getKey().equals(WorldObjectTagEdits.TARGET_REFERENCE)
                        && !Set.of(WorldObjectDataEdits.TARGET_REFERENCE, WorldObjectDataEdits.COUNT_REFERENCE,
                        WorldObjectDataEdits.SUMMARY_REFERENCE).contains(ref.getKey()))
                .sorted(Map.Entry.comparingByKey()).forEach(ref->
                source.add(new DatabaseSelection.Field(ref.getKey(),ref.getValue())));
        return new DatabaseSelection(displayName(entry),typeLabel(entry.provider(), entry.type())+" · "+tr("world"),
                "",UiThemes.EDITOR.text().muted(),List.of(),
                List.of(new DatabaseSelection.Action(tr("focus"),this::focusSelected)),List.of(
                new DatabaseSelection.Section(tr("spatial_position"),entry.dimension(),DatabaseSelection.Kind.POSITION,position),
                new DatabaseSelection.Section(tr("object_information"),"",DatabaseSelection.Kind.METADATA,metadata),
                new DatabaseSelection.Section(tr("tags"),"",DatabaseSelection.Kind.TAGS,
                        entry.tags().stream().map(tag->new DatabaseSelection.Field("",tag)).toList(),
                        session.canEditTags(entry) ? new DatabaseSelection.TagEditor(
                                entry.references().get(WorldObjectTagEdits.TARGET_REFERENCE),
                                !session.isTagUpdatePending(), session.tagUpdateMessage().isEmpty() ? ""
                                        : Component.translatable("geometry_node." + session.tagUpdateMessage()).getString(),
                                tag -> submitTagEdit(entry, tag, true),
                                tag -> submitTagEdit(entry, tag, false)) : null),
                new DatabaseSelection.Section(tr("source"),"",DatabaseSelection.Kind.SOURCE,source)), entityPreview(entry),
                entry.references().containsKey(WorldObjectDataEdits.TARGET_REFERENCE)
                        ? new DatabaseSelection.DataEditor(stateScope + ":" + entry.references().get(WorldObjectDataEdits.TARGET_REFERENCE),
                        dataCount(entry), dataBinding(entry)) : null);
    }
    private static int dataCount(WorldObjectEntry entry) {
        try { return Math.max(0, Integer.parseInt(entry.references().getOrDefault(WorldObjectDataEdits.COUNT_REFERENCE, "0"))); }
        catch (NumberFormatException invalid) { return 0; }
    }
    private DataDictionaryBinding dataBinding(WorldObjectEntry entry) {
        var source = session;
        String scope = stateScope;
        return DatabaseDataBindings.world(source, entry, () -> !disposed && shown && session == source
                && Objects.equals(scope, stateScope) && Objects.equals(scope, UiStateScopes.current())
                && selected.size() == 1 && selected.contains(entry.key()));
    }
    private void openDataEditor(WorldObjectEntry expected) {
        if (!canSelect()) return;
        var live = snapshot.entries().stream().filter(row -> row.key().equals(expected.key())
                && Objects.equals(row.references().get(WorldObjectDataEdits.TARGET_REFERENCE),
                expected.references().get(WorldObjectDataEdits.TARGET_REFERENCE))).findFirst().orElse(null);
        if (live == null) return;
        selected.clear(); selected.add(live.key()); anchorKey = live.key();
        updateSelection();
        UiDataDictionaryEditor.show(this, displayName(live) + " · " + tr("world"), dataBinding(live));
    }
    private static DatabaseSelection.EntityPreview entityPreview(WorldObjectEntry entry) {
        if (!entry.provider().equals("native") || entry.type().equals("minecraft:interaction")) return null;
        try {
            return new DatabaseSelection.EntityPreview(UUID.fromString(entry.references().getOrDefault("entityUuid", "")),
                    entry.dimension(), null);
        } catch (IllegalArgumentException invalid) { return null; }
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
    private static String number(double value) {return String.format(Locale.ROOT,"%.2f",value);}
    private static String position(WorldObjectEntry entry) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", entry.position().x, entry.position().y, entry.position().z);
    }
    private void focusSelected() {
        if (canSelect() && !selectedEntries().isEmpty()) setStatus(WorldObjectNavigation.focus(selectedEntries()));
    }
    private void saveState() {
        if (restoringState || stateScope == null) return;
        uiState.write(DatabaseViewState.WORLD_COLUMN_FILTERS, stateScope, columnFilters);
        uiState.write(DatabaseViewState.WORLD, stateScope, new DatabaseViewState.World(
                "", "", range.name(),
                "", provider, "", "", "", category == null ? "" : category.id(),
                extraProvider, sort, sortAscending, registryTypeFilter));
    }
    private void switchScope() {
        cancelMount();
        removeCallbacks(renderTask); removeCallbacks(fallbackQueryTask);
        dismissMenu();
        saveState(); restoreState(); selected.clear(); anchorKey = null;
        session.removeChangeListener(changed); session.close();
        session = ClientWorldObjectRepository.INSTANCE.newSession(); session.addChangeListener(changed);
        awaitingScopeResponse = true;
        snapshot = new ClientWorldObjectRepository.Snapshot(WorldObjectQuery.defaults(), WorldObjectPage.empty(0, ""), true, 0);
        renderedSnapshot = null; mountedEntries = null; mountedTypes = null;
        plannedEntries = null; plannedTypes = null;
        typeCatalog = List.of(); dimensionCatalog = List.of();
        navigationCounts = WorldObjectPage.Counts.empty();
        indexedTypeCatalog = null; typeLookup.clear();
        rows.removeAllViews(); rowViews.clear(); rowBindings.clear(); resetDoubleClick();
        updateFilters(); rebuildNavigation(); updateSelection();
    }
    private void restoreState() {
        stateScope = UiStateScopes.current();
        var value = uiState.read(DatabaseViewState.WORLD, stateScope);
        restoringState = true;
        try {
            columnFilters = uiState.read(DatabaseViewState.WORLD_COLUMN_FILTERS, stateScope);
            registryTypeFilter = value.registryType();
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
        cancelMount();
        saveState();
        shown = false; resetDoubleClick(); removeCallbacks(renderTask); removeCallbacks(fallbackQueryTask); dismissMenu();
        updateInteractionState();
    }
    public void onDispose() {
        if (disposed) return;
        onHide(); disposed = true;
        plannedEntries = null; plannedTypes = null;
        UiStateScopes.removeChangeListener(scopeChanged); removeCallbacks(scopeTask);
        session.removeChangeListener(changed); session.close();
    }
}
