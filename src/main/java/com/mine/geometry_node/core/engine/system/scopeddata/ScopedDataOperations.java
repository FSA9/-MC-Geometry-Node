package com.mine.geometry_node.core.engine.system.scopeddata;

import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataDataValues;
import com.mine.geometry_node.core.metadata.DataAccess;
import com.mine.geometry_node.core.network.packet.scopeddata.*;
import net.minecraft.core.RegistryAccess;
import java.util.*;

/** Stateless operations over existing scoped providers. No dictionaries or baselines are retained. */
public final class ScopedDataOperations {
    private ScopedDataOperations() {}
    public interface Access {
        boolean permitted();
        Iterable<String> dimensions();
        DataAccess data(String target);
    }
    public static PacketScopedDataResult execute(PacketScopedDataRequest request, Access access, RegistryAccess registries) {
        if (!access.permitted()) return PacketScopedDataResult.error(request, "forbidden");
        try {
            if (request.action() == PacketScopedDataRequest.Action.CATALOG) {
                SortedSet<String> dimensions = new TreeSet<>();
                int scanned = 0;
                for (String dimension : access.dimensions()) {
                    if (++scanned > PacketScopedDataResult.MAX_DIMENSIONS) return PacketScopedDataResult.error(request, "too_large");
                    PacketScopedDataRequest.validateDimension(dimension); dimensions.add(dimension);
                }
                var result = new PacketScopedDataResult(request.requestId(), request.action(), request.target(),
                        List.copyOf(dimensions), Map.of(), "", "");
                result.ensureEncodable(registries); return result;
            }
            DataAccess data = access.data(request.target());
            if (request.action() == PacketScopedDataRequest.Action.READ) return readable(request, data.values(), registries);
            Map<String, Object> next = GraphMetadataDataValues.snapshot(request.replacement());
            GraphMetadataDataValues.encode(next, registries, GraphMetadataDataValues.MAX_WRITE_BYTES);
            // Precompute and encode the exact success response before allowing the owner to commit.
            PacketScopedDataResult success = readable(request, next, registries);
            data.update(current -> {
                if (!access.permitted()) throw new Forbidden();
                if (!GraphMetadataDataValues.revision(current, registries).equals(request.expectedRevision())) throw new Conflict();
                return next;
            });
            // Both a no-op and a historical capacity rejection can return false.
            if (!GraphMetadataDataValues.equivalent(data.values(), next)) return PacketScopedDataResult.error(request, "invalid");
            return success;
        } catch (Conflict conflict) { return PacketScopedDataResult.error(request, "conflict"); }
        catch (Forbidden forbidden) { return PacketScopedDataResult.error(request, "forbidden"); }
        catch (IllegalArgumentException invalid) { return PacketScopedDataResult.error(request, "too_large"); }
        catch (IllegalStateException | UnsupportedOperationException unavailable) { return PacketScopedDataResult.error(request, "unavailable"); }
    }
    private static PacketScopedDataResult readable(PacketScopedDataRequest request, Map<String, Object> values, RegistryAccess registries) {
        Map<String, Object> snapshot = GraphMetadataDataValues.snapshot(values);
        var result = new PacketScopedDataResult(request.requestId(), request.action(), request.target(), List.of(), snapshot,
                GraphMetadataDataValues.revision(snapshot, registries), "");
        result.ensureEncodable(registries); return result;
    }
    private static final class Conflict extends RuntimeException {}
    private static final class Forbidden extends RuntimeException {}
}
