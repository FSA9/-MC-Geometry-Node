package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.gpu.*;
import com.mine.geometry_node.core.engine.system.model.identity.ModelAssetReference;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;

import java.util.Objects;
import java.util.Optional;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Read-only shared resource view. Only the coordinator can release its ownership lease. */
public final class LoadedModelResource {
    private final ModelAssetReference asset;
    private final ModelDefinition definition;
    private final Supplier<CompletableFuture<ModelGpuLease>> gpuLeaseFactory;
    private final StaticModelRenderMetadata metadata;
    private final long sourceBytes;
    private final long triangles;
    private final long loadNanos;
    private CompletableFuture<ModelGpuLease> gpuLease;
    private Throwable gpuFailure;
    private boolean gpuFailureReported;
    private boolean gpuLeaseClosed;
    private final Map<Object, BackendArtifactLease<?>> backendArtifacts = new IdentityHashMap<>();
    private final Map<Object, RuntimeException> backendArtifactFailures = new IdentityHashMap<>();
    private final java.util.Set<Object> reportedBackendArtifactFailures =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final java.util.Set<Object> reportedDiagnostics =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean released;

    LoadedModelResource(ModelDefinition definition, Supplier<CompletableFuture<ModelGpuLease>> gpuLeaseFactory,
                        StaticModelRenderMetadata metadata, long sourceBytes,
                        long triangles, long loadNanos) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.asset = definition.source();
        this.gpuLeaseFactory = Objects.requireNonNull(gpuLeaseFactory, "gpuLeaseFactory");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.sourceBytes = sourceBytes;
        this.triangles = triangles;
        this.loadNanos = loadNanos;
    }

    public ModelAssetReference asset() { return asset; }
    public ModelDefinition definition() { return definition; }
    /** Starts standalone preparation on first real consumer and never blocks the render thread. */
    public synchronized Optional<ModelGpuResource> standaloneGpuResource() {
        if (released) return Optional.empty();
        CompletableFuture<ModelGpuLease> pending = gpuLease;
        if (pending == null) {
            try {
                pending = Objects.requireNonNull(gpuLeaseFactory.get(), "gpuLeaseFactory result");
            } catch (RuntimeException failure) {
                pending = CompletableFuture.failedFuture(failure);
            }
            gpuLease = pending;
            pending.whenComplete((lease, failure) -> {
                ModelGpuLease close = null;
                synchronized (LoadedModelResource.this) {
                    if (failure != null) {
                        gpuFailure = unwrap(failure);
                    } else if (released && !gpuLeaseClosed) {
                        gpuLeaseClosed = true;
                        close = lease;
                    }
                }
                if (close != null) close.close();
            });
        }
        if (!pending.isDone() || pending.isCompletedExceptionally() || pending.isCancelled()) return Optional.empty();
        ModelGpuLease ready = pending.getNow(null);
        return ready == null || ready.isClosed() ? Optional.empty() : Optional.of(ready.resource());
    }
    public synchronized Optional<Throwable> standaloneGpuFailure() { return Optional.ofNullable(gpuFailure); }
    public synchronized Optional<Throwable> standaloneGpuFailureForReport() {
        if (gpuFailure == null || gpuFailureReported) return Optional.empty();
        gpuFailureReported = true;
        return Optional.of(gpuFailure);
    }
    /** Acquires one shared, lazy artifact per backend key. The caller owns backend thread constraints. */
    public synchronized <T> Optional<T> backendArtifact(BackendArtifactKey<T> backendKey,
                                                         Supplier<BackendArtifactLease<T>> factory) {
        if (released) return Optional.empty();
        Objects.requireNonNull(backendKey, "backendKey");
        Objects.requireNonNull(factory, "factory");
        BackendArtifactLease<?> lease = backendArtifacts.get(backendKey);
        if (backendArtifactFailures.containsKey(backendKey)) return Optional.empty();
        if (lease == null) {
            try {
                lease = Objects.requireNonNull(factory.get(), "backend artifact factory result");
            } catch (RuntimeException failure) {
                backendArtifactFailures.put(backendKey, failure);
                return Optional.empty();
            }
            backendArtifacts.put(backendKey, lease);
        }
        @SuppressWarnings("unchecked") BackendArtifactLease<T> typed = (BackendArtifactLease<T>) lease;
        return Optional.of(typed.artifact());
    }
    public synchronized <T> Optional<T> existingBackendArtifact(BackendArtifactKey<T> backendKey) {
        BackendArtifactLease<?> lease = backendArtifacts.get(Objects.requireNonNull(backendKey, "backendKey"));
        if (lease == null || lease.isClosed()) return Optional.empty();
        @SuppressWarnings("unchecked") BackendArtifactLease<T> typed = (BackendArtifactLease<T>) lease;
        return Optional.of(typed.artifact());
    }
    public synchronized boolean reportDiagnosticOnce(Object key) {
        return !released && reportedDiagnostics.add(Objects.requireNonNull(key, "key"));
    }
    public synchronized Optional<RuntimeException> backendArtifactFailureForReport(BackendArtifactKey<?> key) {
        RuntimeException failure = backendArtifactFailures.get(Objects.requireNonNull(key, "key"));
        if (failure == null || !reportedBackendArtifactFailures.add(key)) return Optional.empty();
        return Optional.of(failure);
    }
    public StaticModelRenderMetadata metadata() { return metadata; }
    public long sourceBytes() { return sourceBytes; }
    public long triangles() { return triangles; }
    public long loadNanos() { return loadNanos; }
    public synchronized boolean isReleased() { return released; }

    void release() {
        ModelGpuLease close = null;
        java.util.List<BackendArtifactLease<?>> closeArtifacts;
        synchronized (this) {
            if (released) return;
            released = true;
            if (gpuLease != null && gpuLease.isDone() && !gpuLease.isCompletedExceptionally()
                    && !gpuLease.isCancelled() && !gpuLeaseClosed) {
                gpuLeaseClosed = true;
                close = gpuLease.getNow(null);
            }
            closeArtifacts = java.util.List.copyOf(backendArtifacts.values());
            backendArtifacts.clear();
            backendArtifactFailures.clear();
            reportedBackendArtifactFailures.clear();
            reportedDiagnostics.clear();
        }
        if (close != null) close.close();
        closeArtifacts.forEach(BackendArtifactLease::close);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
