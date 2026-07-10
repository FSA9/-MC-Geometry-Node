package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

final class BlockPropertySetter {

    private BlockPropertySetter() {
    }

    static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, T value) {
        if (state == null) {
            return null;
        }

        if (!state.hasProperty(property)) {
            return state;
        }

        if (!property.getPossibleValues().contains(value)) {
            return state;
        }

        return state.setValue(property, value);
    }

    static BlockState setFromString(BlockState state, String propertyName, String rawValue) {
        if (state == null || propertyName == null || rawValue == null) {
            return state;
        }

        Property<?> property = findProperty(state, propertyName);
        if (property == null) {
            return state;
        }

        return setFromString(state, property, rawValue);
    }

    private static Property<?> findProperty(BlockState state, String propertyName) {
        String normalizedName = propertyName.trim();
        if (normalizedName.isEmpty()) {
            return null;
        }

        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(normalizedName)) {
                return property;
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> BlockState setFromString(BlockState state, Property<T> property, String rawValue) {
        String normalizedValue = rawValue.trim();
        if (normalizedValue.isEmpty()) {
            return state;
        }

        return property.getValue(normalizedValue)
                .map(value -> state.setValue(property, value))
                .orElse(state);
    }
}
