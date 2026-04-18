package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;
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
import icyllis.modernui.view.*;
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

        // 【核心修复1】明确指定为纯像素单位，防止系统 SP 缩放导致文字撑爆容器
        dropdownBtn.setTextSize(0, UIUtils.dp2px(UIConstants.Node.TEXT_SIZE_LABEL));
        // 【核心修复2】强制单行排版，修正基线漂移问题
        dropdownBtn.setSingleLine(true);
        // 明确声明水平靠左，垂直居中
        dropdownBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        dropdownBtn.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);

        ShapeDrawable borderBg = new ShapeDrawable();
        borderBg.setColor(0x05FFFFFF);
        borderBg.setCornerRadius(UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().node.cornerRadius));
        borderBg.setStroke(UIUtils.dp2pxInt(1), 0xFF555555);
        dropdownBtn.setBackground(borderBg);

        dropdownBtn.setOnClickListener(v -> {
            icyllis.modernui.view.ViewParent parent = v.getParent();
            while (parent != null && !(parent instanceof Viewport)) parent = parent.getParent();

            if (parent instanceof Viewport viewport) {
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

                // 【核心修改】直接把按钮本身 (v) 交给菜单，让菜单自己去实时追踪它的坐标
                menu.showAt(v, viewport);
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
        int targetHeight = UIConstants.Node.ROW_HEIGHT - 2;

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
        private ScrollView mScrollView;
        private final List<String> mOptions;
        private final List<String> mFilteredOptions;
        private final Consumer<String> mOnSelect;

        private float mCurrentScale = 1.0f;
        private float mLastRenderedScale = -1.0f;

        private View mAnchor;
        private Viewport mViewport;
        private boolean mIsTracking = false;

        public DropdownSearchMenu(Context context, List<String> options, Consumer<String> onSelect) {
            super(context);
            this.mOptions = options;
            this.mFilteredOptions = new ArrayList<>(options);
            this.mOnSelect = onSelect;
            initUI(context);
        }

        private void initUI(Context context) {
            this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            this.setOnClickListener(v -> dismiss());

            mContentLayout = new LinearLayout(context);
            mContentLayout.setOrientation(LinearLayout.VERTICAL);
            mContentLayout.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.BG_COLOR, UIConstants.ViewPort.NodeMenu.BORDER_RADIUS));
            mContentLayout.setOnClickListener(v -> {});

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mContentLayout.setLayoutParams(lp);

            mSearchBox = new EditText(context);
            mSearchBox.setHint("Search...");
            mSearchBox.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_SEARCH);
            mSearchBox.setHintTextColor(0xFF666666);
            mSearchBox.setBackground(createRectDrawable(UIConstants.ViewPort.NodeMenu.SEARCH_BG_COLOR, (int) ConfigManager.INSTANCE.getConfig().node.cornerRadius));

            mContentLayout.addView(mSearchBox);

            mScrollView = new ScrollView(context);
            mListContainer = new LinearLayout(context);
            mListContainer.setOrientation(LinearLayout.VERTICAL);
            mScrollView.addView(mListContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            mContentLayout.addView(mScrollView);
            addView(mContentLayout);

            mSearchBox.addTextChangedListener(new TextWatcher() {
                @Override public void afterTextChanged(Editable s) { performSearch(s.toString()); }
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            });
        }

        private void renderList() {
            mListContainer.removeAllViews();
            for (String item : mFilteredOptions) {
                TextView tv = new TextView(getContext());
                tv.setText(item);

                float fontSize = UIConstants.Node.TEXT_SIZE_LABEL * mCurrentScale;
                tv.setTextSize(0, UIUtils.dp2px(fontSize));
                tv.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR);

                int padH = (int)UIUtils.dp2px(8 * mCurrentScale);
                int padV = (int)UIUtils.dp2px(2 * mCurrentScale);
                tv.setPadding(padH, padV, padH, padV);
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

                int itemHeight = (int)UIUtils.dp2px(20 * mCurrentScale);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight);
                lp.setMargins(UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(1), UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(1));
                mListContainer.addView(tv, lp);
            }
        }

        private void performSearch(String query) {
            String q = query.toLowerCase().trim();
            mFilteredOptions.clear();
            if (q.isEmpty()) {
                mFilteredOptions.addAll(mOptions);
            } else {
                for (String opt : mOptions) if (opt.toLowerCase().contains(q)) mFilteredOptions.add(opt);
            }
            renderList();
        }

        public void showAt(View anchor, Viewport viewport) {
            this.mAnchor = anchor;
            this.mViewport = viewport;

            // 初次强制刷新排版
            updatePosition();

            if (this.getParent() != null) ((ViewGroup) this.getParent()).removeView(this);
            viewport.addView(this);

            mSearchBox.post(() -> { mSearchBox.setText(""); mSearchBox.requestFocus(); });

            // 启动实时追踪循环
            if (!mIsTracking) {
                mIsTracking = true;
                post(mTrackTask);
            }
        }

        // 每帧追踪锚点位置的任务
        private final Runnable mTrackTask = new Runnable() {
            @Override
            public void run() {
                if (getParent() == null) {
                    mIsTracking = false;
                    return;
                }
                updatePosition();
                post(this);
            }
        };

        private void updatePosition() {
            if (mAnchor == null || mViewport == null) return;

            float newScale = mViewport.getCurrentScale();
            this.mCurrentScale = newScale;

            // 1. 如果检测到缩放比例变化，动态刷新所有 UI 尺寸 (包含容器高度)
            if (Math.abs(mLastRenderedScale - mCurrentScale) > 0.001f) {
                mContentLayout.setPadding(
                        (int)UIUtils.dp2px(4 * mCurrentScale),
                        (int)UIUtils.dp2px(4 * mCurrentScale),
                        (int)UIUtils.dp2px(4 * mCurrentScale),
                        (int)UIUtils.dp2px(4 * mCurrentScale)
                );

                float scaledSearchFontSize = UIConstants.Node.TEXT_SIZE_LABEL * mCurrentScale;
                mSearchBox.setTextSize(0, UIUtils.dp2px(scaledSearchFontSize));
                mSearchBox.setPadding((int)UIUtils.dp2px(10 * mCurrentScale), 0, (int)UIUtils.dp2px(10 * mCurrentScale), 0);

                LinearLayout.LayoutParams searchLp = (LinearLayout.LayoutParams) mSearchBox.getLayoutParams();
                searchLp.height = (int) UIUtils.dp2px(24 * mCurrentScale);
                searchLp.setMargins(
                        (int)UIUtils.dp2px(4 * mCurrentScale),
                        (int)UIUtils.dp2px(4 * mCurrentScale),
                        (int)UIUtils.dp2px(4 * mCurrentScale),
                        (int)UIUtils.dp2px(6 * mCurrentScale)
                );
                mSearchBox.setLayoutParams(searchLp);

                // 【核心修复】让滚动容器的最大高度也跟着缩放！
                LinearLayout.LayoutParams svLp = (LinearLayout.LayoutParams) mScrollView.getLayoutParams();
                svLp.height = (int) UIUtils.dp2px(250 * mCurrentScale);
                mScrollView.setLayoutParams(svLp);

                renderList();
                mLastRenderedScale = mCurrentScale;
            }

            // 2. 实时计算物理坐标
            int[] btnLoc = new int[2]; mAnchor.getLocationOnScreen(btnLoc);
            int[] vpLoc = new int[2]; mViewport.getLocationOnScreen(vpLoc);

            float relX = btnLoc[0] - vpLoc[0];
            float relY = btnLoc[1] - vpLoc[1];

            float scaledTargetWidth = mAnchor.getWidth() * mCurrentScale;
            float scaledHeight = mAnchor.getHeight() * mCurrentScale;

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.width = (int) scaledTargetWidth;

            int targetX = (int) relX;
            int targetY = (int) (relY + scaledHeight);

            // 实时测量真实高度，防止写死的推断高度导致提前触发越界保护
            int widthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            mContentLayout.measure(widthSpec, heightSpec);
            int contentHeight = mContentLayout.getMeasuredHeight();

            // 动态边界安全检查：如果下方超出屏幕边缘，则翻转到按钮上方弹出
            if (targetX + lp.width > mViewport.getWidth()) {
                targetX = Math.max(0, mViewport.getWidth() - lp.width);
            }
            if (targetY + contentHeight > mViewport.getHeight()) {
                int popUpY = (int) (relY - contentHeight);
                if (popUpY > 0) {
                    targetY = popUpY; // 上翻
                } else {
                    targetY = Math.max(0, mViewport.getHeight() - contentHeight); // 强行贴底
                }
            }

            lp.leftMargin = targetX;
            lp.topMargin = targetY;
            mContentLayout.setLayoutParams(lp);
        }

        public void dismiss() {
            if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
            mIsTracking = false;
        }

        private ShapeDrawable createRectDrawable(int color, int radius) {
            ShapeDrawable d = new ShapeDrawable();
            d.setColor(color);
            d.setCornerRadius(UIUtils.dp2px(radius));
            return d;
        }
    }
}