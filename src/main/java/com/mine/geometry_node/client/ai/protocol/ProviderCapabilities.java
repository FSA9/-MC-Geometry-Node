package com.mine.geometry_node.client.ai.protocol;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Explicit capability negotiation; UNKNOWN must never be treated as supported. */
public record ProviderCapabilities(Map<Capability, Support> values) {
    public enum Capability { CHAT, STREAMING, TOOLS, PARALLEL_TOOLS, FORCED_TOOL, STRICT_TOOLS, REASONING }
    public enum Support { SUPPORTED, UNSUPPORTED, UNKNOWN }

    public ProviderCapabilities {
        EnumMap<Capability, Support> normalized = new EnumMap<>(Capability.class);
        Objects.requireNonNull(values, "values").forEach((capability, support) ->
                normalized.put(Objects.requireNonNull(capability, "capability"),
                        Objects.requireNonNull(support, "support")));
        for (Capability capability : Capability.values()) normalized.putIfAbsent(capability, Support.UNKNOWN);
        values = Map.copyOf(normalized);
    }

    public Support support(Capability capability) {
        return values.get(Objects.requireNonNull(capability, "capability"));
    }

    public boolean isSupported(Capability capability) {
        return support(capability) == Support.SUPPORTED;
    }
}
