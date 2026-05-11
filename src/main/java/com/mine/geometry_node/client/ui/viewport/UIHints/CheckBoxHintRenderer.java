package com.mine.geometry_node.client.ui.viewport.UIHints;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

public class CheckBoxHintRenderer implements UIHintRenderer {

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 0.0f; // CheckBox 通常与 Label 同行，不需要额外行
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String portId = row.leftPort().id();
        Object val = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();

        boolean initialCheck = String.valueOf(val).equalsIgnoreCase("true");

        View cb = new View(context) {
            private final Paint mPaint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                boolean isChecked = getTag() != null && (Boolean) getTag();
                float w = getWidth();
                float h = getHeight();
                float r = w * 0f;
                mPaint.setAntiAlias(true);

                float strokeWidth = Math.max(1.0f, w * 0.08f);
                float offset = strokeWidth / 2.0f;

                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(isChecked ? 0xFF3B82F6 : 0xFF252525);
                canvas.drawRoundRect(offset, offset, w - offset, h - offset, r, (int) r, mPaint);

                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(strokeWidth);
                mPaint.setColor(0xFF555555);
                canvas.drawRoundRect(offset, offset, w - offset, h - offset, r, (int) r, mPaint);

                if (isChecked) {
                    mPaint.setColor(0xFFFFFFFF);
                    mPaint.setStrokeWidth(Math.max(1.5f, w * 0.12f));
                    mPaint.setStrokeCap(Paint.Cap.ROUND);
                    mPaint.setStrokeJoin(Paint.Join.ROUND);

                    canvas.drawLine(w * 0.25f, h * 0.5f, w * 0.45f, h * 0.7f, mPaint);
                    canvas.drawLine(w * 0.45f, h * 0.7f, w * 0.75f, h * 0.3f, mPaint);
                }
            }
        };

        cb.setTag(initialCheck);
        cb.setOnClickListener(v -> {
            boolean current = cb.getTag() != null && (Boolean) cb.getTag();
            boolean isChecked = !current;
            cb.setTag(isChecked);
            cb.invalidate();

            if (editorContext != null) {
                Object oldVal = nodeData.inputs.get(portId);
                editorContext.getCommandManager().execute(new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, isChecked));
            } else {
                nodeData.inputs.put(portId, isChecked);
            }
        });
        return cb;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        int cbSizePx = UIUtils.dp2pxInt(UIConstants.Node.CHECKBOX_DEFAULT_WIDTH);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cbSizePx, cbSizePx);
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.LABEL_MARGIN_PORT);

        int rowHeightPx = UIUtils.dp2pxInt(UIConstants.Node.ROW_HEIGHT);
        lp.topMargin = UIUtils.dp2pxInt(currentY) + (rowHeightPx - cbSizePx) / 2;

        view.setLayoutParams(lp);
    }
}