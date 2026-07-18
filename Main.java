public class Main {
    public static void main(String[] args) {
        GCounter a = new GCounter("A");
        GCounter b = new GCounter("B");
        GCounter c = new GCounter("C");

        for (int i = 0; i < 3; i++) a.increment();
        for (int i = 0; i < 2; i++) b.increment();
        for (int i = 0; i < 4; i++) c.increment();

        System.out.println("Before any sync:");
        System.out.println(a);
        System.out.println(b);
        System.out.println(c);

        GCounter aPath = a.merge(b).merge(c);
        GCounter cPath = c.merge(a).merge(b);
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