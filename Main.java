public class Main {
    public static void main(String[] args) {
        // Three independent replicas -- imagine three servers in different
        // datacenters, or three phones editing offline during a network partition.
        GCounter a = new GCounter("A");
        GCounter b = new GCounter("B");
        GCounter c = new GCounter("C");

        // Each replica does its own local increments -- no coordination, no locks,
        // no waiting for the network. This is the whole point of a CRDT.
        for (int i = 0; i < 3; i++) a.increment(); // A does 3 increments
        for (int i = 0; i < 2; i++) b.increment(); // B does 2 increments
        for (int i = 0; i < 4; i++) c.increment(); // C does 4 increments

        System.out.println("Before any sync:");
        System.out.println(a);
        System.out.println(b);
        System.out.println(c);

        // Scenario 1: A syncs with B first, then with C.
        GCounter aPath = a.merge(b).merge(c);

        // Scenario 2: C syncs with A first, then with B -- a DIFFERENT order.
        GCounter cPath = c.merge(a).merge(b);

        // Scenario 3: B receives A's state TWICE -- simulating a network retry
        // that re-delivers a message that had actually already arrived.
        GCounter bPath = b.merge(a).merge(a).merge(c);

        System.out.println("\nAfter merging (different orders, one with a duplicate delivery):");
        System.out.println("A-path: " + aPath);
        System.out.println("C-path: " + cPath);
        System.out.println("B-path (duplicate merge of A): " + bPath);

        boolean converged = aPath.value() == cPath.value() && cPath.value() == bPath.value();
        System.out.println("\nAll three converged to the same value: " + converged);
        System.out.println("Final agreed value: " + aPath.value() + " (expected 3+2+4=9)");
    }
}