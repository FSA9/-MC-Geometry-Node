package com.mine.geometry_node.client.model.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModelResourceCoordinatorTest {
    @Test
    void concurrentAcquireSharesTheWholeLoadAndLastLeaseReleasesGpuResource() {
        CompletableFuture<LoadedModelResource> loading = new CompletableFuture<>();
        AtomicInteger loads = new AtomicInteger();
        ModelResourceCoordinator coordinator = new ModelResourceCoordinator((request, cancellation) -> {
            loads.incrementAndGet();
            return loading;
        });
        LocalModelAssetRequest request = new LocalModelAssetRequest(Path.of("model.glb"), 12, 34);

        ClientModelResourceLease first = coordinator.acquire(request);
        ClientModelResourceLease second = coordinator.acquire(request);
        LoadedModelResource resource = TestLoadedModelResourceFactory.create();
        loading.complete(resource);

        assertEquals(1, loads.get());
        assertSame(resource, first.resource().join());
        assertSame(resource, second.resource().join());
        first.close();
        assertFalse(resource.isReleased());
        second.close();
        assertTrue(resource.isReleased());
        assertEquals(0, coordinator.entryCount());
    }

    @Test
    void lastReleaseBeforeCompletionClosesLateResource() {
        CompletableFuture<LoadedModelResource> loading = new CompletableFuture<>();
        ModelResourceCoordinator coordinator = new ModelResourceCoordinator((request, cancellation) -> loading);
        ClientModelResourceLease lease = coordinator.acquire(
                new LocalModelAssetRequest(Path.of("late.glb"), 1, 1));

        lease.close();
        LoadedModelResource resource = TestLoadedModelResourceFactory.create();
        loading.complete(resource);

        assertTrue(resource.isReleased());
        assertEquals(0, coordinator.entryCount());
    }

    @Test
    void cancellingOneLeaseViewCannotPoisonOtherOwners() {
        CompletableFuture<LoadedModelResource> loading = new CompletableFuture<>();
        ModelResourceCoordinator coordinator = new ModelResourceCoordinator((request, cancellation) -> loading);
        LocalModelAssetRequest request = new LocalModelAssetRequest(Path.of("shared.glb"), 1, 1);
        ClientModelResourceLease first = coordinator.acquire(request);
        ClientModelResourceLease second = coordinator.acquire(request);

        assertTrue(first.resource().cancel(false));
        LoadedModelResource resource = TestLoadedModelResourceFactory.create();
        loading.complete(resource);

        assertSame(resource, second.resource().join());
        first.close();
        second.close();
    }

    @Test
    void failedLoadRemovesCoordinatorEntryWithoutLeaseUnderflow() {
        ModelResourceCoordinator coordinator = new ModelResourceCoordinator((request, cancellation) ->
                CompletableFuture.failedFuture(new IllegalArgumentException("broken fixture")));
        ClientModelResourceLease lease = coordinator.acquire(
                new LocalModelAssetRequest(Path.of("broken.glb"), 1, 1));

        assertThrows(java.util.concurrent.CompletionException.class, () -> lease.resource().join());
        assertEquals(0, coordinator.entryCount());
        assertDoesNotThrow(lease::close);
    }

}
