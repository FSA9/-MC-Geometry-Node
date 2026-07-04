package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextShaper;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SelectHintRenderer implements UIHintRenderer {
    private static final int MENU_WIDTH_DP = 220;
    private static final int MENU_PADDING_DP = 8;
    private static final int SEARCH_HEIGHT_DP = 28;
    private static final int TITLE_HEIGHT_DP = 22;
    private static final int SEARCH_RADIUS_DP = 5;
    private static final int ITEM_HEIGHT_DP = 24;
    private static final int ITEM_RADIUS_DP = 4;
    private static final int MAX_VISIBLE_ITEMS = 6;

    private static final int COLOR_BUTTON_BG = 0xFF252525;
    private static final int COLOR_BUTTON_BG_HOVER = 0xFF30343B;
    private static final int COLOR_BUTTON_BG_ACTIVE = 0xFF343B45;
    private static final int COLOR_BUTTON_BORDER = 0xFF333333;
    private static final int COLOR_BUTTON_BORDER_HOVER = 0xFF566070;
    private static final int COLOR_PANEL_BG = 0xFF2B2B2B;
    private static final int COLOR_PANEL_BORDER = 0xFF151515;
    private static final int COLOR_TITLE_BG = 0xFF242424;
    private static final int COLOR_TITLE_TEXT = 0xFFBFC7D5;
    private static final int COLOR_SEARCH_BG = 0xFF1E1E1E;
    private static final int COLOR_SEARCH_BORDER = 0xFF3A3A3A;
    private static final int COLOR_NODE_TEXT = 0xFFCCCCCC;
    private static final int COLOR_MUTED_TEXT = 0xFF999999;
    private static final int COLOR_HOVER_BG = 0xFF3A4652;

    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 0.0f;
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
            val = UIHintValueBinder.getValue(nodeData, row.leftPort());
        }

        String displayVal = val != null ? val.toString() : (resolvedOptions.isEmpty() ? "" : resolvedOptions.get(0));
        String title = selectTitle(nodeData, row);

        SelectButtonView dropdownBtn = new SelectButtonView(context, displayVal);

        dropdownBtn.setOnClickListener(v -> {
            icyllis.modernui.view.ViewParent parent = v.getParent();

            while (parent != null && !(parent instanceof InteractionContext)) {
                parent = parent.getParent();
            }

            if (parent instanceof InteractionContext interactionContext && !portId.isEmpty()) {
                DropdownSearchMenu menu = new DropdownSearchMenu(context, title, resolvedOptions, selectedVal -> {
                    dropdownBtn.setValue(selectedVal);
                    UIHintValueBinder.commit(editorContext, nodeData, portId, selectedVal);
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
        lp.topMargin = UIUtils.dp2pxInt(currentY + verticalMargin);

        view.setLayoutParams(lp);
    }

    private static String selectTitle(NodeData nodeData, PortRow row) {
        if (row == null || row.leftPort() == null || row.leftPort().displayName() == null) {
            return "";
        }
        String defaultName = row.leftPort().displayName().getString();
        return nodeData != null ? nodeData.getEffectivePortName("inputs", row.leftPort().id(), defaultName) : defaultName;
    }

    private static ShapeDrawable createRectDrawable(int color, float radiusDp) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        return drawable;
    }

    private static ShapeDrawable createRectDrawable(int color, float radiusDp, float strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = createRectDrawable(color, radiusDp);
        drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        return drawable;
    }

    private static class SelectButtonView extends View {
        private static final float TEXT_PADDING_DP = 8.0f;

        private final Paint mPaint = new Paint();
        private final RectF mRect = new RectF();
        private final TextPaint mTextPaint = new TextPaint();
        private final FontMetricsInt mMetrics = new FontMetricsInt();
        private ShapedText mValueText;
        private ShapedText mArrowText;
        private boolean mHovered;
        private boolean mPressed;

        SelectButtonView(Context context, String value) {
            super(context);
            setWillNotDraw(false);
            setFocusable(true);
            setFocusableInTouchMode(true);
            mPaint.setAntiAlias(true);
            mTextPaint.setTextAntiAlias(true);
            mTextPaint.setTextSize(UIUtils.dp2px(UIConstants.Node.TEXT_SIZE_LABEL));
            mArrowText = shape("▼");
            setValue(value);
        }

        void setValue(String value) {
            String safeValue = value == null ? "" : value;
            mValueText = shape(safeValue);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            float radius = UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().node.cornerRadius);
            float stroke = UIUtils.dp2px(1.0f);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(mPressed ? COLOR_BUTTON_BG_ACTIVE : (mHovered ? COLOR_BUTTON_BG_HOVER : COLOR_BUTTON_BG));
            mRect.set(0, 0, w, h);
            canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(stroke);
            mPaint.setColor(mHovered ? COLOR_BUTTON_BORDER_HOVER : COLOR_BUTTON_BORDER);
            mRect.set(stroke * 0.5f, stroke * 0.5f, w - stroke * 0.5f, h - stroke * 0.5f);
            canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

            mTextPaint.getFontMetricsInt(mMetrics);
            float baseline = h * 0.5f - (mMetrics.ascent + mMetrics.descent) * 0.5f;
            float padding = UIUtils.dp2px(TEXT_PADDING_DP);
            float arrowX = Math.max(padding, w - padding - mArrowText.getAdvance());

            if (mValueText != null) {
                mTextPaint.setColor(UIConstants.CLR_GRAY_LABEL);
                canvas.drawShapedText(mValueText, padding, baseline, mTextPaint);
            }

            mTextPaint.setColor(0xFF8C95A4);
            canvas.drawShapedText(mArrowText, arrowX, baseline, mTextPaint);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return onTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                requestFocus();
                mPressed = true;
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                boolean wasPressed = mPressed;
                mPressed = false;
                invalidate();
                if (wasPressed) {
                    performClick();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                mPressed = false;
                invalidate();
                return true;
            }
            return true;
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
                setControlHovered(true);
                return true;
            }
            if (action == MotionEvent.ACTION_HOVER_EXIT) {
                setControlHovered(false);
                return true;
            }
            return super.dispatchGenericMotionEvent(event);
        }

        private void setControlHovered(boolean hovered) {
            if (mHovered == hovered) {
                return;
            }
            mHovered = hovered;
            invalidate();
        }

        private ShapedText shape(String text) {
            return TextShaper.shapeText(text, 0, text.length(), TextDirectionHeuristics.FIRSTSTRONG_LTR, mTextPaint);
        }
    }

    private static class DropdownSearchMenu extends FrameLayout {
        private LinearLayout mContentLayout;
        private LinearLayout mListContainer;
        private EditText mSearchBox;
        private ScrollView mScrollView;
        private final String mTitle;
        private final List<String> mOptions;
        private final List<String> mFilteredOptions;
        private final Consumer<String> mOnSelect;

        private View mAnchor;

        private InteractionContext mContext;
        private boolean mIsTracking = false;

        public DropdownSearchMenu(Context context, String title, List<String> options, Consumer<String> onSelect) {
            super(context);
            this.mTitle = title;
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
            mContentLayout.setBackground(createRectDrawable(COLOR_PANEL_BG, UIConstants.ViewPort.NodeMenu.BORDER_RADIUS + 5, 1, COLOR_PANEL_BORDER));
            mContentLayout.setPadding(
                    UIUtils.dp2pxInt(MENU_PADDING_DP),
                    UIUtils.dp2pxInt(MENU_PADDING_DP),
                    UIUtils.dp2pxInt(MENU_PADDING_DP),
                    UIUtils.dp2pxInt(MENU_PADDING_DP)
            );
            mContentLayout.setOnClickListener(v -> {});

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(MENU_WIDTH_DP), LayoutParams.WRAP_CONTENT);
            mContentLayout.setLayoutParams(lp);

            if (mTitle != null && !mTitle.isBlank()) {
                TextView titleView = new TextView(context);
                titleView.setText(mTitle);
                titleView.setTextSize(0, UIUtils.dp2px(12));
                titleView.setTextColor(COLOR_TITLE_TEXT);
                titleView.setSingleLine(true);
                titleView.setGravity(Gravity.CENTER_VERTICAL);
                titleView.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
                titleView.setBackground(createRectDrawable(COLOR_TITLE_BG, ITEM_RADIUS_DP));

                LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        UIUtils.dp2pxInt(TITLE_HEIGHT_DP)
                );
                titleLp.setMargins(0, 0, 0, UIUtils.dp2pxInt(6));
                mContentLayout.addView(titleView, titleLp);
            }

            mSearchBox = new EditText(context);
            mSearchBox.setHint("Search...");
            mSearchBox.setTextSize(0, UIUtils.dp2px(12));
            mSearchBox.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_SEARCH);
            mSearchBox.setHintTextColor(0xFF777777);
            mSearchBox.setSingleLine(true);
            mSearchBox.setGravity(Gravity.CENTER_VERTICAL);
            mSearchBox.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
            mSearchBox.setBackground(createRectDrawable(COLOR_SEARCH_BG, SEARCH_RADIUS_DP, 1, COLOR_SEARCH_BORDER));

            LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(SEARCH_HEIGHT_DP));
            searchLp.setMargins(0, 0, 0, UIUtils.dp2pxInt(8));
            mContentLayout.addView(mSearchBox, searchLp);

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
            if (mFilteredOptions.isEmpty()) {
                addEmptyItem("无匹配选项");
                updateScrollHeight(1);
                return;
            }

            for (String item : mFilteredOptions) {
                TextView tv = new TextView(getContext());
                tv.setText(item);

                tv.setTextSize(0, UIUtils.dp2px(12));
                tv.setTextColor(COLOR_NODE_TEXT);
                tv.setSingleLine(true);

                int padH = UIUtils.dp2pxInt(10);
                tv.setPadding(padH, 0, padH, 0);
                tv.setGravity(Gravity.CENTER_VERTICAL);

                tv.setOnClickListener(v -> { mOnSelect.accept(item); post(this::dismiss); });
                tv.setOnHoverListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                        tv.setBackground(createRectDrawable(COLOR_HOVER_BG, ITEM_RADIUS_DP));
                        tv.setTextColor(UIConstants.ViewPort.NodeMenu.TEXT_COLOR_HOVER);
                    } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                        tv.setBackground(null);
                        tv.setTextColor(COLOR_NODE_TEXT);
                    }
                    return false;
                });

                int itemHeight = UIUtils.dp2pxInt(ITEM_HEIGHT_DP);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight);
                int marginV = UIUtils.dp2pxInt(1);
                lp.setMargins(0, marginV, 0, marginV);
                mListContainer.addView(tv, lp);
            }

            updateScrollHeight(mFilteredOptions.size());
        }

        private void addEmptyItem(String text) {
            TextView tv = new TextView(getContext());
            tv.setText(text);
            tv.setTextSize(0, UIUtils.dp2px(12));
            tv.setTextColor(COLOR_MUTED_TEXT);
            tv.setSingleLine(true);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(ITEM_HEIGHT_DP)
            );
            mListContainer.addView(tv, lp);
        }

        private void updateScrollHeight(int itemCount) {
            int visibleItems = Math.min(Math.max(itemCount, 1), MAX_VISIBLE_ITEMS);
            int targetHeightPx = UIUtils.dp2pxInt(visibleItems * (ITEM_HEIGHT_DP + 2));

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

            renderList();
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

            ViewGroup parentView = (ViewGroup) mContext;

            int[] btnLoc = new int[2]; mAnchor.getLocationOnScreen(btnLoc);
            int[] vpLoc = new int[2]; parentView.getLocationOnScreen(vpLoc);

            float relX = btnLoc[0] - vpLoc[0];
            float relY = btnLoc[1] - vpLoc[1];

            int targetWidth = UIUtils.dp2pxInt(MENU_WIDTH_DP);
            float scaledHeight = mAnchor.getHeight() * mContext.getCamera().getScale();

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContentLayout.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.width = targetWidth;

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
    }
}
