package com.mine.geometry_node.client.ui.viewport.frame;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.viewport.node.NodeUiMetrics;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.document.NodeData;

/**
 * Pure frame auto-bounds calculator shared by committed graph data and drag previews.
 */
public final class FrameBoundsCalculator {
    public static final float CONTENT_PADDING = 30f;
    public static final float HEADER_HEIGHT = UIFrame.FRAME_HEADER_H;
    public static final float FALLBACK_NODE_HEIGHT = 100f;

    private FrameBoundsCalculator() {}

    public static Result computeCommittedBounds(
            String frameId,
            Iterable<NodeData> nodes,
            Iterable<FrameData> frames,
            Result out
    ) {
        Result result = prepare(out);

        for (NodeData node : nodes) {
            if (frameId.equals(node.parentFrame)) {
                result.include(
                        node.uiPos[0],
                        node.uiPos[1],
                        NodeUiMetrics.width(node),
                        NodeUiMetrics.height(node)
                );
            }
        }

        for (FrameData frame : frames) {
            if (!frameId.equals(frame.id) && frameId.equals(frame.parentFrame)) {
                result.include(
                        frame.uiPos[0],
                        frame.uiPos[1],
                        positiveSize(frame.uiSize, 0, 0f),
                        positiveSize(frame.uiSize, 1, 0f)
                );
            }
        }

        result.finish();
        return result;
    }

    public static Result computePreviewBounds(
            String frameId,
            Iterable<? extends NodeVisualAdapter> nodes,
            Iterable<? extends FrameVisualAdapter> frames,
            Result out
    ) {
        Result result = prepare(out);

        for (NodeVisualAdapter node : nodes) {
            if (frameId.equals(node.getParentFrameId())) {
                result.include(
                        node.getUiX(),
                        node.getUiY(),
                        positive(node.getVisualWidthDp(), UIConstants.Node.NODE_WIDTH),
                        positive(node.getVisualHeightDp(), FALLBACK_NODE_HEIGHT)
                );
            }
        }

        for (FrameVisualAdapter frame : frames) {
            if (!frameId.equals(frame.getFrameId()) && frameId.equals(frame.getParentFrameId())) {
                result.include(
                        frame.getUiX(),
                        frame.getUiY(),
                        positive(frame.getVisualWidthDp(), 0f),
                        positive(frame.getVisualHeightDp(), 0f)
                );
            }
        }

        result.finish();
        return result;
    }

    private static Result prepare(Result out) {
        Result result = out != null ? out : new Result();
        result.reset();
        return result;
    }

    private static float positiveSize(float[] size, int index, float fallback) {
        return size != null && size.length > index ? positive(size[index], fallback) : fallback;
    }

    private static float positive(float value, float fallback) {
        return value > 0f ? value : fallback;
    }

    public static final class Result {
        private float mMinX;
        private float mMinY;
        private float mMaxX;
        private float mMaxY;

        private boolean mHasChildren;
        private float mX;
        private float mY;
        private float mWidth;
        private float mHeight;

        private void reset() {
            mMinX = Float.MAX_VALUE;
            mMinY = Float.MAX_VALUE;
            mMaxX = -Float.MAX_VALUE;
            mMaxY = -Float.MAX_VALUE;
            mHasChildren = false;
            mX = 0f;
            mY = 0f;
            mWidth = 0f;
            mHeight = 0f;
        }

        private void include(float x, float y, float w, float h) {
            mHasChildren = true;
            mMinX = Math.min(mMinX, x);
            mMinY = Math.min(mMinY, y);
            mMaxX = Math.max(mMaxX, x + w);
            mMaxY = Math.max(mMaxY, y + h);
        }

        private void finish() {
            if (!mHasChildren) return;

            mX = mMinX - CONTENT_PADDING;
            mY = mMinY - CONTENT_PADDING - HEADER_HEIGHT;
            mWidth = (mMaxX - mMinX) + 2 * CONTENT_PADDING;
            mHeight = (mMaxY - mMinY) + 2 * CONTENT_PADDING + HEADER_HEIGHT;
        }

        public boolean hasChildren() {
            return mHasChildren;
        }

        public float x() {
            return mX;
        }

        public float y() {
            return mY;
        }

        public float width() {
            return mWidth;
        }

        public float height() {
            return mHeight;
        }
    }
}
