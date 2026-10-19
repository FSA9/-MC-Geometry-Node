package com.mine.geometry_node.core.engine.metadata;

import com.mine.geometry_node.core.metadata.*;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

/** Owned by a live runtime instance, never by a query result or global identity map. */
public class RuntimeTags {
    private List<String> tags = List.of();
    private long revision;
    private boolean updating;

    /** Monotonic owner metadata version; no-op and rejected writes leave it unchanged. */
    public final long revision() { return revision; }
    protected final void changed() { revision = Math.incrementExact(revision); }

    /** One mutation boundary shared by all capabilities on this owner. */
    protected final boolean updateMetadata(Runnable requireCurrent, BooleanSupplier mutation) {
        if (updating) throw new IllegalStateException("Recursive runtime metadata update");
        updating = true;
        try {
            requireCurrent.run();
            return mutation.getAsBoolean();
        } finally {
            updating = false;
        }
    }

    /** The caller owns the runtime thread; the returned snapshot is immutable. */
    public final List<String> tagSnapshot() { return tags; }

    public final TagAccess tags(Runnable requireCurrent) {
        Objects.requireNonNull(requireCurrent);
        return new TagAccess(new MetadataStore<>() {
            @Override public List<String> read() {
                requireCurrent.run();
                return tags;
            }
            @Override public boolean update(UnaryOperator<List<String>> change) {
                return updateMetadata(requireCurrent, () -> {
                    List<String> next = TagSet.normalize(Objects.requireNonNull(change.apply(tags)), TagPolicy.EXACT);
                    requireCurrent.run();
                    if (tags.equals(next)) return false;
                    changed();
                    tags = next;
                    return true;
                });
            }
        }, TagPolicy.EXACT);
    }
}
