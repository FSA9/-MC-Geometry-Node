package com.mine.geometry_node.client.integration.asset;

import com.mine.geometry_node.client.ui.components.integration.asset.dialog.FolderPickerDialog;
import com.mine.geometry_node.client.ui.components.integration.asset.dialog.OverwriteConfirmDialog;
import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferPlanState;
import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.ui.components.integration.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.repository.AssetRepositoryOperation;
import com.mine.geometry_node.client.ui.components.integration.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.feature.asset.file.LocalAssetService;
import com.mine.geometry_node.client.ui.components.integration.asset.task.AssetTaskController;
import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.core.engine.system.asset.repository.RemoteAssetConflict;
import com.mine.geometry_node.core.engine.system.asset.AssetDescriptor;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.network.packet.asset.transfer.AssetTransferPlanKind;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.mc.MuiModApi;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import com.mine.geometry_node.client.feature.asset.path.AssetPathUtils;
import com.mine.geometry_node.client.ui.editor.asset.AssetBrowserAreaBindings;
import com.mine.geometry_node.client.ui.components.integration.asset.browser.AssetBrowserDocumentActions;

/** Transfer planning and document upload assembly, scoped to one asset Area owner. */
public final class AssetBrowserTransferController implements AssetBrowserAreaBindings.Transfers {
    private final ViewGroup mOwner;
    private final AssetFileBrowserPanel mBrowserPanel;
    private final AssetTaskController mIoTasks;
    private final AssetBrowserDocumentActions mDocuments;
    private final LocalAssetService mLocalAssetService = new LocalAssetService();
    private long mRemoteGeneration;
    private boolean mRemoteActive = true;
    private final Set<Integer> mTransferPlanRequestIds = new HashSet<>();

    public AssetBrowserTransferController(ViewGroup owner, AssetFileBrowserPanel browser,
                                          AssetTaskController tasks, AssetBrowserDocumentActions documents) {
        mOwner = java.util.Objects.requireNonNull(owner);
        mBrowserPanel = java.util.Objects.requireNonNull(browser);
        mIoTasks = java.util.Objects.requireNonNull(tasks);
        mDocuments = java.util.Objects.requireNonNull(documents);
    }

    @Override
    public void showUploadDialog(List<File> selectedFiles) {
        if (selectedFiles == null || selectedFiles.isEmpty() || mBrowserPanel == null
                || !mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.UPLOAD)) return;
        FolderPickerDialog dialog = FolderPickerDialog.remote(
                mOwner.getContext(),
                "上传到服务器",
                "",
                targetDirectory -> preflightUpload(selectedFiles, targetDirectory)
        );
        dialog.show(mOwner);
    }

    @Override
    public void showDownloadDialog(List<AssetEntry> remoteEntries) {
        if (remoteEntries == null || remoteEntries.isEmpty() || mBrowserPanel == null
                || !mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.DOWNLOAD)) return;
        FolderPickerDialog dialog = FolderPickerDialog.local(
                mOwner.getContext(),
                "下载到本地",
                AssetBrowserPathPolicy.getLocalDraftsDir(),
                targetDirectory -> startDownload(remoteEntries, targetDirectory)
        );
        dialog.show(mOwner);
    }

    @Override
    public void createRemoteGraph(String targetPath, Runnable onSuccess) {
        if (targetPath == null || targetPath.isBlank() || mBrowserPanel == null
                || !mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.UPLOAD)) return;
        if (!mRemoteActive) return;
        long generation = mRemoteGeneration;
        mIoTasks.runSilent(context -> {
            Path temporary = Files.createTempFile("geometry-node-new-graph-", ".json");
            try {
                Files.writeString(temporary, mDocuments.newFileContent(), StandardCharsets.UTF_8);
                return new GeneratedGraph(temporary);
            } catch (Exception exception) {
                Files.deleteIfExists(temporary);
                throw exception;
            }
        }, temporary -> {
            if (!isRemoteCurrent(generation)) {
                try { temporary.close(); }
                catch (java.io.IOException exception) {
                    System.err.println("[AssetBrowser] Failed to remove abandoned graph temporary file: " + temporary);
                }
                return;
            }
            java.util.UUID jobId;
            try {
                jobId = ClientAssetTransferService.INSTANCE.submit(List.of(ClientAssetTransferRequest.upload(
                        temporary.path(), targetPath, AssetTransferConflictPolicy.FAIL_IF_EXISTS)));
            } catch (RuntimeException exception) {
                try {
                    temporary.close();
                } catch (java.io.IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
                System.err.println("[AssetBrowser] Failed to submit generated graph: " + exception.getMessage());
                return;
            }
            ClientAssetTransferService.INSTANCE.onCompletion(jobId, (result, error) -> {
                try {
                    temporary.close();
                } catch (java.io.IOException exception) {
                    System.err.println("[AssetBrowser] Failed to remove generated graph temporary file: "
                            + temporary);
                }
                if (error == null && result.completedFileCount() == result.files().size()) {
                    postRemote(generation, () -> {
                        if (mBrowserPanel != null) mBrowserPanel.refreshFileList(() -> {
                            if (isRemoteCurrent(generation) && onSuccess != null) onSuccess.run();
                        });
                    });
                }
            });
        }, exception -> System.err.println("[AssetBrowser] Failed to create remote graph: "
                + exception.getMessage()));
    }

    /** Owns the generated file until transfer completion, including cancelled task delivery. */
    private record GeneratedGraph(Path path) implements AutoCloseable {
        @Override public void close() throws java.io.IOException {
            Files.deleteIfExists(path);
        }
    }

    private void preflightUpload(List<File> selectedFiles, String targetDirectory) {
        if (!mRemoteActive) return;
        long generation = mRemoteGeneration;
        List<File> selectedSnapshot = selectedFiles == null ? List.of() : List.copyOf(selectedFiles);
        mIoTasks.runSilent(
                context -> mLocalAssetService.collectUploadSources(selectedSnapshot, targetDirectory, context),
                result -> {
                    if (!isRemoteCurrent(generation)) return;
                    if (result.files().isEmpty()) {
                        System.err.println("[AssetBrowser] No transferable upload sources: " + result.failedPaths());
                        return;
                    }
                    if (!result.failedPaths().isEmpty()) {
                        System.err.println("[AssetBrowser] Some upload sources could not be scanned: " + result.failedPaths());
                    }
                    requestUploadPreflight(result.files());
                }, exception -> {
                    System.err.println("[AssetBrowser] Upload scan failed: " + exception.getMessage());
                    exception.printStackTrace();
                });
    }

    private void requestUploadPreflight(List<LocalAssetService.UploadSource> files) {
        if (!mRemoteActive) return;
        long generation = mRemoteGeneration;
        int[] requestHolder = new int[1];
        int requestId = ClientAssetTransferPlanState.request(AssetTransferPlanKind.UPLOAD_CONFLICTS,
                files.stream().map(LocalAssetService.UploadSource::targetPath).toList(), response -> {
            postTransferPlan(requestHolder, generation, () -> {
                if (!response.success()) {
                    System.err.println("[AssetBrowser] Upload planning failed: " + response.message());
                    return;
                }
                if (response.conflicts().isEmpty()) {
                    startUpload(files, false);
                    return;
                }
                List<String> conflictPaths = new ArrayList<>();
                for (RemoteAssetConflict conflict : response.conflicts()) {
                    conflictPaths.add(conflict.targetPath());
                }
                ConflictResolutionState<LocalAssetService.UploadSource> resolution =
                        new ConflictResolutionState<>(files, conflictPaths, LocalAssetService.UploadSource::targetPath);
                new OverwriteConfirmDialog(mOwner.getContext(), conflictPaths, decision -> {
                    if (!isRemoteCurrent(generation)) return;
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
                }).show(mOwner);
            });
        }, () -> postTransferPlan(requestHolder, generation, () -> { }));
        requestHolder[0] = requestId;
        mTransferPlanRequestIds.add(requestId);
    }

    private void startUpload(List<LocalAssetService.UploadSource> files, boolean overwrite) {
        startUpload(files, overwrite, List.of());
    }

    private void startUpload(List<LocalAssetService.UploadSource> files, boolean overwrite, List<String> overwritePaths) {
        if (!mRemoteActive || files.isEmpty()) return;
        long generation = mRemoteGeneration;
        Set<String> overwriteSet = new HashSet<>(overwritePaths);
        List<ClientAssetTransferRequest> requests = files.stream().map(file -> ClientAssetTransferRequest.upload(
                file.sourcePath(), file.targetPath(), overwrite || overwriteSet.contains(file.targetPath())
                        ? AssetTransferConflictPolicy.OVERWRITE : AssetTransferConflictPolicy.FAIL_IF_EXISTS)).toList();
        java.util.UUID jobId = ClientAssetTransferService.INSTANCE.submit(requests);
        ClientAssetTransferService.INSTANCE.onCompletion(jobId, (result, error) -> postRemote(generation, () -> {
            if (error == null && mBrowserPanel != null) mBrowserPanel.refreshFileList();
        }));
    }

    private void startDownload(List<AssetEntry> remoteEntries, File targetDirectory) {
        if (!mRemoteActive || remoteEntries.isEmpty() || targetDirectory == null) return;
        long generation = mRemoteGeneration;
        List<String> paths = remoteEntries.stream().map(AssetEntry::path).toList();
        int[] requestHolder = new int[1];
        int requestId = ClientAssetTransferPlanState.request(AssetTransferPlanKind.DOWNLOAD_MANIFEST, paths, response -> {
            postTransferPlan(requestHolder, generation, () -> {
                if (!response.success()) {
                    System.err.println("[AssetBrowser] Download planning failed: " + response.message());
                    return;
                }
                finishDownload(response.files(), targetDirectory);
            });
        }, () -> postTransferPlan(requestHolder, generation, () -> { }));
        requestHolder[0] = requestId;
        mTransferPlanRequestIds.add(requestId);
    }

    private void finishDownload(List<AssetDescriptor> files, File targetDirectory) {
        long generation = mRemoteGeneration;
        List<String> conflicts = findLocalDownloadConflicts(files, targetDirectory);
        if (conflicts.isEmpty()) {
            submitDownload(files, targetDirectory, false, List.of());
            return;
        }

        ConflictResolutionState<AssetDescriptor> resolution =
                new ConflictResolutionState<>(files, conflicts, file -> AssetPathUtils.normalizeRemoteFilePath(file.path()));
        new OverwriteConfirmDialog(mOwner.getContext(), conflicts, decision -> {
            if (!isRemoteCurrent(generation)) return;
            switch (decision) {
                case OVERWRITE_CURRENT -> {
                    if (resolution.markCurrentOverwrite()) {
                        submitDownload(resolution.remainingFiles(), targetDirectory, false, resolution.overwritePaths());
                    }
                }
                case OVERWRITE_ALL -> submitDownload(files, targetDirectory, true, List.of());
                case SKIP_CURRENT -> {
                    if (resolution.markCurrentSkip()) {
                        submitDownload(resolution.remainingFiles(), targetDirectory, false, resolution.overwritePaths());
                    }
                }
                case SKIP_ALL -> submitDownload(resolution.withoutConflicts(), targetDirectory, false, List.of());
                case CANCEL -> {
                }
            }
        }).show(mOwner);
    }

    private void submitDownload(List<AssetDescriptor> files, File targetDirectory,
                                boolean overwrite, List<String> overwritePaths) {
        if (!mRemoteActive || files.isEmpty()) return;
        long generation = mRemoteGeneration;
        Path root = targetDirectory.toPath().toAbsolutePath().normalize();
        Set<String> overwriteSet = new HashSet<>(overwritePaths);
        List<ClientAssetTransferRequest> requests = new ArrayList<>();
        for (AssetDescriptor file : files) {
            String remotePath = AssetPathUtils.normalizeRemoteFilePath(file.path());
            Path target = root.resolve(remotePath).normalize();
            if (!target.startsWith(root)) continue;
            requests.add(ClientAssetTransferRequest.download(remotePath, target,
                    overwrite || overwriteSet.contains(remotePath)
                            ? AssetTransferConflictPolicy.OVERWRITE : AssetTransferConflictPolicy.FAIL_IF_EXISTS));
        }
        if (requests.isEmpty()) return;
        java.util.UUID jobId = ClientAssetTransferService.INSTANCE.submit(requests);
        ClientAssetTransferService.INSTANCE.onCompletion(jobId, (result, error) -> MuiModApi.postToUiThread(() -> {
            if (error == null) {
                mDocuments.refreshFileReferences();
                ImageThumbnailView.clearCache();
                // File/cache updates describe the completed download; only the owner UI is fenced.
                if (isRemoteCurrent(generation) && mBrowserPanel != null) mBrowserPanel.refreshFileList();
            }
        }));
    }

    private List<String> findLocalDownloadConflicts(List<AssetDescriptor> files, File targetDirectory) {
        List<String> conflicts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AssetDescriptor file : files) {
            String path = AssetPathUtils.normalizeRemoteFilePath(file.path());
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

    private boolean isRemoteCurrent(long generation) {
        return mRemoteActive && generation == mRemoteGeneration;
    }

    private void postRemote(long generation, Runnable action) {
        mOwner.post(() -> {
            if (isRemoteCurrent(generation)) action.run();
        });
    }

    private void postTransferPlan(int[] requestHolder, long generation, Runnable action) {
        // Read the returned ID on the UI thread, after request() assigned it, even for inline responses.
        postRemote(generation, () -> {
            if (mTransferPlanRequestIds.remove(requestHolder[0])) action.run();
        });
    }

    @Override public void activate() { mRemoteActive = true; }

    @Override public void deactivate() {
        mRemoteActive = false;
        mRemoteGeneration++;
        List<Integer> plans = List.copyOf(mTransferPlanRequestIds);
        mTransferPlanRequestIds.clear();
        for (int requestId : plans) ClientAssetTransferPlanState.cancel(requestId);
    }

    @Override public void dispose() {
        mIoTasks.cancelAll();
        deactivate();
    }
}
