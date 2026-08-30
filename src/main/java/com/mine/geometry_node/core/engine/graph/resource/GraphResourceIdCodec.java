package com.mine.geometry_node.core.engine.graph.resource;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Versioned, lossless string codec used only at network/debug boundaries. */
public final class GraphResourceIdCodec {
    private static final String VERSION = "gr1";

    private GraphResourceIdCodec() {
    }

    public static String encode(GraphResourceId id) {
        String scopeKind;
        String owner;
        if (id.scope() instanceof GraphResourceScope.EntityScope entity) {
            scopeKind = "e";
            owner = entity.ownerId().toString();
        } else {
            scopeKind = "l";
            owner = "-";
        }
        String selectorKind;
        String selectorValue;
        if (id.selector() instanceof GraphResourceSelector.Node node) {
            selectorKind = "n";
            selectorValue = node.stableNodeId();
        } else if (id.selector() instanceof GraphResourceSelector.Named named) {
            selectorKind = "k";
            selectorValue = named.key();
        } else {
            selectorKind = "g";
            selectorValue = "";
        }
        return String.join(".", VERSION, part(id.type().id().toString()), scopeKind,
                part(id.scope().dimension().identifier().toString()), owner, id.binding().kind().id(),
                part(id.binding().graphId()), selectorKind, part(selectorValue),
                nullableUuid(id.targetEntityId()), nullableUuid(id.processInstanceId()));
    }

    public static GraphResourceId decode(String encoded) {
        String[] parts = encoded == null ? new String[0] : encoded.split("\\.", -1);
        if (parts.length != 11 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Invalid graph resource id");
        }
        GraphResourceType type = GraphResourceTypeRegistry.INSTANCE.require(Identifier.parse(value(parts[1])));
        ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse(value(parts[3])));
        GraphResourceScope scope = switch (parts[2]) {
            case "l" -> new GraphResourceScope.LevelScope(dimension);
            case "e" -> new GraphResourceScope.EntityScope(dimension, UUID.fromString(parts[4]));
            default -> throw new IllegalArgumentException("Invalid graph resource scope");
        };
        GraphBindingKey binding = new GraphBindingKey(GraphKind.fromId(parts[5]), value(parts[6]));
        GraphResourceSelector selector = switch (parts[7]) {
            case "g" -> GraphResourceSelector.Graph.INSTANCE;
            case "n" -> new GraphResourceSelector.Node(value(parts[8]));
            case "k" -> new GraphResourceSelector.Named(value(parts[8]));
            default -> throw new IllegalArgumentException("Invalid graph resource selector");
        };
        return new GraphResourceId(type, scope, binding, selector, uuid(parts[9]), uuid(parts[10]));
    }

    private static String part(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String nullableUuid(UUID value) {
        return value != null ? value.toString() : "-";
    }

    private static UUID uuid(String value) {
        return "-".equals(value) ? null : UUID.fromString(value);
    }
}
