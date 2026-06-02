package com.mine.geometry_node.client.ui.bottom_window.asset_library;

import com.mine.geometry_node.client.ui.bottom_window.IToolWindow;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.FolderPickerDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.OverwriteConfirmDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.TransferProgressDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.left.LeftQuickAccessPanel;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.right.RightFileBrowserPanel;
import com.mine.geometry_node.client.ui.utils.PanelSplitter;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.PathUtils;
import com.mine.geometry_node.core.engine.blueprint.execution.storage.RemoteGraphFileService;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphCapabilitiesRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphDownloadRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphUploadRequest;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public class AssetBrowserPanel extends FrameLayout implements IToolWindow {

    private final LinearLayout mMainLayout;
    private final LeftQuickAccessPanel mLeftPanel;
    private final RightFileBrowserPanel mRightPanel;

    public AssetBrowserPanel(Context context) {
        super(context);

        mMainLayout = new LinearLayout(context);
        mMainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mMainLayout.setBackground(createColorDrawable(UIConstants.MainUI.BG_TIMELINE));
        addView(mMainLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mLeftPanel = new LeftQuickAccessPanel(context, this);
        mMainLayout.addView(mLeftPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.2f));

        mMainLayout.addView(PanelSplitter.create(context, true));

        mRightPanel = new RightFileBrowserPanel(context, this);
        mMainLayout.addView(mRightPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.8f));

        dispatchNavigateTo(PathUtils.getLocalDraftsDir());
        requestRemoteCapabilities();
    }

    /**
     * 跨区协调总线：将来自左侧边栏选中的目录精准分发给右侧文件浏览器
     */
    public void dispatchNavigateTo(File directory) {
        if (mRightPanel != null) {
            mRightPanel.navigateTo(directory);
        }
    }

    public void dispatchNavigateToRemoteRoot() {
        if (RemoteGraphClientState.canBrowse() && mRightPanel != null) {
            mRightPanel.navigateToRemoteRoot();
        }
    }

    public boolean canBrowseRemote() {
        return RemoteGraphClientState.canBrowse();
    }

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

    public void showDownloadDialog(List<AssetEntry> remoteEntries) {
        if (remoteEntries == null || remoteEntries.isEmpty()) return;
        FolderPickerDialog dialog = FolderPickerDialog.local(
                getContext(),
                "下载到本地",
                PathUtils.getLocalDraftsDir(),
                targetDirectory -> startDownload(remoteEntries, targetDirectory)
        );
        dialog.showIn(this);
    }

    /**
     * 跨区协调总线：当右侧通过 NavBar 的 "+" 添加了新的快速路径后，驱动左侧状态重塑
     */
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
            mRightPanel.refreshFileList();
        }
        if (mLeftPanel != null) {
            mLeftPanel.buildSidebar();
        }
    }

    @Override
    public void onHide() {
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private void requestRemoteCapabilities() {
        int requestId = RemoteGraphClientState.nextRequestId();
        RemoteGraphClientState.onCapabilities(requestId, response -> {
            post(() -> {
                if (mLeftPanel != null) {
                    mLeftPanel.buildSidebar();
                }
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphCapabilitiesRequest(requestId));
    }

    private void preflightUpload(List<File> selectedFiles, String targetDirectory) {
        List<RemoteGraphFileService.UploadFile> files = collectUploadFiles(selectedFiles, targetDirectory);
        if (files.isEmpty()) return;

        int requestId = RemoteGraphClientState.nextRequestId();
        RemoteGraphClientState.onUpload(requestId, response -> {
            post(() -> {
                if (!response.preflight()) return;
                if (!response.success() && response.conflicts().isEmpty()) {
                    TransferProgressDialog progress = new TransferProgressDialog(getContext(), "上传图纸");
                    progress.showIn(this);
                    progress.fail(response.message());
                    return;
                }
                if (response.conflicts().isEmpty()) {
                    startUpload(files, false);
                    return;
                }
                List<String> conflictPaths = new ArrayList<>();
                for (RemoteGraphFileService.Conflict conflict : response.conflicts()) {
                    conflictPaths.add(conflict.targetPath());
                }
                ConflictResolutionState<RemoteGraphFileService.UploadFile> resolution =
                        new ConflictResolutionState<>(files, conflictPaths, RemoteGraphFileService.UploadFile::targetPath);
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

    private void startUpload(List<RemoteGraphFileService.UploadFile> files, boolean overwrite) {
        startUpload(files, overwrite, List.of());
    }

    private void startUpload(List<RemoteGraphFileService.UploadFile> files, boolean overwrite, List<String> overwritePaths) {
        if (files.isEmpty()) return;
        TransferProgressDialog progress = new TransferProgressDialog(getContext(), "上传图纸");
        progress.showIn(this);
        UploadBatchRunner runner = new UploadBatchRunner(files, overwrite, overwritePaths, progress);
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

        TransferProgressDialog progress = new TransferProgressDialog(getContext(), "下载图纸");
        progress.showIn(this);
        int requestId = RemoteGraphClientState.nextRequestId();
        List<RemoteGraphFileService.UploadFile> downloaded = new ArrayList<>();
        RemoteGraphClientState.onDownload(requestId, response -> {
            post(() -> {
                if (!response.success()) {
                    progress.fail(response.message());
                    return;
                }
                if (!response.files().isEmpty()) {
                    downloaded.addAll(response.files());
                }
                if ("下载完成".equals(response.message())) {
                    progress.update(response.message(), response.processed(), response.total());
                    finishDownload(downloaded, targetDirectory);
                } else {
                    progress.update(response.message(), response.processed(), response.total() + 1);
                }
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphDownloadRequest(requestId, paths));
    }

    private List<RemoteGraphFileService.UploadFile> collectUploadFiles(List<File> selectedFiles, String targetDirectory) {
        List<RemoteGraphFileService.UploadFile> files = new ArrayList<>();
        String targetPrefix = AssetPathUtils.normalizeRemoteDirectory(targetDirectory);
        for (File selected : selectedFiles) {
            if (selected == null || !selected.exists()) continue;
            Path base = selected.isDirectory() ? selected.toPath().getParent() : selected.toPath().getParent();
            collectUploadFile(files, base, selected.toPath(), targetPrefix);
        }
        return files;
    }

    private void collectUploadFile(List<RemoteGraphFileService.UploadFile> out, Path base, Path path, String targetPrefix) {
        try {
            if (Files.isSymbolicLink(path)) return;
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    stream.forEach(child -> collectUploadFile(out, base, child, targetPrefix));
                }
                return;
            }
            if (!Files.isRegularFile(path)) return;
            if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) return;
            String relative = base.relativize(path).toString().replace('\\', '/');
            String targetPath = targetPrefix.isEmpty() ? relative : targetPrefix + "/" + relative;
            targetPath = AssetPathUtils.normalizeRemoteFilePath(targetPath);
            out.add(new RemoteGraphFileService.UploadFile(targetPath, Files.readString(path)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void finishDownload(List<RemoteGraphFileService.UploadFile> files, File targetDirectory) {
        List<String> conflicts = findLocalDownloadConflicts(files, targetDirectory);
        if (conflicts.isEmpty()) {
            saveDownloadedFiles(files, targetDirectory);
            return;
        }

        ConflictResolutionState<RemoteGraphFileService.UploadFile> resolution =
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

    private void saveDownloadedFiles(List<RemoteGraphFileService.UploadFile> files, File targetDirectory) {
        Path root = targetDirectory.toPath().toAbsolutePath().normalize();
        for (RemoteGraphFileService.UploadFile file : files) {
            try {
                String relative = AssetPathUtils.normalizeRemoteFilePath(file.targetPath());
                Path target = root.resolve(relative).normalize();
                if (!target.startsWith(root)) {
                    throw new IllegalArgumentException("invalid download path: " + file.targetPath());
                }
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(target, file.jsonContent());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mRightPanel != null) {
            mRightPanel.refreshFileList();
        }
    }

    private List<String> findLocalDownloadConflicts(List<RemoteGraphFileService.UploadFile> files, File targetDirectory) {
        List<String> conflicts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RemoteGraphFileService.UploadFile file : files) {
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
        private final List<RemoteGraphFileService.UploadFile> mFiles;
        private final boolean mOverwriteAll;
        private final Set<String> mOverwritePaths;
        private final TransferProgressDialog mProgress;
        private int mIndex = 0;

        private UploadBatchRunner(
                List<RemoteGraphFileService.UploadFile> files,
                boolean overwriteAll,
                List<String> overwritePaths,
                TransferProgressDialog progress
        ) {
            mFiles = files;
            mOverwriteAll = overwriteAll;
            mOverwritePaths = new HashSet<>(overwritePaths);
            mProgress = progress;
        }

        void sendNext() {
            if (mIndex >= mFiles.size()) {
                mProgress.update("上传完成", mFiles.size(), mFiles.size());
                if (mRightPanel != null) {
                    mRightPanel.refreshFileList();
                }
                return;
            }

            RemoteGraphFileService.UploadFile file = mFiles.get(mIndex);
            int requestId = RemoteGraphClientState.nextRequestId();
            RemoteGraphClientState.onUpload(requestId, response -> {
                post(() -> {
                    if (!response.success()) {
                        mProgress.fail(response.message());
                        return;
                    }
                    if ("上传完成".equals(response.message())) {
                        mIndex++;
                        mProgress.update(file.targetPath(), mIndex, mFiles.size());
                        sendNext();
                    } else {
                        mProgress.update(file.targetPath(), mIndex + response.processed(), mFiles.size());
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
}
