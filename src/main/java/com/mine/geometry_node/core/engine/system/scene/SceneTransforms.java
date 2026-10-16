package com.mine.geometry_node.core.engine.system.scene;

import net.minecraft.world.phys.*;
import org.joml.*;

/** Shared local TRS convention: scale, XYZ Euler rotation (radians), then translation. */
public final class SceneTransforms {
    private SceneTransforms() {}
    public static Quaterniond rotation(Vec3 angles){return new Quaterniond().rotationXYZ(angles.x,angles.y,angles.z);}
    public static Vec3 rotate(Quaterniond q,Vec3 point){var v=q.transform(new Vector3d(point.x,point.y,point.z));return new Vec3(v.x,v.y,v.z);}
    public static Vec3 local(SceneObject o,Vec3 point){return rotate(rotation(o.rotation()),point.multiply(o.scale()));}
    public static AABB bounds(SceneObject o,AABB untransformed){
        AABB result=null;
        for(int i=0;i<8;i++){
            Vec3 p=new Vec3((i&1)==0?untransformed.minX:untransformed.maxX,(i&2)==0?untransformed.minY:untransformed.maxY,(i&4)==0?untransformed.minZ:untransformed.maxZ);
            p=local(o,p.subtract(o.position())).add(o.position());
            AABB b=new AABB(p,p);result=result==null?b:result.minmax(b);
        }return result;
    }
}
