package com.mine.geometry_node.core.utils;

import java.util.Map;

public class ASTNodes {

    public record ConstantNode(double value) implements ASTNode {
        @Override
        public double evaluate(Map<String, Double> vars) {
            return value;
        }
    }

    public record VariableNode(String name) implements ASTNode {
        @Override
        public double evaluate(Map<String, Double> vars) {
            // 找不到变量时默认返回 0.0，防止渲染崩溃
            return vars.getOrDefault(name, 0.0);
        }
    }

    public record BinaryNode(char operator, ASTNode left, ASTNode right) implements ASTNode {
        @Override
        public double evaluate(Map<String, Double> vars) {
            double l = left.evaluate(vars);
            double r = right.evaluate(vars);
            return switch (operator) {
                case '+' -> l + r;
                case '-' -> l - r;
                case '*' -> l * r;
                case '/' -> r == 0 ? 0 : l / r; // 防止除零报错
                case '^' -> Math.pow(l, r);
                default -> 0;
            };
        }
    }

    public record UnaryNode(char operator, ASTNode child) implements ASTNode {
        @Override
        public double evaluate(Map<String, Double> vars) {
            double v = child.evaluate(vars);
            return operator == '-' ? -v : v;
        }
    }

    public record FunctionNode(String funcName, ASTNode child) implements ASTNode {
        @Override
        public double evaluate(Map<String, Double> vars) {
            double v = child.evaluate(vars);
            return switch (funcName) {
                case "sin" -> Math.sin(v);
                case "cos" -> Math.cos(v);
                case "tan" -> Math.tan(v);
                case "sqrt" -> Math.sqrt(v);
                case "abs" -> Math.abs(v);
                default -> 0;
            };
        }
    }
}