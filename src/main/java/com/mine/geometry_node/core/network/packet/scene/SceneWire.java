package com.mine.geometry_node.core.network.packet.scene;

import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

final class SceneWire {
    private SceneWire() {}
    static void write(RegistryFriendlyByteBuf buf, SceneObject object) {
        buf.writeUUID(object.id()); buf.writeUtf(object.typeId(), 64); buf.writeUtf(object.name(), 96);
        vector(buf, object.position()); vector(buf, object.color()); vector(buf,object.rotation()); vector(buf,object.scale());
        buf.writeBoolean(object.enabled()); buf.writeLong(object.revision());
        buf.writeVarInt(object.parameters().size());
        object.parameters().forEach((key,value)->{buf.writeUtf(key,64);buf.writeUtf(value,256);});
    }
    static SceneObject read(RegistryFriendlyByteBuf buf) {
        var id=buf.readUUID();var type=buf.readUtf(64);var name=buf.readUtf(96);
        var position=vector(buf);var color=vector(buf);var rotation=vector(buf);var scale=vector(buf);
        return new SceneObject(id,type,name,position,color,buf.readBoolean(),buf.readLong(),parameters(buf),rotation,scale);
    }
    private static java.util.Map<String,String> parameters(RegistryFriendlyByteBuf buf){
        int count=count(buf,32);var result=new java.util.LinkedHashMap<String,String>();
        for(int i=0;i<count;i++)if(result.putIfAbsent(buf.readUtf(64),buf.readUtf(256))!=null)throw new IllegalArgumentException("Duplicate parameter");
        return result;
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
