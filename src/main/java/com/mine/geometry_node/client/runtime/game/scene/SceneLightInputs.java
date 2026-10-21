package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.light.store.ClientLightStore;
import com.mine.geometry_node.core.engine.graph.light.LightDefinition;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.node.value.light.LightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.mine.geometry_node.core.engine.resource.residency.ResourceResidency;
import java.util.function.Predicate;
import java.util.List;
import java.util.ArrayList;

/** Adapter only: authored server objects never enter the graph-resource mirror/lifecycle. */
public final class SceneLightInputs {
    private SceneLightInputs() {}
    public static List<ClientLightStore.ResolvedLight> resolved() {
        var level=Minecraft.getInstance().level;
        if(level==null) return List.of();
        var snapshot=EditViewportRuntime.renderedScene();
        if(snapshot==null) snapshot=ClientSceneService.INSTANCE.confirmedSnapshot();
        if(!level.dimension().identifier().toString().equals(snapshot.dimension())) return List.of();
        return resolve(snapshot.objects(), bounds -> ResourceResidency.isResident(level, bounds, null));
    }
    public static List<ClientLightStore.ResolvedLight> resolve(List<SceneObject> objects, Predicate<AABB> residency) {
        List<ClientLightStore.ResolvedLight> result=new ArrayList<>();
        for(var object:objects) {
            if(!SceneObject.POINT_LIGHT.equals(object.typeId()) || !object.enabled() || object.intensity()<=0) continue;
            double diameter = object.range() * 2.0;
            if (!residency.test(AABB.ofSize(object.position(), diameter, diameter, diameter))) continue;
            var config=LightConfig.DEFAULT.withShadow(new LightConfig.Shadow(LightConfig.DEFAULT.shadow().enabled(),
                    (float)object.number("source_size"))).withSourceVisible(Boolean.parseBoolean(object.parameters().get("source_visible")));
            var definition=new LightDefinition("point",object.color(),object.intensity(),object.range(),60,0.25f,
                    object.position(),Vec3.ZERO,config);
            result.add(new ClientLightStore.ResolvedLight("scene:"+object.id(),definition,object.position(),
                    new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),null));
        }
        return List.copyOf(result);
    }
}
