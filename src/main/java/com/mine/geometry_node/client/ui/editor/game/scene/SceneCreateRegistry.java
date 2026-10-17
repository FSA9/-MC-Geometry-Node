package com.mine.geometry_node.client.ui.editor.game.scene;

import com.mine.geometry_node.client.scene.ClientSceneService;
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
    static {
        register(new Entry("geometry_node:interaction", "geometry_node.game_view.scene.layer.interaction", "",
                com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization.Layer.INTERACTION,
                com.mine.geometry_node.client.runtime.game.selection.ViewportInteractionCreationClient::create));
    }
    private SceneCreateRegistry(){}
    public static synchronized void register(Entry entry){if(ENTRIES.putIfAbsent(entry.typeId(),entry)!=null)throw new IllegalArgumentException("Duplicate creation entry");}
    public static synchronized List<Entry> entries(){
        Map<String,Entry> result = new LinkedHashMap<>();
        for (var type : SceneObjectTypes.all()) result.put(type.id(), new Entry(type.id(),type.labelKey(),type.category(),
                com.mine.geometry_node.client.runtime.game.scene.ScenePresentations.layer(type.id()),
                (generation,position)->ClientSceneService.INSTANCE.create(generation,type.id(),position)));
        result.putAll(ENTRIES);
        return List.copyOf(result.values());
    }
}
