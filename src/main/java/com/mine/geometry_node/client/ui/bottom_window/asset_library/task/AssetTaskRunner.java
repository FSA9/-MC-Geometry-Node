package com.mine.geometry_node.client.ui.bottom_window.asset_library.task;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AssetTaskRunner {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-AssetLibrary-IO");
        thread.setDaemon(true);
        return thread;
    });

    private AssetTaskRunner() {
    }

    public static <T> AssetTaskHandle submit(
            AssetTask<T> task,
            Consumer<Runnable> callbackExecutor,
            AssetTaskListener<T> listener
    ) {
        TaskHandle handle = new TaskHandle();
        Consumer<Runnable> dispatcher = callbackExecutor != null ? callbackExecutor : Runnable::run;
        AssetTaskListener<T> safeListener = listener != null ? listener : new AssetTaskListener<>() {
        };
        TaskContext context = new TaskContext(handle, dispatcher, safeListener);

        Future<?> future = IO_EXECUTOR.submit(() -> {
            dispatch(dispatcher, safeListener::onStarted);
            try {
                context.checkCancelled();
                T result = task.run(context);
                context.checkCancelled();
                dispatch(dispatcher, () -> safeListener.onSuccess(result));
            } catch (InterruptedException | CancellationException e) {
                Thread.interrupted();
                handle.cancel();
                dispatch(dispatcher, safeListener::onCancelled);
            } catch (Exception e) {
                if (handle.isCancelled()) {
                    dispatch(dispatcher, safeListener::onCancelled);
                } else {
                    dispatch(dispatcher, () -> safeListener.onFailure(e));
                }
            }
        });
        handle.setFuture(future);
        return handle;
    }

    private static void dispatch(Consumer<Runnable> dispatcher, Runnable runnable) {
        dispatcher.accept(runnable);
    }

    private static final class TaskHandle implements AssetTaskHandle {
        private final AtomicBoolean mCancelled = new AtomicBoolean(false);
        private volatile Future<?> mFuture;

        @Override
        public void cancel() {
            mCancelled.set(true);
            Future<?> future = mFuture;
            if (future != null) {
                future.cancel(true);
            }
        }

        @Override
        public boolean isCancelled() {
            return mCancelled.get();
        }

        private void setFuture(Future<?> future) {
            mFuture = future;
            if (mCancelled.get() && future != null) {
                future.cancel(true);
            }
        }
    }

    private static final class TaskContext implements AssetTaskContext {
        private final AssetTaskHandle mHandle;
        private final Consumer<Runnable> mDispatcher;
        private final AssetTaskListener<?> mListener;

        private TaskContext(AssetTaskHandle handle, Consumer<Runnable> dispatcher, AssetTaskListener<?> listener) {
            mHandle = handle;
            mDispatcher = dispatcher;
            mListener = listener;
        }

        @Override
        public boolean isCancelled() {
            return mHandle.isCancelled();
        }

        @Override
        public void progress(String message, int processed, int total) {
            AssetTaskProgress progress = new AssetTaskProgress(message, processed, total);
            dispatch(mDispatcher, () -> mListener.onProgress(progress));
        }
    }
}
