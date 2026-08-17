package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.gpu.ModelGpuLease;
import com.mine.geometry_node.client.model.gpu.TestModelGpuLeaseFactory;
import com.mine.geometry_node.client.model.render.backend.host.entity.HostPreparedArtifact;
import com.mine.geometry_node.core.engine.system.model.identity.ModelAssetReference;
import com.mine.geometry_node.core.engine.system.model.identity.ModelAssetRevision;
import com.mine.geometry_node.core.engine.system.model.identity.ModelSourceKind;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LoadedModelResourceTest {
    private static final ModelAssetReference ASSET = new ModelAssetReference(ModelSourceKind.MEMORY,
            "test", "lazy", new ModelAssetRevision(1, 0, ""));

    @Test
    void gpuArtifactIsAbsentUntilFirstConsumerAndPreparedOnlyOnce() {
        AtomicInteger preparations = new AtomicInteger();
        CompletableFuture<ModelGpuLease> pending = new CompletableFuture<>();
        LoadedModelResource resource = TestLoadedModelResourceFactory.create(() -> {
            preparations.incrementAndGet();
            return pending;
        });

        assertEquals(0, preparations.get());
        assertTrue(resource.standaloneGpuResource().isEmpty());
        assertTrue(resource.standaloneGpuResource().isEmpty());
        assertEquals(1, preparations.get());

        pending.complete(TestModelGpuLeaseFactory.create(ASSET));
        assertTrue(resource.standaloneGpuResource().isPresent());
        assertEquals(1, preparations.get());
    }

    @Test
    void releaseBeforeGpuPreparationCompletesClosesLateLease() {
        CompletableFuture<ModelGpuLease> pending = new CompletableFuture<>();
        LoadedModelResource resource = TestLoadedModelResourceFactory.create(() -> pending);
        assertTrue(resource.standaloneGpuResource().isEmpty());

        resource.release();
        ModelGpuLease late = TestModelGpuLeaseFactory.create(ASSET);
        pending.complete(late);

        assertTrue(resource.isReleased());
        assertTrue(late.isClosed());
        assertTrue(resource.standaloneGpuResource().isEmpty());
    }

    @Test
    void releaseCancelsGpuPreparationExactlyOnce() {
        AtomicInteger cancellations = new AtomicInteger();
        CompletableFuture<ModelGpuLease> pending = new CompletableFuture<>();
        LoadedModelResource resource = TestLoadedModelResourceFactory.create(() -> pending,
                cancellations::incrementAndGet);
        assertTrue(resource.standaloneGpuResource().isEmpty());

        resource.release();
        resource.release();

        assertEquals(1, cancellations.get());
    }

    @Test
    void failedGpuPreparationRemainsNonBlockingAndIsNotRetriedPerFrame() {
        AtomicInteger preparations = new AtomicInteger();
        LoadedModelResource resource = TestLoadedModelResourceFactory.create(() -> {
            preparations.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("upload failed"));
        });

        assertDoesNotThrow(resource::standaloneGpuResource);
        assertTrue(resource.standaloneGpuResource().isEmpty());
        assertEquals(1, preparations.get());
        Throwable failure = resource.standaloneGpuFailure().orElseThrow();
        assertInstanceOf(IllegalStateException.class, failure);
        assertEquals("upload failed", failure.getMessage());
        assertSame(failure, resource.standaloneGpuFailureForReport().orElseThrow());
        assertTrue(resource.standaloneGpuFailureForReport().isEmpty());
    }

    @Test
    void releaseBeforeFirstConsumerNeverStartsGpuPreparation() {
        AtomicInteger preparations = new AtomicInteger();
        LoadedModelResource resource = TestLoadedModelResourceFactory.create(() -> {
            preparations.incrementAndGet();
            return CompletableFuture.completedFuture(TestModelGpuLeaseFactory.create(ASSET));
        });

        resource.release();

        assertTrue(resource.standaloneGpuResource().isEmpty());
        assertEquals(0, preparations.get());
    }

    @Test
    void synchronousFactoryFailureIsCapturedOnce() {
        AtomicInteger preparations = new AtomicInteger();
        LoadedModelResource resource = TestLoadedModelResourceFactory.create(() -> {
            preparations.incrementAndGet();
            throw new IllegalStateException("factory failed");
        });

        assertTrue(resource.standaloneGpuResource().isEmpty());
        assertTrue(resource.standaloneGpuResource().isEmpty());
        assertEquals(1, preparations.get());
        assertEquals("factory failed", resource.standaloneGpuFailure().orElseThrow().getMessage());
    }

    @Test
    void hostArtifactIsPreparedOnceAndReleasedExactlyOnce() {
        AtomicInteger preparations = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        LoadedModelResource resource = TestLoadedModelResourceFactory.create();
        BackendArtifactKey<HostPreparedArtifact> backendKey = new BackendArtifactKey<>("test-host");
        java.util.function.Supplier<BackendArtifactLease<HostPreparedArtifact>> factory = () -> {
            preparations.incrementAndGet();
            HostPreparedArtifact artifact = HostPreparedArtifact.prepare(resource.definition(), resource.metadata());
            return new BackendArtifactLease<>(artifact, releases::incrementAndGet);
        };

        assertEquals(0, preparations.get());
        assertSame(resource.backendArtifact(backendKey, factory).orElseThrow(),
                resource.backendArtifact(backendKey, factory).orElseThrow());
        assertEquals(1, preparations.get());

        resource.release();
        resource.release();
        assertEquals(1, releases.get());
        assertTrue(resource.backendArtifact(backendKey, factory).isEmpty());
    }

    @Test
    void releaseBeforeFirstHostConsumerDoesNotPrepareArtifact() {
        AtomicInteger preparations = new AtomicInteger();
        LoadedModelResource resource = TestLoadedModelResourceFactory.create();
        BackendArtifactKey<HostPreparedArtifact> backendKey = new BackendArtifactKey<>("test-host");
        java.util.function.Supplier<BackendArtifactLease<HostPreparedArtifact>> factory = () -> {
            preparations.incrementAndGet();
            throw new AssertionError("host artifact must remain lazy");
        };

        resource.release();

        assertTrue(resource.backendArtifact(backendKey, factory).isEmpty());
        assertEquals(0, preparations.get());
    }

    @Test
    void backendArtifactFailureIsCachedAndReportedOnce() {
        LoadedModelResource resource = TestLoadedModelResourceFactory.create();
        BackendArtifactKey<Object> key = new BackendArtifactKey<>("failing-backend");
        AtomicInteger attempts = new AtomicInteger();
        java.util.function.Supplier<BackendArtifactLease<Object>> factory = () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("prepare failed");
        };

        assertTrue(resource.backendArtifact(key, factory).isEmpty());
        assertTrue(resource.backendArtifact(key, factory).isEmpty());
        assertEquals(1, attempts.get());
        assertEquals("prepare failed", resource.backendArtifactFailureForReport(key).orElseThrow().getMessage());
        assertTrue(resource.backendArtifactFailureForReport(key).isEmpty());
    }

    @Test
    void asynchronousBackendArtifactIsPublishedAtomicallyAndReleased() {
        LoadedModelResource resource = TestLoadedModelResourceFactory.create();
        BackendArtifactKey<Object> key = new BackendArtifactKey<>("async-backend");
        CompletableFuture<BackendArtifactLease<Object>> pending = new CompletableFuture<>();
        AtomicInteger releases = new AtomicInteger();

        assertTrue(resource.backendArtifactAsync(key, () -> pending).isEmpty());
        assertTrue(resource.existingBackendArtifact(key).isEmpty());
        pending.complete(new BackendArtifactLease<>(new Object(), releases::incrementAndGet));

        assertTrue(resource.existingBackendArtifact(key).isPresent());
        resource.release();
        assertEquals(1, releases.get());
    }
}
