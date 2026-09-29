package com.mine.geometry_node.core.engine.system.model.runtime;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
/** One request's ownership, not a global asset cache. Disposer must schedule graphics cleanup on its owner thread. */
public final class ModelHandle<T> implements AutoCloseable {
    private final long generation;
    private final Consumer<T> disposer;
    private final CompletableFuture<T> completion=new CompletableFuture<>();
    private ModelLoadState state=ModelLoadState.REQUESTED;
    private T value;
    public ModelHandle(long generation, Consumer<T> disposer) {
        if(generation<0) throw new IllegalArgumentException("negative generation");
        this.generation=generation; this.disposer=Objects.requireNonNull(disposer);
    }
    public CompletionStage<T> completion() { return completion.minimalCompletionStage(); }
    public synchronized ModelLoadState state() { return state; }
    public synchronized boolean advance(ModelLoadState next) {
        boolean valid=switch(state) {
            case REQUESTED -> next==ModelLoadState.READING;
            case READING -> next==ModelLoadState.PARSING;
            case PARSING -> next==ModelLoadState.CPU_READY;
            case CPU_READY -> next==ModelLoadState.UPLOAD_QUEUED;
            default -> false;
        };
        if(valid) state=next;
        return valid;
    }
    public boolean publish(long resultGeneration,T result) {
        Objects.requireNonNull(result); boolean accepted;
        synchronized(this) {
            accepted=resultGeneration==generation && state==ModelLoadState.UPLOAD_QUEUED;
            if(accepted) { value=result; state=ModelLoadState.READY; }
        }
        if(accepted) completion.complete(result); else disposer.accept(result);
        return accepted;
    }
    public void fail(Throwable cause) {
        synchronized(this) {
            if(state==ModelLoadState.READY || state==ModelLoadState.DISPOSED || state==ModelLoadState.FAILED) return;
            state=ModelLoadState.FAILED;
        }
        completion.completeExceptionally(cause);
    }
    public void close() {
        T retired;
        synchronized(this) {
            if(state==ModelLoadState.DISPOSED) return;
            retired=value; value=null; state=ModelLoadState.DISPOSED;
        }
        completion.completeExceptionally(new CancellationException("model request disposed"));
        if(retired!=null) disposer.accept(retired);
    }
}
