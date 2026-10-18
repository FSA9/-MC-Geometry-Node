package com.mine.geometry_node.core.engine.system.worldquery;

/** Empty labelKey instructs consumers to display the registered ID verbatim. */
public record WorldObjectType(String provider, String id, String labelKey) {
    public WorldObjectType {
        WorldObjectEntry.validateText(provider); WorldObjectEntry.validateText(id); WorldObjectEntry.validateText(labelKey);
        if (provider.length() > 128 || id.length() > 256 || labelKey.length() > 256)
            throw new IllegalArgumentException("World object type metadata too long");
    }
}
