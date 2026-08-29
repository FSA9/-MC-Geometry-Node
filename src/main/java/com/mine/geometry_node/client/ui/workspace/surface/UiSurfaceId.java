package com.mine.geometry_node.client.ui.workspace.surface;

import java.util.Locale;
import java.util.Objects;

/** The only public identity of a UI surface. Its display reference is always derived. */
public record UiSurfaceId(UiSurfaceType type, int number) implements Comparable<UiSurfaceId> {
    public UiSurfaceId {
        Objects.requireNonNull(type, "type");
        if (number < 1) throw new IllegalArgumentException("UI surface number must be positive");
    }

    public String ref() {
        return type.prefix() + number;
    }

    public static UiSurfaceId parse(String value) {
        String normalized = Objects.requireNonNull(value, "value").strip().toUpperCase(Locale.ROOT);
        for (UiSurfaceType type : UiSurfaceType.values()) {
            if (!normalized.startsWith(type.prefix())) continue;
            String suffix = normalized.substring(type.prefix().length());
            if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) continue;
            try {
                return new UiSurfaceId(type, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                break;
            }
        }
        throw new IllegalArgumentException("Invalid UI surface reference: " + value);
    }

    @Override
    public int compareTo(UiSurfaceId other) {
        int typeOrder = Integer.compare(type.ordinal(), other.type.ordinal());
        return typeOrder != 0 ? typeOrder : Integer.compare(number, other.number);
    }

    @Override
    public String toString() {
        return ref();
    }
}
