package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.frame.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.menu.FrameMenu;
import com.mine.geometry_node.client.ui.viewport.menu.GroupNodeMenu;
import com.mine.geometry_node.client.ui.viewport.menu.PortMenu;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;

final class ContextMenuRouter {
    private final InteractionContext mContext;

    ContextMenuRouter(InteractionContext context) {
        this.mContext = context;
    }

    RouteResult route(float uiX, float uiY, float screenX, float screenY) {
        NodeVisualAdapter targetNode = mContext.findNodeAt(uiX, uiY);
        if (targetNode != null) {
            float localX = uiX - targetNode.getUiX();
            float localY = uiY - targetNode.getUiY();
            String clickedLabelPortId = targetNode.hitTestLabel(UIUtils.dp2px(localX), UIUtils.dp2px(localY));
            if (clickedLabelPortId != null) {
                PortMenu.show(mContext, targetNode, clickedLabelPortId, screenX, screenY);
                return RouteResult.handled(false);
            }
            if (targetNode.getNodeData().isGroupNode() && localY >= 0 && localY <= UIConstants.Node.HEADER_HEIGHT) {
                mContext.clearSelection();
                mContext.addToSelection(targetNode);
                GroupNodeMenu.show(mContext, targetNode, screenX, screenY);
                return RouteResult.handled(true);
            }
        }

        if (targetNode == null) {
            FrameVisualAdapter targetFrame = mContext.findFrameAt(uiX, uiY);
            if (targetFrame != null) {
                mContext.clearSelection();
                mContext.addToSelection(targetFrame);
                FrameMenu.show(mContext, targetFrame, screenX, screenY);
                return RouteResult.handled(true);
            }
        }

        mContext.showMenu(screenX, screenY);
        return RouteResult.handled(true);
    }

    static final class RouteResult {
        final boolean handled;
        final boolean invalidate;

        private RouteResult(boolean handled, boolean invalidate) {
            this.handled = handled;
            this.invalidate = invalidate;
        }

        static RouteResult handled(boolean invalidate) {
            return new RouteResult(true, invalidate);
        }
    }
}
