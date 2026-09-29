package com.mine.geometry_node.core.engine.system.model;

import com.mine.geometry_node.core.engine.system.model.runtime.*;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ModelRepositoryTest {
    private static final class ManualExecutor implements Executor {
        final Queue<Runnable> tasks=new ArrayDeque<>();
        @Override public void execute(Runnable task){tasks.add(task);}
        void run(){tasks.remove().run();}
    }
    @Test void coalescesOneHundredLeasesAndDisposesOnlyAfterLastRelease() {
        ManualExecutor executor=new ManualExecutor();AtomicInteger loads=new AtomicInteger(),disposals=new AtomicInteger();
        try(var repository=new ModelRepository<String,String>(executor,value->disposals.incrementAndGet())) {
            List<ModelLease<String>> leases=new ArrayList<>();
            for(int i=0;i<100;i++)leases.add(repository.acquire("asset",(key,cancel)->{loads.incrementAndGet();return "mesh";}));
            assertEquals(1,executor.tasks.size());executor.run();assertEquals(1,loads.get());
            for(var lease:leases)assertEquals("mesh",lease.completion().toCompletableFuture().join());
            for(int i=0;i<99;i++)leases.get(i).close();assertEquals(0,disposals.get());
            leases.get(99).close();leases.get(99).close();assertEquals(1,disposals.get());assertEquals(0,repository.size());
        }
    }
    @Test void cancellingOneConsumerDoesNotCancelSharedWork() {
        ManualExecutor executor=new ManualExecutor();
        try(var repository=new ModelRepository<String,String>(executor,ignored->{})) {
            var first=repository.acquire("asset",(key,cancel)->"mesh");
            var second=repository.acquire("asset",(key,cancel)->fail("duplicate loader"));
            first.close();executor.run();
            assertThrows(CompletionException.class,()->first.completion().toCompletableFuture().join());
            assertEquals("mesh",second.completion().toCompletableFuture().join());second.close();
        }
    }
    @Test void invalidationLetsExistingReadyLeasesLiveButStartsNewGeneration() {
        ManualExecutor executor=new ManualExecutor();List<String> retired=new ArrayList<>();
        try(var repository=new ModelRepository<String,String>(executor,retired::add)) {
            var old=repository.acquire("asset",(key,cancel)->"old");executor.run();repository.invalidate("asset");
            assertTrue(retired.isEmpty());assertEquals("old",old.completion().toCompletableFuture().join());
            var fresh=repository.acquire("asset",(key,cancel)->"new");executor.run();assertTrue(fresh.generation()>old.generation());
            old.close();assertEquals(List.of("old"),retired);fresh.close();assertEquals(List.of("old","new"),retired);
        }
    }
    @Test void lateCancelledResultIsDisposedAndNeverPublished() throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();CountDownLatch entered=new CountDownLatch(1),finish=new CountDownLatch(1),disposed=new CountDownLatch(1);
        try(var repository=new ModelRepository<String,String>(executor,value->disposed.countDown(),true)) {
            var lease=repository.acquire("asset",(key,cancel)->{entered.countDown();assertTrue(finish.await(5,TimeUnit.SECONDS));return "late";});
            assertTrue(entered.await(5,TimeUnit.SECONDS));lease.close();finish.countDown();assertTrue(disposed.await(5,TimeUnit.SECONDS));
            assertThrows(CompletionException.class,()->lease.completion().toCompletableFuture().join());assertEquals(0,repository.size());
        }
    }
    @Test void rejectedWorkFailsAndCanBeRetriedAfterLeaseClose() {
        AtomicInteger rejected=new AtomicInteger();
        try(var repository=new ModelRepository<String,String>(task->{rejected.incrementAndGet();throw new RejectedExecutionException("full");},ignored->{})) {
            var lease=repository.acquire("asset",(key,cancel)->"never");
            assertThrows(CompletionException.class,()->lease.completion().toCompletableFuture().join());lease.close();
            var retry=repository.acquire("asset",(key,cancel)->"never");assertEquals(2,rejected.get());retry.close();
        }
    }
    @Test void instanceTransformAndVisibilityAreIndependentAndReplacementReleasesOnce() {
        AtomicInteger released=new AtomicInteger();ModelInstanceStore<String> store=new ModelInstanceStore<>();
        store.put("a","asset",0,ModelTransform.IDENTITY,true,released::incrementAndGet);
        store.put("b","asset",0,ModelTransform.IDENTITY,true,released::incrementAndGet);
        store.visible("a",false);store.transform("a",new ModelTransform(4,5,6,0,0,0,1,2,2,2));
        assertFalse(store.get("a").orElseThrow().visible());assertTrue(store.get("b").orElseThrow().visible());
        assertEquals(0,store.get("b").orElseThrow().transform().x());
        store.put("a","new",1,ModelTransform.IDENTITY,true,released::incrementAndGet);assertEquals(1,released.get());
        assertTrue(store.remove("a"));assertFalse(store.remove("a"));store.close();assertEquals(3,released.get());
    }
}
