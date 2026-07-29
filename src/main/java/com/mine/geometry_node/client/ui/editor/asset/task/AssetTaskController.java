package com.mine.geometry_node.client.ui.editor.asset.task;

import com.mine.geometry_node.client.ui.editor.asset.dialog.TransferProgressDialog;
import icyllis.modernui.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AssetTaskController {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-AssetLibrary-IO");
        thread.setDaemon(true);
        return thread;
    });

    private final ViewGroup mOwner;
    private final List<TaskHandle> mActiveTasks = new ArrayList<>();

    public AssetTaskController(ViewGroup owner) {
        mOwner = owner;
    }

    public interface Success<T> {
        void accept(T result, TransferProgressDialog progress);
    }

    public <T> void run(String title, AssetTask<T> task, Success<T> onSuccess) {
        TransferProgressDialog progress = new TransferProgressDialog(mOwner.getContext(), title);
        TaskHandle handle = new TaskHandle();
        mActiveTasks.add(handle);
        progress.setOnCancel(() -> {
            handle.cancel();
            finish(handle, progress::cancelled);
        });
        progress.showIn(mOwner);

        Future<?> future = IO_EXECUTOR.submit(() -> execute(handle, task, onSuccess, progress));
        handle.setFuture(future);
    }

    public void cancelAll() {
        for (TaskHandle handle : new ArrayList<>(mActiveTasks)) {
            handle.cancel();
            handle.markFinished();
        }
        mActiveTasks.clear();
    }

    private <T> void execute(
            TaskHandle handle,
            AssetTask<T> task,
            Success<T> onSuccess,
            TransferProgressDialog progress
    ) {
        TaskContext context = new TaskContext(handle, progress);
        postIfActive(handle, () -> progress.update("准备中", 0, 0));
        try {
            context.checkCancelled();
            T result = task.run(context);
            context.checkCancelled();
            postTerminal(handle, () -> {
                if (onSuccess != null) {
                    onSuccess.accept(result, progress);
                } else {
                    progress.update("操作完成", 1, 1);
                }
            });
        } catch (InterruptedException | CancellationException e) {
            Thread.interrupted();
            handle.markCancelled();
            postTerminal(handle, progress::cancelled);
        } catch (Exception e) {
            if (handle.isCancelled()) {
                postTerminal(handle, progress::cancelled);
            } else {
                postTerminal(handle, () -> {
                    e.printStackTrace();
                    progress.fail(e.getMessage() == null || e.getMessage().isEmpty()
                            ? "操作失败"
                            : e.getMessage());
                });
            }
        }
    }

    private void postIfActive(TaskHandle handle, Runnable action) {
        mOwner.post(() -> {
            if (!handle.isCancelled() && !handle.isFinished()) {
                action.run();
            }
        });
    }

    private void postTerminal(TaskHandle handle, Runnable action) {
        mOwner.post(() -> finish(handle, action));
    }

    private void finish(TaskHandle handle, Runnable action) {
        if (!handle.markFinished()) return;
        mActiveTasks.remove(handle);
        action.run();
    }

    private final class TaskContext implements AssetTaskContext {
        private final TaskHandle mHandle;
        private final TransferProgressDialog mProgress;

        private TaskContext(TaskHandle handle, TransferProgressDialog progress) {
            mHandle = handle;
            mProgress = progress;
        }

        @Override
        public boolean isCancelled() {
            return mHandle.isCancelled();
        }

        @Override
        public void progress(String message, int processed, int total) {
            String safeMessage = message == null ? "" : message;
            int safeProcessed = Math.max(0, processed);
            int safeTotal = Math.max(0, total);
            postIfActive(mHandle, () -> mProgress.update(safeMessage, safeProcessed, safeTotal));
        }
    }

    private static final class TaskHandle {
        private final AtomicBoolean mCancelled = new AtomicBoolean(false);
        private final AtomicBoolean mFinished = new AtomicBoolean(false);
        private volatile Future<?> mFuture;

        private void cancel() {
            mCancelled.set(true);
            Future<?> future = mFuture;
            if (future != null) {
                future.cancel(true);
            }
        }

        private boolean isCancelled() {
            return mCancelled.get();
        }

        private void markCancelled() {
            mCancelled.set(true);
        }

        private boolean isFinished() {
            return mFinished.get();
        }

        private boolean markFinished() {
            return mFinished.compareAndSet(false, true);
        }

        private void setFuture(Future<?> future) {
            mFuture = future;
            if (mCancelled.get() && future != null) {
                future.cancel(true);
            }
        }
    }
}
