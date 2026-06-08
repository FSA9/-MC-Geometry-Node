package com.mine.geometry_node.client.ui.viewport.action;

public final class ViewportActionRequest {
    public static final ViewportActionRequest EMPTY = new Builder().build();

    private final Float mScreenX;
    private final Float mScreenY;
    private final Float mUiX;
    private final Float mUiY;
    private final String mTypeId;
    private final String mNodeId;
    private final String mFrameId;
    private final String mPortCategory;
    private final String mPortId;
    private final String mOldName;
    private final String mNewName;
    private final String mTitle;
    private final Integer mColor;
    private final String mComment;

    private ViewportActionRequest(Builder builder) {
        mScreenX = builder.mScreenX;
        mScreenY = builder.mScreenY;
        mUiX = builder.mUiX;
        mUiY = builder.mUiY;
        mTypeId = builder.mTypeId;
        mNodeId = builder.mNodeId;
        mFrameId = builder.mFrameId;
        mPortCategory = builder.mPortCategory;
        mPortId = builder.mPortId;
        mOldName = builder.mOldName;
        mNewName = builder.mNewName;
        mTitle = builder.mTitle;
        mColor = builder.mColor;
        mComment = builder.mComment;
    }

    public Float screenX() { return mScreenX; }
    public Float screenY() { return mScreenY; }
    public Float uiX() { return mUiX; }
    public Float uiY() { return mUiY; }
    public String typeId() { return mTypeId; }
    public String nodeId() { return mNodeId; }
    public String frameId() { return mFrameId; }
    public String portCategory() { return mPortCategory; }
    public String portId() { return mPortId; }
    public String oldName() { return mOldName; }
    public String newName() { return mNewName; }
    public String title() { return mTitle; }
    public Integer color() { return mColor; }
    public String comment() { return mComment; }

    public float screenXOr(float fallback) { return mScreenX != null ? mScreenX : fallback; }
    public float screenYOr(float fallback) { return mScreenY != null ? mScreenY : fallback; }
    public float uiXOr(float fallback) { return mUiX != null ? mUiX : fallback; }
    public float uiYOr(float fallback) { return mUiY != null ? mUiY : fallback; }
    public int colorOr(int fallback) { return mColor != null ? mColor : fallback; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Float mScreenX;
        private Float mScreenY;
        private Float mUiX;
        private Float mUiY;
        private String mTypeId;
        private String mNodeId;
        private String mFrameId;
        private String mPortCategory;
        private String mPortId;
        private String mOldName;
        private String mNewName;
        private String mTitle;
        private Integer mColor;
        private String mComment;

        public Builder screen(float x, float y) {
            mScreenX = x;
            mScreenY = y;
            return this;
        }

        public Builder ui(float x, float y) {
            mUiX = x;
            mUiY = y;
            return this;
        }

        public Builder typeId(String typeId) {
            mTypeId = typeId;
            return this;
        }

        public Builder nodeId(String nodeId) {
            mNodeId = nodeId;
            return this;
        }

        public Builder frameId(String frameId) {
            mFrameId = frameId;
            return this;
        }

        public Builder port(String category, String portId) {
            mPortCategory = category;
            mPortId = portId;
            return this;
        }

        public Builder rename(String oldName, String newName) {
            mOldName = oldName;
            mNewName = newName;
            return this;
        }

        public Builder title(String title) {
            mTitle = title;
            return this;
        }

        public Builder color(int color) {
            mColor = color;
            return this;
        }

        public Builder comment(String comment) {
            mComment = comment;
            return this;
        }

        public ViewportActionRequest build() {
            return new ViewportActionRequest(this);
        }
    }
}
