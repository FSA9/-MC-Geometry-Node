package com.mine.geometry_node.client.feature.ai.command;

import com.google.gson.JsonObject;
import com.mine.geometry_node.client.feature.ai.protocol.ToolContract;
import com.mine.geometry_node.client.history.HistoryState;

import java.util.List;

/** Runtime-neutral history query contract. Mutations continue through their domain's command adapter. */
public final class HistoryCommandCatalog {
    private HistoryCommandCatalog() {}

    static void registerInto(CommandRegistry registry) {
        JsonObject properties = new JsonObject();
        for (String key : List.of("surface_ref", "scope_id", "phase", "last_change_id", "undo_label",
                "redo_label", "undo_blocked_reason", "redo_blocked_reason"))
            properties.add(key, CommandSpec.scalarSchema("string"));
        for (String key : List.of("revision", "undo_count", "redo_count"))
            properties.add(key, CommandSpec.scalarSchema("integer"));
        for (String key : List.of("can_undo", "can_redo", "savepoints", "dirty"))
            properties.add(key, CommandSpec.scalarSchema("boolean"));
        registry.register(new CommandSpec("get_history_state", List.of(),
                "查询指定编辑界面的历史作用域、撤销/重做能力、等待状态与禁用原因；支持 Graph 和编辑模式的游戏视图。"
                        + "revision 是本地历史变更计数，不是服务器场景版本；savepoints=false 时 dirty 不表示世界是否落盘。",
                "get_history_state <surface_ref>",
                List.of(new CommandArgumentSpec("surface_ref", "明确的界面引用，例如 V1 或 GV1", true,
                        null, CommandSpec.scalarSchema("string"), null)),
                CommandSpec.objectSchema(properties, properties.keySet().toArray(String[]::new)),
                ToolContract.CommandEffect.READ_ONLY, ToolContract.RiskLevel.READ_ONLY, false, false,
                CommandSpec.Exposure.MODEL_VISIBLE,
                (context, args) -> context.target() instanceof HistoryQueryTarget target
                        ? target.getHistoryState(args.get("surface_ref").getAsString())
                        : CommandResult.failure("HISTORY_UNAVAILABLE", "当前目标未提供历史查询能力")));
    }

    public static CommandResult describe(String surfaceRef, HistoryState state) {
        JsonObject data = new JsonObject();
        data.addProperty("surface_ref", surfaceRef);
        data.addProperty("scope_id", state.scopeId());
        data.addProperty("phase", state.phase().name());
        data.addProperty("revision", state.revision());
        data.addProperty("last_change_id", state.lastChangeId());
        data.addProperty("undo_count", state.undoCount());
        data.addProperty("redo_count", state.redoCount());
        data.addProperty("undo_label", state.undoLabel());
        data.addProperty("redo_label", state.redoLabel());
        data.addProperty("can_undo", state.canUndo());
        data.addProperty("can_redo", state.canRedo());
        data.addProperty("savepoints", state.savepoints());
        data.addProperty("dirty", state.dirty());
        data.addProperty("undo_blocked_reason", state.undoBlockedReason());
        data.addProperty("redo_blocked_reason", state.redoBlockedReason());
        return CommandResult.success("HISTORY_STATE", "已读取历史状态", data);
    }
}
