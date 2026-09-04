package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.engine.graph.value.GraphValueCodecRegistry;

import java.util.Locale;
import java.util.Set;

/** The single source of truth for values that may be stored in a Data Library. */
public final class DataLibraryTypes {
    private DataLibraryTypes() {
    }

    public static Set<PortType> supported() {
        return GraphValueCodecRegistry.supportedPortTypes();
    }

    public static boolean supports(PortType type) {
        return GraphValueCodecRegistry.supportsPortType(type);
    }

    public static String[] optionIds() {
        return java.util.Arrays.stream(PortType.values())
                .filter(DataLibraryTypes::supports)
                .map(Enum::name)
                .toArray(String[]::new);
    }

    public static PortType resolve(Object value) {
        if (!(value instanceof String text) || text.isBlank()) return null;
        try {
            PortType type = PortType.valueOf(text.trim().toUpperCase(Locale.ROOT));
            return supports(type) ? type : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
