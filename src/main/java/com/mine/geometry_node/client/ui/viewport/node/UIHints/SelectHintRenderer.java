package com.mine.geometry_node.client.ui.viewport.node.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionContext;
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
        // 1. 获取选项列表
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

        // 2. 统一使用 leftPort 的 ID 作为数据存储的 Key
        String portId = row.leftPort() != null ? row.leftPort().id() : "";
        Object val = null;
        if (row.leftPort() != null) {
            val = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();
        }

        TextView dropdownBtn = new TextView(context);
        String displayVal = val != null ? val.toString() : (resolvedOptions.isEmpty() ? "" : resolvedOptions.get(0));

        dropdownBtn.setText(displayVal + " ▼");
        dropdownBtn.setTextColor(UIConstants.CLR_GRAY_LABEL);

        dropdownBtn.setTextSize(0, UIUtils.dp2px(UIConstants.Node.TEXT_SIZE_LABEL));
        dropdownBtn.setSingleLine(true);
        dropdownBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        dropdownBtn.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);

        ShapeDrawable borderBg = new ShapeDrawable();
        borderBg.setColor(0x05FFFFFF);
        borderBg.setCornerRadius(UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().node.cornerRadius));
        borderBg.setStroke(UIUtils.dp2pxInt(1), 0xFF555555);
        dropdownBtn.setBackground(borderBg);

        dropdownBtn.setOnClickListener(v -> {
            icyllis.modernui.view.ViewParent parent = v.getParent();

            while (parent != null && !(parent instanceof InteractionContext)) {
                parent = parent.getParent();
            }

            if (parent instanceof InteractionContext interactionContext && !portId.isEmpty()) {
                DropdownSearchMenu menu = new DropdownSearchMenu(context, resolvedOptions, selectedVal -> {
                    dropdownBtn.setText(selectedVal + " ▼");

                    Object oldVal = nodeData.inputs.get(portId);
                    if (oldVal == null || !selectedVal.equals(oldVal.toString())) {
                        if (editorContext != null) {
                            editorContext.getCommandManager().execute(new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, selectedVal));
                        } else {
                            nodeData.inputs.put(portId, selectedVal);
                        }
                    }
                });

                menu.showAt(v, interactionContext);
            }
        });
        return dropdownBtn;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        float startX = UIConstants.Node.LABEL_MARGIN_PORT;
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        boolean hasLabel = row.leftPort() != null || row.rightPort() != null;
        float topOffset = hasLabel ? UIConstants.Node.ROW_HEIGHT : 0;

        float inputBoxHeight = UIHintUtils.getStandardInputHeight();
        float verticalMargin = (UIConstants.Node.ROW_HEIGHT - inputBoxHeight) / 2.0f;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        int widthPx = UIUtils.dp2pxInt(endX - startX);
        int heightPx = UIUtils.dp2pxInt(inputBoxHeight);

        if (lp == null) {
            lp = new FrameLayout.LayoutParams(widthPx, heightPx);
        } else {
            lp.width = widthPx;
            lp.height = heightPx;
        }

        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.leftMargin = UIUtils.dp2pxInt(startX);
        lp.topMargin = UIUtils.dp2pxInt(currentY + topOffset + verticalMargin);

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

        private InteractionContext mContext;
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

                float fontSize = 12f * mCurrentScale;
                tv.setTextSize(0, UIUtils.dp2px(fontSize));
                tv.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR);
                tv.setSingleLine(true);

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

            int visibleItems = Math.min(mFilteredOptions.size(), 5);
            if (visibleItems == 0) visibleItems = 1;
            int itemTotalHeightDp = 22;
            int targetHeightPx = (int) UIUtils.dp2px(visibleItems * itemTotalHeightDp * mCurrentScale);

            LinearLayout.LayoutParams svLp = (LinearLayout.LayoutParams) mScrollView.getLayoutParams();
            if (svLp.height != targetHeightPx) {
                svLp.height = targetHeightPx;
                mScrollView.setLayoutParams(svLp);
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

        public void showAt(View anchor, InteractionContext context) {
            this.mAnchor = anchor;
            this.mContext = context;

            updatePosition();

            if (this.getParent() != null) ((ViewGroup) this.getParent()).removeView(this);

            ((ViewGroup) context).addView(this);

            mSearchBox.post(() -> { mSearchBox.setText(""); mSearchBox.requestFocus(); });

            if (!mIsTracking) {
                mIsTracking = true;
                post(mTrackTask);
            }
        }

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
            if (mAnchor == null || mContext == null) return;

            float newScale = mContext.getCamera().getScale();
            this.mCurrentScale = newScale;

            if (Math.abs(mLastRenderedScale - mCurrentScale) > 0.001f) {
                mContentLayout.setPadding(
                        (int)UIUtils.dp2px(4 * mCurrentScale),
                        (int)UIUtils.dp2px(4 * mCurrentScale),
                        (int)UIUtils.dp2px(4 * mCurrentScale),
                        (int)UIUtils.dp2px(4 * mCurrentScale)
                );

                float scaledSearchFontSize = 12f * mCurrentScale;
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

                renderList();
                mLastRenderedScale = mCurrentScale;
            }

            ViewGroup parentView = (ViewGroup) mContext;

            int[] btnLoc = new int[2]; mAnchor.getLocationOnScreen(btnLoc);
            int[] vpLoc = new int[2]; parentView.getLocationOnScreen(vpLoc);

            float relX = btnLoc[0] - vpLoc[0];
            float relY = btnLoc[1] - vpLoc[1];

            float minMenuWidth = UIUtils.dp2px(200 * mCurrentScale);
            float scaledTargetWidth = Math.max(mAnchor.getWidth() * mCurrentScale, minMenuWidth);
            float scaledHeight = mAnchor.getHeight() * mCurrentScale;

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.width = (int) scaledTargetWidth;

            int targetX = (int) relX;
            int targetY = (int) (relY + scaledHeight);

            int widthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            mContentLayout.measure(widthSpec, heightSpec);
            int contentHeight = mContentLayout.getMeasuredHeight();

            // 边缘溢出检测
            if (targetX + lp.width > parentView.getWidth()) {
                targetX = Math.max(0, parentView.getWidth() - lp.width);
            }
            if (targetY + contentHeight > parentView.getHeight()) {
                int popUpY = (int) (relY - contentHeight);
                if (popUpY > 0) {
                    targetY = popUpY;
                } else {
                    targetY = Math.max(0, parentView.getHeight() - contentHeight);
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
