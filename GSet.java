import java.util.HashSet;
import java.util.Set;

/**
 * G-Set: a Grow-only Set CRDT.
 *
 * Elements can be added but never removed.
 * Merging two G-Sets = set union.
 */
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