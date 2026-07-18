import java.util.HashSet;
import java.util.Set;

public class GSet<T> {
    private final Set<T> elements;

    public GSet() {
        this.elements = new HashSet<>();
    }

    private GSet(Set<T> elements) {
        this.elements = elements;
    }

    public void add(T element) {
        elements.add(element);
    }

    public boolean contains(T element) {
        return elements.contains(element);
    }

    public GSet<T> merge(GSet<T> other) {
        Set<T> merged = new HashSet<>(this.elements);
        merged.addAll(other.elements);
        return new GSet<>(merged);
    }

    @Override
    public String toString() {
        return elements.toString();
    }
}