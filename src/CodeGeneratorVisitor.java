import java.util.ArrayList;
import java.util.List;

public class CodeGeneratorVisitor extends ExprBaseVisitor<Void> {
    private final List<String> instructions = new ArrayList<>();
    private final SymbolTable symbolTable = new SymbolTable();
    private final TypeCheckingVisitor typeChecker = new TypeCheckingVisitor();
    private int labelCounter = 0;
    private boolean isInControlFlow = false;
    private boolean firstFappend = true;
    private String fileVarName = null;

    public List<String> getInstructions() {
        return instructions;
    }

    private String generateNumericLabel() {
        return String.valueOf(labelCounter++);
    }

    private Type inferType(ExprParser.ExpressionContext ctx) {
        return typeChecker.visit(ctx);
    }
    
    private void addRelationalInstruction(String op) {
        String suffix = isInControlFlow ? " I" : "";
        switch (op) {
            case "<" -> instructions.add("lt" + suffix);
            case ">" -> instructions.add("gt" + suffix);
        }
    }

    @Override
    public Void visitProgram(ExprParser.ProgramContext ctx) {
        for (var stmt : ctx.statement()) {
            visit(stmt);
        }
        return null;
    }

    @Override
    public Void visitVarDeclStatement(ExprParser.VarDeclStatementContext ctx) {
        String typeStr = ctx.TYPE().getText();
        Type declaredType = switch (typeStr) {
            case "int" -> Type.INT;
            case "float" -> Type.FLOAT;
            case "string" -> Type.STRING;
            case "bool" -> Type.BOOL;
            case "file" -> Type.FILE;
            default -> throw new RuntimeException("Unknown type: " + typeStr);
        };

        for (var var : ctx.VAR()) {
            String name = var.getText();
            symbolTable.declare(name, declaredType);
            if (declaredType == Type.FILE) {
                continue;
            }
            switch (declaredType) {
                case INT -> instructions.add("push I 0");
                case FLOAT -> instructions.add("push F 0.0");
                case STRING -> instructions.add("push S \"\"");
                case BOOL -> instructions.add("push B false");
            }
            instructions.add("save " + name);
        }
        return null;
    }



    @Override
    public Void visitAssignExpression(ExprParser.AssignExpressionContext ctx) {
        String name = ctx.VAR().getText();
        Type varType = symbolTable.getType(name);
        Type exprType = inferType(ctx.expression());

        visit(ctx.expression());

        if (varType == Type.FLOAT && exprType == Type.INT) {
            instructions.add("itof");
        }

        instructions.add("save " + name);
        instructions.add("load " + name);
        instructions.add("pop");
        return null;
    }

    @Override
    public Void visitLiteralExpression(ExprParser.LiteralExpressionContext ctx) {
        var literal = ctx.literal();
        if (literal.INT() != null) {
            instructions.add("push I " + literal.INT().getText());
        } else if (literal.FLOAT() != null) {
            instructions.add("push F " + literal.FLOAT().getText());
        } else if (literal.STRING() != null) {
            instructions.add("push S " + literal.STRING().getText());
        } else if (literal.BOOL() != null) {
            instructions.add("push B " + literal.BOOL().getText());
        }
        return null;
    }

    @Override
    public Void visitVarExpression(ExprParser.VarExpressionContext ctx) {
        instructions.add("load " + ctx.VAR().getText());
        return null;
    }

    @Override
    public Void visitWriteStatement(ExprParser.WriteStatementContext ctx) {
        for (var expr : ctx.expression()) {
            visit(expr);
        }
        instructions.add("print " + ctx.expression().size());
        return null;
    }
    
    @Override
    public Void visitFopenExpression(ExprParser.FopenExpressionContext ctx) {
        String varName = ctx.VAR().getText();
        String filename = ctx.STRING().getText();
        int fappVar = ctx.invokingState ;
        instructions.add("push S " + filename);
        instructions.add("fopen");
        instructions.add("save " + varName);
        return null;
    }
    
//    @Override
//    public Void visitFileAppendExpression(ExprParser.FileAppendExpressionContext ctx) {
//        if (!(ctx.expression(0) instanceof ExprParser.FileAppendExpressionContext)) {
//            visit(ctx.expression(0));
//        } else {
//            visit(ctx.expression(0));
//        }
//        ctx.expression(1).accept(this);
//        instructions.add("fappend");
//        if (!(ctx.getParent() instanceof ExprParser.FileAppendExpressionContext)) {
//            instructions.add("pop");
//        }
//        return null;
//    }
    
    @Override
    public Void visitFileAppendStatement(ExprParser.FileAppendStatementContext ctx) {
        String fileVar = ctx.VAR().getText();
        instructions.add("load " + fileVar);
        for (var expr : ctx.expression()) {
            visit(expr);
        }
        int totalArgs = 1 + ctx.expression().size();
        instructions.add("fappend " + totalArgs);
        return null;
    }


    @Override
    public Void visitExprStatement(ExprParser.ExprStatementContext ctx) {
        visit(ctx.expression());

        if (!firstFappend && fileVarName != null) {
            instructions.add("pop");
            firstFappend = true;
            fileVarName = null;
        }

        return null;
    }


    @Override
    public Void visitReadStatement(ExprParser.ReadStatementContext ctx) {
        for (var var : ctx.VAR()) {
            String name = var.getText();
            Type type = symbolTable.getType(name);
            if (type == Type.INT) instructions.add("read I");
            else if (type == Type.FLOAT) instructions.add("read F");
            else if (type == Type.STRING) instructions.add("read S");
            else if (type == Type.BOOL) instructions.add("read B");
            instructions.add("save " + name);
        }
        return null;
    }

    @Override
    public Void visitAritmExpression(ExprParser.AritmExpressionContext ctx) {
        Type leftType = inferType(ctx.expression(0));
        Type rightType = inferType(ctx.expression(1));

        visit(ctx.expression(0));
        if (leftType == Type.INT && rightType == Type.FLOAT) {
            instructions.add("swap");
            instructions.add("itof");
            instructions.add("swap");
        }

        visit(ctx.expression(1));
        if (leftType == Type.FLOAT && rightType == Type.INT) {
            instructions.add("itof");
        }

        switch (ctx.op.getText()) {
	        case "+" -> instructions.add(isInControlFlow ? "add I" : "add");
	        case "-" -> instructions.add(isInControlFlow ? "sub I" : "sub");
	        case "*" -> instructions.add(isInControlFlow ? "mul I" : "mul");
	        case "/" -> instructions.add(isInControlFlow ? "div I" : "div");
	    }


        return null;
    }

    @Override
    public Void visitModuloExpression(ExprParser.ModuloExpressionContext ctx) {
        visit(ctx.expression(0));
        visit(ctx.expression(1));
        instructions.add("mod");
        return null;
    }

    @Override
    public Void visitConcatExpression(ExprParser.ConcatExpressionContext ctx) {
        visit(ctx.expression(0));
        visit(ctx.expression(1));
        instructions.add("concat");
        return null;
    }

    @Override
    public Void visitComparisonExpression(ExprParser.ComparisonExpressionContext ctx) {
        visit(ctx.expression(0));
        visit(ctx.expression(1));

        switch (ctx.op.getText()) {
            case "==" -> instructions.add("eq");
            case "!=" -> {
                instructions.add("eq");
                instructions.add("not");
            }
        }
        return null;
    }

    @Override
    public Void visitRelationExpression(ExprParser.RelationExpressionContext ctx) {
        Type leftType = inferType(ctx.expression(0));
        Type rightType = inferType(ctx.expression(1));

        visit(ctx.expression(0));
        if (leftType == Type.INT && rightType == Type.FLOAT) instructions.add("itof");

        visit(ctx.expression(1));
        if (leftType == Type.FLOAT && rightType == Type.INT) instructions.add("itof");

        addRelationalInstruction(ctx.op.getText());
        return null;
    }

    @Override
    public Void visitLogicExpression(ExprParser.LogicExpressionContext ctx) {
        visit(ctx.expression(0));
        visit(ctx.expression(1));
        switch (ctx.op.getText()) {
            case "&&" -> instructions.add("and");
            case "||" -> instructions.add("or");
        }
        return null;
    }

    @Override
    public Void visitNegExpression(ExprParser.NegExpressionContext ctx) {
        visit(ctx.expression());
        instructions.add("uminus");
        return null;
    }

    @Override
    public Void visitNotExpression(ExprParser.NotExpressionContext ctx) {
        visit(ctx.expression());
        instructions.add("not");
        return null;
    }

    @Override
    public Void visitParenExpression(ExprParser.ParenExpressionContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Void visitIfStatement(ExprParser.IfStatementContext ctx) {
        String elseLabel = generateNumericLabel();
        String endLabel = generateNumericLabel();

        isInControlFlow = true;
        visit(ctx.condition());
        isInControlFlow = false;

        instructions.add("fjmp " + elseLabel);
        visit(ctx.statement(0));
        instructions.add("jmp " + endLabel);
        instructions.add("label " + elseLabel);
        if (ctx.statement().size() > 1) visit(ctx.statement(1));
        instructions.add("label " + endLabel);
        return null;
    }

    @Override
    public Void visitWhileStatement(ExprParser.WhileStatementContext ctx) {
        String beginLabel = generateNumericLabel();
        String endLabel = generateNumericLabel();

        instructions.add("label " + beginLabel);
        isInControlFlow = true;
        visit(ctx.condition());
        isInControlFlow = false;

        instructions.add("fjmp " + endLabel);
        visit(ctx.statement());
        instructions.add("jmp " + beginLabel);
        instructions.add("label " + endLabel);
        return null;
    }
}