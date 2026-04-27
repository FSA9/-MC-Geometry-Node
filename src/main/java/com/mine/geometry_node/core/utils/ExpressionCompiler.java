package com.mine.geometry_node.core.utils;

public class ExpressionCompiler {

    /**
     * 将字符串表达式编译为高效率的 AST 树
     * 只需要在接收到网络包时调用【一次】
     */
    public static ASTNode compile(final String str) {
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
                } else if ((ch >= '0' && ch <= '9') || ch == '.') { // 数字常量
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    double value = Double.parseDouble(str.substring(startPos, this.pos));
                    node = new ASTNodes.ConstantNode(value);
                } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) { // 变量或函数
                    while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_') {
                        nextChar();
                    }
                    String name = str.substring(startPos, this.pos);

                    if (name.equalsIgnoreCase("tick")) {
                        node = new ASTNodes.VariableNode("tick");
                    }
                    // 判断是否是内置函数
                    else if (name.equals("sin") || name.equals("cos") || name.equals("tan") ||
                            name.equals("sqrt") || name.equals("abs")) {
                        node = new ASTNodes.FunctionNode(name, parseFactor());
                    }
                    // 其他统统作为普通动态变量 (如 A, B, env_123_pos_x)
                    else {
                        node = new ASTNodes.VariableNode(name);
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