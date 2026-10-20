package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Creation, deletion and directory scope controls, mounted in their owning toolbar rows. */
final class DataLibraryToolbar {
    final UiIconButton addFolderButton;
    final UiIconButton newEntryButton;
    final UiIconButton deleteButton;
    final UiActionButton scopeButton;

    DataLibraryToolbar(Context context, Runnable addFolder, Consumer<View> addEntry,
                       Runnable delete, Runnable toggleScope) {
        scopeButton = scopeButton(context);
        scopeButton.setContentDescription(tr("geometry_node.data_library.include_subfolders"));
        scopeButton.setOnClickListener(view -> toggleScope.run());
        addFolderButton = icon(context, SvgIconView.Icon.PLUS, "geometry_node.data_library.add_folder");
        addFolderButton.setOnClickListener(view -> addFolder.run());
        newEntryButton = icon(context, SvgIconView.Icon.PLUS, "geometry_node.data_library.add");
        newEntryButton.setOnClickListener(addEntry::accept);
        deleteButton = icon(context, SvgIconView.Icon.CLEAR, "geometry_node.common.delete");
        deleteButton.setOnClickListener(view -> delete.run());
    }

    private static UiActionButton scopeButton(Context context) {
        UiActionButton button = UiActionButton.withPalette(context,
                tr("geometry_node.data_library.include_subfolders"), UiActionButton.Palette.toggle(false),
                UiActionButton.Density.COMPACT,
                new UiActionButton.Style(0, 0, UiTheme.Control.ICON_BORDER, UiTheme.Metrics.DISABLED_ALPHA));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        button.setSingleLine(true);
        return button;
    }

    private static UiIconButton icon(Context context, SvgIconView.Icon icon, String key) {
        UiIconButton button = new UiIconButton(context, new SvgIconView(context, icon, UiTheme.Text.SECONDARY),
                new UiIconButton.Style(0, 0, UiTheme.Control.ICON, UiTheme.Control.BORDER, UiTheme.Control.ICON_BORDER));
        button.setContentDescription(tr(key));
        return button;
    }

    private static String tr(String key) { return Component.translatable(key).getString(); }
}
