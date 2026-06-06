package com.mine.geometry_node.client.ui.bottom_window.asset_library.right;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
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
    }

    private static final float TEXT_SIZE_LIST_SUBTITLE = 11.0f;
    private static final int COLOR_ITEM_SELECTED = 0xFF445566;
    private static final int COLOR_ITEM_TRANSPARENT = 0x00000000;
    private static final int COLOR_FOLDER = 0xFFDDAA00;
    private static final int COLOR_FILE = 0xFF88CCFF;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_SUBTEXT = 0xFF888888;
    private static final int COLOR_TAG_BG = 0xFF344458;
    private static final int COLOR_TAG_TEXT = 0xFFE6F0FF;

    private final AssetEntry mEntry;
    private final List<String> mTags;
    private final TextView mIconView;
    private final TextView mNameView;
    private final TextView mSubtitleView;
    private final Listener mListener;
    private boolean mIsSelected;
    private float mDownX;
    private float mDownY;
    private boolean mMoved;
    private boolean mDragging;
    private final float mTouchSlop;

    AssetFileItemView(Context context, AssetEntry entry, AssetViewMode mode, String displayName, String parentLabel, List<String> tags, Listener listener) {
        super(context);
        mEntry = entry;
        mTags = tags != null ? tags : List.of();
        mListener = listener;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp2pxInt(6), dp2pxInt(4), dp2pxInt(6), dp2pxInt(4));
        setBackground(RightFileBrowserPanel.createRectDrawable(COLOR_ITEM_TRANSPARENT, 4));

        mIconView = UIUtils.createLockedTextView(context, entry.isDirectory() ? "📁" : "📄", mode.iconTextSizeDp, entry.isDirectory() ? COLOR_FOLDER : COLOR_FILE);
        mIconView.setGravity(Gravity.CENTER);
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

    @Override
    public void setSelected(boolean selected) {
        if (mIsSelected == selected) return;
        mIsSelected = selected;
        super.setSelected(selected);
        setBackground(RightFileBrowserPanel.createRectDrawable(selected ? COLOR_ITEM_SELECTED : COLOR_ITEM_TRANSPARENT, 4));
    }

    private void buildLayoutForMode(AssetViewMode mode, String displayName, String parentLabel) {
        removeAllViews();
        mIconView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(mode.iconTextSizeDp));
        mNameView.setText(displayName);
        mNameView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(mode.nameTextSizeDp));
        mSubtitleView.setText(parentLabel);
        mSubtitleView.setVisibility(parentLabel.isEmpty() ? View.GONE : View.VISIBLE);

        if (mode == AssetViewMode.LIST) {
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            mIconView.setText((mEntry.isDirectory() ? "📁 " : "📄 "));
            addView(mIconView, new LinearLayout.LayoutParams(dp2pxInt(34), ViewGroup.LayoutParams.MATCH_PARENT));

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
            return;
        }

        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.CENTER);
        mIconView.setText(mEntry.isDirectory() ? "📁" : "📄");
        addView(mIconView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(mode.iconTextSizeDp + 10)));
        addView(mNameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mSubtitleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView createTagChip(String tag) {
        TextView chip = UIUtils.createLockedTextView(getContext(), "#" + tag, 10.0f, COLOR_TAG_TEXT);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp2pxInt(7), 0, dp2pxInt(7), 0);
        chip.setBackground(RightFileBrowserPanel.createRectDrawable(COLOR_TAG_BG, 4));
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
