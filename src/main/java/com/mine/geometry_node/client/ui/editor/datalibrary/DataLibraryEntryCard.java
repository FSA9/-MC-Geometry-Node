package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.UiCheckBox;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** Card presentation on an existing drag surface, without an extra view hierarchy. */
final class DataLibraryEntryCard {
    private final DataLibraryDragSurface surface;
    private final int typeColor;
    private final TextView name;
    private final UiCheckBox check;
    private final SvgIconView disclosure;
    private boolean selected;
    private boolean expanded;

    DataLibraryEntryCard(DataLibraryDragSurface surface, String title, int typeColor,
                         boolean selected, Runnable toggleExpanded, Consumer<Boolean> selectionChanged) {
        this.surface = surface;
        this.typeColor = typeColor;
        Context context = surface.getContext();
        surface.setOrientation(LinearLayout.VERTICAL);
        surface.setPadding(px(2), px(2), px(2), px(2));
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(background((typeColor & 0x00FFFFFF) | 0x28000000, 2, typeColor));
        disclosure = new SvgIconView(context, SvgIconView.Icon.forExpandedState(false), 0xFFD2D2D2);
        disclosure.setClickable(true);
        disclosure.setContentDescription(tr("geometry_node.data_library.expand_entry"));
        disclosure.setOnClickListener(view -> toggleExpanded.run());
        header.addView(disclosure, new LinearLayout.LayoutParams(px(18), px(32)));
        name = UIUtils.createLockedTextView(context, title, 12, typeColor);
        name.setSingleLine(true);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(name, new LinearLayout.LayoutParams(0, px(32), 1));
        check = new UiCheckBox(context);
        check.setContentDescription(tr("geometry_node.data_library.select_entry"));
        check.setTooltipText(tr("geometry_node.data_library.select_entry"));
        check.setOnCheckedChangeListener((button, checked) -> selectionChanged.accept(checked));
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(px(16), px(16));
        checkParams.leftMargin = px(5);
        checkParams.rightMargin = px(5);
        header.addView(check, checkParams);
        surface.addView(header, new LinearLayout.LayoutParams(-1, px(32)));
        this.selected = selected;
        check.setChecked(selected);
        applySelectionBackground();
    }

    DataLibraryDragSurface surface() { return surface; }
    TextView nameView() { return name; }
    boolean isExpanded() { return expanded; }

    void setName(String value) {
        if (!Objects.equals(name.getText().toString(), value)) name.setText(value);
    }

    void setSelected(boolean value) {
        check.setChecked(value);
        if (selected == value) return;
        selected = value;
        applySelectionBackground();
    }

    void setExpandedContent(boolean value, View editor) {
        while (surface.getChildCount() > 1) surface.removeViewAt(1);
        if (value) {
            View divider = new View(surface.getContext());
            divider.setBackground(background(0xFF383838, 0, 0));
            surface.addView(divider, new LinearLayout.LayoutParams(-1, px(1)));
            surface.addView(Objects.requireNonNull(editor),
                    new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        expanded = value;
        disclosure.setIcon(SvgIconView.Icon.forExpandedState(value));
        disclosure.setContentDescription(tr(value
                ? "geometry_node.data_library.collapse_entry" : "geometry_node.data_library.expand_entry"));
    }

    private void applySelectionBackground() {
        surface.setBackground(background(selected ? 0xFF303840 : 0xFF292929, 3,
                selected ? typeColor : 0xFF444444));
    }

    private static ShapeDrawable background(int color, float radius, int border) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radius));
        if (border != 0) drawable.setStroke(Math.max(1, px(1)), border);
        return drawable;
    }

    private static int px(float value) { return UIUtils.dp2pxInt(value); }
    private static String tr(String key) { return Component.translatable(key).getString(); }
}
