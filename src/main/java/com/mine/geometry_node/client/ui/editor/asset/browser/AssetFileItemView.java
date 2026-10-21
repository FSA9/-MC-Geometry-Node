package com.mine.geometry_node.client.ui.editor.asset.browser;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagChip;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagStrip;
import net.minecraft.network.chat.Component;
import com.mine.geometry_node.client.ui.components.base.icon.VectorIconView;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.ui.editor.asset.preview.AssetPreviewView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragGesture;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
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
        default void onItemCancelled(AssetEntry entry, MotionEvent event) {}
        void onFavoriteToggled(AssetEntry entry);
    }

    private static final float TEXT_SIZE_LIST_SUBTITLE = 11.0f;
    private static final int COLOR_ITEM_SELECTED = UiThemes.EDITOR.selection().subtle();
    // Unselected items expose the parent surface; this is structural transparency.
    private static final int COLOR_ITEM_TRANSPARENT = 0x00000000;
    private static final int COLOR_TEXT = UiThemes.EDITOR.text().normal();
    private static final int COLOR_SUBTEXT = UiThemes.EDITOR.text().muted();
    private static final int COLOR_STAR_ON = UiThemes.EDITOR.decoration().favorite();
    private static final int COLOR_STAR_OFF = UiThemes.EDITOR.text().hint();

    private final AssetEntry mEntry;
    private final List<String> mTags;
    private final String mGraphTypeId;
    private final boolean mFavorite;
    private final VectorIconView mIconView;
    private final SvgIconView mGraphIconView;
    private final AssetPreviewView mPreviewView;
    private final TextView mNameView;
    private final TextView mSubtitleView;
    private final Listener mListener;
    private boolean mIsSelected;

    AssetFileItemView(Context context, AssetEntry entry, AssetViewMode mode, String displayName, String parentLabel,
                      List<String> tags, String graphTypeId, boolean favorite, Listener listener) {
        super(context);
        mEntry = entry;
        mTags = tags != null ? tags : List.of();
        mGraphTypeId = graphTypeId != null ? graphTypeId : "";
        mFavorite = favorite;
        mListener = listener;
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp2pxInt(6), dp2pxInt(4), dp2pxInt(6), dp2pxInt(4));
        setBackground(AssetFileBrowserPanel.createRectDrawable(COLOR_ITEM_TRANSPARENT, 4));

        mIconView = new VectorIconView(context, iconKind(), iconColor());
        SvgIconView.Icon graphIcon = AssetTypeRegistry.INSTANCE.isType(entry, AssetTypeRegistry.GRAPH_ID)
                ? SvgIconView.Icon.forGraphType(mGraphTypeId) : null;
        mGraphIconView = graphIcon != null ? new SvgIconView(context, graphIcon, iconColor()) : null;
        mPreviewView = entry.supports(AssetTypeAction.PREVIEW)
                && entry.type().previewKind().isConcrete()
                ? new AssetPreviewView(context, entry, mIconView)
                : null;
        mNameView = UIUtils.createLockedTextView(context, displayName, mode.nameTextSizeDp, COLOR_TEXT);
        mNameView.setGravity(mode == AssetViewMode.LIST ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        mNameView.setSingleLine(true);
        mSubtitleView = UIUtils.createLockedTextView(context, parentLabel, TEXT_SIZE_LIST_SUBTITLE, COLOR_SUBTEXT);
        mSubtitleView.setGravity(mode == AssetViewMode.LIST ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        mSubtitleView.setSingleLine(true);

        buildLayoutForMode(mode, displayName, parentLabel);
        setOnTouchListener(new WorkspaceDragGesture(getContext(), new WorkspaceDragGesture.Listener() {
            @Override public void onPressed(MotionEvent event) { mListener.onItemPressed(mEntry, event); }
            @Override public void onDragStarted(MotionEvent event) { mListener.onItemDragStarted(mEntry, event); }
            @Override public void onDragged(MotionEvent event) { }
            @Override public void onReleased(MotionEvent event, boolean moved) { mListener.onItemReleased(mEntry, event, moved); }
            @Override public void onCancelled(MotionEvent event) { mListener.onItemCancelled(mEntry, event); }
        }));
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
        if (mGraphIconView != null) mGraphIconView.setIconColor(iconColor());
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
                UiTagStrip tagRow = new UiTagStrip(getContext());
                tagRow.setPadding(dp2pxInt(8), 0, 0, 0);
                tagRow.setTags(mTags);
                nameRow.addView(tagRow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
            }

            textColumn.addView(nameRow, parentLabel.isEmpty()
                    ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(UiTagChip.HEIGHT_DP)));
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

    private UiIconButton createFavoriteButton() {
        TextView star = UIUtils.createLockedTextView(getContext(), mFavorite ? "★" : "☆", 15.0f,
                mFavorite ? COLOR_STAR_ON : COLOR_STAR_OFF);
        star.setGravity(Gravity.CENTER);
        star.setSingleLine(true);
        star.setPadding(0, 0, 0, 0);
        UiIconButton button = new UiIconButton(getContext(), star, new UiIconButton.Style(
                UiMetrics.RADIUS_SMALL, 0, 0, UiThemes.EDITOR.control().iconHover(),
                UiThemes.EDITOR.control().iconPressed(), 0, 0, 20, UiMetrics.DISABLED_ALPHA))
                .tooltip(Component.translatable("geometry_node.asset_library.action.favorite_toggle").getString());
        button.setOnClickListener(view -> mListener.onFavoriteToggled(mEntry));
        button.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) star.setTextColor(COLOR_STAR_ON);
            else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                star.setTextColor(mFavorite ? COLOR_STAR_ON : COLOR_STAR_OFF);
            }
            return false;
        });
        return button;
    }

    private VectorIconView.Kind iconKind() {
        return mEntry.isDirectory() ? VectorIconView.Kind.FOLDER : VectorIconView.Kind.FILE;
    }

    // Graph/asset type identity remains independent of selection and favorite decoration.
    private int iconColor() {
        if (AssetTypeRegistry.INSTANCE.isType(mEntry, AssetTypeRegistry.GRAPH_ID)) {
            GraphType graphType = GraphTypeRegistry.INSTANCE.get(mGraphTypeId);
            if (graphType != null) return graphType.assetIconColor();
        }
        return mEntry.type().defaultColor();
    }

    private View iconView() {
        if (mPreviewView != null) return mPreviewView;
        return mGraphIconView != null ? mGraphIconView : mIconView;
    }

}
