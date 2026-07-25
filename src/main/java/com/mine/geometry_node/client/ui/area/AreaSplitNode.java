package com.mine.geometry_node.client.ui.area;

final class AreaSplitNode extends AreaNode {
    private static final float MIN_RATIO = 0.12f;
    private static final float MAX_RATIO = 0.88f;

    private final AreaSplitDirection mDirection;
    private AreaNode mFirst;
    private AreaNode mSecond;
    private float mRatio;

    AreaSplitNode(AreaSplitDirection direction, float ratio, AreaNode first, AreaNode second) {
        mDirection = direction;
        mRatio = clampRatio(ratio);
        setFirst(first);
        setSecond(second);
    }

    AreaSplitDirection direction() {
        return mDirection;
    }

    AreaNode first() {
        return mFirst;
    }

    AreaNode second() {
        return mSecond;
    }

    float ratio() {
        return mRatio;
    }

    void setRatio(float ratio) {
        mRatio = clampRatio(ratio);
    }

    AreaNode siblingOf(AreaNode child) {
        if (child == mFirst) {
            return mSecond;
        }
        if (child == mSecond) {
            return mFirst;
        }
        return null;
    }

    boolean replaceChild(AreaNode oldChild, AreaNode newChild) {
        if (oldChild == mFirst) {
            setFirst(newChild);
            return true;
        }
        if (oldChild == mSecond) {
            setSecond(newChild);
            return true;
        }
        return false;
    }

    private void setFirst(AreaNode node) {
        mFirst = node;
        if (node != null) {
            node.setParent(this);
        }
    }

    private void setSecond(AreaNode node) {
        mSecond = node;
        if (node != null) {
            node.setParent(this);
        }
    }

    @Override
    void dispose() {
        if (mFirst != null) {
            mFirst.dispose();
        }
        if (mSecond != null) {
            mSecond.dispose();
        }
    }

    private static float clampRatio(float value) {
        if (!Float.isFinite(value)) {
            return 0.5f;
        }
        return Math.max(MIN_RATIO, Math.min(MAX_RATIO, value));
    }
}
