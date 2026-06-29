package com.mine.geometry_node.client.ui.viewport.node;

import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.text.ShapedText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeLayout {
    public final Map<String, Float> inputPortY = new HashMap<>();
    public final Map<String, Float> outputPortY = new HashMap<>();
    public final Map<String, LabelRun> labelsByPortId = new HashMap<>();
    public final List<LabelRun> labels = new ArrayList<>();

    public ShapedText titleText;
    public float titleX;
    public float titleBaseline;

    public int width;
    public int totalHeight;

    public static class LabelRun {
        public final String portId;
        public final String text;
        public final ShapedText shapedText;
        public final float x;
        public final float baseline;
        public final RectF hitRect;

        public LabelRun(String portId, String text, ShapedText shapedText, float x, float baseline, RectF hitRect) {
            this.portId = portId;
            this.text = text;
            this.shapedText = shapedText;
            this.x = x;
            this.baseline = baseline;
            this.hitRect = hitRect;
        }
    }
}
