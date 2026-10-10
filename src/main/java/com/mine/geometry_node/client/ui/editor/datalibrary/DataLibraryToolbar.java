package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.components.common.UiSearchInput;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Toolbar presentation; filtering, creation and persistence remain window actions. */
final class DataLibraryToolbar extends LinearLayout {
    final UiIconButton addFolderButton;
    final UiIconButton deleteButton;
    final UiActionButton typeButton;
    final UiSearchInput searchInput;

    DataLibraryToolbar(Context context, Runnable addFolder, Consumer<View> addEntry,
                       Runnable delete, Consumer<View> chooseType, Runnable search, Runnable clear) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(px(8), px(4), px(8), px(4));
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(0xFF272727);
        background.setStroke(Math.max(1, px(1)), 0xFF383838);
        setBackground(background);
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        addView(actions, new LayoutParams(-1, px(32)));
        addFolderButton = icon(context, SvgIconView.Icon.SQUARE_PLUS, "geometry_node.data_library.add_folder");
        addFolderButton.setOnClickListener(view -> addFolder.run());
        actions.addView(addFolderButton, dimensions(30, 32, 0));
        UiIconButton add = icon(context, SvgIconView.Icon.PLUS, "geometry_node.data_library.add");
        add.setOnClickListener(addEntry::accept);
        actions.addView(add, dimensions(30, 32, 4));
        deleteButton = icon(context, SvgIconView.Icon.CLEAR, "geometry_node.common.delete");
        deleteButton.setOnClickListener(view -> delete.run());
        actions.addView(deleteButton, dimensions(30, 32, 4));
        actions.addView(new View(context), new LayoutParams(0, 1, 1));
        typeButton = new UiActionButton(context, tr("geometry_node.data_library.all_types"),
                UiActionButton.Role.INLINE, UiActionButton.Density.COMPACT);
        typeButton.setOnClickListener(chooseType::accept);
        actions.addView(typeButton, dimensions(92, 32, 8));
        LinearLayout queries = new LinearLayout(context);
        queries.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams queryParams = new LayoutParams(-1, px(32));
        queryParams.topMargin = px(4);
        addView(queries, queryParams);
        searchInput = new UiSearchInput(context);
        searchInput.setHint(tr("geometry_node.data_library.search"));
        // Search remains a draft until the explicit search action.
        queries.addView(searchInput, new LayoutParams(0, px(32), 1));
        UiIconButton submit = icon(context, SvgIconView.Icon.SEARCH, "geometry_node.asset_library.toolbar.search");
        submit.setOnClickListener(view -> search.run());
        queries.addView(submit, dimensions(30, 32, 4));
        UiIconButton reset = icon(context, SvgIconView.Icon.CLOSE, "geometry_node.data_library.clear_search");
        reset.setOnClickListener(view -> clear.run());
        queries.addView(reset, dimensions(30, 32, 4));
    }

    private static UiIconButton icon(Context context, SvgIconView.Icon icon, String key) {
        return new UiIconButton(context, new SvgIconView(context, icon, 0xFFD2D2D2),
                new UiIconButton.Style(3, 1, 0xFF3A3A3A, 0xFF4A4A4A, 0xFF484848)).tooltip(tr(key));
    }

    private static LayoutParams dimensions(int width, int height, int margin) {
        LayoutParams params = new LayoutParams(px(width), px(height));
        params.leftMargin = px(margin);
        return params;
    }

    private static int px(float value) { return UIUtils.dp2pxInt(value); }
    private static String tr(String key) { return Component.translatable(key).getString(); }
}
