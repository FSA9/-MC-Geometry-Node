package com.mine.geometry_node.core.engine.system.scene;

import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** Shared local TRS convention: scale, XYZ Euler rotation (radians), then translation. */
public final class SceneTransforms {
    private SceneTransforms() {}
    private static final Map<String, BiFunction<SceneObject, Vec3, Vec3>> LOCAL = new ConcurrentHashMap<>();
    public static void registerLocal(String typeId, BiFunction<SceneObject, Vec3, Vec3> transform) {
        if (LOCAL.putIfAbsent(typeId, Objects.requireNonNull(transform)) != null)
            throw new IllegalArgumentException("Duplicate scene transform: " + typeId);
    }
    public static Quaterniond rotation(Vec3 angles){return new Quaterniond().rotationXYZ(angles.x,angles.y,angles.z);}
    public static Vec3 rotate(Quaterniond q,Vec3 point){var v=q.transform(new Vector3d(point.x,point.y,point.z));return new Vec3(v.x,v.y,v.z);}
    public static Vec3 local(SceneObject o,Vec3 point){
        var adapter = LOCAL.get(o.typeId());
        if (adapter != null) return adapter.apply(o, point);
        return rotate(rotation(o.rotation()),point.multiply(o.scale()));
    }
    /** YXZ degrees with positive Y, as used by analytic areas. */
    public static Vec3 yxzDegrees(Vec3 sceneRadians) {
        Vector3d angles = rotation(sceneRadians).getEulerAnglesYXZ(new Vector3d());
        return new Vec3(Math.toDegrees(angles.x), Math.toDegrees(angles.y), Math.toDegrees(angles.z));
    }
    public static Vec3 xyzRadians(Vec3 yxzDegrees) {
        Vector3d angles = new Quaterniond().rotationYXZ(Math.toRadians(yxzDegrees.y), Math.toRadians(yxzDegrees.x),
                Math.toRadians(yxzDegrees.z)).getEulerAnglesXYZ(new Vector3d());
        return new Vec3(angles.x, angles.y, angles.z);
    }
    public static AABB bounds(SceneObject o,AABB untransformed){
        AABB result=null;
        for(int i=0;i<8;i++){
            Vec3 p=new Vec3((i&1)==0?untransformed.minX:untransformed.maxX,(i&2)==0?untransformed.minY:untransformed.maxY,(i&4)==0?untransformed.minZ:untransformed.maxZ);
            p=local(o,p.subtract(o.position())).add(o.position());
            AABB b=new AABB(p,p);result=result==null?b:result.minmax(b);
        }return result;
    }
}
