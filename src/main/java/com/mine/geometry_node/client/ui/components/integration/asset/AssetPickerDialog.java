package com.mine.geometry_node.client.ui.components.integration.asset;

import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.feature.asset.remote.RemoteAssetClient;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.integration.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.components.integration.asset.navigation.AssetNavigationPanel;
import com.mine.geometry_node.client.feature.asset.repository.AssetLocation;
import com.mine.geometry_node.client.feature.asset.repository.AssetRepositoryOperation;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.overlay.OverlayHandle;
import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import com.mine.geometry_node.client.ui.overlay.OverlayHost;
import com.mine.geometry_node.core.engine.system.asset.AssetDescriptor;
import com.mine.geometry_node.core.engine.system.asset.AssetMetadata;
import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryPath;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Single-selection modal backed by the shared asset browser and the anchor's overlay host. */
public class AssetPickerDialog extends ModalWindowView implements AssetBrowserCoordinator {
    private final LinearLayout mPanel;
    private final UiTheme mTheme;
    private final AssetPickerOptions mOptions;
    private final Consumer<AssetEntry> mOnPick;
    private final AssetFileBrowserPanel mBrowser;
    private final AssetNavigationPanel mSidebar;
    private final TextView mStatus;
    private final CapabilityRequests mCapabilityRequests;
    private String mPendingRemoteDirectory;
    private int mCapabilityRequestId;
    private boolean mDestroyed;
    private boolean mCompleted;

    protected interface CapabilityRequests {
        void request(int id, Consumer<Boolean> available, Runnable unavailable);
        void cancel(int id);
    }

    protected static final CapabilityRequests REMOTE_CAPABILITIES = new CapabilityRequests() {
        @Override public void request(int id, Consumer<Boolean> available, Runnable unavailable) {
            RemoteAssetClient.requestCapabilities(id, response -> available.accept(response.canBrowse()), unavailable);
        }
        @Override public void cancel(int id) { RemoteAssetClient.cancel(id); }
    };

    public static AssetPickerDialog show(View anchor, String title, AssetPickerOptions options,
                                         Consumer<AssetEntry> onPick) {
        return show(anchor, title, options, onPick, UiThemes.EDITOR);
    }

    public static AssetPickerDialog show(View anchor, String title, AssetPickerOptions options,
                                         Consumer<AssetEntry> onPick, UiTheme theme) {
        if (anchor == null) return null;
        AssetPickerDialog picker = new AssetPickerDialog(anchor.getContext(), title, options, onPick, theme);
        picker.show(anchor);
        return picker;
    }

    public AssetPickerDialog(Context context, String title, AssetPickerOptions options, Consumer<AssetEntry> onPick) {
        this(context, title, options, onPick, UiThemes.EDITOR);
    }

    public AssetPickerDialog(Context context, String title, AssetPickerOptions options,
                             Consumer<AssetEntry> onPick, UiTheme theme) {
        this(context, title, options, onPick, REMOTE_CAPABILITIES, theme);
    }

    protected AssetPickerDialog(Context context, String title, AssetPickerOptions options,
                                Consumer<AssetEntry> onPick, CapabilityRequests capabilityRequests) {
        this(context, title, options, onPick, capabilityRequests, UiThemes.EDITOR);
    }

    protected AssetPickerDialog(Context context, String title, AssetPickerOptions options,
                                Consumer<AssetEntry> onPick, CapabilityRequests capabilityRequests, UiTheme theme) {
        super(context, title, MovementMode.DRAGGABLE, Style.forTheme(theme));
        mTheme = theme;
        mPanel = new LinearLayout(context);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        setContent(mPanel);
        mOptions = Objects.requireNonNull(options, "options");
        mOnPick = onPick;
        mCapabilityRequests = Objects.requireNonNull(capabilityRequests, "capabilityRequests");
        setPreferredSizeDp(920, 0);

        mBrowser = AssetFileBrowserPanel.picker(context, this);
        mBrowser.setAllowedSources(options.sources().allowedSources());
        mBrowser.setCreationAllowed(options.target().allowsFolders(), false);
        mBrowser.setEntryFilter(options::isVisible);
        mBrowser.setPickFileAction(options.target().allowsFiles() ? this::chooseEntry : null);
        mBrowser.setPickCurrentDirectoryAction(options.target().allowsFolders() ? this::chooseCurrentDirectory : null);

        LinearLayout browserArea = new LinearLayout(context);
        browserArea.setOrientation(LinearLayout.HORIZONTAL);
        browserArea.setBackground(rect(theme.surface().root(), UiMetrics.RADIUS_INPUT));
        mSidebar = new AssetNavigationPanel(context, this);
        browserArea.addView(mSidebar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .22f));
        browserArea.addView(ThinResizableDivider.weighted(context, ThinResizableDivider.Orientation.HORIZONTAL));
        browserArea.addView(mBrowser, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .78f));
        mPanel.addView(browserArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(430)));

        mStatus = label(context, "", 12, theme.status().warning());
        mPanel.addView(mStatus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(22)));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        if (options.target().allowsFolders()) {
            addAction(actions, tr("create_folder"), UiActionButton.Role.QUIET, this::createFolder, 108);
        }
        actions.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
        addAction(actions, tr("cancel"), UiActionButton.Role.SECONDARY, this::requestClose, 92);
        if (options.target().allowsFolders()) {
            addAction(actions, tr("select_current_folder"), UiActionButton.Role.QUIET, this::chooseCurrentDirectory, 150);
        }
        addAction(actions, tr(options.target() == AssetPickerOptions.Target.FOLDER ? "select_folder" : "select_file"),
                UiActionButton.Role.PRIMARY, this::chooseSelectedEntry, 108);
        setActions(actions);
        ViewGroup.LayoutParams actionParams = actions.getLayoutParams();
        actionParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        actions.setLayoutParams(actionParams);

        navigateInitial(options.initialDirectory());
        mSidebar.setLocation(mBrowser.locationSnapshot());
        mBrowser.setLocationChangedListener(() -> {
            if (mDestroyed) return;
            mPendingRemoteDirectory = null;
            mSidebar.setLocation(mBrowser.locationSnapshot());
        });
        if (options.sources().allows(AssetSourceKind.REMOTE)) requestRemoteCapabilities();
    }

    private void addAction(LinearLayout actions, String text, UiActionButton.Role role, Runnable action, int width) {
        UiActionButton button = new UiActionButton(getContext(), mTheme, text, role, UiActionButton.Density.NORMAL,
                UiActionButton.Style.forTheme(mTheme));
        UIUtils.setLockedTextSize(button, 14);
        button.setSingleLine(true);
        button.setPadding(UIUtils.dp2pxInt(14), 0, UIUtils.dp2pxInt(14), 0);
        button.setMinimumWidth(UIUtils.dp2pxInt(width));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UIUtils.dp2pxInt(32));
        if (actions.getChildCount() > 0) params.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(button, params);
    }

    private void chooseSelectedEntry() {
        List<AssetEntry> selected = mBrowser.getSelectedEntriesSnapshot();
        if (selected.size() == 1 && mOptions.canSelect(selected.getFirst())) {
            chooseEntry(selected.getFirst());
        } else {
            mStatus.setText(tr("invalid_selection"));
        }
    }

    private void chooseEntry(AssetEntry entry) {
        if (mDestroyed || mCompleted) return;
        if (!mOptions.canSelect(entry)) {
            mStatus.setText(tr("invalid_selection"));
            return;
        }
        mCompleted = true;
        if (!requestClose()) {
            mCompleted = false;
            return;
        }
        if (mOnPick != null) mOnPick.accept(entry);
    }

    private void chooseCurrentDirectory() {
        if (mBrowser.isFavoritesMode()) {
            mStatus.setText(tr("favorites_no_directory"));
            return;
        }
        AssetEntry entry;
        if (mBrowser.getSourceKind() == AssetSourceKind.REMOTE) {
            if (!mBrowser.hasVerifiedCurrentDirectory()) {
                mStatus.setText(tr("invalid_selection"));
                return;
            }
            String path = mBrowser.getRemoteDirectory();
            AssetRepositoryPath address = AssetRepositoryPath.parse(path, true);
            String name = address.isRoot() ? address.isCommon() ? "Common" : "Packs" : address.name();
            entry = AssetEntry.remote(new AssetDescriptor(path, name, true, 0, 0, AssetMetadata.UNKNOWN));
        } else {
            File directory = mBrowser.getCurrentDirectory();
            entry = directory != null && directory.isDirectory()
                    ? AssetEntry.local(directory, directory.getAbsolutePath(), directory.getAbsolutePath()) : null;
        }
        chooseEntry(entry);
    }

    private void createFolder() {
        if (mDestroyed || mBrowser.isFavoritesMode()) return;
        if (mBrowser.getSourceKind() == AssetSourceKind.REMOTE) mBrowser.createRemoteFolderInCurrentDirectory();
        else mBrowser.createLocalFolderInCurrentDirectory();
    }

    private void navigateInitial(AssetLocation initial) {
        if (initial instanceof AssetLocation.Remote remote) {
            if (mOptions.sources() == AssetPickerOptions.Sources.REMOTE || canBrowseRemote()) {
                mBrowser.navigateToRemote(remote.directory());
                return;
            }
            // BOTH preserves the legacy local fallback while capability discovery is pending.
            mPendingRemoteDirectory = remote.directory();
        } else if (initial instanceof AssetLocation.Local local && local.directory().isDirectory()) {
            mBrowser.navigateTo(local.directory());
            return;
        }
        if (mOptions.sources() == AssetPickerOptions.Sources.REMOTE) {
            mBrowser.navigateToRemoteRoot();
        } else {
            File drafts = AssetBrowserPathPolicy.getCommonDir();
            if (!drafts.exists()) drafts.mkdirs();
            mBrowser.navigateTo(drafts);
        }
    }

    private void requestRemoteCapabilities() {
        int id = RemoteAssetClient.nextRequestId();
        mCapabilityRequestId = id;
        try {
            mCapabilityRequests.request(id, available -> post(() -> {
                if (mDestroyed || mCapabilityRequestId != id) return;
                mCapabilityRequestId = 0;
                notifySidebarChanged();
                if (!available) {
                    mStatus.setText(tr("remote_unavailable"));
                } else if (mPendingRemoteDirectory != null) {
                    String target = mPendingRemoteDirectory;
                    mPendingRemoteDirectory = null;
                    mBrowser.navigateToRemote(target);
                } else if (mBrowser.getSourceKind() == AssetSourceKind.REMOTE) {
                    mBrowser.refreshFileList();
                }
            }), () -> post(() -> {
                if (mDestroyed || mCapabilityRequestId != id) return;
                mCapabilityRequestId = 0;
                mStatus.setText(tr("remote_unavailable"));
            }));
        } catch (RuntimeException failure) {
            mCapabilityRequestId = 0;
            mStatus.setText(tr("remote_unavailable"));
        }
    }

    @Override protected final void onWindowDestroyed() {
        destroyPicker();
        onAssetDialogDestroyed();
    }

    private void destroyPicker() {
        if (mDestroyed) return;
        mDestroyed = true;
        mPendingRemoteDirectory = null;
        mBrowser.deactivatePanel();
        int id = mCapabilityRequestId;
        mCapabilityRequestId = 0;
        if (id != 0) mCapabilityRequests.cancel(id);
    }

    /** Optional adapter cleanup; picker cleanup always runs first. */
    protected void onAssetDialogDestroyed() { }

    @Override public void dispatchNavigateTo(File directory) {
        if (!mDestroyed && mOptions.sources().allows(AssetSourceKind.LOCAL)) mBrowser.navigateTo(directory);
    }

    @Override public void dispatchNavigateToFavorites() {
        if (!mDestroyed && mOptions.sources().allows(AssetSourceKind.LOCAL)) mBrowser.navigateToFavorites();
    }

    @Override public void dispatchNavigateToRemoteRoot() {
        dispatchNavigateToRemote("");
    }

    @Override public void dispatchNavigateToRemote(String directory) {
        if (!mDestroyed && canBrowseRemote()) mBrowser.navigateToRemote(directory);
    }

    @Override public boolean allowsSource(AssetSourceKind source) {
        return mOptions.sources().allows(source);
    }

    @Override public boolean canBrowseRemote() {
        return mOptions.sources().allows(AssetSourceKind.REMOTE) && mBrowser != null
                && mBrowser.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE);
    }

    @Override public void notifySidebarChanged() {
        if (mSidebar != null) mSidebar.buildSidebar();
    }

    @Override public void showUploadDialog(List<File> files) { }
    @Override public void showDownloadDialog(List<AssetEntry> entries) { }

    public final OverlayHandle show(View anchor) {
        bindSessionOwner(anchor);
        return java.util.Objects.requireNonNull(OverlayHost.find(anchor), "No overlay host for asset dialog").showModal(this,
                new ModalOptions(false, true, anchor, mTheme.surface().modalScrimLight()));
    }

    private TextView label(Context context, String text, float size, int color) {
        TextView label = new TextView(context);
        label.setText(text);
        UIUtils.setLockedTextSize(label, size);
        label.setTextColor(color);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private ShapeDrawable rect(int color, float radius) {
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(color);
        background.setCornerRadius(UIUtils.dp2px(radius));
        return background;
    }

    private static String tr(String key) {
        return Component.translatable("geometry_node.asset_picker." + key).getString();
    }
}
