package com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetPathUtils;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.left.LeftQuickAccessPanel;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.right.RightFileBrowserPanel;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.utils.PanelSplitter;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphCapabilitiesRequest;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class FilePickerDialog extends AssetDialogBase implements AssetBrowserCoordinator {
    private static final float WINDOW_WIDTH = 920.0f;
    private static final float BROWSER_HEIGHT = 430.0f;
    private static final float LEFT_WIDTH = 190.0f;

    private final LeftQuickAccessPanel mLeftPanel;
    private final RightFileBrowserPanel mBrowser;
    private final Consumer<String> mOnPick;
    private final TextView mStatus;
    private String mPendingRemoteInitialDirectory;

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
        ViewGroup host = findWindowHost(anchor);
        if (host == null) {
            return null;
        }
        FilePickerDialog dialog = path(anchor.getContext(), "选择路径", initialPath, onPick);
        dialog.showIn(host);
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

        mLeftPanel = new LeftQuickAccessPanel(context, this);
        browserArea.addView(mLeftPanel, new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(LEFT_WIDTH),
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        browserArea.addView(PanelSplitter.create(context, true));

        mBrowser = RightFileBrowserPanel.picker(context, this);
        mBrowser.setPickFileAction(this::chooseEntry);
        mBrowser.setPickCurrentDirectoryAction(this::chooseCurrentDirectory);
        browserArea.addView(mBrowser, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f
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
        Button cancel = button(context, "取消", 0xFF4A4A4A);
        cancel.setOnClickListener(v -> dismiss());
        Button chooseDirectory = button(context, "选择当前文件夹", 0xFF4A5563);
        chooseDirectory.setOnClickListener(v -> chooseCurrentDirectory());
        Button choose = button(context, "选择路径", 0xFF2F7DDE);
        choose.setOnClickListener(v -> chooseSelectedFile());
        actions.addView(cancel, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));
        LinearLayout.LayoutParams chooseDirectoryLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(126), UIUtils.dp2pxInt(32));
        chooseDirectoryLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(chooseDirectory, chooseDirectoryLp);
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(100), UIUtils.dp2pxInt(32));
        chooseLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(choose, chooseLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(36)
        );
        actionsLp.topMargin = UIUtils.dp2pxInt(8);
        mPanel.addView(actions, actionsLp);

        navigateInitial(initialPath);
        requestRemoteCapabilities();
    }

    @Override
    public void showIn(ViewGroup parent) {
        super.showIn(parent);
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
        if (mOnPick != null) {
            mOnPick.accept(toNodePath(entry));
        }
        dismiss();
    }

    private boolean isPickableFile(AssetEntry entry) {
        return entry != null
                && !entry.isDirectory()
                && (entry.sourceKind() == AssetSourceKind.REMOTE || entry.localFile() != null);
    }

    private void chooseCurrentDirectory() {
        String selectedPath = currentDirectoryPath();
        if (selectedPath.isEmpty()) {
            mStatus.setText("当前没有可选择的文件夹。");
            return;
        }
        if (mOnPick != null) {
            mOnPick.accept(selectedPath);
        }
        dismiss();
    }

    private String currentDirectoryPath() {
        if (mBrowser.getSourceKind() == AssetSourceKind.REMOTE) {
            return mBrowser.getRemoteDirectory();
        }
        File directory = mBrowser.getCurrentDirectory();
        return directory == null ? "" : absolutePath(directory);
    }

    private static String toNodePath(AssetEntry entry) {
        if (entry == null) return "";
        if (entry.sourceKind() == AssetSourceKind.REMOTE) {
            return entry.path();
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
        if (remoteDirectory != null && RemoteGraphClientState.canBrowse()) {
            mBrowser.navigateToRemote(remoteDirectory);
            return;
        }
        mPendingRemoteInitialDirectory = remoteDirectory;
        mBrowser.navigateTo(localDirectory != null ? localDirectory : AssetBrowserPathPolicy.getLocalDraftsDir());
    }

    private void requestRemoteCapabilities() {
        int requestId = RemoteGraphClientState.nextRequestId();
        RemoteGraphClientState.onCapabilities(requestId, response -> post(() -> {
            mLeftPanel.buildSidebar();
            if (response.canBrowse() && mPendingRemoteInitialDirectory != null) {
                String target = mPendingRemoteInitialDirectory;
                mPendingRemoteInitialDirectory = null;
                mBrowser.navigateToRemote(target);
            }
        }));
        NetworkHandler.sendToServer(new PacketRemoteGraphCapabilitiesRequest(requestId));
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
        if (value.startsWith("remote:/") || value.startsWith("remote://")) {
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
        if (RemoteGraphClientState.canBrowse()) {
            mBrowser.navigateToRemoteRoot();
        }
    }

    @Override
    public boolean canBrowseRemote() {
        return RemoteGraphClientState.canBrowse();
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

    private static ViewGroup findWindowHost(View anchor) {
        View current = anchor;
        ViewGroup best = anchor instanceof ViewGroup viewGroup ? viewGroup : null;
        while (current != null) {
            if (current instanceof FrameLayout frameLayout) {
                best = frameLayout;
            }
            if (!(current.getParent() instanceof View parentView)) {
                break;
            }
            current = parentView;
        }
        return best != null ? best : anchor.getParent() instanceof ViewGroup parent ? parent : null;
    }
}
