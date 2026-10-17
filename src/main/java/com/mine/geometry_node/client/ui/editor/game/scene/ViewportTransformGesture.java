package com.mine.geometry_node.client.ui.editor.game.scene;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera;
import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportLayout;
import com.mine.geometry_node.core.engine.system.scene.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** One modal transform = one server transaction. All movement uses the frozen presented camera. */
final class ViewportTransformGesture {
    enum Operation { MOVE, ROTATE, SCALE }
    private final ClientSceneService service;
    private final PresentedViewportFrame frame;
    private final GameViewportLayout bounds;
    private final Map<UUID,SceneObject> originals;
    private final Vec3 center;
    private final float startX,startY;
    private float x,y;
    private int axes;
    private final ViewportMoveSnap snapping;
    private final ViewportTransformNumber number=new ViewportTransformNumber();
    private boolean valid=true;
    void refresh(){move(x,y);}
    private final Operation operation;
    ViewportTransformGesture(ClientSceneService service,PresentedViewportFrame frame,GameViewportLayout bounds,
                             Map<UUID,SceneObject> originals,Operation operation,float x,float y,ViewportMoveSnap snapping){
        this.service=service;this.frame=frame;this.bounds=bounds;this.originals=Map.copyOf(originals);this.operation=operation;
        this.startX=this.x=x;this.startY=this.y=y;
        this.snapping=snapping;
        Vec3 sum=Vec3.ZERO;for(var o:originals.values())sum=sum.add(o.position());center=sum.scale(1.0/originals.size());
    }
    boolean begin(){
        Map<UUID,Long> revisions=new LinkedHashMap<>();Map<UUID,Vec3> positions=new LinkedHashMap<>();
        for(var o:originals.values()){revisions.put(o.id(),o.revision());positions.put(o.id(),o.position());}
        // A throttled frame may still show an older cancelled preview with the same revision.
        for(var o:service.snapshot().objects())if(originals.containsKey(o.id())&&!originals.get(o.id()).sameContent(o))return false;
        return service.beginPreview(this,frame.scene().worldGeneration(),revisions,positions);
    }
    int axes(){return axes;}
    String label(){return net.minecraft.network.chat.Component.translatable("geometry_node.game_view.scene."+operation.name().toLowerCase(java.util.Locale.ROOT)).getString()+(axes==0?"":" · "+((axes&1)!=0?"X":"")+((axes&2)!=0?"Y":"")+((axes&4)!=0?"Z":""))+(number.active()?" · "+number.text()+(operation==Operation.ROTATE?"°":operation==Operation.SCALE?"×":" m"):"")+(valid?"":" · "+net.minecraft.network.chat.Component.translatable("geometry_node.game_view.scene.invalid_transform").getString());}
    boolean active(){return service.previewActive(this);}
    void cancel(){service.cancelPreview(this);}
    boolean confirm(){if(!valid)return false;service.commitPreview(this);return true;}
    boolean numberKey(int key){if(!number.key(key))return false;move(x,y);return true;}
    void axis(int bit){axes^=bit;if(axes==7)axes=0;move(x,y);}
    private double u(float px){return (px-bounds.left())/(bounds.right()-bounds.left());}
    private double v(float py){return (py-bounds.top())/(bounds.bottom()-bounds.top());}
    private Vec3 plane(float px,float py,Vec3 normal){
        var ray=ViewportSceneMath.ray(frame,u(px),v(py));if(ray==null)return null;
        double denominator=ray.direction().dot(normal);if(Math.abs(denominator)<1e-6)return null;
        return ray.origin().add(ray.direction().scale(center.subtract(ray.origin()).dot(normal)/denominator));
    }
    private double rotationAngle(float x,float y,Vec3 normal){
        Vec3 a=plane(startX,startY,normal), b=plane(x,y,normal);
        var pivot=ViewportSceneMath.project(frame,center);
        double px=bounds.left()+pivot.u()*(bounds.right()-bounds.left());
        double py=bounds.top()+pivot.v()*(bounds.bottom()-bounds.top());
        double deadZone=com.mine.geometry_node.client.ui.utils.UIUtils.dp2px(12);
        if(a!=null&&b!=null&&Math.hypot(startX-px,startY-py)>deadZone&&Math.hypot(x-px,y-py)>deadZone){
            a=a.subtract(center);b=b.subtract(center);
            if(a.lengthSqr()>1e-12&&b.lengthSqr()>1e-12)
                return Math.atan2(normal.dot(a.cross(b)),a.dot(b));
        }
        // At the pivot (or edge-on to the rotation plane), the angular handle is undefined.
        return -(x-startX)/(bounds.right()-bounds.left())*Math.PI*2;
    }
    void move(float x,float y){
        if(!active()||!Float.isFinite(x)||!Float.isFinite(y))return;this.x=x;this.y=y;
        var ray=ViewportSceneMath.ray(frame,.5,.5);if(ray==null)return;
        Vec3 normal=ray.direction(),delta=Vec3.ZERO;
        Double numeric=number.active()?number.value():null;
        if(number.active()&&numeric==null){valid=false;return;}
        int count=Integer.bitCount(axes);
        if(operation==Operation.MOVE && numeric==null){
            if(count==1){
                var axis=axes==1?EditViewportCamera.Axis.POSITIVE_X:axes==2?EditViewportCamera.Axis.POSITIVE_Y:EditViewportCamera.Axis.POSITIVE_Z;
                double amount=ViewportSceneMath.axisDrag(frame,center,axis,u(startX),v(startY),u(x),v(y));
                delta=new Vec3(axis.x*amount,axis.y*amount,axis.z*amount);
            }else{
                if(count==2)normal=axes==3?new Vec3(0,0,1):axes==5?new Vec3(0,1,0):new Vec3(1,0,0);
                Vec3 a=plane(startX,startY,normal),b=plane(x,y,normal);if(a==null||b==null)return;delta=b.subtract(a);
            }
        }
        if(operation==Operation.MOVE && numeric!=null){
            if(axes!=0)delta=new Vec3((axes&1)!=0?numeric:0,(axes&2)!=0?numeric:0,(axes&4)!=0?numeric:0);
            else {
                Vec3 a=plane(startX,startY,normal),b=plane(x,y,normal);
                Vec3 direction=a!=null&&b!=null?b.subtract(a):Vec3.ZERO;
                if(direction.lengthSqr()<1e-12){
                    a=plane((bounds.left()+bounds.right())/2,(bounds.top()+bounds.bottom())/2,normal);
                    b=plane((bounds.left()+bounds.right())/2+32,(bounds.top()+bounds.bottom())/2,normal);
                    direction=a!=null&&b!=null?b.subtract(a):new Vec3(1,0,0);
                }
                delta=direction.normalize().scale(numeric);
            }
        }
        if(operation==Operation.MOVE && numeric==null)delta=snapping.apply(center,delta,axes,x,y);
        else snapping.suspend();
        if(operation==Operation.ROTATE&&axes!=0){
            int bit=count==2?7^axes:axes;normal=new Vec3((bit&1)!=0?1:0,(bit&2)!=0?1:0,(bit&4)!=0?1:0);
        }
        double angle=operation==Operation.ROTATE?(numeric!=null?Math.toRadians(numeric):rotationAngle(x,y,normal)):0;
        Quaterniond turn=new Quaterniond().rotationAxis(angle,normal.x,normal.y,normal.z);
        double factor=operation==Operation.SCALE&&numeric!=null?numeric:Math.exp(Math.clamp((x-startX)/(bounds.right()-bounds.left())*3,-6,6));
        Vec3 factors=new Vec3(axes==0||(axes&1)!=0?factor:1,axes==0||(axes&2)!=0?factor:1,axes==0||(axes&4)!=0?factor:1);
        Map<UUID,SceneObject> next=new LinkedHashMap<>();
        try{
            for(var o:originals.values()){
                Vec3 position=o.position(),rotation=o.rotation(),scale=o.scale();
                switch(operation){
                    case MOVE -> position=position.add(delta);
                    case ROTATE -> {position=center.add(SceneTransforms.rotate(turn,position.subtract(center)));var e= new Quaterniond(turn).mul(SceneTransforms.rotation(rotation)).getEulerAnglesXYZ(new Vector3d());rotation=new Vec3(e.x,e.y,e.z);}
                    case SCALE -> {position=center.add(position.subtract(center).multiply(factors));scale=scale.multiply(factors);}
                }
                next.put(o.id(),o.withTransform(position,rotation,scale));
            }
            service.previewObjects(this,next);valid=true;
        }catch(IllegalArgumentException invalid){valid=false; /* Keep the last valid preview; never confirm stale input. */ }
    }
}
