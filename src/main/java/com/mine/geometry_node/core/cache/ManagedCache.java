package com.mine.geometry_node.core.cache;

import java.util.concurrent.CompletionStage;

/**
 * Control-plane adapter, not storage. Methods return promptly; implementations dispatch to their
 * owning thread/executor. A user clear must preserve live leases and keep the business usable.
 * Namespace-specific and lifecycle clears are opt-in: never silently clear unrelated servers.
 */
public interface ManagedCache {
    CompletionStage<CacheStats> stats();
    CompletionStage<Void> clear(CacheClearRequest request);
    /** Legacy adapters report completion; owners retaining resources should override this outcome. */
    default CompletionStage<CacheClearOutcome> clearOutcome(CacheClearRequest request) {
        return clear(request).thenApply(ignored -> CacheClearOutcome.completed());
    }
    default boolean supports(CacheClearRequest request) {
        return request.reason() == CacheClearRequest.Reason.USER && request.namespace() == null;
    }
}
