package com.mine.geometry_node.client.ui.editor.asset.browser;

import com.mine.geometry_node.client.ui.common.VectorIconView;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetPreviewKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.ui.editor.asset.preview.AssetPreviewView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.List;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

final class AssetFileItemView extends LinearLayout {
    interface Listener {
        void onItemPressed(AssetEntry entry, MotionEvent event);
        void onItemDragStarted(AssetEntry entry, MotionEvent event);
        void onItemReleased(AssetEntry entry, MotionEvent event, boolean moved);
        void onFavoriteToggled(AssetEntry entry);
    }

    private static final float TEXT_SIZE_LIST_SUBTITLE = 11.0f;
    private static final int COLOR_ITEM_SELECTED = 0xFF445566;
    private static final int COLOR_ITEM_TRANSPARENT = 0x00000000;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_SUBTEXT = 0xFF888888;
    private static final int COLOR_TAG_BG = 0xFF344458;
    private static final int COLOR_TAG_TEXT = 0xFFE6F0FF;
    private static final int COLOR_STAR_ON = 0xFFFFD166;
    private static final int COLOR_STAR_OFF = 0xFF69727D;

    private final AssetEntry mEntry;
    private final List<String> mTags;
    private final String mGraphTypeId;
    private final boolean mFavorite;
    private final VectorIconView mIconView;
    private final AssetPreviewView mPreviewView;
    private final TextView mNameView;
    private final TextView mSubtitleView;
    private final Listener mListener;
    private boolean mIsSelected;
    private float mDownX;
    private float mDownY;
    private boolean mMoved;
    private boolean mDragging;
    private final float mTouchSlop;

    AssetFileItemView(Context context, AssetEntry entry, AssetViewMode mode, String displayName, String parentLabel,
                      List<String> tags, String graphTypeId, boolean favorite, Listener listener) {
        super(context);
        mEntry = entry;
        mTags = tags != null ? tags : List.of();
        mGraphTypeId = graphTypeId != null ? graphTypeId : "";
        mFavorite = favorite;
        mListener = listener;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp2pxInt(6), dp2pxInt(4), dp2pxInt(6), dp2pxInt(4));
        setBackground(AssetFileBrowserPanel.createRectDrawable(COLOR_ITEM_TRANSPARENT, 4));

        mIconView = new VectorIconView(context, iconKind(), iconColor());
        mPreviewView = entry.supports(AssetTypeAction.PREVIEW)
                && entry.type().previewKind() != AssetPreviewKind.NONE
                ? new AssetPreviewView(context, entry, mIconView)
                : null;
        mNameView = UIUtils.createLockedTextView(context, displayName, mode.nameTextSizeDp, COLOR_TEXT);
        mNameView.setGravity(mode == AssetViewMode.LIST ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        mNameView.setSingleLine(true);
        mSubtitleView = UIUtils.createLockedTextView(context, parentLabel, TEXT_SIZE_LIST_SUBTITLE, COLOR_SUBTEXT);
        mSubtitleView.setGravity(mode == AssetViewMode.LIST ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        mSubtitleView.setSingleLine(true);

        buildLayoutForMode(mode, displayName, parentLabel);
        setOnTouchListener(this::onItemTouch);
    }

    AssetEntry getEntry() {
        return mEntry;
    }

    TextView getNameView() {
        return mNameView;
    }

    void preloadThumbnail() {
        if (mPreviewView != null) mPreviewView.preload();
    }

    @Override
    public void setSelected(boolean selected) {
        if (mIsSelected == selected) return;
        mIsSelected = selected;
        super.setSelected(selected);
        setBackground(AssetFileBrowserPanel.createRectDrawable(selected ? COLOR_ITEM_SELECTED : COLOR_ITEM_TRANSPARENT, 4));
    }

    private void buildLayoutForMode(AssetViewMode mode, String displayName, String parentLabel) {
        removeAllViews();
        mIconView.setKind(iconKind());
        mIconView.setIconColor(iconColor());
        View iconView = iconView();
        mNameView.setText(displayName);
        mNameView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(mode.nameTextSizeDp));
        mSubtitleView.setText(parentLabel);
        mSubtitleView.setVisibility(parentLabel.isEmpty() ? View.GONE : View.VISIBLE);

        if (mode == AssetViewMode.LIST) {
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            addView(iconView, new LinearLayout.LayoutParams(dp2pxInt(34), ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout textColumn = new LinearLayout(getContext());
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout nameRow = new LinearLayout(getContext());
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            nameRow.addView(mNameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (!mTags.isEmpty()) {
                LinearLayout tagRow = new LinearLayout(getContext());
                tagRow.setOrientation(LinearLayout.HORIZONTAL);
                tagRow.setGravity(Gravity.CENTER_VERTICAL);
                tagRow.setPadding(dp2pxInt(8), 0, 0, 0);
                for (String tag : mTags) {
                    tagRow.addView(createTagChip(tag), tagLayoutParams());
                }
                nameRow.addView(tagRow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
            }

            textColumn.addView(nameRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
            textColumn.addView(mSubtitleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
            addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
            if (canFavorite()) {
                addView(createFavoriteButton(), new LinearLayout.LayoutParams(dp2pxInt(34), ViewGroup.LayoutParams.MATCH_PARENT));
            }
            return;
        }

        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.CENTER);
        FrameLayout iconFrame = new FrameLayout(getContext());
        iconFrame.addView(iconView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (canFavorite()) {
            FrameLayout.LayoutParams starLp = new FrameLayout.LayoutParams(dp2pxInt(24), dp2pxInt(24));
            starLp.gravity = Gravity.TOP | Gravity.RIGHT;
            iconFrame.addView(createFavoriteButton(), starLp);
        }
        addView(iconFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(mode.iconTextSizeDp + 10)));
        addView(mNameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mSubtitleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private boolean canFavorite() {
        return mEntry.sourceKind() == AssetSourceKind.LOCAL
                && mEntry.supports(AssetTypeAction.FAVORITE);
    }

    private TextView createFavoriteButton() {
        TextView star = UIUtils.createLockedTextView(getContext(), mFavorite ? "★" : "☆", 15.0f, mFavorite ? COLOR_STAR_ON : COLOR_STAR_OFF);
        star.setGravity(Gravity.CENTER);
        star.setSingleLine(true);
        star.setPadding(0, 0, 0, 0);
        star.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (event.getX() >= 0 && event.getY() >= 0 && event.getX() < v.getWidth() && event.getY() < v.getHeight()) {
                    mListener.onFavoriteToggled(mEntry);
                }
                return true;
            }
            return true;
        });
        star.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                star.setTextColor(COLOR_STAR_ON);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                star.setTextColor(mFavorite ? COLOR_STAR_ON : COLOR_STAR_OFF);
            }
            return false;
        });
        return star;
    }

    private VectorIconView.Kind iconKind() {
        return mEntry.isDirectory() ? VectorIconView.Kind.FOLDER : VectorIconView.Kind.FILE;
    }

    private int iconColor() {
        if (AssetTypeRegistry.INSTANCE.isType(mEntry, AssetTypeRegistry.GRAPH_ID)) {
            GraphType graphType = GraphTypeRegistry.INSTANCE.get(mGraphTypeId);
            if (graphType != null) return graphType.assetIconColor();
        }
        return mEntry.type().defaultColor();
    }

    private View iconView() {
        return mPreviewView != null ? mPreviewView : mIconView;
    }

    private TextView createTagChip(String tag) {
        TextView chip = UIUtils.createLockedTextView(getContext(), "#" + tag, 10.0f, COLOR_TAG_TEXT);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp2pxInt(7), 0, dp2pxInt(7), 0);
        chip.setBackground(AssetFileBrowserPanel.createRectDrawable(COLOR_TAG_BG, 4));
        return chip;
    }

    private LinearLayout.LayoutParams tagLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp2pxInt(20));
        lp.rightMargin = dp2pxInt(5);
        return lp;
    }

    private boolean onItemTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                mMoved = false;
                mDragging = false;
                mListener.onItemPressed(mEntry, event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - mDownX) > mTouchSlop || Math.abs(event.getY() - mDownY) > mTouchSlop) {
                    mMoved = true;
                    if (!mDragging) {
                        mDragging = true;
                        mListener.onItemDragStarted(mEntry, event);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                mListener.onItemReleased(mEntry, event, mMoved);
                mDragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mMoved = false;
                mDragging = false;
                return true;
            default:
                return true;
        }
    }
}
