package com.mine.geometry_node.core.cache;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Common-side cache control plane. Owner callbacks never execute under the registry lock.
 * Registrations are explicitly owned; closing one unregisters it without destroying resources.
 * Descriptor lifetime documents ownership, it is not permission to destroy live resources.
 */
public final class CacheRegistry {
    public static final CacheRegistry GLOBAL = new CacheRegistry(Duration.ofSeconds(15));
    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private final long timeoutMillis;
    private long nextId;

    public CacheRegistry() { this(Duration.ofSeconds(15)); }
    public CacheRegistry(Duration timeout) {
        timeoutMillis = Objects.requireNonNull(timeout).toMillis();
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Cache timeout must be positive");
    }

    public synchronized Registration register(CacheDescriptor descriptor, ManagedCache backend) {
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(backend);
        for (Entry entry : entries.values()) {
            if (entry.descriptor.id().equals(descriptor.id()) && !entry.descriptor.equals(descriptor))
                throw new IllegalArgumentException("Conflicting cache descriptor: " + descriptor.id());
        }
        long id = ++nextId;
        Entry entry = new Entry(descriptor, backend);
        entries.put(id, entry);
        return new Registration(id, entry);
    }

    public CompletableFuture<List<CacheReport>> inspect(Predicate<CacheDescriptor> selector) {
        var selected = select(selector);
        List<CompletableFuture<CacheReport>> jobs = new ArrayList<>();
        for (Entry entry : selected) {
            jobs.add(invoke(() -> {
                if (!entry.active) return CompletableFuture.completedFuture(CacheStats.memory(0, 0));
                return entry.backend.stats();
            }).handle((stats, failure) -> {
                String error = failure == null ? "" : message(failure);
                boolean clearable = false;
                try { clearable = entry.active && entry.backend.supports(CacheClearRequest.user()); }
                catch (Throwable unsupported) { rethrowFatal(unsupported); error = join(error, message(unsupported)); }
                return new CacheReport(entry.descriptor, stats == null ? CacheStats.unknown() : stats, clearable, error);
            }));
        }
        return sequence(jobs).thenApply(reports -> {
            Map<String, CacheReport> grouped = new TreeMap<>();
            for (CacheReport report : reports) grouped.merge(report.descriptor().id(), report, (a, b) ->
                    new CacheReport(a.descriptor(), a.stats().plus(b.stats()), a.clearable() || b.clearable(),
                            join(a.error(), b.error())));
            return List.copyOf(grouped.values());
        });
    }

    public CompletableFuture<List<CacheClearResult>> clear(Predicate<CacheDescriptor> selector, CacheClearRequest request) {
        Objects.requireNonNull(request);
        List<CompletableFuture<CacheClearResult>> jobs = new ArrayList<>();
        for (Entry entry : select(selector)) {
            try {
                if (!entry.active || !entry.backend.supports(request)) {
                    jobs.add(CompletableFuture.completedFuture(result(entry, 0, 1, null)));
                } else {
                    jobs.add(invoke(() -> entry.active ? entry.backend.clearOutcome(request)
                                    : CompletableFuture.completedFuture(CacheClearOutcome.skipped()))
                            .handle((outcome, failure) -> {
                                if (failure != null) return result(entry, 0, 0, failure);
                                if (outcome == null) return result(entry, 0, 0, new IllegalStateException("Missing clear outcome"));
                                return new CacheClearResult(entry.descriptor, outcome.performed() ? 1 : 0,
                                        outcome.performed() ? 0 : 1, List.of(), outcome.retainedEntries());
                            }));
                }
            } catch (Throwable failure) {
                rethrowFatal(failure);
                jobs.add(CompletableFuture.completedFuture(result(entry, 0, 0, failure)));
            }
        }
        return sequence(jobs).thenApply(results -> {
            Map<String, CacheClearResult> grouped = new TreeMap<>();
            for (CacheClearResult result : results) grouped.merge(result.descriptor().id(), result, (a, b) -> {
                List<String> failures = new ArrayList<>(a.errors());
                failures.addAll(b.errors());
                long retained = a.retainedEntries() < 0 || b.retainedEntries() < 0 ? -1
                        : Long.MAX_VALUE - a.retainedEntries() < b.retainedEntries() ? Long.MAX_VALUE
                        : a.retainedEntries() + b.retainedEntries();
                return new CacheClearResult(a.descriptor(), a.cleared() + b.cleared(), a.skipped() + b.skipped(), failures, retained);
            });
            return List.copyOf(grouped.values());
        });
    }

    public synchronized List<CacheDescriptor> descriptors() {
        return entries.values().stream().map(entry -> entry.descriptor).distinct()
                .sorted(Comparator.comparing(CacheDescriptor::id)).toList();
    }

    private List<Entry> select(Predicate<CacheDescriptor> selector) {
        Objects.requireNonNull(selector);
        List<Entry> snapshot;
        synchronized (this) { snapshot = List.copyOf(entries.values()); }
        return snapshot.stream().filter(entry -> selector.test(entry.descriptor)).toList();
    }

    private <T> CompletableFuture<T> invoke(Supplier<CompletionStage<T>> action) {
        // Use a separate result so timeout never mutates an owner's shared future.
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            Objects.requireNonNull(action.get(), "Cache adapter returned null stage")
                    .whenComplete((value, error) -> {
                        if (error == null) result.complete(value); else result.completeExceptionally(error);
                    });
        } catch (Throwable error) { result.completeExceptionally(error); rethrowFatal(error); }
        return result.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private static <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> jobs) {
        return CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> jobs.stream().map(CompletableFuture::join).toList());
    }
    private static CacheClearResult result(Entry entry, int cleared, int skipped, Throwable error) {
        return new CacheClearResult(entry.descriptor, cleared, skipped, error == null ? List.of() : List.of(message(error)), error == null ? 0 : -1);
    }
    private static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError fatal) throw fatal;
        if (error instanceof ThreadDeath fatal) throw fatal;
    }
    private static String message(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null)
            error = error.getCause();
        return error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
    }
    private static String join(String left, String right) {
        if (left.isEmpty()) return right;
        return right.isEmpty() || right.equals(left) ? left : left + "; " + right;
    }
    private static final class Entry {
        final CacheDescriptor descriptor;
        final ManagedCache backend;
        volatile boolean active = true;
        Entry(CacheDescriptor descriptor, ManagedCache backend) { this.descriptor = descriptor; this.backend = backend; }
    }
    /** Close prevents new dispatch; already dispatched work must validate its owner's generation. */
    public final class Registration implements AutoCloseable {
        private final long id;
        private final Entry entry;
        private Registration(long id, Entry entry) { this.id = id; this.entry = entry; }
        @Override public void close() {
            synchronized (CacheRegistry.this) {
                entry.active = false;
                entries.remove(id, entry);
            }
        }
    }
}
