package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

public class CheckBoxHintRenderer implements UIHintRenderer {
    private static final int COLOR_BG_OFF = 0xFF252525;
    private static final int COLOR_BG_ON = 0xFF3D6EA8;
    private static final int COLOR_BORDER_OFF = 0xFF3A3A3A;
    private static final int COLOR_BORDER_ON = 0xFF6FA2DD;
    private static final int COLOR_CHECK = 0xFFFFFFFF;

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 0.0f; // CheckBox 通常与 Label 同行，不需要额外行
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String portId = row.leftPort().id();
        Object val = UIHintValueBinder.getValue(nodeData, row.leftPort());

        boolean initialCheck = String.valueOf(val).equalsIgnoreCase("true");

        View cb = new View(context) {
            private final Paint mPaint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                boolean isChecked = getTag() != null && (Boolean) getTag();
                float w = getWidth();
                float h = getHeight();
                int r = UIUtils.dp2pxInt(2.0f);
                mPaint.setAntiAlias(true);

                float strokeWidth = UIUtils.dp2px(1.0f);
                float offset = strokeWidth / 2.0f;

                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(isChecked ? COLOR_BG_ON : COLOR_BG_OFF);
                canvas.drawRoundRect(offset, offset, w - offset, h - offset, r, r, mPaint);

                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(strokeWidth);
                mPaint.setColor(isChecked ? COLOR_BORDER_ON : COLOR_BORDER_OFF);
                canvas.drawRoundRect(offset, offset, w - offset, h - offset, r, r, mPaint);

                if (isChecked) {
                    mPaint.setColor(COLOR_CHECK);
                    mPaint.setStrokeWidth(UIUtils.dp2px(1.6f));
                    mPaint.setStrokeCap(Paint.Cap.ROUND);
                    mPaint.setStrokeJoin(Paint.Join.ROUND);

                    canvas.drawLine(w * 0.27f, h * 0.52f, w * 0.43f, h * 0.68f, mPaint);
                    canvas.drawLine(w * 0.43f, h * 0.68f, w * 0.74f, h * 0.32f, mPaint);
                }
            }
        };

        cb.setTag(initialCheck);
        cb.setOnClickListener(v -> {
            boolean current = cb.getTag() != null && (Boolean) cb.getTag();
            boolean isChecked = !current;
            cb.setTag(isChecked);
            cb.invalidate();

            UIHintValueBinder.commit(editorContext, nodeData, portId, isChecked);
        });
        return cb;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        int cbSizePx = UIUtils.dp2pxInt(UIHintUtils.getStandardInputHeight());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cbSizePx, cbSizePx);
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(UIConstants.Node.LABEL_MARGIN_PORT);

        float verticalMargin = (UIConstants.Node.ROW_HEIGHT - UIHintUtils.getStandardInputHeight()) / 2.0f;
        lp.topMargin = UIUtils.dp2pxInt(currentY + verticalMargin);

        view.setLayoutParams(lp);
    }
}
