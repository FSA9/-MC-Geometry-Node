package com.mine.geometry_node.client.ui.persistence.config;

import java.util.List;

public record ConfigDefinition(
        String key,
        String label,
        String description,
        Type type,
        double min,
        double max,
        double step,
        List<String> choices
) {
    public enum Type {
        BOOLEAN,
        INTEGER,
        FLOAT,
        CHOICE,
        PATH_LIST,
        SHORTCUT,
        KEY_BINDING
    }

    public static ConfigDefinition bool(String key, String label, String description) {
        return new ConfigDefinition(key, label, description, Type.BOOLEAN, 0, 0, 0, List.of());
    }

    public static ConfigDefinition integer(String key, String label, String description, int min, int max, int step) {
        return new ConfigDefinition(key, label, description, Type.INTEGER, min, max, step, List.of());
    }

    public static ConfigDefinition floating(String key, String label, String description, double min, double max, double step) {
        return new ConfigDefinition(key, label, description, Type.FLOAT, min, max, step, List.of());
    }

    public static ConfigDefinition choice(String key, String label, String description, List<String> choices) {
        return new ConfigDefinition(key, label, description, Type.CHOICE, 0, 0, 0, List.copyOf(choices));
    }

    public static ConfigDefinition pathList(String key, String label, String description) {
        return new ConfigDefinition(key, label, description, Type.PATH_LIST, 0, 0, 0, List.of());
    }

    public static ConfigDefinition keyBinding(String key, String label, String description) {
        return new ConfigDefinition(key, label, description, Type.KEY_BINDING, 0, 0, 0, List.of());
    }

    public static ConfigDefinition shortcut(String key, String label, String description) {
        return new ConfigDefinition(key, label, description, Type.SHORTCUT, 0, 0, 0, List.of());
    }
}
