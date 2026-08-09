package com.mine.geometry_node.client.ui.editor.asset.dialog;

import com.mine.geometry_node.client.ui.common.UiActionButton;
import com.mine.geometry_node.client.ui.editor.asset.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.editor.asset.AssetPathUtils;
import com.mine.geometry_node.client.ui.editor.asset.navigation.AssetNavigationPanel;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetRepositoryOperation;
import com.mine.geometry_node.client.ui.editor.asset.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.editor.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.ui.common.ResizableDivider;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphCapabilitiesRequest;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class FilePickerDialog extends AssetDialogBase implements AssetBrowserCoordinator {
    private static final float WINDOW_WIDTH = 920.0f;
    private static final float BROWSER_HEIGHT = 430.0f;

    private final AssetNavigationPanel mLeftPanel;
    private final AssetFileBrowserPanel mBrowser;
    private final Consumer<String> mOnPick;
    private final TextView mStatus;
    private String mPendingRemoteInitialDirectory;
    private int mCapabilityRequestId;

    public static FilePickerDialog path(Context context, String title, String initialPath, Consumer<String> onPick) {
        return new FilePickerDialog(context, title, initialPath, onPick);
    }

    public static FilePickerDialog schematic(Context context, String title, File initialDirectory, Consumer<String> onPick) {
        return new FilePickerDialog(context, title, initialDirectory == null ? "" : initialDirectory.getAbsolutePath(), onPick);
    }

    public static FilePickerDialog showPath(View anchor, String initialPath, Consumer<String> onPick) {
        if (anchor == null) {
            return null;
        }
        FilePickerDialog dialog = path(anchor.getContext(), "选择路径", initialPath, onPick);
        dialog.show(anchor);
        return dialog;
    }

    public static FilePickerDialog showSchematic(View anchor, File initialDirectory, Consumer<String> onPick) {
        return showPath(anchor, initialDirectory == null ? "" : initialDirectory.getAbsolutePath(), onPick);
    }

    private FilePickerDialog(Context context, String title, String initialPath, Consumer<String> onPick) {
        super(context, title);
        setWindowSizeDp(WINDOW_WIDTH, 0);
        mOnPick = onPick;

        LinearLayout browserArea = new LinearLayout(context);
        browserArea.setOrientation(LinearLayout.HORIZONTAL);
        browserArea.setBackground(rect(0xFF1F1F1F, 3));

        mLeftPanel = new AssetNavigationPanel(context, this);
        browserArea.addView(mLeftPanel, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                0.22f
        ));

        browserArea.addView(ResizableDivider.weighted(context, ResizableDivider.Orientation.HORIZONTAL));

        mBrowser = AssetFileBrowserPanel.picker(context, this);
        mBrowser.setPickFileAction(this::chooseEntry);
        mBrowser.setPickCurrentDirectoryAction(this::chooseCurrentDirectory);
        browserArea.addView(mBrowser, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                0.78f
        ));

        mPanel.addView(browserArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(BROWSER_HEIGHT)
        ));

        mStatus = label(context, "", 12, 0xFFFFB86C);
        mPanel.addView(mStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(22)
        ));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        UiActionButton cancel = actionButton(context, "取消", UiActionButton.Role.SECONDARY);
        cancel.setOnClickListener(v -> requestClose());
        UiActionButton chooseDirectory = actionButton(context, "选择当前文件夹", UiActionButton.Role.QUIET);
        chooseDirectory.setOnClickListener(v -> chooseCurrentDirectory());
        UiActionButton choose = actionButton(context, "选择路径", UiActionButton.Role.PRIMARY);
        choose.setOnClickListener(v -> chooseSelectedFile());
        actions.addView(cancel, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));
        LinearLayout.LayoutParams chooseDirectoryLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(126), UIUtils.dp2pxInt(32));
        chooseDirectoryLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(chooseDirectory, chooseDirectoryLp);
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(100), UIUtils.dp2pxInt(32));
        chooseLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(choose, chooseLp);
        setActions(actions);

        navigateInitial(initialPath);
        requestRemoteCapabilities();
    }

    private void chooseSelectedFile() {
        List<AssetEntry> selected = mBrowser.getSelectedEntriesSnapshot();
        for (AssetEntry entry : selected) {
            if (isPickableFile(entry)) {
                chooseEntry(entry);
                return;
            }
        }
        mStatus.setText("请选择一个支持的文件。");
    }

    private void chooseEntry(AssetEntry entry) {
        if (!isPickableFile(entry)) {
            mStatus.setText("请选择一个支持的文件。");
            return;
        }
        if (requestClose() && mOnPick != null) {
            mOnPick.accept(toNodePath(entry));
        }
    }

    private boolean isPickableFile(AssetEntry entry) {
        return entry != null
                && !entry.isDirectory()
                && entry.supports(AssetTypeAction.PICK)
                && (entry.sourceKind() == AssetSourceKind.REMOTE || entry.localFile() != null);
    }

    private void chooseCurrentDirectory() {
        String selectedPath = currentDirectoryPath();
        if (selectedPath.isEmpty()) {
            mStatus.setText("当前没有可选择的文件夹。");
            return;
        }
        if (requestClose() && mOnPick != null) {
            mOnPick.accept(selectedPath);
        }
    }

    private String currentDirectoryPath() {
        if (mBrowser.getSourceKind() == AssetSourceKind.REMOTE) {
            return AssetPathUtils.formatRemotePath(mBrowser.getRemoteDirectory());
        }
        File directory = mBrowser.getCurrentDirectory();
        return directory == null ? "" : absolutePath(directory);
    }

    private static String toNodePath(AssetEntry entry) {
        if (entry == null) return "";
        if (entry.sourceKind() == AssetSourceKind.REMOTE) {
            return AssetPathUtils.formatRemotePath(entry.path());
        }
        return absolutePath(entry.localFile());
    }

    private static String absolutePath(File file) {
        if (file == null) return "";
        try {
            return file.getCanonicalPath();
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
    }

    private void navigateInitial(String initialPath) {
        String remoteDirectory = initialRemoteDirectory(initialPath);
        File localDirectory = initialLocalDirectory(initialPath);
        if (remoteDirectory != null
                && mBrowser.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE)) {
            mBrowser.navigateToRemote(remoteDirectory);
            return;
        }
        mPendingRemoteInitialDirectory = remoteDirectory;
        mBrowser.navigateTo(localDirectory != null ? localDirectory : AssetBrowserPathPolicy.getLocalDraftsDir());
    }

    private void requestRemoteCapabilities() {
        int requestId = RemoteGraphClientState.nextRequestId();
        mCapabilityRequestId = requestId;
        RemoteGraphClientState.onCapabilities(requestId, response -> post(() -> {
            if (mCapabilityRequestId != requestId) return;
            mCapabilityRequestId = 0;
            mLeftPanel.buildSidebar();
            if (response.canBrowse() && mPendingRemoteInitialDirectory != null) {
                String target = mPendingRemoteInitialDirectory;
                mPendingRemoteInitialDirectory = null;
                mBrowser.navigateToRemote(target);
            }
        }));
        NetworkHandler.sendToServer(new PacketRemoteGraphCapabilitiesRequest(requestId));
    }

    @Override
    protected void onAssetDialogDestroyed() {
        if (mCapabilityRequestId != 0) {
            RemoteGraphClientState.cancel(mCapabilityRequestId);
            mCapabilityRequestId = 0;
        }
    }

    private static File initialLocalDirectory(String currentPath) {
        String value = currentPath == null ? "" : currentPath.trim();
        if (!value.isEmpty()) {
            File direct = AssetBrowserPathPolicy.resolveConfigPath(value);
            File directDirectory = directoryOfExistingPath(direct);
            if (directDirectory != null) {
                return directDirectory;
            }
        }
        return AssetBrowserPathPolicy.getLocalDraftsDir();
    }

    private static String initialRemoteDirectory(String currentPath) {
        String value = currentPath == null ? "" : currentPath.trim().replace('\\', '/');
        if (value.isEmpty()) return null;
        if (AssetPathUtils.isRemotePathInput(value)) {
            return remoteDirectoryFromPath(AssetPathUtils.remotePathFromInput(value));
        }
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*") || value.matches("^[A-Za-z][A-Za-z0-9+.-]*:/.*")) {
            return null;
        }
        File local = AssetBrowserPathPolicy.resolveConfigPath(value);
        if (directoryOfExistingPath(local) != null) {
            return null;
        }
        return remoteDirectoryFromPath(value);
    }

    private static String remoteDirectoryFromPath(String path) {
        String normalized = AssetPathUtils.remotePathFromInput(path);
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (!name.contains(".")) {
            return normalized;
        }
        return parentRemoteDirectory(normalized);
    }

    private static String parentRemoteDirectory(String normalized) {
        int slash = normalized.lastIndexOf('/');
        return slash > 0 ? normalized.substring(0, slash) : "";
    }

    private static File directoryOfExistingPath(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        return file.isDirectory() ? file : file.getParentFile();
    }

    @Override
    public void dispatchNavigateTo(File directory) {
        mBrowser.navigateTo(directory);
    }

    @Override
    public void dispatchNavigateToFavorites() {
        mBrowser.navigateToFavorites();
    }

    @Override
    public void dispatchNavigateToRemoteRoot() {
        if (mBrowser.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE)) {
            mBrowser.navigateToRemoteRoot();
        }
    }

    @Override
    public boolean canBrowseRemote() {
        return mBrowser.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE);
    }

    @Override
    public void showUploadDialog(List<File> selectedFiles) {
    }

    @Override
    public void showDownloadDialog(List<AssetEntry> remoteEntries) {
    }

    @Override
    public void notifySidebarChanged() {
        mLeftPanel.buildSidebar();
    }

}
