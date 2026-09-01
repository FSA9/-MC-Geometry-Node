package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.node.definition.port.PortType;

import java.util.EnumSet;
import java.util.Set;

/** The single source of truth for values that may be stored in a Data Library. */
public final class DataLibraryTypes {
    private static final Set<PortType> SUPPORTED = Set.copyOf(EnumSet.of(
            PortType.INTEGER,
            PortType.LONG,
            PortType.FLOAT,
            PortType.BOOLEAN,
            PortType.STRING,
            PortType.PATH,
            PortType.RICH_TEXT,
            PortType.ENTITY,
            PortType.ENTITY_TEMPLATE,
            PortType.ITEM,
            PortType.ITEM_STACK,
            PortType.SLOT,
            PortType.BLOCK,
            PortType.GEOMETRY,
            PortType.XYZ,
            PortType.COLOR,
            PortType.LIST,
            PortType.DICT,
            PortType.SHOP));

    private DataLibraryTypes() {
    }

    public static Set<PortType> supported() {
        return SUPPORTED;
    }

    public static boolean supports(PortType type) {
        return type != null && SUPPORTED.contains(type);
    }
}
