import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ORSet<T> {
    private record TaggedElement<E>(E element, String tag) {}

    private final Set<TaggedElement<T>> added;
    private final Set<TaggedElement<T>> removed;

    public ORSet() {
        this.added = new HashSet<>();
        this.removed = new HashSet<>();
    }

    private ORSet(Set<TaggedElement<T>> added, Set<TaggedElement<T>> removed) {
        this.added = added;
        this.removed = removed;
    }

    public void add(T element) {
        added.add(new TaggedElement<>(element, UUID.randomUUID().toString()));
    }

    public void remove(T element) {
        for (TaggedElement<T> t : added) {
            if (t.element().equals(element))
                removed.add(t);
        }
    }

    public boolean contains(T element) {
        for (TaggedElement<T> t : added) {
            if (t.element().equals(element) && !removed.contains(t))
                return true;
        }
        return false;
    }

    public ORSet<T> merge(ORSet<T> other) {
        Set<TaggedElement<T>> mergedAdded = new HashSet<>(this.added);
        mergedAdded.addAll(other.added);
        Set<TaggedElement<T>> mergedRemoved = new HashSet<>(this.removed);
        mergedRemoved.addAll(other.removed);
        return new ORSet<>(mergedAdded, mergedRemoved);
    }
}