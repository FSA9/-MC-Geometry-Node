package com.mine.geometry_node.core.metadata;

import java.util.*;
import java.util.function.Function;

/** Explicit typed capability registration. No inheritance guessing or concrete owner dependencies. */
public final class MetadataCapabilities {
    public static final MetadataCapabilities GLOBAL = new MetadataCapabilities();
    public enum Capability { TAG, DATA }
    public record Access(TagAccess tags, DataAccess data) {
        public Access {
            if (tags == null && data == null) throw new IllegalArgumentException("Empty metadata access");
        }
        public boolean supports(Capability capability) { return capability == Capability.TAG ? tags != null : data != null; }
        public TagAccess requireTags() {
            if (tags == null) throw new UnsupportedOperationException("Target does not support tags");
            return tags;
        }
        public DataAccess requireData() {
            if (data == null) throw new UnsupportedOperationException("Target does not support data");
            return data;
        }
    }
    private record Registration<T>(Class<T> type, Set<Capability> capabilities, Function<T, Access> resolver) {
        Access resolve(Object target) {
            Access access = Objects.requireNonNull(resolver.apply(type.cast(target)), "metadata access");
            for (Capability capability : Capability.values())
                if (access.supports(capability) != capabilities.contains(capability))
                    throw new IllegalStateException("Adapter does not match registered capabilities");
            return access;
        }
    }
    private final Map<String, Registration<?>> registrations = new LinkedHashMap<>();

    public synchronized <T> void register(String id, Class<T> targetType, Set<Capability> capabilities, Function<T, Access> resolver) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid metadata target id");
        Set<Capability> declared = Set.copyOf(capabilities);
        if (declared.isEmpty()) throw new IllegalArgumentException("No capabilities declared");
        var registration = new Registration<>(Objects.requireNonNull(targetType), declared, Objects.requireNonNull(resolver));
        if (registrations.putIfAbsent(id, registration) != null) throw new IllegalArgumentException("Duplicate metadata target: " + id);
    }
    public Access resolve(String id, Object target) {
        Registration<?> registration;
        synchronized (this) { registration = registrations.get(id); }
        if (registration == null) throw new IllegalArgumentException("Unknown metadata target: " + id);
        if (!registration.type().isInstance(target)) throw new IllegalArgumentException("Incorrect metadata target type: " + id);
        return registration.resolve(target);
    }
    public synchronized Map<String, Set<Capability>> types() {
        Map<String, Set<Capability>> result = new LinkedHashMap<>();
        registrations.forEach((id, registration) -> result.put(id, registration.capabilities()));
        return Collections.unmodifiableMap(result);
    }
}
