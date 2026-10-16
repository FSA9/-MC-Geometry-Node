package com.mine.geometry_node.client.ui.editor.game.scene;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.BiConsumer;

/** UI factories are keyed by the same stable type IDs validated by the scene service. */
public final class SceneCreateRegistry {
    public record Entry(String typeId,String labelKey,BiConsumer<Long,Vec3> create) {
        public Entry { Objects.requireNonNull(typeId);Objects.requireNonNull(labelKey);Objects.requireNonNull(create); }
    }
    private static final Map<String,Entry> ENTRIES=new LinkedHashMap<>();
    static { register(new Entry(SceneObject.POINT_LIGHT,"geometry_node.game_view.scene.point_light",
            (generation,position)->ClientSceneService.INSTANCE.createPointLight(generation,position))); }
    private SceneCreateRegistry(){}
    public static synchronized void register(Entry entry){
        if(ENTRIES.putIfAbsent(entry.typeId(),entry)!=null)throw new IllegalArgumentException("Duplicate scene creation entry: "+entry.typeId());
    }
    public static synchronized List<Entry> entries(){
        Set<String> types=new HashSet<>();for(var type:SceneObjectTypes.all())types.add(type.id());
        return ENTRIES.values().stream().filter(e->types.contains(e.typeId())).toList();
    }
}
