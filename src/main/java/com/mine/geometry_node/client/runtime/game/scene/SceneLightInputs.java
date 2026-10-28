package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.client.feature.scene.ClientSceneService;
import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.light.store.ClientLightStore;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.light.SceneLightTypes;
import com.mine.geometry_node.core.engine.system.scene.geometry.SceneTransforms;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.mine.geometry_node.core.engine.residency.ResourceResidency;
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
            if(!SceneLightTypes.isLight(object.typeId()) || !object.enabled() || object.intensity()<=0) continue;
            var definition = SceneLightTypes.definition(object);
            if (!residency.test(SceneLightTypes.influenceBounds(definition))) continue;
            var rotation = SceneTransforms.rotation(object.rotation());
            result.add(new ClientLightStore.ResolvedLight("scene:"+object.id(),definition,object.position(),
                    SceneTransforms.rotate(rotation, new Vec3(0,0,1)),
                    SceneTransforms.rotate(rotation, new Vec3(1,0,0)),
                    SceneTransforms.rotate(rotation, new Vec3(0,1,0)),null));
        }
        return List.copyOf(result);
    }
}
