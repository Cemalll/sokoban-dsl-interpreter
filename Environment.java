import java.util.HashMap;
import java.util.Map;

public class Environment {

    private final Environment outer;
    private final Map<String, Object> values = new HashMap<>();

    public Environment() {
        this.outer = null;
    }

    public Environment(Environment outer) {
        this.outer = outer;
    }

    public void define(String name, Object value) {
        values.put(name, value);
    }

    public Object get(Token token, String name) {
        if (values.containsKey(name)) {
            return values.get(name);
        }

        if (outer != null) {
            return outer.get(token, name);
        }

        throw new Errors.UndefinedVariableException(token, name);
    }

    public void assign(Token token, String name, Object value) {
        if (values.containsKey(name)) {
            values.put(name, value);
            return;
        }

        if (outer != null) {
            outer.assign(token, name, value);
            return;
        }

        throw new Errors.UndefinedVariableException(token, name);
    }
}