package com.mine.geometry_node.core.engine.graph.resource;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldAddress;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Node-facing resource references. A public address is deliberately separate from graph ownership:
 * areas and force fields are dimension-wide, markers may be persistent and server-wide.
 */
public final class GraphResourceReferences {
    private static final String VERSION = "rf1";

    private GraphResourceReferences() {
    }

    public sealed interface Reference permits GraphReference, PublicReference {
        String encoded();
    }

    public record GraphReference(GraphResourceId id) implements Reference {
        public GraphReference {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public String encoded() {
            return GraphResourceIdCodec.encode(id);
        }
    }

    public enum PublicKind { AREA, FORCE_FIELD, MARKER, SCHEMATIC_PLACEMENT, MODEL_COLLISION, SCENE_OBJECT }

    public record PublicReference(PublicKind kind, ResourceKey<Level> dimension, UUID viewerId, String key)
            implements Reference {
        public PublicReference {
            Objects.requireNonNull(kind, "kind");
            key = key == null ? "" : key.trim();
            int limit = kind == PublicKind.MARKER ? MarkerAddress.MAX_KEY_LENGTH : 512;
            if (key.isEmpty() || key.length() > limit) throw new IllegalArgumentException("Invalid resource key");
            if (kind == PublicKind.MARKER) {
                if (dimension != null) throw new IllegalArgumentException("Marker address has no dimension");
            } else {
                Objects.requireNonNull(dimension, "dimension");
                if (viewerId != null) throw new IllegalArgumentException("Resource address has no viewer");
            }
        }

        @Override
        public String encoded() {
            return String.join(".", VERSION, kind.name(),
                    dimension == null ? "-" : part(dimension.identifier().toString()),
                    viewerId == null ? "-" : viewerId.toString(), part(key));
        }
    }

    public record PlacementAddress(ResourceKey<Level> dimension, String key) {
        public PlacementAddress {
            PublicReference validated = new PublicReference(PublicKind.SCHEMATIC_PLACEMENT, dimension, null, key);
            key = validated.key();
        }
    }

    /** Preserve String and UUID collision identities even when their text is identical. */
    public record ModelCollisionAddress(ResourceKey<Level> dimension, Object instanceId) {
        public ModelCollisionAddress {
            Objects.requireNonNull(dimension, "dimension");
            if (!(instanceId instanceof String) && !(instanceId instanceof UUID))
                throw new IllegalArgumentException("Model collision identity must be a String or UUID");
            new PublicReference(PublicKind.MODEL_COLLISION, dimension, null,
                    collisionKey(instanceId));
        }
    }

    public record SceneObjectAddress(ResourceKey<Level> dimension, UUID objectId) {
        public SceneObjectAddress {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(objectId, "objectId");
        }
    }

    public static Object modelCollisionIdentity(PublicReference reference) {
        if (reference.kind() != PublicKind.MODEL_COLLISION)
            throw new IllegalArgumentException("Resource is not a model collision");
        if (reference.key().startsWith("string:")) return value(reference.key().substring(7));
        if (reference.key().startsWith("uuid:")) return UUID.fromString(reference.key().substring(5));
        throw new IllegalArgumentException("Invalid model collision identity kind");
    }

    /** Reserved prefixes are never treated as ordinary names, including unknown future versions. */
    public static boolean isEncoded(String input) {
        if (input == null) return false;
        String value = input.trim();
        return reservedPrefix(value, "gr") || reservedPrefix(value, "rf");
    }

    private static boolean reservedPrefix(String value, String prefix) {
        if (!value.startsWith(prefix)) return false;
        int dot = value.indexOf('.', prefix.length());
        if (dot <= prefix.length()) return false;
        for (int i = prefix.length(); i < dot; i++) {
            char character = value.charAt(i);
            if (character < '0' || character > '9') return false;
        }
        return true;
    }

    public static Reference parse(String input) {
        String value = input == null ? "" : input.trim();
        if (value.startsWith("gr1.")) return new GraphReference(GraphResourceIdCodec.decode(value));
        String[] parts = value.split("\\.", -1);
        if (parts.length != 5 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Invalid resource reference");
        }
        ResourceKey<Level> dimension = "-".equals(parts[2]) ? null
                : ResourceKey.create(Registries.DIMENSION, Identifier.parse(value(parts[2])));
        UUID viewer = "-".equals(parts[3]) ? null : UUID.fromString(parts[3]);
        return new PublicReference(PublicKind.valueOf(parts[1]), dimension, viewer, value(parts[4]));
    }

    public static GraphResourceId resolveCreate(GraphDataContext context, String stableNodeId,
                                                 GraphResourceType type, String input) {
        return GraphResourceIds.resolveCreate(context, stableNodeId, type, input);
    }

    public static GraphResourceId resolveExisting(GraphDataContext context, GraphResourceType type, String input) {
        return GraphResourceIds.resolveExisting(context, type, input);
    }

    public static AreaAddress resolveArea(GraphDataContext context, String stableNodeId, String input,
                                          ResourceKey<Level> dimension, boolean create) {
        PublicReference reference = resolvePublic(context, stableNodeId, input, PublicKind.AREA, dimension, null, create);
        return new AreaAddress(reference.dimension(), reference.key());
    }

    public static ForceFieldAddress resolveForceField(GraphDataContext context, String stableNodeId, String input,
                                                       ResourceKey<Level> dimension, boolean create) {
        PublicReference reference = resolvePublic(context, stableNodeId, input, PublicKind.FORCE_FIELD, dimension, null, create);
        return new ForceFieldAddress(reference.dimension(), reference.key());
    }

    public static MarkerAddress resolveMarker(GraphDataContext context, String stableNodeId, String input,
                                               UUID viewerId, boolean create) {
        PublicReference reference = resolvePublic(context, stableNodeId, input, PublicKind.MARKER, null, viewerId, create);
        return reference.viewerId() == null ? MarkerAddress.all(reference.key())
                : MarkerAddress.self(reference.viewerId(), reference.key());
    }

    public static PlacementAddress resolvePlacement(GraphDataContext context, String stableNodeId, String input,
                                                     ResourceKey<Level> dimension, boolean create) {
        PublicReference reference = resolvePublic(context, stableNodeId, input, PublicKind.SCHEMATIC_PLACEMENT,
                dimension, null, create);
        return new PlacementAddress(reference.dimension(), reference.key());
    }

    public static String encode(AreaAddress address) {
        return new PublicReference(PublicKind.AREA, address.dimension(), null, address.id()).encoded();
    }

    public static String encode(ForceFieldAddress address) {
        return new PublicReference(PublicKind.FORCE_FIELD, address.dimension(), null, address.id()).encoded();
    }

    public static String encode(MarkerAddress address) {
        return new PublicReference(PublicKind.MARKER, null, address.viewerId(), address.key()).encoded();
    }

    public static String encode(PlacementAddress address) {
        return new PublicReference(PublicKind.SCHEMATIC_PLACEMENT, address.dimension(), null, address.key()).encoded();
    }

    public static String encode(ModelCollisionAddress address) {
        return new PublicReference(PublicKind.MODEL_COLLISION, address.dimension(), null,
                collisionKey(address.instanceId())).encoded();
    }

    private static String collisionKey(Object identity) {
        return identity instanceof UUID ? "uuid:" + identity : "string:" + part((String) identity);
    }

    public static String encode(SceneObjectAddress address) {
        return new PublicReference(PublicKind.SCENE_OBJECT, address.dimension(), null, address.objectId().toString()).encoded();
    }

    private static PublicReference resolvePublic(GraphDataContext context, String stableNodeId, String input,
                                                  PublicKind kind, ResourceKey<Level> dimension, UUID viewer,
                                                  boolean create) {
        String key = input == null ? "" : input.trim();
        if (isEncoded(key)) {
            Reference decoded = parse(key);
            if (!(decoded instanceof PublicReference reference) || reference.kind() != kind) {
                throw new IllegalArgumentException("Resource reference does not refer to " + kind);
            }
            return reference;
        }
        if (key.isEmpty()) {
            if (!create) throw new IllegalArgumentException("Resource id cannot be empty");
            key = GraphResourceIds.automaticKey();
        }
        return new PublicReference(kind, dimension, viewer, key);
    }

    private static String part(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
