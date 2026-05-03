package com.mine.geometry_node.core.utils;

public class ExpressionCompiler {

    /**
     * 传入变量注册表进行编译，自动为变量分配数组索引
     */
    public static ASTNode compile(final String str, VariableRegistry registry) {
        if (str == null || str.trim().isEmpty()) {
            return new ASTNodes.ConstantNode(0);
        }

        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            ASTNode parse() {
                nextChar();
                ASTNode node = parseExpression();
                if (pos < str.length()) {
                    System.err.println("[ExpressionCompiler] Unexpected character: " + (char) ch + " in " + str);
                }
                return node;
            }

            ASTNode parseExpression() {
                ASTNode node = parseTerm();
                for (;;) {
                    if      (eat('+')) node = new ASTNodes.BinaryNode('+', node, parseTerm());
                    else if (eat('-')) node = new ASTNodes.BinaryNode('-', node, parseTerm());
                    else return node;
                }
            }

            ASTNode parseTerm() {
                ASTNode node = parseFactor();
                for (;;) {
                    if      (eat('*')) node = new ASTNodes.BinaryNode('*', node, parseFactor());
                    else if (eat('/')) node = new ASTNodes.BinaryNode('/', node, parseFactor());
                    else return node;
                }
            }

            ASTNode parseFactor() {
                if (eat('+')) return new ASTNodes.UnaryNode('+', parseFactor());
                if (eat('-')) return new ASTNodes.UnaryNode('-', parseFactor());

                ASTNode node = null;
                int startPos = this.pos;
                if (eat('(')) {
                    node = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    double value = Double.parseDouble(str.substring(startPos, this.pos));
                    node = new ASTNodes.ConstantNode(value);
                } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                    while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_') {
                        nextChar();
                    }
                    String name = str.substring(startPos, this.pos);

                    // 拦截内置函数
                    if (name.equals("sin") || name.equals("cos") || name.equals("tan") ||
                            name.equals("sqrt") || name.equals("abs")) {
                        node = new ASTNodes.FunctionNode(name, parseFactor());
                    } else {
                        // 统一走注册表获取数组索引
                        String varName = name.equalsIgnoreCase("tick") ? "tick" : name;
                        int index = registry.registerOrGet(varName);
                        node = new ASTNodes.VariableNode(index, varName);
                    }
                } else {
                    System.err.println("[ExpressionCompiler] Illegal character: " + (char) ch);
                    return new ASTNodes.ConstantNode(0);
                }

                if (eat('^')) node = new ASTNodes.BinaryNode('^', node, parseFactor());
                return node;
            }
        }.parse();
    }
}