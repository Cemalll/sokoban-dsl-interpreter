import java.util.ArrayList;
import java.util.List;

public class AstNode {

    private final String name;
    private final String value;
    private final List<AstNode> children;

    public AstNode(String name) {
        this(name, null);
    }

    public AstNode(String name, String value) {
        this.name = name;
        this.value = value;
        this.children = new ArrayList<>();
    }

    public void addChild(AstNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    public List<AstNode> getChildren() {
        return children;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public void print() {
        print(0);
    }

    private void print(int indent) {
        for (int i = 0; i < indent; i++) {
            System.out.print("  ");
        }

        if (value == null) {
            System.out.println(name);
        } else {
            System.out.println(name + "(" + value + ")");
        }

        for (AstNode child : children) {
            child.print(indent + 1);
        }
    }
}