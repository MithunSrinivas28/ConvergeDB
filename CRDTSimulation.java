public class CRDTSimulation {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("         CRDT Convergence Simulation");
        System.out.println("=".repeat(60));

        testGCounter();
        testPNCounter();
        testGSet();
        testTwoPSet();
        testLWWRegister();
        testORSet();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Results: " + passed + " passed, " + failed + " failed");
        System.out.println("=".repeat(60));
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + label);
            passed++;
        } else {
            System.out.println("  [FAIL] " + label);
            failed++;
        }
    }

    private static void testGCounter() {
        System.out.println("\n--- GCounter ---");

        GCounter r1 = new GCounter("R1");
        GCounter r2 = new GCounter("R2");
        GCounter r3 = new GCounter("R3");

        // Phase 1: concurrent increments during network partition
        System.out.println("Phase 1: Network partition - each replica increments independently");
        for (int i = 0; i < 5; i++) r1.increment();
        for (int i = 0; i < 3; i++) r2.increment();
        for (int i = 0; i < 7; i++) r3.increment();

        System.out.println("  R1 sees: " + r1.value());
        System.out.println("  R2 sees: " + r2.value());
        System.out.println("  R3 sees: " + r3.value());

        // Phase 2: reconnect and merge
        System.out.println("Phase 2: Replicas reconnect and sync");
        GCounter m1 = r1.merge(r2).merge(r3);
        GCounter m2 = r2.merge(r3).merge(r1);
        GCounter m3 = r3.merge(r1).merge(r2);

        System.out.println("  R1 sees: " + m1.value());
        System.out.println("  R2 sees: " + m2.value());
        System.out.println("  R3 sees: " + m3.value());

        check("All replicas converge to 15", m1.value() == 15 && m2.value() == 15 && m3.value() == 15);
        check("Merge is idempotent", m1.merge(m1).value() == 15);
    }

    private static void testPNCounter() {
        System.out.println("\n--- PNCounter ---");

        PNCounter r1 = new PNCounter("R1");
        PNCounter r2 = new PNCounter("R2");
        PNCounter r3 = new PNCounter("R3");

        System.out.println("Phase 1: Network partition - concurrent increments and decrements");
        for (int i = 0; i < 10; i++) r1.increment();
        for (int i = 0; i < 3; i++) r1.decrement();

        for (int i = 0; i < 4; i++) r2.increment();
        for (int i = 0; i < 6; i++) r2.decrement();

        for (int i = 0; i < 8; i++) r3.increment();
        for (int i = 0; i < 1; i++) r3.decrement();

        System.out.println("  R1 sees: " + r1.value() + " (10 inc, 3 dec)");
        System.out.println("  R2 sees: " + r2.value() + " (4 inc, 6 dec)");
        System.out.println("  R3 sees: " + r3.value() + " (8 inc, 1 dec)");

        System.out.println("Phase 2: Replicas reconnect and sync");
        PNCounter m1 = r1.merge(r2).merge(r3);
        PNCounter m2 = r2.merge(r3).merge(r1);
        PNCounter m3 = r3.merge(r1).merge(r2);

        System.out.println("  R1 sees: " + m1);
        System.out.println("  R2 sees: " + m2);
        System.out.println("  R3 sees: " + m3);

        int expected = (10 + 4 + 8) - (3 + 6 + 1);
        check("All replicas converge to " + expected, m1.value() == expected && m2.value() == expected && m3.value() == expected);
    }

    private static void testGSet() {
        System.out.println("\n--- GSet ---");

        GSet<String> r1 = new GSet<>();
        GSet<String> r2 = new GSet<>();
        GSet<String> r3 = new GSet<>();

        System.out.println("Phase 1: Network partition - each replica adds different items");
        r1.add("alice");
        r1.add("bob");

        r2.add("bob");
        r2.add("charlie");

        r3.add("diana");
        r3.add("alice");

        System.out.println("  R1: " + r1);
        System.out.println("  R2: " + r2);
        System.out.println("  R3: " + r3);

        System.out.println("Phase 2: Replicas reconnect and sync");
        GSet<String> m1 = r1.merge(r2).merge(r3);
        GSet<String> m2 = r2.merge(r3).merge(r1);
        GSet<String> m3 = r3.merge(r1).merge(r2);

        System.out.println("  R1: " + m1);
        System.out.println("  R2: " + m2);
        System.out.println("  R3: " + m3);

        check("All replicas contain alice", m1.contains("alice") && m2.contains("alice") && m3.contains("alice"));
        check("All replicas contain bob", m1.contains("bob") && m2.contains("bob") && m3.contains("bob"));
        check("All replicas contain charlie", m1.contains("charlie") && m2.contains("charlie") && m3.contains("charlie"));
        check("All replicas contain diana", m1.contains("diana") && m2.contains("diana") && m3.contains("diana"));
    }

    private static void testTwoPSet() {
        System.out.println("\n--- TwoPSet ---");

        TwoPSet<String> r1 = new TwoPSet<>();
        TwoPSet<String> r2 = new TwoPSet<>();
        TwoPSet<String> r3 = new TwoPSet<>();

        System.out.println("Phase 1: Network partition - adds and removes on different replicas");
        r1.add("x");
        r1.add("y");
        r1.add("z");
        r1.remove("y");

        r2.add("x");
        r2.add("w");

        r3.add("z");
        r3.add("v");
        r3.remove("z");

        System.out.println("  R1 contains x=" + r1.contains("x") + " y=" + r1.contains("y") + " z=" + r1.contains("z"));
        System.out.println("  R2 contains x=" + r2.contains("x") + " w=" + r2.contains("w"));
        System.out.println("  R3 contains z=" + r3.contains("z") + " v=" + r3.contains("v"));

        System.out.println("Phase 2: Replicas reconnect and sync");
        TwoPSet<String> m1 = r1.merge(r2).merge(r3);
        TwoPSet<String> m2 = r2.merge(r3).merge(r1);
        TwoPSet<String> m3 = r3.merge(r1).merge(r2);

        System.out.println("  After merge:");
        System.out.println("  x present: " + m1.contains("x") + ", " + m2.contains("x") + ", " + m3.contains("x"));
        System.out.println("  y present: " + m1.contains("y") + ", " + m2.contains("y") + ", " + m3.contains("y"));
        System.out.println("  z present: " + m1.contains("z") + ", " + m2.contains("z") + ", " + m3.contains("z"));
        System.out.println("  w present: " + m1.contains("w") + ", " + m2.contains("w") + ", " + m3.contains("w"));
        System.out.println("  v present: " + m1.contains("v") + ", " + m2.contains("v") + ", " + m3.contains("v"));

        check("x is present (added, never removed)", m1.contains("x") && m2.contains("x") && m3.contains("x"));
        check("y is absent (removed by R1)", !m1.contains("y") && !m2.contains("y") && !m3.contains("y"));
        check("z is absent (removed by R3)", !m1.contains("z") && !m2.contains("z") && !m3.contains("z"));
        check("w is present", m1.contains("w") && m2.contains("w") && m3.contains("w"));
        check("v is present", m1.contains("v") && m2.contains("v") && m3.contains("v"));
    }

    private static void testLWWRegister() {
        System.out.println("\n--- LWWRegister ---");

        LWWRegister<String> r1 = new LWWRegister<>("R1", "init");
        LWWRegister<String> r2 = new LWWRegister<>("R2", "init");
        LWWRegister<String> r3 = new LWWRegister<>("R3", "init");

        System.out.println("Phase 1: Network partition - each replica writes a different value");
        r1 = r1.set("from-R1");
        sleep(15);
        r2 = r2.set("from-R2");
        sleep(15);
        r3 = r3.set("from-R3");

        System.out.println("  R1: " + r1);
        System.out.println("  R2: " + r2);
        System.out.println("  R3: " + r3);

        System.out.println("Phase 2: Replicas reconnect and sync (last write wins)");
        LWWRegister<String> m1 = r1.merge(r2).merge(r3);
        LWWRegister<String> m2 = r2.merge(r3).merge(r1);
        LWWRegister<String> m3 = r3.merge(r1).merge(r2);

        System.out.println("  R1: " + m1);
        System.out.println("  R2: " + m2);
        System.out.println("  R3: " + m3);

        check("All replicas agree on the same value",
                m1.getValue().equals(m2.getValue()) && m2.getValue().equals(m3.getValue()));
        check("Winner is from-R3 (latest write)", m1.getValue().equals("from-R3"));
    }

    private static void testORSet() {
        System.out.println("\n--- ORSet ---");

        ORSet<String> r1 = new ORSet<>();
        ORSet<String> r2 = new ORSet<>();
        ORSet<String> r3 = new ORSet<>();

        System.out.println("Phase 1: Network partition - concurrent add/remove");
        r1.add("apple");
        r1.add("banana");
        r1.remove("banana");

        r2.add("banana");
        r2.add("cherry");

        r3.add("apple");
        r3.add("date");

        System.out.println("  R1: apple=" + r1.contains("apple") + " banana=" + r1.contains("banana"));
        System.out.println("  R2: banana=" + r2.contains("banana") + " cherry=" + r2.contains("cherry"));
        System.out.println("  R3: apple=" + r3.contains("apple") + " date=" + r3.contains("date"));

        System.out.println("Phase 2: Replicas reconnect and sync");
        ORSet<String> m1 = r1.merge(r2).merge(r3);
        ORSet<String> m2 = r2.merge(r3).merge(r1);
        ORSet<String> m3 = r3.merge(r1).merge(r2);

        System.out.println("  After merge:");
        System.out.println("  apple:  " + m1.contains("apple") + ", " + m2.contains("apple") + ", " + m3.contains("apple"));
        System.out.println("  banana: " + m1.contains("banana") + ", " + m2.contains("banana") + ", " + m3.contains("banana"));
        System.out.println("  cherry: " + m1.contains("cherry") + ", " + m2.contains("cherry") + ", " + m3.contains("cherry"));
        System.out.println("  date:   " + m1.contains("date") + ", " + m2.contains("date") + ", " + m3.contains("date"));

        check("apple present on all (add wins over unrelated remove)", m1.contains("apple") && m2.contains("apple") && m3.contains("apple"));
        check("banana present on all (R2's add is independent of R1's remove)", m1.contains("banana") && m2.contains("banana") && m3.contains("banana"));
        check("cherry present on all", m1.contains("cherry") && m2.contains("cherry") && m3.contains("cherry"));
        check("date present on all", m1.contains("date") && m2.contains("date") && m3.contains("date"));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
