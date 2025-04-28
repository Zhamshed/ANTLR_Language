import java.util.Map;
import java.util.HashMap;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

public class TypeCheckingVisitor extends ExprBaseVisitor<Type> {
    private final SymbolTable symbolTable = new SymbolTable();
    private final ErrorReporter errorReporter = new ErrorReporter();
    
    
    private void reportError(Token token, String message) {
        errorReporter.report(token.getLine(), token.getCharPositionInLine(), message);
    }
    
    @Override
    public Type visitProgram(ExprParser.ProgramContext ctx) {
        for (var stmt : ctx.statement()) {
            if (stmt instanceof ExprParser.VarDeclStatementContext decl) {
                visit(decl);
            }
        }
        for (var stmt : ctx.statement()) {
            if (!(stmt instanceof ExprParser.VarDeclStatementContext)) {
                visit(stmt);
            }
        }
        return Type.INVALID;
    }


    @Override
    public Type visitVarDeclStatement(ExprParser.VarDeclStatementContext ctx) {
        String typeStr = ctx.TYPE().getText();
        Type declaredType = switch (ctx.TYPE().getText()) {
	        case "int" -> Type.INT;
	        case "float" -> Type.FLOAT;
	        case "bool" -> Type.BOOL;
	        case "string" -> Type.STRING;
	        case "file" -> Type.FILE;
	        default -> throw new RuntimeException("Unknown type: " + ctx.TYPE().getText());
        };


        for (TerminalNode var : ctx.VAR()) {
            String varName = var.getText();
            if (symbolTable.isDeclared(varName)) {
                reportError(ctx.getStart(), "Variable '" + varName + "' is already declared.");
            } else {
                symbolTable.declare(varName, declaredType);
            }
        }
        return null;
    }


    @Override
    public Type visitAssignExpression(ExprParser.AssignExpressionContext ctx) {
        String name = ctx.VAR().getText();
        Type valueType = visit(ctx.expression());
        if (!symbolTable.isDeclared(name)) {
            errorReporter.report(ctx.VAR().getSymbol().getLine(), ctx.VAR().getSymbol().getCharPositionInLine(),
                    "Variable '" + name + "' is not declared.");
            return Type.INVALID;
        }
        Type varType = symbolTable.getType(name);
        if (!isAssignable(varType, valueType)) {
            errorReporter.report(ctx.VAR().getSymbol().getLine(), ctx.VAR().getSymbol().getCharPositionInLine(),
                    "Cannot assign " + valueType + " to variable '" + name + "' of type " + varType + ".");
            return Type.INVALID;
        }
        return varType;
    }

    @Override
    public Type visitVarExpression(ExprParser.VarExpressionContext ctx) {
        String name = ctx.VAR().getText();
        if (!symbolTable.isDeclared(name)) {
            errorReporter.report(ctx.VAR().getSymbol().getLine(), ctx.VAR().getSymbol().getCharPositionInLine(),
                    "Variable '" + name + "' is not declared.");
            return Type.INVALID;
        }
        return symbolTable.getType(name);
    }

    @Override
    public Type visitLiteralExpression(ExprParser.LiteralExpressionContext ctx) {
        return visit(ctx.literal());
    }

    @Override
    public Type visitLiteral(ExprParser.LiteralContext ctx) {
        if (ctx.INT() != null) return Type.INT;
        if (ctx.FLOAT() != null) return Type.FLOAT;
        if (ctx.BOOL() != null) return Type.BOOL;
        if (ctx.STRING() != null) return Type.STRING;
        return Type.INVALID;
    }

    @Override
    public Type visitParenExpression(ExprParser.ParenExpressionContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Type visitNegExpression(ExprParser.NegExpressionContext ctx) {
        Type t = visit(ctx.expression());
        if (t == Type.INT || t == Type.FLOAT) return t;
        errorReporter.report(ctx.op.getLine(), ctx.op.getCharPositionInLine(), "Unary minus requires numeric operand.");
        return Type.INVALID;
    }

    @Override
    public Type visitNotExpression(ExprParser.NotExpressionContext ctx) {
        Type t = visit(ctx.expression());
        if (t == Type.BOOL) return Type.BOOL;
        errorReporter.report(ctx.op.getLine(), ctx.op.getCharPositionInLine(), "Logical NOT requires boolean operand.");
        return Type.INVALID;
    }

    @Override
    public Type visitLogicExpression(ExprParser.LogicExpressionContext ctx) {
        Type left = visit(ctx.expression(0));
        Type right = visit(ctx.expression(1));
        if (left == Type.BOOL && right == Type.BOOL) return Type.BOOL;
        errorReporter.report(ctx.op.getLine(), ctx.op.getCharPositionInLine(), "Logical operator requires boolean operands.");
        return Type.INVALID;
    }

    @Override
    public Type visitComparisonExpression(ExprParser.ComparisonExpressionContext ctx) {
        Type left = visit(ctx.expression(0));
        Type right = visit(ctx.expression(1));
        return Type.BOOL; // Тип проверяется в relationExpression
    }

    @Override
    public Type visitRelationExpression(ExprParser.RelationExpressionContext ctx) {
        Type left = visit(ctx.expression(0));
        Type right = visit(ctx.expression(1));
        if (left == Type.STRING || right == Type.STRING || left == Type.BOOL || right == Type.BOOL) {
            errorReporter.report(ctx.op.getLine(), ctx.op.getCharPositionInLine(), "Relational operators require numeric operands.");
            return Type.INVALID;
        }
        return Type.BOOL;
    }

    @Override
    public Type visitAritmExpression(ExprParser.AritmExpressionContext ctx) {
        Type left = visit(ctx.expression(0));
        Type right = visit(ctx.expression(1));
        if (left == Type.STRING || right == Type.STRING || left == Type.BOOL || right == Type.BOOL) {
            errorReporter.report(ctx.op.getLine(), ctx.op.getCharPositionInLine(), "Arithmetic operations require numeric operands.");
            return Type.INVALID;
        }
        return (left == Type.FLOAT || right == Type.FLOAT) ? Type.FLOAT : Type.INT;
    }

    @Override
    public Type visitModuloExpression(ExprParser.ModuloExpressionContext ctx) {
        Type left = visit(ctx.expression(0));
        Type right = visit(ctx.expression(1));
        if (left != Type.INT || right != Type.INT) {
            errorReporter.report(ctx.op.getLine(), ctx.op.getCharPositionInLine(), "Modulo operator requires integer operands.");
            return Type.INVALID;
        }
        return Type.INT;
    }

    @Override
    public Type visitConcatExpression(ExprParser.ConcatExpressionContext ctx) {
        Type left = visit(ctx.expression(0));
        Type right = visit(ctx.expression(1));
        if (left != Type.STRING || right != Type.STRING) {
            errorReporter.report(ctx.op.getLine(), ctx.op.getCharPositionInLine(), "Concatenation requires string operands.");
            return Type.INVALID;
        }
        return Type.STRING;
    }

    @Override
    public Type visitWriteStatement(ExprParser.WriteStatementContext ctx) {
        for (var expr : ctx.expression()) visit(expr);
        return Type.INVALID;
    }

    @Override
    public Type visitReadStatement(ExprParser.ReadStatementContext ctx) {
        for (var var : ctx.VAR()) {
            String name = var.getText();
            if (!symbolTable.isDeclared(name)) {
                errorReporter.report(var.getSymbol().getLine(), var.getSymbol().getCharPositionInLine(),
                        "Variable '" + name + "' is not declared.");
            }
        }
        return Type.INVALID;
    }

    @Override
    public Type visitIfStatement(ExprParser.IfStatementContext ctx) {
        Type cond = visit(ctx.condition());
        if (cond != Type.BOOL) {
            errorReporter.report(ctx.condition().start.getLine(), ctx.condition().start.getCharPositionInLine(),
                    "Condition in 'if' must be boolean.");
        }
        visit(ctx.statement(0));
        if (ctx.statement().size() > 1) visit(ctx.statement(1));
        return Type.INVALID;
    }

    @Override
    public Type visitWhileStatement(ExprParser.WhileStatementContext ctx) {
        Type cond = visit(ctx.condition());
        if (cond != Type.BOOL) {
            errorReporter.report(ctx.condition().start.getLine(), ctx.condition().start.getCharPositionInLine(),
                    "Condition in 'while' must be boolean.");
        }
        visit(ctx.statement());
        return Type.INVALID;
    }

    private boolean isAssignable(Type target, Type value) {
        return target == value || (target == Type.FLOAT && value == Type.INT);
    }

    public boolean hasErrors() {
        return errorReporter.hasErrors();
    }
}
