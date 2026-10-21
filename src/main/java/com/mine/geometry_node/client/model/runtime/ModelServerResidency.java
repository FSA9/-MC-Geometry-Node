package com.mine.geometry_node.client.model.runtime;

import java.lang.ref.WeakReference;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Server-owner-thread state. Availability is checked again at every publication, not just callbacks. */
final class ModelServerResidency<G> implements AutoCloseable {
    private static final int MAX_ATTEMPTS=4;
    record Build<G>(CompletionStage<G> completion, Runnable cancel) {}
    record RetryStatus(int failedAttempts,long retryInTicks,boolean exhausted) {}
    private final Executor owner;
    private final BooleanSupplier available;
    private final Supplier<Build<G>> build;
    private final Predicate<G> publish;
    private final Runnable withdraw;
    private final Consumer<Throwable> failed;
    private WeakReference<G> cached=new WeakReference<>(null);
    private G active;
    private Attempt<G> pending;
    private boolean closed;
    private long tick,retryAt=Long.MAX_VALUE;
    private int failedAttempts;

    ModelServerResidency(Executor owner, BooleanSupplier available, Supplier<Build<G>> build,
                         Predicate<G> publish, Runnable withdraw, Consumer<Throwable> failed) {
        this.owner=owner;this.available=available;this.build=build;
        this.publish=publish;this.withdraw=withdraw;this.failed=failed;
    }

    void reconcile() {
        if(closed)return;
        if(!available.getAsBoolean()){suspend();return;}
        if(active!=null||pending!=null)return;
        G reusable=cached.get();
        if(reusable!=null){offer(reusable);return;}
        if(failedAttempts>=MAX_ATTEMPTS||(failedAttempts>0&&tick<retryAt))return;
        Attempt<G> attempt=new Attempt<>();
        pending=attempt;
        try {
            attempt.work=build.get();
            attempt.work.completion().whenComplete((geometry,error)->owner.execute(()->{
                if(closed||pending!=attempt)return;
                pending=null;
                if(!available.getAsBoolean()){suspend();return;}
                if(error!=null){recordFailure(error);return;}
                offer(geometry);
            }));
        } catch(RuntimeException failure) {
            if(pending==attempt)pending=null;
            recordFailure(failure);
        }
    }

    /** Bounded retries after 20, 40, then 80 server ticks. No worker sleeps or immediate resubmission. */
    void tick() {
        if(closed)return;
        tick++;
        if(failedAttempts>0&&failedAttempts<MAX_ATTEMPTS&&tick>=retryAt)reconcile();
    }

    RetryStatus retryStatus() {
        return new RetryStatus(failedAttempts,retryAt==Long.MAX_VALUE?-1:Math.max(0,retryAt-tick),
                failedAttempts>=MAX_ATTEMPTS);
    }

    private void recordFailure(Throwable error) {
        failedAttempts++;
        retryAt=failedAttempts<MAX_ATTEMPTS?tick+(20L<<(failedAttempts-1)):Long.MAX_VALUE;
        failed.accept(error);
    }

    private void resetRetries(){failedAttempts=0;retryAt=Long.MAX_VALUE;}

    /** A client build is optional reusable input, never authority for server availability. */
    void offer(G geometry) {
        if(closed)return;
        cancelPending();
        cached=new WeakReference<>(geometry);
        if(!available.getAsBoolean()){suspend();return;}
        if(publish.test(geometry)){active=geometry;resetRetries();}
        else suspend();
    }

    private void suspend() {
        cancelPending();
        active=null;
        resetRetries();
        withdraw.run();
    }

    private void cancelPending() {
        Attempt<G> old=pending;pending=null;
        if(old!=null&&old.work!=null)old.work.cancel().run();
    }

    @Override public void close() {
        if(closed)return;
        closed=true;suspend();cached.clear();
    }

    private static final class Attempt<G> { Build<G> work; }
}
