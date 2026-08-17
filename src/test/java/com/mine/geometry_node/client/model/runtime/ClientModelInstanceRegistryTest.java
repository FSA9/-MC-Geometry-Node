package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ClientModelInstanceRegistryTest {
    @TempDir Path temporary;

    @Test
    void twoInstancesShareResourceButKeepIndependentNodeState() throws Exception {
        Path path = Files.write(temporary.resolve("shared.glb"), new byte[]{1});
        CompletableFuture<LoadedModelResource> loading = new CompletableFuture<>();
        ModelResourceCoordinator resources = new ModelResourceCoordinator((request, cancellation) -> loading);
        QueuedRenderThread render = new QueuedRenderThread();
        ClientModelInstanceRegistry registry = new ClientModelInstanceRegistry(resources, render);
        ModelInstanceId first = new ModelInstanceId("first");
        ModelInstanceId second = new ModelInstanceId("second");

        render.run(() -> {
            registry.upsertLocal(first, path, state(Set.of(1)));
            registry.upsertLocal(second, path, state(Set.of(2)));
        });
        LoadedModelResource shared = TestLoadedModelResourceFactory.create();
        loading.complete(shared);
        assertTrue(render.call(() -> registry.readySnapshot().isEmpty()));
        render.drain();

        List<ClientModelInstanceRegistry.ReadyInstance> ready = render.call(registry::readySnapshot);
        assertEquals(2, ready.size());
        assertSame(ready, render.call(registry::readySnapshot));
        assertSame(ready.get(0).resource(), ready.get(1).resource());
        assertNotEquals(ready.get(0).state().nodeState().hiddenNodes(), ready.get(1).state().nodeState().hiddenNodes());
        ClientModelInstanceRegistry.Diagnostics diagnostics = render.call(registry::diagnostics);
        assertEquals(2, diagnostics.instances());
        assertEquals(2, diagnostics.ready());
        assertEquals(2, diagnostics.visible());
        assertEquals(1, diagnostics.resources());
        assertEquals(1, diagnostics.sourceBytes());
        assertEquals(0, diagnostics.vertices());
        assertEquals(0, diagnostics.triangles());

        render.run(() -> registry.updateState(first, state(Set.of(3))));
        List<ClientModelInstanceRegistry.ReadyInstance> updated = render.call(registry::readySnapshot);
        assertNotSame(ready, updated);
        assertEquals(Set.of(3), updated.get(0).state().nodeState().hiddenNodes());

        render.run(() -> registry.remove(first));
        assertFalse(shared.isReleased());
        render.run(() -> registry.remove(second));
        assertTrue(shared.isReleased());
    }

    @Test
    void staleCompletionCannotReplaceNewerInstance() throws Exception {
        Path oldPath = Files.write(temporary.resolve("old.glb"), new byte[]{1});
        Path newPath = Files.write(temporary.resolve("new.glb"), new byte[]{2});
        Map<Path, CompletableFuture<LoadedModelResource>> loads = new HashMap<>();
        ModelResourceCoordinator resources = new ModelResourceCoordinator((request, cancellation) ->
                loads.computeIfAbsent(request.path(), ignored -> new CompletableFuture<>()));
        QueuedRenderThread render = new QueuedRenderThread();
        ClientModelInstanceRegistry registry = new ClientModelInstanceRegistry(resources, render);
        ModelInstanceId id = new ModelInstanceId("replace");

        render.run(() -> {
            registry.upsertLocal(id, oldPath, state(Set.of()));
            registry.upsertLocal(id, newPath, state(Set.of()));
        });
        LoadedModelResource stale = TestLoadedModelResourceFactory.create();
        loads.get(oldPath.toAbsolutePath().normalize()).complete(stale);
        render.drain();
        assertEquals(ModelLoadState.LOADING, render.call(() -> registry.status(id).state()));
        assertTrue(stale.isReleased());

        LoadedModelResource current = TestLoadedModelResourceFactory.create();
        loads.get(newPath.toAbsolutePath().normalize()).complete(current);
        render.drain();
        assertSame(current, render.call(() -> registry.status(id).resource()));
        render.run(registry::clear);
    }

    @Test
    void registryRejectsNonRenderThreadAndClearsInspectionFailure() {
        QueuedRenderThread render = new QueuedRenderThread();
        ClientModelInstanceRegistry registry = new ClientModelInstanceRegistry(
                new ModelResourceCoordinator((request, cancellation) -> new CompletableFuture<>()), render);
        assertThrows(IllegalStateException.class, registry::size);
        render.run(() -> {
            registry.upsertLocal(new ModelInstanceId("missing"), temporary.resolve("missing.glb"), state(Set.of()));
            assertEquals(ModelLoadState.FAILED, registry.status(new ModelInstanceId("missing")).state());
            assertDoesNotThrow(registry::clear);
        });
    }

    @Test
    void remainsLoadingUntilBackendArtifactCompletes() throws Exception {
        Path path = Files.write(temporary.resolve("backend-pending.glb"), new byte[]{1});
        CompletableFuture<LoadedModelResource> loading = new CompletableFuture<>();
        CompletableFuture<Void> backend = new CompletableFuture<>();
        QueuedRenderThread render = new QueuedRenderThread();
        ClientModelInstanceRegistry registry = new ClientModelInstanceRegistry(
                new ModelResourceCoordinator((request, cancellation) -> loading), render,
                (resource, ignored) -> backend);
        ModelInstanceId id = new ModelInstanceId("backend-pending");

        render.run(() -> registry.upsertLocal(id, path, state(Set.of())));
        loading.complete(TestLoadedModelResourceFactory.create());
        render.drain();

        assertEquals(ModelLoadState.LOADING, render.call(() -> registry.status(id).state()));
        assertTrue(render.call(registry::readySnapshot).isEmpty());
        backend.complete(null);
        render.drain();
        assertEquals(ModelLoadState.READY, render.call(() -> registry.status(id).state()));
        render.run(registry::clear);
    }

    private static ModelInstanceState state(Set<Integer> hidden) {
        return new ModelInstanceState(new ModelDimensionId("minecraft:overworld"), ModelInstancePlacement.at(0, 0, 0),
                true, 0, 0, new ModelInstanceNodeState(hidden, hidden.hashCode() & 0x7fffffffL));
    }

    private static final class QueuedRenderThread implements RenderThreadDispatcher {
        private final Deque<Runnable> queued = new ArrayDeque<>();
        private boolean rendering;
        @Override public boolean isRenderThread() { return rendering; }
        @Override public void execute(Runnable task) { queued.addLast(task); }
        void drain() { while (!queued.isEmpty()) run(queued.removeFirst()); }
        void run(Runnable task) { call(() -> { task.run(); return null; }); }
        <T> T call(java.util.concurrent.Callable<T> task) {
            boolean previous = rendering;
            rendering = true;
            try { return task.call(); }
            catch (Exception exception) { throw new RuntimeException(exception); }
            finally { rendering = previous; }
        }
    }
}
