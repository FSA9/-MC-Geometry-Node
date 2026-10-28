package com.mine.geometry_node.client.integration.graph;

import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.definition.port.PortOptionResolver;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/** Adapts connection-owned catalogs to the shared port option resolver without storing world state. */
public final class GraphPortOptions {
    private GraphPortOptions() {}

    public static PortOptionResolver.Resolution resolve(PortRow row) {
        var client = Minecraft.getInstance();
        return PortOptionResolver.resolve(row,
                client.level == null ? null : client.level.registryAccess(),
                key -> Component.translatable(key).getString(), registry -> {
                    if (!RegistryDataManager.DIMENSION_REGISTRY_ID.equals(registry)) return null;
                    var connection = client.getConnection();
                    // Dimension keys are sent at login; the dimension registry itself is not synced.
                    List<String> dimensions = connection == null ? List.of() : connection.levels().stream()
                            .map(key -> key.identifier().toString()).sorted().toList();
                    return new RegistryDataManager.DynamicOptions(dimensions, Map.of(), connection != null);
                });
    }
}
