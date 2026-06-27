package com.mine.geometry_node.client.ui.top_window.graph_editor;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.PanelSplitter;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.ViewportPanel;
import com.mine.geometry_node.client.ui.window.IToolWindow;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RelativeLayout;
import icyllis.modernui.widget.TextView;

public class GraphEditorWindow extends LinearLayout implements IToolWindow {
    public GraphEditorWindow(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setBackground(createColorDrawable(UIConstants.MainUI.BG_ROOT));

        View leftPanel = createPanel(context, "Outliner", UIConstants.MainUI.BG_OUTLINER);
        addView(leftPanel, createWeightParams(UIConstants.MainUI.WEIGHT_LEFT));

        addView(PanelSplitter.create(context, true));

        ViewportPanel centerPanel = new ViewportPanel(context);
        addView(centerPanel, createWeightParams(UIConstants.MainUI.WEIGHT_CENTER));

        addView(PanelSplitter.create(context, true));

        View rightPanel = createPanel(context, "Properties", UIConstants.MainUI.BG_PROPERTIES);
        addView(rightPanel, createWeightParams(UIConstants.MainUI.WEIGHT_RIGHT));
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
    }

    @Override
    public void onHide() {
    }

    private RelativeLayout createPanel(Context context, String title, int colorHex) {
        RelativeLayout panel = new RelativeLayout(context);
        panel.setBackground(createColorDrawable(colorHex));

        TextView textView = new TextView(context);
        textView.setText(title);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, UIUtils.dp2px(UIConstants.MainUI.TEXT_SIZE));
        textView.setTextColor(UIConstants.MainUI.TEXT_COLOR);

        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        panel.addView(textView, textParams);
        return panel;
    }

    private LinearLayout.LayoutParams createWeightParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.weight = weight;
        return params;
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        return drawable;
    }
}
