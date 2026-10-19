package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Creation, deletion and directory scope controls, mounted in their owning toolbar rows. */
final class DataLibraryToolbar {
    final UiIconButton addFolderButton;
    final UiIconButton newEntryButton;
    final UiIconButton deleteButton;
    final UiIconButton scopeButton;

    DataLibraryToolbar(Context context, Runnable addFolder, Consumer<View> addEntry,
                       Runnable delete, Runnable toggleScope) {
        scopeButton = scopeIcon(context);
        scopeButton.setContentDescription(tr("geometry_node.data_library.include_subfolders"));
        scopeButton.setOnClickListener(view -> toggleScope.run());
        addFolderButton = icon(context, SvgIconView.Icon.PLUS, "geometry_node.data_library.add_folder");
        addFolderButton.setOnClickListener(view -> addFolder.run());
        newEntryButton = icon(context, SvgIconView.Icon.PLUS, "geometry_node.data_library.add");
        newEntryButton.setOnClickListener(addEntry::accept);
        deleteButton = icon(context, SvgIconView.Icon.CLEAR, "geometry_node.common.delete");
        deleteButton.setOnClickListener(view -> delete.run());
    }

    private static UiIconButton scopeIcon(Context context) {
        return new UiIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.INCLUDE_SUBFOLDERS, UiTheme.Text.NORMAL),
                new UiIconButton.Style(0, 0, 0, UiTheme.Control.ICON_HOVER,
                        UiTheme.Control.ICON_PRESSED, UiTheme.Surface.PANEL, 0, 0,
                        UiTheme.Text.NORMAL, UiTheme.Text.DISABLED, 24, 24, 14,
                        UiTheme.Metrics.DISABLED_ALPHA)) {
            @Override
            protected void drawableStateChanged() {
                super.drawableStateChanged();
                if (isEnabled() && getBackground() instanceof ShapeDrawable background) {
                    var palette = UiTableStyle.rowPalette(isSelected(), 0);
                    background.setColor(isPressed() ? palette.pressed()
                            : isHovered() ? palette.hover() : palette.normal());
                }
            }
        };
    }

    private static UiIconButton icon(Context context, SvgIconView.Icon icon, String key) {
        UiIconButton button = new UiIconButton(context, new SvgIconView(context, icon, UiTheme.Text.SECONDARY),
                new UiIconButton.Style(0, 0, UiTheme.Control.ICON, UiTheme.Control.BORDER, UiTheme.Control.ICON_BORDER));
        button.setContentDescription(tr(key));
        return button;
    }

    private static String tr(String key) { return Component.translatable(key).getString(); }
}
