public class LWWRegister<T> {
    private final String replicaId;
    private final T value;
    private final long timestamp;

    public LWWRegister(String replicaId, T initialValue) {
        this.replicaId = replicaId;
        this.value = initialValue;
        this.timestamp = System.currentTimeMillis();
    }

    private LWWRegister(String replicaId, T value, long timestamp) {
        this.replicaId = replicaId;
        this.value = value;
        this.timestamp = timestamp;
    }

    public LWWRegister<T> set(T newValue) {
        return new LWWRegister<>(this.replicaId, newValue, System.currentTimeMillis());
    }

    public T getValue() {
        return value;
    }

    public LWWRegister<T> merge(LWWRegister<T> other) {
        if (this.timestamp > other.timestamp)
            return this;
        if (other.timestamp > this.timestamp)
            return other;
        return this.replicaId.compareTo(other.replicaId) >= 0 ? this : other;
    }

    @Override
    public String toString() {
        return "value=" + value + " (ts=" + timestamp + ", from=" + replicaId + ")";
    }
}