package com.mine.geometry_node.client.ui.bottom_window.asset_library.task;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.TransferProgressDialog;
import icyllis.modernui.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public final class AssetTaskController {
    private final ViewGroup mOwner;
    private final List<AssetTaskHandle> mActiveTasks = new ArrayList<>();

    public AssetTaskController(ViewGroup owner) {
        mOwner = owner;
    }

    public interface Success<T> {
        void accept(T result, TransferProgressDialog progress);
    }

    public <T> void run(String title, AssetTask<T> task, Success<T> onSuccess) {
        TransferProgressDialog progress = new TransferProgressDialog(mOwner.getContext(), title);
        final AssetTaskHandle[] handleRef = new AssetTaskHandle[1];
        final boolean[] finished = {false};
        AssetTaskHandle handle = AssetTaskRunner.submit(task, runnable -> mOwner.post(runnable), new AssetTaskListener<>() {
            @Override
            public void onStarted() {
                progress.update("准备中", 0, 0);
            }

            @Override
            public void onProgress(AssetTaskProgress taskProgress) {
                progress.update(taskProgress.message(), taskProgress.processed(), taskProgress.total());
            }

            @Override
            public void onSuccess(T result) {
                markFinished(handleRef, finished);
                if (onSuccess != null) {
                    onSuccess.accept(result, progress);
                } else {
                    progress.update("操作完成", 1, 1);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                markFinished(handleRef, finished);
                error.printStackTrace();
                progress.fail(error.getMessage() == null || error.getMessage().isEmpty()
                        ? "操作失败"
                        : error.getMessage());
            }

            @Override
            public void onCancelled() {
                markFinished(handleRef, finished);
                progress.cancelled();
            }
        });
        handleRef[0] = handle;
        if (!finished[0]) {
            mActiveTasks.add(handle);
        }
        progress.setOnCancel(() -> {
            handle.cancel();
            markFinished(handleRef, finished);
            progress.cancelled();
        });
        progress.showIn(mOwner);
    }

    public void cancelAll() {
        for (AssetTaskHandle handle : new ArrayList<>(mActiveTasks)) {
            handle.cancel();
        }
        mActiveTasks.clear();
    }

    private void markFinished(AssetTaskHandle[] handleRef, boolean[] finished) {
        finished[0] = true;
        if (handleRef[0] != null) {
            mActiveTasks.remove(handleRef[0]);
        }
    }
}
