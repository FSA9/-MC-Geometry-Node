package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.graph.light.LightDefinition;
import com.mine.geometry_node.core.node.value.light.LightConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Small configuration-only payload; images are asset references, never embedded pixels. */
public record PacketGraphLight(Identifier dimension, String resourceId, int operation, int entityId,
                               LightDefinition definition) implements CustomPacketPayload {
    public static final int UPSERT = 0, REMOVE = 1, CLEAR = 2;
    public static final Type<PacketGraphLight> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "graph_light"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketGraphLight> STREAM_CODEC =
            StreamCodec.of(PacketGraphLight::write, PacketGraphLight::read);

    private static void write(RegistryFriendlyByteBuf b, PacketGraphLight p) {
        b.writeIdentifier(p.dimension); b.writeUtf(p.resourceId, 2048); b.writeByte(p.operation);
        if (p.operation != UPSERT) return;
        b.writeInt(p.entityId);
        LightDefinition d = p.definition;
        b.writeUtf(d.type(), 16); vec(b, d.color());
        b.writeFloat(d.intensity()); b.writeFloat(d.range()); b.writeFloat(d.coneAngle()); b.writeFloat(d.edgeSoftness());
        vec(b, d.position()); vec(b, d.rotation());
        var t = d.config().transform();
        b.writeBoolean(t.entityId() != null); if (t.entityId() != null) b.writeUUID(t.entityId());
        vec(b, t.position()); vec(b, t.rotation()); b.writeBoolean(t.followView()); b.writeBoolean(t.eyeAnchor());
        var s = d.config().shadow(); b.writeBoolean(s.enabled()); b.writeFloat(s.sourceSize());
        var v = d.config().volume();
        b.writeUtf(v.mode(), 16); b.writeFloat(v.strength()); b.writeFloat(v.density());
        b.writeFloat(v.noiseStrength()); b.writeFloat(v.noiseScale()); vec(b, v.velocity()); b.writeInt(v.seed());
        var ptn = d.config().projection();
        b.writeUtf(ptn.path(), 1024); b.writeBoolean(ptn.colored()); b.writeFloat(ptn.rotation()); b.writeFloat(ptn.scale());
        var q = d.config().quality();
        b.writeFloat(q.shadowResolution()); b.writeVarInt(q.shadowSamples()); b.writeVarInt(q.volumeSamples()); b.writeFloat(q.volumeResolution());
        b.writeVarInt(q.volumeEmitterSamples());
        var l = d.config().lifecycle(); b.writeVarInt(l.durationTicks()); b.writeBoolean(l.enabled());
        b.writeBoolean(d.config().sourceVisible());
        if ("area".equals(d.type())) {
            b.writeFloat(d.config().area().width()); b.writeFloat(d.config().area().height());
        }
    }

    private static PacketGraphLight read(RegistryFriendlyByteBuf b) {
        Identifier dimension = b.readIdentifier(); String id = b.readUtf(2048); int op = b.readUnsignedByte();
        if (op < UPSERT || op > CLEAR) throw new IllegalArgumentException("Invalid light operation");
        if (op != UPSERT) return new PacketGraphLight(dimension, id, op, -1, null);
        int entity = b.readInt(); String type = b.readUtf(16); Vec3 color = vec(b);
        float intensity = b.readFloat(), range = b.readFloat(), angle = b.readFloat(), edge = b.readFloat();
        Vec3 position = vec(b), rotation = vec(b);
        var transform = new LightConfig.Transform(b.readBoolean() ? b.readUUID() : null, vec(b), vec(b), b.readBoolean(), b.readBoolean());
        var shadow = new LightConfig.Shadow(b.readBoolean(), b.readFloat());
        var volume = new LightConfig.Volume(b.readUtf(16), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), vec(b), b.readInt());
        var projection = new LightConfig.Projection(b.readUtf(1024), b.readBoolean(), b.readFloat(), b.readFloat());
        var quality = new LightConfig.Quality(b.readFloat(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readVarInt());
        var lifecycle = new LightConfig.Lifecycle(b.readVarInt(), b.readBoolean());
        boolean visible = b.readBoolean();
        var area = "area".equals(type) ? new LightConfig.Area(b.readFloat(), b.readFloat()) : LightConfig.Area.DEFAULT;
        return new PacketGraphLight(dimension, id, op, entity, new LightDefinition(type, color, intensity, range, angle, edge, position, rotation,
                new LightConfig(transform, shadow, volume, projection, quality, lifecycle, visible, area)));
    }
    private static void vec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf b) {
        Vec3 value = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        if (!LightDefinition.finite(value)) throw new IllegalArgumentException("Nonfinite light vector");
        return value;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
