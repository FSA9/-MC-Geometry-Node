package com.mine.geometry_node.client.ui.editor.game.selection;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportLayout;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;

/** Navigation destination outline only. Selection highlights render in the world pass. */
public final class ViewportSelectionOverlay {
    private final Paint paint = new Paint();
    public ViewportSelectionOverlay() { paint.setAntiAlias(true); }
    public void draw(Canvas canvas, PresentedViewportFrame frame, GameViewportLayout layout,
                     BlockPos destination) {
        if (destination != null) box(canvas, frame, layout, new AABB(destination).inflate(.005));
    }
    private void box(Canvas canvas,PresentedViewportFrame frame,GameViewportLayout layout,AABB b) {
        paint.setStyle(Paint.STROKE);paint.setColor(UiThemes.EDITOR.selection().outline());
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)line(canvas,frame,layout,corner(b,i),corner(b,i|bit));
    }
    static float projectedWidth(PresentedViewportFrame frame,GameViewportLayout layout,Vec3 center,double worldWidth) {
        double yaw=Math.toRadians(frame.camera().yaw());
        var a=ViewportSceneMath.project(frame,center);
        var b=ViewportSceneMath.project(frame,center.add(Math.cos(yaw)*worldWidth,0,Math.sin(yaw)*worldWidth));
        if(!inDepth(frame,a)||!inDepth(frame,b))return 0;
        return (float)Math.hypot((a.u()-b.u())*(layout.right()-layout.left()),(a.v()-b.v())*(layout.bottom()-layout.top()));
    }
    private static boolean inDepth(PresentedViewportFrame frame,ViewportSceneMath.Projected p) {
        return Double.isFinite(p.u())&&Double.isFinite(p.v())&&p.depth()>=(frame.zeroToOne()?0:-1)&&p.depth()<=1;
    }
    private static Vec3 corner(AABB b,int i) { return new Vec3((i&1)==0?b.minX:b.maxX,(i&2)==0?b.minY:b.maxY,(i&4)==0?b.minZ:b.maxZ); }
    private static float px(GameViewportLayout b,double u){return b.left()+(float)u*(b.right()-b.left());}
    private static float py(GameViewportLayout b,double v){return b.top()+(float)v*(b.bottom()-b.top());}
    private void line(Canvas canvas,PresentedViewportFrame frame,GameViewportLayout b,Vec3 from,Vec3 to) {
        var segment=ViewportSceneMath.projectSegment(frame,from,to);
        if(segment==null)return;
        // Measure at the visible piece, not the box center (which may be behind the near plane).
        var a=segment.from();var z=segment.to();
        var sample=new org.joml.Vector4d(a.u()+z.u()-1,1-a.v()-z.v(),(a.depth()+z.depth())*.5,1);
        frame.inverseViewProjection(new org.joml.Matrix4d()).transform(sample);
        if(!Double.isFinite(sample.w)||Math.abs(sample.w)<1e-9)return;
        sample.div(sample.w);var camera=frame.camera();
        float width=projectedWidth(frame,b,new Vec3(sample.x+camera.x(),sample.y+camera.y(),sample.z+camera.z()),.02);
        if(width<=0)return;
        // No dp/pixel floor: distant outlines shrink with the world as well.
        paint.setStrokeWidth(width);
        canvas.drawLine(px(b,a.u()),py(b,a.v()),px(b,z.u()),py(b,z.v()),paint);
    }
}
