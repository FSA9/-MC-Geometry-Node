package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.quest;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.List;

import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.rect;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.tr;

public final class QuestConditionOverviewView extends LinearLayout {
    private static final int COLOR_LABEL = 0xFFA8A8A8;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_MUTED = 0xFF777777;
    private static final int COLOR_ROW = 0xFF292929;

    public QuestConditionOverviewView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setOverview(QuestConditionOverview.EMPTY);
    }

    public void setOverview(QuestConditionOverview overview) {
        QuestConditionOverview value = overview != null ? overview : QuestConditionOverview.EMPTY;
        removeAllViews();
        addSection("geometry_node.graph_properties.quest.visibility_conditions", value.visibility());
        addSection("geometry_node.graph_properties.quest.acceptance_conditions", value.acceptance());
        addSection("geometry_node.graph_properties.quest.completion_conditions", value.completion());
    }

    private void addSection(String titleKey, List<String> texts) {
        TextView title = text(tr(titleKey), 10.5f, COLOR_LABEL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(20));
        if (getChildCount() > 0) titleParams.topMargin = UIUtils.dp2pxInt(6);
        addView(title, titleParams);

        List<String> values = texts != null ? texts : List.of();
        if (values.isEmpty()) {
            addTextRow(tr("geometry_node.graph_properties.quest.condition_none"), COLOR_MUTED);
            return;
        }
        for (String value : values) {
            addTextRow(value, COLOR_TEXT);
        }
    }

    private void addTextRow(String value, int color) {
        TextView row = text(value, 11.0f, color);
        row.setMinHeight(UIUtils.dp2pxInt(28));
        row.setPadding(
                UIUtils.dp2pxInt(8),
                UIUtils.dp2pxInt(5),
                UIUtils.dp2pxInt(8),
                UIUtils.dp2pxInt(5));
        row.setBackground(rect(COLOR_ROW, 3.0f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = UIUtils.dp2pxInt(3);
        addView(row, params);
    }

    private TextView text(String value, float sizeSp, int color) {
        TextView view = new TextView(getContext());
        view.setText(value != null ? value : "");
        view.setTextSize(0, UIUtils.dp2px(sizeSp));
        view.setTextColor(color);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setClickable(false);
        view.setFocusable(false);
        return view;
    }

}
