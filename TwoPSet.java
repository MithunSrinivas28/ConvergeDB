public class TwoPSet<T> {
    private final GSet<T> added;
    private final GSet<T> removed;

    public TwoPSet() {
        this.added = new GSet<>();
        this.removed = new GSet<>();
    }

    private TwoPSet(GSet<T> added, GSet<T> removed) {
        this.added = added;
        this.removed = removed;
    }

    public void add(T element) {
        added.add(element);
    }

    public void remove(T element) {
        // Can only tombstone something that was actually added.
        if (added.contains(element)) {
            removed.add(element);
        }
    }

    public boolean contains(T element) {
        return added.contains(element) && !removed.contains(element);
    }

    public TwoPSet<T> merge(TwoPSet<T> other) {
        return new TwoPSet<>(this.added.merge(other.added), this.removed.merge(other.removed));
    }
}