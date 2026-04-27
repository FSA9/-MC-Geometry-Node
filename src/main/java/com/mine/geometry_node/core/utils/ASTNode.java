package com.mine.geometry_node.core.utils;

import java.util.Map;

/**
 * 抽象语法树基础节点
 */
public interface ASTNode {
    // 渲染时每帧调用，传入当前的动态变量表 (如 T, X, Y, Z, V)
    double evaluate(Map<String, Double> vars);

    /**
     * [新增] 将树中的变量节点替换为指定的子树
     * @param substitutions 变量名 -> 目标 AST 节点的映射
     * @return 嫁接后的新树（或原树）
     */
    ASTNode substitute(Map<String, ASTNode> substitutions);

    /**
     * [新增] 将当前的 AST 结构还原为标准的数学公式字符串
     * 用于在服务端运算后，合成最终公式发给客户端渲染。
     */
    String toFormulaString();
}