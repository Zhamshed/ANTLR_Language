import java.util.*;
import java.io.*;

public class VirtualMachine {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Integer> labels = new HashMap<>();
    private final Stack<Object> stack = new Stack<>();
    private final List<String> program;
    private final Scanner inputScanner;
    private int ip = 0;
    private final Map<String, BufferedWriter> fileHandles = new HashMap<>();

    public VirtualMachine(List<String> program, Scanner inputScanner) {
        this.program = program;
        this.inputScanner = inputScanner;

        for (int i = 0; i < program.size(); i++) {
            String line = program.get(i);
            if (line.startsWith("label ")) {
                String label = line.substring(6).trim();
                labels.put(label, i);
            }
        }
    }

    public void run() {
        while (ip < program.size()) {
            String line = program.get(ip++).trim();
            if (line.isEmpty() || line.startsWith("label")) continue;

            String[] parts = line.split(" ", 3);
            String instr = parts[0];
            //System.out.println(">> " + line);

            switch (instr.toLowerCase()) {
                case "push" -> push(parts[1], parts[2]);
                case "load" -> stack.push(variables.get(parts[1]));
                case "save" -> {
                    if (!stack.isEmpty()) {
                        variables.put(parts[1], stack.pop());
                    } else {
                        System.out.println("Warning: Tried to save from empty stack to variable " + parts[1] + " at instruction " + (ip - 1));
                        variables.put(parts[1], 0); 
                    }
                }
                case "read" -> {
                    String type = parts[1]; 
                    Object value = readInput(type);
                    System.out.println(">> read " + value + " as " + type); 
                    stack.push(value);
                }
                case "print" -> print(parts[1]);
                case "pop" -> {
                    if (!stack.isEmpty()) {
                        stack.pop();
                    } else {
                        System.err.println("Warning: Tried to pop from an empty stack at instruction " + (ip - 1));
                    }
                }
                case "swap" -> {
                    Object a = stack.pop();
                    Object b = stack.pop();
                    stack.push(a);
                    stack.push(b);
                }
                case "add", "addi" -> binOp((a, b) -> a + b);
                case "sub", "subi" -> binOp((a, b) -> a - b);
                case "mul", "muli" -> binOp((a, b) -> a * b);
                case "div", "divi" -> {
                    Object bVal = stack.pop();
                    Object aVal = stack.pop();
                    if (aVal instanceof Integer && bVal instanceof Integer) {
                        stack.push((int) ((int) aVal / (int) bVal));
                    } else {
                        stack.push(toDouble(aVal) / toDouble(bVal));
                    }
                }
                case "mod" -> {
                    int b = (int) stack.pop();
                    int a = (int) stack.pop();
                    stack.push(a % b);
                }
                case "uminus" -> {
                    Object val = stack.pop();
                    if (val instanceof Integer i) stack.push(-i);
                    else if (val instanceof Double d) stack.push(-d);
                    else throw new RuntimeException("Cannot apply - to " + val);
                }
                case "itof" -> {
                    Object val = stack.pop();
                    if (val instanceof Integer i) stack.push(i.doubleValue());
                    else stack.push(val); //already float
                }
                case "concat" -> {
                    String b = stack.pop().toString();
                    String a = stack.pop().toString();
                    stack.push(a + b);
                }
                case "eq" -> {
                    Object b = stack.pop();
                    Object a = stack.pop();
                    stack.push(Objects.equals(a, b));
                }
                case "neq" -> {
                    Object b = stack.pop();
                    Object a = stack.pop();
                    stack.push(!Objects.equals(a, b));
                }
                case "lt", "lti" -> compare((a, b) -> a < b);
                case "gt", "gti" -> compare((a, b) -> a > b);
                case "and" -> {
                    boolean b = (boolean) stack.pop();
                    boolean a = (boolean) stack.pop();
                    stack.push(a && b);
                }
                case "or" -> {
                    boolean b = (boolean) stack.pop();
                    boolean a = (boolean) stack.pop();
                    stack.push(a || b);
                }
                case "not" -> stack.push(!(boolean) stack.pop());
                case "jmp" -> ip = labels.get(parts[1]);
                case "fjmp" -> {
                    boolean cond = (boolean) stack.pop();
                    if (!cond) ip = labels.get(parts[1]);
                }
                case "fopen" -> {
                    if (stack.isEmpty()) {
                        throw new RuntimeException("Stack is empty, expected filename on top before fopen");
                    }
                    Object filenameObj = stack.pop();
                    if (!(filenameObj instanceof String filename)) {
                        throw new RuntimeException("Expected filename (string) on stack before fopen, got: " + filenameObj);
                    }
                    try {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
                        stack.push(writer);
                    } catch (IOException e) {
                        throw new RuntimeException("Cannot open file: " + filename, e);
                    }
                }
                case "fclose" -> {
                    Object fileObj = stack.pop();
                    if (fileObj instanceof BufferedWriter writer) {
                        try {
                            writer.close();
                        } catch (IOException e) {
                            throw new RuntimeException("Error closing file", e);
                        }
                    } else {
                        throw new RuntimeException("Expected file object to close, but got: " + fileObj);
                    }
                }               
                case "fappend" -> {
                    if (parts.length < 2) {
                        throw new RuntimeException("Missing argument for fappend: expected count");
                    }
                    int count = Integer.parseInt(parts[1]);
                    if (stack.size() < count) {
                        throw new RuntimeException("Not enough elements on stack for fappend " + count);
                    }
                    List<Object> args = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        args.add(0, stack.pop());
                    }
                    Object fileObj = args.get(0);
                    if (!(fileObj instanceof BufferedWriter writer)) {
                        throw new RuntimeException("Expected file object as first argument to fappend");
                    }
                    for (int i = 1; i < args.size(); i++) {
                        try {
                            writer.write(args.get(i).toString());
                        } catch (IOException e) {
                            throw new RuntimeException("Error writing to file", e);
                        }
                    }
                    try {
                        writer.flush();
                    } catch (IOException e) {
                        throw new RuntimeException("Error flushing file", e);
                    }
                    stack.push(writer); 
                }

                default -> throw new RuntimeException("Unknown instruction: " + instr);
            }
        }
    }

    private void push(String type, String value) {
        switch (type) {
            case "I" -> stack.push(Integer.parseInt(value));
            case "F" -> stack.push(Double.parseDouble(value));
            case "S" -> stack.push(value.replaceAll("^\"|\"$", ""));
            case "B" -> stack.push(Boolean.parseBoolean(value));
            default -> throw new RuntimeException("Unknown push type: " + type);
        }
    }

    private Object readInput(String type) {
        return switch (type) {
            case "I" -> inputScanner.nextInt();
            case "F" -> inputScanner.nextDouble();
            case "S" -> inputScanner.next();
            case "B" -> inputScanner.nextBoolean();
            default -> throw new RuntimeException("Unknown read type: " + type);
        };
    }

    private void print(String arg) {
        if (arg.matches("\\d+")) {
            int count = Integer.parseInt(arg);
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Object value = stack.pop();
                String text;
                if (value instanceof Double d) {
                    text = (d % 1 == 0) ? Integer.toString(d.intValue()) : Double.toString(d);
                } else {
                    text = value.toString();
                }
                parts.add(0, text);
            }
            String output = String.join(" ", parts);
            if (!output.matches(".*: .*")) {
                output = output.replaceAll("=", " = ")
                               .replaceAll(",", " , ")
                               .replaceAll("\\s+", " ").trim();
            }
            System.out.println(output);
        } else {
            switch (arg.toUpperCase()) {
                case "I", "F", "S", "B" -> {
                    if (!stack.isEmpty()) {
                        Object value = stack.peek();
                        System.out.println(value.toString());
                    } else {
                        System.err.println("Warning: Tried to print from an empty stack.");
                    }
                }
                default -> throw new RuntimeException("Unknown print type or invalid argument: " + arg);
            }
        }
    }




    private void binOp(java.util.function.BiFunction<Double, Double, Double> op) {
        Object bVal = stack.pop();
        Object aVal = stack.pop();

        double a = toDouble(aVal);
        double b = toDouble(bVal);
        double result = op.apply(a, b);

        if (aVal instanceof Integer && bVal instanceof Integer && result % 1 == 0) {
            stack.push((int) result);
        } else {
            stack.push(result);
        }
    }

    private void compare(java.util.function.BiPredicate<Double, Double> predicate) {
        double b = toDouble(stack.pop());
        double a = toDouble(stack.pop());
        stack.push(predicate.test(a, b));
    }

    private double toDouble(Object val) {
        if (val instanceof Integer) return ((Integer) val).doubleValue();
        if (val instanceof Double) return (Double) val;
        throw new RuntimeException("Expected number but got: " + val);
    }
}
