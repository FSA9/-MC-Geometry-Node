package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.client.ui.UIConstants;
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

        // 解析选项
        if (row.hintParams() != null) {
            String[] staticOptions = (String[]) row.hintParams().get(PortMetaKeys.OPTIONS);
            if (staticOptions != null && staticOptions.length > 0) {
                resolvedOptions.addAll(List.of(staticOptions));
            } else {
                String dynamicRegistryId = (String) row.hintParams().get(PortMetaKeys.DYNAMIC_REGISTRY_ID);
                if (dynamicRegistryId != null) {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.level != null) {
                        resolvedOptions.addAll(RegistryDataManager.getDynamicOptions(dynamicRegistryId, mc.level.registryAccess()));
                    }
                }
            }
        }

        Object val = null;
        if (propKey != null) {
            val = nodeData.properties.get(propKey);
        } else if (row.leftPort() != null) {
            val = nodeData.inputs.containsKey(row.leftPort().id()) ? nodeData.inputs.get(row.leftPort().id()) : row.leftPort().defaultValue();
        }

        // --- 使用伪造的按钮替代 Spinner ---
        TextView dropdownBtn = new TextView(context);
        String displayVal = val != null ? val.toString() : (resolvedOptions.isEmpty() ? "" : resolvedOptions.get(0));

        // 加上一个小倒三角表示下拉
        dropdownBtn.setText(displayVal + " ▼");
        dropdownBtn.setTextColor(UIConstants.CLR_GRAY_LABEL);
        dropdownBtn.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
        dropdownBtn.setGravity(Gravity.CENTER_VERTICAL);
        dropdownBtn.setPadding(8, 0, 8, 0);

        ShapeDrawable borderBg = new ShapeDrawable();
        borderBg.setColor(0x05FFFFFF);
        borderBg.setCornerRadius(3);
        borderBg.setStroke(1, 0xFF555555);
        dropdownBtn.setBackground(borderBg);

        // 点击按钮唤起带搜索的弹窗
        dropdownBtn.setOnClickListener(v -> {
            // 向上寻找 Viewport
            icyllis.modernui.view.ViewParent parent = v.getParent();
            while (parent != null && !(parent instanceof Viewport)) {
                parent = parent.getParent();
            }

            if (parent instanceof Viewport viewport) {
                // 计算按钮相对于 Viewport 的绝对坐标
                float absX = 0;
                float absY = 0;
                icyllis.modernui.view.View current = v;
                while (current != null && current != viewport) {
                    absX += current.getLeft() + current.getTranslationX();
                    absY += current.getTop() + current.getTranslationY();
                    current = (icyllis.modernui.view.View) current.getParent();
                }

                // 实例化内部类菜单
                DropdownSearchMenu menu = new DropdownSearchMenu(context, resolvedOptions, selectedVal -> {
                    dropdownBtn.setText(selectedVal + " ▼"); // 更新UI

                    // 执行数据变更命令
                    if (editorContext != null) {
                        if (propKey != null) {
                            Object oldVal = nodeData.properties.get(propKey);
                            if (oldVal == null || !selectedVal.equals(oldVal.toString())) {
                                CmdChangeProperty cmd = new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, selectedVal);
                                editorContext.getCommandManager().execute(cmd);
                            }
                        } else if (row.leftPort() != null) {
                            String portId = row.leftPort().id();
                            Object oldVal = nodeData.inputs.get(portId);
                            if (oldVal == null || !selectedVal.equals(oldVal.toString())) {
                                CmdChangeInputValue cmd = new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, selectedVal);
                                editorContext.getCommandManager().execute(cmd);
                            }
                        }
                    } else {
                        if (propKey != null) nodeData.properties.put(propKey, selectedVal);
                        else if (row.leftPort() != null) nodeData.inputs.put(row.leftPort().id(), selectedVal);
                    }
                });

                // 弹窗在按钮正下方显示
                menu.showAt(absX, absY + v.getHeight(), viewport);
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
        if (lp == null) lp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
        else {
            lp.width = targetWidth;
            lp.height = targetHeight;
        }

        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = leftMargin;
        lp.topMargin = (int) currentY + 2;
        view.setLayoutParams(lp);
    }

    // ==========================================
    // 专属私有内部类：带搜索的下拉菜单
    // 仅仅为 SelectHintRenderer 服务，不再暴露到外部
    // ==========================================
    private static class DropdownSearchMenu extends FrameLayout {
        private LinearLayout mContentLayout;
        private LinearLayout mListContainer;
        private EditText mSearchBox;

        private final List<String> mOptions;
        private final Consumer<String> mOnSelect;

        public DropdownSearchMenu(Context context, List<String> options, Consumer<String> onSelect) {
            super(context);
            this.mOptions = options;
            this.mOnSelect = onSelect;
            initUI(context);
            renderList(options);
        }

        private void initUI(Context context) {
            this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            this.setOnClickListener(v -> dismiss()); // 点击遮罩关闭

            mContentLayout = new LinearLayout(context);
            mContentLayout.setOrientation(LinearLayout.VERTICAL);
            mContentLayout.setBackground(createRectDrawable(
                    UIConstants.ViewPort.NodeMenu.BG_COLOR,
                    UIConstants.ViewPort.NodeMenu.BORDER_RADIUS));
            mContentLayout.setPadding(4, 4, 4, 4);
            mContentLayout.setOnClickListener(v -> {}); // 拦截点击

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(220, LayoutParams.WRAP_CONTENT);
            mContentLayout.setLayoutParams(lp);

            // 搜索框
            mSearchBox = new EditText(context);
            mSearchBox.setHint("Search...");
            float searchFontSize = UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX * (float)UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
            mSearchBox.setTextSize(0, searchFontSize);
            mSearchBox.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_SEARCH);
            mSearchBox.setHintTextColor(0xFF666666);
            mSearchBox.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.SEARCH_BG_COLOR, 4));
            mSearchBox.setPadding(10, 0, 10, 0);

            mSearchBox.addTextChangedListener(new TextWatcher() {
                @Override public void afterTextChanged(Editable s) { performSearch(s.toString()); }
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            });

            LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UIConstants.ViewPort.NodeMenu.HEIGHT_SEARCH_BOX);
            searchLp.setMargins(4, 4, 4, 6);
            mContentLayout.addView(mSearchBox, searchLp);

            // 滚动列表区
            ScrollView sv = new ScrollView(context);
            mListContainer = new LinearLayout(context);
            mListContainer.setOrientation(LinearLayout.VERTICAL);
            sv.addView(mListContainer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            mContentLayout.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 250));
            addView(mContentLayout);
        }

        private void renderList(List<String> items) {
            mListContainer.removeAllViews();
            for (String item : items) {
                TextView tv = new TextView(getContext());
                tv.setText(item);
                float fontSize = UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT * (float)UIConstants.ViewPort.NodeMenu.TEXT_SIZE;
                tv.setTextSize(0, fontSize);
                tv.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR);
                tv.setPadding(12, 0, 12, 0);
                tv.setGravity(Gravity.CENTER_VERTICAL);

                tv.setOnClickListener(v -> {
                    mOnSelect.accept(item);
                    post(this::dismiss);
                });

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

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, UIConstants.ViewPort.NodeMenu.ITEM_HEIGHT);
                lp.setMargins(2, 1, 2, 1);
                mListContainer.addView(tv, lp);
            }
        }

        private void performSearch(String query) {
            if (query.trim().isEmpty()) {
                renderList(mOptions);
                return;
            }
            String q = query.toLowerCase().trim();
            List<String> filtered = new ArrayList<>();
            for (String opt : mOptions) {
                if (opt.toLowerCase().contains(q)) filtered.add(opt);
            }
            renderList(filtered);
        }

        public void showAt(float x, float y, ViewGroup parent) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
            lp.leftMargin = (int) x;
            lp.topMargin = (int) y;

            if (parent != null) {
                if (x + 220 > parent.getWidth()) lp.leftMargin = (int) (parent.getWidth() - 220);
                if (y + 300 > parent.getHeight()) lp.topMargin = (int) (parent.getHeight() - 300);
            }
            mContentLayout.setLayoutParams(lp);

            if (this.getParent() != null) ((ViewGroup) this.getParent()).removeView(this);
            parent.addView(this);

            mSearchBox.post(() -> {
                mSearchBox.setText("");
                mSearchBox.requestFocus();
            });
        }

        public void dismiss() {
            if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        }

        private ShapeDrawable createRectDrawable(int color, int radius) {
            ShapeDrawable d = new ShapeDrawable();
            d.setColor(color);
            d.setCornerRadius(radius);
            return d;
        }
    }
}