package com.mine.geometry_node.client.ui.editor.asset.task;

import com.mine.geometry_node.client.ui.editor.asset.dialog.TransferProgressDialog;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.mc.MuiModApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AssetTaskController {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-AssetLibrary-IO");
        thread.setDaemon(true);
        return thread;
    });

    private final ViewGroup mOwner;
    private final List<TaskHandle> mActiveTasks = new ArrayList<>();
    private final List<TransferProgressDialog> mProgressDialogs = new ArrayList<>();
    private final ExecutorService mExecutor;
    private final Executor mUiExecutor;
    private final Consumer<TransferProgressDialog> mShowProgress;
    private long mOwnerGeneration;
    private boolean mActive = true;

    public AssetTaskController(ViewGroup owner) {
        // Completion must reach the UI thread even after the view has detached: committed
        // file operations still need document/cache cleanup, but no longer own its UI.
        this(owner, IO_EXECUTOR, MuiModApi::postToUiThread, progress -> progress.show(owner));
    }

    AssetTaskController(ViewGroup owner, ExecutorService executor, Executor uiExecutor,
                        Consumer<TransferProgressDialog> showProgress) {
        mOwner = owner;
        mExecutor = executor;
        mUiExecutor = uiExecutor;
        mShowProgress = showProgress;
    }

    public interface Success<T> {
        void accept(T result, TransferProgressDialog progress);
    }

    public <T> void run(String title, AssetTask<T> task, Success<T> onSuccess) {
        run(title, task, null, onSuccess);
    }

    /** onCommitted maintains file/cache state; onSuccess may update only the current owner UI. */
    public <T> void run(String title, AssetTask<T> task, Consumer<T> onCommitted, Success<T> onSuccess) {
        if (!mActive) return;
        TransferProgressDialog progress = new TransferProgressDialog(mOwner.getContext(), title) {
            @Override protected void onAssetDialogDestroyed() {
                mProgressDialogs.remove(this);
                super.onAssetDialogDestroyed();
            }
        };
        TaskHandle handle = new TaskHandle(mOwnerGeneration);
        mActiveTasks.add(handle);
        mProgressDialogs.add(progress);
        progress.setOnCancel(() -> {
            if (handle.cancel()) {
                finish(handle, progress::cancelled);
            }
        });
        mShowProgress.accept(progress);

        Future<?> future = mExecutor.submit(() -> execute(handle, task, onCommitted, onSuccess, progress));
        handle.setFuture(future);
    }

    public <T> void runSilent(AssetTask<T> task, Consumer<T> onSuccess, Consumer<Exception> onFailure) {
        if (!mActive) return;
        TaskHandle handle = new TaskHandle(mOwnerGeneration);
        mActiveTasks.add(handle);
        Future<?> future = mExecutor.submit(() -> executeSilent(handle, task, onSuccess, onFailure));
        handle.setFuture(future);
    }

    public void cancelAll() {
        mOwnerGeneration++;
        List<TransferProgressDialog> dialogs = List.copyOf(mProgressDialogs);
        mProgressDialogs.clear();
        for (TaskHandle handle : new ArrayList<>(mActiveTasks)) {
            if (handle.cancel()) {
                handle.markFinished();
                mActiveTasks.remove(handle);
            }
        }
        for (TransferProgressDialog dialog : dialogs) dialog.closeSilently();
    }

    public void activate() { mActive = true; }

    public void deactivate() {
        mActive = false;
        cancelAll();
    }

    private boolean isCurrent(TaskHandle handle) {
        return mActive && handle.ownerGeneration == mOwnerGeneration;
    }

    private <T> void execute(
            TaskHandle handle,
            AssetTask<T> task,
            Consumer<T> onCommitted,
            Success<T> onSuccess,
            TransferProgressDialog progress
    ) {
        TaskContext context = new TaskContext(handle, progress);
        T result = null;
        postIfActive(handle, () -> progress.update("准备中", 0, 0));
        try {
            context.checkCancelled();
            result = task.run(context);
            context.checkCancelled();
            T completedResult = result;
            postTerminal(handle, () -> {
                if (onCommitted != null) onCommitted.accept(completedResult);
                // Cleanup can reenter lifecycle (for example closing an affected document).
                if (!isCurrent(handle)) {
                    closeAbandoned(completedResult);
                    return;
                }
                if (onSuccess != null) {
                    onSuccess.accept(completedResult, progress);
                } else {
                    progress.complete("操作完成", 1, 1);
                }
            }, () -> closeAbandoned(completedResult));
        } catch (InterruptedException | CancellationException e) {
            closeAbandoned(result);
            Thread.interrupted();
            handle.markCancelled();
            postOwnerTerminal(handle, progress::cancelled);
        } catch (Exception e) {
            closeAbandoned(result);
            if (handle.isCancelled()) {
                postOwnerTerminal(handle, progress::cancelled);
            } else {
                postOwnerTerminal(handle, () -> {
                    e.printStackTrace();
                    progress.fail(e.getMessage() == null || e.getMessage().isEmpty()
                            ? "操作失败"
                            : e.getMessage());
                });
            }
        }
    }

    private <T> void executeSilent(
            TaskHandle handle,
            AssetTask<T> task,
            Consumer<T> onSuccess,
            Consumer<Exception> onFailure
    ) {
        SilentTaskContext context = new SilentTaskContext(handle);
        T result = null;
        try {
            context.checkCancelled();
            result = task.run(context);
            context.checkCancelled();
            T completedResult = result;
            postTerminal(handle, () -> {
                if (!isCurrent(handle)) closeAbandoned(completedResult);
                else if (onSuccess != null) onSuccess.accept(completedResult);
            }, () -> closeAbandoned(completedResult));
        } catch (InterruptedException | CancellationException exception) {
            closeAbandoned(result);
            Thread.interrupted();
            handle.markCancelled();
            postTerminal(handle, () -> { });
        } catch (Exception exception) {
            closeAbandoned(result);
            if (handle.isCancelled()) {
                postTerminal(handle, () -> { });
            } else {
                postOwnerTerminal(handle, () -> {
                    if (onFailure != null) onFailure.accept(exception);
                    else exception.printStackTrace();
                });
            }
        }
    }

    private void postIfActive(TaskHandle handle, Runnable action) {
        mUiExecutor.execute(() -> {
            if (isCurrent(handle) && !handle.isCancelled() && !handle.isFinished()) {
                action.run();
            }
        });
    }

    private void postOwnerTerminal(TaskHandle handle, Runnable action) {
        postTerminal(handle, () -> { if (isCurrent(handle)) action.run(); });
    }

    private void postTerminal(TaskHandle handle, Runnable action) {
        postTerminal(handle, action, null);
    }

    private void postTerminal(TaskHandle handle, Runnable action, Runnable onAbandoned) {
        mUiExecutor.execute(() -> {
            if (!finish(handle, action) && onAbandoned != null) onAbandoned.run();
        });
    }

    private boolean finish(TaskHandle handle, Runnable action) {
        if (!handle.markFinished()) return false;
        mActiveTasks.remove(handle);
        action.run();
        return true;
    }

    private static void closeAbandoned(Object result) {
        if (!(result instanceof AutoCloseable closeable)) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
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

        @Override
        public void enterCommitPhase() throws InterruptedException {
            if (!mHandle.enterCommitPhase()) {
                throw new InterruptedException("asset task cancelled before commit");
            }
            postIfActive(mHandle, mProgress::enterCommitPhase);
        }
    }

    private static final class SilentTaskContext implements AssetTaskContext {
        private final TaskHandle mHandle;

        private SilentTaskContext(TaskHandle handle) {
            mHandle = handle;
        }

        @Override public boolean isCancelled() { return mHandle.isCancelled(); }
        @Override public void progress(String message, int processed, int total) { }

        @Override
        public void enterCommitPhase() throws InterruptedException {
            if (!mHandle.enterCommitPhase()) throw new InterruptedException("asset task cancelled before commit");
        }
    }

    private static final class TaskHandle {
        private final long ownerGeneration;
        private final AtomicBoolean mCancelled = new AtomicBoolean(false);
        private final AtomicBoolean mFinished = new AtomicBoolean(false);
        private boolean mCommitting;
        private volatile Future<?> mFuture;

        private TaskHandle(long ownerGeneration) { this.ownerGeneration = ownerGeneration; }

        private synchronized boolean cancel() {
            if (mCommitting || mFinished.get()) return false;
            mCancelled.set(true);
            Future<?> future = mFuture;
            if (future != null) {
                future.cancel(true);
            }
            return true;
        }

        private synchronized boolean enterCommitPhase() {
            if (mCancelled.get() || mFinished.get()) return false;
            mCommitting = true;
            return true;
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

        private synchronized void setFuture(Future<?> future) {
            mFuture = future;
            if (mCancelled.get() && future != null) {
                future.cancel(true);
            }
        }
    }
}
