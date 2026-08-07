package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.visual.image.ImagePathReference;
import com.mine.geometry_node.core.engine.visual.image.ServerImageAssetService;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Displays an image as a temporary plane in world space. */
public final class DrawImageVisual extends BaseNode {
    public static final String TYPE_ID = "draw_image_visual";
    public static final String SIZE_MODE_STRETCH = "stretch";
    public static final String SIZE_MODE_FIT = "fit";
    private static final Set<String> REPORTED_FAILURES = Collections.synchronizedSet(new HashSet<>());

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_image_visual"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.PATH, "path")
                        .input(StandardPorts.XYZ, "xyz")
                        .input(StandardPorts.ROTATION, "rotation")
                        .input(StandardPorts.SIZE_MODE, "size_mode")
                        .input(StandardPorts.WIDTH, "width")
                        .input(StandardPorts.HEIGHT, "height")
                        .input(StandardPorts.ALPHA, "alpha")
                        .input(StandardPorts.TICK, "tick")
                        .input(StandardPorts.VISIBILITY_RANGE, "visibility_range")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PATH.toInput(""), null, UIHint.PATH, null, null))
                .addRow(new PortRow(StandardPorts.XYZ.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_MODE.toInput(SIZE_MODE_STRETCH), null, UIHint.SELECT, null,
                        Map.of(
                                PortMetaKeys.OPTIONS, new String[]{SIZE_MODE_STRETCH, SIZE_MODE_FIT},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.image.size_mode.stretch",
                                        "geometry_node.image.size_mode.fit"
                                }
                        )))
                .addRow(floatRow(StandardPorts.WIDTH, 1.0f, 0.01f, 1024.0f))
                .addRow(floatRow(StandardPorts.HEIGHT, 1.0f, 0.01f, 1024.0f))
                .addRow(floatRow(StandardPorts.ALPHA, 1.0f, 0.0f, 1.0f))
                .addRow(new PortRow(StandardPorts.TICK.toInput(20), null, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.NUMERIC_MIN, 1, PortMetaKeys.NUMERIC_MAX, 72000)))
                .addRow(floatRow(StandardPorts.VISIBILITY_RANGE, 128.0f, 1.0f, 4096.0f))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String rawPath = getInput(context, StandardPorts.PATH.getId(), String.class);
        ServerLevel level = context.getLevel();
        if (level == null || rawPath == null || rawPath.isBlank()) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        try {
            ImagePathReference reference = ImagePathReference.parse(rawPath);
            Vec3 position = valueOr(getInput(context, StandardPorts.XYZ.getId(), Vec3.class), Vec3.ZERO);
            Vec3 rotation = valueOr(getInput(context, StandardPorts.ROTATION.getId(), Vec3.class), Vec3.ZERO);
            String sizeMode = valueOr(getInput(context, StandardPorts.SIZE_MODE.getId(), String.class), SIZE_MODE_STRETCH);
            if (!SIZE_MODE_FIT.equals(sizeMode)) sizeMode = SIZE_MODE_STRETCH;
            float width = clamp(valueOr(getInput(context, StandardPorts.WIDTH.getId(), Float.class), 1.0f), 0.01f, 1024.0f);
            float height = clamp(valueOr(getInput(context, StandardPorts.HEIGHT.getId(), Float.class), 1.0f), 0.01f, 1024.0f);
            float alpha = clamp(valueOr(getInput(context, StandardPorts.ALPHA.getId(), Float.class), 1.0f), 0.0f, 1.0f);
            int duration = Math.clamp(valueOr(getInput(context, StandardPorts.TICK.getId(), Integer.class), 20), 1, 72000);
            double visibleRange = clamp(valueOr(getInput(context, StandardPorts.VISIBILITY_RANGE.getId(), Float.class), 128.0f), 1.0f, 4096.0f);

            CompoundTag extraData = new CompoundTag();
            extraData.putString("imageSource", reference.source().id());
            extraData.putDouble("posX", position.x);
            extraData.putDouble("posY", position.y);
            extraData.putDouble("posZ", position.z);
            extraData.putDouble("rotX", rotation.x);
            extraData.putDouble("rotY", rotation.y);
            extraData.putDouble("rotZ", rotation.z);
            extraData.putString("sizeMode", sizeMode);
            extraData.putFloat("width", width);
            extraData.putFloat("height", height);
            extraData.putFloat("alpha", alpha);

            List<GraphEngineServices.VisualAsset> assets = List.of();
            if (reference.source() == ImagePathReference.Source.SERVER) {
                GraphEngineServices.VisualAsset asset = ServerImageAssetService.load(level.getServer(), reference.path());
                extraData.putString("imageRef", asset.assetId());
                assets = List.of(asset);
            } else {
                extraData.putString("imageRef", reference.path());
            }

            context.broadcastDynamicVisual(
                    "image_visual",
                    0xFFFFFFFF,
                    duration,
                    Map.of(),
                    Map.of(),
                    extraData,
                    position,
                    visibleRange,
                    assets
            );
        } catch (IOException | IllegalArgumentException exception) {
            reportOnce(rawPath, exception);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    private static PortRow floatRow(StandardPorts port, float defaultValue, float min, float max) {
        return new PortRow(port.toInput(defaultValue), null, UIHint.INPUT, null,
                Map.of(PortMetaKeys.NUMERIC_MIN, min, PortMetaKeys.NUMERIC_MAX, max));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static void reportOnce(String path, Exception exception) {
        String key = path + '\n' + exception.getMessage();
        if (REPORTED_FAILURES.add(key)) {
            GeometryNode.LOGGER.warn("Unable to display image '{}': {}", path, exception.getMessage());
        }
    }
}
