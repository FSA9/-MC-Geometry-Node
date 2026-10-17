package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.quest;

import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.components.common.VectorIconView;
import com.mine.geometry_node.client.ui.components.menu.UiPopover;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.quest.model.QuestHintType;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.rect;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.tr;

/** Shared explicit display-kind selection for quest objectives and rewards. */
final class QuestHintSelector extends FrameLayout {
    private final TextView value;
    private final List<QuestHintType> options;
    private final Consumer<QuestHintType> onSelected;
    private QuestHintType selected = QuestHintType.NONE;
    private UiPopover menu;

    QuestHintSelector(Context context, boolean allowEntity, Consumer<QuestHintType> onSelected) {
        super(context);
        this.onSelected = onSelected;
        options = allowEntity
                ? List.of(QuestHintType.NONE, QuestHintType.ITEM_STACK, QuestHintType.ENTITY)
                : List.of(QuestHintType.NONE, QuestHintType.ITEM_STACK);
        setBackground(rect(0xFF242424, 3, 1, 0xFF4A4A4A));
        value = label(context, "", 11, 0xFFE3E3E3);
        value.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(32), 0);
        addView(value, new LayoutParams(-1, -1));
        UiIconButton arrow = GraphPropertiesUi.actionIcon(context, VectorIconView.Kind.CHEVRON_DOWN,
                "geometry_node.graph_properties.quest.objective_display");
        LayoutParams arrowLp = new LayoutParams(UIUtils.dp2pxInt(GraphPropertiesUi.ACTION_SIZE_DP),
                UIUtils.dp2pxInt(GraphPropertiesUi.ACTION_SIZE_DP), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        arrowLp.rightMargin = UIUtils.dp2pxInt(1);
        addView(arrow, arrowLp);
        arrow.setOnClickListener(v -> toggleMenu());
        setOnClickListener(v -> toggleMenu());
        setSelected(selected);
    }

    void setSelected(QuestHintType type) {
        selected = type;
        value.setText(optionLabel(type));
    }

    private void toggleMenu() {
        if (menu != null && menu.isOpen()) {
            menu.dismiss();
            return;
        }
        FrameLayout host = menuHost();
        if (host == null || getWidth() <= 0) return;
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        for (QuestHintType type : options) {
            TextView option = label(getContext(), optionLabel(type), 11, 0xFFE3E3E3);
            option.setPadding(UIUtils.dp2pxInt(7), 0, UIUtils.dp2pxInt(7), 0);
            ShapeDrawable background = rect(type == selected ? 0xFF424242 : 0xFF242424, 2, 0, 0);
            option.setBackground(background);
            option.setOnHoverListener((v, event) -> {
                boolean hovered = event.getAction() == MotionEvent.ACTION_HOVER_ENTER
                        || event.getAction() == MotionEvent.ACTION_HOVER_MOVE;
                background.setColor(hovered ? GraphPropertiesUi.ACTION_HOVER
                        : type == selected ? 0xFF424242 : 0xFF242424);
                option.invalidate();
                return true;
            });
            option.setOnClickListener(v -> {
                menu.dismiss();
                onSelected.accept(type);
            });
            content.addView(option, new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(28)));
        }
        int widthDp = Math.max(1, Math.round(getWidth() / UIUtils.dp2px(1)));
        menu = new UiPopover(getContext(), content, widthDp);
        menu.showBelow(this, host);
    }

    private FrameLayout menuHost() {
        View current = this;
        while (current.getParent() instanceof View parent) {
            current = parent;
            if (current instanceof FrameLayout host && !(current instanceof ScrollView)) return host;
        }
        return null;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (menu != null) menu.dismiss();
        super.onDetachedFromWindow();
    }

    private static String optionLabel(QuestHintType type) {
        return tr(switch (type) {
            case NONE -> "geometry_node.graph_properties.quest.hint_none";
            case ITEM_STACK, BLOCK -> "geometry_node.graph_properties.quest.hint_item";
            case ENTITY -> "geometry_node.graph_properties.quest.hint_entity";
        });
    }
}
