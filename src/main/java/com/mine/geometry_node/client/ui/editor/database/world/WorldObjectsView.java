package com.mine.geometry_node.client.ui.editor.database.world;

import com.mine.geometry_node.client.worldquery.ClientWorldObjectRepository;
import com.mine.geometry_node.client.ui.components.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.list.UiNavigationRow;
import com.mine.geometry_node.client.ui.components.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.list.UiListSelection;
import com.mine.geometry_node.client.ui.components.display.UiTagStrip;
import com.mine.geometry_node.client.ui.components.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.input.UiSearchBar;
import com.mine.geometry_node.client.ui.components.menu.UiSearchableMenu;
import com.mine.geometry_node.client.ui.components.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.table.UiTableColumns;
import com.mine.geometry_node.client.ui.components.table.UiTableRow;
import com.mine.geometry_node.client.ui.components.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.table.UiTableViewport;
import com.mine.geometry_node.client.ui.editor.database.DatabaseSelection;
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
    private final ClientWorldObjectRepository.Session session;
    private final Consumer<DatabaseSelection> selectionChanged;
    private final LinearLayout rows;
    private final TextView status;
    private final UiActionButton scopeButton, dimensionButton, typeButton, providerButton, lifetimeButton, focusButton;
    private final UiSearchInput search, tags, source, radius;
    private final UiSearchBar searchBar;
    private final UiActionButton filterButton;
    private String submittedSearch = "";
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, UiActionButton> rowViews = new LinkedHashMap<>();
    private final Map<String, RowBinding> rowBindings = new LinkedHashMap<>();
    private record RowBinding(UiActionButton button, UiCheckBox checkbox, View stripe,
                              UiTableRow cells, int baseColor) {}
    private final UiTableColumns columns = new UiTableColumns(List.of(
            UiTableStyle.rowNumberColumn(),
            UiTableStyle.column("selection", 32),
            UiTableStyle.column("name", 240),
            UiTableStyle.column("type", 140),
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
    private WorldObjectQuery.Range range = WorldObjectQuery.Range.CURRENT_DIMENSION;
    private String dimension = "", type = "", provider = "", lifetime = "";
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

    private final Runnable queryTask = this::query;
    private final Runnable renderTask = this::refresh;
    private final Runnable changed = () -> {
        if (!disposed && shown) { removeCallbacks(renderTask); post(renderTask); }
    };

    public WorldObjectsView(Context context, Consumer<DatabaseSelection> selectionChanged) {
        super(context); this.selectionChanged = selectionChanged;
        session = ClientWorldObjectRepository.INSTANCE.newSession();
        snapshot = session.snapshot(); session.addChangeListener(changed);
        UiFixedSplitLayout body = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.HORIZONTAL,
                UiFixedSplitLayout.Edge.FIRST, 216, 100, 240);
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
        scopeButton=button(context,"",this::chooseScope);
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
        tags=input(tr("tags_hint")); source=input(tr("source_hint")); radius=input(tr("radius_hint"));
        radius.setText("128");
        filterField(advancedFilters,tr("scope"),scopeButton);
        filterField(advancedFilters,tr("lifetime"),lifetimeButton);
        filterField(advancedFilters,tr("dimension"),dimensionButton);
        filterField(advancedFilters,tr("provider"),providerButton);
        filterField(advancedFilters,tr("type"),typeButton);
        filterField(advancedFilters,tr("radius"),radius);
        filterField(advancedFilters,tr("tags"),tags);
        filterField(advancedFilters,tr("source"),source);
        filterReset(advancedFilters,this::resetFilters);
        LinearLayout resultBar=new LinearLayout(context); resultBar.setGravity(Gravity.CENTER_VERTICAL);
        resultBar.setPadding(px(14),0,px(10),0);
        resultTitle=label(context,tr("all_objects"),UiTheme.Text.NORMAL);
        resultTitle.setSingleLine(true);resultTitle.setEllipsize(TextUtils.TruncateAt.END);
        resultTitle.setOnClickListener(this::chooseCategory);
        resultBar.addView(resultTitle,new LinearLayout.LayoutParams(0,-1,1));
        focusButton=button(context,tr("focus"),this::focusSelected); focusButton.setEnabled(false);
        resultBar.addView(focusButton,new LinearLayout.LayoutParams(-2,px(28)));
        column.addView(resultBar,new LinearLayout.LayoutParams(-1,px(38)));
        tableHeader=UiTableStyle.header(context, columns);
        tableHeader.addView(UiTableStyle.rowNumberCell(context, 0),new ViewGroup.LayoutParams(-1,-1));
        selectAll=new UiCheckBox(context);
        selectAll.setContentDescription(tr("select_all"));
        selectAll.setOnCheckedChangeListener((checkbox, checked)->toggleAll());
        FrameLayout selectAllHost=new FrameLayout(context);
        selectAllHost.setOnClickListener(view->selectAll.performClick());
        selectAllHost.addView(selectAll,new LayoutParams(px(14),px(14),Gravity.CENTER));
        tableHeader.addView(selectAllHost,new ViewGroup.LayoutParams(-1,-1));
        for(String key:List.of("name","type","dimension","position","lifetime","tags")) addHeader(key);
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
        column.addView(status,new LinearLayout.LayoutParams(-1,px(28)));
        for(UiSearchInput input:List.of(tags,source,radius))input.setOnQueryChanged(value->scheduleQuery());
        selectAll.setEnabled(false);
        rebuildNavigation(); updateFilters();
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
        range=WorldObjectQuery.Range.CURRENT_DIMENSION;
        dimension=type=provider=lifetime=extraProvider="";category=null;
        submittedSearch="";
        search.setText("");tags.setText("");source.setText("");radius.setText("128");
        updateFilters();rebuildNavigation();queryNow();
    }
    private void rebuildNavigation() {
        categoryRows.removeAllViews();categoryButtons.clear();
        addCategory("",tr("all_objects"),
                snapshot.entries().size(),()->{category=null;extraProvider=provider=type="";queryNow();});
        for(var value:WorldObjectCategories.all()) addCategory(value.id(),Component.translatable(value.labelKey()).getString(),
                snapshot.entries().stream().filter(value::matches).count(),()->{
                    category=value;extraProvider=provider=type="";queryNow();
                });
        Set<String> known=new HashSet<>();WorldObjectCategories.all().forEach(c->known.addAll(c.providers()));
        typeCatalog.stream().map(WorldObjectType::provider).filter(p->!known.contains(p)&&!p.equals("scene"))
                .distinct().forEach(id->addCategory("provider:"+id,providerLabel(id),snapshot.entries().stream().filter(e->e.provider().equals(id)).count(),()->{
                            category=null;extraProvider=provider=id;type="";queryNow();
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
                +"  ·  "+sortedEntries().size()+"  ▾");
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
        UiActionButton button = UiTableStyle.headerCell(getContext(), tr(key), () -> {
            sortAscending = !sort.equals(key) || !sortAscending; sort = key; resetDoubleClick();
            if (!snapshot.loading()) rebuildRows();
        });
        tableHeader.addView(button, new ViewGroup.LayoutParams(-1, -1));
    }
    private void scheduleQuery() {
        removeCallbacks(queryTask);
        if (shown && !disposed) postDelayed(queryTask, 300);
    }
    private void queryNow() { removeCallbacks(queryTask); query(); }
    private void query() {
        if (!shown || disposed) return;
        final WorldObjectQuery query;
        try {
            double distance = range == WorldObjectQuery.Range.NEARBY
                    ? Double.parseDouble(radius.getText().toString().trim()) : 128;
            Set<String> providers = one(provider), types = one(type);
            if (category != null && provider.isEmpty()) providers = category.providers();
            if (category != null && type.isEmpty()) {
                Set<String> matching = new LinkedHashSet<>();
                typeCatalog.stream().filter(t -> category.accepts().test(t.provider(),t.id())).forEach(t -> matching.add(t.id()));
                // Entity registries can exceed the wire budget; retain provider filtering plus a page predicate.
                if (!matching.isEmpty() && matching.size() <= WorldObjectQuery.MAX_FILTERS && !category.id().equals("entity")) types = matching;
                if(category.id().equals("interaction"))types=Set.of("minecraft:interaction");
            }
            query = new WorldObjectQuery(range, dimension, providers, types, submittedSearch,
                    tagFilters(tags.getText().toString()), one(source.getText().toString().trim()), one(lifetime),
                    distance, WorldObjectQuery.MAX_RESULTS);
        } catch (IllegalArgumentException invalid) { setStatus(tr("invalid_query")); return; }
        dismissMenu(); resetDoubleClick();
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
        if (disposed || !shown) return;
        snapshot = session.snapshot();
        if (snapshot.equals(renderedSnapshot)) { updateInteractionState(); return; }
        renderedSnapshot=snapshot;
        if (!snapshot.loading()) {
            if (!snapshot.types().isEmpty()) typeCatalog = snapshot.types();
            if (!snapshot.dimensionIds().isEmpty()) dimensionCatalog = snapshot.dimensionIds();
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
    private String typeLabel(String id) {
        for (WorldObjectType value : typeCatalog) if (value.id().equals(id))
            return value.labelKey().isEmpty() ? id : Component.translatable(value.labelKey()).getString();
        return id;
    }
    private void updateFilters() {
        scopeButton.setText(tr("range." + range.name().toLowerCase(Locale.ROOT)));
        dimensionButton.setText(dimension.isEmpty() ? tr("automatic_dimension") : dimension);
        providerButton.setText(provider.isEmpty() ? tr("all") : providerLabel(provider));
        typeButton.setText(type.isEmpty() ? tr("all") : typeLabel(type));
        lifetimeButton.setText(lifetime.isEmpty() ? tr("all") : lifetimeLabel(lifetime));
        for (UiActionButton button : List.of(scopeButton, dimensionButton, providerButton, typeButton, lifetimeButton)) {
            button.setEllipsize(TextUtils.TruncateAt.END);
        }
        radius.setEnabled(range == WorldObjectQuery.Range.NEARBY);
    }
    private String providerLabel(String id) {
        String key = "geometry_node.database.provider." + id;
        String text = Component.translatable(key).getString();
        return text.equals(key) ? id : text;
    }
    private void chooseCategory(View anchor) {
        LinkedHashMap<String,String> options=new LinkedHashMap<>();
        options.put("",tr("all_objects"));
        for(var value:WorldObjectCategories.all())options.put("category:"+value.id(),Component.translatable(value.labelKey()).getString());
        typeCatalog.stream().map(WorldObjectType::provider).distinct().forEach(id->options.put("provider:"+id,providerLabel(id)));
        choose(anchor,"object_types",options,value->{
            category=null;extraProvider=provider=type="";
            if(value.startsWith("category:"))category=WorldObjectCategories.all().stream()
                    .filter(c->value.equals("category:"+c.id())).findFirst().orElse(null);
            else if(value.startsWith("provider:"))extraProvider=provider=value.substring("provider:".length());
            queryNow();
        });
    }
    private void chooseScope() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (var value : WorldObjectQuery.Range.values()) options.put(value.name(), tr("range." + value.name().toLowerCase(Locale.ROOT)));
        choose(scopeButton, "scope", options, value -> {
            range = WorldObjectQuery.Range.valueOf(value);
            // Nearby always refers to this connection's current dimension/camera.
            dimension = ""; updateFilters(); queryNow();
        });
    }
    private void chooseDimension() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>(); options.put("", tr("automatic_dimension"));
        for (String id : dimensionCatalog) options.put(id, id);
        choose(dimensionButton, "dimension", options, value -> { dimension = value; updateFilters(); queryNow(); });
    }
    private void chooseProvider() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>(); options.put("", tr("all"));
        typeCatalog.stream().map(WorldObjectType::provider).distinct().forEach(id -> options.put(id, providerLabel(id)));
        choose(providerButton, "provider", options, value -> { provider = value; extraProvider=value; category=null; type = ""; updateFilters(); queryNow(); });
    }
    private void chooseType() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>(); options.put("", tr("all"));
        for (var value : typeCatalog) if ((provider.isEmpty() || provider.equals(value.provider())) && (category==null || category.accepts().test(value.provider(),value.id()))) options.put(value.id(), typeOption(value.id()));
        for (var entry : snapshot.entries()) if ((provider.isEmpty() || provider.equals(entry.provider())) && (category==null || category.matches(entry))) options.putIfAbsent(entry.type(), typeOption(entry.type()));
        choose(typeButton, "type", options, value -> { type = value; updateFilters(); queryNow(); });
    }
    private String typeOption(String id) {
        String label = typeLabel(id);
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
            case "type" -> Comparator.comparing(entry -> typeLabel(entry.type()), String.CASE_INSENSITIVE_ORDER);
            case "dimension" -> Comparator.comparing(WorldObjectEntry::dimension);
            case "lifetime" -> Comparator.comparing(WorldObjectEntry::lifetime);
            case "tags" -> Comparator.comparing(entry -> String.join(",",entry.tags()));
            case "position" -> Comparator.comparing(WorldObjectEntry::dimension).thenComparingDouble(entry -> entry.position().x);
            default -> Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER);
        };
        if (!sortAscending) comparator = comparator.reversed();
        return snapshot.entries().stream().filter(e -> category == null || category.matches(e)).sorted(comparator.thenComparing(WorldObjectEntry::key)).toList();
    }
    private String displayName(WorldObjectEntry entry) { return entry.name().isEmpty() ? typeLabel(entry.type()) : entry.name(); }
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
            line.addView(UiTableStyle.rowNumberCell(getContext(), index),new ViewGroup.LayoutParams(-1,-1));
            FrameLayout checkboxHost=new FrameLayout(getContext());
            UiCheckBox checkbox=new UiCheckBox(getContext());
            checkbox.setContentDescription(displayName(entry));
            checkbox.setOnCheckedChangeListener((view,checked)->toggleCheckbox(entry,checked));
            checkboxHost.setOnClickListener(view->checkbox.performClick());
            checkboxHost.addView(checkbox,new LayoutParams(px(14),px(14),Gravity.CENTER));
            line.addView(checkboxHost,new ViewGroup.LayoutParams(-1,-1));
            var presentation=WorldObjectCategories.forEntry(entry);
            cell(line,displayName(entry));
            cell(line,presentation==null?typeLabel(entry.type()):Component.translatable(presentation.labelKey()).getString());
            cell(line,dimensionLabel(entry.dimension()));
            cell(line,position(entry));
            cell(line,lifetimeLabel(entry.lifetime()));
            UiTagStrip tagCell=new UiTagStrip(getContext());tagCell.setTags(entry.tags());
            line.addView(tagCell,new ViewGroup.LayoutParams(-1,-1));

            FrameLayout host = new FrameLayout(getContext());
            host.addView(row, new LayoutParams(-1, -1));
            // Text cells pass events to the row; the checkbox owns its separate hit target.
            host.addView(line, new LayoutParams(-1, -1));
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
            rows.addView(host, new LinearLayout.LayoutParams(-1, px(UiTableStyle.ROW_HEIGHT)));
        }
        updateSelection();
    }
    private void cell(UiTableRow row, String text) {
        TextView cell = UiTableStyle.cell(getContext(), text, UiTheme.Text.NORMAL);
        row.addView(cell, new ViewGroup.LayoutParams(-1, -1));
    }
    private boolean canSelect() { return shown && !disposed && !snapshot.loading(); }
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
        if(snapshot.loading())return null;
        List<WorldObjectEntry> entries = selectedEntries();
        if (entries.isEmpty()) return null;
        if(entries.size()>1)return new DatabaseSelection(tr("selected",entries.size()),tr("world"),"",0,
                List.of(new DatabaseSelection.Field(tr("selected_count"),Integer.toString(entries.size()))),
                List.of(new DatabaseSelection.Action(tr("focus"),this::focusSelected)),List.of());
        var entry=entries.getFirst();
        var position=List.of(new DatabaseSelection.Field("X",number(entry.position().x)),
                new DatabaseSelection.Field("Y",number(entry.position().y)),new DatabaseSelection.Field("Z",number(entry.position().z)));
        var metadata=List.of(new DatabaseSelection.Field(tr("type"),typeOption(entry.type())),
                new DatabaseSelection.Field(tr("lifetime"),lifetimeLabel(entry.lifetime())),
                new DatabaseSelection.Field(tr("id"),entry.key()));
        var source=new ArrayList<DatabaseSelection.Field>();
        source.add(new DatabaseSelection.Field(tr("source"),entry.source().isEmpty()?"—":entry.source()));
        source.add(new DatabaseSelection.Field(tr("provider"),providerLabel(entry.provider())));
        entry.references().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(ref->
                source.add(new DatabaseSelection.Field(ref.getKey(),ref.getValue())));
        return new DatabaseSelection(displayName(entry),typeLabel(entry.type())+" · "+tr("world"),
                "",UiTheme.Text.MUTED,List.of(),
                List.of(new DatabaseSelection.Action(tr("focus"),this::focusSelected)),List.of(
                new DatabaseSelection.Section(tr("spatial_position"),dimensionLabel(entry.dimension()),DatabaseSelection.Kind.POSITION,position),
                new DatabaseSelection.Section(tr("object_information"),"",DatabaseSelection.Kind.METADATA,metadata),
                new DatabaseSelection.Section(tr("tags"),"",DatabaseSelection.Kind.TAGS,
                        entry.tags().stream().map(tag->new DatabaseSelection.Field("",tag)).toList()),
                new DatabaseSelection.Section(tr("source"),"",DatabaseSelection.Kind.SOURCE,source)));
    }
    private static String number(double value) {return String.format(Locale.ROOT,"%.2f",value);}
    private static String dimensionLabel(String id) {
        return switch(id){case "minecraft:overworld"->tr("dimension.overworld");case "minecraft:the_nether"->tr("dimension.nether");
            case "minecraft:the_end"->tr("dimension.end");default->id;};
    }
    private static String position(WorldObjectEntry entry) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", entry.position().x, entry.position().y, entry.position().z);
    }
    private void focusSelected() {
        if (canSelect() && !selectedEntries().isEmpty()) setStatus(WorldObjectNavigation.focus(selectedEntries()));
    }
    public void onShow() {
        if (disposed || shown) return;
        shown = true;
        if (!Boolean.getBoolean("gn.standalone")) queryNow(); else refresh();
    }
    public void onHide() {
        shown = false; resetDoubleClick(); removeCallbacks(queryTask); removeCallbacks(renderTask); dismissMenu();
        if(filterPopover!=null)filterPopover.dismiss();
        updateInteractionState();
    }
    public void onDispose() {
        if (disposed) return;
        onHide(); disposed = true; session.removeChangeListener(changed); session.close();
    }
}
