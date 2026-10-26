package com.mine.geometry_node.client.runtime.model.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** Serializes the latest desired server publication, including rollback and removal, per world/id. */
final class ModelPublicationQueue<K> {
    private final Map<K, Job> latest = new HashMap<>();
    private static final class Job {
        private final Runnable barrier;
        Job(Runnable barrier) { this.barrier=barrier; }
        Runnable barrier() { return barrier; }
    }

    void submit(K key, Executor executor, Runnable publication) {
        submit(key,executor,null,publication);
    }

    /** A delete must invalidate metadata even when recreation supersedes its queued removal. */
    void remove(K key, Executor executor, Runnable removal) {
        submit(key,executor,removal,()->{});
    }

    private void submit(K key, Executor executor, Runnable barrier, Runnable publication) {
        Job ticket;
        synchronized (this) {
            Job previous=latest.get(key);
            Runnable inherited=previous==null?null:previous.barrier();
            Runnable combined=barrier==null?inherited:inherited==null?barrier:()->{inherited.run();barrier.run();};
            ticket=new Job(combined);
            latest.put(key,ticket);
        }
        try {
            executor.execute(() -> {
                synchronized (this) {
                    if (latest.get(key) != ticket) return;
                    try {
                        if(ticket.barrier()!=null)ticket.barrier().run();
                        publication.run();
                    }
                    finally { latest.remove(key, ticket); }
                }
            });
        } catch (RuntimeException failure) {
            synchronized (this) { latest.remove(key, ticket); }
            throw failure;
        }
    }
}
