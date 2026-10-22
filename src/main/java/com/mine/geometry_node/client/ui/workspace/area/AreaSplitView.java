package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.components.base.layout.ResizableDivider;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;

final class AreaSplitView extends LinearLayout {
    private final AreaSplitNode mNode;
    private final AreaLayoutRoot mRoot;

    AreaSplitView(Context context, AreaLayoutRoot root, AreaSplitNode node) {
        super(context);
        mRoot = root;
        mNode = node;
        setOrientation(node.direction() == AreaSplitDirection.HORIZONTAL ? HORIZONTAL : VERTICAL);
        setBackground(AreaStyle.rect(AreaStyle.COLOR_PLATE));

        View firstView = root.createNodeView(node.first());
        View secondView = root.createNodeView(node.second());
        AreaLayoutRoot.detachFromParent(firstView);
        AreaLayoutRoot.detachFromParent(secondView);

        addView(firstView, weightedParams(node.ratio()));
        ResizableDivider.Orientation orientation = toDividerOrientation(node.direction());
        addView(new ResizableDivider(context, orientation, this::handleDividerDrag),
                ResizableDivider.layoutParams(orientation));
        addView(secondView, weightedParams(1.0f - node.ratio()));
    }

    private void handleDividerDrag(float delta) {
        float totalSize = mNode.direction() == AreaSplitDirection.HORIZONTAL ? getWidth() : getHeight();
        if (totalSize <= 0.0f) {
            return;
        }
        mNode.setRatio(mNode.ratio() + delta / totalSize);
        applyWeights();
        mRoot.requestSessionSave();
    }

    private void applyWeights() {
        // Splitting or closing a descendant can replace either direct child. The
        // original view may now belong to a nested split with a different axis.
        applyWeight(getChildAt(0), mNode.ratio());
        applyWeight(getChildAt(2), 1.0f - mNode.ratio());
        requestLayout();
    }

    private static void applyWeight(View pane, float weight) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) pane.getLayoutParams();
        params.weight = weight;
        pane.setLayoutParams(params);
    }

    private LinearLayout.LayoutParams weightedParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                mNode.direction() == AreaSplitDirection.HORIZONTAL ? 0 : ViewGroup.LayoutParams.MATCH_PARENT,
                mNode.direction() == AreaSplitDirection.HORIZONTAL ? ViewGroup.LayoutParams.MATCH_PARENT : 0);
        params.weight = weight;
        return params;
    }

    private static ResizableDivider.Orientation toDividerOrientation(AreaSplitDirection direction) {
        return direction == AreaSplitDirection.VERTICAL
                ? ResizableDivider.Orientation.VERTICAL
                : ResizableDivider.Orientation.HORIZONTAL;
    }
}
