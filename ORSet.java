import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ORSet<T> {
    private record TaggedElement<E>(E element, String tag) {}

    private final Set<TaggedElement<T>> addedTags;
    private final Set<TaggedElement<T>> removedTags;

    public ORSet() {
        this.addedTags = new HashSet<>();
        this.removedTags = new HashSet<>();
    }

    private ORSet(Set<TaggedElement<T>> addedTags, Set<TaggedElement<T>> removedTags) {
        this.addedTags = addedTags;
        this.removedTags = removedTags;
    }

    // Every add gets a fresh, unique tag -- this is what lets "apple" be
    // re-added later even after an earlier "apple" instance was removed.
    public void add(T element) {
        addedTags.add(new TaggedElement<>(element, UUID.randomUUID().toString()));
    }

    // Only tombstones the tags we currently observe as live for this element --
    // NOT a permanent ban on the element itself.
    public void remove(T element) {
        for (TaggedElement<T> tagged : addedTags) {
            if (tagged.element().equals(element)) {
                removedTags.add(tagged);
            }
        }
    }

    public boolean contains(T element) {
        return addedTags.stream()
                .anyMatch(t -> t.element().equals(element) && !removedTags.contains(t));
    }

    public ORSet<T> merge(ORSet<T> other) {
        Set<TaggedElement<T>> mergedAdded = new HashSet<>(this.addedTags);
        mergedAdded.addAll(other.addedTags);
        Set<TaggedElement<T>> mergedRemoved = new HashSet<>(this.removedTags);
        mergedRemoved.addAll(other.removedTags);
        return new ORSet<>(mergedAdded, mergedRemoved);
    }
}