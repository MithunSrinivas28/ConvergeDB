# ConvergeDB

**Building every major CRDT from scratch in Java to understand how distributed systems agree without coordination.**

---

## Why I Built This

Applications like **Google Docs**, **Figma**, and offline-first mobile apps allow multiple users to modify the same data simultaneously, even across different devices and unreliable networks. Despite concurrent edits, every replica eventually reaches the same state without requiring manual conflict resolution.

This is made possible by **Conflict-free Replicated Data Types (CRDTs)**—data structures specifically designed for distributed systems. Instead of preventing conflicts through locks or centralized coordination, CRDTs allow replicas to update independently and rely on deterministic merge algorithms to guarantee eventual consistency.

This project is my implementation of several fundamental **state-based (CvRDT)** data structures in Java. The goal was to understand how different CRDTs represent state, perform merge operations, and ensure convergence in distributed environments.

---

## The Big Question

> **How can multiple replicas modify the same data independently and still converge to the same state without conflicts?**

Traditional systems often rely on a central server, distributed locks, or conflict-resolution strategies. While these approaches work, they introduce latency, coordination overhead, and single points of failure.

CRDTs take a different approach. They are designed so that every replica can accept local updates independently, exchange state asynchronously, and deterministically merge those states. Because the merge operation is **commutative, associative, and idempotent**, every replica eventually reaches the same state regardless of message order, duplication, or network delays.

No locks. No coordination. Just deterministic merging.
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
