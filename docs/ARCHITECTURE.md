# Architecture and execution semantics

## Layer boundaries
```
JavaFX UI (ui/)
    -> SimulationController (engine/api/), background service and animation events
        -> SimulationEngine (engine/core/)
            -> WorldGenerator & immutable WorldSnapshot (world/)
            -> AStarPathfinder (pathfinding/)
            -> IntentComputer: Sequential or Parallel (engine/sequential/, engine/parallel/)
            -> Deterministic IntentResolver / Committer (engine/core/)
    -> benchmark/ (headless CLI, CSV, metrics; NEVER benchmark UI painting)
```
Suggested package root: `edu.parallel.evacuate`.

## World model
- Grid coordinates `(x,y)` with unique integer cell ID `y * width + x`.
- Cells are either blocked or traversable. Exit cells traversable and can remove incoming vehicles without occupancy limit. No diagonal movement.
- Each car has an immutable integer ID and an active position; add wait counter used for fairness.
- Initial placement unique, outside exits, and with a reachable exit for each car. World generator deterministic given `ScenarioConfig.seed`.
- An optional event closes one safe road on tick 20 or a configured tick; choose a closure guaranteed not to cover an active vehicle at time of event, or explicitly define a deterministic fallback that defers closure. The event schedule must be identical across modes.

## One logical tick (BOTH modes)
1. Coordinator applies deterministic scheduled road event (if any) and snapshots the current world **once**.
2. Build ordered active-car list sorted by car ID; all workers see that same immutable snapshot.
3. **Intent stage:** for each car, compute the A* next step to an exit, or a STAY intent; only read snapshot data. Sequential runs one loop; Parallel partitions cars across a reused fixed thread pool and joins all futures.
4. **Resolver:** shared single-threaded deterministic resolution of intents. Invalid moves rejected (blocked/outside grid/occupied in starting snapshot). If several cars request a free cell, highest accumulated wait count wins; car ID ascending breaks ties. Cars entering an exit are removed. Every eligible car moves at most one cell per tick. All others stay and update wait counters.
5. Commit a complete new WorldState, validate invariants, emit a TickStats + optional immutable UI snapshot.

**Important intentionally simple rule:** targeting a road cell *occupied at tick start* is prohibited, even when that occupant intends to leave this tick. This avoids swap/cycle complexity and ensures both modes agree. Explain that the rule can create congestion/gridlock (a traffic phenomenon, not a thread deadlock).

## Algorithm choices
- A*: Manhattan distance to the nearest *reachable* exit (admissible lower bound) with deterministic priority queue comparator, e.g. `(f, h, cellId)`, stable neighbor order N/E/S/W, and shortest tie-break by exit ID.
- Paths ignore transient car occupancy when planning; congestion is resolved at movement stage. Recompute next hop per active car per tick initially: useful parallel workload and simple correctness; only add route caching if data shows this is needed and determinism remains.
- If no route is possible, STAY and record reason; no exception-loop runaway.
- Pre-allocate per-car outputs or keep local partition lists. No concurrent append to shared mutable structures.

## Ownership and API
Use `docs/API_CONTRACTS.md` as the shared contract. Establish public types in T01 and do not change them in worker branches without coordinator approval. Avoid UI imports in `world`, `pathfinding`, `engine`, or `benchmark`.

## Observability
TickStats: remaining, evacuated, moved, blocked/conflicts, max waitTicks, optional per-worker load. SessionStats: elapsed wall-clock time, ticks processed, throughput and final SHA-256 state fingerprint. GPU/ML are not involved.

## Failure and shutdown behavior
- On cancellation: signal engine stop, interrupt worker tasks when appropriate, call executor shutdown and await termination boundedly; never leave non-daemon workers stuck.
- On exception: GUI gets visible error; engine leaves state internally valid, no partial commit.
- Separate benchmark runner from GUI session to avoid measuring rendering time.
