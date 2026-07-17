public class PNCounter {
    private final GCounter increments;
    private final GCounter decrements;

    public PNCounter(String replicaId) {
        this.increments = new GCounter(replicaId);
        this.decrements = new GCounter(replicaId);
    }

    private PNCounter(GCounter increments, GCounter decrements) {
        this.increments = increments;
        this.decrements = decrements;
    }

    public void increment() {
        increments.increment();
    }

    // Note: decrementing INCREMENTS the decrements-counter. We never subtract directly.
    public void decrement() {
        decrements.increment();
    }

    public int value() {
        return increments.value() - decrements.value();
    }

    public PNCounter merge(PNCounter other) {
        return new PNCounter(this.increments.merge(other.increments), this.decrements.merge(other.decrements));
    }

    @Override
    public String toString() {
        return "value=" + value() + " (P=" + increments.value() + ", N=" + decrements.value() + ")";
    }
}