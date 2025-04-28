import java.util.HashMap;
import java.util.Map;

import java.util.*;

public class SymbolTable {
    private final Map<String, Type> table = new HashMap<>();

    public void declare(String name, Type type) {
        table.put(name, type);
    }

    public boolean isDeclared(String name) {
        return table.containsKey(name);
    }

    public Type getType(String name) {
        return table.getOrDefault(name, Type.INVALID);
    }

    public void assign(String name, Type type) {
        table.put(name, type);
    }
}