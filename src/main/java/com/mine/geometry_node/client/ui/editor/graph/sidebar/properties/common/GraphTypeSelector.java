package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.common;

import com.mine.geometry_node.client.ui.components.common.VectorIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
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
    private static final int COLOR_MUTED = 0xFF777777;
    private static final int COLOR_BUTTON = 0xFF4A4A4A;
    private static final int COLOR_BUTTON_HOVER = 0xFF5A5A5A;
    private static final int COLOR_SELECTED = 0xFF424242;

    private final TextView mValue;
    private FrameLayout mDropdown;
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

        FrameLayout menuButton = new FrameLayout(context);
        menuButton.setBackground(rect(COLOR_BUTTON, 2.0f, 0, 0));
        menuButton.setOnClickListener(v -> toggleMenu());
        menuButton.setOnHoverListener((v, event) -> {
            boolean hovered = event.getAction() == MotionEvent.ACTION_HOVER_ENTER
                    || event.getAction() == MotionEvent.ACTION_HOVER_MOVE;
            menuButton.setBackground(rect(
                    hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON, 2.0f, 0, 0));
            return true;
        });
        VectorIconView menuIcon = new VectorIconView(
                context, VectorIconView.Kind.CHEVRON_DOWN, COLOR_MUTED);
        menuIcon.setClickable(false);
        menuButton.addView(menuIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                UIUtils.dp2pxInt(28),
                ViewGroup.LayoutParams.MATCH_PARENT);
        buttonParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        buttonParams.setMargins(0, UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(2));
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
        if (mDropdown.getParent() instanceof ViewGroup parent) parent.removeView(mDropdown);
        mDropdown = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissMenu();
        super.onDetachedFromWindow();
    }

    private void toggleMenu() {
        if (mDropdown != null) {
            dismissMenu();
        } else {
            showMenu();
        }
    }

    private void showMenu() {
        FrameLayout host = findMenuHost();
        if (host == null || getWidth() <= 0) return;

        FrameLayout overlay = new FrameLayout(getContext());
        overlay.setClickable(true);
        overlay.setBackground(rect(0x00000000, 0.0f, 0, 0));
        overlay.setOnHoverListener((v, event) -> true);
        overlay.setOnGenericMotionListener((v, event) -> true);
        overlay.setOnClickListener(v -> dismissMenu());
        host.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout menu = new LinearLayout(getContext());
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(2),
                UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(2));
        menu.setBackground(rect(COLOR_INPUT, 3.0f, 1, COLOR_INPUT_BORDER));
        menu.setClickable(true);
        menu.setOnClickListener(v -> {
        });
        for (GraphType type : GraphTypeRegistry.INSTANCE.authorable()) {
            menu.addView(createOption(type), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(28)));
        }

        int[] rootLocation = new int[2];
        int[] anchorLocation = new int[2];
        host.getLocationOnScreen(rootLocation);
        getLocationOnScreen(anchorLocation);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                getWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        menuParams.gravity = Gravity.TOP | Gravity.LEFT;
        menuParams.setMargins(
                anchorLocation[0] - rootLocation[0],
                anchorLocation[1] - rootLocation[1] + getHeight(),
                0,
                0);
        overlay.addView(menu, menuParams);
        mDropdown = overlay;
    }

    private TextView createOption(GraphType type) {
        TextView option = label(getContext(), graphTypeLabel(type.id()), 11.0f, COLOR_TEXT);
        option.setPadding(UIUtils.dp2pxInt(7), 0, UIUtils.dp2pxInt(7), 0);
        option.setBackground(rect(
                type.id().equals(mSelectedId) ? COLOR_SELECTED : COLOR_INPUT,
                2.0f, 0, 0));
        option.setClickable(true);
        option.setOnHoverListener((v, event) -> {
            boolean hovered = event.getAction() == MotionEvent.ACTION_HOVER_ENTER
                    || event.getAction() == MotionEvent.ACTION_HOVER_MOVE;
            option.setBackground(rect(
                    hovered ? COLOR_BUTTON_HOVER
                            : type.id().equals(mSelectedId) ? COLOR_SELECTED : COLOR_INPUT,
                    2.0f, 0, 0));
            return true;
        });
        option.setOnClickListener(v -> select(type.id()));
        return option;
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
