package com.mine.geometry_node.client.ui.bottom_window.asset_library.right;

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

import java.io.File;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

final class AssetFileItemView extends LinearLayout {
    interface Listener {
        void onItemPressed(File file, MotionEvent event);
        void onItemReleased(File file, MotionEvent event, boolean moved);
    }

    private static final float TEXT_SIZE_LIST_SUBTITLE = 11.0f;
    private static final int COLOR_ITEM_SELECTED = 0xFF445566;
    private static final int COLOR_ITEM_TRANSPARENT = 0x00000000;
    private static final int COLOR_FOLDER = 0xFFDDAA00;
    private static final int COLOR_FILE = 0xFF88CCFF;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_SUBTEXT = 0xFF888888;

    private final File mFile;
    private final TextView mIconView;
    private final TextView mNameView;
    private final TextView mSubtitleView;
    private final Listener mListener;
    private boolean mIsSelected;
    private float mDownX;
    private float mDownY;
    private boolean mMoved;
    private final float mTouchSlop;

    AssetFileItemView(Context context, File file, AssetViewMode mode, String displayName, String parentLabel, Listener listener) {
        super(context);
        mFile = file;
        mListener = listener;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp2pxInt(6), dp2pxInt(4), dp2pxInt(6), dp2pxInt(4));
        setBackground(RightFileBrowserPanel.createRectDrawable(COLOR_ITEM_TRANSPARENT, 4));

        mIconView = UIUtils.createLockedTextView(context, file.isDirectory() ? "📁" : "📄", mode.iconTextSizeDp, file.isDirectory() ? COLOR_FOLDER : COLOR_FILE);
        mIconView.setGravity(Gravity.CENTER);
        mNameView = UIUtils.createLockedTextView(context, displayName, mode.nameTextSizeDp, COLOR_TEXT);
        mNameView.setGravity(mode == AssetViewMode.LIST ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        mSubtitleView = UIUtils.createLockedTextView(context, parentLabel, TEXT_SIZE_LIST_SUBTITLE, COLOR_SUBTEXT);
        mSubtitleView.setGravity(mode == AssetViewMode.LIST ? Gravity.CENTER_VERTICAL : Gravity.CENTER);

        buildLayoutForMode(mode, displayName, parentLabel);
        setOnTouchListener(this::onItemTouch);
    }

    File getFile() {
        return mFile;
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
            mIconView.setText((mFile.isDirectory() ? "📁 " : "📄 "));
            addView(mIconView, new LinearLayout.LayoutParams(dp2pxInt(34), ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout textColumn = new LinearLayout(getContext());
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setGravity(Gravity.CENTER_VERTICAL);
            textColumn.addView(mNameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
            textColumn.addView(mSubtitleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
            addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
            return;
        }

        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.CENTER);
        mIconView.setText(mFile.isDirectory() ? "📁" : "📄");
        addView(mIconView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(mode.iconTextSizeDp + 10)));
        addView(mNameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mSubtitleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private boolean onItemTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                mMoved = false;
                mListener.onItemPressed(mFile, event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - mDownX) > mTouchSlop || Math.abs(event.getY() - mDownY) > mTouchSlop) {
                    mMoved = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                mListener.onItemReleased(mFile, event, mMoved);
                return true;
            case MotionEvent.ACTION_CANCEL:
                mMoved = false;
                return true;
            default:
                return true;
        }
    }
}
