package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.common;

import com.mine.geometry_node.client.ui.components.base.icon.VectorIconView;
import com.mine.geometry_node.client.ui.components.composite.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.function.Consumer;

import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.rect;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.tr;

/**
 * Graph-type dropdown used by the graph-properties panel.
 */
public final class GraphTypeSelector extends FrameLayout {
    private static final int COLOR_INPUT = 0xFF242424;
    private static final int COLOR_INPUT_BORDER = 0xFF4A4A4A;
    private static final int COLOR_TEXT = 0xFFE3E3E3;

    private final TextView mValue;
    private UiPopover mDropdown;
    private Consumer<String> mOnSelected;
    private String mSelectedId = "";

    public GraphTypeSelector(Context context) {
        super(context);
        setBackground(rect(COLOR_INPUT, 3.0f, 1, COLOR_INPUT_BORDER));

        mValue = label(context, "", 11.0f, COLOR_TEXT);
        mValue.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(32), 0);
        addView(mValue, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        UiIconButton menuButton = GraphPropertiesUi.actionIcon(context, VectorIconView.Kind.CHEVRON_DOWN,
                "geometry_node.graph_properties.type");
        menuButton.setOnClickListener(v -> toggleMenu());
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(GraphPropertiesUi.ACTION_SIZE_DP),
                UIUtils.dp2pxInt(GraphPropertiesUi.ACTION_SIZE_DP));
        buttonParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        buttonParams.rightMargin = UIUtils.dp2pxInt(1);
        addView(menuButton, buttonParams);
    }

    public void setOnSelected(Consumer<String> listener) {
        mOnSelected = listener;
    }

    public String selectedId() {
        return mSelectedId;
    }

    public void setSelectedId(String graphTypeId) {
        mSelectedId = GraphType.normalizeId(graphTypeId);
        mValue.setText(graphTypeLabel(mSelectedId));
    }

    public void dismissMenu() {
        if (mDropdown == null) return;
        mDropdown.dismiss();
        mDropdown = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissMenu();
        super.onDetachedFromWindow();
    }

    private void toggleMenu() {
        if (mDropdown != null && mDropdown.isOpen()) {
            dismissMenu();
        } else {
            showMenu();
        }
    }

    private void showMenu() {
        FrameLayout host = findMenuHost();
        if (host == null || getWidth() <= 0) return;

        dismissMenu();
        LinearLayout menu = new LinearLayout(getContext());
        menu.setOrientation(LinearLayout.VERTICAL);
        for (GraphType type : GraphTypeRegistry.INSTANCE.authorable()) {
            menu.addView(createOption(type), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(28)));
        }

        // Width is already in project-scaled pixels; the popover adds its symmetric inset.
        mDropdown = UiPopover.forContentWidth(getContext(), menu, getWidth(), 0);
        mDropdown.showBelow(this, host);
    }

    private TextView createOption(GraphType type) {
        return UiMenuItems.row(getContext(), graphTypeLabel(type.id()),
                type.id().equals(mSelectedId), true, () -> {
                    if (mDropdown != null && mDropdown.isOpen()) select(type.id());
                });
    }

    private void select(String graphTypeId) {
        String normalizedId = GraphType.normalizeId(graphTypeId);
        if (normalizedId.equals(mSelectedId)) {
            dismissMenu();
            return;
        }
        setSelectedId(normalizedId);
        dismissMenu();
        if (mOnSelected != null) mOnSelected.accept(normalizedId);
    }

    private FrameLayout findMenuHost() {
        View current = this;
        while (current.getParent() instanceof View parent) {
            current = parent;
            if (current instanceof FrameLayout frameLayout && !(current instanceof ScrollView)) {
                return frameLayout;
            }
        }
        return null;
    }

    private static String graphTypeLabel(String graphTypeId) {
        GraphType type = GraphTypeRegistry.INSTANCE.get(graphTypeId);
        if (type != null) return tr(type.translationKey());
        String rawId = GraphType.normalizeId(graphTypeId);
        return rawId.isEmpty()
                ? tr("geometry_node.graph_properties.kind.unknown")
                : tr("geometry_node.graph_properties.kind.unknown") + ": " + rawId;
    }

}
