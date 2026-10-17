package com.mine.geometry_node.client.ui.editor.game.scene;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.client.scene.SceneEditBackends;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.BiConsumer;

/** Default creation entries are derived from type plugins; client factories may override them. */
public final class SceneCreateRegistry {
    public record Entry(String typeId,String labelKey,String category,
                        com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization.Layer layer,
                        BiConsumer<Long,Vec3> create) {}
    private static final Map<String,Entry> ENTRIES=new LinkedHashMap<>();
    private SceneCreateRegistry(){}
    public static synchronized void register(Entry entry){if(ENTRIES.putIfAbsent(entry.typeId(),entry)!=null)throw new IllegalArgumentException("Duplicate creation entry");}
    public static synchronized List<Entry> entries(){
        Map<String,Entry> result = new LinkedHashMap<>();
        for (var type : SceneObjectTypes.all()) {
            if (!type.creatable()) continue;
            if (!SceneEditBackends.supports(type.create(new UUID(0,0), Vec3.ZERO), SceneEditBackends.Mutation.CREATE)) continue;
            result.put(type.id(), new Entry(type.id(),type.labelKey(),type.category(),
                com.mine.geometry_node.client.runtime.game.scene.ScenePresentations.layer(type.id()),
                (generation,position)->ClientSceneService.INSTANCE.create(generation,type.id(),position)));
        }
        result.putAll(ENTRIES);
        return List.copyOf(result.values());
    }
}
