package com.mine.geometry_node.client.ui.components.integration.graph.properties.quest;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.tr;

/** Shared sizing and controls for objective and reward cards. */
final class QuestEntryUi {
    private QuestEntryUi() {}

    /** Only changing the inline text discards its rich formatting. */
    static RichTextValue afterInlineEdit(RichTextValue value, CharSequence text) {
        String plain = text.toString();
        return plain.equals(value.plain()) ? value : RichTextValue.plain(plain);
    }

    /** Detached text and caret state; the owning editor still resolves the entry and field. */
    record TextEdit(String text, int selectionStart, int selectionEnd) {
        static TextEdit capture(EditText input) {
            return new TextEdit(input.getText().toString(), input.getSelectionStart(), input.getSelectionEnd());
        }

        void restore(EditText input) {
            input.setText(text);
            int start = Math.clamp(selectionStart, 0, text.length());
            int end = Math.clamp(selectionEnd, start, text.length());
            input.requestFocus();
            input.setSelection(start, end);
        }
    }

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
        TextView title = label(context, tr(labelKey), 10.5f, UiThemes.EDITOR.text().muted());
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        titleLp.leftMargin = UIUtils.dp2pxInt(7);
        option.addView(title, titleLp);
        title.setOnClickListener(v -> toggle.performClick());
        row.addView(option, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(74), UIUtils.dp2pxInt(28)));
    }
}
