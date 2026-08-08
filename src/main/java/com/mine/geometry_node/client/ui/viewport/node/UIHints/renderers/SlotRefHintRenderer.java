package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.VanillaInventoryPicker;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.value.SlotRef;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

public class SlotRefHintRenderer implements UIHintRenderer {
    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 1.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String portId = row.leftPort().id();
        Object value = UIHintValueBinder.getValue(nodeData, row.leftPort());
        SlotRef slotRef = SlotRef.from(value);
        SlotButton button = new SlotButton(context, slotRef != null ? slotRef : SlotRef.DEFAULT);
        button.setOnClickListener(v -> VanillaInventoryPicker.openSlotRef(selected -> {
            button.setSlotRef(selected);
            UIHintValueBinder.commit(editorContext, nodeData, portId, selected.serialize());
        }, button::requestFocus));
        return button;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;
        float inputBoxHeight = UIHintUtils.getStandardInputHeight();
        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0.0f;
        float verticalMargin = (UIConstants.Node.ROW_HEIGHT - inputBoxHeight) / 2.0f;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        int widthPx = UIUtils.dp2pxInt(endX - startX);
        int heightPx = UIUtils.dp2pxInt(inputBoxHeight);
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(widthPx, heightPx);
        } else {
            lp.width = widthPx;
            lp.height = heightPx;
        }
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);
        lp.topMargin = UIUtils.dp2pxInt(currentY + topOffset + verticalMargin);
        view.setLayoutParams(lp);
    }

    private static final class SlotButton extends TextView {
        private SlotRef slotRef;
        private boolean pressed;

        private SlotButton(Context context, SlotRef slotRef) {
            super(context);
            setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            setTextColor(UIConstants.CLR_GRAY_LABEL);
            UIUtils.setLockedTextSize(this, UIConstants.Node.TEXT_SIZE_LABEL);
            setSingleLine(true);
            setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
            setClickable(true);
            setFocusable(true);
            setFocusableInTouchMode(true);

            ShapeDrawable bg = new ShapeDrawable();
            bg.setColor(0xFF252525);
            bg.setCornerRadius(UIUtils.dp2px(2.0f));
            bg.setStroke(UIUtils.dp2pxInt(1), 0xFF333333);
            setBackground(bg);
            setSlotRef(slotRef);
        }

        private void setSlotRef(SlotRef slotRef) {
            this.slotRef = slotRef != null ? slotRef : SlotRef.DEFAULT;
            setText(this.slotRef.displayName());
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return onTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                requestFocus();
                pressed = true;
                setPressed(true);
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                boolean wasPressed = pressed;
                pressed = false;
                setPressed(false);
                if (wasPressed) {
                    performClick();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                setPressed(false);
                return true;
            }
            return true;
        }

        @Override
        public PointerIcon onResolvePointerIcon(MotionEvent event) {
            return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
        }
    }
}
