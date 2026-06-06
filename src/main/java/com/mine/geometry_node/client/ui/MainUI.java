package com.mine.geometry_node.client.ui;

import com.mine.geometry_node.client.ui.bottom_window.BottomToolWindowManager;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserPanel;
import com.mine.geometry_node.client.ui.viewport.ViewportPanel;
import com.mine.geometry_node.client.ui.utils.PanelSplitter;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.NodeRegistry;
import icyllis.modernui.ModernUI;
import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RelativeLayout;
import icyllis.modernui.widget.TextView;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;

public class MainUI extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = getContext();

        // 密度初始化
        float BASE_HEIGHT = 1080.0f;
        float physicalHeight;

        if ("true".equals(System.getProperty("gn.standalone"))) {
            physicalHeight = 1080.0f;
        } else {
//            physicalHeight = net.minecraft.client.Minecraft.getInstance().getWindow().getHeight();
        }

//        UIConstants.mDensity = physicalHeight / BASE_HEIGHT;

        UIConstants.mDensity = context.getResources().getDisplayMetrics().density;

        FrameLayout rootFrame = new FrameLayout(context);
        LinearLayout rootLayout = createRootLayout(context);
        rootFrame.addView(rootLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setupHeader(context, rootLayout);
        setupMiddleSection(context, rootLayout);
        setupBottomSection(context, rootLayout);

        return rootFrame;
    }

    private LinearLayout createRootLayout(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createColorDrawable(UIConstants.MainUI.BG_ROOT));
        return root;
    }

    private void setupHeader(Context context, LinearLayout root) {
        RelativeLayout header = createPanel(context, "Header / Menu", UIConstants.MainUI.BG_HEADER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(UIConstants.MainUI.HEIGHT_HEADER)
        );
        root.addView(header, params);
    }

    private void setupMiddleSection(Context context, LinearLayout root) {
        LinearLayout middleContainer = new LinearLayout(context);
        middleContainer.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        middleParams.weight = 1.0f;

        View leftPanel = createPanel(context, "Outliner", UIConstants.MainUI.BG_OUTLINER);
        middleContainer.addView(leftPanel, createWeightParams(UIConstants.MainUI.WEIGHT_LEFT));

        middleContainer.addView(PanelSplitter.create(context, true));

        ViewportPanel centerPanel = new ViewportPanel(context);
        middleContainer.addView(centerPanel, createWeightParams(UIConstants.MainUI.WEIGHT_CENTER));

        middleContainer.addView(PanelSplitter.create(context, true));

        View rightPanel = createPanel(context, "Properties", UIConstants.MainUI.BG_PROPERTIES);
        middleContainer.addView(rightPanel, createWeightParams(UIConstants.MainUI.WEIGHT_RIGHT));

        root.addView(middleContainer, middleParams);
    }

    private void setupBottomSection(Context context, LinearLayout root) {
        BottomToolWindowManager bottomPanel = new BottomToolWindowManager(context);

        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0
        );
        bottomParams.weight = 0.3f;

        root.addView(PanelSplitter.create(context, false));
        root.addView(bottomPanel, bottomParams);
    }

    private RelativeLayout createPanel(Context context, String title, int colorHex) {
        RelativeLayout panel = new RelativeLayout(context);
        panel.setBackground(createColorDrawable(colorHex));

        // 【修改点】使用 COMPLEX_UNIT_PX 锁定字体大小
        TextView textView = new TextView(context);
        textView.setText(title);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(UIConstants.MainUI.TEXT_SIZE));
        textView.setTextColor(UIConstants.MainUI.TEXT_COLOR);

        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        panel.addView(textView, textParams);
        return panel;
    }

    private LinearLayout.LayoutParams createWeightParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        params.weight = weight;
        return params;
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        return drawable;
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("gn.standalone", "true");
        Configurator.setRootLevel(Level.DEBUG);

        com.mine.geometry_node.client.ui.persistence.config.ConfigManager.INSTANCE.initOrLoad();
        NodeRegistry.INSTANCE.init();

        try (ModernUI app = new ModernUI()) {
            app.run(new MainUI());
        }
        AudioManager.getInstance().close();
        System.gc();
    }
}
