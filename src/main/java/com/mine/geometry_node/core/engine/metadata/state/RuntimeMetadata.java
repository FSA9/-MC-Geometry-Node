package com.mine.geometry_node.core.engine.metadata.state;

import com.mine.geometry_node.core.metadata.*;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Tags and typed data whose lifetime is exactly that of their owning resource. */
public final class RuntimeMetadata extends RuntimeTags {
    private Map<String, Object> data = Map.of();

    public MetadataCapabilities.Access access(DataValuePolicy policy, Runnable requireCurrent) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(requireCurrent);
        requireCurrent.run();
        return new MetadataCapabilities.Access(tags(requireCurrent), new DataAccess(new MetadataStore<>() {
            @Override public Map<String, Object> read() {
                requireCurrent.run();
                return DataDictionary.snapshot(data, policy);
            }
            @Override public boolean update(UnaryOperator<Map<String, Object>> change) {
                return updateMetadata(requireCurrent, () -> {
                    Map<String, Object> next = DataDictionary.snapshot(
                            Objects.requireNonNull(change.apply(DataDictionary.snapshot(data, policy))), policy);
                    requireCurrent.run();
                    if (DataDictionary.equivalent(data, next, policy)) return false;
                    changed();
                    data = next;
                    return true;
                });
            }
        }, policy));
    }
}
