package com.mine.geometry_node.client.ui.bottom_window.console;

import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.UICommand.commands.*;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeRegistry;
import java.util.*;

public class ConsoleCommandRegistry {

    public interface LogCallback {
        void onLog(String text, int color);
        void onClear();
    }

    public static void executeLine(String line, GraphSession session, LogCallback logger) {
        line = line.trim();
        if (line.isEmpty()) return;

        String[] parts = line.split("\\s+");
        String cmdName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        if (session == null && !cmdName.equals("clear")) {
            logger.onLog("[Error] 执行失败: 当前没有打开且活跃的蓝图会话", 0xFFFF4444);
            return;
        }

        try {
            switch (cmdName) {
                case "clear" -> logger.onClear();
                case "addnode" -> handleAddNode(args, session, logger);
                case "delete" -> handleDelete(args, session, logger);
                case "connect" -> handleConnect(args, session, logger);
                default -> logger.onLog("[Error] 语法错误: 未知的指令 '" + cmdName + "'", 0xFFFF4444);
            }
        } catch (Exception e) {
            logger.onLog("[Error] 发生了未捕获的严重异常: " + e.getMessage(), 0xFFFF0000);
        }
    }

    private static void handleAddNode(String[] args, GraphSession session, LogCallback logger) {
        // 1. 参数数量校验
        if (args.length < 1) {
            logger.onLog("[Error] 缺少参数。用法: addnode <型体ID> [x] [y] [自定义ID]", 0xFFFF4444);
            return;
        }

        String typeId = args[0];

        // 2. 类型存在性校验
        if (!NodeRegistry.INSTANCE.has(typeId)) {
            logger.onLog("[Error] 节点生成失败: 注册表中不存在类型为 '" + typeId + "' 的节点", 0xFFFF4444);
            return;
        }

        // 3. 数字格式校验
        float x = 0f, y = 0f;
        try {
            if (args.length > 1) x = Float.parseFloat(args[1]);
            if (args.length > 2) y = Float.parseFloat(args[2]);
        } catch (NumberFormatException e) {
            logger.onLog("[Error] 参数格式错误: 坐标 x 和 y 必须是合法的数字", 0xFFFF4444);
            return;
        }

        String id = args.length > 3 ? args[3] : UUID.randomUUID().toString();

        // 4. ID 冲突校验 (防止覆盖已有的同名节点)
        if (session.editorContext.getGraph().getNode(id) != null) {
            logger.onLog("[Error] 节点生成失败: 当前画布中已经存在 ID 为 '" + id + "' 的节点", 0xFFFF4444);
            return;
        }

        // 5. 正式执行
        NodeData data = new NodeData(id, typeId, x, y);
        CmdAddNode cmd = new CmdAddNode(session.editorContext.getGraphController(), data);
        session.editorContext.getCommandManager().execute(cmd);

        logger.onLog("[Success] 节点添加成功 | Type: " + typeId + " | ID: " + id, 0xFF00AAFF);
    }

    private static void handleDelete(String[] args, GraphSession session, LogCallback logger) {
        if (args.length < 1) {
            logger.onLog("[Error] 缺少参数。用法: delete <节点ID>", 0xFFFF4444);
            return;
        }

        String id = args[0];

        // 1. 目标节点存在性校验
        if (session.editorContext.getGraph().getNode(id) == null) {
            logger.onLog("[Error] 删除失败: 画布中找不到 ID 为 '" + id + "' 的节点", 0xFFFF4444);
            return;
        }

        CmdRemoveNodes cmd = new CmdRemoveNodes(session.editorContext.getGraphController(),
                session.editorContext.getGraph(), Collections.singletonList(id));
        session.editorContext.getCommandManager().execute(cmd);

        logger.onLog("[Success] 节点已删除 | ID: " + id, 0xFF00AAFF);
    }

    private static void handleConnect(String[] args, GraphSession session, LogCallback logger) {
        if (args.length < 4) {
            logger.onLog("[Error] 缺少参数。用法: connect <输出节点ID> <输出端口> <输入节点ID> <输入端口>", 0xFFFF4444);
            return;
        }

        String outId = args[0];
        String outPort = args[1];
        String inId = args[2];
        String inPort = args[3];

        // 1. 节点存在性双重校验
        if (session.editorContext.getGraph().getNode(outId) == null) {
            logger.onLog("[Error] 连线失败: 找不到输出端节点 ID '" + outId + "'", 0xFFFF4444);
            return;
        }
        if (session.editorContext.getGraph().getNode(inId) == null) {
            logger.onLog("[Error] 连线失败: 找不到输入端节点 ID '" + inId + "'", 0xFFFF4444);
            return;
        }

        CmdConnect cmd = new CmdConnect(session.editorContext.getGraphController(),
                session.editorContext.getGraph(), outId, outPort, inId, inPort);
        session.editorContext.getCommandManager().execute(cmd);

        logger.onLog(String.format("[Success] 连线成功 | %s[%s] -> %s[%s]", outId, outPort, inId, inPort), 0xFF00AAFF);
    }
}