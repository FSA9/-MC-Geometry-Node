/**
 * Shared, JDK-only cache infrastructure for client, server and common code.
 *
 * <p>{@link com.mine.geometry_node.core.cache.BoundedCache} owns bounded storage,
 * expiration, eviction and value disposal. {@link com.mine.geometry_node.core.cache.CacheRegistry}
 * and its descriptor, statistics and clear contracts expose owner-provided cache controls;
 * registration does not transfer ownership of cached resources.
 * {@link com.mine.geometry_node.core.cache.CacheExecutors} provides bounded background
 * I/O dispatch for cache maintenance and persistence.
 *
 * <p>Concrete caches remain in their owning domains. Their adapters retain responsibility
 * for thread affinity, live leases, lifecycle validation and disk layout. This package must
 * not depend on game, engine, client, UI or business implementations. General-purpose
 * asynchronous work does not belong in the cache maintenance executor.
 */
package com.mine.geometry_node.core.cache;
