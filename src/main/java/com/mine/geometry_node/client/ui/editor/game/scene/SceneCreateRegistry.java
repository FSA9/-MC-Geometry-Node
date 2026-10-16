package com.mine.geometry_node.client.ui.editor.game.scene;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.BiConsumer;

/** Default creation entries are derived from type plugins; client factories may override them. */
public final class SceneCreateRegistry {
    public record Entry(String typeId,String labelKey,String category,BiConsumer<Long,Vec3> create) {}
    private static final Map<String,Entry> ENTRIES=new LinkedHashMap<>();
    private SceneCreateRegistry(){}
    public static synchronized void register(Entry entry){if(ENTRIES.putIfAbsent(entry.typeId(),entry)!=null)throw new IllegalArgumentException("Duplicate creation entry");}
    public static synchronized List<Entry> entries(){
        return SceneObjectTypes.all().stream().map(type->ENTRIES.getOrDefault(type.id(),new Entry(type.id(),type.labelKey(),type.category(),
                (generation,position)->ClientSceneService.INSTANCE.create(generation,type.id(),position)))).toList();
    }
}
