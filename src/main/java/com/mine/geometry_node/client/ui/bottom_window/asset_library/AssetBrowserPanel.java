package com.mine.geometry_node.client.ui.bottom_window.asset_library;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.FolderPickerDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.OverwriteConfirmDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.TransferProgressDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.UploadFailureRetryDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.left.LeftQuickAccessPanel;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.right.AssetGraphPropertiesPanel;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.right.RightFileBrowserPanel;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.service.LocalAssetService;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.task.AssetTaskController;
import com.mine.geometry_node.client.ui.common.ResizableDivider;
import com.mine.geometry_node.client.ui.common.CollapsibleSidebar;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.window.IToolWindow;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphConflict;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphUploadFile;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphCapabilitiesRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphDownloadRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphUploadRequest;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class AssetBrowserPanel extends FrameLayout implements IToolWindow, AssetBrowserCoordinator {

    private final LinearLayout mMainLayout;
    private final LeftQuickAccessPanel mLeftPanel;
    private final RightFileBrowserPanel mRightPanel;
    private final AssetGraphPropertiesPanel mPropertiesPanel;
    private final View mPropertiesDivider;
    private final CollapsibleSidebar mPropertiesSidebar;
    private final LocalAssetService mLocalAssetService = new LocalAssetService();
    private final AssetTaskController mIoTasks;
    private final Set<Integer> mRemoteRequestIds = new HashSet<>();
    private float mLastPropertiesSidebarWeight;

    public AssetBrowserPanel(Context context) {
        super(context);
        mIoTasks = new AssetTaskController(this);

        mMainLayout = new LinearLayout(context);
        mMainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mMainLayout.setBackground(createColorDrawable(UIConstants.MainUI.BG_TIMELINE));
        addView(mMainLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mLeftPanel = new LeftQuickAccessPanel(context, this);
        mMainLayout.addView(mLeftPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.2f));

        mMainLayout.addView(ResizableDivider.weighted(context, ResizableDivider.Orientation.HORIZONTAL));

        AppConfig.AssetBrowserConfig browserConfig = ConfigManager.INSTANCE.getConfig().assetBrowser;
        mLastPropertiesSidebarWeight = browserConfig.rightSidebarWeight;

        mRightPanel = new RightFileBrowserPanel(context, this);
        mMainLayout.addView(mRightPanel, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                0.8f - mLastPropertiesSidebarWeight));

        mPropertiesDivider = ResizableDivider.weighted(context, ResizableDivider.Orientation.HORIZONTAL);
        mMainLayout.addView(mPropertiesDivider);

        mPropertiesPanel = new AssetGraphPropertiesPanel(context, mRightPanel::refreshFileList);
        mPropertiesSidebar = new CollapsibleSidebar(
                context,
                tr("geometry_node.graph_properties.title"),
                () -> setPropertiesSidebarVisible(false, true));
        mPropertiesSidebar.setContent(mPropertiesPanel);
        mMainLayout.addView(mPropertiesSidebar, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                mLastPropertiesSidebarWeight));
        mRightPanel.setSelectionChangedListener(mPropertiesPanel::bindSelection);
        setPropertiesSidebarVisible(browserConfig.rightSidebarVisible, false);

        dispatchNavigateTo(AssetBrowserPathPolicy.getLocalDraftsDir());
        requestRemoteCapabilities();
    }

    /**
     * 跨区协调总线：将来自左侧边栏选中的目录精准分发给右侧文件浏览器
     */
    @Override
    public void dispatchNavigateTo(File directory) {
        if (mRightPanel != null) {
            mRightPanel.navigateTo(directory);
        }
    }

    @Override
    public void dispatchNavigateToFavorites() {
        if (mRightPanel != null) {
            mRightPanel.navigateToFavorites();
        }
    }

    @Override
    public void dispatchNavigateToRemoteRoot() {
        if (RemoteGraphClientState.canBrowse() && mRightPanel != null) {
            mRightPanel.navigateToRemoteRoot();
        }
    }

    @Override
    public boolean canBrowseRemote() {
        return RemoteGraphClientState.canBrowse();
    }

    @Override
    public void showUploadDialog(List<File> selectedFiles) {
        if (selectedFiles == null || selectedFiles.isEmpty()) return;
        FolderPickerDialog dialog = FolderPickerDialog.remote(
                getContext(),
                "上传到服务器",
                "",
                targetDirectory -> preflightUpload(selectedFiles, targetDirectory)
        );
        dialog.showIn(this);
    }

    @Override
    public void showDownloadDialog(List<AssetEntry> remoteEntries) {
        if (remoteEntries == null || remoteEntries.isEmpty()) return;
        FolderPickerDialog dialog = FolderPickerDialog.local(
                getContext(),
                "下载到本地",
                AssetBrowserPathPolicy.getLocalDraftsDir(),
                targetDirectory -> startDownload(remoteEntries, targetDirectory)
        );
        dialog.showIn(this);
    }

    /**
     * 跨区协调总线：当右侧通过 NavBar 的 "+" 添加了新的快速路径后，驱动左侧状态重塑
     */
    @Override
    public void notifySidebarChanged() {
        if (mLeftPanel != null) {
            mLeftPanel.buildSidebar();
        }
        requestRemoteCapabilities();
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
        if (mRightPanel != null) {
            mRightPanel.activatePanel();
        }
        if (mRightPanel != null) {
            mRightPanel.refreshFileList();
        }
        if (mLeftPanel != null) {
            mLeftPanel.buildSidebar();
        }
    }

    @Override
    public void onHide() {
        mPropertiesPanel.commitPendingEdits();
        persistPropertiesSidebarState();
        if (mRightPanel != null) {
            mRightPanel.deactivatePanel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mPropertiesPanel.commitPendingEdits();
        mIoTasks.cancelAll();
        cancelRemoteRequests();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            KeyBinding saveBinding = KeyBinding.parse(
                    ConfigManager.INSTANCE.getConfig().keyBindings.global.save);
            if (saveBinding != null && saveBinding.matches(event)) {
                mPropertiesPanel.commitPendingEdits();
                return true;
            }

            if (findFocus() instanceof EditText) return super.dispatchKeyEvent(event);
            KeyBinding sidebarBinding = KeyBinding.parse(
                    ConfigManager.INSTANCE.getConfig().keyBindings.viewport.toggleRightSidebar);
            if (sidebarBinding != null && sidebarBinding.matches(event)) {
                setPropertiesSidebarVisible(mPropertiesSidebar.getVisibility() != View.VISIBLE, true);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void setPropertiesSidebarVisible(boolean visible, boolean persist) {
        boolean currentlyVisible = mPropertiesSidebar.getVisibility() == View.VISIBLE;
        if (currentlyVisible == visible) {
            if (persist) persistPropertiesSidebarState();
            return;
        }

        if (!visible) {
            mPropertiesPanel.commitPendingEdits();
            rememberPropertiesSidebarWeight();
            transferPropertiesWeightToBrowser(mLastPropertiesSidebarWeight);
        } else {
            transferPropertiesWeightToBrowser(-mLastPropertiesSidebarWeight);
        }
        mPropertiesDivider.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        mPropertiesSidebar.setVisibility(visible ? View.VISIBLE : View.GONE);
        mMainLayout.requestLayout();
        if (persist) persistPropertiesSidebarState();
    }

    private void transferPropertiesWeightToBrowser(float sidebarWeightDelta) {
        if (!(mRightPanel.getLayoutParams() instanceof LinearLayout.LayoutParams browserParams)) return;
        browserParams.weight = Math.max(
                UIConstants.MainUI.WEIGHT_MIN,
                browserParams.weight + sidebarWeightDelta);
        mRightPanel.setLayoutParams(browserParams);
    }

    private void rememberPropertiesSidebarWeight() {
        if (mPropertiesSidebar.getLayoutParams() instanceof LinearLayout.LayoutParams params && params.weight > 0.0f) {
            mLastPropertiesSidebarWeight = params.weight;
        }
    }

    private void persistPropertiesSidebarState() {
        rememberPropertiesSidebarWeight();
        boolean visible = mPropertiesSidebar.getVisibility() == View.VISIBLE;
        float weight = mLastPropertiesSidebarWeight;
        ConfigManager.INSTANCE.update(config -> {
            config.assetBrowser.rightSidebarVisible = visible;
            config.assetBrowser.rightSidebarWeight = weight;
        });
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private void requestRemoteCapabilities() {
        int requestId = beginRemoteRequest();
        RemoteGraphClientState.onCapabilities(requestId, response -> {
            finishRemoteRequest(requestId);
            post(() -> {
                if (mLeftPanel != null) {
                    mLeftPanel.buildSidebar();
                }
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphCapabilitiesRequest(requestId));
    }

    private void preflightUpload(List<File> selectedFiles, String targetDirectory) {
        List<File> selectedSnapshot = selectedFiles == null ? List.of() : List.copyOf(selectedFiles);
        mIoTasks.run("准备上传",
                context -> mLocalAssetService.collectUploadFiles(selectedSnapshot, targetDirectory, context),
                (result, progress) -> {
                    if (result.files().isEmpty()) {
                        progress.fail(result.failedPaths().isEmpty()
                                ? "没有可上传的 .json 图纸"
                                : "文件读取失败: " + summarizePaths(result.failedPaths()));
                        return;
                    }
                    if (!result.failedPaths().isEmpty()) {
                        System.err.println("[AssetBrowser] Some upload files failed to read: " + result.failedPaths());
                    }
                    progress.update("准备完成", result.files().size(), result.files().size());
                    requestUploadPreflight(result.files());
                });
    }

    private void requestUploadPreflight(List<RemoteGraphUploadFile> files) {
        int requestId = beginRemoteRequest();
        RemoteGraphClientState.onUpload(requestId, response -> {
            if (response.terminal()) finishRemoteRequest(requestId);
            post(() -> {
                if (!response.preflight()) return;
                if (!response.success() && response.conflicts().isEmpty()) {
                    showUploadFailureDialog(files, false, List.of());
                    return;
                }
                if (response.conflicts().isEmpty()) {
                    startUpload(files, false);
                    return;
                }
                List<String> conflictPaths = new ArrayList<>();
                for (RemoteGraphConflict conflict : response.conflicts()) {
                    conflictPaths.add(conflict.targetPath());
                }
                ConflictResolutionState<RemoteGraphUploadFile> resolution =
                        new ConflictResolutionState<>(files, conflictPaths, RemoteGraphUploadFile::targetPath);
                new OverwriteConfirmDialog(getContext(), conflictPaths, decision -> {
                    switch (decision) {
                        case OVERWRITE_CURRENT -> {
                            if (resolution.markCurrentOverwrite()) {
                                startUpload(resolution.remainingFiles(), false, resolution.overwritePaths());
                            }
                        }
                        case OVERWRITE_ALL -> startUpload(files, true, List.of());
                        case SKIP_CURRENT -> {
                            if (resolution.markCurrentSkip()) {
                                startUpload(resolution.remainingFiles(), false, resolution.overwritePaths());
                            }
                        }
                        case SKIP_ALL -> startUpload(resolution.withoutConflicts(), false, List.of());
                        case CANCEL -> {
                        }
                    }
                }).showIn(this);
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphUploadRequest(requestId, true, false, files));
    }

    private void startUpload(List<RemoteGraphUploadFile> files, boolean overwrite) {
        startUpload(files, overwrite, List.of());
    }

    private void startUpload(List<RemoteGraphUploadFile> files, boolean overwrite, List<String> overwritePaths) {
        if (files.isEmpty()) return;
        UploadBatchRunner runner = new UploadBatchRunner(files, overwrite, overwritePaths);
        runner.sendNext();
    }

    private void startDownload(List<AssetEntry> remoteEntries, File targetDirectory) {
        if (remoteEntries.isEmpty() || targetDirectory == null) return;
        requestDownload(remoteEntries, targetDirectory);
    }

    private void requestDownload(List<AssetEntry> remoteEntries, File targetDirectory) {
        if (remoteEntries.isEmpty()) return;
        List<String> paths = new ArrayList<>();
        for (AssetEntry entry : remoteEntries) {
            paths.add(entry.path());
        }

        int requestId = beginRemoteRequest();
        List<RemoteGraphUploadFile> downloaded = new ArrayList<>();
        RemoteGraphClientState.onDownload(requestId, response -> {
            if (response.terminal()) finishRemoteRequest(requestId);
            post(() -> {
                if (!response.success()) {
                    TransferProgressDialog progress = new TransferProgressDialog(getContext(), "下载图纸");
                    progress.showIn(this);
                    progress.fail(response.message());
                    return;
                }
                if (!response.files().isEmpty()) {
                    downloaded.addAll(response.files());
                }
                if (response.terminal()) {
                    finishDownload(downloaded, targetDirectory);
                }
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphDownloadRequest(requestId, paths));
    }

    private void finishDownload(List<RemoteGraphUploadFile> files, File targetDirectory) {
        List<String> conflicts = findLocalDownloadConflicts(files, targetDirectory);
        if (conflicts.isEmpty()) {
            saveDownloadedFiles(files, targetDirectory);
            return;
        }

        ConflictResolutionState<RemoteGraphUploadFile> resolution =
                new ConflictResolutionState<>(files, conflicts, file -> AssetPathUtils.normalizeRemoteFilePath(file.targetPath()));
        new OverwriteConfirmDialog(getContext(), conflicts, decision -> {
            switch (decision) {
                case OVERWRITE_CURRENT -> {
                    if (resolution.markCurrentOverwrite()) {
                        saveDownloadedFiles(resolution.remainingFiles(), targetDirectory);
                    }
                }
                case OVERWRITE_ALL -> saveDownloadedFiles(files, targetDirectory);
                case SKIP_CURRENT -> {
                    if (resolution.markCurrentSkip()) {
                        saveDownloadedFiles(resolution.remainingFiles(), targetDirectory);
                    }
                }
                case SKIP_ALL -> saveDownloadedFiles(resolution.withoutConflicts(), targetDirectory);
                case CANCEL -> {
                }
            }
        }).showIn(this);
    }

    private void saveDownloadedFiles(List<RemoteGraphUploadFile> files, File targetDirectory) {
        List<RemoteGraphUploadFile> fileSnapshot = files == null ? List.of() : List.copyOf(files);
        mIoTasks.run("保存下载",
                context -> mLocalAssetService.saveDownloadedFiles(fileSnapshot, targetDirectory, context),
                (result, progress) -> {
                    if (mRightPanel != null) {
                        mRightPanel.refreshFileList();
                    }
                    if (!result.failedPaths().isEmpty()) {
                        progress.fail("部分文件保存失败: " + summarizePaths(result.failedPaths()));
                        return;
                    }
                    progress.update("下载完成", result.successCount(), Math.max(1, fileSnapshot.size()));
                });
    }

    private void showUploadFailureDialog(
            List<RemoteGraphUploadFile> failedFiles,
            boolean overwrite,
            List<String> overwritePaths
    ) {
        if (failedFiles == null || failedFiles.isEmpty()) return;
        List<String> failedPaths = new ArrayList<>();
        for (RemoteGraphUploadFile file : failedFiles) {
            failedPaths.add(file.targetPath());
        }
        new UploadFailureRetryDialog(
                getContext(),
                failedPaths,
                () -> startUpload(failedFiles, overwrite, overwritePaths)
        ).showIn(this);
    }

    private String summarizePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) return "";
        int limit = Math.min(3, paths.size());
        List<String> head = paths.subList(0, limit);
        String summary = String.join(", ", head);
        if (paths.size() > limit) {
            summary += " 等 " + paths.size() + " 项";
        }
        return summary;
    }

    private List<String> findLocalDownloadConflicts(List<RemoteGraphUploadFile> files, File targetDirectory) {
        List<String> conflicts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RemoteGraphUploadFile file : files) {
            String path = AssetPathUtils.normalizeRemoteFilePath(file.targetPath());
            if (!seen.add(path)) continue;
            File target = new File(targetDirectory, path);
            if (target.exists()) {
                conflicts.add(path);
            }
        }
        return conflicts;
    }

    private static final class ConflictResolutionState<T> {
        private final List<T> mFiles;
        private final List<String> mConflicts;
        private final Function<T, String> mPathProvider;
        private final Set<String> mOverwritePaths = new LinkedHashSet<>();
        private final Set<String> mSkipPaths = new LinkedHashSet<>();
        private final Set<String> mConflictSet;
        private int mIndex = 0;

        private ConflictResolutionState(List<T> files, List<String> conflicts, Function<T, String> pathProvider) {
            mFiles = files;
            mConflicts = conflicts;
            mPathProvider = pathProvider;
            mConflictSet = new HashSet<>(conflicts);
        }

        boolean markCurrentOverwrite() {
            if (mIndex < mConflicts.size()) {
                mOverwritePaths.add(mConflicts.get(mIndex));
            }
            return advance();
        }

        boolean markCurrentSkip() {
            if (mIndex < mConflicts.size()) {
                mSkipPaths.add(mConflicts.get(mIndex));
            }
            return advance();
        }

        private boolean advance() {
            mIndex++;
            return mIndex >= mConflicts.size();
        }

        List<String> overwritePaths() {
            return new ArrayList<>(mOverwritePaths);
        }

        List<T> remainingFiles() {
            List<T> result = new ArrayList<>();
            for (T file : mFiles) {
                if (!mSkipPaths.contains(mPathProvider.apply(file))) {
                    result.add(file);
                }
            }
            return result;
        }

        List<T> withoutConflicts() {
            List<T> result = new ArrayList<>();
            for (T file : mFiles) {
                if (!mConflictSet.contains(mPathProvider.apply(file))) {
                    result.add(file);
                }
            }
            return result;
        }
    }

    private final class UploadBatchRunner {
        private final List<RemoteGraphUploadFile> mFiles;
        private final boolean mOverwriteAll;
        private final Set<String> mOverwritePaths;
        private final List<RemoteGraphUploadFile> mFailedFiles = new ArrayList<>();
        private int mIndex = 0;

        private UploadBatchRunner(
                List<RemoteGraphUploadFile> files,
                boolean overwriteAll,
                List<String> overwritePaths
        ) {
            mFiles = files;
            mOverwriteAll = overwriteAll;
            mOverwritePaths = new HashSet<>(overwritePaths);
        }

        void sendNext() {
            if (mIndex >= mFiles.size()) {
                if (mRightPanel != null) {
                    mRightPanel.refreshFileList();
                }
                if (!mFailedFiles.isEmpty()) {
                    showUploadFailureDialog(mFailedFiles, mOverwriteAll, new ArrayList<>(mOverwritePaths));
                }
                return;
            }

            RemoteGraphUploadFile file = mFiles.get(mIndex);
            int requestId = beginRemoteRequest();
            RemoteGraphClientState.onUpload(requestId, response -> {
                if (response.terminal()) finishRemoteRequest(requestId);
                post(() -> {
                    if (!response.success()) {
                        mFailedFiles.add(file);
                        mIndex++;
                        sendNext();
                        return;
                    }
                    if (response.terminal()) {
                        mIndex++;
                        sendNext();
                    }
                });
            });
            boolean overwrite = mOverwriteAll || mOverwritePaths.contains(file.targetPath());
            NetworkHandler.sendToServer(new PacketRemoteGraphUploadRequest(
                    requestId,
                    false,
                    overwrite,
                    overwrite ? List.of(file.targetPath()) : List.of(),
                    List.of(file)
            ));
        }
    }

    private int beginRemoteRequest() {
        int requestId = RemoteGraphClientState.nextRequestId();
        mRemoteRequestIds.add(requestId);
        return requestId;
    }

    private void finishRemoteRequest(int requestId) {
        mRemoteRequestIds.remove(requestId);
    }

    private void cancelRemoteRequests() {
        for (int requestId : mRemoteRequestIds) {
            RemoteGraphClientState.cancel(requestId);
        }
        mRemoteRequestIds.clear();
    }
}
