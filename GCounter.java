import java.util.HashMap;
import java.util.Map;

public class GCounter {
    private final String replicaId;
    private final Map<String, Integer> counts;

    public GCounter(String replicaId) {
        this.replicaId = replicaId;
        this.counts = new HashMap<>();
        this.counts.put(replicaId, 0);
    }

    private GCounter(String replicaId, Map<String, Integer> counts) {
        this.replicaId = replicaId;
        this.counts = counts;
    }

    public void increment() {
        counts.merge(replicaId, 1, Integer::sum);
    }

    public int value() {
        int sum = 0;
        for (int v : counts.values())
            sum += v;
        return sum;
    }

    public GCounter merge(GCounter other) {
        Map<String, Integer> merged = new HashMap<>(this.counts);
        for (Map.Entry<String, Integer> e : other.counts.entrySet())
            merged.merge(e.getKey(), e.getValue(), Math::max);
        return new GCounter(this.replicaId, merged);
    }

    public Map<String, Integer> getState() {
        return new HashMap<>(counts);
    }

    @Override
    public String toString() {
        return replicaId + counts + " -> value=" + value();
    }
}