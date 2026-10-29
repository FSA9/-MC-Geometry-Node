package com.mine.geometry_node.client.ui.components.integration.asset.browser;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.composite.table.UiTable;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagStrip;
import net.minecraft.network.chat.Component;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.ui.components.integration.asset.preview.AssetPreviewView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.drag.WorkspaceDragGesture;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import java.util.List;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;
import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.rect;

final class AssetFileItemView extends LinearLayout {
    interface Listener {
        void onItemPressed(AssetEntry entry, MotionEvent event);
        void onItemDragStarted(AssetEntry entry, MotionEvent event);
        void onItemReleased(AssetEntry entry, MotionEvent event, boolean moved);
        default void onItemCancelled(AssetEntry entry, MotionEvent event) {}
        void onFavoriteToggled(AssetEntry entry);
        void onItemSelectionToggled(AssetEntry entry);
    }

    private static final float TEXT_SIZE_GRID_SUBTITLE = 11.0f;
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
    private final SvgIconView mIconView;
    private final SvgIconView mGraphIconView;
    private final AssetPreviewView mPreviewView;
    private final TextView mNameView;
    private final TextView mSubtitleView;
    private final Listener mListener;
    private boolean mIsSelected;
    private UiCheckBox mSelectionCheck;
    private final UiTable mTable;
    private final int mRowIndex;
    private UiTableRow mRow;

    AssetFileItemView(Context context, AssetEntry entry, AssetViewMode mode, String displayName, String parentLabel,
                      List<String> tags, String graphTypeId, boolean favorite, Listener listener, UiTable table, int rowIndex) {
        super(context);
        mEntry = entry;
        mTable = table;
        mRowIndex = rowIndex;
        mTags = tags != null ? tags : List.of();
        mGraphTypeId = graphTypeId != null ? graphTypeId : "";
        mFavorite = favorite;
        mListener = listener;
        setGravity(Gravity.CENTER_VERTICAL);
        if (mode != AssetViewMode.LIST) setPadding(dp2pxInt(6), dp2pxInt(4), dp2pxInt(6), dp2pxInt(4));
        if (mode != AssetViewMode.LIST) setBackground(rect(COLOR_ITEM_TRANSPARENT, 4, 0, 0));

        mIconView = new SvgIconView(context, iconKind(), iconColor());
        SvgIconView.Icon graphIcon = AssetTypeRegistry.INSTANCE.isType(entry, AssetTypeRegistry.GRAPH_ID)
                ? SvgIconView.Icon.forGraphType(mGraphTypeId) : null;
        mGraphIconView = graphIcon != null ? new SvgIconView(context, graphIcon, iconColor()) : null;
        mPreviewView = mode != AssetViewMode.LIST && entry.supports(AssetTypeAction.PREVIEW)
                && entry.type().previewKind().isConcrete()
                ? new AssetPreviewView(context, entry, mIconView)
                : null;
        mNameView = mode == AssetViewMode.LIST ? UiTableStyle.cell(context, displayName, COLOR_TEXT)
                : UIUtils.createLockedTextView(context, displayName, mode.nameTextSizeDp, COLOR_TEXT);
        if (mode != AssetViewMode.LIST) mNameView.setGravity(Gravity.CENTER);
        mNameView.setSingleLine(true);
        mSubtitleView = mode == AssetViewMode.LIST ? UiTableStyle.cell(context, parentLabel, COLOR_SUBTEXT)
                : UIUtils.createLockedTextView(context, parentLabel, TEXT_SIZE_GRID_SUBTITLE, COLOR_SUBTEXT);
        if (mode != AssetViewMode.LIST) mSubtitleView.setGravity(Gravity.CENTER);
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
        if (mSelectionCheck != null) mSelectionCheck.setChecked(selected);
        if (mIsSelected == selected) return;
        mIsSelected = selected;
        super.setSelected(selected);
        if (mRow != null) mRow.setSelected(selected);
        else setBackground(rect(selected ? COLOR_ITEM_SELECTED : COLOR_ITEM_TRANSPARENT, 4, 0, 0));
    }

    private void buildLayoutForMode(AssetViewMode mode, String displayName, String parentLabel) {
        removeAllViews();
        mIconView.setIcon(iconKind());
        mIconView.setIconColor(iconColor());
        if (mGraphIconView != null) mGraphIconView.setIconColor(iconColor());
        View iconView = iconView();
        mNameView.setText(displayName);
        mSubtitleView.setText(parentLabel);
        mSubtitleView.setVisibility(parentLabel.isEmpty() ? View.GONE : View.VISIBLE);

        if (mode == AssetViewMode.LIST) {
            setOrientation(LinearLayout.VERTICAL);
            mRow = mTable.createRow(id -> createListCell(id, iconView, displayName));
            mRow.setSurfaceOwner(this);
            mTable.styleRow(mRow, mRowIndex, mIsSelected);
            addView(mRow, new LinearLayout.LayoutParams(-1, -2));
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

    private View createListCell(String id, View iconView, String displayName) {
        return switch (id) {
            case "selection" -> {
                mSelectionCheck = new UiCheckBox(getContext());
                mSelectionCheck.setContentDescription(displayName);
                mSelectionCheck.setOnCheckedChangeListener((view, checked) -> mListener.onItemSelectionToggled(mEntry));
                yield UiTableStyle.selectionCell(getContext(), mSelectionCheck);
            }
            case "type" -> {
                FrameLayout type = new FrameLayout(getContext());
                type.addView(iconView, new FrameLayout.LayoutParams(dp2pxInt(22), dp2pxInt(22), Gravity.CENTER));
                yield type;
            }
            case "name" -> {
                LinearLayout name = new LinearLayout(getContext());
                name.setOrientation(LinearLayout.VERTICAL);
                name.setGravity(Gravity.CENTER_VERTICAL);
                name.addView(mNameView, new LinearLayout.LayoutParams(-1, -2));
                name.addView(mSubtitleView, new LinearLayout.LayoutParams(-1, -2));
                yield name;
            }
            case "description" -> {
                String description = mEntry.descriptor().documentInfo().description();
                View cell = UiTableStyle.cell(getContext(), description, COLOR_SUBTEXT);
                cell.setTooltipText(description);
                yield cell;
            }
            case "tags" -> {
                UiTagStrip tags = new UiTagStrip(getContext(), mTable.theme(), UiTableStyle.CELL_TEXT_SIZE_DP);
                // Match the shared text cell insets without duplicating table spacing values.
                tags.setPadding(mNameView.getPaddingLeft(), mNameView.getPaddingTop(),
                        mNameView.getPaddingRight(), mNameView.getPaddingBottom());
                tags.setTags(mTags);
                yield tags;
            }
            case "favorite" -> {
                FrameLayout favorite = new FrameLayout(getContext());
                if (canFavorite()) favorite.addView(createFavoriteButton(),
                        new FrameLayout.LayoutParams(dp2pxInt(24), dp2pxInt(24), Gravity.CENTER));
                yield favorite;
            }
            default -> throw new IllegalArgumentException("Unknown asset column: " + id);
        };
    }

    private boolean canFavorite() {
        return mEntry.sourceKind() == AssetSourceKind.LOCAL
                && mEntry.supports(AssetTypeAction.FAVORITE);
    }

    private UiIconButton createFavoriteButton() {
        SvgIconView star = new SvgIconView(getContext(), SvgIconView.Icon.STAR,
                mFavorite ? COLOR_STAR_ON : COLOR_STAR_OFF);
        UiIconButton button = new UiIconButton(getContext(), star,
                UiIconButton.Style.forPreset(UiThemes.EDITOR, UiIconButton.Preset.GLYPH).withSize(0, 0, 12))
                .tooltip(Component.translatable("geometry_node.asset_library.action.favorite_toggle").getString());
        button.setOnClickListener(view -> mListener.onFavoriteToggled(mEntry));
        button.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) star.setIconColor(COLOR_STAR_ON);
            else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                star.setIconColor(mFavorite || button.isFocused() ? COLOR_STAR_ON : COLOR_STAR_OFF);
            }
            return false;
        });
        button.setOnFocusChangeListener((view, focused) -> star.setIconColor(
                mFavorite || focused || button.isHovered() ? COLOR_STAR_ON : COLOR_STAR_OFF));
        return button;
    }

    private SvgIconView.Icon iconKind() {
        if (mEntry.isDirectory()) return SvgIconView.Icon.FOLDER;
        return switch (mEntry.type().id()) {
            case "image" -> SvgIconView.Icon.IMAGE;
            case "schematic" -> SvgIconView.Icon.SCHEMATIC;
            case "model" -> SvgIconView.Icon.MODEL;
            default -> SvgIconView.Icon.FILE;
        };
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
