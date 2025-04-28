import java.util.*;

public class ErrorReporter {
    private final List<String> errors = new ArrayList<>();

    public void report(int line, int charPositionInLine, String message) {
        String formatted = String.format("%d:%d - %s", line, charPositionInLine, message);
        errors.add(formatted);
        System.out.println(formatted);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }
}