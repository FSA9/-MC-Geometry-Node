package com.mine.geometry_node.client.ui.components.integration.asset.navigation;

import com.mine.geometry_node.client.config.AppConfig;
import com.mine.geometry_node.client.config.ConfigChangeListener;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.feature.asset.navigation.AssetQuickAccessStore;
import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.feature.asset.repository.AssetLocation;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.integration.asset.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;
import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.solid;

/** Source groups own their fold state; the feature store owns shortcut identity and persistence. */
public class AssetNavigationPanel extends UiScrollView {
    private static final int ROW_HEIGHT = 40;
    private static final int ICON_SLOT = 24;
    private static final int ICON_SIZE = 16;
    private static final int FOLDER_COLOR = AssetTypeRegistry.INSTANCE
            .get(AssetTypeRegistry.DIRECTORY_ID).defaultColor();

    private final AssetBrowserCoordinator mCoordinator;
    private final AssetQuickAccessStore mQuickAccess = new AssetQuickAccessStore();
    private final LinearLayout mSidebar;
    private final float mTouchSlop;
    private final Map<AssetSourceKind, Boolean> mExpanded = new EnumMap<>(AssetSourceKind.class);
    private final List<SelectionRow> mSelectionRows = new ArrayList<>();
    private final List<QuickAccessListLayout> mQuickLists = new ArrayList<>();
    private String mSelectedKey = "";
    private ConfigChangeListener mConfigListener;
    private QuickAccessSnapshot mConfigSnapshot;
    private volatile int mAttachmentGeneration;

    public AssetNavigationPanel(Context context, AssetBrowserCoordinator coordinator) {
        super(context);
        mCoordinator = coordinator;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mExpanded.put(AssetSourceKind.REMOTE, true);
        mExpanded.put(AssetSourceKind.LOCAL, true);
        mSidebar = verticalLayout();
        setBackground(solid(UiThemes.EDITOR.surface().panel()));
        mSidebar.setBackground(solid(UiThemes.EDITOR.surface().panel()));
        addView(mSidebar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildSidebar();
    }

    /** Called by browser/path navigation as well as by sidebar actions. Does not change fold state. */
    public void setLocation(AssetLocation location) {
        mSelectedKey = selectionKey(location);
        for (SelectionRow row : mSelectionRows) {
            row.view().setSelected(row.key().equals(mSelectedKey));
        }
    }

    public void buildSidebar() {
        for (QuickAccessListLayout list : mQuickLists) list.cancelDrag();
        mQuickLists.clear();
        mSelectionRows.clear();
        mSidebar.removeAllViews();
        TextView heading = UIUtils.createLockedTextView(getContext(), "快速访问", 12,
                UiThemes.EDITOR.text().muted());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp2pxInt(10), 0, dp2pxInt(10), 0);
        mSidebar.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(32)));
        // Favorites is independent of either source group's expansion.
        if (mCoordinator.allowsSource(AssetSourceKind.LOCAL)) {
            addDestination(mSidebar, "我的收藏", new AssetLocation.Local(null, true, List.of()),
                    SvgIconView.Icon.STAR, UiThemes.EDITOR.decoration().favorite(), true);
        }
        for (AssetSourceKind source : List.of(AssetSourceKind.REMOTE, AssetSourceKind.LOCAL)) {
            if (mCoordinator.allowsSource(source)) addSourceGroup(source);
        }
        if (mCoordinator.allowsSource(AssetSourceKind.LOCAL)) addLocalLocations();
    }

    private void addSourceGroup(AssetSourceKind source) {
        boolean remote = source == AssetSourceKind.REMOTE;
        boolean expanded = mExpanded.get(source);
        LinearLayout header = createRow(true, () -> {
            mExpanded.put(source, !mExpanded.get(source));
            buildSidebar();
        });
        addIcon(header, SvgIconView.Icon.forExpandedState(expanded),
                UiThemes.EDITOR.text().muted());
        addIcon(header, remote ? SvgIconView.Icon.CLOUD : SvgIconView.Icon.FOLDER,
                remote ? UiThemes.EDITOR.decoration().remote() : FOLDER_COLOR);
        addLabel(header, remote ? "远程服务器" : "本地", true);
        mSidebar.addView(header);
        if (!expanded) return;

        boolean available = !remote || mCoordinator.canBrowseRemote();
        LinearLayout content = verticalLayout();
        mSidebar.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AssetLocation common = remote ? new AssetLocation.Remote("", false)
                : local(AssetBrowserPathPolicy.getCommonDir());
        AssetLocation packs = remote ? new AssetLocation.Remote("worldpack:", false)
                : local(AssetBrowserPathPolicy.getPacksDir());
        // Common/Packs are deliberately text-only children of their source.
        addDestination(content, "Common", common, null, 0, available);
        addDestination(content, "Packs", packs, null, 0, available);
        if (remote) addShortcuts(content, source, available);
    }

    /** Disks and local shortcuts remain visible independently of the Common/Packs group. */
    private void addLocalLocations() {
        View separator = new View(getContext());
        separator.setBackground(solid(UiThemes.EDITOR.border().divider()));
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(1));
        separatorParams.setMargins(dp2pxInt(14), dp2pxInt(13), dp2pxInt(14), dp2pxInt(7));
        mSidebar.addView(separator, separatorParams);
        for (File root : AssetBrowserPathPolicy.listRootDirectories()) {
            addDestination(mSidebar, root.getPath(), local(root), SvgIconView.Icon.FOLDER,
                    FOLDER_COLOR, true);
        }
        addShortcuts(mSidebar, AssetSourceKind.LOCAL, true);
    }

    private void addShortcuts(LinearLayout parent, AssetSourceKind source, boolean available) {
        QuickAccessListLayout shortcuts = new QuickAccessListLayout(getContext(), source);
        mQuickLists.add(shortcuts);
        parent.addView(shortcuts, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (AssetQuickAccessStore.Entry entry : mQuickAccess.entries(source)) {
            shortcuts.addView(createQuickAccessRow(shortcuts, entry, available));
        }
    }

    private void addDestination(LinearLayout parent, String label, AssetLocation location,
                                SvgIconView.Icon icon, int tint, boolean enabled) {
        LinearLayout row = destinationRow(location, enabled);
        if (icon == null) {
            row.setPadding(dp2pxInt(ICON_SLOT * 2 + 12), 0, 0, 0);
        } else {
            // Reserve the chevron column: disk icons share the source icon's left edge.
            row.addView(new View(getContext()), new LinearLayout.LayoutParams(dp2pxInt(ICON_SLOT), 1));
            addIcon(row, icon, tint);
        }
        addLabel(row, label, enabled);
        parent.addView(row);
    }

    private LinearLayout createQuickAccessRow(QuickAccessListLayout list,
                                              AssetQuickAccessStore.Entry entry, boolean enabled) {
        LinearLayout row = destinationRow(entry.location(), enabled);
        FrameLayout handle = new FrameLayout(getContext());
        FrameLayout.LayoutParams handleIcon = new FrameLayout.LayoutParams(dp2pxInt(ICON_SIZE), dp2pxInt(ICON_SIZE));
        handleIcon.gravity = Gravity.CENTER;
        handle.addView(new SvgIconView(getContext(), SvgIconView.Icon.DRAG_HANDLE,
                UiThemes.EDITOR.text().hint()), handleIcon);
        row.addView(handle, new LinearLayout.LayoutParams(dp2pxInt(ICON_SLOT), ViewGroup.LayoutParams.MATCH_PARENT));
        handle.setContentDescription("拖动以排序");
        handle.setEnabled(enabled);
        handle.setOnTouchListener(new ShortcutDrag(list, row, entry.key()));
        boolean remote = list.source == AssetSourceKind.REMOTE;
        addIcon(row, remote ? SvgIconView.Icon.CLOUD : SvgIconView.Icon.FOLDER,
                remote ? UiThemes.EDITOR.decoration().remote() : FOLDER_COLOR);
        addLabel(row, entry.label(), enabled);
        UiIconButton remove = UiIconButton.createThemed(getContext(), UiThemes.EDITOR,
                new SvgIconView(getContext(), SvgIconView.Icon.CLOSE, UiThemes.EDITOR.status().error()),
                UiIconButton.Preset.INLINE).tooltip(Component.translatable(
                        "geometry_node.asset_library.action.remove_quick_access").getString());
        remove.setEnabled(enabled);
        remove.setOnClickListener(view -> {
            mQuickAccess.remove(list.source, entry.key());
            buildSidebar();
        });
        row.addView(remove, new LinearLayout.LayoutParams(dp2pxInt(ROW_HEIGHT),
                ViewGroup.LayoutParams.MATCH_PARENT));
        return row;
    }

    private LinearLayout destinationRow(AssetLocation location, boolean enabled) {
        LinearLayout row = createRow(enabled, () -> navigate(location));
        String key = selectionKey(location);
        mSelectionRows.add(new SelectionRow(row, key));
        row.setSelected(key.equals(mSelectedKey));
        return row;
    }

    private void navigate(AssetLocation location) {
        if (!mCoordinator.allowsSource(location.sourceKind())) return;
        if (location instanceof AssetLocation.Remote remote) {
            if (!mCoordinator.canBrowseRemote()) return;
            setLocation(location);
            mCoordinator.dispatchNavigateToRemote(remote.directory());
        } else if (location instanceof AssetLocation.Local local) {
            setLocation(location);
            if (local.favorites()) mCoordinator.dispatchNavigateToFavorites();
            else mCoordinator.dispatchNavigateTo(local.directory());
        }
    }

    private LinearLayout createRow(boolean enabled, Runnable action) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(ROW_HEIGHT)));
        int normal = UiThemes.EDITOR.surface().panel();
        int hover = UiThemes.EDITOR.selection().subtleHover();
        int pressed = UiThemes.EDITOR.selection().subtlePressed();
        int selected = UiThemes.EDITOR.selection().subtle();
        ShapeDrawable background = solid(normal);
        background.setColor(new ColorStateList(new int[][]{
                {-R.attr.state_enabled, R.attr.state_selected}, {-R.attr.state_enabled},
                {R.attr.state_pressed}, {R.attr.state_selected}, {R.attr.state_hovered}, {}
        }, new int[]{selected, normal, pressed, selected, hover, normal}));
        row.setBackground(background);
        row.setEnabled(enabled);
        row.setOnClickListener(view -> {
            if (row.isEnabled()) action.run();
        });
        bindTapSlop(row);
        return row;
    }

    private void bindTapSlop(View row) {
        row.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        downX = event.getRawX();
                        downY = event.getRawY();
                        moved = false;
                    }
                    case MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                        if (!moved && Math.hypot(event.getRawX() - downX, event.getRawY() - downY) > mTouchSlop) {
                            moved = true;
                            MotionEvent cancel = event.copy();
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            try { row.onTouchEvent(cancel); }
                            finally { cancel.recycle(); }
                        }
                        return moved;
                    }
                    case MotionEvent.ACTION_CANCEL -> moved = false;
                }
                return false;
            }
        });
    }

    private SvgIconView addIcon(LinearLayout row, SvgIconView.Icon kind, int tint) {
        SvgIconView icon = new SvgIconView(getContext(), kind, tint);
        int inset = dp2pxInt((ICON_SLOT - ICON_SIZE) / 2);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp2pxInt(ICON_SIZE), dp2pxInt(ICON_SIZE));
        params.setMargins(inset, 0, inset, 0);
        row.addView(icon, params);
        return icon;
    }

    private void addLabel(LinearLayout row, String text, boolean enabled) {
        TextView label = UIUtils.createLockedTextView(getContext(), text, 14,
                enabled ? UiThemes.EDITOR.text().normal() : UiThemes.EDITOR.text().disabled());
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp2pxInt(6), 0, dp2pxInt(10), 0);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static AssetLocation.Local local(File directory) {
        return new AssetLocation.Local(directory, false, List.of());
    }

    private static String selectionKey(AssetLocation location) {
        if (location instanceof AssetLocation.Remote remote) return "remote:" + remote.directory();
        if (location instanceof AssetLocation.Local local) {
            if (local.favorites()) return "favorites";
            if (local.directory() != null) {
                try { return "local:" + local.directory().getCanonicalPath(); }
                catch (Exception ignored) { return "local:" + local.directory().getAbsolutePath(); }
            }
        }
        return "";
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        int generation = ++mAttachmentGeneration;
        mConfigListener = config -> post(() -> {
            if (!isAttachedToWindow() || generation != mAttachmentGeneration) return;
            QuickAccessSnapshot snapshot = QuickAccessSnapshot.of(config);
            if (!Objects.equals(mConfigSnapshot, snapshot)) {
                mConfigSnapshot = snapshot;
                buildSidebar();
            }
        });
        ConfigManager.INSTANCE.addChangeListener(mConfigListener);
        // Reading a baseline belongs to listener setup; all mutations go through the store.
        mConfigSnapshot = QuickAccessSnapshot.of(ConfigManager.INSTANCE.getConfig());
        buildSidebar();
    }

    @Override
    protected void onDetachedFromWindow() {
        ++mAttachmentGeneration;
        ConfigManager.INSTANCE.removeChangeListener(mConfigListener);
        mConfigListener = null;
        for (QuickAccessListLayout list : mQuickLists) list.cancelDrag();
        super.onDetachedFromWindow();
    }

    private record QuickAccessSnapshot(List<String> local, Map<String, List<String>> remote) {
        static QuickAccessSnapshot of(AppConfig config) {
            Map<String, List<String>> remote = new LinkedHashMap<>();
            config.assetBrowser.remoteQuickAccessPaths.forEach((server, paths) -> remote.put(server, List.copyOf(paths)));
            return new QuickAccessSnapshot(List.copyOf(config.assetBrowser.quickAccessPaths), Map.copyOf(remote));
        }
    }

    private record SelectionRow(View view, String key) {}

    /** Coordinates only this source's custom rows; fixed rows can never enter reorder indices. */
    private final class ShortcutDrag implements View.OnTouchListener {
        private final QuickAccessListLayout list;
        private final View row;
        private final String key;
        private float downX;
        private float downY;
        private boolean tracking;
        private boolean dragging;

        ShortcutDrag(QuickAccessListLayout list, View row, String key) {
            this.list = list;
            this.row = row;
            this.key = key;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    if (!row.isEnabled()) return false;
                    list.cancelDrag();
                    list.activeDrag = this;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    tracking = true;
                    requestDisallowInterceptTouchEvent(true);
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return true;
                    float dy = Math.abs(event.getRawY() - downY);
                    if (!dragging && dy > mTouchSlop) {
                        dragging = true;
                        row.setAlpha(0.4f);
                    } else if (!dragging && Math.abs(event.getRawX() - downX) > mTouchSlop) {
                        list.cancelDrag();
                        return true;
                    }
                    if (dragging) list.showDrop(event.getRawX(), event.getRawY());
                }
                case MotionEvent.ACTION_UP -> {
                    int insertion = tracking && dragging ? list.insertionAt(event.getRawX(), event.getRawY()) : -1;
                    list.cancelDrag();
                    if (insertion >= 0) {
                        mQuickAccess.move(list.source, key, insertion);
                        buildSidebar();
                    }
                }
                case MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> list.cancelDrag();
            }
            return true;
        }

        void cancel() {
            tracking = false;
            dragging = false;
            row.setAlpha(1);
            requestDisallowInterceptTouchEvent(false);
        }
    }

    private final class QuickAccessListLayout extends LinearLayout {
        private final AssetSourceKind source;
        private final Paint indicatorPaint = new Paint();
        private final int[] screenLocation = new int[2];
        private int indicatorY = -1;
        private ShortcutDrag activeDrag;

        QuickAccessListLayout(Context context, AssetSourceKind source) {
            super(context);
            this.source = source;
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            indicatorPaint.setColor(UiThemes.EDITOR.selection().outline());
        }

        int insertionAt(float rawX, float rawY) {
            getLocationOnScreen(screenLocation);
            float x = rawX - screenLocation[0];
            float y = rawY - screenLocation[1];
            if (x < 0 || x > getWidth() || y < 0 || y > getHeight()) return -1;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (y < (child.getTop() + child.getBottom()) / 2f) return i;
            }
            return getChildCount();
        }

        void showDrop(float rawX, float rawY) {
            int insertion = insertionAt(rawX, rawY);
            indicatorY = insertion < 0 ? -1 : insertion == getChildCount()
                    ? getChildAt(insertion - 1).getBottom() : getChildAt(insertion).getTop();
            invalidate();
        }

        void cancelDrag() {
            if (activeDrag != null) activeDrag.cancel();
            activeDrag = null;
            indicatorY = -1;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (indicatorY >= 0) {
                int halfHeight = dp2pxInt(2);
                int y = Math.max(halfHeight, Math.min(getHeight() - halfHeight, indicatorY));
                canvas.drawRect(0, y - halfHeight, getWidth(), y + halfHeight, indicatorPaint);
            }
        }
    }

}
