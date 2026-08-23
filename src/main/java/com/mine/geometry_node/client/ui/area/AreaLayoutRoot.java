package com.mine.geometry_node.client.ui.area;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionState;
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionStore;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

import java.util.IdentityHashMap;
import java.util.Map;

public final class AreaLayoutRoot extends FrameLayout {
    private static final long SAVE_DELAY_MS = 300L;
    private static final int MAX_LEAVES = 32;

    private final AreaEditorRegistry mEditorRegistry = new AreaEditorRegistry();
    private final Map<AreaNode, View> mNodeViews = new IdentityHashMap<>();
    private AreaNode mRootNode;
    private AreaLeafNode mDragSourceNode;
    private AreaLeafView mDragTargetView;
    private final Runnable mSaveRunnable = this::persistNow;
    private boolean mClosing;
    private boolean mClosed;

    public AreaLayoutRoot(Context context) {
        super(context);
        setBackground(AreaStyle.rect(AreaStyle.COLOR_ROOT));
        int padding = UIUtils.dp2pxInt(AreaStyle.ROOT_PADDING_DP);
        setPadding(padding, padding, padding, padding);
        EditorSessionState restored = EditorSessionStore.INSTANCE.load();
        mRootNode = restoreNode(restored.layout);
        if (mRootNode == null) {
            mRootNode = createDefaultLayout();
        }
        installRootView(createNodeView(mRootNode));
    }

    AreaEditorRegistry editorRegistry() {
        return mEditorRegistry;
    }

    public void close() {
        if (mClosing || mClosed) {
            return;
        }
        mClosing = true;
        removeCallbacks(mSaveRunnable);
        try {
            if (mRootNode != null) {
                mRootNode.dispose();
            }
        } finally {
            try {
                persistNow();
            } finally {
                mClosed = true;
                mClosing = false;
                mNodeViews.clear();
                removeAllViews();
            }
        }
    }

    View createNodeView(AreaNode node) {
        View existingView = mNodeViews.get(node);
        if (existingView != null) {
            return existingView;
        }

        View view;
        if (node instanceof AreaLeafNode leaf) {
            view = new AreaLeafView(getContext(), this, leaf);
        } else if (node instanceof AreaSplitNode split) {
            view = new AreaSplitView(getContext(), this, split);
        } else {
            view = new FrameLayout(getContext());
        }
        mNodeViews.put(node, view);
        return view;
    }

    void splitLeaf(AreaLeafNode leaf, AreaSplitDirection direction) {
        if (leaf == null || direction == null || countLeaves(mRootNode) >= MAX_LEAVES) {
            return;
        }

        AreaSplitNode oldParent = leaf.parent();
        AreaLeafNode newLeaf = new AreaLeafNode(leaf.editorType());
        AreaSplitNode split = new AreaSplitNode(direction, 0.5f, leaf, newLeaf);
        if (oldParent == null) {
            mRootNode = split;
            split.setParent(null);
        } else {
            oldParent.replaceChild(leaf, split);
        }
        replaceNodeView(leaf, split);
        refreshLeafChrome(mRootNode);
        requestSessionSave();
    }

    void closeLeaf(AreaLeafNode leaf) {
        if (leaf == null || countLeaves(mRootNode) <= 1) {
            return;
        }

        AreaSplitNode parent = leaf.parent();
        if (parent == null) {
            return;
        }

        AreaNode sibling = parent.siblingOf(leaf);
        if (sibling == null) {
            return;
        }

        AreaSplitNode grandParent = parent.parent();

        if (grandParent == null) {
            sibling.setParent(null);
            mRootNode = sibling;
        } else {
            grandParent.replaceChild(parent, sibling);
        }

        replaceNodeView(parent, sibling);
        leaf.dispose();
        leaf.setParent(null);
        parent.setParent(null);
        removeNodeView(parent);
        removeNodeView(leaf);
        refreshLeafChrome(mRootNode);
        requestSessionSave();
    }

    boolean canCloseLeaf() {
        return countLeaves(mRootNode) > 1;
    }

    void beginLeafDrag(AreaLeafNode sourceNode, float rawX, float rawY) {
        mDragSourceNode = sourceNode;
        updateLeafDrag(rawX, rawY);
    }

    void updateLeafDrag(float rawX, float rawY) {
        if (mDragSourceNode == null) {
            return;
        }

        AreaLeafView target = findLeafViewAtRaw(rawX, rawY);
        if (target != null && target.node() == mDragSourceNode) {
            target = null;
        }
        setDragTargetView(target);
    }

    void finishLeafDrag(float rawX, float rawY) {
        if (mDragSourceNode == null) {
            return;
        }

        updateLeafDrag(rawX, rawY);
        AreaLeafView targetView = mDragTargetView;
        AreaLeafNode sourceNode = mDragSourceNode;
        cancelLeafDrag();

        if (targetView != null && targetView.node() != null && targetView.node() != sourceNode) {
            AreaLeafView sourceView = leafView(sourceNode);
            AreaLeafNode targetNode = targetView.node();
            if (sourceView != null) {
                sourceView.prepareForContentSwap();
            }
            targetView.prepareForContentSwap();
            sourceNode.swapContentsWith(targetNode);
            if (sourceView != null) {
                sourceView.refreshFromNode();
            }
            targetView.refreshFromNode();
            requestSessionSave();
        }
    }

    void requestSessionSave() {
        if (mClosing || mClosed) {
            return;
        }
        removeCallbacks(mSaveRunnable);
        postDelayed(mSaveRunnable, SAVE_DELAY_MS);
    }

    public void persistNow() {
        if (mClosed) {
            return;
        }
        removeCallbacks(mSaveRunnable);
        EditorSessionState state = new EditorSessionState();
        state.layout = snapshotNode(mRootNode);
        EditorSessionStore.INSTANCE.save(state);
    }

    void cancelLeafDrag() {
        setDragTargetView(null);
        mDragSourceNode = null;
    }

    private void rebuild() {
        cancelLeafDrag();
        mNodeViews.clear();
        removeAllViews();
        installRootView(createNodeView(mRootNode));
    }

    private void installRootView(View view) {
        detachFromParent(view);
        removeAllViews();
        addView(view, matchParentParams());
    }

    private void replaceNodeView(AreaNode oldNode, AreaNode newNode) {
        cancelLeafDrag();

        View oldView = mNodeViews.get(oldNode);
        if (oldView == null || !(oldView.getParent() instanceof ViewGroup parentView)) {
            rebuild();
            return;
        }

        int index = parentView.indexOfChild(oldView);
        ViewGroup.LayoutParams oldParams = oldView.getLayoutParams();
        View newView = createNodeView(newNode);
        detachFromParent(newView);
        if (oldView.getParent() == parentView) {
            parentView.removeView(oldView);
        }

        int safeIndex = Math.max(0, Math.min(index, parentView.getChildCount()));
        parentView.addView(newView, safeIndex, oldParams);
        parentView.requestLayout();
        parentView.invalidate();
    }

    private FrameLayout.LayoutParams matchParentParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private AreaLeafView leafView(AreaLeafNode node) {
        View view = mNodeViews.get(node);
        return view instanceof AreaLeafView leafView ? leafView : null;
    }

    private void refreshLeafChrome(AreaNode node) {
        if (node instanceof AreaLeafNode leaf) {
            AreaLeafView view = leafView(leaf);
            if (view != null) {
                view.refreshChrome();
            }
            return;
        }
        if (node instanceof AreaSplitNode split) {
            refreshLeafChrome(split.first());
            refreshLeafChrome(split.second());
        }
    }

    private void removeNodeView(AreaNode node) {
        View view = mNodeViews.remove(node);
        if (view != null) {
            detachFromParent(view);
        }
    }

    static void detachFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup parent) {
            parent.removeView(view);
        }
    }

    private AreaLeafView findLeafViewAtRaw(float rawX, float rawY) {
        int[] rootLocation = new int[2];
        getLocationOnScreen(rootLocation);
        return findLeafViewAt(this, rawX - rootLocation[0], rawY - rootLocation[1]);
    }

    private AreaLeafView findLeafViewAt(View view, float x, float y) {
        if (view instanceof AreaLeafView leafView) {
            return x >= 0 && y >= 0 && x < leafView.getWidth() && y < leafView.getHeight()
                    ? leafView
                    : null;
        }
        if (!(view instanceof ViewGroup group)) {
            return null;
        }

        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            float childX = x - child.getLeft();
            float childY = y - child.getTop();
            if (childX < 0 || childY < 0 || childX >= child.getWidth() || childY >= child.getHeight()) {
                continue;
            }
            AreaLeafView leaf = findLeafViewAt(child, childX, childY);
            if (leaf != null) {
                return leaf;
            }
        }
        return null;
    }

    private void setDragTargetView(AreaLeafView targetView) {
        if (mDragTargetView == targetView) {
            return;
        }
        if (mDragTargetView != null) {
            mDragTargetView.setDragTargetHighlighted(false);
        }
        mDragTargetView = targetView;
        if (mDragTargetView != null) {
            mDragTargetView.setDragTargetHighlighted(true);
        }
    }

    private static AreaNode createDefaultLayout() {
        AreaLeafNode graph = new AreaLeafNode(AreaEditorType.GRAPH_EDITOR);
        AreaLeafNode assets = new AreaLeafNode(AreaEditorType.ASSET_BROWSER);
        AreaLeafNode console = new AreaLeafNode(AreaEditorType.TERMINAL);
        AreaSplitNode bottom = new AreaSplitNode(AreaSplitDirection.HORIZONTAL, 0.58f, assets, console);
        return new AreaSplitNode(AreaSplitDirection.VERTICAL, 0.72f, graph, bottom);
    }

    private static AreaNode restoreNode(EditorSessionState.AreaState state) {
        if (state == null) {
            return null;
        }
        if ("leaf".equals(state.kind)) {
            return new AreaLeafNode(state);
        }
        if (!"split".equals(state.kind)) {
            return null;
        }
        AreaNode first = restoreNode(state.first);
        AreaNode second = restoreNode(state.second);
        if (first == null || second == null) {
            return null;
        }
        AreaSplitDirection direction = "VERTICAL".equals(state.direction)
                ? AreaSplitDirection.VERTICAL
                : AreaSplitDirection.HORIZONTAL;
        return new AreaSplitNode(direction, state.ratio, first, second);
    }

    private static EditorSessionState.AreaState snapshotNode(AreaNode node) {
        if (node instanceof AreaLeafNode leaf) {
            return leaf.sessionState();
        }
        if (node instanceof AreaSplitNode split) {
            EditorSessionState.AreaState state = new EditorSessionState.AreaState();
            state.kind = "split";
            state.direction = split.direction().name();
            state.ratio = split.ratio();
            state.first = snapshotNode(split.first());
            state.second = snapshotNode(split.second());
            state.graphEditor = null;
            state.assetBrowser = null;
            state.terminal = null;
            return state;
        }
        return null;
    }

    private static int countLeaves(AreaNode node) {
        if (node instanceof AreaLeafNode) {
            return 1;
        }
        if (node instanceof AreaSplitNode split) {
            return countLeaves(split.first()) + countLeaves(split.second());
        }
        return 0;
    }

}
