package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils; // 引入
import com.mine.geometry_node.client.ui.Viewport.Viewport;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SelectHintRenderer implements UIHintRenderer {

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 1.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String propKey = row.hintParams() != null ? (String) row.hintParams().get(PortMetaKeys.BIND_PROPERTY) : null;
        List<String> resolvedOptions = new ArrayList<>();

        if (row.hintParams() != null) {
            String[] staticOptions = (String[]) row.hintParams().get(PortMetaKeys.OPTIONS);
            if (staticOptions != null && staticOptions.length > 0) {
                resolvedOptions.addAll(List.of(staticOptions));
            } else {
                String dynamicRegistryId = (String) row.hintParams().get(PortMetaKeys.DYNAMIC_REGISTRY_ID);
                if (dynamicRegistryId != null) {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.level != null) resolvedOptions.addAll(RegistryDataManager.getDynamicOptions(dynamicRegistryId, mc.level.registryAccess()));
                }
            }
        }

        Object val = null;
        if (propKey != null) val = nodeData.properties.get(propKey);
        else if (row.leftPort() != null) val = nodeData.inputs.containsKey(row.leftPort().id()) ? nodeData.inputs.get(row.leftPort().id()) : row.leftPort().defaultValue();

        TextView dropdownBtn = new TextView(context);
        String displayVal = val != null ? val.toString() : (resolvedOptions.isEmpty() ? "" : resolvedOptions.get(0));

        dropdownBtn.setText(displayVal + " ▼");
        dropdownBtn.setTextColor(UIConstants.CLR_GRAY_LABEL);
        dropdownBtn.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
        dropdownBtn.setGravity(Gravity.CENTER_VERTICAL);
        dropdownBtn.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);

        ShapeDrawable borderBg = new ShapeDrawable();
        borderBg.setColor(0x05FFFFFF);
        borderBg.setCornerRadius(UIUtils.dp2px(3));
        borderBg.setStroke(UIUtils.dp2pxInt(1), 0xFF555555);
        dropdownBtn.setBackground(borderBg);

        dropdownBtn.setOnClickListener(v -> {
            icyllis.modernui.view.ViewParent parent = v.getParent();
            while (parent != null && !(parent instanceof Viewport)) parent = parent.getParent();

            if (parent instanceof Viewport viewport) {
                int[] btnLoc = new int[2]; v.getLocationOnScreen(btnLoc);
                int[] vpLoc = new int[2]; viewport.getLocationOnScreen(vpLoc);
                float relX = btnLoc[0] - vpLoc[0];
                float relY = btnLoc[1] - vpLoc[1];
                float currentScale = viewport.getCurrentScale();
                float scaledTargetWidth = v.getWidth() * currentScale;

                DropdownSearchMenu menu = new DropdownSearchMenu(context, resolvedOptions, selectedVal -> {
                    dropdownBtn.setText(selectedVal + " ▼");
                    if (editorContext != null) {
                        if (propKey != null) {
                            Object oldVal = nodeData.properties.get(propKey);
                            if (oldVal == null || !selectedVal.equals(oldVal.toString())) editorContext.getCommandManager().execute(new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, selectedVal));
                        } else if (row.leftPort() != null) {
                            String portId = row.leftPort().id();
                            Object oldVal = nodeData.inputs.get(portId);
                            if (oldVal == null || !selectedVal.equals(oldVal.toString())) editorContext.getCommandManager().execute(new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, selectedVal));
                        }
                    } else {
                        if (propKey != null) nodeData.properties.put(propKey, selectedVal);
                        else if (row.leftPort() != null) nodeData.inputs.put(row.leftPort().id(), selectedVal);
                    }
                });
                menu.showAt(relX, relY + (v.getHeight() * currentScale), viewport, scaledTargetWidth, currentScale);
            }
        });
        return dropdownBtn;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        int leftMargin = (row.leftPort() != null) ? (int)(nodeWidth * 0.45f) : UIConstants.Node.LABEL_MARGIN_PORT;
        int rightMargin = (row.rightPort() != null) ? UIConstants.Node.ROW_HEIGHT : UIConstants.Node.LABEL_MARGIN_PORT;
        int targetWidth = nodeWidth - leftMargin - rightMargin;
        if (targetWidth < 10) targetWidth = 10;
        int targetHeight = UIConstants.Node.ROW_HEIGHT - 4;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (lp == null) lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(targetWidth), UIUtils.dp2pxInt(targetHeight));
        else { lp.width = UIUtils.dp2pxInt(targetWidth); lp.height = UIUtils.dp2pxInt(targetHeight); }

        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(leftMargin);
        lp.topMargin = UIUtils.dp2pxInt(currentY + 2);
        view.setLayoutParams(lp);
    }

    private static class DropdownSearchMenu extends FrameLayout {
        private LinearLayout mContentLayout;
        private LinearLayout mListContainer;
        private EditText mSearchBox;
        private final List<String> mOptions;
        private final Consumer<String> mOnSelect;
        private float mCurrentScale = 1.0f;

        public DropdownSearchMenu(Context context, List<String> options, Consumer<String> onSelect) {
            super(context); this.mOptions = options; this.mOnSelect = onSelect;
            initUI(context); renderList(options);
        }

        private void initUI(Context context) {
            this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            this.setOnClickListener(v -> dismiss());

            mContentLayout = new LinearLayout(context);
            mContentLayout.setOrientation(LinearLayout.VERTICAL);
            mContentLayout.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.BG_COLOR, UIConstants.ViewPort.NodeMenu.BORDER_RADIUS));
            mContentLayout.setPadding(UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(4));
            mContentLayout.setOnClickListener(v -> {});

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(220), LayoutParams.WRAP_CONTENT);
            mContentLayout.setLayoutParams(lp);

            mSearchBox = new EditText(context);
            mSearchBox.setHint("Search...");
            mSearchBox.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_SEARCH);
            mSearchBox.setHintTextColor(0xFF666666);
            mSearchBox.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.SEARCH_BG_COLOR, 4));
            mSearchBox.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);

            mSearchBox.addTextChangedListener(new TextWatcher() {
                @Override public void afterTextChanged(Editable s) { performSearch(s.toString()); }
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            });

            LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX));
            searchLp.setMargins(UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(6));
            mContentLayout.addView(mSearchBox, searchLp);

            ScrollView sv = new ScrollView(context);
            mListContainer = new LinearLayout(context);
            mListContainer.setOrientation(LinearLayout.VERTICAL);
            sv.addView(mListContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            mContentLayout.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(250)));
            addView(mContentLayout);
        }

        private void renderList(List<String> items) {
            mListContainer.removeAllViews();
            for (String item : items) {
                TextView tv = new TextView(getContext());
                tv.setText(item);
                float fontSize = UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT * (float)UIConstants.ViewPort.NodeMenu.TEXT_SIZE * mCurrentScale;
                tv.setTextSize(0, fontSize);
                tv.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR);
                tv.setPadding((int)UIUtils.dp2px(12 * mCurrentScale), 0, (int)UIUtils.dp2px(12 * mCurrentScale), 0);
                tv.setGravity(Gravity.CENTER_VERTICAL);

                tv.setOnClickListener(v -> { mOnSelect.accept(item); post(this::dismiss); });
                tv.setOnHoverListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                        tv.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.HOVER_COLOR, 4));
                        tv.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_HOVER);
                    } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                        tv.setBackground(null);
                        tv.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR);
                    }
                    return false;
                });

                int itemHeight = (int)UIUtils.dp2px(UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT * mCurrentScale);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight);
                lp.setMargins(UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(1), UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(1));
                mListContainer.addView(tv, lp);
            }
        }

        private void performSearch(String query) {
            if (query.trim().isEmpty()) { renderList(mOptions); return; }
            String q = query.toLowerCase().trim();
            List<String> filtered = new ArrayList<>();
            for (String opt : mOptions) if (opt.toLowerCase().contains(q)) filtered.add(opt);
            renderList(filtered);
        }

        public void showAt(float x, float y, ViewGroup parent, float targetWidth, float scale) {
            this.mCurrentScale = scale;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
            lp.leftMargin = (int) x;
            lp.topMargin = (int) y;
            lp.width = (int) Math.max(targetWidth, UIUtils.dp2px(150 * scale));

            if (parent != null) {
                if (x + lp.width > parent.getWidth()) lp.leftMargin = (int) (parent.getWidth() - lp.width);
                if (y + UIUtils.dp2px(300 * scale) > parent.getHeight()) lp.topMargin = (int) (parent.getHeight() - UIUtils.dp2px(300 * scale));
            }
            mContentLayout.setLayoutParams(lp);

            float scaledSearchFontSize = UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX * (float)UIConstants.ViewPort.NodeMenu.TEXT_SIZE * scale;
            mSearchBox.setTextSize(0, scaledSearchFontSize);
            LinearLayout.LayoutParams searchLp = (LinearLayout.LayoutParams) mSearchBox.getLayoutParams();
            searchLp.height = (int) UIUtils.dp2px(UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX * scale);
            mSearchBox.setLayoutParams(searchLp);

            renderList(mOptions);
            if (this.getParent() != null) ((ViewGroup) this.getParent()).removeView(this);
            parent.addView(this);

            mSearchBox.post(() -> { mSearchBox.setText(""); mSearchBox.requestFocus(); });
        }

        public void dismiss() { if (getParent() != null) ((ViewGroup) getParent()).removeView(this); }
        private ShapeDrawable createRectDrawable(int color, int radius) { ShapeDrawable d = new ShapeDrawable(); d.setColor(color); d.setCornerRadius(UIUtils.dp2px(radius)); return d; }
    }
}