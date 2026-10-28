package com.mine.geometry_node.core.node.nodes.actions.light;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.*;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.value.light.LightConfig;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

/** Independently composable light configuration groups. */
public final class LightConfigurationNode extends BaseNode {
    public enum Group { TRANSFORM, SHADOW, VOLUME, PROJECTION, QUALITY, SURFACE_QUALITY, VOLUME_QUALITY, LIFECYCLE }
    private final Group group;
    public LightConfigurationNode(Group group) { this.group = group; }
    public static PortDef port(String id, PortType type, Object fallback) {
        return PortDef.create(id, "geometry_node.port.light." + id, type, fallback);
    }
    @Override public NodeDef getDefaultDefinition() {
        return definition(null);
    }
    @Override public NodeDef getDefinition(NodeData data) {
        return definition(data);
    }
    private NodeDef definition(NodeData data) {
        String id = "light_" + group.name().toLowerCase(java.util.Locale.ROOT);
        var b = NodeDef.builder(id, NodeType.DATA, Component.translatable("geometry_node.node." + id))
                .addMeta(com.mine.geometry_node.core.node.meta.SchemaKeys.HIDDEN_FROM_MENU, true);
        b.addRow(new PortRow(null, port("config", PortType.LIGHT_CONFIG, LightConfig.DEFAULT), UIHint.DEFAULT, null, null));
        b.addPassthroughInput(port("base", PortType.LIGHT_CONFIG, LightConfig.DEFAULT), UIHint.DEFAULT);
        addGroupInputs(b, group);
        return LightNodeSchema.finish(b.build(), data, group == Group.VOLUME);
    }
    static void addGroupInputs(NodeDef.Builder b, Group group) {
        switch (group) {
            case TRANSFORM -> {
                b.addRow(AnchorNodeSupport.row(StandardPorts.SPATIAL_ANCHOR));
                b.addRow(PortRow.passthrough(port("local_offset",PortType.XYZ,Vec3.ZERO),UIHint.VECTOR,null,null));
                b.addRow(PortRow.passthrough(port("local_rotation",PortType.XYZ,Vec3.ZERO),UIHint.VECTOR,null,null));
            }
            case SHADOW -> {
                add(b, "enabled", PortType.BOOLEAN, true, UIHint.CHECKBOX);
                add(b, "source_size", PortType.FLOAT, 0.02f, UIHint.INPUT);
            }
            case VOLUME -> {
                b.addPassthroughInput(port("mode", PortType.STRING, "off"), UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, new String[]{"off", "uniform", "noise"}, PortMetaKeys.OPTION_LABELS,
                                new String[]{"geometry_node.light.option.off", "geometry_node.light.option.uniform", "geometry_node.light.option.noise"}));
                add(b, "strength", PortType.FLOAT, 1f, UIHint.INPUT);
                add(b, "density", PortType.FLOAT, 0.02f, UIHint.INPUT);
                add(b, "noise_strength", PortType.FLOAT, 0.5f, UIHint.INPUT);
                add(b, "noise_scale", PortType.FLOAT, 1f, UIHint.INPUT);
                add(b, "velocity", PortType.XYZ, Vec3.ZERO, UIHint.VECTOR);
                add(b, "seed", PortType.INTEGER, 0, UIHint.INPUT);
            }
            case PROJECTION -> {
                add(b, "path", PortType.PATH, "", UIHint.PATH);
                add(b, "colored", PortType.BOOLEAN, false, UIHint.CHECKBOX);
                add(b, "angle", PortType.FLOAT, 0f, UIHint.INPUT);
                add(b, "scale", PortType.FLOAT, 1f, UIHint.INPUT);
            }
            case SURFACE_QUALITY -> {
                add(b, "shadow_resolution", PortType.FLOAT, 1f, UIHint.INPUT);
                add(b, "shadow_samples", PortType.INTEGER, 4, UIHint.INPUT);
            }
            case VOLUME_QUALITY -> {
                add(b, "volume_resolution", PortType.FLOAT, 0.5f, UIHint.INPUT);
                add(b, "volume_samples", PortType.INTEGER, 24, UIHint.INPUT);
                add(b, "volume_emitter_samples", PortType.INTEGER, 4, UIHint.INPUT);
            }
            case QUALITY -> {
                add(b, "shadow_resolution", PortType.FLOAT, 1f, UIHint.INPUT);
                add(b, "shadow_samples", PortType.INTEGER, 4, UIHint.INPUT);
                add(b, "volume_samples", PortType.INTEGER, 24, UIHint.INPUT);
                add(b, "volume_resolution", PortType.FLOAT, 0.5f, UIHint.INPUT);
            }
            case LIFECYCLE -> {
                add(b, "duration_ticks", PortType.INTEGER, 0, UIHint.INPUT);
                add(b, "enabled", PortType.BOOLEAN, true, UIHint.CHECKBOX);
            }
        }
    }
    static void add(NodeDef.Builder b, String id, PortType type, Object fallback, UIHint hint) {
        b.addPassthroughInput(port(id, type, fallback), hint, null, LightNodeSchema.numericBounds(id));
    }
    private <T> T value(GraphDataContext c, String id, Class<T> type, T fallback) {
        T v = getInput(c, id, type); return v == null ? fallback : v;
    }
    @Override public Object compute(GraphDataContext c, String port) {
        if (!"config".equals(port)) return null;
        LightConfig base = value(c, "base", LightConfig.class, LightConfig.DEFAULT);
        return apply(c, base);
    }
    LightConfig apply(GraphDataContext c, LightConfig base) {
        return switch (group) {
            case TRANSFORM -> {
                SpatialAnchor anchor = AnchorNodeSupport.read(c, StandardPorts.SPATIAL_ANCHOR);
                if (anchor == null) throw new IllegalArgumentException("Light spatial anchor is unavailable");
                yield base.withTransform(new LightConfig.Transform(anchor,
                        value(c,"local_offset",Vec3.class,Vec3.ZERO), value(c,"local_rotation",Vec3.class,Vec3.ZERO)));
            }
            case SHADOW -> base.withShadow(new LightConfig.Shadow(value(c,"enabled",Boolean.class,true), value(c,"source_size",Float.class,0.02f)));
            case VOLUME -> base.withVolume(new LightConfig.Volume(value(c,"mode",String.class,"off"), value(c,"strength",Float.class,1f),
                    value(c,"density",Float.class,0.02f), value(c,"noise_strength",Float.class,0.5f), value(c,"noise_scale",Float.class,1f),
                    value(c,"velocity",Vec3.class,Vec3.ZERO), value(c,"seed",Integer.class,0)));
            case PROJECTION -> base.withProjection(new LightConfig.Projection(value(c,"path",String.class,""), value(c,"colored",Boolean.class,false),
                    value(c,"angle",Float.class,0f), value(c,"scale",Float.class,1f)));
            case SURFACE_QUALITY -> base.withQuality(new LightConfig.Quality(
                    value(c,"shadow_resolution",Float.class,1f), value(c,"shadow_samples",Integer.class,4),
                    base.quality().volumeSamples(), base.quality().volumeResolution(), base.quality().volumeEmitterSamples()));
            case VOLUME_QUALITY -> base.withQuality(new LightConfig.Quality(
                    base.quality().shadowResolution(), base.quality().shadowSamples(),
                    value(c,"volume_samples",Integer.class,24), value(c,"volume_resolution",Float.class,0.5f),
                    value(c,"volume_emitter_samples",Integer.class,4)));
            case QUALITY -> base.withQuality(new LightConfig.Quality(value(c,"shadow_resolution",Float.class,1f), value(c,"shadow_samples",Integer.class,4),
                    value(c,"volume_samples",Integer.class,24), value(c,"volume_resolution",Float.class,0.5f)));
            case LIFECYCLE -> base.withLifecycle(new LightConfig.Lifecycle(value(c,"duration_ticks",Integer.class,0), value(c,"enabled",Boolean.class,true)));
        };
    }
}
