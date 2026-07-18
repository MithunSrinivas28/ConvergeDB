# Learning Notes — CRDTs from Scratch

These are your personal study notes. Read them like a conversation, not a textbook.

---

# Part 1: Understanding Every CRDT

Before we touch any Java code, let's build a deep understanding of what each CRDT is, why it exists, and what tradeoff it makes.

---

## G-Counter (Grow-only Counter)

### What problem does it solve?

You have three servers. Each one is counting page views. Users are hitting all three servers simultaneously. At the end of the day, you want the total count.

The naive approach is to have one shared counter that all three servers increment — but that means every single page view requires a network round-trip to a central counter. At scale, that central counter becomes a bottleneck. If it goes down, you stop counting entirely.

What you really want is for each server to count independently, and then combine the counts later.

### Why was it invented?

Because a plain integer doesn't merge well. If server A has the number 7 and server B has the number 5, what's the "merged" value? 12? 7? 5? You don't know, because a plain integer doesn't carry enough information to merge correctly. The G-Counter was designed to be a counter that carries enough context to always merge correctly.

### What's the core idea?

Instead of one number, keep a *map* — one slot per replica. Each replica only ever touches its own slot. To get the "real" count, sum all the slots.

```
Replica A: {A: 7, B: 0, C: 0}   → value = 7
Replica B: {A: 0, B: 5, C: 0}   → value = 5
Replica C: {A: 0, B: 0, C: 3}   → value = 3
```

No replica ever writes to another replica's slot. That's the rule. It's what makes the whole thing safe.

### How does the merge work?

Element-wise `max`. For every replica slot, take the larger value from the two states being merged.

```
merge({A: 7, B: 0, C: 0}, {A: 0, B: 5, C: 0})
  = {A: max(7,0), B: max(0,5), C: max(0,0)}
  = {A: 7, B: 5, C: 0}
  → value = 12
```

Why `max` and not `sum`? Because if a message gets delivered twice, `sum` would double-count. `max` is idempotent — applying it twice changes nothing. That's the entire point.

### What are its limitations?

It can only go up. You cannot decrement a G-Counter. If you need decrements, you need the next data structure (PN-Counter). This feels restrictive, but it's exactly this restriction that makes the merge so simple and bulletproof.

### Where is it used in real systems?

- **Cassandra** uses a variant of G-Counters for its distributed counter columns.
- **View counters, like counters, and impression trackers** — anywhere you're counting events across multiple nodes and only need the total.

---

## PN-Counter (Positive-Negative Counter)

### What problem does it solve?

The G-Counter can only go up. But sometimes you need a counter that can go both up and down. Think of an inventory system — items get added and removed. Or a voting system with upvotes and downvotes.

### Why was it invented?

Because subtracting from a G-Counter would break its merge. If replica A has `{A: 5}` and decrements to `{A: 4}`, then merges with an old copy `{A: 5}`, the `max` brings it right back to 5. The decrement gets silently lost. Subtraction and `max` don't play well together.

### What's the core idea?

Don't subtract. Instead, keep *two* G-Counters: one for all the increments (P), and one for all the decrements (N). The real value is always P − N.

Think of it like accounting. You don't erase transactions — you record debits and credits separately. The balance is credits minus debits.

### How does the merge work?

Merge the P counter using G-Counter merge. Merge the N counter using G-Counter merge. That's it. The PN-Counter inherits all of its merge safety from the G-Counter.

### What are its limitations?

The value can go negative — there's no built-in "floor." If your domain requires that a counter never goes below zero (like a stock quantity), you need additional application-level logic. The CRDT itself doesn't enforce that constraint.

### Where is it used in real systems?

- **Riak** uses PN-Counters for its distributed counters.
- **Upvote/downvote systems** where votes come in from different servers.
- **Shopping cart quantities** — add one, remove one.

---

## G-Set (Grow-only Set)

### What problem does it solve?

You have a set of data — say, a list of users who have signed up, or a list of tags applied to an item. Multiple replicas can add items to this set independently. You need them to eventually agree on the contents.

### Why was it invented?

Because a set that allows both add and remove is surprisingly hard to get right in a distributed setting. The G-Set takes the simplest possible approach: only allow adds. No removes, ever. This makes the merge trivial and the semantics crystal clear.

### What's the core idea?

It's a `HashSet` where you can only call `add`, never `remove`. That's literally it. The simplicity is the point.

### How does the merge work?

Set union. If A has `{apple, banana}` and B has `{banana, cherry}`, the merged result is `{apple, banana, cherry}`.

Union is commutative (`A ∪ B = B ∪ A`), associative (`(A ∪ B) ∪ C = A ∪ (B ∪ C)`), and idempotent (`A ∪ A = A`). You get all three CRDT properties for free from a single set operation.

### What are its limitations?

Once something is in the set, it's in the set forever. You can't remove it. If you accidentally add the wrong item, tough luck — there's no undo. If you need removals, you need a 2P-Set or an OR-Set.

### Where is it used in real systems?

- **Membership lists** — the set of nodes in a cluster (nodes join but don't leave).
- **Append-only logs** — events that happened, which by definition can't un-happen.
- **Tag systems** where tags are added to content but never removed.

---

## 2P-Set (Two-Phase Set)

### What problem does it solve?

The G-Set can't remove elements. The 2P-Set adds removal — but with a catch. Once you remove an element, it's gone forever. You can never add it back.

### Why was it invented?

Because adding removal to a distributed set is the beginning of a rabbit hole. The simplest way to do it is to keep a second set — a "tombstone set" — that records everything that has been removed. An element is considered "present" only if it's in the add-set and *not* in the tombstone set.

### What's the core idea?

Two G-Sets working together. One grows with adds. The other grows with removes. The visible contents at any moment is the difference: `added − removed`.

Think of it like a whitelist and a blacklist. You can put someone on the whitelist. You can later move them to the blacklist. But once they're on the blacklist, they can never come back.

### How does the merge work?

Merge each G-Set independently. The add-set gets merged (union), and the remove-set gets merged (union). This means if *any* replica removed an element, that removal propagates to every replica. Remove wins over add — this is called "remove-wins" semantics.

### What are its limitations?

The big one: **no re-add.** If you add "apple," then remove "apple," you can never add "apple" again. For many use cases, this is a dealbreaker. Imagine a shopping cart where you remove an item and then change your mind — with a 2P-Set, that item is permanently banned.

### Where is it used in real systems?

- Situations where removal is rare and permanent — like banning a user, revoking a certificate, or decommissioning a server from a cluster.
- In practice, most systems that need add-and-remove use the OR-Set instead. The 2P-Set is more of a stepping stone on the path to understanding the OR-Set.

---

## LWW-Register (Last-Writer-Wins Register)

### What problem does it solve?

You have a single value — a user's display name, a configuration setting, a profile picture URL. Multiple replicas might update it at the same time. You need a rule for which update wins.

### Why was it invented?

Because "just keep the latest value" sounds simple but is actually tricky in distributed systems. "Latest" requires agreement on time, and clocks on different machines are never perfectly synchronized. The LWW-Register defines a precise, deterministic rule for picking a winner that every replica can apply independently and arrive at the same answer.

### What's the core idea?

Every write is stamped with a timestamp. When you merge two registers, the one with the higher timestamp wins. If the timestamps are exactly equal (which happens more often than you'd think due to clock resolution), you break the tie using the replica ID — lexicographically higher ID wins.

The key insight is that the tie-breaking rule must be *deterministic and symmetric*. Both sides of the merge must pick the same winner. If replica A thinks A wins the tie, then replica B must *also* think A wins the tie. Otherwise they'd diverge.

### How does the merge work?

```
merge(R1, R2):
  if R1.timestamp > R2.timestamp → keep R1
  if R2.timestamp > R1.timestamp → keep R2
  if timestamps equal → keep the one with the higher replicaId
```

This is O(1) — no iteration, no maps, just a comparison.

### What are its limitations?

**It throws away the losing write.** If replica A sets the name to "Alice" and replica B sets it to "Bob" at almost the same time, one of them will be silently discarded. There's no way to detect that a conflict happened. For a display name, this is probably fine. For a bank balance, this would be catastrophic.

Also, **it depends on timestamps being reasonably accurate.** If one machine's clock is an hour ahead, its writes will always win, even if they happened earlier in "real" time. Production systems that use LWW typically use logical clocks (like Lamport clocks or hybrid logical clocks) instead of wall-clock time to mitigate this.

### Where is it used in real systems?

- **Cassandra** — every column in Cassandra is essentially an LWW-Register. The most recent write (by timestamp) wins.
- **DynamoDB** — uses LWW as one of its conflict resolution strategies.
- **Session state** — the latest user preference or setting wins.

---

## OR-Set (Observed-Remove Set)

### What problem does it solve?

The 2P-Set's fatal flaw: once you remove an element, it can never be added again. The OR-Set fixes this. You can add "apple," remove "apple," and add "apple" again. The final state correctly shows "apple" as present.

### Why was it invented?

Because real applications need real sets. Shopping carts, task lists, friend lists — users add and remove items all the time. The OR-Set was designed to handle concurrent add and remove of the same element in a way that feels intuitive: if one replica adds "apple" while another replica removes "apple," the add wins. This is called "add-wins" semantics, and it's usually what users expect.

### What's the core idea?

This is the cleverest CRDT in the project, so let's take it slowly.

The problem with the 2P-Set is that when you remove "apple," you're banning the *element* forever. The OR-Set's insight is: **don't remove the element. Remove specific *instances* of the element.**

Every time you add "apple," you also generate a globally unique tag (a UUID). So the internal state isn't `{apple}` — it's `{(apple, tag-1)}`. If you add "apple" again later, it becomes `{(apple, tag-1), (apple, tag-2)}`.

When you remove "apple," you don't tombstone all possible apples — you only tombstone the specific tags that *you can currently see*. If another replica concurrently added `(apple, tag-3)`, you've never seen `tag-3`, so your remove doesn't touch it. When you later merge with that replica, `tag-3` shows up in your add-set, isn't in your remove-set, so "apple" reappears.

This is why it's called "Observed-Remove" — you remove only what you've *observed*.

### How does the merge work?

Set union on both the add-tags set and the remove-tags set. An element is "present" if it has at least one tag in the add-set that is not in the remove-set.

### What are its limitations?

**Tombstone growth.** Every remove creates permanent tombstones. Over time, the remove-set grows without bound. Production OR-Sets use garbage collection or "causal context" optimizations to compact the tombstone set. Our educational implementation doesn't — and that's fine for learning.

**UUID overhead.** Every single add creates a new UUID. For a set with millions of adds, that's a lot of metadata.

### Where is it used in real systems?

- **Riak** — uses an optimized OR-Set variant (based on "dots" instead of UUIDs) for its distributed sets.
- **Automerge** — the CRDT library used in local-first software uses OR-Set semantics for its collection types.
- **Shopping carts** — the canonical example. Add to cart, remove from cart, add again — all with concurrent devices.

---

# Part 2: Understanding Every Java Class

Now let's look at the code. For each file, I'll explain why it exists and how it works — not line by line, but idea by idea.

---

## GCounter.java

### Why this file exists

This is the foundation. Every other counter-type CRDT in the project is built on top of this class. Get this one right, and the PN-Counter comes almost for free.

### What responsibility it has

It models a grow-only counter that can be independently incremented by multiple replicas and later merged to produce the correct total.

### The important fields

- **`replicaId`** (`String`) — Identifies which replica this counter belongs to. This determines which slot in the map this replica is allowed to increment.
- **`counts`** (`Map<String, Integer>`) — The heart of the CRDT. Maps each replica ID to that replica's local count. This is the "state" that gets exchanged during sync.

### The important methods

- **`increment()`** — Bumps this replica's own slot by 1. Uses `counts.merge(replicaId, 1, Integer::sum)`, which is a clean way of saying "add 1 to my slot, creating it if it doesn't exist."
- **`value()`** — Sums every slot. This is the query — the "real" counter value as seen from this replica.
- **`merge(GCounter other)`** — Creates a new GCounter with element-wise `max` across all slots. Returns a *new* instance — neither `this` nor `other` is mutated.
- **`getState()`** — Returns a defensive copy of the counts map. Useful for inspection without risking mutation.

### The core algorithm

The entire algorithm fits in one sentence: each replica increments its own slot, and merge takes the element-wise maximum. Everything else — the constructors, `toString`, `getState` — is scaffolding around this core.

### How it interacts with other classes

`PNCounter` contains two `GCounter` instances and delegates everything to them. `Main` creates three `GCounter` instances and demonstrates convergence.

---

## PNCounter.java

### Why this file exists

Because the G-Counter can't go down. This class adds decrement support by using the "two counters" trick — the most elegant pattern in this entire project.

### What responsibility it has

It models a counter that supports both increment and decrement, built entirely out of G-Counters.

### The important fields

- **`increments`** (`GCounter`) — The P counter. Tracks all increment operations.
- **`decrements`** (`GCounter`) — The N counter. Tracks all decrement operations.

Notice there's no `replicaId` field here. The replica identity is embedded inside each `GCounter`.

### The important methods

- **`increment()`** — Delegates to `increments.increment()`.
- **`decrement()`** — Calls `decrements.increment()`. Read that again — to decrement the PN-Counter, you *increment the decrement counter*. This is the key insight. We never subtract from a G-Counter.
- **`value()`** — Returns `increments.value() - decrements.value()`.
- **`merge(PNCounter other)`** — Merges each G-Counter independently and wraps the results in a new PNCounter.

### The core algorithm

This class is pure composition. It doesn't implement a new merge algorithm — it delegates entirely to `GCounter.merge()`. The CRDT properties hold because `GCounter` already satisfies them, and merging two independent CRDTs preserves those properties.

### How it interacts with other classes

Depends on `GCounter`. Not used by anything else currently (Main only demos GCounter).

---

## GSet.java

### Why this file exists

This is the set-theoretic equivalent of GCounter — the simplest possible set CRDT. It's also the building block that `TwoPSet` is built on, just like `GCounter` is the building block for `PNCounter`.

### What responsibility it has

It models a set that only grows. Elements can be added but never removed.

### The important fields

- **`elements`** (`Set<T>`) — A `HashSet` containing all elements ever added.

That's it. One field. This is the simplest CRDT in the project.

### The important methods

- **`add(T element)`** — Adds to the internal set. Standard `HashSet.add`.
- **`contains(T element)`** — Standard `HashSet.contains`.
- **`merge(GSet<T> other)`** — Creates a new `GSet` containing the union of both sets.

### The core algorithm

Merge is set union. There is genuinely nothing more to it.

### How it interacts with other classes

`TwoPSet` contains two `GSet` instances — one for adds, one for removes.

---

## TwoPSet.java

### Why this file exists

To show how you can add removal semantics to a G-Set using the same composition pattern that `PNCounter` uses for `GCounter`.

### What responsibility it has

It models a set that supports both add and remove, with the limitation that removes are permanent.

### The important fields

- **`added`** (`GSet<T>`) — Everything that has ever been added.
- **`removed`** (`GSet<T>`) — Everything that has ever been removed (the "tombstone" set).

### The important methods

- **`add(T element)`** — Delegates to `added.add(element)`.
- **`remove(T element)`** — Adds the element to `removed`, but *only if it's currently in `added`*. This guard prevents tombstoning something that was never added.
- **`contains(T element)`** — Returns `true` only if the element is in `added` AND not in `removed`.
- **`merge(TwoPSet<T> other)`** — Merges both G-Sets independently.

### The core algorithm

The visible set is always `added \ removed` (set difference). Since both `added` and `removed` are G-Sets that only grow, once an element enters `removed`, it can never leave. That's why removal is permanent.

### How it interacts with other classes

Depends on `GSet`. Uses the exact same composition pattern as `PNCounter` uses with `GCounter`.

---

## LWWRegister.java

### Why this file exists

Counters and sets are collections. But sometimes you just have a single value — one field, one setting. This class handles that case.

### What responsibility it has

It models a single value that can be updated by multiple replicas, with last-writer-wins conflict resolution.

### The important fields

- **`replicaId`** (`String`) — Who wrote this value. Used for tie-breaking.
- **`value`** (`T`) — The actual stored value.
- **`timestamp`** (`long`) — When this value was written. Drives the merge decision.

All three fields are `final`. This class is immutable — every update creates a new instance.

### The important methods

- **`set(T newValue)`** — Returns a *new* `LWWRegister` with the new value and a fresh timestamp. The old register is untouched.
- **`getValue()`** — Returns the current value.
- **`merge(LWWRegister<T> other)`** — Picks the register with the higher timestamp. On a tie, picks the one with the lexicographically higher `replicaId`.

### The core algorithm

The merge is a total order over `(timestamp, replicaId)` pairs. Higher timestamp wins. On equal timestamps, higher replicaId wins. This total order means there is always exactly one winner, and both sides of the merge agree on who it is.

### How it interacts with other classes

Standalone. Doesn't depend on or get used by any other CRDT class.

---

## ORSet.java

### Why this file exists

This is the most sophisticated CRDT in the project. It solves the 2P-Set's biggest limitation — the inability to re-add a removed element — using a clever tagging scheme.

### What responsibility it has

It models a set that supports add, remove, and re-add, with "add-wins" semantics for concurrent operations.

### The important fields

- **`addedTags`** (`Set<TaggedElement<T>>`) — Every add that has ever happened, each stamped with a unique UUID.
- **`removedTags`** (`Set<TaggedElement<T>>`) — The tombstoned tags. An element's *tag* is tombstoned, not the element itself.

There's also a private record:

- **`TaggedElement<E>`** — A pair of `(element, tag)`. Two `TaggedElement`s with the same element but different tags are *different entries*. This is the core of the whole design.

### The important methods

- **`add(T element)`** — Creates a new `TaggedElement` with the element and a fresh UUID, and adds it to `addedTags`. Every single add creates a unique entry, even if the same element has been added before.
- **`remove(T element)`** — Iterates over `addedTags`, finds every tag associated with this element, and moves those specific tags into `removedTags`. It only removes what it can *see* — hence "observed-remove."
- **`contains(T element)`** — Returns `true` if there exists *any* tag for this element in `addedTags` that is *not* in `removedTags`.
- **`merge(ORSet<T> other)`** — Union of both `addedTags` and union of both `removedTags`.

### The core algorithm

The magic is in the tagging. Let me walk you through why this works:

1. Replica A adds "apple" → `addedTags = {(apple, tag-1)}`.
2. Replica B also adds "apple" → `addedTags = {(apple, tag-2)}`.
3. Replica A removes "apple" → it sees `tag-1`, so `removedTags = {(apple, tag-1)}`.
4. Now A and B merge.
   - merged addedTags: `{(apple, tag-1), (apple, tag-2)}`
   - merged removedTags: `{(apple, tag-1)}`
   - `tag-1` is tombstoned. But `tag-2` is *not*. So "apple" is still present.

Replica A's remove only killed the instance of apple that A had seen. B's concurrent add (with a different tag) survived. This is exactly the behavior users expect.

### How it interacts with other classes

Standalone. The most complex CRDT, but it doesn't depend on `GSet` or any other class. It implements its own set logic using tagged elements.

---

# Part 3: The Complete Execution Flow

Here is exactly what happens from the moment `java Main` runs to the final output line.

## Phase 1 — Construction

```java
GCounter a = new GCounter("A");
GCounter b = new GCounter("B");
GCounter c = new GCounter("C");
```

Three G-Counters are created. Each one initializes its map with a single entry:

```
a.counts = {A: 0}
b.counts = {B: 0}
c.counts = {C: 0}
```

Each replica only knows about itself at this point. They have no knowledge of each other.

## Phase 2 — Independent Local Operations

```java
for (int i = 0; i < 3; i++) a.increment();   // A does 3
for (int i = 0; i < 2; i++) b.increment();   // B does 2
for (int i = 0; i < 4; i++) c.increment();   // C does 4
```

Each call to `increment()` uses `counts.merge(replicaId, 1, Integer::sum)` to bump the replica's own slot.

After this phase:

```
a.counts = {A: 3}       → value = 3
b.counts = {B: 2}       → value = 2
c.counts = {C: 4}       → value = 4
```

The system is now diverged. All three replicas have different values. None of them is "wrong" — each one is correct from its own perspective.

## Phase 3 — The Print Before Sync

```
Before any sync:
A{A=3} -> value=3
B{B=2} -> value=2
C{C=4} -> value=4
```

This proves the replicas are independent. No leaking of state between them.

## Phase 4 — Three Different Merge Paths

### Path 1: A merges B, then C

```java
GCounter aPath = a.merge(b).merge(c);
```

**Step 1:** `a.merge(b)`
```
{A: max(3,—), B: max(—,2)} = {A: 3, B: 2}   → value = 5
```
(The `—` means that key didn't exist in the other map. `merge` in HashMap skips it, so only the existing value carries through.)

**Step 2:** `{A:3, B:2}.merge(c)`
```
{A: max(3,—), B: max(2,—), C: max(—,4)} = {A: 3, B: 2, C: 4}   → value = 9
```

### Path 2: C merges A, then B

```java
GCounter cPath = c.merge(a).merge(b);
```

**Step 1:** `c.merge(a)` = `{A: 3, C: 4}` → value = 7
**Step 2:** `{A:3, C:4}.merge(b)` = `{A: 3, B: 2, C: 4}` → value = 9

### Path 3: B merges A, then A again, then C

```java
GCounter bPath = b.merge(a).merge(a).merge(c);
```

**Step 1:** `b.merge(a)` = `{A: 3, B: 2}` → value = 5
**Step 2:** `{A:3, B:2}.merge(a)` = `{A: max(3,3), B: max(2,—)}` = `{A: 3, B: 2}` → value = 5

The duplicate merge of A changed nothing. That's idempotence.

**Step 3:** `{A:3, B:2}.merge(c)` = `{A: 3, B: 2, C: 4}` → value = 9

## Phase 5 — Convergence Check

```java
boolean converged = aPath.value() == cPath.value() && cPath.value() == bPath.value();
```

All three paths produced value = 9. `converged = true`.

```
After merging (different orders, one with a duplicate delivery):
A-path: A{A=3, B=2, C=4} -> value=9
C-path: C{A=3, B=2, C=4} -> value=9
B-path (duplicate merge of A): B{A=3, B=2, C=4} -> value=9

All three converged to the same value: true
Final agreed value: 9 (expected 3+2+4=9)
```

The three properties in action:
- **Commutativity:** Path 1 (A→B→C) and Path 2 (C→A→B) gave the same result.
- **Idempotence:** Path 3 merged A twice and still gave the same result.
- **Associativity:** The grouping didn't matter — `(A merge B) merge C` = `A merge (B merge C)`.

This is the entire thesis of the project, demonstrated in 39 lines of code.

---

# Things I Should Remember

1. A **CRDT** is a data structure whose merge function is commutative, associative, and idempotent — guaranteeing convergence without coordination.

2. **Commutativity** means `merge(A, B) = merge(B, A)`. Order of syncing doesn't matter.

3. **Associativity** means `merge(merge(A, B), C) = merge(A, merge(B, C))`. Grouping doesn't matter.

4. **Idempotence** means `merge(A, A) = A`. Duplicate messages are harmless.

5. These three properties together mean: **any replica that has seen the same set of updates will be in the same state**, regardless of network behavior.

6. A **G-Counter** uses a map with one slot per replica. Each replica only increments its own slot. Merge is element-wise `max`.

7. A **PN-Counter** is just *two* G-Counters — one for increments, one for decrements. Value = P − N.

8. A **G-Set** is a set where you can only add, never remove. Merge is set union.

9. A **2P-Set** is two G-Sets — one for adds, one for tombstones. An element is present if it's been added but not tombstoned.

10. A **2P-Set removal is permanent.** Once tombstoned, an element can never come back.

11. An **LWW-Register** picks the value with the highest timestamp. Ties are broken by replica ID.

12. LWW **tie-breaking must be deterministic and symmetric** — both sides of a merge must agree on the winner.

13. LWW **discards the losing write silently.** It's not appropriate for data where every update matters.

14. An **OR-Set** tags every add with a unique ID (UUID). Remove only tombstones tags you've *observed*.

15. The OR-Set's key insight: **tombstone the tag, not the element.** This is what allows re-adding.

16. In an OR-Set, **concurrent add and remove of the same element results in the element being present** (add-wins semantics), because the add created a new tag that the remover hasn't seen.

17. CRDTs use **merge instead of overwrite.** No data is discarded during merge (except in LWW, by design).

18. The **composition pattern** appears twice: PNCounter = 2 × GCounter, TwoPSet = 2 × GSet. If the component CRDT is correct, the composite is automatically correct.

19. Local operations (increment, add, remove, set) **mutate the local replica.** Merge operations **return a new instance.** This asymmetry is intentional — local ops are "my change," merge is "incorporating someone else's state."

20. **G-Counter merge uses `max`, not `sum`**, precisely because `max` is idempotent and `sum` is not. If a sync message is delivered twice, `max` handles it correctly; `sum` would double-count.

21. **Replicas never write to each other's slots** in a G-Counter. This is the rule that makes concurrent increments safe by construction.

22. In a **2P-Set**, the `remove()` method checks `added.contains(element)` before tombstoning — you can't remove something that was never added.

23. The OR-Set's `remove()` iterates `addedTags` and writes to `removedTags`. It does **not** modify `addedTags` during iteration, so there is no `ConcurrentModificationException`.

24. **Tombstone growth** is a real problem in OR-Sets and 2P-Sets. Production systems use garbage collection (e.g., vector-clock-based pruning) to manage this.

25. **LWW-Registers depend on clocks.** Wall-clock time can be skewed. Production systems often use logical clocks (Lamport, HLC) instead.

26. CRDTs provide **strong eventual consistency**: once replicas have exchanged all updates, they are guaranteed to be in the same state. Not probabilistically — *guaranteed*.

27. The trade-off: CRDTs guarantee convergence but **restrict what operations you can express.** You can't have a set that does arbitrary add/remove with perfect semantics — every CRDT makes a semantic trade-off (G-Set: no remove; 2P-Set: no re-add; OR-Set: add-wins, not application-wins).

28. **No conflict resolution callbacks.** CRDTs don't detect and resolve conflicts at runtime — they make conflicts structurally impossible through the design of the merge function.

29. Real systems like **Cassandra, Riak, Redis CRDB, and Automerge** all use CRDTs or CRDT-inspired data structures internally.

30. The entire codebase compiles with `javac *.java` and runs with `java Main`. No dependencies beyond `java.util`. That's the whole point — understanding CRDTs requires nothing but the algorithms themselves.
