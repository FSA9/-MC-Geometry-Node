package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.input.UiSearchBar;
import com.mine.geometry_node.client.ui.editor.database.DatabaseWidgets;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Search and query filters; commands remain owned by the window. */
final class DataLibraryToolbar extends LinearLayout {
    final UiIconButton addFolderButton;
    final UiIconButton deleteButton;
    final UiActionButton typeButton;
    final UiActionButton scopeButton;
    final UiActionButton filterButton;
    final UiActionButton tagButton;
    final LinearLayout filterContent;
    final UiSearchBar searchBar;
    final UiSearchInput searchInput;
    private final UiActionButton newEntryButton;

    DataLibraryToolbar(Context context, Runnable addFolder, Consumer<View> addEntry,
                       Runnable delete, Consumer<View> chooseType, Consumer<View> chooseTag, Runnable toggleScope,
                       Runnable search, Runnable reset, Consumer<View> showFilters) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(px(10), 0, px(10), px(8));
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(UiTheme.Surface.PANEL);
        setBackground(background);

        LinearLayout queries = new LinearLayout(context);
        queries.setBaselineAligned(false);
        queries.setGravity(Gravity.CENTER_VERTICAL);
        addView(queries, new LayoutParams(-1, px(32)));
        searchBar = new UiSearchBar(context, tr("geometry_node.data_library.search"),
                tr("geometry_node.asset_library.toolbar.search"), search);
        searchInput = searchBar.input();
        queries.addView(searchBar, new LayoutParams(0, px(32), 1));
        filterButton = action(context, tr("geometry_node.database.filter"));
        filterButton.setOnClickListener(showFilters::accept);
        queries.addView(filterButton, dimensions(60, 32, 8));
        newEntryButton = action(context, tr("geometry_node.data_library.add"));
        newEntryButton.setOnClickListener(addEntry::accept);
        queries.addView(newEntryButton, dimensions(104, 32, 8));

        filterContent = new LinearLayout(context);
        filterContent.setOrientation(VERTICAL);
        scopeButton = action(context, tr("geometry_node.data_library.include_subfolders"));
        scopeButton.setOnClickListener(view -> toggleScope.run());
        typeButton = action(context, tr("geometry_node.data_library.all_types"));
        typeButton.setOnClickListener(chooseType::accept);
        tagButton = action(context, tr("geometry_node.data_library.all_tags"));
        tagButton.setOnClickListener(chooseTag::accept);
        DatabaseWidgets.filterField(filterContent, tr("geometry_node.database.folder"), scopeButton);
        DatabaseWidgets.filterField(filterContent, tr("geometry_node.database.type"), typeButton);
        DatabaseWidgets.filterField(filterContent, tr("geometry_node.database.tags"), tagButton);
        DatabaseWidgets.filterReset(filterContent, reset);

        // Mounted by the window in the directory heading and results toolbar respectively.
        addFolderButton = icon(context, SvgIconView.Icon.PLUS, "geometry_node.data_library.add_folder");
        addFolderButton.setOnClickListener(view -> addFolder.run());
        deleteButton = icon(context, SvgIconView.Icon.CLEAR, "geometry_node.common.delete");
        deleteButton.setOnClickListener(view -> delete.run());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight());
        newEntryButton.getLayoutParams().width = Math.min(px(104), Math.max(px(56), available / 3));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private static UiActionButton action(Context context, String text) {
        return DatabaseWidgets.button(context, text, () -> {});
    }

    private static UiIconButton icon(Context context, SvgIconView.Icon icon, String key) {
        UiIconButton button = new UiIconButton(context, new SvgIconView(context, icon, UiTheme.Text.SECONDARY),
                new UiIconButton.Style(0, 0, UiTheme.Control.ICON, UiTheme.Control.BORDER, UiTheme.Control.ICON_BORDER));
        button.setContentDescription(tr(key));
        return button;
    }

    private static LayoutParams dimensions(int width, int height, int margin) {
        LayoutParams params = new LayoutParams(px(width), px(height));
        params.leftMargin = px(margin);
        return params;
    }

    private static int px(float value) { return UIUtils.dp2pxInt(value); }
    private static String tr(String key) { return Component.translatable(key).getString(); }
}
