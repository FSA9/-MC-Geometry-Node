package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.system.visual.image.ImagePathReference;
import com.mine.geometry_node.core.engine.system.visual.image.ImageVisualRequest;
import com.mine.geometry_node.core.engine.system.visual.image.ServerImageAssetService;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.utils.RateLimitedLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Displays an image as a temporary plane in world space. */
public final class DrawImageVisual extends BaseNode {
    public static final String TYPE_ID = "draw_image_visual";
    public static final String SIZE_MODE_STRETCH = "stretch";
    public static final String SIZE_MODE_FIT = "fit";
    public static final PortDef POSITION_PORT = StandardPorts.XYZ.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef ROTATION_PORT = StandardPorts.ROTATION.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef WIDTH_PORT = StandardPorts.WIDTH.toInput(1.0F).liveExpression();
    public static final PortDef HEIGHT_PORT = StandardPorts.HEIGHT.toInput(1.0F).liveExpression();
    public static final PortDef ALPHA_PORT = StandardPorts.ALPHA.toInput(1.0F).liveExpression();
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
                .addPassthroughInput(StandardPorts.PATH.toInput(""), UIHint.PATH)
                .addPassthroughInput(POSITION_PORT, UIHint.VECTOR)
                .addPassthroughInput(ROTATION_PORT, UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.SIZE_MODE.toInput(SIZE_MODE_STRETCH), UIHint.SELECT, null, Map.of(
                                PortMetaKeys.OPTIONS, new String[]{SIZE_MODE_STRETCH, SIZE_MODE_FIT},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.image.size_mode.stretch",
                                        "geometry_node.image.size_mode.fit"
                                }
                        ))
                .addPassthroughInput(WIDTH_PORT, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.NUMERIC_MIN, 0.01f, PortMetaKeys.NUMERIC_MAX, 1024.0f))
                .addPassthroughInput(HEIGHT_PORT, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.NUMERIC_MIN, 0.01f, PortMetaKeys.NUMERIC_MAX, 1024.0f))
                .addPassthroughInput(ALPHA_PORT, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.NUMERIC_MIN, 0.0f, PortMetaKeys.NUMERIC_MAX, 1.0f))
                .addPassthroughInput(StandardPorts.TICK.toInput(20), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 1, PortMetaKeys.NUMERIC_MAX, 72000))
                .addPassthroughInput(StandardPorts.VISIBILITY_RANGE.toInput(128.0F), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 1.0F,
                                PortMetaKeys.NUMERIC_MAX, 4096.0F))
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

            Map<String, ExpressionData> expressions = new LinkedHashMap<>();
            putInputExpression(context, StandardPorts.XYZ.getId(), "position", expressions);
            putInputExpression(context, StandardPorts.ROTATION.getId(), "rotation", expressions);
            putInputExpression(context, StandardPorts.WIDTH.getId(), "width", expressions);
            putInputExpression(context, StandardPorts.HEIGHT.getId(), "height", expressions);
            putInputExpression(context, StandardPorts.ALPHA.getId(), "alpha", expressions);

            if (reference.source() == ImagePathReference.Source.SERVER) {
                return ExecutionResult.externalWait(ServerImageAssetService.ID, new ImageVisualRequest(
                        reference.path(), duration, expressions, extraData, position, visibleRange));
            }
            extraData.putString("imageRef", reference.path());

            context.broadcastDynamicVisual(
                    "image_visual",
                    0xFFFFFFFF,
                    duration,
                    expressions,
                    extraData,
                    position,
                    visibleRange,
                    List.of()
            );
        } catch (IllegalArgumentException exception) {
            if (RateLimitedLog.acquire(context,
                    "image_visual:" + rawPath + ':' + exception.getClass().getName())) {
                GeometryNode.LOGGER.warn("Unable to display image '{}': {}", rawPath, exception.getMessage());
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }

}
