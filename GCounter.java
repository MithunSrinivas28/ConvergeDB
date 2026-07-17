import java.util.HashMap;
import java.util.Map;

/**
 * G-Counter: a Grow-only Counter CRDT.
 *
 * The rule: each replica can ONLY increment its own slot in the map.
 * It never touches another replica's slot directly.
 *
 * Merging two G-Counters = element-wise MAX of every replica's slot.
 * This merge function is:
 *   - Commutative:  merge(A,B) == merge(B,A)
 *   - Associative:  merge(merge(A,B),C) == merge(A,merge(B,C))
 *   - Idempotent:   merge(A,A) == A
 *
 * Those three properties guarantee that no matter what order replicas
 * sync in, or whether a message gets duplicated by the network, every
 * replica converges to the exact same final value.
 */
public class GCounter {
    private final String replicaId;
    private final Map<String, Integer> counts;

    public GCounter(String replicaId) {
        this.replicaId = replicaId;
        this.counts = new HashMap<>();
        this.counts.put(replicaId, 0);
    }

    // Private constructor used internally when building a new merged counter.
    private GCounter(String replicaId, Map<String, Integer> counts) {
        this.replicaId = replicaId;
        this.counts = counts;
    }

    // Increment ONLY this replica's own slot. This is the core rule of a G-Counter --
    // it's what makes concurrent increments from different replicas safe by construction.
    public void increment() {
        counts.merge(replicaId, 1, Integer::sum);
    }

    // The "true" value is just the sum of every replica's slot.
    public int value() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    // The merge function -- take the element-wise MAX across every known replica slot.
    public GCounter merge(GCounter other) {
        Map<String, Integer> mergedCounts = new HashMap<>(this.counts);
        for (Map.Entry<String, Integer> entry : other.counts.entrySet()) {
            mergedCounts.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        return new GCounter(this.replicaId, mergedCounts);
    }

    public Map<String, Integer> getState() {
        return new HashMap<>(counts);
    }

    @Override
    public String toString() {
        return replicaId + counts + " -> value=" + value();
    }
}