package com.mine.geometry_node.client.ui.components.integration.graph.properties.quest;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.ui.components.composite.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.quest.model.QuestHintType;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import java.util.List;
import java.util.function.Consumer;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.tr;

/** Shared explicit display-kind selection for quest objectives and rewards. */
final class QuestHintSelector extends FrameLayout {
    private final TextView value;
    private final UiIconButton arrow;
    private final List<QuestHintType> options;
    private final Consumer<QuestHintType> onSelected;
    private QuestHintType selected = QuestHintType.NONE;
    private UiPopover menu;
    private long menuGeneration;

    QuestHintSelector(Context context, boolean allowEntity, Consumer<QuestHintType> onSelected) {
        super(context);
        this.onSelected = onSelected;
        options = allowEntity
                ? List.of(QuestHintType.NONE, QuestHintType.ITEM_STACK, QuestHintType.ENTITY)
                : List.of(QuestHintType.NONE, QuestHintType.ITEM_STACK);
        UiMenuItems.decorate(this, UiThemes.EDITOR.control().input(), 3, UiThemes.EDITOR);
        ((ShapeDrawable) getBackground()).setStroke(UIUtils.dp2pxInt(1), new ColorStateList(new int[][]{
                {-R.attr.state_enabled}, {R.attr.state_focused}, {R.attr.state_hovered}, {}
        }, new int[]{UiThemes.EDITOR.control().inputBorder(), UiThemes.EDITOR.border().focus(),
                UiThemes.EDITOR.border().focus(), UiThemes.EDITOR.control().inputBorder()}));
        value = label(context, "", 11, UiThemes.EDITOR.text().normal());
        value.setTextColor(UiMenuItems.textColors(UiThemes.EDITOR.text().normal()));
        value.setDuplicateParentStateEnabled(true);
        value.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(32), 0);
        addView(value, new LayoutParams(-1, -1));
        arrow = QuestEntryUi.action(context, SvgIconView.Icon.CHEVRON_DOWN,
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

    @Override public boolean performClick() {
        return isEnabled() && super.performClick();
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (arrow != null) arrow.setEnabled(enabled);
        if (!enabled) dismissMenu();
    }

    private void toggleMenu() {
        if (!isEnabled()) return;
        if (menu != null && menu.isOpen()) {
            dismissMenu();
            return;
        }
        FrameLayout host = menuHost();
        if (host == null || getWidth() <= 0) return;
        dismissMenu();
        long generation = menuGeneration;
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        for (QuestHintType type : options) {
            TextView option = new TextView(getContext()) {
                @Override public boolean performClick() { return isEnabled() && super.performClick(); }
            };
            option.setText(optionLabel(type));
            option.setTextSize(0, UIUtils.dp2px(11));
            option.setTextColor(UiMenuItems.textColors(UiThemes.EDITOR.text().normal()));
            option.setSingleLine(true);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(UIUtils.dp2pxInt(7), 0, UIUtils.dp2pxInt(7), 0);
            UiMenuItems.decorate(option, UiThemes.EDITOR.surface().popup(), 2, UiThemes.EDITOR);
            option.setSelected(type == selected);
            option.setOnClickListener(v -> {
                if (generation != menuGeneration || !isEnabled() || menu == null || !menu.isOpen()) return;
                dismissMenu();
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
        dismissMenu();
        super.onDetachedFromWindow();
    }

    private void dismissMenu() {
        menuGeneration++;
        if (menu != null) menu.dismiss();
        menu = null;
    }

    private static String optionLabel(QuestHintType type) {
        return tr(switch (type) {
            case NONE -> "geometry_node.graph_properties.quest.hint_none";
            case ITEM_STACK, BLOCK -> "geometry_node.graph_properties.quest.hint_item";
            case ENTITY -> "geometry_node.graph_properties.quest.hint_entity";
        });
    }
}
