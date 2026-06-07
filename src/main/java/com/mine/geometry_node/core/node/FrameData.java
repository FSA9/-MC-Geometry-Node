package com.mine.geometry_node.core.node;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class FrameData {
    public static final int DEFAULT_COLOR = 0xFF556677;

    public transient String id;

    @SerializedName("title")
    public String title = "New Frame";

    @SerializedName("tags")
    public List<String> tags = new ArrayList<>();

    @SerializedName("UI_pos")
    public float[] uiPos = new float[2];

    @SerializedName("UI_size")
    public float[] uiSize = new float[]{400, 300};

    @SerializedName("color")
    public int color = DEFAULT_COLOR;

    @SerializedName("parent_frame")
    public String parentFrame;

    public FrameData() {}

    public FrameData(String id, float x, float y) {
        this.id = id;
        this.uiPos[0] = x;
        this.uiPos[1] = y;
    }

    public void setPosition(float x, float y) {
        this.uiPos[0] = x;
        this.uiPos[1] = y;
    }

    public void setSize(float w, float h) {
        this.uiSize[0] = w;
        this.uiSize[1] = h;
    }
}
