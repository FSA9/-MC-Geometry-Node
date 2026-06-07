package com.mine.geometry_node.client.ui.viewport.node;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.HintRendererFactory;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintRenderer;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;

import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextShaper;

public class NodeLayoutEngine {
    private final TextPaint mHeaderPaint = new TextPaint();
    private final TextPaint mLabelPaint = new TextPaint();
    private final FontMetricsInt mHeaderMetrics = new FontMetricsInt();
    private final FontMetricsInt mLabelMetrics = new FontMetricsInt();

    public NodeLayoutEngine() {
        mHeaderPaint.setTextSize(UIUtils.dp2px(UIConstants.Node.TEXT_SIZE_HEADER));
        mHeaderPaint.setTextAntiAlias(true);
        mLabelPaint.setTextSize(UIUtils.dp2px(UIConstants.Node.TEXT_SIZE_LABEL));
        mLabelPaint.setTextAntiAlias(true);
    }

    public TextPaint getHeaderPaint() {
        return mHeaderPaint;
    }

    public TextPaint getLabelPaint() {
        return mLabelPaint;
    }

    public NodeLayout build(NodeData nodeData, NodeDef nodeDef) {
        NodeLayout layout = new NodeLayout();

        mHeaderPaint.getFontMetricsInt(mHeaderMetrics);
        mLabelPaint.getFontMetricsInt(mLabelMetrics);

        String title = nodeDef.displayName().getString();
        layout.titleText = shape(title, mHeaderPaint);
        layout.titleX = (UIUtils.dp2px(UIConstants.Node.NODE_WIDTH) - layout.titleText.getAdvance()) / 2.0f;
        layout.titleBaseline = centeredBaseline(0, UIConstants.Node.HEADER_HEIGHT, mHeaderMetrics);

        float currentY = UIConstants.Node.HEADER_HEIGHT;
        for (PortRow row : nodeDef.rows()) {
            float rowHeight = calculateRowHeight(nodeData, row);
            float portCenterY = currentY + UIConstants.Node.ROW_HEIGHT / 2.0f;

            if (row.leftPort() != null) {
                PortDef port = row.leftPort();
                if (!port.hidePin()) {
                    layout.inputPortY.put(port.id(), portCenterY);
                }
                addLabel(layout, nodeData, port, true, row.uiHint(), currentY);
            }

            if (row.rightPort() != null) {
                PortDef port = row.rightPort();
                layout.outputPortY.put(port.id(), portCenterY);
                addLabel(layout, nodeData, port, false, row.uiHint(), currentY);
            }

            currentY += rowHeight;
        }

        boolean isInputDynamic = nodeDef.getMeta(com.mine.geometry_node.core.node.meta.SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();
        boolean isOutputDynamic = nodeDef.getMeta(com.mine.geometry_node.core.node.meta.SchemaKeys.MAX_DYNAMIC_OUTPUT).isPresent();
        boolean isGroupVirtualDynamic = nodeData.isGroupInputNode() || nodeData.isGroupOutputNode();
        if (isInputDynamic || isOutputDynamic || isGroupVirtualDynamic) {
            currentY += UIConstants.Node.ROW_HEIGHT;
        }

        layout.totalHeight = (int) currentY;
        if (nodeData.uiSize == null) {
            nodeData.uiSize = new float[2];
        }
        nodeData.uiSize[0] = UIConstants.Node.NODE_WIDTH;
        nodeData.uiSize[1] = layout.totalHeight;

        return layout;
    }

    public float calculateRowHeight(NodeData nodeData, PortRow row) {
        float height = UIConstants.Node.ROW_HEIGHT;
        if (row.uiHint() == null) return height;
        if (!nodeData.isInputConnected(row.leftPort() != null ? row.leftPort().id() : "")) {
            UIHintRenderer renderer = HintRendererFactory.getRenderer(row.uiHint());
            float extraRows = renderer != null ? renderer.getRequiredExtraRows(row) : 0.0f;
            height = UIConstants.Node.ROW_HEIGHT * (row.leftPort() != null || row.rightPort() != null ? 1.0f + extraRows : Math.max(1.0f, extraRows));
        }
        return height;
    }

    private void addLabel(NodeLayout layout, NodeData nodeData, PortDef port, boolean isLeft, UIHint hint, float currentY) {
        String category = getPortCategory(port, isLeft);
        String defaultName = port.displayName().getString();
        String effectiveName = nodeData.getEffectivePortName(category, port.id(), defaultName);
        ShapedText shapedText = shape(effectiveName, mLabelPaint);

        float leftDp;
        float rightDp;
        if (isLeft) {
            int leftMargin = UIConstants.Node.LABEL_MARGIN_PORT;
            if (hint == UIHint.CHECKBOX && !nodeData.isInputConnected(port.id())) {
                leftMargin = UIConstants.Node.LABEL_MARGIN_PORT + UIConstants.Node.CHECKBOX_DEFAULT_WIDTH + UIConstants.Node.MARGIN_CHECKBOX_GAP;
            }
            leftDp = leftMargin;
            rightDp = Math.min(UIConstants.Node.NODE_WIDTH - UIConstants.Node.LABEL_MARGIN_PORT, leftDp + UIUtils.px2dp(shapedText.getAdvance()));
        } else {
            rightDp = UIConstants.Node.NODE_WIDTH - UIConstants.Node.LABEL_MARGIN_PORT;
            leftDp = Math.max(UIConstants.Node.LABEL_MARGIN_PORT, rightDp - UIUtils.px2dp(shapedText.getAdvance()));
        }

        float xPx = isLeft ? UIUtils.dp2px(leftDp) : UIUtils.dp2px(rightDp) - shapedText.getAdvance();
        float baselinePx = centeredBaseline(currentY, UIConstants.Node.ROW_HEIGHT, mLabelMetrics);
        RectF hitRect = new RectF(UIUtils.dp2px(leftDp), UIUtils.dp2px(currentY), UIUtils.dp2px(rightDp), UIUtils.dp2px(currentY + UIConstants.Node.ROW_HEIGHT));

        NodeLayout.LabelRun label = new NodeLayout.LabelRun(port.id(), effectiveName, shapedText, xPx, baselinePx, hitRect);
        layout.labels.add(label);
        layout.labelsByPortId.put(port.id(), label);
    }

    private String getPortCategory(PortDef port, boolean isLeft) {
        boolean isExec = port.type() == PortType.EXECUTION;
        if (isLeft) {
            return isExec ? "exec_inputs" : "inputs";
        }
        return isExec ? "exec_outputs" : "outputs";
    }

    private ShapedText shape(String text, TextPaint paint) {
        String safeText = text == null ? "" : text;
        return TextShaper.shapeText(safeText, 0, safeText.length(), TextDirectionHeuristics.FIRSTSTRONG_LTR, paint);
    }

    private float centeredBaseline(float topDp, float heightDp, FontMetricsInt metrics) {
        float centerPx = UIUtils.dp2px(topDp + heightDp / 2.0f);
        return centerPx - (metrics.ascent + metrics.descent) / 2.0f;
    }
}
