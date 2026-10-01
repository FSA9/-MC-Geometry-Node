package com.mine.geometry_node.core.engine.system.model;

import com.mine.geometry_node.client.model.runtime.ClientModelService;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import com.mine.geometry_node.core.engine.system.model.runtime.ModelInstanceLifetime;
import com.mine.geometry_node.core.engine.system.model.runtime.ModelInstanceStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModelInstanceLifetimeTest {
    @Test
    void temporaryClearDoesNotRetirePersistentStoreEntries() {
        AtomicInteger released=new AtomicInteger();
        ModelInstanceStore<String> store=new ModelInstanceStore<>();
        store.put("temporary","a",0,ModelTransform.IDENTITY,true,ModelInstanceLifetime.TEMPORARY,released::incrementAndGet);
        store.put("persistent","b",0,ModelTransform.IDENTITY,true,ModelInstanceLifetime.PERSISTENT,released::incrementAndGet);

        store.clear(ModelInstanceLifetime.TEMPORARY);

        assertTrue(store.get("temporary").isEmpty());
        assertEquals(ModelInstanceLifetime.PERSISTENT,store.get("persistent").orElseThrow().lifetime());
        assertEquals(1,released.get());
        store.clear();
        assertEquals(2,released.get());
    }

    @Test
    void replacementPreservesLifetimeAndAdvancesRevision() {
        ModelInstanceStore<String> store=new ModelInstanceStore<>();
        store.put("building","asset",0,ModelTransform.IDENTITY,true,ModelInstanceLifetime.PERSISTENT,()->{});
        var first=store.get("building").orElseThrow();
        store.visible("building",false);
        var second=store.get("building").orElseThrow();

        assertEquals(ModelInstanceLifetime.PERSISTENT,second.lifetime());
        assertFalse(second.visible());
        assertTrue(second.revision()>first.revision());
    }

    @Test
    void idleGateTracksVisibilityReplacementRemovalAndLifetimeClear() {
        ModelInstanceStore<String> store = new ModelInstanceStore<>();
        assertFalse(store.hasVisibleInstances());
        store.put("a", "asset", 0, ModelTransform.IDENTITY, false, () -> {});
        assertFalse(store.hasVisibleInstances());
        store.visible("a", true);
        assertTrue(store.hasVisibleInstances());
        store.put("a", "replacement", 0, ModelTransform.IDENTITY, false, () -> {});
        assertFalse(store.hasVisibleInstances());
        store.put("b", "asset", 0, ModelTransform.IDENTITY, true, ModelInstanceLifetime.PERSISTENT, () -> {});
        store.clear(ModelInstanceLifetime.TEMPORARY);
        assertTrue(store.hasVisibleInstances());
        store.remove("b");
        assertFalse(store.hasVisibleInstances());
        store.put("c", "asset", 0, ModelTransform.IDENTITY, true, () -> {});
        store.clear();
        assertFalse(store.hasVisibleInstances());
    }

    @Test
    void localSourceIdentityNeverUsesAbsolutePath() {
        var source=ClientModelService.Source.localRoot(Path.of("C:\\private\\models"),"house/model.glb");
        String identity=source.sessionIdentity();

        assertTrue(identity.startsWith("local-session:"));
        assertFalse(identity.contains("private"));
        assertFalse(identity.contains("models"));
        assertEquals(identity,source.sessionIdentity());
        assertNotEquals(identity,ClientModelService.Source.localRoot(Path.of("C:\\private\\models"),"other.glb").sessionIdentity());
    }
}
