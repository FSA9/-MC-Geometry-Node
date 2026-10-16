package com.mine.geometry_node.core.network.packet.scene;

import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

final class SceneWire {
    private SceneWire() {}
    static void write(RegistryFriendlyByteBuf buf, SceneObject object) {
        buf.writeUUID(object.id()); buf.writeUtf(object.typeId(), 64); buf.writeUtf(object.name(), 96);
        vector(buf, object.position()); vector(buf, object.color());
        buf.writeFloat(object.intensity()); buf.writeFloat(object.range()); buf.writeBoolean(object.enabled()); buf.writeLong(object.revision());
    }
    static SceneObject read(RegistryFriendlyByteBuf buf) {
        return new SceneObject(buf.readUUID(), buf.readUtf(64), buf.readUtf(96), vector(buf), vector(buf),
                buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readLong());
    }
    static int count(RegistryFriendlyByteBuf buf, int maximum) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Scene payload exceeds budget");
        return count;
    }
    private static void vector(RegistryFriendlyByteBuf buf, Vec3 value) {
        buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
    }
    private static Vec3 vector(RegistryFriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }
}
