import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        String fileName = "input.txt";

        try {
            String input = Files.readString(Paths.get(fileName));

            CharStream charStream = CharStreams.fromString(input);
            ExprLexer lexer = new ExprLexer(charStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            ExprParser parser = new ExprParser(tokens);

            ParseTree tree = parser.program();
           // System.out.println(tree.toStringTree(parser));

            //type checking
            TypeCheckingVisitor typeChecker = new TypeCheckingVisitor();
            typeChecker.visit(tree);

            if (!typeChecker.hasErrors()) {
                EvalVisitor evaluator = new EvalVisitor();
                evaluator.visit(tree);

                //instruction
                CodeGeneratorVisitor generator = new CodeGeneratorVisitor();
                generator.visit(tree);
                List<String> code = generator.getInstructions();

                //saving of bite code
                Files.write(Paths.get("program.out"), code);


            } else {
                System.err.println("Type checking failed. Program not executed.");
            }

        } catch (IOException e) {
            System.err.println("File error: " + e.getMessage());
        } catch (RecognitionException e) {
            System.err.println("Syntax error: " + e.getMessage());
        }
    }
}