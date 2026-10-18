package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.quest;

import com.mine.geometry_node.client.ui.components.input.UiCheckBox;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.utils.GraphPropertiesUi.tr;

/** Shared sizing and controls for objective and reward cards. */
final class QuestEntryUi {
    private QuestEntryUi() {}

    static LinearLayout.LayoutParams detailParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32));
        lp.topMargin = UIUtils.dp2pxInt(4);
        return lp;
    }

    static void addOptionLabel(Context context, LinearLayout row, UiCheckBox toggle, String labelKey) {
        LinearLayout option = new LinearLayout(context);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.addView(toggle, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(14)));
        TextView title = label(context, tr(labelKey), 10.5f, UiTheme.Text.MUTED);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        titleLp.leftMargin = UIUtils.dp2pxInt(7);
        option.addView(title, titleLp);
        title.setOnClickListener(v -> toggle.performClick());
        row.addView(option, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(74), UIUtils.dp2pxInt(28)));
    }
}
