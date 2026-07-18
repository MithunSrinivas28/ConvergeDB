# ConvergeDB

**Building every major CRDT from scratch in Java to understand how distributed systems agree without coordination.**

---

## Why I Built This

Imagine you're editing a document in Google Docs. Your friend is editing the same paragraph at the same time. Neither of you is waiting for the other. There's no "lock" on the document. And yet, seconds later, both your screens show the same result with both changes intact.

How?

This seems simple when it works, but the underlying problem is one of the hardest in computer science. The moment you have two or more machines that can independently modify the same piece of data, you have a conflict waiting to happen.

This isn't a niche scenario. It's everywhere:

- **Google Docs** lets dozens of people type into the same document simultaneously, across different continents, with no visible delay.
- **Figma** lets designers move objects on the same canvas at the same time, even when someone's connection drops for a few seconds.
- **Redis Active-Active** replicates data across multiple data centers, where each data center can accept writes independently — and they all need to eventually agree.
- **Offline mobile apps** let you keep working — adding items to a shopping list, writing notes, marking tasks complete — even with no network at all. When connectivity returns, your changes need to merge cleanly with whatever happened on other devices while you were offline.

The classic approach to this problem is straightforward: pick one central server to be the "source of truth," and make every write go through it. If two writes conflict, the server decides who wins. Simple.

But this falls apart quickly:

- What if the central server goes down? Every client is stuck.
- What if the server is on another continent? Every keystroke has 200ms of latency.
- What if you need to work offline? You can't write to a server you can't reach.
- What if the system is so large that a single server becomes a bottleneck?

You could use locks — "only one person can edit this field at a time" — but locks kill the user experience. Nobody wants to see "this cell is locked by another user" in a collaborative spreadsheet.

So the question becomes: is there a way to let every replica accept writes freely, with no coordination, no locks, no central server, and still guarantee that they all converge to the same state?

---

## The Big Question

> If two computers update the same piece of data at the same time, with no communication between them, how can they eventually agree on the result — without losing anyone's changes?

This is not an obvious thing to solve. Your first instinct might be "just take the latest write" — but what if both writes happened at the exact same millisecond? Or what if "latest" means different things on two machines whose clocks disagree?

Your next instinct might be "merge them somehow" — but what does "merge" even mean for a counter? For a set? For a text field? And how do you make sure that merging in different orders doesn't produce different results?

It turns out there is an elegant family of data structures designed exactly for this problem. They're called **CRDTs** — Conflict-free Replicated Data Types.

The core idea: if you design your data structure so that its merge operation is mathematically guaranteed to produce the same result regardless of the order it's applied in, then you never need conflict resolution at all. Replicas can sync in any order, at any time, even with duplicate or delayed messages, and they will always converge to the same state.

No central server. No locks. No conflict resolution logic. Just math.

---

## What Are CRDTs?

A CRDT is a data structure where the "merge" operation has been carefully designed so that order doesn't matter.

Think about the `max` function. If you have the numbers 3, 7, and 5, it doesn't matter what order you compare them in:

- `max(max(3, 7), 5)` = 7
- `max(3, max(7, 5))` = 7
- `max(max(5, 3), 7)` = 7

The answer is always 7. You can't get a wrong answer by applying `max` in the wrong order. You can even apply it to the same pair twice and nothing changes — `max(7, 7)` is still 7.

CRDTs work the same way, but instead of merging single numbers, they merge entire data structures — counters, sets, registers, maps. The merge function is designed so that:

1. **Order doesn't matter.** Merging A with B gives the same result as merging B with A.
2. **Grouping doesn't matter.** Merging A with B, then merging the result with C, is the same as merging A with the result of merging B with C.
3. **Duplicates are harmless.** Merging A with itself changes nothing.

If all three of these hold, then no matter how chaotic the network is — messages arrive out of order, get duplicated, or take different paths — every replica that has received the same set of updates will have the same state.

This is eventual consistency by construction. Not by hope, not by retry logic, not by conflict resolution callbacks. By the mathematical properties of the merge function itself.

---

## Project Goal

This project implements six fundamental CRDTs completely from scratch in Java.

No CRDT libraries. No distributed systems frameworks. No dependencies beyond `java.util`.

Every implementation is written from first principles: the state representation, the mutation operations, and the merge function. The goal is to make each algorithm's core idea so clear that you could reimplement it from memory after reading the code once.

This is an educational project. It prioritizes clarity and correctness over production-grade performance. Each CRDT is a self-contained class that you can read top to bottom in under a minute.

---

## Implemented CRDTs

| CRDT | Purpose | Core Idea | Merge Strategy | Time Complexity (merge) |
|---|---|---|---|---|
| **G-Counter** | Distributed counting (increment only) | Each replica owns a personal slot in a map; it only ever increments its own slot | Element-wise `max` across all slots | O(n) where n = number of replicas |
| **PN-Counter** | Distributed counting (increment and decrement) | Two G-Counters: one tracks increments, one tracks decrements. Value = P − N | Merge each G-Counter independently | O(n) |
| **G-Set** | Distributed set (add only) | A `HashSet` that only grows; elements can never be removed | Set union | O(n) where n = elements in other set |
| **2P-Set** | Distributed set (add and remove, but remove is permanent) | Two G-Sets: one for adds, one for tombstones. An element is "in" the set if it's been added but not tombstoned | Merge each G-Set independently | O(n) |
| **LWW-Register** | Single-value register (last writer wins) | Every write is stamped with a timestamp. The value with the highest timestamp wins. Ties are broken deterministically by replica ID | Keep the entry with the higher timestamp; break ties by comparing replica IDs lexicographically | O(1) |
| **OR-Set** | Distributed set (add and remove, with re-add support) | Every `add` generates a globally unique tag (UUID). `remove` only tombstones the tags that the remover has *observed* — concurrent adds from other replicas survive | Set union of both the add-tags and the remove-tags | O(n) where n = total tags in other set |

---

## Architecture

The project is intentionally flat — seven Java files in one directory, no packages, no frameworks:

```
├── GCounter.java          The foundational counter CRDT
├── PNCounter.java         Composed of two GCounters
├── GSet.java              The foundational set CRDT
├── TwoPSet.java           Composed of two GSets
├── LWWRegister.java       Timestamp-based register
├── ORSet.java             Tag-based observable-remove set
├── Main.java              Demonstrates GCounter convergence
└── CRDTSimulation.java    Full convergence test for all 6 CRDTs
```

**Design decisions:**

- **Composition over inheritance.** `PNCounter` doesn't extend `GCounter` — it *contains* two of them. `TwoPSet` doesn't extend `GSet` — it contains two of them. This mirrors how the algorithms are actually defined in the literature: a PN-Counter *is* a pair of G-Counters, not a special kind of G-Counter.

- **Immutable merge results.** Every `merge()` method returns a *new* instance rather than mutating either input. This means you can merge freely without worrying about accidentally corrupting a replica's state. The input replicas remain untouched.

- **Mutating local operations.** Operations like `increment()`, `add()`, and `remove()` mutate the local replica in place. This is a deliberate choice: these operations represent a replica applying a change to its own state, which is conceptually a local mutation. The asymmetry — mutating local ops, immutable merges — reflects the real-world split between "I'm updating my own state" and "I'm incorporating someone else's state."

- **No shared interfaces.** Each CRDT has its own method signatures because each one models a fundamentally different data type. A counter has `increment()` and `value()`. A set has `add()` and `contains()`. A register has `set()` and `getValue()`. Forcing them into a common interface would add abstraction without adding clarity.

- **Generics where appropriate.** The set-based CRDTs (`GSet<T>`, `TwoPSet<T>`, `ORSet<T>`) and the register (`LWWRegister<T>`) are generic. The counters are not — they count integers, always.

---

## How Convergence Happens

Here's a small walkthrough using the G-Counter to show how two replicas can diverge, merge, and converge.

**Setup:** Two replicas, A and B, each starting from zero.

```
Replica A: {A: 0, B: 0}    value = 0
Replica B: {A: 0, B: 0}    value = 0
```

**Step 1 — Independent local operations (no communication):**

Replica A increments 3 times. Replica B increments 2 times. Neither knows about the other.

```
Replica A: {A: 3, B: 0}    value = 3
Replica B: {A: 0, B: 2}    value = 2
```

They have diverged. A thinks the count is 3. B thinks it's 2. Both are correct from their own perspective.

**Step 2 — Merge:**

Now A receives B's state and merges:

```
merge(A, B):
  A-slot: max(3, 0) = 3
  B-slot: max(0, 2) = 2
  Result: {A: 3, B: 2}    value = 5
```

B receives A's state and merges:

```
merge(B, A):
  A-slot: max(0, 3) = 3
  B-slot: max(2, 0) = 2
  Result: {A: 3, B: 2}    value = 5
```

**Result:** Both replicas now hold `{A: 3, B: 2}` with value 5. They have converged to the same state, and neither replica's increments were lost.

**What if a message gets duplicated?**

B merges A's state a second time (simulating a network retry):

```
merge({A: 3, B: 2}, {A: 3, B: 0}):
  A-slot: max(3, 3) = 3
  B-slot: max(2, 0) = 2
  Result: {A: 3, B: 2}    value = 5
```

Still 5. The duplicate had no effect. This is idempotence in action.

---

## Key Design Ideas

### Eventual Consistency

Every CRDT in this project guarantees eventual consistency: if two replicas have received the same set of updates (in any order, with any duplicates), they will be in the same state. This is not a hope or a best-effort property — it's a mathematical consequence of how the merge function is constructed.

### Replica Independence

No replica ever needs to ask another replica for permission to perform a local operation. An `increment()` on a G-Counter, an `add()` on a G-Set, a `set()` on an LWW-Register — these are all purely local. The replica doesn't need to be connected to the network. It doesn't need to wait for a response. It just applies the operation and moves on. Synchronization happens later, asynchronously, through merge.

### Merge Instead of Overwrite

Traditional databases often resolve conflicts by picking a winner and discarding the loser. CRDTs don't discard anything. The G-Counter doesn't pick A's count or B's count — it takes the `max` of each slot, preserving every replica's contribution. The G-Set doesn't pick one replica's elements — it takes the union. The OR-Set doesn't permanently ban a removed element — it only removes the specific *instances* that the remover had observed, allowing concurrent adds to survive.

### No Conflict Resolution Logic

There is no `onConflict` callback. No `conflictResolver` strategy pattern. No "if both sides changed the same field, do X." The data structure itself makes conflicts impossible by construction. This is the deepest insight of CRDTs: you don't resolve conflicts at runtime — you design them away at the data structure level.

---

## What I Learned

Building CRDTs from scratch changed how I think about distributed systems.

Before this project, I thought of conflicts as an inevitability — something you detect after the fact and resolve with heuristics. Pick the latest write. Ask the user. Merge the JSON fields. Every approach felt like a patch over a fundamental problem.

CRDTs showed me that the problem isn't inevitable. It's a consequence of choosing data structures that weren't designed for concurrency. If you choose the right data structure — one whose merge operation is commutative, associative, and idempotent — conflicts simply cannot arise. The math prevents them.

I also gained a much deeper appreciation for how merge algorithms work. It's easy to say "just take the max" for a counter, but the design space gets genuinely interesting when you move to sets. The difference between a 2P-Set (where remove is permanent) and an OR-Set (where you can re-add after removing) comes down to a single design choice: do you tombstone the *element* or the *tag*? That one decision completely changes the semantics of the data structure.

Working with eventual consistency also taught me how hard it is to reason about time in distributed systems. The LWW-Register looks simple until you realize that "last" depends on clock synchronization, and clocks across machines can disagree. The tie-breaking logic (using replica IDs) isn't an optimization — it's a correctness requirement. Without it, two replicas could merge the same pair of concurrent writes and reach different conclusions.

Perhaps the biggest takeaway is how much engineering goes into the collaborative tools I use every day. When I type in Google Docs and my teammate's edits appear seamlessly, or when I drag an element in Figma and my colleague sees it move in real time — there are layers of carefully designed data structures making that feel effortless. This project gave me a small window into that world, and a deep respect for the people who build these systems at scale.

---

## Running the Simulation

`CRDTSimulation.java` is a self-contained demo that verifies all six CRDTs. It simulates a realistic distributed scenario for each data structure:

1. **Three replicas** (R1, R2, R3) are created for each CRDT.
2. **Network partition** — each replica performs operations independently with no communication.
3. **Reconnection** — all replicas merge with each other in different orders.
4. **Convergence check** — the simulation asserts that every replica arrives at the same state after merging.

CRDTs tested: GCounter, PNCounter, GSet, TwoPSet, LWWRegister, ORSet.

```bash
javac *.java
java CRDTSimulation
```

Expected output: 18 passed, 0 failed.

To run the original GCounter-only demo instead:

```bash
java Main
```

---

## Build and Run

```bash
javac *.java
java Main
```

Requires Java 16+ (uses records in `ORSet`).

No external dependencies.
