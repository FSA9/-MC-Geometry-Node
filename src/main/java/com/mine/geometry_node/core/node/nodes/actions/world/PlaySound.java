package com.mine.geometry_node.core.node.nodes.actions.world;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class PlaySound extends BaseNode {

    public static final String TYPE_ID = "play_sound";
    public static final String PROPERTY_SOUND = "sound_id";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.play_sound"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.XYZ.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(
                        null, null, UIHint.SELECT, null,
                        Map.of(
                                PortMetaKeys.BIND_PROPERTY, PROPERTY_SOUND,
                                PortMetaKeys.OPTIONS, RegistryDataManager.getAllSounds().toArray(new String[0])
                        )
                ))
                .addRow(new PortRow(StandardPorts.VALUE.toInputWithIndex(0, 1.0f), null, UIHint.SLIDER, null, null)) // Volume
                .addRow(new PortRow(StandardPorts.VALUE.toInputWithIndex(1, 1.0f), null, UIHint.SLIDER, null, null)) // Pitch
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Vec3 pos = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        String soundId = (String) context.getNodeProperty(PROPERTY_SOUND);
        Float volume = getInput(context, StandardPorts.VALUE.getIdWithIndex(0), Float.class);
        Float pitch = getInput(context, StandardPorts.VALUE.getIdWithIndex(1), Float.class);

        if (pos != null && soundId != null && context.getLevel() instanceof ServerLevel level) {
            // 修复点：使用 tryParse 替代 new ResourceLocation
            ResourceLocation rl = ResourceLocation.tryParse(soundId);

            if (rl != null) {
                SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(rl);
                if (soundEvent != null) {
                    float vol = volume != null ? volume : 1.0f;
                    float p = pitch != null ? pitch : 1.0f;
                    level.playSound(null, pos.x, pos.y, pos.z, soundEvent, SoundSource.MASTER, vol, p);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}