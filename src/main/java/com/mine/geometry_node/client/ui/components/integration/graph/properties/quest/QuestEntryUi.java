package com.mine.geometry_node.client.ui.components.integration.graph.properties.quest;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.RichTextValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.ViewParent;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import java.util.function.IntConsumer;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.label;
import static com.mine.geometry_node.client.ui.components.integration.graph.properties.utils.GraphPropertiesUi.tr;
import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.rect;

/** Shared sizing and controls for objective and reward cards. */
final class QuestEntryUi {
    private QuestEntryUi() {}

    static UiIconButton action(Context context, SvgIconView.Icon icon, String tooltipKey) {
        return new UiIconButton(context,
                new SvgIconView(context, icon, UiThemes.EDITOR.text().secondary()),
                UiIconButton.Style.forPreset(UiThemes.EDITOR, UiIconButton.Preset.INLINE).withSize(0, 0, 14))
                .tooltip(tr(tooltipKey));
    }

    static UiIconButton.Style disclosureStyle() {
        return UiIconButton.Style.forPreset(UiThemes.EDITOR, UiIconButton.Preset.INLINE).withSize(0, 0, 11);
    }

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
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UIUtils.dp2pxInt(12);
        return lp;
    }

    static LinearLayout inlineRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        return row;
    }

    static LinearLayout.LayoutParams inlineFieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(32), 1);
        params.leftMargin = UIUtils.dp2pxInt(6);
        return params;
    }

    static void addQuantityAndCounter(LinearLayout details, LinearLayout quantity, LinearLayout counter) {
        LinearLayout row = inlineRow(details.getContext());
        row.addView(quantity, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(110), UIUtils.dp2pxInt(32)));
        LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(32), 1);
        counterParams.leftMargin = UIUtils.dp2pxInt(12);
        row.addView(counter, counterParams);
        details.addView(row, detailParams());
    }

    static TextView entryNumber(Context context) {
        return label(context, "", 10, UiThemes.EDITOR.text().hint());
    }

    static void decorateCard(LinearLayout root, LinearLayout header, LinearLayout details) {
        root.setPadding(UIUtils.dp2pxInt(1), UIUtils.dp2pxInt(1),
                UIUtils.dp2pxInt(1), UIUtils.dp2pxInt(1));
        root.setBackground(rect(UiThemes.EDITOR.surface().panel(), 4, 1,
                UiThemes.EDITOR.border().subtle()));
        header.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8),
                UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(8));
        header.setBackground(rect(UiThemes.EDITOR.surface().sidebar(), 0, 0, 0));
        details.setPadding(UIUtils.dp2pxInt(12), 0,
                UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(12));
    }

    /** The handle owns its pointer; release reorders once so editable rows keep their state. */
    static UiIconButton reorderHandle(Context context, LinearLayout rows, View row, IntConsumer move) {
        UiIconButton handle = new UiIconButton(context,
                new SvgIconView(context, SvgIconView.Icon.DRAG_HANDLE, UiThemes.EDITOR.text().hint()),
                UiIconButton.Style.forPreset(UiThemes.EDITOR, UiIconButton.Preset.INLINE).withSize(0, 0, 11));
        handle.setTooltipText(tr("geometry_node.graph_properties.quest.drag_sort"));
        rows.setClipChildren(false);
        class ReorderGesture implements View.OnTouchListener, View.OnAttachStateChangeListener {
            private ViewParent pointerParent;
            private float startY;
            private float oldZ;
            private boolean tracking;
            private boolean dragging;

            private void finish() {
                tracking = false;
                dragging = false;
                row.setTranslationY(0);
                row.setZ(oldZ);
                row.setAlpha(1);
                if (pointerParent != null) pointerParent.requestDisallowInterceptTouchEvent(false);
                pointerParent = null;
            }

            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) { if (tracking) finish(); }

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        if (row.getParent() != rows || !view.isEnabled()) return false;
                        startY = event.getRawY();
                        oldZ = row.getZ();
                        tracking = true;
                        pointerParent = view.getParent();
                        if (pointerParent != null) pointerParent.requestDisallowInterceptTouchEvent(true);
                        view.requestFocus();
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        if (!tracking) return false;
                        float delta = event.getRawY() - startY;
                        if (Math.abs(delta) > ViewConfiguration.get(context).getScaledTouchSlop()) dragging = true;
                        if (dragging) {
                            row.setTranslationY(delta);
                            row.setZ(oldZ + 1);
                            row.setAlpha(0.7f);
                        }
                    }
                    case MotionEvent.ACTION_UP -> {
                        if (!tracking) return false;
                        int from = rows.indexOfChild(row);
                        int to = from;
                        if (dragging && from >= 0) {
                            float center = row.getTop() + row.getHeight() / 2f + event.getRawY() - startY;
                            for (int i = 0; i < rows.getChildCount(); i++) {
                                View sibling = rows.getChildAt(i);
                                float middle = sibling.getTop() + sibling.getHeight() / 2f;
                                if (i < from && center < middle) to = Math.min(to, i);
                                if (i > from && center > middle) to = Math.max(to, i);
                            }
                        }
                        finish();
                        if (from >= 0 && to != from) move.accept(to - from);
                        else view.performClick();
                    }
                    case MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> finish();
                }
                return true;
            }
        }
        ReorderGesture gesture = new ReorderGesture();
        handle.setOnTouchListener(gesture);
        handle.addOnAttachStateChangeListener(gesture);
        handle.setOnKeyListener((view, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || (key != KeyEvent.KEY_UP && key != KeyEvent.KEY_DOWN)) return false;
            move.accept(key == KeyEvent.KEY_UP ? -1 : 1);
            return true;
        });
        return handle;
    }

    static LinearLayout.LayoutParams handleParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(18), UIUtils.dp2pxInt(26));
        params.rightMargin = UIUtils.dp2pxInt(4);
        return params;
    }

    static void addOptionLabel(Context context, LinearLayout row, UiCheckBox toggle, String labelKey) {
        LinearLayout option = new LinearLayout(context);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.addView(toggle, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(14), UIUtils.dp2pxInt(14)));
        TextView title = label(context, tr(labelKey), 10.5f, UiThemes.EDITOR.text().muted());
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        titleLp.leftMargin = UIUtils.dp2pxInt(7);
        option.addView(title, titleLp);
        title.setOnClickListener(v -> toggle.performClick());
        row.addView(option, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(32)));
    }

    /** Quest-local disclosure heading; collapsing does not remove or recreate editable rows. */
    static final class SectionHeader extends LinearLayout {
        private final TextView count;
        private final UiIconButton add;
        private final View body;
        private final SvgIconView arrow;
        private boolean expanded = true;

        SectionHeader(Context context, String titleKey, String addTooltipKey, View body, Runnable addAction) {
            super(context);
            this.body = body;
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0, UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(8));
            LinearLayout disclosure = new LinearLayout(context);
            disclosure.setGravity(Gravity.CENTER_VERTICAL);
            arrow = new SvgIconView(context, SvgIconView.Icon.forExpandedState(true), UiThemes.EDITOR.text().muted());
            disclosure.addView(arrow, new LayoutParams(UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(12)));
            TextView title = label(context, tr(titleKey), 11, UiThemes.EDITOR.text().normal());
            LayoutParams titleLp = new LayoutParams(LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(28));
            titleLp.leftMargin = UIUtils.dp2pxInt(6);
            disclosure.addView(title, titleLp);
            disclosure.setOnClickListener(v -> setExpanded(!expanded));
            disclosure.setTooltipText(tr(titleKey));
            addView(disclosure, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            count = label(context, "0", 10, UiThemes.EDITOR.text().muted());
            LayoutParams countLp = new LayoutParams(0, UIUtils.dp2pxInt(28), 1);
            countLp.leftMargin = UIUtils.dp2pxInt(8);
            addView(count, countLp);
            add = UiIconButton.createThemed(context, UiThemes.EDITOR, SvgIconView.Icon.PLUS,
                    tr(addTooltipKey), UiIconButton.Preset.INLINE);
            add.setTag(addTooltipKey);
            add.setOnClickListener(v -> {setExpanded(true); addAction.run();});
            addView(add, GraphPropertiesUi.actionParams());
        }

        void setExpanded(boolean value) {
            expanded = value;
            body.setVisibility(value ? View.VISIBLE : View.GONE);
            arrow.setIcon(SvgIconView.Icon.forExpandedState(value));
        }

        void setCount(int value) { count.setText(Integer.toString(value)); }
        void setAddEnabled(boolean value) { add.setEnabled(value); }
    }
}
