package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.components.common.ResizableDivider;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;

final class AreaSplitView extends LinearLayout {
    private final AreaSplitNode mNode;
    private final LinearLayout.LayoutParams mFirstParams;
    private final LinearLayout.LayoutParams mSecondParams;
    private final View mFirstView;
    private final View mSecondView;
    private final AreaLayoutRoot mRoot;

    AreaSplitView(Context context, AreaLayoutRoot root, AreaSplitNode node) {
        super(context);
        mRoot = root;
        mNode = node;
        setOrientation(node.direction() == AreaSplitDirection.HORIZONTAL ? HORIZONTAL : VERTICAL);
        setBackground(AreaStyle.rect(AreaStyle.COLOR_PLATE));

        mFirstView = root.createNodeView(node.first());
        mSecondView = root.createNodeView(node.second());
        AreaLayoutRoot.detachFromParent(mFirstView);
        AreaLayoutRoot.detachFromParent(mSecondView);
        mFirstParams = weightedParams(node.ratio());
        mSecondParams = weightedParams(1.0f - node.ratio());

        addView(mFirstView, mFirstParams);
        ResizableDivider.Orientation orientation = toDividerOrientation(node.direction());
        addView(new ResizableDivider(context, orientation, this::handleDividerDrag),
                ResizableDivider.layoutParams(orientation));
        addView(mSecondView, mSecondParams);
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
        mFirstParams.weight = mNode.ratio();
        mSecondParams.weight = 1.0f - mNode.ratio();
        mFirstView.setLayoutParams(mFirstParams);
        mSecondView.setLayoutParams(mSecondParams);
        requestLayout();
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
